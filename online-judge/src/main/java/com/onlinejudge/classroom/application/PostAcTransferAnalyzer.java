package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostAcTransferAnalyzer {

    public static final String PHASE_NOT_ACCEPTED = "NOT_ACCEPTED";
    public static final String PHASE_JUST_ACCEPTED = "JUST_ACCEPTED";
    public static final String PHASE_REFLECTION_NEEDED = "REFLECTION_NEEDED";
    public static final String PHASE_REFLECTION_EVIDENCED = "REFLECTION_EVIDENCED";
    public static final String PHASE_TRANSFER_READY = "TRANSFER_READY";
    public static final String PHASE_TRANSFER_VERIFIED = "TRANSFER_VERIFIED";

    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;

    public Map<Long, StudentTrajectoryResponse.PostAcTransferSignal> analyzeTasks(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            Map<Long, CoachInteractionSummaryResponse> coachInteractions,
            Map<Long, Problem> problems) {
        return analyzeTasks(submissions, analyses, coachInteractions, problems, List.of());
    }

    public Map<Long, StudentTrajectoryResponse.PostAcTransferSignal> analyzeTasks(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            Map<Long, CoachInteractionSummaryResponse> coachInteractions,
            Map<Long, Problem> problems,
            List<StudentRecommendationEvent> recommendationEvents) {
        List<Submission> safeSubmissions = sortedAsc(submissions);
        Map<Long, SubmissionAnalysis> safeAnalyses = analyses == null ? Map.of() : analyses;
        Map<Long, CoachInteractionSummaryResponse> safeCoachInteractions = coachInteractions == null ? Map.of() : coachInteractions;
        Map<Long, Problem> safeProblems = problems == null ? Map.of() : problems;
        Map<Long, List<Submission>> byProblem = safeSubmissions.stream()
                .filter(submission -> submission.getProblemId() != null)
                .collect(Collectors.groupingBy(Submission::getProblemId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, StudentTrajectoryResponse.PostAcTransferSignal> signals = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Submission>> entry : byProblem.entrySet()) {
            signals.put(entry.getKey(), analyzeProblem(
                    entry.getKey(),
                    entry.getValue(),
                    safeSubmissions,
                    safeAnalyses,
                    safeCoachInteractions,
                    safeProblems,
                    recommendationEvents));
        }
        return signals;
    }

    public StudentTrajectoryResponse.PostAcTransferSignal summarize(
            List<StudentTrajectoryResponse.PostAcTransferSignal> signals) {
        List<StudentTrajectoryResponse.PostAcTransferSignal> candidates = signals == null ? List.of() : signals.stream()
                .filter(Objects::nonNull)
                .filter(signal -> !PHASE_NOT_ACCEPTED.equals(signal.getPhase()))
                .toList();
        if (candidates.isEmpty()) {
            return StudentTrajectoryResponse.PostAcTransferSignal.builder()
                    .phase(PHASE_NOT_ACCEPTED)
                    .label(label(PHASE_NOT_ACCEPTED))
                    .summary("还没有通过题目，暂不进入 AC 后复盘迁移。")
                    .evidenceRefs(List.of())
                    .recommendedAction("先完成一次通过提交，再做复盘迁移。")
                    .targetTags(List.of())
                    .needsTeacherAttention(false)
                    .build();
        }
        StudentTrajectoryResponse.PostAcTransferSignal selected = candidates.stream()
                .sorted(Comparator
                        .comparingInt((StudentTrajectoryResponse.PostAcTransferSignal signal) -> phaseUrgency(signal.getPhase()))
                        .reversed()
                        .thenComparing(signal -> signal.getProblemId() == null ? Long.MAX_VALUE : signal.getProblemId()))
                .findFirst()
                .orElse(candidates.get(0));
        long pendingCount = candidates.stream().filter(this::isPending).count();
        long verifiedCount = candidates.stream().filter(signal -> PHASE_TRANSFER_VERIFIED.equals(signal.getPhase())).count();
        if (pendingCount <= 1 && verifiedCount == 0) {
            return selected;
        }
        return StudentTrajectoryResponse.PostAcTransferSignal.builder()
                .phase(selected.getPhase())
                .label(selected.getLabel())
                .summary("共有 " + pendingCount + " 个已通过任务仍缺复盘迁移证据，"
                        + verifiedCount + " 个任务已形成迁移验证。当前优先处理："
                        + safeText(selected.getProblemTitle(), "最近通过题") + "。")
                .evidenceRefs(selected.getEvidenceRefs())
                .recommendedAction(selected.getRecommendedAction())
                .targetAbility(selected.getTargetAbility())
                .targetTags(selected.getTargetTags())
                .problemId(selected.getProblemId())
                .problemTitle(selected.getProblemTitle())
                .needsTeacherAttention(selected.isNeedsTeacherAttention())
                .build();
    }

    public boolean isPending(StudentTrajectoryResponse.PostAcTransferSignal signal) {
        return signal != null && (PHASE_JUST_ACCEPTED.equals(signal.getPhase())
                || PHASE_REFLECTION_NEEDED.equals(signal.getPhase()));
    }

    private StudentTrajectoryResponse.PostAcTransferSignal analyzeProblem(
            Long problemId,
            List<Submission> problemSubmissions,
            List<Submission> allSubmissionsAsc,
            Map<Long, SubmissionAnalysis> analyses,
            Map<Long, CoachInteractionSummaryResponse> coachInteractions,
            Map<Long, Problem> problems,
            List<StudentRecommendationEvent> recommendationEvents) {
        List<Submission> ordered = sortedAsc(problemSubmissions);
        Submission accepted = ordered.stream()
                .filter(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)
                .findFirst()
                .orElse(null);
        Problem problem = problems.get(problemId);
        if (accepted == null) {
            return StudentTrajectoryResponse.PostAcTransferSignal.builder()
                    .phase(PHASE_NOT_ACCEPTED)
                    .label(label(PHASE_NOT_ACCEPTED))
                    .summary("该任务尚未通过，先完成调试闭环。")
                    .evidenceRefs(evidenceRefs(problemId, null, List.of()))
                    .recommendedAction("继续根据最新诊断缩小失败样例。")
                    .targetAbility(targetAbility(problem, ordered, analyses))
                    .targetTags(targetTags(problem, ordered, analyses))
                    .problemId(problemId)
                    .problemTitle(problem == null ? null : problem.getTitle())
                    .needsTeacherAttention(false)
                    .build();
        }

        List<String> refs = evidenceRefs(problemId, accepted, List.of("submission:" + accepted.getId()));
        Evidence coachEvidence = coachEvidence(problemId, accepted, ordered, coachInteractions);
        Evidence recommendationEvidence = recommendationEvidence(problemId, accepted, recommendationEvents);
        Evidence transferEvidence = transferEvidence(problemId, accepted, allSubmissionsAsc, analyses, problems);
        String phase = resolvePhase(accepted, ordered, coachEvidence, recommendationEvidence, transferEvidence);
        List<String> allRefs = mergeRefs(refs, coachEvidence.refs(), recommendationEvidence.refs(), transferEvidence.refs());
        String targetAbility = targetAbility(problem, ordered, analyses);
        List<String> targetTags = targetTags(problem, ordered, analyses);
        return StudentTrajectoryResponse.PostAcTransferSignal.builder()
                .phase(phase)
                .label(label(phase))
                .summary(summary(phase, problem, accepted, coachEvidence, recommendationEvidence, transferEvidence))
                .evidenceRefs(allRefs)
                .recommendedAction(recommendedAction(phase, targetAbility, targetTags))
                .targetAbility(targetAbility)
                .targetTags(targetTags)
                .problemId(problemId)
                .problemTitle(problem == null ? null : problem.getTitle())
                .needsTeacherAttention(PHASE_REFLECTION_NEEDED.equals(phase))
                .build();
    }

    private String resolvePhase(Submission accepted,
                                List<Submission> ordered,
                                Evidence coachEvidence,
                                Evidence recommendationEvidence,
                                Evidence transferEvidence) {
        if (transferEvidence.strong()) {
            return PHASE_TRANSFER_VERIFIED;
        }
        if (coachEvidence.transferReady() || recommendationEvidence.transferReady()) {
            return PHASE_TRANSFER_READY;
        }
        if (coachEvidence.strong() || recommendationEvidence.strong()) {
            return PHASE_REFLECTION_EVIDENCED;
        }
        boolean hadFailureBeforeAc = ordered.stream()
                .filter(submission -> beforeOrSame(submission.getSubmittedAt(), accepted.getSubmittedAt()))
                .anyMatch(submission -> submission.getVerdict() != Submission.Verdict.ACCEPTED);
        return hadFailureBeforeAc ? PHASE_REFLECTION_NEEDED : PHASE_JUST_ACCEPTED;
    }

    private Evidence coachEvidence(Long problemId,
                                   Submission accepted,
                                   List<Submission> ordered,
                                   Map<Long, CoachInteractionSummaryResponse> coachInteractions) {
        if (coachInteractions == null || coachInteractions.isEmpty()) {
            return Evidence.empty();
        }
        return ordered.stream()
                .filter(submission -> Objects.equals(submission.getProblemId(), problemId))
                .filter(submission -> beforeOrSame(submission.getSubmittedAt(), accepted.getSubmittedAt()))
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .map(submission -> coachInteractions.get(submission.getId()))
                .filter(Objects::nonNull)
                .map(this::coachEvidence)
                .filter(Evidence::present)
                .findFirst()
                .orElse(Evidence.empty());
    }

    private Evidence coachEvidence(CoachInteractionSummaryResponse interaction) {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal quality = interaction.getAnswerQualitySignal();
        if (quality == null || !Boolean.TRUE.equals(quality.getVerifiable())) {
            return Evidence.empty();
        }
        String ref = "coach:submission:" + interaction.getSubmissionId() + ":" + safeText(quality.getQualityLevel(), "VERIFIABLE");
        if ("READY_TO_TRANSFER".equals(quality.getActionStatus()) || "TRANSFER_READY".equals(quality.getQualityLevel())) {
            return new Evidence(true, true, true, ref, quality.getSummary());
        }
        return new Evidence(true, true, false, ref, quality.getSummary());
    }

    private Evidence recommendationEvidence(Long problemId,
                                            Submission accepted,
                                            List<StudentRecommendationEvent> recommendationEvents) {
        if (recommendationEvents == null || recommendationEvents.isEmpty()) {
            return Evidence.empty();
        }
        return recommendationEvents.stream()
                .filter(event -> event != null && event.getProblemId() != null && Objects.equals(event.getProblemId(), problemId))
                .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                .filter(event -> Submission.Verdict.ACCEPTED.name().equals(event.getFollowupVerdict()))
                .filter(event -> event.getCreatedAt() == null || accepted.getSubmittedAt() == null
                        || !event.getCreatedAt().isBefore(accepted.getSubmittedAt()))
                .findFirst()
                .map(event -> new Evidence(true, true,
                        "TRANSFER_TO_NEW_PROBLEM".equals(event.getStrategy()),
                        "recommendation:" + event.getRecommendationToken(),
                        "推荐后的后续提交已经通过。"))
                .orElse(Evidence.empty());
    }

    private Evidence transferEvidence(Long problemId,
                                      Submission accepted,
                                      List<Submission> allSubmissionsAsc,
                                      Map<Long, SubmissionAnalysis> analyses,
                                      Map<Long, Problem> problems) {
        String targetAbility = targetAbility(problems.get(problemId),
                allSubmissionsAsc.stream().filter(submission -> Objects.equals(submission.getProblemId(), problemId)).toList(),
                analyses);
        List<String> targetTags = targetTags(problems.get(problemId),
                allSubmissionsAsc.stream().filter(submission -> Objects.equals(submission.getProblemId(), problemId)).toList(),
                analyses);
        if (targetAbility == null && targetTags.isEmpty()) {
            return Evidence.empty();
        }
        return allSubmissionsAsc.stream()
                .filter(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)
                .filter(submission -> !Objects.equals(submission.getProblemId(), problemId))
                .filter(submission -> accepted.getSubmittedAt() == null || submission.getSubmittedAt() == null
                        || submission.getSubmittedAt().isAfter(accepted.getSubmittedAt()))
                .filter(submission -> overlaps(targetAbility, targetTags, problems.get(submission.getProblemId()), analyses.get(submission.getId())))
                .findFirst()
                .map(submission -> new Evidence(true, true, true,
                        "transfer-submission:" + submission.getId(),
                        "通过后又完成了同能力或同标签的新题验证。"))
                .orElse(Evidence.empty());
    }

    private boolean overlaps(String targetAbility,
                             List<String> targetTags,
                             Problem problem,
                             SubmissionAnalysis analysis) {
        Set<String> tags = new LinkedHashSet<>();
        addAll(tags, diagnosisReportReader.issueTags(analysis));
        addAll(tags, diagnosisReportReader.fineGrainedTags(analysis));
        addAll(tags, problemTags(problem));
        if (targetAbility != null && !targetAbility.isBlank()) {
            boolean abilityMatch = tags.stream()
                    .map(diagnosisTaxonomy::get)
                    .filter(Objects::nonNull)
                    .anyMatch(tag -> targetAbility.equals(tag.getAbilityPoint()));
            if (abilityMatch) {
                return true;
            }
        }
        return targetTags != null && targetTags.stream().anyMatch(tags::contains);
    }

    private String targetAbility(Problem problem,
                                 List<Submission> problemSubmissions,
                                 Map<Long, SubmissionAnalysis> analyses) {
        List<String> tags = targetTags(problem, problemSubmissions, analyses);
        for (String tagId : tags) {
            DiagnosisTaxonomy.DiagnosisTag tag = diagnosisTaxonomy.get(tagId);
            if (tag != null && tag.getAbilityPoint() != null && !tag.getAbilityPoint().isBlank()) {
                return tag.getAbilityPoint();
            }
        }
        List<String> problemTags = problemTags(problem);
        return problemTags.isEmpty() ? null : problemTags.get(0);
    }

    private List<String> targetTags(Problem problem,
                                    List<Submission> problemSubmissions,
                                    Map<Long, SubmissionAnalysis> analyses) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        sortedAsc(problemSubmissions).stream()
                .filter(submission -> submission.getVerdict() != Submission.Verdict.ACCEPTED)
                .map(submission -> analyses.get(submission.getId()))
                .filter(Objects::nonNull)
                .forEach(analysis -> {
                    addAll(tags, diagnosisReportReader.fineGrainedTags(analysis));
                    addAll(tags, diagnosisReportReader.issueTags(analysis));
                });
        addAll(tags, problemTags(problem));
        return tags.stream().limit(4).toList();
    }

    private List<String> problemTags(Problem problem) {
        if (problem == null) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addAll(tags, problem.getKnowledgePoints());
        addAll(tags, problem.getCommonMistakes());
        addAll(tags, problem.getBoundaryTypes());
        return tags.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).toList();
    }

    private String summary(String phase,
                           Problem problem,
                           Submission accepted,
                           Evidence coachEvidence,
                           Evidence recommendationEvidence,
                           Evidence transferEvidence) {
        String title = problem == null ? "该题" : "《" + problem.getTitle() + "》";
        return switch (phase) {
            case PHASE_TRANSFER_VERIFIED -> title + " 通过后已经出现迁移验证证据：" + transferEvidence.summary();
            case PHASE_TRANSFER_READY -> title + " 已有可迁移复盘证据，适合进入同能力新题。";
            case PHASE_REFLECTION_EVIDENCED -> title + " 已留下复盘证据，但还需要一次新情境迁移验证。";
            case PHASE_JUST_ACCEPTED -> title + " 已通过，但目前只有 AC 证据，建议补一条复盘说明。";
            case PHASE_REFLECTION_NEEDED -> title + " 已从失败推进到 AC，但缺少复盘或迁移证据，不能仅用通过率判断已经沉淀。";
            default -> title + " 尚未通过，暂不进入通过后迁移。";
        };
    }

    private String recommendedAction(String phase, String targetAbility, List<String> targetTags) {
        String focus = targetAbility != null && !targetAbility.isBlank()
                ? targetAbility
                : (targetTags == null || targetTags.isEmpty() ? "本题关键策略" : diagnosisTaxonomy.label(targetTags.get(0)));
        return switch (phase) {
            case PHASE_TRANSFER_VERIFIED -> "把这次迁移证据沉淀成一句规律：什么时候可以复用 " + focus + "。";
            case PHASE_TRANSFER_READY -> "选择一道同能力新题验证 " + focus + " 是否能迁移。";
            case PHASE_REFLECTION_EVIDENCED -> "用一个不同边界样例验证刚才的复盘是否仍成立。";
            case PHASE_JUST_ACCEPTED, PHASE_REFLECTION_NEEDED -> "写出关键修复、一个边界样例和复杂度判断，再进入下一题。";
            default -> "先完成当前题的最小失败样例和一次通过提交。";
        };
    }

    private String label(String phase) {
        return switch (phase == null ? "" : phase) {
            case PHASE_TRANSFER_VERIFIED -> "迁移已验证";
            case PHASE_TRANSFER_READY -> "可进入迁移";
            case PHASE_REFLECTION_EVIDENCED -> "已有复盘证据";
            case PHASE_REFLECTION_NEEDED -> "通过后待复盘";
            case PHASE_JUST_ACCEPTED -> "刚通过";
            case PHASE_NOT_ACCEPTED -> "未通过";
            default -> "待判断";
        };
    }

    private int phaseUrgency(String phase) {
        return switch (phase == null ? "" : phase) {
            case PHASE_REFLECTION_NEEDED -> 6;
            case PHASE_JUST_ACCEPTED -> 5;
            case PHASE_REFLECTION_EVIDENCED -> 4;
            case PHASE_TRANSFER_READY -> 3;
            case PHASE_TRANSFER_VERIFIED -> 2;
            default -> 1;
        };
    }

    private List<Submission> sortedAsc(List<Submission> submissions) {
        return submissions == null ? List.of() : submissions.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .toList();
    }

    private boolean beforeOrSame(LocalDateTime left, LocalDateTime right) {
        return left == null || right == null || !left.isAfter(right);
    }

    @SafeVarargs
    private List<String> mergeRefs(List<String>... refs) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (List<String> refList : refs) {
            addAll(merged, refList);
        }
        return merged.stream().limit(6).toList();
    }

    private List<String> evidenceRefs(Long problemId, Submission accepted, List<String> refs) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (problemId != null) {
            values.add("problem:" + problemId);
        }
        if (accepted != null && accepted.getId() != null) {
            values.add("accepted-submission:" + accepted.getId());
        }
        addAll(values, refs);
        return values.stream().toList();
    }

    private void addAll(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(target::add);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Evidence(boolean present, boolean strong, boolean transferReady, String ref, String summary) {
        private static Evidence empty() {
            return new Evidence(false, false, false, null, "");
        }

        private List<String> refs() {
            return ref == null || ref.isBlank() ? List.of() : List.of(ref);
        }
    }
}
