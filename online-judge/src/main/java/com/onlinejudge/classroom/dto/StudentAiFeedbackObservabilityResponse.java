package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StudentAiFeedbackObservabilityResponse {
    private Long assignmentId;
    private long submissionCount;
    private long failedSubmissionCount;
    private long feedbackRecordCount;
    private long modelReadyCount;
    private long feedbackFailedCount;
    private long timeoutCount;
    private long safetyRejectedCount;
    private long viewedCount;
    private double modelReadyRate;
    private double viewRate;
    private Long p50LatencyMs;
    private Long p95LatencyMs;
    private long latencySampleCount;
    private List<FailureReasonStat> failureReasons;
    private List<ImpactStat> impactStats;
    private String summary;
    private String recommendedAction;

    @Data
    @Builder
    public static class FailureReasonStat {
        private String reason;
        private long count;
    }

    @Data
    @Builder
    public static class ImpactStat {
        private String status;
        private String label;
        private long count;
    }
}
