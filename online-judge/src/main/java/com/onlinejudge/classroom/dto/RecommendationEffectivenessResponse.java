package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RecommendationEffectivenessResponse {
    private long recentEventCount;
    private long uniqueRecommendationCount;
    private long exposureCount;
    private long clickCount;
    private long enteredProblemCount;
    private long followupSubmissionCount;
    private long acceptedFollowupCount;
    private long sameFocusIssueCount;
    private long clickedWithoutSubmissionCount;
    private long unresolvedLearningSignalCount;
    private long teacherInterventionRecommendedCount;
    private double clickThroughRate;
    private double followupSubmissionRate;
    private double acceptedFollowupRate;
    private double sameFocusIssueRate;
    private String summary;
    private List<SegmentStat> byType;
    private List<SegmentStat> byStrategy;
    private List<SegmentStat> focusTags;
    private List<FeedbackSignal> feedbackSignals;
    private List<ActionEvidenceSignal> actionEvidenceSignals;

    @Data
    @Builder
    public static class SegmentStat {
        private String key;
        private String label;
        private long exposureCount;
        private long clickCount;
        private long enteredProblemCount;
        private long followupSubmissionCount;
        private long acceptedFollowupCount;
        private long sameFocusIssueCount;
        private long unresolvedLearningSignalCount;
        private long teacherInterventionRecommendedCount;
        private double clickThroughRate;
        private double followupSubmissionRate;
        private double acceptedFollowupRate;
        private double sameFocusIssueRate;
    }

    @Data
    @Builder
    public static class FeedbackSignal {
        private String signal;
        private String strategy;
        private String severity;
        private long evidenceCount;
        private String summary;
        private String recommendedAction;
        private List<String> evidenceTokens;
    }

    @Data
    @Builder
    public static class ActionEvidenceSignal {
        private String recommendationToken;
        private Long studentProfileId;
        private Long assignmentId;
        private Long problemId;
        private String type;
        private String strategy;
        private String riskLevel;
        private String learningHypothesis;
        private String expectedCompletionSignal;
        private String outcome;
        private String matchBasis;
        private String summary;
        private String recommendedAdjustment;
        private boolean needsTeacherAttention;
        private Long followupSubmissionId;
        private String followupVerdict;
        private String followupIssueTag;
        private String followupFineGrainedTag;
        private List<String> focusPointKeys;
        private List<String> focusTestSemanticCodes;
        private List<String> followupPointKeys;
        private List<String> followupFailedTestSemanticCodes;
        private List<String> evidenceRefs;
        private String lastEventAt;
    }
}
