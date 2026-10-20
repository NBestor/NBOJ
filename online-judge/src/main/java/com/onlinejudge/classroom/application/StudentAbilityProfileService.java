package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import com.onlinejudge.classroom.dto.StudentProfileResponse;
import com.onlinejudge.classroom.persistence.StudentRecommendationEventRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
public class StudentAbilityProfileService {

    private static final int RECENT_WINDOW = 40;
    private static final int REVIEW_CARD_LIMIT = 5;

    private final StudentProfileRepository studentProfileRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final ProblemRepository problemRepository;
    private final AbilitySignalAnalyzer abilitySignalAnalyzer;
    private final CoachInteractionAnalyzer coachInteractionAnalyzer;
    private final StudentIdentityService studentIdentityService;
    private final StudentRecommendationEventRepository recommendationEventRepository;
    private final CoachImpactAnalyzer coachImpactAnalyzer;
    private final RecurringMisconceptionAnalyzer recurringMisconceptionAnalyzer;
    private final SelfExplanationMasteryAnalyzer selfExplanationMasteryAnalyzer;
    private final AiDependencyAnalyzer aiDependencyAnalyzer;
    private final MasteryGrowthAnalyzer masteryGrowthAnalyzer;
    private final TeachingActionOrchestrator teachingActionOrchestrator;
    private final DiagnosisReportReader diagnosisReportReader;

