package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiQualityTrendResponse {
    private long assignmentCount;
    private long analyzedSubmissionCount;
    private long correctionCount;
    private long evalCandidateCount;
    private long interventionEvalCandidateCount;
    private long interventionWaitingFollowupCount;
    private long interventionImprovedCount;
    private long interventionShiftedCount;
    private long interventionStillStuckCount;
    private long lowConfidenceCount;
    private long highLeakRiskCount;
    private long promptSafetyIncidentCount;
    private long promptSafetyDowngradeCount;
    private long promptSafetyHighRiskDowngradeCount;
    private long coachSafetyRejectionCount;
    private long modelCompletedCount;
    private long modelPartialCount;
    private long modelRuntimeFailureCount;
    private double correctionRate;
    private double lowConfidenceRate;
    private double highLeakRiskRate;
    private double promptSafetyIncidentRate;
    private double modelRuntimeFailureRate;
    private String summary;
    private List<AssignmentQualityPoint> assignments;
    private List<TagTrendStat> correctedTags;
    private List<TagTrendStat> evalNeededTags;
    private List<SourceQualitySegment> sourceSegments;

    @Data
    @Builder
    public static class AssignmentQualityPoint {
        private Long assignmentId;
        private String assignmentTitle;
        private long analyzedSubmissionCount;
        private long correctionCount;
        private long evalCandidateCount;
        private long interventionEvalCandidateCount;
        private long interventionWaitingFollowupCount;
        private long interventionImprovedCount;
        private long interventionShiftedCount;
        private long interventionStillStuckCount;
        private long lowConfidenceCount;
        private long highLeakRiskCount;
        private long promptSafetyIncidentCount;
        private long promptSafetyDowngradeCount;
        private long promptSafetyHighRiskDowngradeCount;
        private long coachSafetyRejectionCount;
        private long modelCompletedCount;
        private long modelPartialCount;
        private long modelRuntimeFailureCount;
        private double correctionRate;
        private double lowConfidenceRate;
        private double highLeakRiskRate;
        private double promptSafetyIncidentRate;
        private double modelRuntimeFailureRate;
        private String summary;
    }

    @Data
    @Builder
    public static class TagTrendStat {
        private String tag;
        private String label;
        private long count;
        private long evalCandidateCount;
    }

    @Data
    @Builder
    public static class SourceQualitySegment {
        private String sourceType;
        private String versionLabel;
        private String provider;
        private String model;
        private String modelVersion;
        private String promptVersion;
        private String agentVersion;
        private String status;
        private String runtimeMode;
        private String failureStage;
        private String failureReason;
        private String transportMode;
        private long fallbackCount;
        private long modelCompletedCount;
        private long modelPartialCount;
        private long modelRuntimeFailureCount;
        private long streamNoContentCount;
        private long streamInvalidChunkCount;
        private long streamFallbackRetryCount;
        private String recoveryStatus;
        private long recoveryCheckCount;
        private long recoveryPassedCheckCount;
        private long recoveryBlockedReasonCount;
        private List<String> recoveryBlockedReasons;
        private List<String> recoverySmokeRequiredChecks;
        private String qualityComparabilityStatus;
        private String qualityComparabilitySummary;
        private long qualityComparabilityReasonCount;
        private List<String> qualityComparabilityReasons;
        private long analyzedSubmissionCount;
        private long correctionCount;
        private long lowConfidenceCount;
        private long highLeakRiskCount;
        private long promptSafetyIncidentCount;
        private long promptSafetyDowngradeCount;
        private long promptSafetyHighRiskDowngradeCount;
        private long coachSafetyRejectionCount;
        private double correctionRate;
        private double lowConfidenceRate;
        private double highLeakRiskRate;
        private double promptSafetyIncidentRate;
        private double modelRuntimeFailureRate;
    }
}
