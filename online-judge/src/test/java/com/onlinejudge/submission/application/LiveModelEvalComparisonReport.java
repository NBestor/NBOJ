package com.onlinejudge.submission.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveModelEvalComparisonReport {

    private String baselineModel;
    private String candidateModel;
    private String baselinePromptVersion;
    private String candidatePromptVersion;
    private String baselineRuntimeProfile;
    private String candidateRuntimeProfile;
    private Long baselineTimeoutSeconds;
    private Long candidateTimeoutSeconds;
    private Integer baselineMaxOutputTokens;
    private Integer candidateMaxOutputTokens;
    private Integer baselineCaseCount;
    private Integer candidateCaseCount;
    private Integer comparableCaseCount;
    private Integer baselineOnlyCaseCount;
    private Integer candidateOnlyCaseCount;
    private List<String> baselineOnlyCaseIds;
    private List<String> candidateOnlyCaseIds;
    private QualitySnapshot baselineQuality;
    private QualitySnapshot candidateQuality;
    private QualityDelta delta;
    private Map<String, Integer> intelligenceMetricFailDelta;
    private Map<String, Integer> modelTraceMetricFailDelta;
    private Map<String, Integer> educationAgentMetricFailDelta;
    private Map<String, Integer> studentFeedbackMetricFailDelta;
    private List<CaseComparison> cases;
    private List<String> improvementSignals;
    private List<String> regressionSignals;
    private IterationAdvice iterationAdvice;
    private String recommendation;

    public String consoleSummary() {
        return "recommendation=" + safe(recommendation)
                + ", comparable=" + number(comparableCaseCount)
                + ", rubricChainAvgDelta=" + decimal(delta == null ? null : delta.getRubricChainAverageScoreDelta())
                + ", rubricChainPassedDelta=" + number(delta == null ? null : delta.getRubricChainPassedCountDelta())
                + ", modelTraceAvgDelta=" + decimal(delta == null ? null : delta.getModelTraceQualityAverageScoreDelta())
                + ", modelTracePassRateDelta=" + decimal(delta == null ? null : delta.getModelTraceMetricPassRateDelta())
                + ", intelligenceAvgDelta=" + decimal(delta == null ? null : delta.getIntelligenceQualityAverageScoreDelta())
                + ", educationAgentAvgDelta=" + decimal(delta == null ? null : delta.getEducationAgentQualityAverageScoreDelta())
                + ", studentFeedbackAvgDelta=" + decimal(delta == null ? null : delta.getStudentFeedbackQualityAverageScoreDelta())
                + ", fallbackDelta=" + number(delta == null ? null : delta.getFallbackCountDelta())
                + ", regressions=" + number(regressionSignals == null ? null : regressionSignals.size())
                + ", improvements=" + number(improvementSignals == null ? null : improvementSignals.size());
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private String decimal(Double value) {
        if (value == null) {
            return "0.000";
        }
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualitySnapshot {
        private Integer completedCount;
        private Integer partialCount;
        private Integer fallbackCount;
        private Integer timeoutCount;
        private Integer latencyBudgetExceededCount;
        private Map<String, Integer> safetyCategoryCounts;
        private Integer rubricChainEvaluatedCount;
        private Integer rubricChainPassedCount;
        private Double rubricChainAverageScore;
        private Integer intelligenceCompletedCount;
        private Integer intelligenceQualityPassedCount;
        private Double intelligenceQualityAverageScore;
        private Integer educationAgentCompletedCount;
        private Integer educationAgentQualityPassedCount;
        private Double educationAgentQualityAverageScore;
        private Integer modelTraceCompletedCount;
        private Integer modelTraceQualityPassedCount;
        private Integer modelTraceMetricPassedCount;
        private Integer modelTraceMetricTotalCount;
        private Double modelTraceMetricPassRate;
        private Double modelTraceQualityAverageScore;
        private Integer studentFeedbackCompletedCount;
        private Integer studentFeedbackQualityPassedCount;
        private Double studentFeedbackQualityAverageScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityDelta {
        private Integer completedCountDelta;
        private Integer fallbackCountDelta;
        private Integer latencyBudgetExceededCountDelta;
        private Map<String, Integer> safetyCategoryCountDelta;
        private Integer rubricChainEvaluatedCountDelta;
        private Integer rubricChainPassedCountDelta;
        private Double rubricChainAverageScoreDelta;
        private Integer intelligenceCompletedCountDelta;
        private Integer intelligenceQualityPassedCountDelta;
        private Double intelligenceQualityAverageScoreDelta;
        private Integer educationAgentCompletedCountDelta;
        private Integer educationAgentQualityPassedCountDelta;
        private Double educationAgentQualityAverageScoreDelta;
        private Integer modelTraceCompletedCountDelta;
        private Integer modelTraceQualityPassedCountDelta;
        private Double modelTraceMetricPassRateDelta;
        private Double modelTraceQualityAverageScoreDelta;
        private Integer studentFeedbackCompletedCountDelta;
        private Integer studentFeedbackQualityPassedCountDelta;
        private Double studentFeedbackQualityAverageScoreDelta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseComparison {
        private String caseId;
        private String baselineStatus;
        private String candidateStatus;
        private Boolean baselineCountedAsModel;
        private Boolean candidateCountedAsModel;
        private Boolean baselineFallbackUsed;
        private Boolean candidateFallbackUsed;
        private Double baselineRubricChainScore;
        private Double candidateRubricChainScore;
        private Double rubricChainScoreDelta;
        private List<String> baselineFailedRubricStages;
        private List<String> candidateFailedRubricStages;
        private Double baselineModelTraceQualityScore;
        private Double candidateModelTraceQualityScore;
        private Double modelTraceQualityScoreDelta;
        private Double baselineIntelligenceQualityScore;
        private Double candidateIntelligenceQualityScore;
        private Double intelligenceQualityScoreDelta;
        private Double baselineEducationAgentQualityScore;
        private Double candidateEducationAgentQualityScore;
        private Double educationAgentQualityScoreDelta;
        private Double baselineStudentFeedbackQualityScore;
        private Double candidateStudentFeedbackQualityScore;
        private Double studentFeedbackQualityScoreDelta;
        private List<String> baselineFailedIntelligenceMetrics;
        private List<String> candidateFailedIntelligenceMetrics;
        private List<String> newlyPassedIntelligenceMetrics;
        private List<String> newlyFailedIntelligenceMetrics;
        private List<String> baselineFailedModelTraceMetrics;
        private List<String> candidateFailedModelTraceMetrics;
        private List<String> newlyPassedModelTraceMetrics;
        private List<String> newlyFailedModelTraceMetrics;
        private List<String> baselineFailedEducationAgentMetrics;
        private List<String> candidateFailedEducationAgentMetrics;
        private List<String> newlyPassedEducationAgentMetrics;
        private List<String> newlyFailedEducationAgentMetrics;
        private List<String> baselineFailedStudentFeedbackMetrics;
        private List<String> candidateFailedStudentFeedbackMetrics;
        private List<String> newlyPassedStudentFeedbackMetrics;
        private List<String> newlyFailedStudentFeedbackMetrics;
        private List<String> improvementSignals;
        private List<String> regressionSignals;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IterationAdvice {
        private String overallDecision;
        private Boolean candidatePromotionAllowed;
        private List<String> blockedPromotionReasons;
        private List<IterationAction> priorityActions;
        private List<IterationAction> promptActions;
        private List<IterationAction> standardLibraryActions;
        private List<IterationAction> runtimeActions;
        private List<IterationAction> evalDataActions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IterationAction {
        private String area;
        private String priority;
        private String title;
        private String rationale;
        private List<String> evidenceSignals;
        private List<String> targetFiles;
        private String validationHint;
    }
}
