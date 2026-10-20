package com.onlinejudge.submission.application;

import com.onlinejudge.eval.LiveEvalQualityBaselineDraft;
import com.onlinejudge.eval.LiveEvalRuntimeFixtureDraft;
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
public class LiveModelEvalReport {

    private String model;
    private String promptVersion;
    private Integer totalCount;
    private Integer completedCount;
    private Integer partialCount;
    private Integer fallbackCount;
    private Integer timeoutCount;
    private Long caseDelayMs;
    private Integer delayedCaseCount;
    private Long latencyBudgetMs;
    private Integer latencyBudgetExceededCount;
    private Integer issueTagHitCount;
    private Integer fineTagHitCount;
    private Integer modelIssueTagHitCount;
    private Integer modelFineTagHitCount;
    private Integer fallbackIssueTagHitCount;
    private Integer fallbackFineTagHitCount;
    private Integer safetyPassedCount;
    private Map<String, Integer> safetyCategoryCounts;
    private Integer complexCaseCount;
    private Integer rubricChainCaseCount;
    private Integer rubricChainEvaluatedCount;
    private Integer rubricChainFallbackExcludedCount;
    private Integer rubricChainPassedCount;
    private Integer rubricChainStagePassedCount;
    private Integer rubricChainStageTotalCount;
    private Double rubricChainAverageScore;
    private Map<String, Integer> rubricChainStagePassCounts;
    private Map<String, Integer> rubricChainStageFailCounts;
    private Integer complexQualityPassedCount;
    private Integer complexMetricPassedCount;
    private Integer complexMetricTotalCount;
    private Double complexQualityAverageScore;
    private String provider;
    private String baseUrl;
    private String runtimeProfile;
    private Long timeoutSeconds;
    private Integer maxOutputTokens;
    private Integer intelligenceCaseCount;
    private Integer intelligenceCompletedCount;
    private Integer intelligenceFallbackExcludedCount;
    private Integer intelligenceQualityPassedCount;
    private Integer intelligenceMetricPassedCount;
    private Integer intelligenceMetricTotalCount;
    private Double intelligenceQualityAverageScore;
    private Map<String, Integer> intelligenceMetricPassCounts;
    private Map<String, Integer> intelligenceMetricFailCounts;
    private Integer educationAgentCaseCount;
    private Integer educationAgentCompletedCount;
    private Integer educationAgentFallbackExcludedCount;
    private Integer educationAgentJudgmentCompleteCount;
    private Integer educationAgentQualityPassedCount;
    private Integer educationAgentMetricPassedCount;
    private Integer educationAgentMetricTotalCount;
    private Double educationAgentQualityAverageScore;
    private Map<String, Integer> educationAgentMetricPassCounts;
    private Map<String, Integer> educationAgentMetricFailCounts;
    private Integer modelTraceCaseCount;
    private Integer modelTraceCompletedCount;
    private Integer modelTraceFallbackExcludedCount;
    private Integer modelTraceQualityPassedCount;
    private Integer modelTraceMetricPassedCount;
    private Integer modelTraceMetricTotalCount;
    private Double modelTraceQualityAverageScore;
    private Map<String, Integer> modelTraceMetricPassCounts;
    private Map<String, Integer> modelTraceMetricFailCounts;
    private Integer studentFeedbackCaseCount;
    private Integer studentFeedbackCompletedCount;
    private Integer studentFeedbackQualityPassedCount;
    private Integer studentFeedbackMetricPassedCount;
    private Integer studentFeedbackMetricTotalCount;
    private Double studentFeedbackQualityAverageScore;
    private Map<String, Integer> studentFeedbackMetricPassCounts;
    private Map<String, Integer> studentFeedbackMetricFailCounts;
    private Integer runtimeFixtureDraftCount;
    private Integer qualityBaselineDraftCount;
    private String recoveryStatus;
    private Integer recoveryCheckCount;
    private Integer recoveryPassedCheckCount;
    private Integer recoveryBlockedReasonCount;
    private List<String> recoveryPassedChecks;
    private List<String> recoveryBlockedReasons;
    private List<Entry> entries;
    private List<LiveEvalRuntimeFixtureDraft> runtimeFixtureDrafts;
    private List<LiveEvalQualityBaselineDraft> qualityBaselineDrafts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String caseId;
        private String model;
        private String promptVersion;
        private String stage;
        private Long latencyMs;
        private Long latencyBudgetMs;
        private Boolean latencyBudgetExceeded;
        private String status;
        private String failureStage;
        private Boolean fallbackUsed;
        private String runtimeProfile;
        private Integer requestBytes;
        private Boolean requestCompact;
        private String transportMode;
        private Integer streamChunkCount;
        private Integer streamContentChunkCount;
        private Integer streamReasoningChunkCount;
        private Integer streamInvalidChunkCount;
        private String streamFinishReason;
        private Boolean streamFallbackRetryUsed;
        private Boolean jsonValid;
        private Boolean modelCompleted;
        private Boolean expectedIssueTagHit;
        private Boolean expectedFineTagHit;
        private Boolean modelIssueTagHit;
        private Boolean modelFineTagHit;
        private Boolean fallbackIssueTagHit;
        private Boolean fallbackFineTagHit;
        private Boolean evidenceValid;
        private Boolean safetyPassed;
        private List<String> safetyCategories;
        private Boolean complexCase;
        private Boolean rubricChainEvaluated;
        private Boolean rubricChainPassed;
        private Integer rubricChainStagePassedCount;
        private Integer rubricChainStageTotalCount;
        private Double rubricChainScore;
        private List<String> rubricChainPassedStages;
        private List<String> rubricChainFailedStages;
        private List<String> rubricChainFailedReasons;
        private Boolean complexQualityPassed;
        private Integer complexMetricPassedCount;
        private Integer complexMetricTotalCount;
        private Double complexQualityScore;
        private List<String> complexPassedMetrics;
        private List<String> complexFailedMetrics;
        private Boolean intelligenceEvaluated;
        private Boolean intelligenceQualityPassed;
        private Integer intelligenceMetricPassedCount;
        private Integer intelligenceMetricTotalCount;
        private Double intelligenceQualityScore;
        private List<String> intelligencePassedMetrics;
        private List<String> intelligenceFailedMetrics;
        private Boolean educationAgentEvaluated;
        private Boolean educationAgentJudgmentComplete;
        private Boolean educationAgentHasPrimaryReasoning;
        private Boolean educationAgentHasNextAction;
        private Boolean educationAgentHasImprovementOpportunity;
        private Boolean educationAgentHasSecondarySignal;
        private Boolean educationAgentQualityEvaluated;
        private Boolean educationAgentQualityPassed;
        private Integer educationAgentMetricPassedCount;
        private Integer educationAgentMetricTotalCount;
        private Double educationAgentQualityScore;
        private List<String> educationAgentPassedMetrics;
        private List<String> educationAgentFailedMetrics;
        private Boolean modelTraceEvaluated;
        private Boolean modelTraceQualityPassed;
        private Integer modelTraceMetricPassedCount;
        private Integer modelTraceMetricTotalCount;
        private Double modelTraceQualityScore;
        private List<String> modelTracePassedMetrics;
        private List<String> modelTraceFailedMetrics;
        private Boolean studentFeedbackEvaluated;
        private Boolean studentFeedbackQualityPassed;
        private Integer studentFeedbackMetricPassedCount;
        private Integer studentFeedbackMetricTotalCount;
        private Double studentFeedbackQualityScore;
        private List<String> studentFeedbackPassedMetrics;
        private List<String> studentFeedbackFailedMetrics;
        private LocalTruth localTruth;
        private ModelOutput modelOutput;
        private StudentFeedbackOutput studentFeedback;
        private ModelJudgment modelJudgment;
        private QualityScore qualityScore;
        private List<String> actualIssueTags;
        private List<String> actualFineGrainedTags;
        private List<String> actualEvidenceRefs;
        private String failureReason;
        private String outputSummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalTruth {
        private String caseId;
        private String bugPattern;
        private String primaryIssueTag;
        private String primaryFineGrainedTag;
        private String primaryEvidenceRef;
        private String expectedTeachingPriority;
        private List<String> requiredEvidenceRefs;
        private List<String> distractingSignals;
        private List<String> mustMention;
        private List<String> mustNotMention;
        private List<ExpectedImprovementOpportunity> expectedImprovementOpportunities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelOutput {
        private List<String> issueTags;
        private List<String> fineGrainedTags;
        private List<String> evidenceRefs;
        private String answerLeakRisk;
        private String summary;
        private String educationJudgmentSource;
        private String educationPrimaryReasoning;
        private String educationTeachingPriority;
        private List<String> educationSecondarySignals;
        private List<String> educationImprovementCategories;
        private String educationNextAction;
        private List<String> educationEvidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentFeedbackOutput {
        private String summary;
        private List<String> blockingMessages;
        private List<String> secondaryMessages;
        private List<String> improvementCategories;
        private List<String> improvementMessages;
        private String nextAction;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpectedImprovementOpportunity {
        private String category;
        private String studentMessage;
        private String benefit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelJudgment {
        private Boolean modelCompleted;
        private Boolean fallbackUsed;
        private Boolean countedAsIntelligence;
        private String status;
        private String failureReason;
        private String provider;
        private String model;
        private String baseUrl;
        private String runtimeProfile;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityScore {
        private Double complexQualityScore;
        private Double intelligenceQualityScore;
        private Double educationAgentQualityScore;
        private Double modelTraceQualityScore;
        private Double studentFeedbackQualityScore;
        private Double rubricChainScore;
        private Map<String, Boolean> complexMetrics;
        private Map<String, Boolean> intelligenceMetrics;
        private Map<String, Boolean> educationAgentMetrics;
        private Map<String, Boolean> modelTraceMetrics;
        private Map<String, Boolean> studentFeedbackMetrics;
        private Map<String, Boolean> rubricChainStages;
    }
}
