package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.AssignmentTask;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.dto.CoachReplyRequest;
import com.onlinejudge.classroom.dto.LearningProofResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.classroom.dto.TeacherProblemLearningProofResponse;
import com.onlinejudge.classroom.persistence.AssignmentTaskRepository;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.application.SubmissionGrowthSummaryService;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.dto.SubmissionGrowthSummaryResponse;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningProofService {

    public static final String REFLECTION_PROMPT_TYPE = "LEARNING_REFLECTION";
    public static final String REFLECTION_QUESTION = "这次真正改变结果的修改是什么？请说明改了哪里、原来为什么会失败，并写出一个可以验证这次修改的样例或边界。";

    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository analysisRepository;
    private final ProblemRepository problemRepository;
    private final AssignmentTaskRepository assignmentTaskRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CoachPromptRepository coachPromptRepository;
    private final SubmissionGrowthSummaryService growthSummaryService;
    private final CoachAnswerQualityAnalyzer answerQualityAnalyzer;
    private final PostAcTransferAnalyzer postAcTransferAnalyzer;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public LearningProofResponse getForSubmission(Long submissionId) {
        Submission target = requireSubmission(submissionId);
        return build(target, sameAssignmentSubmissions(target));
    }

    @Transactional
    public LearningProofResponse createReflection(Long submissionId) {
        Submission target = requireSubmission(submissionId);
        List<Submission> assignmentSubmissions = sameAssignmentSubmissions(target);
        LearningProofResponse proof = build(target, assignmentSubmissions);
        if (target.getVerdict() != Submission.Verdict.ACCEPTED
                && !"REPAIRED".equals(proof.getRepair().getStatus())) {
            throw new IllegalStateException("形成关键修改或通过后再说明。");
        }
        Long reflectionSubmissionId = proof.getExplanation().getSubmissionId();
        if (reflectionSubmissionId == null) {
            throw new IllegalStateException("当前还没有可绑定的关键提交。");
        }
        Submission reflectionSubmission = requireSubmission(reflectionSubmissionId);
        coachPromptRepository.findTopBySubmissionIdAndPromptTypeOrderByCreatedAtDesc(
                reflectionSubmissionId, REFLECTION_PROMPT_TYPE).orElseGet(() -> coachPromptRepository.save(CoachPrompt.builder()
                .assignmentId(reflectionSubmission.getAssignmentId())
                .studentProfileId(reflectionSubmission.getStudentProfileId())
                .submissionId(reflectionSubmission.getId())
                .turnIndex(0)
                .hintPolicy("FIXED")
                .promptType(REFLECTION_PROMPT_TYPE)
                .question(REFLECTION_QUESTION)
                .rationale("固定学习证明问题，不调用模型。")
                .contextSummary("回答绑定关键修改与当前提交。")
                .evidenceRefs(toJson(reflectionRefs(proof, reflectionSubmission)))
                .build()));
        return build(target, assignmentSubmissions);
    }

    @Transactional
    public LearningProofResponse answerReflection(Long submissionId, CoachReplyRequest request) {
        Submission target = requireSubmission(submissionId);
        String answer = request == null || request.getAnswer() == null ? "" : request.getAnswer().trim();
        if (answer.isBlank()) {
            throw new IllegalArgumentException("请先写下这次修改的说明。");
        }
        List<Submission> assignmentSubmissions = sameAssignmentSubmissions(target);
        LearningProofResponse proof = build(target, assignmentSubmissions);
        Long reflectionSubmissionId = proof.getExplanation().getSubmissionId();
        if (reflectionSubmissionId == null) {
            throw new IllegalStateException("请先形成可说明的关键提交。");
        }
        CoachPrompt prompt = coachPromptRepository.findTopBySubmissionIdAndPromptTypeOrderByCreatedAtDesc(
                        reflectionSubmissionId, REFLECTION_PROMPT_TYPE)
                .orElseThrow(() -> new IllegalStateException("请先打开修正说明。"));
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal quality = answerQualityAnalyzer.analyze(answer);
        prompt.setStudentAnswer(answer);
        prompt.setCoachFeedback(Boolean.TRUE.equals(quality.getVerifiable())
                ? "这段说明已经包含可检查的修改或样例，可以继续用另一道题验证。"
                : "说明已经保存；再补充一个具体输入、边界或前后结果，会更容易核验。");
        prompt.setAnsweredAt(LocalDateTime.now());
        coachPromptRepository.save(prompt);
        return build(target, assignmentSubmissions);
    }

    @Transactional(readOnly = true)
    public TeacherProblemLearningProofResponse getTeacherProblemProof(Long assignmentId, Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + problemId));
        List<Submission> assignmentSubmissions = submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId);
        List<Submission> problemSubmissions = assignmentSubmissions
                .stream()
                .filter(item -> Objects.equals(problemId, item.getProblemId()))
                .filter(item -> item.getStudentProfileId() != null)
                .toList();
        Map<Long, List<Submission>> byStudent = problemSubmissions.stream()
                .collect(Collectors.groupingBy(Submission::getStudentProfileId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, StudentProfile> profiles = studentProfileRepository.findAllById(byStudent.keySet()).stream()
                .collect(Collectors.toMap(StudentProfile::getId, Function.identity()));
        List<TeacherProblemLearningProofResponse.StudentProof> students = new ArrayList<>();
        for (Map.Entry<Long, List<Submission>> entry : byStudent.entrySet()) {
            Submission latest = latest(entry.getValue());
            List<Submission> studentAssignmentSubmissions = assignmentSubmissions.stream()
                    .filter(item -> Objects.equals(entry.getKey(), item.getStudentProfileId()))
                    .toList();
            LearningProofResponse proof = build(latest, studentAssignmentSubmissions);
            StudentProfile profile = profiles.get(entry.getKey());
            boolean hadFailure = entry.getValue().stream().anyMatch(this::isFailedAttempt);
            boolean repaired = "REPAIRED".equals(proof.getRepair().getStatus());
            boolean explained = List.of("PROVIDED", "CHECKABLE").contains(proof.getExplanation().getStatus());
            boolean verified = "VERIFIED".equals(proof.getIndependentUse().getStatus());
            students.add(TeacherProblemLearningProofResponse.StudentProof.builder()
                    .studentProfileId(entry.getKey())
                    .displayName(profile == null ? "学生 #" + entry.getKey() : profile.getDisplayName())
                    .studentNo(profile == null ? "" : profile.getStudentNo())
                    .latestSubmissionId(latest.getId())
                    .hadFailure(hadFailure)
                    .repaired(repaired)
                    .explained(explained)
                    .explanationCheckable(proof.getExplanation().isCheckable())
                    .independentVerified(verified)
                    .proof(proof)
                    .build());
        }
        students.sort(Comparator.comparing(TeacherProblemLearningProofResponse.StudentProof::getDisplayName,
                Comparator.nullsLast(String::compareToIgnoreCase)));
        return TeacherProblemLearningProofResponse.builder()
                .assignmentId(assignmentId)
                .problemId(problemId)
                .problemTitle(problem.getTitle())
                .failedStudentCount(students.stream().filter(TeacherProblemLearningProofResponse.StudentProof::isHadFailure).count())
                .repairedStudentCount(students.stream().filter(TeacherProblemLearningProofResponse.StudentProof::isRepaired).count())
                .explainedStudentCount(students.stream().filter(TeacherProblemLearningProofResponse.StudentProof::isExplained).count())
                .independentVerifiedStudentCount(students.stream().filter(TeacherProblemLearningProofResponse.StudentProof::isIndependentVerified).count())
                .students(students)
                .build();
    }

    private LearningProofResponse build(Submission requested, List<Submission> assignmentSubmissions) {
        List<Submission> scope = assignmentSubmissions.stream()
                .filter(item -> Objects.equals(item.getProblemId(), requested.getProblemId()))
                .filter(item -> Objects.equals(item.getAssignmentId(), requested.getAssignmentId()))
                .sorted(submissionComparator())
                .toList();
        Map<Long, SubmissionGrowthSummaryResponse> summaries = growthSummaryService.summarize(scope);
        LearningProofResponse.RepairProof repair = repairProof(scope, summaries);
        Submission reflectionSubmission = reflectionSubmission(scope, repair, requested);
        LearningProofResponse.ExplanationProof explanation = explanationProof(reflectionSubmission, repair);
        LearningProofResponse.IndependentUseProof independent = independentUseProof(
                requested, scope, assignmentSubmissions, reflectionSubmission);
        Problem problem = problemRepository.findById(requested.getProblemId()).orElse(null);
        return LearningProofResponse.builder()
                .studentProfileId(requested.getStudentProfileId())
                .assignmentId(requested.getAssignmentId())
                .problemId(requested.getProblemId())
                .problemTitle(problem == null ? null : problem.getTitle())
                .latestSubmissionId(latest(scope).getId())
                .repair(repair)
                .explanation(explanation)
                .independentUse(independent)
                .build();
    }

    private LearningProofResponse.RepairProof repairProof(
            List<Submission> scope,
            Map<Long, SubmissionGrowthSummaryResponse> summaries) {
        SubmissionGrowthSummaryResponse selected = scope.stream()
                .map(item -> summaries.get(item.getId()))
                .filter(Objects::nonNull)
                .filter(SubmissionGrowthSummaryResponse::isComparable)
                .filter(SubmissionGrowthSummaryResponse::isEffectiveAttempt)
                .filter(item -> item.getComparisonSubmissionId() != null)
                .filter(this::hasRepairEvidence)
                .max(Comparator.comparingInt(this::repairStrength)
                        .thenComparing(SubmissionGrowthSummaryResponse::getSubmissionId))
                .orElse(null);
        if (selected == null) {
            Submission accepted = scope.stream()
                    .filter(item -> item.getVerdict() == Submission.Verdict.ACCEPTED)
                    .filter(item -> scope.stream().anyMatch(previous -> isBefore(previous, item) && isFailedAttempt(previous)))
                    .findFirst()
                    .orElse(null);
            if (accepted == null) {
                return LearningProofResponse.RepairProof.builder()
                        .status(scope.stream().anyMatch(this::isFailedAttempt) ? "IN_PROGRESS" : "NOT_OBSERVED")
                        .recoveredIssueCount(0)
                        .recoveredIssues(List.of())
                        .evidenceRefs(scope.stream().map(item -> "submission:" + item.getId()).toList())
                        .build();
            }
            Submission baseline = scope.stream()
                    .filter(item -> isBefore(item, accepted) && isFailedAttempt(item))
                    .max(submissionComparator())
                    .orElse(null);
            return LearningProofResponse.RepairProof.builder()
                    .status("REPAIRED")
                    .baselineSubmissionId(baseline == null ? null : baseline.getId())
                    .targetSubmissionId(accepted.getId())
                    .recoveredIssueCount(0)
                    .recoveredIssues(List.of())
                    .evidenceRefs(evidenceRefs(baseline, accepted))
                    .build();
        }
        List<String> recovered = safe(selected.getIssueSignals()).stream()
                .filter(item -> "RECOVERED".equals(item.getChangeStatus()))
                .map(SubmissionGrowthSummaryResponse.IssueSignal::getTitle)
                .filter(this::hasText)
                .distinct()
                .toList();
        return LearningProofResponse.RepairProof.builder()
                .status("REPAIRED")
                .baselineSubmissionId(selected.getComparisonSubmissionId())
                .targetSubmissionId(selected.getSubmissionId())
                .passedTestCaseDelta(selected.getPassedTestCaseDelta())
                .recoveredIssueCount(selected.getRecoveredCount())
                .recoveredIssues(recovered)
                .evidenceRefs(List.of(
                        "submission:" + selected.getComparisonSubmissionId(),
                        "submission:" + selected.getSubmissionId(),
                        "growth-rule:" + selected.getRuleVersion()))
                .build();
    }

    private LearningProofResponse.ExplanationProof explanationProof(
            Submission reflectionSubmission,
            LearningProofResponse.RepairProof repair) {
        if (reflectionSubmission == null) {
            return emptyExplanation("NOT_READY", null, repair);
        }
        CoachPrompt prompt = coachPromptRepository.findTopBySubmissionIdAndPromptTypeOrderByCreatedAtDesc(
                reflectionSubmission.getId(), REFLECTION_PROMPT_TYPE).orElse(null);
        if (prompt == null) {
            return emptyExplanation("TO_EXPLAIN", reflectionSubmission.getId(), repair);
        }
        List<String> refs = parseRefs(prompt.getEvidenceRefs());
        if (!hasText(prompt.getStudentAnswer())) {
            return LearningProofResponse.ExplanationProof.builder()
                    .status("WAITING")
                    .promptId(prompt.getId())
                    .submissionId(prompt.getSubmissionId())
                    .question(prompt.getQuestion())
                    .feedback(prompt.getCoachFeedback())
                    .checkable(false)
                    .evidenceTypes(List.of())
                    .evidenceRefs(refs)
                    .build();
        }
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal quality = answerQualityAnalyzer.analyze(prompt.getStudentAnswer());
        boolean checkable = Boolean.TRUE.equals(quality.getVerifiable());
        return LearningProofResponse.ExplanationProof.builder()
                .status(checkable ? "CHECKABLE" : "PROVIDED")
                .promptId(prompt.getId())
                .submissionId(prompt.getSubmissionId())
                .question(prompt.getQuestion())
                .answer(prompt.getStudentAnswer())
                .feedback(prompt.getCoachFeedback())
                .checkable(checkable)
                .evidenceTypes(quality.getEvidenceTypes())
                .evidenceRefs(refs)
                .build();
    }

    private LearningProofResponse.ExplanationProof emptyExplanation(
            String status,
            Long submissionId,
            LearningProofResponse.RepairProof repair) {
        return LearningProofResponse.ExplanationProof.builder()
                .status(status)
                .submissionId(submissionId)
                .question(REFLECTION_QUESTION)
                .checkable(false)
                .evidenceTypes(List.of())
                .evidenceRefs(repair == null ? List.of() : repair.getEvidenceRefs())
                .build();
    }

    private LearningProofResponse.IndependentUseProof independentUseProof(
            Submission requested,
            List<Submission> scope,
            List<Submission> assignmentSubmissions,
            Submission reflectionSubmission) {
        Submission source = scope.stream()
                .filter(item -> item.getVerdict() == Submission.Verdict.ACCEPTED)
                .findFirst()
                .orElse(reflectionSubmission);
        if (source == null || requested.getAssignmentId() == null) {
            return emptyIndependent("NOT_READY", source);
        }
        List<AssignmentTask> tasks = assignmentTaskRepository.findByAssignmentIdOrderByOrderIndexAsc(requested.getAssignmentId());
        Set<Long> taskProblemIds = tasks.stream().map(AssignmentTask::getProblemId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Problem> problems = problemRepository.findAllById(taskProblemIds).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        Problem sourceProblem = problems.get(requested.getProblemId());
        if (sourceProblem == null) {
            return emptyIndependent("NOT_READY", source);
        }
        Map<Long, List<Submission>> byProblem = assignmentSubmissions.stream()
                .filter(item -> item.getProblemId() != null)
                .collect(Collectors.groupingBy(Submission::getProblemId));
        List<Problem> related = tasks.stream()
                .map(task -> problems.get(task.getProblemId()))
                .filter(Objects::nonNull)
                .filter(problem -> !Objects.equals(problem.getId(), sourceProblem.getId()))
                .filter(problem -> overlapsKnowledge(sourceProblem, problem))
                .toList();
        Problem target = related.stream()
                .filter(problem -> byProblem.getOrDefault(problem.getId(), List.of()).stream()
                        .anyMatch(item -> item.getVerdict() == Submission.Verdict.ACCEPTED && isAfter(item, source)))
                .findFirst()
                .orElse(null);
        Submission targetSubmission = target == null ? null : byProblem.getOrDefault(target.getId(), List.of()).stream()
                .filter(item -> item.getVerdict() == Submission.Verdict.ACCEPTED && isAfter(item, source))
                .min(submissionComparator())
                .orElse(null);
        if (targetSubmission != null && transferVerified(sourceProblem.getId(), assignmentSubmissions, problems)) {
            return LearningProofResponse.IndependentUseProof.builder()
                    .status("VERIFIED")
                    .sourceSubmissionId(source.getId())
                    .targetProblemId(target.getId())
                    .targetProblemTitle(target.getTitle())
                    .targetSubmissionId(targetSubmission.getId())
                    .evidenceRefs(List.of("submission:" + source.getId(), "transfer-submission:" + targetSubmission.getId()))
                    .build();
        }
        target = related.stream()
                .filter(problem -> !byProblem.getOrDefault(problem.getId(), List.of()).isEmpty())
                .findFirst()
                .orElseGet(() -> related.stream()
                        .filter(problem -> byProblem.getOrDefault(problem.getId(), List.of()).isEmpty())
                        .findFirst()
                        .orElse(null));
        if (target == null) {
            return emptyIndependent("NOT_AVAILABLE", source);
        }
        List<Submission> attempts = byProblem.getOrDefault(target.getId(), List.of());
        Submission latestTarget = attempts.stream().max(submissionComparator()).orElse(null);
        return LearningProofResponse.IndependentUseProof.builder()
                .status(attempts.isEmpty() ? "TARGET_AVAILABLE" : "NEEDS_SUPPORT")
                .sourceSubmissionId(source.getId())
                .targetProblemId(target.getId())
                .targetProblemTitle(target.getTitle())
                .targetSubmissionId(latestTarget == null ? null : latestTarget.getId())
                .evidenceRefs(latestTarget == null
                        ? List.of("submission:" + source.getId(), "problem:" + target.getId())
                        : List.of("submission:" + source.getId(), "submission:" + latestTarget.getId()))
                .build();
    }

    private boolean transferVerified(Long sourceProblemId, List<Submission> submissions, Map<Long, Problem> problems) {
        List<Long> ids = submissions.stream().map(Submission::getId).filter(Objects::nonNull).toList();
        Map<Long, SubmissionAnalysis> analyses = analysisRepository.findBySubmissionIdIn(ids).stream()
                .collect(Collectors.toMap(SubmissionAnalysis::getSubmissionId, Function.identity()));
        StudentTrajectoryResponse.PostAcTransferSignal signal = postAcTransferAnalyzer
                .analyzeTasks(submissions, analyses, Map.of(), problems)
                .get(sourceProblemId);
        return signal != null && PostAcTransferAnalyzer.PHASE_TRANSFER_VERIFIED.equals(signal.getPhase());
    }

    private LearningProofResponse.IndependentUseProof emptyIndependent(String status, Submission source) {
        return LearningProofResponse.IndependentUseProof.builder()
                .status(status)
                .sourceSubmissionId(source == null ? null : source.getId())
                .evidenceRefs(source == null ? List.of() : List.of("submission:" + source.getId()))
                .build();
    }

    private Submission reflectionSubmission(
            List<Submission> scope,
            LearningProofResponse.RepairProof repair,
            Submission requested) {
        if (repair != null && repair.getTargetSubmissionId() != null) {
            return scope.stream().filter(item -> Objects.equals(item.getId(), repair.getTargetSubmissionId())).findFirst().orElse(null);
        }
        if (requested.getVerdict() == Submission.Verdict.ACCEPTED) {
            return requested;
        }
        return scope.stream().filter(item -> item.getVerdict() == Submission.Verdict.ACCEPTED).findFirst().orElse(null);
    }

    private List<Submission> sameAssignmentSubmissions(Submission target) {
        if (target.getStudentProfileId() == null) {
            return List.of(target);
        }
        if (target.getAssignmentId() == null) {
            return submissionRepository.findByProblemIdOrderBySubmittedAtAsc(target.getProblemId()).stream()
                    .filter(item -> item.getAssignmentId() == null)
                    .filter(item -> Objects.equals(item.getStudentProfileId(), target.getStudentProfileId()))
                    .toList();
        }
        return submissionRepository.findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(
                target.getAssignmentId(), target.getStudentProfileId());
    }

    private boolean hasRepairEvidence(SubmissionGrowthSummaryResponse item) {
        return item.getRecoveredCount() > 0
                || (item.getPassedTestCaseDelta() != null && item.getPassedTestCaseDelta() > 0)
                || "COMPLETED".equals(item.getGrowthState());
    }

    private int repairStrength(SubmissionGrowthSummaryResponse item) {
        int strength = Math.toIntExact(Math.min(100, item.getRecoveredCount() * 10));
        if (item.getPassedTestCaseDelta() != null && item.getPassedTestCaseDelta() > 0) {
            strength += Math.min(9, item.getPassedTestCaseDelta());
        }
        if ("COMPLETED".equals(item.getGrowthState())) {
            strength += 100;
        }
        return strength;
    }

    private boolean overlapsKnowledge(Problem source, Problem target) {
        Set<String> sourcePoints = normalize(source.getKnowledgePoints());
        Set<String> targetPoints = normalize(target.getKnowledgePoints());
        return !sourcePoints.isEmpty() && sourcePoints.stream().anyMatch(targetPoints::contains);
    }

    private Set<String> normalize(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(this::hasText)
                .map(String::toLowerCase).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> reflectionRefs(LearningProofResponse proof, Submission target) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        refs.add("submission:" + target.getId());
        if (proof != null && proof.getRepair() != null && proof.getRepair().getEvidenceRefs() != null) {
            refs.addAll(proof.getRepair().getEvidenceRefs());
        }
        return List.copyOf(refs);
    }

    private List<String> evidenceRefs(Submission baseline, Submission target) {
        List<String> refs = new ArrayList<>();
        if (baseline != null) {
            refs.add("submission:" + baseline.getId());
        }
        if (target != null) {
            refs.add("submission:" + target.getId());
        }
        return refs;
    }

    private boolean isFailedAttempt(Submission submission) {
        return submission.getVerdict() != null
                && submission.getVerdict() != Submission.Verdict.PENDING
                && submission.getVerdict() != Submission.Verdict.ACCEPTED;
    }

    private boolean isBefore(Submission left, Submission right) {
        return submissionComparator().compare(left, right) < 0;
    }

    private boolean isAfter(Submission left, Submission right) {
        return submissionComparator().compare(left, right) > 0;
    }

    private Comparator<Submission> submissionComparator() {
        return Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Submission::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Submission latest(List<Submission> submissions) {
        return submissions.stream().max(submissionComparator())
                .orElseThrow(() -> new IllegalStateException("当前范围没有提交记录。"));
    }

    private Submission requireSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ignored) {
            return "[]";
        }
    }

    private List<String> parseRefs(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }
}
