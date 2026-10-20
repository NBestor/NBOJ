package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import com.onlinejudge.classroom.dto.AssignmentOverviewResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class TeacherInterventionImpactAnalyzer {

    public static final String STATUS_NO_FEEDBACK = "NO_FEEDBACK";
    public static final String STATUS_DISMISSED = "DISMISSED";
    public static final String STATUS_WAITING_FOLLOWUP = "WAITING_FOLLOWUP";
    public static final String STATUS_IMPROVED = "IMPROVED";
    public static final String STATUS_SHIFTED = "SHIFTED";
    public static final String STATUS_STILL_STUCK = "STILL_STUCK";

    private final DiagnosisReportReader diagnosisReportReader;

    public AssignmentOverviewResponse.TeacherInterventionImpact analyze(
            AssignmentOverviewResponse.ClassReviewSuggestion suggestion,
            ClassReviewFeedback feedback,
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses) {
        if (feedback == null) {
            return impact(
                    STATUS_NO_FEEDBACK,
                    "待教师反馈",
                    "教师尚未采纳、调整或忽略这条复盘建议。",
                    "先由教师判断这条建议是否适合作为课堂复盘动作。",
                    false,
                    null,
                    null,
                    null,
                    null,
                    evidenceSubmissionIds(suggestion),
                    List.of()
            );
        }
        String actionType = normalize(feedback.getActionType());
        if (ClassReviewFeedbackService.ACTION_DISMISSED.equals(actionType)) {
            return impact(
                    STATUS_DISMISSED,
                    "已忽略",
                    "教师已忽略这条 AI 复盘建议，不计入介入成效评估。",
                    "保留教师判断；后续可重新生成更贴合课堂证据的建议。",
                    false,
                    actionType,
                    feedback.getCreatedAt(),
                    null,
                    null,
                    evidenceSubmissionIds(suggestion),
                    List.of()
            );
        }
        if (!ClassReviewFeedbackService.ACTION_ACCEPTED.equals(actionType)
                && !ClassReviewFeedbackService.ACTION_MODIFIED.equals(actionType)) {
            return waitingImpact(actionType, feedback.getCreatedAt(), null, null, evidenceSubmissionIds(suggestion),
                    "教师反馈动作暂不能判断为已执行介入。");
        }
        return analyzeExecutedFeedback(
                actionType,
                feedback.getCreatedAt(),
                feedback.getExampleProblemId(),
                evidenceSubmissionIds(suggestion),
                evidenceTags(suggestion),
                submissions,
                analyses
        );
    }

    public List<AssignmentOverviewResponse.TeacherInterventionImpact> analyzeFeedbacks(
            List<ClassReviewFeedback> feedbacks,
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            Function<ClassReviewFeedback, List<String>> evidenceTagResolver) {
        if (feedbacks == null || feedbacks.isEmpty()) {
            return List.of();
        }
        return feedbacks.stream()
                .map(feedback -> analyzeExecutedOrDismissedFeedback(
                        feedback,
                        submissions,
                        analyses,
                        evidenceTagResolver == null ? List.of() : evidenceTagResolver.apply(feedback)))
                .toList();
    }

    public Summary summarize(List<AssignmentOverviewResponse.TeacherInterventionImpact> impacts) {
        if (impacts == null || impacts.isEmpty()) {
            return Summary.builder().build();
        }
        long executedCount = 0;
        long waitingFollowupCount = 0;
        long improvedCount = 0;
        long shiftedCount = 0;
        long stillStuckCount = 0;
        long escalationCount = 0;
        for (AssignmentOverviewResponse.TeacherInterventionImpact impact : impacts) {
            if (impact == null) {
                continue;
            }
            switch (normalize(impact.getStatus())) {
                case STATUS_WAITING_FOLLOWUP -> waitingFollowupCount++;
                case STATUS_IMPROVED -> improvedCount++;
                case STATUS_SHIFTED -> shiftedCount++;
                case STATUS_STILL_STUCK -> stillStuckCount++;
                default -> {
                }
            }
            if (ClassReviewFeedbackService.ACTION_ACCEPTED.equals(normalize(impact.getFeedbackActionType()))
                    || ClassReviewFeedbackService.ACTION_MODIFIED.equals(normalize(impact.getFeedbackActionType()))) {
                executedCount++;
            }
            if (impact.isNeedsEscalation()) {
                escalationCount++;
            }
        }
        return Summary.builder()
                .executedCount(executedCount)
                .waitingFollowupCount(waitingFollowupCount)
                .improvedCount(improvedCount)
                .shiftedCount(shiftedCount)
                .stillStuckCount(stillStuckCount)
                .escalationCount(escalationCount)
                .build();
    }

    private AssignmentOverviewResponse.TeacherInterventionImpact analyzeExecutedOrDismissedFeedback(
            ClassReviewFeedback feedback,
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            List<String> evidenceTags) {
        if (feedback == null) {
            return null;
        }
        String actionType = normalize(feedback.getActionType());
        if (ClassReviewFeedbackService.ACTION_DISMISSED.equals(actionType)) {
            return impact(
                    STATUS_DISMISSED,
                    "已忽略",
                    "教师已忽略这条 AI 复盘建议，不计入介入成效评估。",
                    "保留教师判断；后续可重新生成更贴合课堂证据的建议。",
                    false,
                    actionType,
                    feedback.getCreatedAt(),
                    null,
                    null,
                    List.of(),
                    List.of()
            );
        }
        if (!ClassReviewFeedbackService.ACTION_ACCEPTED.equals(actionType)
                && !ClassReviewFeedbackService.ACTION_MODIFIED.equals(actionType)) {
            return null;
        }
        return analyzeExecutedFeedback(
                actionType,
                feedback.getCreatedAt(),
                feedback.getExampleProblemId(),
                List.of(),
                evidenceTags,
                submissions == null ? List.of() : submissions.stream()
                        .filter(submission -> Objects.equals(submission.getAssignmentId(), feedback.getAssignmentId()))
                        .toList(),
                analyses
        );
    }

    private AssignmentOverviewResponse.TeacherInterventionImpact analyzeExecutedFeedback(
            String actionType,
            LocalDateTime feedbackAt,
            Long exampleProblemId,
            List<Long> evidenceSubmissionIds,
            List<String> evidenceTags,
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses) {
        List<Submission> followups = relevantFollowups(submissions, feedbackAt, exampleProblemId);
        if (followups.isEmpty()) {
            return waitingImpact(actionType, feedbackAt, null, null, evidenceSubmissionIds,
                    "教师已执行复盘动作，但还没有观察到相关后续提交。");
        }
        Submission latest = followups.get(0);
        SubmissionAnalysis analysis = analyses == null ? null : analyses.get(latest.getId());
        if (latest.getVerdict() == Submission.Verdict.ACCEPTED) {
            return impact(
                    STATUS_IMPROVED,
                    "已有改善",
                    "教师介入后，相关后续提交已经通过，可进入复盘迁移。",
                    "让学生复述这次修正的关键证据，并迁移到一个新样例。",
                    false,
                    actionType,
                    feedbackAt,
                    latest.getId(),
                    verdict(latest),
                    evidenceSubmissionIds,
                    matchedTags(analysis, evidenceTags)
            );
        }
        if (analysis == null) {
            return waitingImpact(actionType, feedbackAt, latest.getId(), verdict(latest), evidenceSubmissionIds,
                    "已经出现后续提交，但诊断分析尚未生成，暂不能判断介入成效。");
        }
        List<String> matchedTags = matchedTags(analysis, evidenceTags);
        if (!matchedTags.isEmpty()) {
            return impact(
                    STATUS_STILL_STUCK,
                    "仍卡同类错因",
                    "教师介入后，后续提交仍命中原复盘证据标签。",
                    "升级为教师点对点检查，要求学生补一个更小失败样例或变量轨迹。",
                    true,
                    actionType,
                    feedbackAt,
                    latest.getId(),
                    verdict(latest),
                    evidenceSubmissionIds,
                    matchedTags
            );
        }
        return impact(
                STATUS_SHIFTED,
                "错因已转移",
                "教师介入后，后续提交不再命中原复盘标签，但仍未通过。",
                "围绕新的错因重新收集证据，避免继续重复原复盘动作。",
                false,
                actionType,
                feedbackAt,
                latest.getId(),
                verdict(latest),
                evidenceSubmissionIds,
                mergedTags(analysis)
        );
    }

    private AssignmentOverviewResponse.TeacherInterventionImpact waitingImpact(String actionType,
                                                                               LocalDateTime feedbackAt,
                                                                               Long followupSubmissionId,
                                                                               String followupVerdict,
                                                                               List<Long> evidenceSubmissionIds,
                                                                               String summary) {
        return impact(
                STATUS_WAITING_FOLLOWUP,
                "等待后续证据",
                summary,
                "等待学生相关后续提交，或请教师补充课堂执行后的观察记录。",
                false,
                actionType,
                feedbackAt,
                followupSubmissionId,
                followupVerdict,
                evidenceSubmissionIds,
                List.of()
        );
    }

    private AssignmentOverviewResponse.TeacherInterventionImpact impact(String status,
                                                                        String statusLabel,
                                                                        String summary,
                                                                        String recommendedAction,
                                                                        boolean needsEscalation,
                                                                        String feedbackActionType,
                                                                        LocalDateTime feedbackAt,
                                                                        Long followupSubmissionId,
                                                                        String followupVerdict,
                                                                        List<Long> evidenceSubmissionIds,
                                                                        List<String> matchedTags) {
        return AssignmentOverviewResponse.TeacherInterventionImpact.builder()
                .status(status)
                .statusLabel(statusLabel)
                .summary(summary)
                .recommendedAction(recommendedAction)
                .needsEscalation(needsEscalation)
                .feedbackActionType(feedbackActionType)
                .feedbackAt(feedbackAt)
                .followupSubmissionId(followupSubmissionId)
                .followupVerdict(followupVerdict)
                .evidenceSubmissionIds(evidenceSubmissionIds == null ? List.of() : evidenceSubmissionIds)
                .matchedTags(matchedTags == null ? List.of() : matchedTags.stream().distinct().limit(6).toList())
                .build();
    }

    private List<Submission> relevantFollowups(List<Submission> submissions, LocalDateTime feedbackAt, Long exampleProblemId) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        return submissions.stream()
                .filter(Objects::nonNull)
                .filter(submission -> exampleProblemId == null || Objects.equals(submission.getProblemId(), exampleProblemId))
                .filter(submission -> feedbackAt == null || isAfter(submission.getSubmittedAt(), feedbackAt))
                .sorted(Comparator
                        .comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(Submission::getId, Comparator.nullsLast(Long::compareTo))
                        .reversed())
                .toList();
    }

    private boolean isAfter(LocalDateTime value, LocalDateTime anchor) {
        return value != null && value.isAfter(anchor);
    }

    private List<Long> evidenceSubmissionIds(AssignmentOverviewResponse.ClassReviewSuggestion suggestion) {
        return suggestion == null || suggestion.getEvidenceSubmissionIds() == null
                ? List.of()
                : suggestion.getEvidenceSubmissionIds();
    }

    private List<String> evidenceTags(AssignmentOverviewResponse.ClassReviewSuggestion suggestion) {
        return suggestion == null || suggestion.getEvidenceTags() == null
                ? List.of()
                : suggestion.getEvidenceTags();
    }

    private List<String> matchedTags(SubmissionAnalysis analysis, List<String> expectedTags) {
        List<String> tags = mergedTags(analysis);
        if (tags.isEmpty() || expectedTags == null || expectedTags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> expected = expectedTags.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return tags.stream()
                .filter(expected::contains)
                .toList();
    }

    private List<String> mergedTags(SubmissionAnalysis analysis) {
        if (analysis == null) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        diagnosisReportReader.fineGrainedTags(analysis).forEach(tags::add);
        diagnosisReportReader.issueTags(analysis).forEach(tags::add);
        return new ArrayList<>(tags);
    }

    private String verdict(Submission submission) {
        return submission == null || submission.getVerdict() == null ? null : submission.getVerdict().name();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    @Data
    @Builder
    public static class Summary {
        @Builder.Default
        private long executedCount = 0;
        @Builder.Default
        private long waitingFollowupCount = 0;
        @Builder.Default
        private long improvedCount = 0;
        @Builder.Default
        private long shiftedCount = 0;
        @Builder.Default
        private long stillStuckCount = 0;
        @Builder.Default
        private long escalationCount = 0;
    }
}
