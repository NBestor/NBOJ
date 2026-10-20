package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import com.onlinejudge.classroom.dto.AssignmentOverviewResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ClassTeachingStrategyImpactAnalyzer {

    public static final String STATUS_NO_FEEDBACK = "NO_FEEDBACK";
    public static final String STATUS_DISMISSED = "DISMISSED";
    public static final String STATUS_WAITING_FOLLOWUP = "WAITING_FOLLOWUP";
    public static final String STATUS_IMPROVED = "IMPROVED";
    public static final String STATUS_SHIFTED = "SHIFTED";
    public static final String STATUS_STILL_STUCK = "STILL_STUCK";

    private final DiagnosisReportReader diagnosisReportReader;

    public AssignmentOverviewResponse.ClassTeachingStrategyImpact analyze(
            AssignmentOverviewResponse.ClassTeachingStrategySignal signal,
            ClassReviewFeedback feedback,
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            List<String> evidenceTags) {
        if (signal == null) {
            return null;
        }
        if (feedback == null) {
            return impact(
                    STATUS_NO_FEEDBACK,
                    "待教师反馈",
                    "教师尚未确认是否采纳、调整或忽略这条班级教学策略。",
                    "先由教师判断这条策略是否适合当前课堂，并记录采纳、调整或忽略。",
                    false,
                    null,
                    null,
                    null,
                    null,
                    evidenceRefs(signal, null, null),
                    List.of()
            );
        }
        String actionType = normalize(feedback.getActionType());
        if (ClassReviewFeedbackService.ACTION_DISMISSED.equals(actionType)) {
            return impact(
                    STATUS_DISMISSED,
                    "已忽略",
                    "教师已忽略这条班级教学策略，不纳入执行成效判断。",
                    "保留教师判断；后续可用新的课堂证据重新生成策略。",
                    false,
                    actionType,
                    feedback.getCreatedAt(),
                    null,
                    null,
                    evidenceRefs(signal, feedback, null),
                    List.of()
            );
        }
        if (!ClassReviewFeedbackService.ACTION_ACCEPTED.equals(actionType)
                && !ClassReviewFeedbackService.ACTION_MODIFIED.equals(actionType)) {
            return waitingImpact(signal, feedback, actionType, null, null,
                    "教师反馈动作暂不能判断为已执行课堂策略。");
        }
        return analyzeExecutedFeedback(signal, feedback, actionType, submissions, analyses, evidenceTags);
    }

    private AssignmentOverviewResponse.ClassTeachingStrategyImpact analyzeExecutedFeedback(
            AssignmentOverviewResponse.ClassTeachingStrategySignal signal,
            ClassReviewFeedback feedback,
            String actionType,
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            List<String> evidenceTags) {
        List<Submission> followups = relevantFollowups(submissions, feedback.getCreatedAt(), feedback.getExampleProblemId());
        if (followups.isEmpty()) {
            return waitingImpact(signal, feedback, actionType, null, null,
                    "教师已处理班级策略，但还没有观察到反馈后的相关提交。");
        }
        Submission latest = followups.get(0);
        SubmissionAnalysis analysis = analyses == null ? null : analyses.get(latest.getId());
        if (latest.getVerdict() == Submission.Verdict.ACCEPTED) {
            return impact(
                    STATUS_IMPROVED,
                    "已有改善",
                    "班级策略执行后，相关后续提交已经通过，可进入迁移复盘。",
                    "让学生复述这次改进的关键证据，并迁移到一个新样例或同能力题。",
                    false,
                    actionType,
                    feedback.getCreatedAt(),
                    latest.getId(),
                    verdict(latest),
                    evidenceRefs(signal, feedback, latest),
                    matchedTags(analysis, expectedTags(signal, evidenceTags))
            );
        }
        if (analysis == null) {
            return waitingImpact(signal, feedback, actionType, latest.getId(), verdict(latest),
                    "已经出现策略反馈后的提交，但诊断分析尚未生成，暂不能判断策略成效。");
        }
        List<String> matchedTags = matchedTags(analysis, expectedTags(signal, evidenceTags));
        if (!matchedTags.isEmpty()) {
            return impact(
                    STATUS_STILL_STUCK,
                    "仍卡同类问题",
                    "班级策略执行后，后续提交仍命中策略聚焦标签或证据标签。",
                    "升级为更小粒度复盘或教师点对点检查，要求补一个最小失败样例或变量轨迹。",
                    true,
                    actionType,
                    feedback.getCreatedAt(),
                    latest.getId(),
                    verdict(latest),
                    evidenceRefs(signal, feedback, latest),
                    matchedTags
            );
        }
        return impact(
                STATUS_SHIFTED,
                "错因已转移",
                "班级策略执行后，后续提交不再命中原策略标签，但仍未通过。",
                "围绕新的错因重新收集证据，避免继续重复原课堂策略。",
                false,
                actionType,
                feedback.getCreatedAt(),
                latest.getId(),
                verdict(latest),
                evidenceRefs(signal, feedback, latest),
                mergedTags(analysis)
        );
    }

    private AssignmentOverviewResponse.ClassTeachingStrategyImpact waitingImpact(
            AssignmentOverviewResponse.ClassTeachingStrategySignal signal,
            ClassReviewFeedback feedback,
            String actionType,
            Long followupSubmissionId,
            String followupVerdict,
            String summary) {
        return impact(
                STATUS_WAITING_FOLLOWUP,
                "等待后续证据",
                summary,
                "等待学生相关后续提交，或请教师补充课堂执行后的观察记录。",
                false,
                actionType,
                feedback == null ? null : feedback.getCreatedAt(),
                followupSubmissionId,
                followupVerdict,
                evidenceRefs(signal, feedback, followupSubmissionId == null ? null : Submission.builder().id(followupSubmissionId).build()),
                List.of()
        );
    }

    private AssignmentOverviewResponse.ClassTeachingStrategyImpact impact(String status,
                                                                          String statusLabel,
                                                                          String summary,
                                                                          String recommendedAction,
                                                                          boolean needsEscalation,
                                                                          String feedbackActionType,
                                                                          LocalDateTime feedbackAt,
                                                                          Long followupSubmissionId,
                                                                          String followupVerdict,
                                                                          List<String> evidenceRefs,
                                                                          List<String> matchedTags) {
        return AssignmentOverviewResponse.ClassTeachingStrategyImpact.builder()
                .status(status)
                .statusLabel(statusLabel)
                .summary(summary)
                .recommendedAction(recommendedAction)
                .needsEscalation(needsEscalation)
                .feedbackActionType(feedbackActionType)
                .feedbackAt(feedbackAt)
                .followupSubmissionId(followupSubmissionId)
                .followupVerdict(followupVerdict)
                .evidenceRefs(evidenceRefs == null ? List.of() : evidenceRefs.stream().distinct().limit(8).toList())
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

    private List<String> expectedTags(AssignmentOverviewResponse.ClassTeachingStrategySignal signal, List<String> evidenceTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (signal != null) {
            addTag(tags, signal.getFocusTag());
            addTag(tags, signal.getFocusAbility());
            addSourceSignalTags(tags, signal.getSourceSignals());
            addSourceSignalTags(tags, signal.getEvidenceRefs());
        }
        if (evidenceTags != null) {
            evidenceTags.forEach(value -> addTag(tags, value));
        }
        return tags.stream().filter(value -> !value.isBlank()).limit(10).toList();
    }

    private void addSourceSignalTags(LinkedHashSet<String> tags, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addTag(tags, value);
            int splitAt = value == null ? -1 : value.indexOf(':');
            if (splitAt >= 0 && splitAt + 1 < value.length()) {
                addTag(tags, value.substring(splitAt + 1));
            }
        }
    }

    private void addTag(LinkedHashSet<String> tags, String value) {
        if (value != null && !value.isBlank()) {
            tags.add(value.trim());
        }
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

    private List<String> evidenceRefs(AssignmentOverviewResponse.ClassTeachingStrategySignal signal,
                                      ClassReviewFeedback feedback,
                                      Submission followup) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        if (signal != null) {
            addRef(refs, "class_strategy:" + signal.getStrategyKey());
            addRef(refs, "class_strategy_status:" + signal.getStatus());
        }
        if (feedback != null) {
            addRef(refs, "strategy_feedback:" + firstNonBlank(feedback.getId() == null ? null : String.valueOf(feedback.getId()),
                    feedback.getSuggestionKey()));
            addRef(refs, "strategy_feedback_action:" + normalize(feedback.getActionType()));
        }
        if (followup != null && followup.getId() != null) {
            addRef(refs, "followup_submission:" + followup.getId());
        }
        if (signal != null) {
            addRefs(refs, signal.getEvidenceRefs());
            addRefs(refs, signal.getSourceSignals());
        }
        return refs.stream().filter(value -> value != null && !value.isBlank()).limit(8).toList();
    }

    private void addRefs(LinkedHashSet<String> refs, List<String> values) {
        if (values == null) {
            return;
        }
        values.forEach(value -> addRef(refs, value));
    }

    private void addRef(LinkedHashSet<String> refs, String value) {
        if (value != null && !value.isBlank()) {
            refs.add(value.trim());
        }
    }

    private String verdict(Submission submission) {
        return submission == null || submission.getVerdict() == null ? null : submission.getVerdict().name();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
