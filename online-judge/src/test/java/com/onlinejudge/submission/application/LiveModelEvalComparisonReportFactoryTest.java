package com.onlinejudge.submission.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LiveModelEvalComparisonReportFactoryTest {

    private final LiveModelEvalComparisonReportFactory factory = new LiveModelEvalComparisonReportFactory();

    @Test
    void comparesPromptIterationsByNativeTraceAndIntelligenceQuality() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .timeoutSeconds(35L)
                .maxOutputTokens(900)
                .completedCount(2)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of())
                .intelligenceCompletedCount(2)
                .intelligenceQualityPassedCount(1)
                .intelligenceQualityAverageScore(0.55)
                .educationAgentCompletedCount(2)
                .educationAgentQualityPassedCount(1)
                .educationAgentQualityAverageScore(0.60)
                .modelTraceCompletedCount(2)
                .modelTraceQualityPassedCount(1)
                .modelTraceMetricPassedCount(6)
                .modelTraceMetricTotalCount(10)
                .modelTraceQualityAverageScore(0.60)
                .studentFeedbackCompletedCount(2)
                .studentFeedbackQualityPassedCount(1)
                .studentFeedbackQualityAverageScore(0.65)
                .modelTraceMetricFailCounts(Map.of(
                        "nativeTeachingPriorityClear", 2,
                        "nativeSecondarySignalsBalanced", 1))
                .entries(List.of(
                        entry("complex-live-01", "MODEL_COMPLETED", false, 0.6, 0.5, 0.6,
                                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                        "modelTraceMetric:nativeSafetyBoundary"),
                                List.of("nativeTeachingPriorityClear")),
                        entry("complex-live-02", "MODEL_COMPLETED", false, 0.6, 0.6, 0.7,
                                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                        "modelTraceMetric:nativeNextActionObservable"),
                                List.of("nativeSecondarySignalsBalanced"))
                ))
                .build();
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .timeoutSeconds(45L)
                .maxOutputTokens(1200)
                .completedCount(2)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of())
                .intelligenceCompletedCount(2)
                .intelligenceQualityPassedCount(2)
                .intelligenceQualityAverageScore(0.80)
                .educationAgentCompletedCount(2)
                .educationAgentQualityPassedCount(2)
                .educationAgentQualityAverageScore(0.90)
                .modelTraceCompletedCount(2)
                .modelTraceQualityPassedCount(2)
                .modelTraceMetricPassedCount(9)
                .modelTraceMetricTotalCount(10)
                .modelTraceQualityAverageScore(0.90)
                .studentFeedbackCompletedCount(2)
                .studentFeedbackQualityPassedCount(2)
                .studentFeedbackQualityAverageScore(0.85)
                .modelTraceMetricFailCounts(Map.of(
                        "nativeTeachingPriorityClear", 0,
                        "nativeSecondarySignalsBalanced", 1))
                .entries(List.of(
                        entry("complex-live-01", "MODEL_COMPLETED", false, 1.0, 0.8, 0.8,
                                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                        "modelTraceMetric:nativeSafetyBoundary",
                                        "modelTraceMetric:nativeTeachingPriorityClear"),
                                List.of()),
                        entry("complex-live-02", "MODEL_COMPLETED", false, 0.8, 0.8, 0.9,
                                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                        "modelTraceMetric:nativeNextActionObservable",
                                        "modelTraceMetric:nativeSafetyBoundary"),
                                List.of("nativeSecondarySignalsBalanced"))
                ))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("PROMOTE_CANDIDATE");
        assertThat(report.getBaselineTimeoutSeconds()).isEqualTo(35L);
        assertThat(report.getCandidateTimeoutSeconds()).isEqualTo(45L);
        assertThat(report.getBaselineMaxOutputTokens()).isEqualTo(900);
        assertThat(report.getCandidateMaxOutputTokens()).isEqualTo(1200);
        assertThat(report.getComparableCaseCount()).isEqualTo(2);
        assertThat(report.getBaselineQuality().getSafetyCategoryCounts()).isEmpty();
        assertThat(report.getCandidateQuality().getSafetyCategoryCounts()).isEmpty();
        assertThat(report.getDelta().getSafetyCategoryCountDelta()).isEmpty();
        assertThat(report.getDelta().getModelTraceQualityAverageScoreDelta()).isEqualTo(0.30000000000000004);
        assertThat(report.getDelta().getModelTraceMetricPassRateDelta()).isEqualTo(0.30000000000000004);
        assertThat(report.getDelta().getIntelligenceQualityAverageScoreDelta()).isEqualTo(0.25);
        assertThat(report.getModelTraceMetricFailDelta()).containsEntry("nativeTeachingPriorityClear", -2);
        assertThat(report.getImprovementSignals()).contains(
                "modelTraceQualityAverageScore +0.300",
                "modelTraceMetricPassRate +0.300",
                "intelligenceQualityAverageScore +0.250",
                "modelTraceMetricFailCount nativeTeachingPriorityClear -2",
                "complex-live-01: modelTraceQualityScore +0.400"
        );
        assertThat(report.getRegressionSignals()).isEmpty();
        assertThat(report.consoleSummary()).contains(
                "recommendation=PROMOTE_CANDIDATE",
                "modelTraceAvgDelta=0.300",
                "regressions=0"
        );
        assertThat(report.getIterationAdvice())
                .satisfies(advice -> {
                    assertThat(advice.getOverallDecision()).isEqualTo("PROMOTE_CANDIDATE");
                    assertThat(advice.getCandidatePromotionAllowed()).isTrue();
                    assertThat(advice.getBlockedPromotionReasons()).isEmpty();
                    assertThat(advice.getPriorityActions()).singleElement()
                            .satisfies(action -> {
                                assertThat(action.getArea()).isEqualTo("BASELINE");
                                assertThat(action.getTitle()).contains("新 live eval baseline");
                                assertThat(action.getTargetFiles()).contains("target/ai-eval-reports/live-model-eval-*.json");
                            });
                });
    }

    @Test
    void flagsFallbackMissingCasesAndNativeTraceRegressions() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(2)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceCompletedCount(2)
                .intelligenceQualityPassedCount(2)
                .intelligenceQualityAverageScore(0.9)
                .educationAgentCompletedCount(2)
                .educationAgentQualityPassedCount(2)
                .educationAgentQualityAverageScore(0.9)
                .modelTraceCompletedCount(2)
                .modelTraceQualityPassedCount(2)
                .modelTraceMetricPassedCount(10)
                .modelTraceMetricTotalCount(10)
                .modelTraceQualityAverageScore(1.0)
                .studentFeedbackCompletedCount(2)
                .studentFeedbackQualityPassedCount(2)
                .studentFeedbackQualityAverageScore(0.9)
                .modelTraceMetricFailCounts(Map.of("nativeSafetyBoundary", 0))
                .entries(List.of(
                        entry("complex-live-01", "MODEL_COMPLETED", false, 1.0, 0.9, 0.9,
                                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                        "modelTraceMetric:nativeSafetyBoundary"),
                                List.of()),
                        entry("complex-live-02", "MODEL_COMPLETED", false, 1.0, 0.9, 0.9,
                                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                        "modelTraceMetric:nativeSafetyBoundary"),
                                List.of())
                ))
                .build();
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("low-latency")
                .completedCount(0)
                .fallbackCount(1)
                .latencyBudgetExceededCount(1)
                .intelligenceCompletedCount(0)
                .intelligenceQualityPassedCount(0)
                .intelligenceQualityAverageScore(0.0)
                .educationAgentCompletedCount(0)
                .educationAgentQualityPassedCount(0)
                .educationAgentQualityAverageScore(0.0)
                .modelTraceCompletedCount(0)
                .modelTraceQualityPassedCount(0)
                .modelTraceMetricPassedCount(0)
                .modelTraceMetricTotalCount(5)
                .modelTraceQualityAverageScore(0.0)
                .studentFeedbackCompletedCount(0)
                .studentFeedbackQualityPassedCount(0)
                .studentFeedbackQualityAverageScore(0.0)
                .modelTraceMetricFailCounts(Map.of("nativeSafetyBoundary", 1))
                .entries(List.of(
                        entry("complex-live-01", "MODEL_RUNTIME_FALLBACK", true, 0.0, 0.0, 0.0,
                                List.of(),
                                List.of("nativePrimaryReasoningGrounded", "nativeSafetyBoundary"))
                ))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getComparableCaseCount()).isEqualTo(1);
        assertThat(report.getBaselineOnlyCaseIds()).containsExactly("complex-live-02");
        assertThat(report.getDelta().getFallbackCountDelta()).isEqualTo(1);
        assertThat(report.getDelta().getLatencyBudgetExceededCountDelta()).isEqualTo(1);
        assertThat(report.getRegressionSignals()).contains(
                "complex-live-02: missing from candidate report",
                "fallbackCount +1",
                "latencyBudgetExceededCount +1",
                "modelTraceQualityAverageScore -1.000",
                "modelTraceMetricFailCount nativeSafetyBoundary +1",
                "complex-live-01: candidate no longer counted as real model completion",
                "complex-live-01: fallback introduced",
                "complex-live-01: modelTraceQualityScore -1.000"
        );
        assertThat(report.getIterationAdvice())
                .satisfies(advice -> {
                    assertThat(advice.getCandidatePromotionAllowed()).isFalse();
                    assertThat(advice.getBlockedPromotionReasons()).contains(
                            "recommendation=KEEP_BASELINE",
                            "fallbackCount increased by 1",
                            "latencyBudgetExceededCount increased by 1",
                            "native trace metric regressed: nativeSafetyBoundary +1"
                    );
                    assertThat(advice.getRuntimeActions())
                            .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                            .contains("先修复真实模型完成率退化", "压低 candidate 的上下文和输出预算");
                    assertThat(advice.getPromptActions())
                            .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                            .contains("收紧不泄题表达边界");
                    assertThat(advice.getStandardLibraryActions())
                            .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                            .contains("把安全退化样式加入 safetyBoundaryRules");
                    assertThat(advice.getEvalDataActions())
                            .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                            .contains("保持 baseline 与 candidate 的代表集一致");
                });
    }

    @Test
    void iterationAdviceSeparatesQuotaAndBudgetGuardFromPromptQualityRegression() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(2)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceCompletedCount(2)
                .intelligenceQualityPassedCount(2)
                .intelligenceQualityAverageScore(1.0)
                .educationAgentCompletedCount(2)
                .educationAgentQualityPassedCount(2)
                .educationAgentQualityAverageScore(0.9)
                .modelTraceCompletedCount(2)
                .modelTraceQualityPassedCount(2)
                .modelTraceMetricPassedCount(10)
                .modelTraceMetricTotalCount(10)
                .modelTraceQualityAverageScore(0.9)
                .studentFeedbackCompletedCount(2)
                .studentFeedbackQualityPassedCount(2)
                .studentFeedbackQualityAverageScore(1.0)
                .entries(List.of(
                        entry("complex-live-01", "MODEL_COMPLETED", false, 0.9, 1.0, 1.0,
                                List.of("modelTraceMetric:nativeRootCauseDecisionChecklistApplied",
                                        "modelTraceMetric:nativePrimaryReasoningGrounded",
                                        "modelTraceMetric:nativeTeachingPriorityClear",
                                        "modelTraceMetric:nativeSecondarySignalsBalanced",
                                        "modelTraceMetric:nativeNextActionObservable"),
                                List.of()),
                        entry("complex-live-02", "MODEL_COMPLETED", false, 0.9, 1.0, 1.0,
                                List.of("modelTraceMetric:nativeRootCauseDecisionChecklistApplied",
                                        "modelTraceMetric:nativePrimaryReasoningGrounded",
                                        "modelTraceMetric:nativeTeachingPriorityClear",
                                        "modelTraceMetric:nativeSecondarySignalsBalanced",
                                        "modelTraceMetric:nativeNextActionObservable"),
                                List.of())
                ))
                .build();
        LiveModelEvalReport.Entry quotaFallback = entry("complex-live-01", "MODEL_RUNTIME_FALLBACK", true,
                0.0, 0.0, 0.0, List.of(), List.of());
        quotaFallback.setFailureReason("MODEL_RUNTIME_FALLBACK:DIAGNOSIS_AND_ADVICE:INSUFFICIENT_QUOTA");
        LiveModelEvalReport.Entry rateLimitFallback = entry("complex-live-02", "MODEL_RUNTIME_FALLBACK", true,
                0.0, 0.0, 0.0, List.of(), List.of());
        rateLimitFallback.setFailureReason("MODEL_RUNTIME_FALLBACK:DIAGNOSIS_AND_ADVICE:RATE_LIMITED");
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(0)
                .fallbackCount(2)
                .latencyBudgetExceededCount(0)
                .intelligenceCompletedCount(0)
                .intelligenceQualityPassedCount(0)
                .intelligenceQualityAverageScore(0.0)
                .educationAgentCompletedCount(0)
                .educationAgentQualityPassedCount(0)
                .educationAgentQualityAverageScore(0.0)
                .modelTraceCompletedCount(0)
                .modelTraceQualityPassedCount(0)
                .modelTraceMetricPassedCount(0)
                .modelTraceMetricTotalCount(0)
                .modelTraceQualityAverageScore(0.0)
                .studentFeedbackCompletedCount(0)
                .studentFeedbackQualityPassedCount(0)
                .studentFeedbackQualityAverageScore(0.0)
                .entries(List.of(quotaFallback, rateLimitFallback))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getRegressionSignals()).contains(
                "complex-live-01: external runtime blocked: failureReason=INSUFFICIENT_QUOTA",
                "complex-live-02: external runtime blocked: failureReason=RATE_LIMIT"
        );
        assertThat(report.getIterationAdvice().getBlockedPromotionReasons())
                .contains("candidate external runtime blocked; rerun after quota or budget guard is cleared");
        assertThat(report.getIterationAdvice().getRuntimeActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains("先解除外接模型运行条件阻塞");
        assertThat(report.getIterationAdvice().getRuntimeActions())
                .flatExtracting(LiveModelEvalComparisonReport.IterationAction::getEvidenceSignals)
                .contains(
                        "complex-live-01: external runtime blocked: failureReason=INSUFFICIENT_QUOTA",
                        "complex-live-02: external runtime blocked: failureReason=RATE_LIMIT"
                );
        assertThat(report.getIterationAdvice().getEvalDataActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains("不要用运行条件受阻报告评估模型智能");
        assertThat(report.getIterationAdvice().getEvalDataActions())
                .flatExtracting(LiveModelEvalComparisonReport.IterationAction::getEvidenceSignals)
                .contains(
                        "complex-live-01: external runtime blocked: failureReason=INSUFFICIENT_QUOTA",
                        "complex-live-02: external runtime blocked: failureReason=RATE_LIMIT"
                );
    }

    @Test
    void iterationAdviceMapsNativeTracePriorityAndSecondaryRegressionsToPromptAndStandardLibraryActions() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceCompletedCount(1)
                .intelligenceQualityPassedCount(1)
                .intelligenceQualityAverageScore(1.0)
                .educationAgentCompletedCount(1)
                .educationAgentQualityPassedCount(1)
                .educationAgentQualityAverageScore(1.0)
                .modelTraceCompletedCount(1)
                .modelTraceQualityPassedCount(1)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(5)
                .modelTraceQualityAverageScore(1.0)
                .studentFeedbackCompletedCount(1)
                .studentFeedbackQualityPassedCount(1)
                .studentFeedbackQualityAverageScore(1.0)
                .modelTraceMetricFailCounts(Map.of(
                        "nativeTeachingPriorityClear", 0,
                        "nativeSecondarySignalsBalanced", 0))
                .entries(List.of(entry("complex-live-04", "MODEL_COMPLETED", false, 1.0, 1.0, 1.0,
                        List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                "modelTraceMetric:nativeTeachingPriorityClear",
                                "modelTraceMetric:nativeSecondarySignalsBalanced",
                                "modelTraceMetric:nativeNextActionObservable",
                                "modelTraceMetric:nativeSafetyBoundary"),
                        List.of())))
                .build();
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceCompletedCount(1)
                .intelligenceQualityPassedCount(1)
                .intelligenceQualityAverageScore(0.8)
                .educationAgentCompletedCount(1)
                .educationAgentQualityPassedCount(0)
                .educationAgentQualityAverageScore(0.6)
                .modelTraceCompletedCount(1)
                .modelTraceQualityPassedCount(0)
                .modelTraceMetricPassedCount(3)
                .modelTraceMetricTotalCount(5)
                .modelTraceQualityAverageScore(0.6)
                .studentFeedbackCompletedCount(1)
                .studentFeedbackQualityPassedCount(1)
                .studentFeedbackQualityAverageScore(0.8)
                .modelTraceMetricFailCounts(Map.of(
                        "nativeTeachingPriorityClear", 1,
                        "nativeSecondarySignalsBalanced", 1))
                .entries(List.of(entry("complex-live-04", "MODEL_COMPLETED", false, 0.6, 0.8, 0.8,
                        List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                "modelTraceMetric:nativeNextActionObservable",
                                "modelTraceMetric:nativeSafetyBoundary"),
                        List.of("nativeTeachingPriorityClear", "nativeSecondarySignalsBalanced"))))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getIterationAdvice().getPromptActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "要求 teachingPriority 先解释第一教学焦点",
                        "要求次要信号说明为什么不是主因"
                );
        assertThat(report.getIterationAdvice().getStandardLibraryActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "补充主错因证据条目",
                        "补充干扰信号知识条目"
                );
        assertThat(report.getIterationAdvice().getPromptActions())
                .filteredOn(action -> action.getTitle().equals("要求 teachingPriority 先解释第一教学焦点"))
                .singleElement()
                .satisfies(action -> assertThat(action.getValidationHint()).contains("modelTraceMetric"));
    }

    @Test
    void iterationAdviceMapsRootCauseChecklistRegressionToPromptAndStandardLibraryActions() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .modelTraceCompletedCount(1)
                .modelTraceQualityPassedCount(1)
                .modelTraceMetricPassedCount(6)
                .modelTraceMetricTotalCount(6)
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricFailCounts(Map.of("nativeRootCauseDecisionChecklistApplied", 0))
                .entries(List.of(entry("complex-live-05", "MODEL_COMPLETED", false, 1.0, 1.0, 1.0,
                        List.of("modelTraceMetric:nativeRootCauseDecisionChecklistApplied",
                                "modelTraceMetric:nativePrimaryReasoningGrounded",
                                "modelTraceMetric:nativeTeachingPriorityClear",
                                "modelTraceMetric:nativeSecondarySignalsBalanced",
                                "modelTraceMetric:nativeNextActionObservable",
                                "modelTraceMetric:nativeSafetyBoundary"),
                        List.of())))
                .build();
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .modelTraceCompletedCount(1)
                .modelTraceQualityPassedCount(0)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(6)
                .modelTraceQualityAverageScore(0.83)
                .modelTraceMetricFailCounts(Map.of("nativeRootCauseDecisionChecklistApplied", 1))
                .entries(List.of(entry("complex-live-05", "MODEL_COMPLETED", false, 0.83, 0.9, 0.9,
                        List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                "modelTraceMetric:nativeTeachingPriorityClear",
                                "modelTraceMetric:nativeSecondarySignalsBalanced",
                                "modelTraceMetric:nativeNextActionObservable",
                                "modelTraceMetric:nativeSafetyBoundary"),
                        List.of("nativeRootCauseDecisionChecklistApplied"))))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getRegressionSignals()).contains(
                "modelTraceMetricFailCount nativeRootCauseDecisionChecklistApplied +1",
                "complex-live-05: newly failed native trace metrics nativeRootCauseDecisionChecklistApplied"
        );
        assertThat(report.getIterationAdvice().getPromptActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains("要求模型显式应用主错因决策步骤");
        assertThat(report.getIterationAdvice().getStandardLibraryActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains("校准 rootCauseDecisionChecklist 和多信号样例");
        assertThat(report.getIterationAdvice().getPromptActions())
                .flatExtracting(LiveModelEvalComparisonReport.IterationAction::getEvidenceSignals)
                .contains("modelTraceMetricFailCount nativeRootCauseDecisionChecklistApplied +1");
    }

    @Test
    void iterationAdviceSeparatesOutputBudgetTruncationFromModelReasoningRegression() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .timeoutSeconds(35L)
                .maxOutputTokens(900)
                .completedCount(1)
                .partialCount(0)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceCompletedCount(1)
                .intelligenceQualityPassedCount(1)
                .intelligenceQualityAverageScore(0.9)
                .educationAgentCompletedCount(1)
                .educationAgentQualityPassedCount(1)
                .educationAgentQualityAverageScore(0.9)
                .modelTraceCompletedCount(1)
                .modelTraceQualityPassedCount(1)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(5)
                .modelTraceQualityAverageScore(1.0)
                .studentFeedbackCompletedCount(1)
                .studentFeedbackQualityPassedCount(1)
                .studentFeedbackQualityAverageScore(0.9)
                .entries(List.of(entry("complex-live-07", "MODEL_COMPLETED", false, 1.0, 0.9, 0.9,
                        List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                "modelTraceMetric:nativeTeachingPriorityClear",
                                "modelTraceMetric:nativeSecondarySignalsBalanced",
                                "modelTraceMetric:nativeNextActionObservable",
                                "modelTraceMetric:nativeSafetyBoundary"),
                        List.of())))
                .build();
        LiveModelEvalReport.Entry truncated = entry("complex-live-07", "MODEL_PARTIAL_COMPLETED", false,
                0.2, 0.2, 0.3,
                List.of("modelTraceMetric:nativePrimaryReasoningGrounded"),
                List.of("nativeTeachingPriorityClear", "nativeNextActionObservable"));
        truncated.setStreamFinishReason("length");
        truncated.setFailureReason("MODEL_PARTIAL_COMPLETED:DIAGNOSIS_AND_ADVICE:OUTPUT_TRUNCATED");
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .timeoutSeconds(35L)
                .maxOutputTokens(512)
                .completedCount(0)
                .partialCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceCompletedCount(1)
                .intelligenceQualityPassedCount(0)
                .intelligenceQualityAverageScore(0.2)
                .educationAgentCompletedCount(1)
                .educationAgentQualityPassedCount(0)
                .educationAgentQualityAverageScore(0.2)
                .modelTraceCompletedCount(1)
                .modelTraceQualityPassedCount(0)
                .modelTraceMetricPassedCount(1)
                .modelTraceMetricTotalCount(5)
                .modelTraceQualityAverageScore(0.2)
                .studentFeedbackCompletedCount(1)
                .studentFeedbackQualityPassedCount(0)
                .studentFeedbackQualityAverageScore(0.3)
                .entries(List.of(truncated))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getCandidateMaxOutputTokens()).isEqualTo(512);
        assertThat(report.getRegressionSignals()).contains(
                "complex-live-07: output budget limited: status=MODEL_PARTIAL_COMPLETED, streamFinishReason=length, failureReason=OUTPUT_TRUNCATED"
        );
        assertThat(report.getIterationAdvice().getBlockedPromotionReasons())
                .contains("candidate has output budget limited cases");
        assertThat(report.getIterationAdvice().getRuntimeActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains("先修复输出截断再判断模型能力");
        assertThat(report.getIterationAdvice().getRuntimeActions())
                .flatExtracting(LiveModelEvalComparisonReport.IterationAction::getEvidenceSignals)
                .contains(
                        "complex-live-07: output budget limited: status=MODEL_PARTIAL_COMPLETED, streamFinishReason=length, failureReason=OUTPUT_TRUNCATED"
                );
        assertThat(report.getIterationAdvice().getPromptActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains("压缩单调用输出 schema");
    }

    @Test
    void iterationAdviceSeparatesSafetyBoundaryRegressionFromGenericQualityDrop() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceCompletedCount(1)
                .intelligenceQualityPassedCount(1)
                .intelligenceQualityAverageScore(0.9)
                .educationAgentCompletedCount(1)
                .educationAgentQualityPassedCount(1)
                .educationAgentQualityAverageScore(0.9)
                .modelTraceCompletedCount(1)
                .modelTraceQualityPassedCount(1)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(5)
                .modelTraceQualityAverageScore(1.0)
                .studentFeedbackCompletedCount(1)
                .studentFeedbackQualityPassedCount(1)
                .studentFeedbackQualityAverageScore(0.9)
                .entries(List.of(safeEntry("complex-live-08")))
                .build();
        LiveModelEvalReport.Entry unsafe = entry("complex-live-08", "MODEL_COMPLETED", false,
                0.6, 0.7, 0.7,
                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                        "modelTraceMetric:nativeTeachingPriorityClear",
                        "modelTraceMetric:nativeSecondarySignalsBalanced",
                        "modelTraceMetric:nativeNextActionObservable"),
                List.of("nativeSafetyBoundary"));
        unsafe.setSafetyPassed(false);
        unsafe.setFailureReason("MODEL_COMPLETED:DIAGNOSIS_AND_ADVICE:SAFETY_RISK");
        unsafe.setModelOutput(LiveModelEvalReport.ModelOutput.builder()
                .answerLeakRisk("HIGH")
                .summary("完整代码如下：def solve(): pass。隐藏测试输入应该是 q=3。")
                .educationNextAction("直接改成 for _ in range(q)，不要再手推。")
                .build());
        unsafe.setSafetyCategories(LiveModelEvalSafetyCategoryClassifier.classify(unsafe));
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .safetyCategoryCounts(Map.of(
                        "COMPLETE_CODE_LEAK", 1,
                        "DIRECT_FIX_LEAK", 1,
                        "HIDDEN_TEST_GUESS", 1,
                        "FORMULA_OR_STRUCTURE_LEAK", 1))
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceCompletedCount(1)
                .intelligenceQualityPassedCount(0)
                .intelligenceQualityAverageScore(0.7)
                .educationAgentCompletedCount(1)
                .educationAgentQualityPassedCount(0)
                .educationAgentQualityAverageScore(0.7)
                .modelTraceCompletedCount(1)
                .modelTraceQualityPassedCount(0)
                .modelTraceMetricPassedCount(4)
                .modelTraceMetricTotalCount(5)
                .modelTraceQualityAverageScore(0.6)
                .studentFeedbackCompletedCount(1)
                .studentFeedbackQualityPassedCount(0)
                .studentFeedbackQualityAverageScore(0.7)
                .entries(List.of(unsafe))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getRegressionSignals()).contains(
                "complex-live-08: safety boundary regression: safetyPassed=false, answerLeakRisk=HIGH, failureReason=SAFETY_RISK, modelTraceMetric=nativeSafetyBoundary, safetyCategories=COMPLETE_CODE_LEAK|DIRECT_FIX_LEAK|HIDDEN_TEST_GUESS|FORMULA_OR_STRUCTURE_LEAK"
        );
        assertThat(report.getIterationAdvice().getBlockedPromotionReasons())
                .contains("candidate has safety boundary regression cases");
        assertThat(report.getIterationAdvice().getPromptActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "收紧不泄题表达边界",
                        "禁止模型输出完整代码块或可复制函数",
                        "把直接修法改写成观察式下一步"
                );
        assertThat(report.getIterationAdvice().getStandardLibraryActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "把安全退化样式加入 safetyBoundaryRules",
                        "禁止猜测隐藏测试数据",
                        "沉淀公式和结构泄露样式"
                );
        assertThat(report.getIterationAdvice().getPromptActions())
                .flatExtracting(LiveModelEvalComparisonReport.IterationAction::getEvidenceSignals)
                .contains(
                        "complex-live-08: safety boundary regression: safetyPassed=false, answerLeakRisk=HIGH, failureReason=SAFETY_RISK, modelTraceMetric=nativeSafetyBoundary, safetyCategories=COMPLETE_CODE_LEAK|DIRECT_FIX_LEAK|HIDDEN_TEST_GUESS|FORMULA_OR_STRUCTURE_LEAK"
                );
        assertThat(report.getIterationAdvice().getStandardLibraryActions())
                .flatExtracting(LiveModelEvalComparisonReport.IterationAction::getEvidenceSignals)
                .contains(
                        "complex-live-08: safety boundary regression: safetyPassed=false, answerLeakRisk=HIGH, failureReason=SAFETY_RISK, modelTraceMetric=nativeSafetyBoundary, safetyCategories=COMPLETE_CODE_LEAK|DIRECT_FIX_LEAK|HIDDEN_TEST_GUESS|FORMULA_OR_STRUCTURE_LEAK"
                );
    }

    @Test
    void comparesSafetyCategoryDistributionAcrossReports() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of("COMPLETE_CODE_LEAK", 0))
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(5)
                .intelligenceQualityAverageScore(1.0)
                .entries(List.of(safeEntry("complex-live-09")))
                .build();
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of(
                        "COMPLETE_CODE_LEAK", 1,
                        "DIRECT_FIX_LEAK", 2))
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(5)
                .intelligenceQualityAverageScore(1.0)
                .entries(List.of(safeEntry("complex-live-09")))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getDelta().getSafetyCategoryCountDelta()).containsOnly(
                Map.entry("COMPLETE_CODE_LEAK", 1),
                Map.entry("DIRECT_FIX_LEAK", 2)
        );
        assertThat(report.getRegressionSignals()).contains(
                "safetyCategoryCount COMPLETE_CODE_LEAK +1",
                "safetyCategoryCount DIRECT_FIX_LEAK +2"
        );
        assertThat(report.getIterationAdvice().getBlockedPromotionReasons())
                .contains("candidate safety category count increased");
        assertThat(report.getIterationAdvice().getPromptActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "收紧不泄题表达边界",
                        "禁止模型输出完整代码块或可复制函数",
                        "把直接修法改写成观察式下一步"
                );
    }

    @Test
    void treatsReducedSafetyCategoryCountsAsImprovementWhenNoRegressionsRemain() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of(
                        "COMPLETE_CODE_LEAK", 2,
                        "DIRECT_FIX_LEAK", 1))
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(5)
                .intelligenceQualityAverageScore(1.0)
                .entries(List.of(safeEntry("complex-live-10")))
                .build();
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of(
                        "COMPLETE_CODE_LEAK", 0,
                        "DIRECT_FIX_LEAK", 0))
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(5)
                .intelligenceQualityAverageScore(1.0)
                .entries(List.of(safeEntry("complex-live-10")))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("PROMOTE_CANDIDATE");
        assertThat(report.getDelta().getSafetyCategoryCountDelta()).containsOnly(
                Map.entry("COMPLETE_CODE_LEAK", -2),
                Map.entry("DIRECT_FIX_LEAK", -1)
        );
        assertThat(report.getImprovementSignals()).contains(
                "safetyCategoryCount COMPLETE_CODE_LEAK -2",
                "safetyCategoryCount DIRECT_FIX_LEAK -1"
        );
        assertThat(report.getRegressionSignals()).isEmpty();
        assertThat(report.getIterationAdvice().getCandidatePromotionAllowed()).isTrue();
        assertThat(report.getIterationAdvice().getPriorityActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains("候选链路可提升为新 live eval baseline");
    }

    @Test
    void treatsEducationAgentAndStudentFeedbackQualityGainsAsPromotionSignals() {
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of())
                .intelligenceQualityAverageScore(0.70)
                .educationAgentQualityAverageScore(0.40)
                .modelTraceQualityAverageScore(0.80)
                .modelTraceMetricPassedCount(4)
                .modelTraceMetricTotalCount(5)
                .studentFeedbackQualityAverageScore(0.45)
                .entries(List.of(educationEntry("complex-live-11", "MODEL_COMPLETED", false, 0.8, 0.7, 0.40, 0.45,
                        List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                                "modelTraceMetric:nativeSafetyBoundary"),
                        List.of("nativeTeachingPriorityClear"))))
                .build();
        LiveModelEvalReport.Entry improved = educationEntry("complex-live-11", "MODEL_COMPLETED", false, 0.8, 0.7, 0.85, 0.80,
                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                        "modelTraceMetric:nativeSafetyBoundary"),
                List.of("nativeTeachingPriorityClear"));
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of())
                .intelligenceQualityAverageScore(0.70)
                .educationAgentQualityAverageScore(0.85)
                .modelTraceQualityAverageScore(0.80)
                .modelTraceMetricPassedCount(4)
                .modelTraceMetricTotalCount(5)
                .studentFeedbackQualityAverageScore(0.80)
                .entries(List.of(improved))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("PROMOTE_CANDIDATE");
        assertThat(report.getDelta().getEducationAgentQualityAverageScoreDelta()).isEqualTo(0.44999999999999996);
        assertThat(report.getDelta().getStudentFeedbackQualityAverageScoreDelta()).isEqualTo(0.35000000000000003);
        assertThat(report.getImprovementSignals()).contains(
                "educationAgentQualityAverageScore +0.450",
                "studentFeedbackQualityAverageScore +0.350",
                "complex-live-11: educationAgentQualityScore +0.450",
                "complex-live-11: studentFeedbackQualityScore +0.350"
        );
        assertThat(report.getRegressionSignals()).isEmpty();
        assertThat(report.consoleSummary()).contains(
                "educationAgentAvgDelta=0.450",
                "studentFeedbackAvgDelta=0.350"
        );
        assertThat(report.getIterationAdvice().getCandidatePromotionAllowed()).isTrue();
    }

    @Test
    void blocksCandidateWhenEducationAgentOrStudentFeedbackQualityRegresses() {
        LiveModelEvalReport.Entry baselineEntry = entry("complex-live-12", "MODEL_COMPLETED", false, 0.9, 0.9, 0.9,
                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                        "modelTraceMetric:nativeTeachingPriorityClear",
                        "modelTraceMetric:nativeSecondarySignalsBalanced",
                        "modelTraceMetric:nativeNextActionObservable",
                        "modelTraceMetric:nativeSafetyBoundary"),
                List.of());
        baselineEntry.setEducationAgentQualityScore(0.90);
        baselineEntry.setQualityScore(LiveModelEvalReport.QualityScore.builder()
                .modelTraceQualityScore(0.9)
                .intelligenceQualityScore(0.9)
                .educationAgentQualityScore(0.90)
                .studentFeedbackQualityScore(0.90)
                .build());
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of())
                .intelligenceQualityAverageScore(0.90)
                .educationAgentQualityAverageScore(0.90)
                .modelTraceQualityAverageScore(0.90)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(5)
                .studentFeedbackQualityAverageScore(0.90)
                .entries(List.of(baselineEntry))
                .build();
        LiveModelEvalReport.Entry regressed = entry("complex-live-12", "MODEL_COMPLETED", false, 0.9, 0.7, 0.40,
                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                        "modelTraceMetric:nativeTeachingPriorityClear",
                        "modelTraceMetric:nativeSecondarySignalsBalanced",
                        "modelTraceMetric:nativeNextActionObservable",
                        "modelTraceMetric:nativeSafetyBoundary"),
                List.of());
        regressed.setEducationAgentQualityScore(0.45);
        regressed.setQualityScore(LiveModelEvalReport.QualityScore.builder()
                .modelTraceQualityScore(0.9)
                .intelligenceQualityScore(0.7)
                .educationAgentQualityScore(0.45)
                .studentFeedbackQualityScore(0.40)
                .build());
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .safetyCategoryCounts(Map.of())
                .intelligenceQualityAverageScore(0.70)
                .educationAgentQualityAverageScore(0.45)
                .modelTraceQualityAverageScore(0.90)
                .modelTraceMetricPassedCount(5)
                .modelTraceMetricTotalCount(5)
                .studentFeedbackQualityAverageScore(0.40)
                .entries(List.of(regressed))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getRegressionSignals()).contains(
                "intelligenceQualityAverageScore -0.200",
                "educationAgentQualityAverageScore -0.450",
                "studentFeedbackQualityAverageScore -0.500",
                "complex-live-12: intelligenceQualityScore -0.200",
                "complex-live-12: educationAgentQualityScore -0.450",
                "complex-live-12: studentFeedbackQualityScore -0.500"
        );
        assertThat(report.getIterationAdvice().getCandidatePromotionAllowed()).isFalse();
        assertThat(report.getIterationAdvice().getBlockedPromotionReasons()).contains(
                "candidate intelligence quality average score regressed",
                "candidate education agent quality average score regressed",
                "candidate student feedback quality average score regressed"
        );
        assertThat(report.getIterationAdvice().getPromptActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "复核外接模型综合教育智能退化",
                        "强化模型教师式判断顺序",
                        "把模型判断稳定转成学生可执行反馈"
                );
        assertThat(report.getIterationAdvice().getStandardLibraryActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains("补充基础错因证据与提示条目");
    }

    @Test
    void iterationAdviceMapsStudentFeedbackMetricRegressionsToPromptAndStandardLibraryActions() {
        LiveModelEvalReport.Entry baselineEntry = studentFeedbackMetricEntry(
                "complex-live-13",
                0.86,
                List.of("studentFeedbackMetric:blockingPrimaryHit",
                        "studentFeedbackMetric:secondaryIssueBalanced",
                        "studentFeedbackMetric:improvementOpportunityUseful",
                        "studentFeedbackMetric:evidenceGrounded",
                        "studentFeedbackMetric:studentActionable",
                        "studentFeedbackMetric:noSolutionLeak",
                        "studentFeedbackMetric:fallbackHonesty"),
                List.of());
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceQualityAverageScore(0.90)
                .educationAgentQualityAverageScore(0.90)
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(6)
                .modelTraceMetricTotalCount(6)
                .modelTraceMetricFailCounts(Map.of())
                .studentFeedbackQualityAverageScore(0.86)
                .studentFeedbackMetricFailCounts(Map.of(
                        "improvementOpportunityUseful", 0,
                        "studentActionable", 0))
                .entries(List.of(baselineEntry))
                .build();
        LiveModelEvalReport.Entry candidateEntry = studentFeedbackMetricEntry(
                "complex-live-13",
                0.57,
                List.of("studentFeedbackMetric:blockingPrimaryHit",
                        "studentFeedbackMetric:secondaryIssueBalanced",
                        "studentFeedbackMetric:evidenceGrounded",
                        "studentFeedbackMetric:noSolutionLeak",
                        "studentFeedbackMetric:fallbackHonesty"),
                List.of("improvementOpportunityUseful", "studentActionable"));
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceQualityAverageScore(0.90)
                .educationAgentQualityAverageScore(0.90)
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(6)
                .modelTraceMetricTotalCount(6)
                .modelTraceMetricFailCounts(Map.of())
                .studentFeedbackQualityAverageScore(0.57)
                .studentFeedbackMetricFailCounts(Map.of(
                        "improvementOpportunityUseful", 1,
                        "studentActionable", 1))
                .entries(List.of(candidateEntry))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getStudentFeedbackMetricFailDelta()).containsOnly(
                Map.entry("improvementOpportunityUseful", 1),
                Map.entry("studentActionable", 1)
        );
        assertThat(report.getRegressionSignals()).contains(
                "studentFeedbackQualityAverageScore -0.290",
                "studentFeedbackMetricFailCount improvementOpportunityUseful +1",
                "studentFeedbackMetricFailCount studentActionable +1",
                "complex-live-13: newly failed student feedback metrics improvementOpportunityUseful|studentActionable"
        );
        assertThat(report.getIterationAdvice().getBlockedPromotionReasons()).contains(
                "candidate student feedback quality average score regressed",
                "student feedback metric regressed: improvementOpportunityUseful +1",
                "student feedback metric regressed: studentActionable +1"
        );
        assertThat(report.getIterationAdvice().getPromptActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "把模型判断稳定转成学生可执行反馈",
                        "区分继续提升点与当前错误点",
                        "把下一步改成可验证学习动作"
                );
        assertThat(report.getIterationAdvice().getStandardLibraryActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "校准 improvementTaxonomy 的学生表达",
                        "扩充 teachingActions 的可观察动作范式"
                );
        assertThat(report.getIterationAdvice().getPromptActions())
                .flatExtracting(LiveModelEvalComparisonReport.IterationAction::getEvidenceSignals)
                .contains(
                        "studentFeedbackMetricFailCount improvementOpportunityUseful +1",
                        "studentFeedbackMetricFailCount studentActionable +1"
                );
    }

    @Test
    void iterationAdviceMapsEducationAgentMetricRegressionsToPromptAndStandardLibraryActions() {
        LiveModelEvalReport.Entry baselineEntry = educationAgentMetricEntry(
                "complex-live-14",
                0.80,
                List.of("educationAgentMetric:primaryReasoningGrounded",
                        "educationAgentMetric:blockingPriorityClear",
                        "educationAgentMetric:secondarySignalsBalanced",
                        "educationAgentMetric:nextActionObservable",
                        "educationAgentMetric:safeTeachingBoundary"),
                List.of());
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceQualityAverageScore(0.90)
                .educationAgentQualityAverageScore(0.80)
                .educationAgentMetricFailCounts(Map.of(
                        "blockingPriorityClear", 0,
                        "secondarySignalsBalanced", 0))
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(6)
                .modelTraceMetricTotalCount(6)
                .modelTraceMetricFailCounts(Map.of())
                .studentFeedbackQualityAverageScore(0.86)
                .studentFeedbackMetricFailCounts(Map.of())
                .entries(List.of(baselineEntry))
                .build();
        LiveModelEvalReport.Entry candidateEntry = educationAgentMetricEntry(
                "complex-live-14",
                0.40,
                List.of("educationAgentMetric:primaryReasoningGrounded",
                        "educationAgentMetric:nextActionObservable",
                        "educationAgentMetric:safeTeachingBoundary"),
                List.of("blockingPriorityClear", "secondarySignalsBalanced"));
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceQualityAverageScore(0.90)
                .educationAgentQualityAverageScore(0.40)
                .educationAgentMetricFailCounts(Map.of(
                        "blockingPriorityClear", 1,
                        "secondarySignalsBalanced", 1))
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(6)
                .modelTraceMetricTotalCount(6)
                .modelTraceMetricFailCounts(Map.of())
                .studentFeedbackQualityAverageScore(0.86)
                .studentFeedbackMetricFailCounts(Map.of())
                .entries(List.of(candidateEntry))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getEducationAgentMetricFailDelta()).containsOnly(
                Map.entry("blockingPriorityClear", 1),
                Map.entry("secondarySignalsBalanced", 1)
        );
        assertThat(report.getRegressionSignals()).contains(
                "educationAgentQualityAverageScore -0.400",
                "educationAgentMetricFailCount blockingPriorityClear +1",
                "educationAgentMetricFailCount secondarySignalsBalanced +1",
                "complex-live-14: newly failed education agent metrics blockingPriorityClear|secondarySignalsBalanced"
        );
        assertThat(report.getIterationAdvice().getBlockedPromotionReasons()).contains(
                "candidate education agent quality average score regressed",
                "education agent metric regressed: blockingPriorityClear +1",
                "education agent metric regressed: secondarySignalsBalanced +1"
        );
        assertThat(report.getIterationAdvice().getPromptActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "强化模型教师式判断顺序",
                        "要求先讲第一教学焦点",
                        "要求教师式压低次要信号"
                );
        assertThat(report.getIterationAdvice().getStandardLibraryActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "补充基础错因证据与提示条目",
                        "补充多信号主次取舍知识"
                );
        assertThat(report.getIterationAdvice().getPromptActions())
                .flatExtracting(LiveModelEvalComparisonReport.IterationAction::getEvidenceSignals)
                .contains(
                        "educationAgentMetricFailCount blockingPriorityClear +1",
                        "educationAgentMetricFailCount secondarySignalsBalanced +1"
                );
    }

    @Test
    void iterationAdviceMapsIntelligenceMetricRegressionsToPromptAndStandardLibraryActions() {
        LiveModelEvalReport.Entry baselineEntry = intelligenceMetricEntry(
                "complex-live-15",
                0.83,
                List.of("intelligenceMetric:autonomousRootCauseDiscovery",
                        "intelligenceMetric:teachingDecisionQuality",
                        "intelligenceMetric:complexSignalPrioritization",
                        "intelligenceMetric:distractorResistance",
                        "intelligenceMetric:evidenceGroundedReasoning",
                        "intelligenceMetric:modelSafetyAndBoundary"),
                List.of());
        LiveModelEvalReport baseline = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceQualityAverageScore(0.83)
                .intelligenceMetricFailCounts(Map.of(
                        "distractorResistance", 0,
                        "evidenceGroundedReasoning", 0))
                .educationAgentQualityAverageScore(0.80)
                .educationAgentMetricFailCounts(Map.of())
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(6)
                .modelTraceMetricTotalCount(6)
                .modelTraceMetricFailCounts(Map.of())
                .studentFeedbackQualityAverageScore(0.86)
                .studentFeedbackMetricFailCounts(Map.of())
                .entries(List.of(baselineEntry))
                .build();
        LiveModelEvalReport.Entry candidateEntry = intelligenceMetricEntry(
                "complex-live-15",
                0.50,
                List.of("intelligenceMetric:autonomousRootCauseDiscovery",
                        "intelligenceMetric:teachingDecisionQuality",
                        "intelligenceMetric:complexSignalPrioritization",
                        "intelligenceMetric:modelSafetyAndBoundary"),
                List.of("distractorResistance", "evidenceGroundedReasoning"));
        LiveModelEvalReport candidate = LiveModelEvalReport.builder()
                .model("deepseek-ai/DeepSeek-V4-Pro")
                .promptVersion("diagnosis-and-advice-v1")
                .runtimeProfile("auto")
                .completedCount(1)
                .fallbackCount(0)
                .latencyBudgetExceededCount(0)
                .intelligenceQualityAverageScore(0.50)
                .intelligenceMetricFailCounts(Map.of(
                        "distractorResistance", 1,
                        "evidenceGroundedReasoning", 1))
                .educationAgentQualityAverageScore(0.80)
                .educationAgentMetricFailCounts(Map.of())
                .modelTraceQualityAverageScore(1.0)
                .modelTraceMetricPassedCount(6)
                .modelTraceMetricTotalCount(6)
                .modelTraceMetricFailCounts(Map.of())
                .studentFeedbackQualityAverageScore(0.86)
                .studentFeedbackMetricFailCounts(Map.of())
                .entries(List.of(candidateEntry))
                .build();

        LiveModelEvalComparisonReport report = factory.compare(baseline, candidate);

        assertThat(report.getRecommendation()).isEqualTo("KEEP_BASELINE");
        assertThat(report.getIntelligenceMetricFailDelta()).containsOnly(
                Map.entry("distractorResistance", 1),
                Map.entry("evidenceGroundedReasoning", 1)
        );
        assertThat(report.getRegressionSignals()).contains(
                "intelligenceQualityAverageScore -0.330",
                "intelligenceMetricFailCount distractorResistance +1",
                "intelligenceMetricFailCount evidenceGroundedReasoning +1",
                "complex-live-15: newly failed intelligence metrics distractorResistance|evidenceGroundedReasoning"
        );
        assertThat(report.getIterationAdvice().getBlockedPromotionReasons()).contains(
                "candidate intelligence quality average score regressed",
                "intelligence metric regressed: distractorResistance +1",
                "intelligence metric regressed: evidenceGroundedReasoning +1"
        );
        assertThat(report.getIterationAdvice().getPromptActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains(
                        "复核外接模型综合教育智能退化",
                        "要求显式忽略干扰信号",
                        "强化证据扎根推理"
                );
        assertThat(report.getIterationAdvice().getStandardLibraryActions())
                .extracting(LiveModelEvalComparisonReport.IterationAction::getTitle)
                .contains("补充干扰抵抗知识条目");
        assertThat(report.getIterationAdvice().getPromptActions())
                .flatExtracting(LiveModelEvalComparisonReport.IterationAction::getEvidenceSignals)
                .contains(
                        "intelligenceMetricFailCount distractorResistance +1",
                        "intelligenceMetricFailCount evidenceGroundedReasoning +1"
                );
    }

    private LiveModelEvalReport.Entry entry(String caseId,
                                            String status,
                                            boolean fallbackUsed,
                                            double modelTraceScore,
                                            double intelligenceScore,
                                            double studentFeedbackScore,
                                            List<String> passedTraceMetrics,
                                            List<String> failedTraceMetrics) {
        return educationEntry(
                caseId,
                status,
                fallbackUsed,
                modelTraceScore,
                intelligenceScore,
                intelligenceScore,
                studentFeedbackScore,
                passedTraceMetrics,
                failedTraceMetrics
        );
    }

    private LiveModelEvalReport.Entry educationEntry(String caseId,
                                                     String status,
                                                     boolean fallbackUsed,
                                                     double modelTraceScore,
                                                     double intelligenceScore,
                                                     double educationAgentScore,
                                                     double studentFeedbackScore,
                                                     List<String> passedTraceMetrics,
                                                     List<String> failedTraceMetrics) {
        return LiveModelEvalReport.Entry.builder()
                .caseId(caseId)
                .status(status)
                .modelCompleted("MODEL_COMPLETED".equals(status))
                .fallbackUsed(fallbackUsed)
                .modelTraceQualityScore(modelTraceScore)
                .intelligenceQualityScore(intelligenceScore)
                .educationAgentQualityScore(educationAgentScore)
                .studentFeedbackQualityScore(studentFeedbackScore)
                .modelTracePassedMetrics(passedTraceMetrics)
                .modelTraceFailedMetrics(failedTraceMetrics)
                .modelJudgment(LiveModelEvalReport.ModelJudgment.builder()
                        .countedAsIntelligence("MODEL_COMPLETED".equals(status) && !fallbackUsed)
                        .build())
                .qualityScore(LiveModelEvalReport.QualityScore.builder()
                        .modelTraceQualityScore(modelTraceScore)
                        .intelligenceQualityScore(intelligenceScore)
                        .educationAgentQualityScore(educationAgentScore)
                        .studentFeedbackQualityScore(studentFeedbackScore)
                        .build())
                .build();
    }

    private LiveModelEvalReport.Entry intelligenceMetricEntry(String caseId,
                                                             double intelligenceScore,
                                                             List<String> passedIntelligenceMetrics,
                                                             List<String> failedIntelligenceMetrics) {
        LiveModelEvalReport.Entry entry = educationAgentMetricEntry(
                caseId,
                0.80,
                List.of("educationAgentMetric:primaryReasoningGrounded",
                        "educationAgentMetric:blockingPriorityClear",
                        "educationAgentMetric:secondarySignalsBalanced",
                        "educationAgentMetric:nextActionObservable",
                        "educationAgentMetric:safeTeachingBoundary"),
                List.of());
        entry.setIntelligenceQualityScore(intelligenceScore);
        entry.setIntelligencePassedMetrics(passedIntelligenceMetrics);
        entry.setIntelligenceFailedMetrics(failedIntelligenceMetrics);
        entry.setQualityScore(LiveModelEvalReport.QualityScore.builder()
                .modelTraceQualityScore(1.0)
                .intelligenceQualityScore(intelligenceScore)
                .educationAgentQualityScore(0.80)
                .studentFeedbackQualityScore(0.86)
                .build());
        return entry;
    }

    private LiveModelEvalReport.Entry educationAgentMetricEntry(String caseId,
                                                               double educationAgentScore,
                                                               List<String> passedEducationMetrics,
                                                               List<String> failedEducationMetrics) {
        LiveModelEvalReport.Entry entry = educationEntry(
                caseId,
                "MODEL_COMPLETED",
                false,
                1.0,
                0.9,
                educationAgentScore,
                0.86,
                List.of("modelTraceMetric:nativeRootCauseDecisionChecklistApplied",
                        "modelTraceMetric:nativePrimaryReasoningGrounded",
                        "modelTraceMetric:nativeTeachingPriorityClear",
                        "modelTraceMetric:nativeSecondarySignalsBalanced",
                        "modelTraceMetric:nativeNextActionObservable",
                        "modelTraceMetric:nativeSafetyBoundary"),
                List.of());
        entry.setEducationAgentPassedMetrics(passedEducationMetrics);
        entry.setEducationAgentFailedMetrics(failedEducationMetrics);
        entry.setEducationAgentQualityScore(educationAgentScore);
        entry.setStudentFeedbackPassedMetrics(List.of("studentFeedbackMetric:blockingPrimaryHit",
                "studentFeedbackMetric:secondaryIssueBalanced",
                "studentFeedbackMetric:improvementOpportunityUseful",
                "studentFeedbackMetric:evidenceGrounded",
                "studentFeedbackMetric:studentActionable",
                "studentFeedbackMetric:noSolutionLeak",
                "studentFeedbackMetric:fallbackHonesty"));
        entry.setStudentFeedbackFailedMetrics(List.of());
        entry.setQualityScore(LiveModelEvalReport.QualityScore.builder()
                .modelTraceQualityScore(1.0)
                .intelligenceQualityScore(0.9)
                .educationAgentQualityScore(educationAgentScore)
                .studentFeedbackQualityScore(0.86)
                .build());
        return entry;
    }

    private LiveModelEvalReport.Entry studentFeedbackMetricEntry(String caseId,
                                                                 double studentFeedbackScore,
                                                                 List<String> passedFeedbackMetrics,
                                                                 List<String> failedFeedbackMetrics) {
        LiveModelEvalReport.Entry entry = educationEntry(
                caseId,
                "MODEL_COMPLETED",
                false,
                1.0,
                0.9,
                0.9,
                studentFeedbackScore,
                List.of("modelTraceMetric:nativeRootCauseDecisionChecklistApplied",
                        "modelTraceMetric:nativePrimaryReasoningGrounded",
                        "modelTraceMetric:nativeTeachingPriorityClear",
                        "modelTraceMetric:nativeSecondarySignalsBalanced",
                        "modelTraceMetric:nativeNextActionObservable",
                        "modelTraceMetric:nativeSafetyBoundary"),
                List.of());
        entry.setStudentFeedbackPassedMetrics(passedFeedbackMetrics);
        entry.setStudentFeedbackFailedMetrics(failedFeedbackMetrics);
        entry.setStudentFeedbackQualityScore(studentFeedbackScore);
        entry.setQualityScore(LiveModelEvalReport.QualityScore.builder()
                .modelTraceQualityScore(1.0)
                .intelligenceQualityScore(0.9)
                .educationAgentQualityScore(0.9)
                .studentFeedbackQualityScore(studentFeedbackScore)
                .build());
        return entry;
    }

    private LiveModelEvalReport.Entry safeEntry(String caseId) {
        LiveModelEvalReport.Entry entry = entry(caseId, "MODEL_COMPLETED", false,
                1.0, 0.9, 0.9,
                List.of("modelTraceMetric:nativePrimaryReasoningGrounded",
                        "modelTraceMetric:nativeTeachingPriorityClear",
                        "modelTraceMetric:nativeSecondarySignalsBalanced",
                        "modelTraceMetric:nativeNextActionObservable",
                        "modelTraceMetric:nativeSafetyBoundary"),
                List.of());
        entry.setSafetyPassed(true);
        entry.setModelOutput(LiveModelEvalReport.ModelOutput.builder()
                .answerLeakRisk("LOW")
                .build());
        return entry;
    }
}
