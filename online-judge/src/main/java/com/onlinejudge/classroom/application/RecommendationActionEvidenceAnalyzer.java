package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.dto.RecommendationEffectivenessResponse;
import com.onlinejudge.submission.domain.Submission;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RecommendationActionEvidenceAnalyzer {

    public static final String OUTCOME_CONTRACT_FULFILLED = "CONTRACT_FULFILLED";
    public static final String OUTCOME_UNRESOLVED_SAME_FOCUS = "UNRESOLVED_SAME_FOCUS";
    public static final String OUTCOME_NO_FOLLOWUP_SUBMISSION = "NO_FOLLOWUP_SUBMISSION";
    public static final String OUTCOME_EXPOSED_ONLY = "EXPOSED_ONLY";
    public static final String OUTCOME_WAITING_DIAGNOSIS = "WAITING_DIAGNOSIS";
    public static final String OUTCOME_TEACHER_INTERVENTION_NEEDED = "TEACHER_INTERVENTION_NEEDED";
    public static final String MATCH_ISSUE_ID = "ISSUE_ID";
    public static final String MATCH_POINT_KEY = "POINT_KEY";
    public static final String MATCH_MISTAKE_POINT = "MISTAKE_POINT";
    public static final String MATCH_SKILL_UNIT = "SKILL_UNIT";
    public static final String MATCH_KNOWLEDGE_NODE = "KNOWLEDGE_NODE";
    public static final String MATCH_TEST_SEMANTIC = "TEST_SEMANTIC";
    public static final String MATCH_LEGACY_TAG = "LEGACY_TAG";
    public static final String MATCH_NONE = "NONE";

    public List<RecommendationEffectivenessResponse.ActionEvidenceSignal> analyze(List<StudentRecommendationEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        Map<String, List<StudentRecommendationEvent>> byToken = events.stream()
                .filter(Objects::nonNull)
                .filter(event -> event.getRecommendationToken() != null && !event.getRecommendationToken().isBlank())
                .collect(Collectors.groupingBy(
                        StudentRecommendationEvent::getRecommendationToken,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return byToken.entrySet()
                .stream()
                .map(entry -> signal(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingInt(this::outcomePriority)
                        .thenComparing(RecommendationEffectivenessResponse.ActionEvidenceSignal::getLastEventAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RecommendationEffectivenessResponse.ActionEvidenceSignal::getRecommendationToken))
                .limit(30)
                .toList();
    }

    public boolean isUnresolved(RecommendationEffectivenessResponse.ActionEvidenceSignal signal) {
        return signal != null
                && (OUTCOME_UNRESOLVED_SAME_FOCUS.equals(signal.getOutcome())
                || OUTCOME_TEACHER_INTERVENTION_NEEDED.equals(signal.getOutcome()));
    }

    private RecommendationEffectivenessResponse.ActionEvidenceSignal signal(String token, List<StudentRecommendationEvent> group) {
        List<StudentRecommendationEvent> safeGroup = group == null ? List.of() : group.stream()
                .filter(Objects::nonNull)
                .toList();
        StudentRecommendationEvent anchor = latest(safeGroup);
        StudentRecommendationEvent submitted = latest(safeGroup.stream()
                .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                .toList());
        boolean clickedOrEntered = safeGroup.stream()
                .anyMatch(event -> StudentRecommendationEventService.EVENT_CLICKED.equals(event.getEventType())
                        || StudentRecommendationEventService.EVENT_ENTERED_PROBLEM.equals(event.getEventType()));
        boolean highRisk = safeGroup.stream().anyMatch(event -> "HIGH".equalsIgnoreCase(nullToBlank(event.getRiskLevel())));
        MatchResult match = match(submitted == null ? anchor : submitted);
        boolean sameFocus = match.matched();
        String outcome = outcome(submitted, clickedOrEntered, highRisk, sameFocus);
        boolean teacherAttention = highRisk && submitted != null
                && !Submission.Verdict.ACCEPTED.name().equals(submitted.getFollowupVerdict());
        if (OUTCOME_TEACHER_INTERVENTION_NEEDED.equals(outcome)) {
            teacherAttention = true;
        }
        return RecommendationEffectivenessResponse.ActionEvidenceSignal.builder()
                .recommendationToken(token)
                .studentProfileId(anchor == null ? null : anchor.getStudentProfileId())
                .assignmentId(anchor == null ? null : anchor.getAssignmentId())
                .problemId(anchor == null ? null : anchor.getProblemId())
                .type(anchor == null ? null : anchor.getType())
                .strategy(anchor == null ? null : anchor.getStrategy())
                .riskLevel(anchor == null ? null : anchor.getRiskLevel())
                .learningHypothesis(anchor == null ? null : anchor.getLearningHypothesis())
                .expectedCompletionSignal(anchor == null ? null : anchor.getExpectedCompletionSignal())
                .outcome(outcome)
                .matchBasis(match.basis())
                .summary(summary(outcome, submitted, sameFocus))
                .recommendedAdjustment(recommendedAdjustment(outcome, teacherAttention))
                .needsTeacherAttention(teacherAttention)
                .followupSubmissionId(submitted == null ? null : submitted.getFollowupSubmissionId())
                .followupVerdict(submitted == null ? null : submitted.getFollowupVerdict())
                .followupIssueTag(submitted == null ? null : submitted.getFollowupIssueTag())
                .followupFineGrainedTag(submitted == null ? null : submitted.getFollowupFineGrainedTag())
                .focusPointKeys(values(anchor == null ? null : anchor.getFocusPointKeys()).stream().toList())
                .focusTestSemanticCodes(values(anchor == null ? null : anchor.getFocusTestSemanticCodes()).stream().toList())
                .followupPointKeys(values(submitted == null ? null : submitted.getFollowupPointKeys()).stream().toList())
                .followupFailedTestSemanticCodes(values(submitted == null ? null : submitted.getFollowupFailedTestSemanticCodes()).stream().toList())
                .evidenceRefs(evidenceRefs(token, anchor, submitted, outcome))
                .lastEventAt(toText(anchor == null ? null : anchor.getCreatedAt()))
                .build();
    }

    private String outcome(StudentRecommendationEvent submitted,
                           boolean clickedOrEntered,
                           boolean highRisk,
                           boolean sameFocus) {
        if (submitted == null) {
            return clickedOrEntered ? OUTCOME_NO_FOLLOWUP_SUBMISSION : OUTCOME_EXPOSED_ONLY;
        }
        if (Submission.Verdict.ACCEPTED.name().equals(submitted.getFollowupVerdict()) && !sameFocus) {
            return OUTCOME_CONTRACT_FULFILLED;
        }
        if (sameFocus) {
            return OUTCOME_UNRESOLVED_SAME_FOCUS;
        }
        if (Submission.Verdict.ACCEPTED.name().equals(submitted.getFollowupVerdict())) {
            return OUTCOME_CONTRACT_FULFILLED;
        }
        if (highRisk) {
            return OUTCOME_TEACHER_INTERVENTION_NEEDED;
        }
        if (isBlank(submitted.getFollowupIssueTag()) && isBlank(submitted.getFollowupFineGrainedTag())) {
            return OUTCOME_WAITING_DIAGNOSIS;
        }
        return OUTCOME_CONTRACT_FULFILLED;
    }

    private String summary(String outcome, StudentRecommendationEvent submitted, boolean sameFocus) {
        return switch (outcome) {
            case OUTCOME_CONTRACT_FULFILLED -> submitted != null
                    && Submission.Verdict.ACCEPTED.name().equals(submitted.getFollowupVerdict())
                    ? "推荐后的后续提交已经通过，行动契约得到观察性兑现。"
                    : "推荐后的后续诊断没有继续命中关注错因，行动契约初步兑现。";
            case OUTCOME_UNRESOLVED_SAME_FOCUS -> "推荐后的后续提交仍命中关注错因，行动契约未兑现。";
            case OUTCOME_NO_FOLLOWUP_SUBMISSION -> "推荐被点击或进入题目，但还没有形成后续提交证据。";
            case OUTCOME_WAITING_DIAGNOSIS -> "推荐后已有提交，但诊断标签尚未回填，暂不能判断契约是否兑现。";
            case OUTCOME_TEACHER_INTERVENTION_NEEDED -> "高风险推荐后仍未通过，需要教师查看行动证据。";
            default -> sameFocus ? "推荐后仍有同类错因风险。" : "推荐仅曝光，尚未形成行动证据。";
        };
    }

    private String recommendedAdjustment(String outcome, boolean teacherAttention) {
        if (teacherAttention) {
            return "带着推荐 token、后续提交和同类错因证据请求教师介入。";
        }
        return switch (outcome) {
            case OUTCOME_UNRESOLVED_SAME_FOCUS -> "下一轮推荐先降级为最小样例复盘，补足证据解释后再进入新题。";
            case OUTCOME_NO_FOLLOWUP_SUBMISSION -> "缩小推荐动作，先判断学生卡在读题、起步还是验证样例。";
            case OUTCOME_WAITING_DIAGNOSIS -> "等待诊断标签回填后再更新推荐效果判断。";
            case OUTCOME_CONTRACT_FULFILLED -> "沉淀该推荐策略，继续观察是否能迁移到同能力新题。";
            default -> "先形成点击、进入或后续提交证据，再判断学习效果。";
        };
    }

    private List<String> evidenceRefs(String token,
                                      StudentRecommendationEvent anchor,
                                      StudentRecommendationEvent submitted,
                                      String outcome) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        refs.add("recommendation:" + token);
        refs.add("recommendation-outcome:" + outcome);
        if (anchor != null && !isBlank(anchor.getStrategy())) {
            refs.add("recommendation-strategy:" + anchor.getStrategy());
        }
        if (submitted != null && submitted.getFollowupSubmissionId() != null) {
            refs.add("submission:" + submitted.getFollowupSubmissionId());
        }
        if (submitted != null && !isBlank(submitted.getFollowupFineGrainedTag())) {
            refs.add("followup-fine-tag:" + submitted.getFollowupFineGrainedTag());
        }
        if (submitted != null && !isBlank(submitted.getFollowupIssueTag())) {
            refs.add("followup-issue-tag:" + submitted.getFollowupIssueTag());
        }
        values(anchor == null ? null : anchor.getFocusPointKeys())
                .forEach(value -> refs.add("focus-point:" + value));
        values(anchor == null ? null : anchor.getFocusTestSemanticCodes())
                .forEach(value -> refs.add("focus-test-semantic:" + value));
        values(submitted == null ? null : submitted.getFollowupPointKeys())
                .forEach(value -> refs.add("followup-point:" + value));
        values(submitted == null ? null : submitted.getFollowupFailedTestSemanticCodes())
                .forEach(value -> refs.add("followup-test-semantic:" + value));
        return refs.stream().toList();
    }

    private int outcomePriority(RecommendationEffectivenessResponse.ActionEvidenceSignal signal) {
        String outcome = signal == null ? "" : signal.getOutcome();
        if (signal != null && signal.isNeedsTeacherAttention()) {
            return 0;
        }
        return switch (outcome) {
            case OUTCOME_UNRESOLVED_SAME_FOCUS, OUTCOME_TEACHER_INTERVENTION_NEEDED -> 1;
            case OUTCOME_NO_FOLLOWUP_SUBMISSION -> 2;
            case OUTCOME_WAITING_DIAGNOSIS -> 3;
            case OUTCOME_EXPOSED_ONLY -> 4;
            case OUTCOME_CONTRACT_FULFILLED -> 5;
            default -> 6;
        };
    }

    private MatchResult match(StudentRecommendationEvent event) {
        if (event == null) {
            return MatchResult.none();
        }
        if (overlaps(event.getFocusIssueIds(), event.getFollowupIssueIds())) {
            return MatchResult.of(MATCH_ISSUE_ID);
        }
        if (overlaps(event.getFocusPointKeys(), event.getFollowupPointKeys())) {
            return MatchResult.of(MATCH_POINT_KEY);
        }
        if (overlaps(event.getFocusMistakePointCodes(), event.getFollowupMistakePointCodes())) {
            return MatchResult.of(MATCH_MISTAKE_POINT);
        }
        if (overlaps(event.getFocusSkillUnitCodes(), event.getFollowupSkillUnitCodes())) {
            return MatchResult.of(MATCH_SKILL_UNIT);
        }
        if (overlaps(event.getFocusKnowledgeNodeCodes(), event.getFollowupKnowledgeNodeCodes())) {
            return MatchResult.of(MATCH_KNOWLEDGE_NODE);
        }
        if (overlaps(event.getFocusTestSemanticCodes(), event.getFollowupFailedTestSemanticCodes())) {
            return MatchResult.of(MATCH_TEST_SEMANTIC);
        }
        Set<String> tags = focusTags(event.getFocusTags()).stream()
                .map(this::normalizeTag)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (tags.isEmpty()) {
            return MatchResult.none();
        }
        boolean matched = tags.contains(normalizeTag(event.getFollowupFineGrainedTag()))
                || tags.contains(normalizeTag(event.getFollowupIssueTag()));
        return matched ? MatchResult.of(MATCH_LEGACY_TAG) : MatchResult.none();
    }

    private boolean overlaps(String left, String right) {
        Set<String> leftValues = values(left).stream()
                .map(this::normalizeTag)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (leftValues.isEmpty()) {
            return false;
        }
        return values(right).stream()
                .map(this::normalizeTag)
                .anyMatch(leftValues::contains);
    }

    private Set<String> values(String raw) {
        return focusTags(raw);
    }

    private Set<String> focusTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return List.of(raw.replace("[", "")
                        .replace("]", "")
                        .replace("\"", "")
                        .split("[,;\\s]+"))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private StudentRecommendationEvent latest(List<StudentRecommendationEvent> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        return events.stream()
                .max(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .orElse(null);
    }

    private String normalizeTag(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String toText(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private record MatchResult(boolean matched, String basis) {
        private static MatchResult of(String basis) {
            return new MatchResult(true, basis);
        }

        private static MatchResult none() {
            return new MatchResult(false, MATCH_NONE);
        }
    }
}
