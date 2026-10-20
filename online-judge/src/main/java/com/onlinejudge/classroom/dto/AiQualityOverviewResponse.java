package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiQualityOverviewResponse {
    private Long assignmentId;
    private long analyzedSubmissionCount;
    private long correctionCount;
    private long evalCandidateCount;
    private long lowConfidenceCount;
    private long highLeakRiskCount;
    private long modelFallbackCount;
    private long modelPartialCount;
    private long modelRuntimeFailureCount;
    private long modelCompletedCount;
    private double correctionRate;
    private double lowConfidenceRate;
    private double highLeakRiskRate;
    private double modelFallbackRate;
    private double modelRuntimeFailureRate;
    private String summary;
    private String qualityRiskSummary;
    private PromptSafetyIncidentSignal promptSafetyIncidentSignal;
    private RuntimeAttributionSignal runtimeAttributionSignal;
    private List<QualityDimension> qualityDimensions;
    private List<ImprovementPriority> improvementPriorities;
    private EvalReadiness evalReadiness;
    private List<TagCorrectionStat> correctedTags;

    @Data
    @Builder
    public static class QualityDimension {
        private String dimension;
        private String label;
        private String status;
        private double score;
        private String summary;
        private List<String> evidenceRefs;
        private String recommendedAction;
    }

    @Data
    @Builder
    public static class ImprovementPriority {
        private String priority;
        private String dimension;
        private String severity;
        private String reason;
        private String recommendedAction;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    public static class EvalReadiness {
        private String status;
        private String summary;
        private long candidateCount;
        private long correctionCount;
        private long interventionCandidateCount;
        private long interventionWaitingFollowupCount;
        private long interventionImprovedCount;
        private long interventionShiftedCount;
        private long interventionStillStuckCount;
        private String modelQualityBaselineStatus;
        private String modelQualityBaselineSummary;
        private long modelQualityBaselineReasonCount;
        private List<String> modelQualityBaselineReasons;
        private String recommendedAction;
        private List<TagCorrectionStat> priorityTags;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    public static class PromptSafetyIncidentSignal {
        private String status;
        private String primaryRiskSource;
        private long totalIncidentCount;
        private long highLeakRiskCount;
        private long safetyDowngradeCount;
        private long highRiskSafetyDowngradeCount;
        private long coachSafetyRiskCount;
        private String summary;
        private String recommendedAction;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    public static class RuntimeAttributionSignal {
        private String status;
        private String primaryFailureType;
        private String primaryFailureReason;
        private String primaryFailureStage;
        private String primaryTransportMode;
        private long modelCompletedCount;
        private long modelPartialCount;
        private long modelRuntimeFailureCount;
        private long modelFallbackCount;
        private long primaryFailureCount;
        private double runtimeFailureRate;
        private long streamNoContentCount;
        private long streamInvalidChunkCount;
        private long streamFallbackRetryCount;
        private String recoveryStatus;
        private long recoveryCheckCount;
        private long recoveryPassedCheckCount;
        private long recoveryBlockedReasonCount;
        private List<String> recoveryPassedChecks;
        private List<String> recoveryBlockedReasons;
        private boolean recoverySmokeRecommended;
        private String recoverySmokeCaseId;
        private String recoverySmokeRuntimeProfile;
        private List<String> recoverySmokeRequiredChecks;
        private String qualityComparabilityStatus;
        private String qualityComparabilitySummary;
        private long qualityComparabilityReasonCount;
        private List<String> qualityComparabilityReasons;
        private String summary;
        private String recommendedAction;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    public static class TagCorrectionStat {
        private String originalTag;
        private String originalLabel;
        private String correctedTag;
        private String correctedLabel;
        private long count;
    }
}