    public StudentScope resolveStudentScope(Long studentProfileId) {
        StudentProfile student = studentProfileRepository.findById(studentProfileId)
                .orElseThrow(() -> new IllegalArgumentException("学生画像不存在: " + studentProfileId));
        List<Long> profileIds = findMergedProfiles(student).stream()
                .map(StudentProfile::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new StudentScope(StudentProfileResponse.from(student), profileIds);
    }

    public StudentAbilityProfileResponse buildProfile(Long studentProfileId) {
        StudentProfile student = studentProfileRepository.findById(studentProfileId)
                .orElseThrow(() -> new IllegalArgumentException("学生画像不存在: " + studentProfileId));
        List<StudentProfile> mergedProfiles = findMergedProfiles(student);
        List<Long> profileIds = mergedProfiles.stream()
                .map(StudentProfile::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Submission> submissions = profileIds.isEmpty()
                ? List.of()
                : submissionRepository.findByStudentProfileIdInOrderBySubmittedAtDesc(profileIds)
                .stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(RECENT_WINDOW)
                .toList();
        List<Long> submissionIds = submissions.stream()
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SubmissionAnalysis> analyses = submissionIds.isEmpty()
                ? Map.of()
                : submissionAnalysisRepository.findBySubmissionIdIn(submissionIds)
                .stream()
                .collect(Collectors.toMap(SubmissionAnalysis::getSubmissionId, Function.identity(), (left, right) -> left));
        Map<Long, Problem> problems = problemRepository.findAllById(submissions.stream()
                        .map(Submission::getProblemId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        Map<Long, CoachInteractionSummaryResponse> coachInteractions = coachInteractionAnalyzer.summarize(submissionIds);
        Map<Long, com.onlinejudge.classroom.dto.CoachImpactResponse> coachImpacts =
                coachImpactAnalyzer.summarizeByCoachedSubmission(submissions, analyses, coachInteractionAnalyzer.findPrompts(submissionIds));
        com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse latestCoachInteraction =
                coachInteractionAnalyzer.latestForOrderedSubmissions(submissionIds, coachInteractions);
        if (latestCoachInteraction != null) {
            latestCoachInteraction.setImpact(coachImpacts.get(latestCoachInteraction.getSubmissionId()));
        }
        com.onlinejudge.classroom.dto.CoachImpactResponse latestCoachImpact =
                coachImpactAnalyzer.latestForOrderedSubmissions(submissionIds, coachImpacts);
        List<StudentRecommendationEvent> recommendationEvents = profileIds.stream()
                .filter(Objects::nonNull)
                .flatMap(profileId -> recommendationEventRepository.findByStudentProfileIdOrderByCreatedAtDesc(profileId).stream())
                .toList();
        List<com.onlinejudge.classroom.domain.CoachPrompt> coachPrompts = coachInteractionAnalyzer.findPrompts(submissionIds);
        List<AbilitySignalAnalyzer.AbilitySignal> abilitySignals = abilitySignalAnalyzer.summarize(submissions, analyses);
        List<StudentAbilityProfileResponse.AbilityStat> abilityGaps = abilitySignals.stream()
                .map(signal -> StudentAbilityProfileResponse.AbilityStat.builder()
                        .abilityPoint(signal.getAbilityPoint())
                        .taskCount(signal.getTaskCount())
                        .submissionCount(signal.getSubmissionCount())
                        .evidenceTags(signal.getEvidenceTags())
                        .build())
                .toList();
        StudentAbilityProfileResponse.RecurringMisconceptionSignal recurringMisconceptionSignal =
                recurringMisconceptionAnalyzer.analyze(submissions, analyses);
        StudentAbilityProfileResponse.SelfExplanationMasterySignal selfExplanationMasterySignal =
                selfExplanationMasteryAnalyzer.analyze(coachPrompts);
        StudentAbilityProfileResponse.AiDependencySignal aiDependencySignal =
                aiDependencyAnalyzer.analyze(submissions, coachPrompts, recommendationEvents);
        StudentAbilityProfileResponse.MasteryGrowthSignal masteryGrowthSignal =
                masteryGrowthAnalyzer.analyze(submissions, analyses);
        String trendSignal = buildTrendSignal(submissions, abilitySignals);
        StudentAbilityProfileResponse.TeachingActionDecision teachingActionDecision =
                teachingActionOrchestrator.decide(
                        null,
                        null,
                        null,
                        recurringMisconceptionSignal,
                        selfExplanationMasterySignal,
                        aiDependencySignal,
                        masteryGrowthSignal,
                        trendSignal
                );

        return StudentAbilityProfileResponse.builder()
                .student(StudentProfileResponse.from(student))
                .mergedStudentProfileIds(profileIds)
                .totalSubmissions(submissions.size())
                .failedSubmissionCount(submissions.stream().filter(submission -> submission.getVerdict() != Submission.Verdict.ACCEPTED).count())
                .problemCount(submissions.stream().map(Submission::getProblemId).filter(Objects::nonNull).distinct().count())
                .assignmentCount(submissions.stream().map(Submission::getAssignmentId).filter(Objects::nonNull).distinct().count())
                .primaryAbilityFocus(abilitySignals.stream()
                        .findFirst()
                        .map(AbilitySignalAnalyzer.AbilitySignal::getAbilityPoint)
                .orElse(null))
                .summary(buildSummary(submissions, abilitySignals))
                .trendSignal(trendSignal)
                .recommendationEffectSummary(buildRecommendationEffectSummary(profileIds))
                .coachImpactSummary(buildCoachImpactSummary(coachImpacts))
                .recurringMisconceptionSignal(recurringMisconceptionSignal)
                .selfExplanationMasterySignal(selfExplanationMasterySignal)
                .aiDependencySignal(aiDependencySignal)
                .masteryGrowthSignal(masteryGrowthSignal)
                .teachingActionDecision(teachingActionDecision)
                .latestCoachInteraction(latestCoachInteraction)
                .latestCoachImpact(latestCoachImpact)
                .abilityGaps(abilityGaps)
                .knowledgeFocus(summarizeProblemTags(submissions, problems, Problem::getKnowledgePoints))
                .commonMistakeFocus(summarizeProblemTags(submissions, problems, Problem::getCommonMistakes))
                .boundaryFocus(summarizeProblemTags(submissions, problems, Problem::getBoundaryTypes))
                .fineGrainedProfile(buildFineGrainedProfile(submissions, analyses))
                .reviewCards(buildReviewCards(submissions, analyses, problems))
                .build();
    }

    public record StudentScope(StudentProfileResponse student, List<Long> profileIds) {
    }

    private List<StudentProfile> findMergedProfiles(StudentProfile student) {
        LinkedHashMap<Long, StudentProfile> merged = new LinkedHashMap<>();
        addProfile(merged, student);
        if (student.getClassGroupId() == null) {
            return List.copyOf(merged.values());
        }
        if (studentIdentityService.isManualIdentityKey(student.getIdentityKey())) {
            addProfiles(merged, studentProfileRepository.findByIdentityKeyOrderByCreatedAtDesc(student.getIdentityKey()));
            return List.copyOf(merged.values());
        }
        String stableIdentityKey = studentIdentityService.buildStableIdentityKey(
                student.getClassGroupId(),
                null,
                student.getDisplayName(),
                student.getStudentNo()
        );
        if (!stableIdentityKey.isBlank()) {
            addProfiles(merged, studentProfileRepository.findByIdentityKeyOrderByCreatedAtDesc(stableIdentityKey));
        }
        if (studentIdentityService.isStableIdentityKey(student.getIdentityKey())) {
            addProfiles(merged, studentProfileRepository.findByIdentityKeyOrderByCreatedAtDesc(student.getIdentityKey()));
        }
        String studentNo = normalize(student.getStudentNo());
        if (!studentNo.isBlank()) {
            addHeuristicProfiles(merged, studentProfileRepository
                    .findByClassGroupIdAndStudentNoIgnoreCaseOrderByCreatedAtDesc(student.getClassGroupId(), studentNo));
            return List.copyOf(merged.values());
        }
        String displayName = normalize(student.getDisplayName());
        if (!displayName.isBlank()) {
            addHeuristicProfiles(merged, studentProfileRepository
                    .findByClassGroupIdAndDisplayNameIgnoreCaseOrderByCreatedAtDesc(student.getClassGroupId(), displayName));
        }
        return List.copyOf(merged.values());
    }

    private void addHeuristicProfiles(Map<Long, StudentProfile> merged, List<StudentProfile> candidates) {
        if (candidates == null) {
            return;
        }
        candidates.stream()
                .filter(candidate -> !studentIdentityService.isManualIdentityKey(candidate.getIdentityKey()))
                .forEach(candidate -> addProfile(merged, candidate));
    }

    private void addProfiles(Map<Long, StudentProfile> merged, List<StudentProfile> candidates) {
        if (candidates == null) {
            return;
        }
        candidates.forEach(candidate -> addProfile(merged, candidate));
    }

    private void addProfile(Map<Long, StudentProfile> merged, StudentProfile profile) {
        if (profile != null && profile.getId() != null) {
            merged.putIfAbsent(profile.getId(), profile);
        }
    }

    private List<StudentAbilityProfileResponse.ProfileStat> summarizeProblemTags(
            List<Submission> submissions,
            Map<Long, Problem> problems,
            Function<Problem, List<String>> extractor) {
        if (submissions == null || submissions.isEmpty() || problems == null || problems.isEmpty()) {
            return List.of();
        }
        boolean hasFailed = submissions.stream().anyMatch(submission -> submission.getVerdict() != Submission.Verdict.ACCEPTED);
        Map<String, TagAccumulator> accumulators = new LinkedHashMap<>();
        submissions.stream()
                .filter(submission -> !hasFailed || submission.getVerdict() != Submission.Verdict.ACCEPTED)
                .limit(RECENT_WINDOW)
                .forEach(submission -> {
                    Problem problem = problems.get(submission.getProblemId());
                    if (problem == null) {
                        return;
                    }
                    List<String> tags = extractor.apply(problem);
                    if (tags == null) {
                        return;
                    }
                    tags.stream()
                            .map(this::normalize)
                            .filter(value -> !value.isBlank())
                            .distinct()
                            .forEach(value -> {
                                TagAccumulator accumulator = accumulators.computeIfAbsent(value, TagAccumulator::new);
                                accumulator.count++;
                                if (submission.getProblemId() != null) {
                                    accumulator.problemIds.add(submission.getProblemId());
                                }
                            });
                });
        return accumulators.values()
                .stream()
                .sorted(Comparator.comparing(TagAccumulator::getCount).reversed()
                        .thenComparing(TagAccumulator::getLabel))
                .limit(5)
                .map(TagAccumulator::toStat)
                .toList();
    }

    private String buildSummary(List<Submission> submissions, List<AbilitySignalAnalyzer.AbilitySignal> abilitySignals) {
        if (submissions == null || submissions.isEmpty()) {
            return "还没有足够的提交证据，完成一次提交后会开始形成长期能力画像。";
        }
        if (abilitySignals != null && !abilitySignals.isEmpty()) {
            AbilitySignalAnalyzer.AbilitySignal top = abilitySignals.get(0);
            if (top.getTaskCount() >= 2) {
                return "跨作业主要集中在：" + top.getAbilityPoint() + "。建议先对比这些题的共同判断步骤。";
            }
            return "近期主要关注：" + top.getAbilityPoint() + "。继续观察下一次提交是否改变这个信号。";
        }
        long accepted = submissions.stream().filter(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED).count();
        if (accepted > 0) {
            return "近期已有通过记录，可以进入复盘：讲清边界、复杂度和可迁移思路。";
        }
        return "提交证据还比较分散，建议先围绕最近一次失败样例做最小化验证。";
    }

    private String buildTrendSignal(List<Submission> submissions, List<AbilitySignalAnalyzer.AbilitySignal> abilitySignals) {
        if (submissions == null || submissions.isEmpty()) {
            return "暂无趋势";
        }
        Submission latest = submissions.get(0);
        if (latest.getVerdict() == Submission.Verdict.ACCEPTED) {
            return "最近一次提交已通过，可以观察是否能解释边界与复杂度。";
        }
        if (submissions.size() >= 3 && submissions.stream().limit(3).noneMatch(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)) {
            return "最近 3 次仍未通过，建议教师或 AI 教练先收窄到一个最小失败样例。";
        }
        if (abilitySignals != null && !abilitySignals.isEmpty() && abilitySignals.get(0).getSubmissionCount() >= 2) {
            return "同一能力点多次出现，下一轮应验证是否真正理解，而不是只改代码。";
        }
        return "趋势还在形成中，继续用下一次提交补充证据。";
    }

    private StudentAbilityProfileResponse.FineGrainedLearningProfile buildFineGrainedProfile(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses) {
        List<Submission> source = profileEvidenceSubmissions(submissions);
        List<StudentAbilityProfileResponse.ProfileStat> issueTags =
                summarizeAnalysisTags(source, analyses, diagnosisReportReader::issueTags);
        List<StudentAbilityProfileResponse.ProfileStat> fineTags =
                summarizeAnalysisTags(source, analyses, diagnosisReportReader::fineGrainedTags);
        List<StudentAbilityProfileResponse.ProfileStat> abilityPoints =
                summarizeAnalysisTags(source, analyses, this::abilityPoints);
        List<StudentAbilityProfileResponse.ProfileStat> focusPoints =
                summarizeAnalysisTags(source, analyses, this::focusPoints);
        return StudentAbilityProfileResponse.FineGrainedLearningProfile.builder()
                .issueTagFocus(issueTags)
                .fineGrainedTagFocus(fineTags)
                .abilityPointFocus(abilityPoints)
                .focusPointFocus(focusPoints)
                .aiContextSummary(buildAiContextSummary(issueTags, fineTags, abilityPoints))
                .build();
    }

    private List<StudentAbilityProfileResponse.ReviewCard> buildReviewCards(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            Map<Long, Problem> problems) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        return submissions.stream()
                .filter(submission -> submission.getVerdict() != Submission.Verdict.ACCEPTED)
                .map(submission -> toReviewCard(submission, analyses.get(submission.getId()), problems.get(submission.getProblemId())))
                .filter(Objects::nonNull)
                .limit(REVIEW_CARD_LIMIT)
                .toList();
    }

    private StudentAbilityProfileResponse.ReviewCard toReviewCard(Submission submission,
                                                                  SubmissionAnalysis analysis,
                                                                  Problem problem) {
        if (submission == null || analysis == null) {
            return null;
        }
        List<String> issueTags = diagnosisReportReader.issueTags(analysis);
        List<String> fineTags = diagnosisReportReader.fineGrainedTags(analysis);
        DiagnosisReportReader.StudentHintPlanSnapshot hintPlan = diagnosisReportReader.studentHintPlan(analysis);
        List<String> abilityPoints = abilityPoints(analysis);
        String nextAction = hintPlan == null ? diagnosisReportReader.studentHint(analysis) : hintPlan.nextAction();
        return StudentAbilityProfileResponse.ReviewCard.builder()
                .submissionId(submission.getId())
                .problemId(submission.getProblemId())
                .problemTitle(problem == null ? "" : problem.getTitle())
                .verdict(submission.getVerdict() == null ? "" : submission.getVerdict().name())
                .primaryIssueTag(first(issueTags))
                .primaryFineGrainedTag(first(fineTags))
                .abilityPoint(first(abilityPoints))
                .summary(defaultIfBlank(analysis.getSummary(), analysis.getHeadline()))
                .nextAction(defaultIfBlank(nextAction, "先复盘本题的第一处失败证据，再写一句下次自查点。"))
                .evidenceRefs(diagnosisReportReader.evidenceRefs(analysis).stream().limit(5).toList())
                .build();
    }

    private List<Submission> profileEvidenceSubmissions(List<Submission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        boolean hasFailed = submissions.stream().anyMatch(submission -> submission.getVerdict() != Submission.Verdict.ACCEPTED);
        return submissions.stream()
                .filter(submission -> !hasFailed || submission.getVerdict() != Submission.Verdict.ACCEPTED)
                .limit(RECENT_WINDOW)
                .toList();
    }

    private List<StudentAbilityProfileResponse.ProfileStat> summarizeAnalysisTags(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            Function<SubmissionAnalysis, List<String>> extractor) {
        if (submissions == null || submissions.isEmpty() || analyses == null || analyses.isEmpty()) {
            return List.of();
        }
        Map<String, TagAccumulator> accumulators = new LinkedHashMap<>();
        submissions.forEach(submission -> {
            SubmissionAnalysis analysis = analyses.get(submission.getId());
            if (analysis == null) {
                return;
            }
            List<String> tags = extractor.apply(analysis);
            if (tags == null) {
                return;
            }
            tags.stream()
                    .map(this::normalize)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .forEach(value -> {
                        TagAccumulator accumulator = accumulators.computeIfAbsent(value, TagAccumulator::new);
                        accumulator.count++;
                        if (submission.getProblemId() != null) {
                            accumulator.problemIds.add(submission.getProblemId());
                        }
                    });
        });
        return accumulators.values().stream()
                .sorted(Comparator.comparing(TagAccumulator::getCount).reversed()
                        .thenComparing(TagAccumulator::getLabel))
                .limit(5)
                .map(TagAccumulator::toStat)
                .toList();
    }

    private List<String> abilityPoints(SubmissionAnalysis analysis) {
        return diagnosisReportReader.abilityPoints(analysis);
    }

    private List<String> focusPoints(SubmissionAnalysis analysis) {
        return diagnosisReportReader.focusPoints(analysis);
    }

    private String buildAiContextSummary(List<StudentAbilityProfileResponse.ProfileStat> issueTags,
                                         List<StudentAbilityProfileResponse.ProfileStat> fineTags,
                                         List<StudentAbilityProfileResponse.ProfileStat> abilityPoints) {
        String fine = firstLabel(fineTags);
        String ability = firstLabel(abilityPoints);
        String issue = firstLabel(issueTags);
        if (!fine.isBlank()) {
            return "AI 助手应优先关注细颗粒错因：" + fine + "，结合能力点：" + defaultIfBlank(ability, issue) + "。";
        }
        if (!ability.isBlank()) {
            return "AI 助手应优先关注能力点：" + ability + "，结合最近提交证据提问。";
        }
        return "画像证据仍少，AI 助手应先围绕最近一次提交做最小失败样例追问。";
    }

    private String firstLabel(List<StudentAbilityProfileResponse.ProfileStat> stats) {
        return stats == null || stats.isEmpty() ? "" : defaultIfBlank(stats.get(0).getLabel(), "");
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? "" : defaultIfBlank(values.get(0), "");
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String buildRecommendationEffectSummary(List<Long> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return null;
        }
        List<StudentRecommendationEvent> events = profileIds.stream()
                .filter(Objects::nonNull)
                .flatMap(profileId -> recommendationEventRepository.findByStudentProfileIdOrderByCreatedAtDesc(profileId).stream())
                .filter(Objects::nonNull)
                .toList();
        if (events.isEmpty()) {
            return null;
        }
        long exposed = countEvents(events, StudentRecommendationEventService.EVENT_EXPOSED);
        long clicked = countEvents(events, StudentRecommendationEventService.EVENT_CLICKED);
        List<StudentRecommendationEvent> submittedEvents = events.stream()
                .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                .toList();
        long submitted = submittedEvents.size();
        long accepted = submittedEvents.stream()
                .filter(event -> Submission.Verdict.ACCEPTED.name().equals(event.getFollowupVerdict()))
                .count();
        long stillSameFocus = submittedEvents.stream()
                .filter(event -> event.getFocusTags() != null)
                .filter(event -> containsFocusTag(event.getFocusTags(), event.getFollowupFineGrainedTag())
                        || containsFocusTag(event.getFocusTags(), event.getFollowupIssueTag()))
                .count();
        if (accepted > 0) {
            return "推荐后已有 " + accepted + " 次提交通过，继续观察能否迁移到新题。";
        }
        if (submitted > 0 && stillSameFocus > 0) {
            return "推荐后已有 " + submitted + " 次提交，但仍有 " + stillSameFocus + " 次命中同类错因，建议先收窄到最小失败样例。";
        }
        if (submitted > 0) {
            return "推荐后已有 " + submitted + " 次提交，暂未观察到通过，可继续看下一次诊断标签是否变化。";
        }
        if (clicked > 0) {
            return "学生已点击 " + clicked + " 次推荐，还需要一次提交来验证是否有效。";
        }
        if (exposed > 0) {
            return "已生成 " + exposed + " 条推荐，等待学生点击或提交来形成效果证据。";
        }
        return null;
    }

    private String buildCoachImpactSummary(Map<Long, com.onlinejudge.classroom.dto.CoachImpactResponse> coachImpacts) {
        if (coachImpacts == null || coachImpacts.isEmpty()) {
            return null;
        }
        long accepted = coachImpacts.values().stream()
                .filter(impact -> "FOLLOWUP_ACCEPTED".equals(impact.getStatus()))
                .count();
        long shifted = coachImpacts.values().stream()
                .filter(impact -> "ISSUE_SHIFTED".equals(impact.getStatus()) || "VERDICT_CHANGED".equals(impact.getStatus()))
                .count();
        long same = coachImpacts.values().stream()
                .filter(impact -> "SAME_ISSUE".equals(impact.getStatus()))
                .count();
        long awaiting = coachImpacts.values().stream()
                .filter(impact -> "AWAITING_FOLLOWUP".equals(impact.getStatus()))
                .count();
        if (accepted > 0) {
            return "AI 追问后已有 " + accepted + " 次同题后续提交通过，建议复盘学生回答中有效的证据意识。";
        }
        if (shifted > 0) {
            return "AI 追问后已有 " + shifted + " 次后续提交出现错因或评测阶段变化，说明追问可能推动了问题进入新阶段。";
        }
        if (same > 0) {
            return "AI 追问后仍有 " + same + " 次同题后续提交卡在同类问题，下一轮需要更小样例或教师介入。";
        }
        if (awaiting > 0) {
            return "学生已回答 AI 追问，但还缺少同题后续提交来判断效果。";
        }
        return null;
    }

    private long countEvents(List<StudentRecommendationEvent> events, String eventType) {
        return events.stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .count();
    }

    private boolean containsFocusTag(String focusTags, String tag) {
        return focusTags != null && tag != null && !tag.isBlank() && focusTags.contains(tag);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static class TagAccumulator {
        private final String label;
        private long count;
        private final Set<Long> problemIds = new LinkedHashSet<>();

        private TagAccumulator(String label) {
            this.label = label;
        }

        private long getCount() {
            return count;
        }

        private String getLabel() {
            return label;
        }

        private StudentAbilityProfileResponse.ProfileStat toStat() {
            return StudentAbilityProfileResponse.ProfileStat.builder()
                    .label(label)
                    .count(count)
                    .evidenceProblemIds(problemIds.stream().limit(5).toList())
                    .build();
        }
    }
}
