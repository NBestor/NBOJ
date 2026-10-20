package com.onlinejudge.submission.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LiveModelEvalComparisonReportFactory {

    private static final double SCORE_DELTA_THRESHOLD = 0.05;

    public LiveModelEvalComparisonReport compare(LiveModelEvalReport baseline,
                                                 LiveModelEvalReport candidate) {
        Map<String, LiveModelEvalReport.Entry> baselineEntries = entriesByCaseId(baseline);
        Map<String, LiveModelEvalReport.Entry> candidateEntries = entriesByCaseId(candidate);
        List<String> baselineOnly = sortedDifference(baselineEntries.keySet(), candidateEntries.keySet());
        List<String> candidateOnly = sortedDifference(candidateEntries.keySet(), baselineEntries.keySet());
        List<String> comparableCaseIds = baselineEntries.keySet().stream()
                .filter(candidateEntries::containsKey)
                .sorted()
                .toList();
        List<LiveModelEvalComparisonReport.CaseComparison> caseComparisons = comparableCaseIds.stream()
                .map(caseId -> compareCase(caseId, baselineEntries.get(caseId), candidateEntries.get(caseId)))
                .toList();

        LiveModelEvalComparisonReport.QualitySnapshot baselineQuality = qualitySnapshot(baseline);
        LiveModelEvalComparisonReport.QualitySnapshot candidateQuality = qualitySnapshot(candidate);
        LiveModelEvalComparisonReport.QualityDelta delta = qualityDelta(baselineQuality, candidateQuality);
        Map<String, Integer> intelligenceMetricFailDelta = metricDelta(
                baseline == null ? null : baseline.getIntelligenceMetricFailCounts(),
                candidate == null ? null : candidate.getIntelligenceMetricFailCounts()
        );
        Map<String, Integer> modelTraceMetricFailDelta = metricDelta(
                baseline == null ? null : baseline.getModelTraceMetricFailCounts(),
                candidate == null ? null : candidate.getModelTraceMetricFailCounts()
        );
        Map<String, Integer> educationAgentMetricFailDelta = metricDelta(
                baseline == null ? null : baseline.getEducationAgentMetricFailCounts(),
                candidate == null ? null : candidate.getEducationAgentMetricFailCounts()
        );
        Map<String, Integer> studentFeedbackMetricFailDelta = metricDelta(
                baseline == null ? null : baseline.getStudentFeedbackMetricFailCounts(),
                candidate == null ? null : candidate.getStudentFeedbackMetricFailCounts()
        );
        List<String> improvementSignals = improvementSignals(
                delta,
                intelligenceMetricFailDelta,
                modelTraceMetricFailDelta,
                educationAgentMetricFailDelta,
                studentFeedbackMetricFailDelta,
                caseComparisons);
        List<String> regressionSignals = regressionSignals(
                delta,
                baselineOnly,
                intelligenceMetricFailDelta,
                modelTraceMetricFailDelta,
                educationAgentMetricFailDelta,
                studentFeedbackMetricFailDelta,
                caseComparisons);
        String recommendation = recommendation(improvementSignals, regressionSignals, comparableCaseIds);
        LiveModelEvalComparisonReport.IterationAdvice iterationAdvice = iterationAdvice(
                recommendation,
                baselineOnly,
                candidateOnly,
                delta,
                intelligenceMetricFailDelta,
                modelTraceMetricFailDelta,
                educationAgentMetricFailDelta,
                studentFeedbackMetricFailDelta,
                improvementSignals,
                regressionSignals,
                caseComparisons
        );
        return LiveModelEvalComparisonReport.builder()
                .baselineModel(baseline == null ? "" : baseline.getModel())
                .candidateModel(candidate == null ? "" : candidate.getModel())
                .baselinePromptVersion(baseline == null ? "" : baseline.getPromptVersion())
                .candidatePromptVersion(candidate == null ? "" : candidate.getPromptVersion())
                .baselineRuntimeProfile(baseline == null ? "" : baseline.getRuntimeProfile())
                .candidateRuntimeProfile(candidate == null ? "" : candidate.getRuntimeProfile())
                .baselineTimeoutSeconds(baseline == null ? null : baseline.getTimeoutSeconds())
                .candidateTimeoutSeconds(candidate == null ? null : candidate.getTimeoutSeconds())
                .baselineMaxOutputTokens(baseline == null ? null : baseline.getMaxOutputTokens())
                .candidateMaxOutputTokens(candidate == null ? null : candidate.getMaxOutputTokens())
                .baselineCaseCount(baselineEntries.size())
                .candidateCaseCount(candidateEntries.size())
                .comparableCaseCount(comparableCaseIds.size())
                .baselineOnlyCaseCount(baselineOnly.size())
                .candidateOnlyCaseCount(candidateOnly.size())
                .baselineOnlyCaseIds(baselineOnly)
                .candidateOnlyCaseIds(candidateOnly)
                .baselineQuality(baselineQuality)
                .candidateQuality(candidateQuality)
                .delta(delta)
                .intelligenceMetricFailDelta(intelligenceMetricFailDelta)
                .modelTraceMetricFailDelta(modelTraceMetricFailDelta)
                .educationAgentMetricFailDelta(educationAgentMetricFailDelta)
                .studentFeedbackMetricFailDelta(studentFeedbackMetricFailDelta)
                .cases(caseComparisons)
                .improvementSignals(improvementSignals)
                .regressionSignals(regressionSignals)
                .iterationAdvice(iterationAdvice)
                .recommendation(recommendation)
                .build();
    }

    private Map<String, LiveModelEvalReport.Entry> entriesByCaseId(LiveModelEvalReport report) {
        if (report == null || report.getEntries() == null) {
            return Map.of();
        }
        return report.getEntries().stream()
                .filter(entry -> hasText(entry.getCaseId()))
                .collect(Collectors.toMap(
                        LiveModelEvalReport.Entry::getCaseId,
                        Function.identity(),
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ));
    }

    private LiveModelEvalComparisonReport.CaseComparison compareCase(String caseId,
                                                                     LiveModelEvalReport.Entry baseline,
                                                                     LiveModelEvalReport.Entry candidate) {
        List<String> baselineFailedIntelligenceMetrics = normalizeMetrics(baseline == null
                ? List.of()
                : baseline.getIntelligenceFailedMetrics(), "intelligenceMetric:");
        List<String> candidateFailedIntelligenceMetrics = normalizeMetrics(candidate == null
                ? List.of()
                : candidate.getIntelligenceFailedMetrics(), "intelligenceMetric:");
        List<String> baselineFailedMetrics = normalizeMetrics(baseline == null
                ? List.of()
                : baseline.getModelTraceFailedMetrics(), "modelTraceMetric:");
        List<String> candidateFailedMetrics = normalizeMetrics(candidate == null
                ? List.of()
                : candidate.getModelTraceFailedMetrics(), "modelTraceMetric:");
        List<String> baselineFailedEducationAgentMetrics = normalizeMetrics(baseline == null
                ? List.of()
                : baseline.getEducationAgentFailedMetrics(), "educationAgentMetric:");
        List<String> candidateFailedEducationAgentMetrics = normalizeMetrics(candidate == null
                ? List.of()
                : candidate.getEducationAgentFailedMetrics(), "educationAgentMetric:");
        List<String> baselineFailedStudentFeedbackMetrics = normalizeMetrics(baseline == null
                ? List.of()
                : baseline.getStudentFeedbackFailedMetrics(), "studentFeedbackMetric:");
        List<String> candidateFailedStudentFeedbackMetrics = normalizeMetrics(candidate == null
                ? List.of()
                : candidate.getStudentFeedbackFailedMetrics(), "studentFeedbackMetric:");
        Set<String> baselinePassedMetrics = new LinkedHashSet<>(normalizeMetrics(baseline == null
                ? List.of()
                : baseline.getModelTracePassedMetrics(), "modelTraceMetric:"));
        Set<String> candidatePassedMetrics = new LinkedHashSet<>(normalizeMetrics(candidate == null
                ? List.of()
                : candidate.getModelTracePassedMetrics(), "modelTraceMetric:"));
        Set<String> baselinePassedEducationAgentMetrics = new LinkedHashSet<>(normalizeMetrics(baseline == null
                ? List.of()
                : baseline.getEducationAgentPassedMetrics(), "educationAgentMetric:"));
        Set<String> candidatePassedEducationAgentMetrics = new LinkedHashSet<>(normalizeMetrics(candidate == null
                ? List.of()
                : candidate.getEducationAgentPassedMetrics(), "educationAgentMetric:"));
        Set<String> baselinePassedStudentFeedbackMetrics = new LinkedHashSet<>(normalizeMetrics(baseline == null
                ? List.of()
                : baseline.getStudentFeedbackPassedMetrics(), "studentFeedbackMetric:"));
        Set<String> candidatePassedStudentFeedbackMetrics = new LinkedHashSet<>(normalizeMetrics(candidate == null
                ? List.of()
                : candidate.getStudentFeedbackPassedMetrics(), "studentFeedbackMetric:"));
        Set<String> baselinePassedIntelligenceMetrics = new LinkedHashSet<>(normalizeMetrics(baseline == null
                ? List.of()
                : baseline.getIntelligencePassedMetrics(), "intelligenceMetric:"));
        Set<String> candidatePassedIntelligenceMetrics = new LinkedHashSet<>(normalizeMetrics(candidate == null
                ? List.of()
                : candidate.getIntelligencePassedMetrics(), "intelligenceMetric:"));
        List<String> newlyPassedIntelligence = candidatePassedIntelligenceMetrics.stream()
                .filter(metric -> !baselinePassedIntelligenceMetrics.contains(metric))
                .sorted()
                .toList();
        List<String> newlyFailedIntelligence = baselinePassedIntelligenceMetrics.stream()
                .filter(metric -> !candidatePassedIntelligenceMetrics.contains(metric))
                .sorted()
                .toList();
        List<String> newlyPassed = candidatePassedMetrics.stream()
                .filter(metric -> !baselinePassedMetrics.contains(metric))
                .sorted()
                .toList();
        List<String> newlyFailed = baselinePassedMetrics.stream()
                .filter(metric -> !candidatePassedMetrics.contains(metric))
                .sorted()
                .toList();
        List<String> newlyPassedEducationAgent = candidatePassedEducationAgentMetrics.stream()
                .filter(metric -> !baselinePassedEducationAgentMetrics.contains(metric))
                .sorted()
                .toList();
        List<String> newlyFailedEducationAgent = baselinePassedEducationAgentMetrics.stream()
                .filter(metric -> !candidatePassedEducationAgentMetrics.contains(metric))
                .sorted()
                .toList();
        List<String> newlyPassedStudentFeedback = candidatePassedStudentFeedbackMetrics.stream()
                .filter(metric -> !baselinePassedStudentFeedbackMetrics.contains(metric))
                .sorted()
                .toList();
        List<String> newlyFailedStudentFeedback = baselinePassedStudentFeedbackMetrics.stream()
                .filter(metric -> !candidatePassedStudentFeedbackMetrics.contains(metric))
                .sorted()
                .toList();
        double rubricChainDelta = delta(score(baseline, ScoreKind.RUBRIC_CHAIN), score(candidate, ScoreKind.RUBRIC_CHAIN));
        double traceDelta = delta(score(baseline, ScoreKind.MODEL_TRACE), score(candidate, ScoreKind.MODEL_TRACE));
        double intelligenceDelta = delta(score(baseline, ScoreKind.INTELLIGENCE), score(candidate, ScoreKind.INTELLIGENCE));
        double educationAgentDelta = delta(
                score(baseline, ScoreKind.EDUCATION_AGENT),
                score(candidate, ScoreKind.EDUCATION_AGENT));
        double feedbackDelta = delta(score(baseline, ScoreKind.STUDENT_FEEDBACK), score(candidate, ScoreKind.STUDENT_FEEDBACK));
        List<String> improvements = new ArrayList<>();
        List<String> regressions = new ArrayList<>();
        if (!countedAsModel(baseline) && countedAsModel(candidate)) {
            improvements.add(caseId + ": candidate restored real model completion");
        }
        if (countedAsModel(baseline) && !countedAsModel(candidate)) {
            regressions.add(caseId + ": candidate no longer counted as real model completion");
        }
        if (Boolean.TRUE.equals(baseline == null ? null : baseline.getFallbackUsed())
                && !Boolean.TRUE.equals(candidate == null ? null : candidate.getFallbackUsed())) {
            improvements.add(caseId + ": fallback removed");
        }
        if (!Boolean.TRUE.equals(baseline == null ? null : baseline.getFallbackUsed())
                && Boolean.TRUE.equals(candidate == null ? null : candidate.getFallbackUsed())) {
            regressions.add(caseId + ": fallback introduced");
        }
        String candidateRuntimeBlockerEvidence = externalRuntimeBlockerEvidence(candidate);
        if (externalRuntimeBlockerEvidence(baseline).isBlank() && !candidateRuntimeBlockerEvidence.isBlank()) {
            regressions.add(caseId + ": external runtime blocked: " + candidateRuntimeBlockerEvidence);
        }
        String candidateOutputBudgetEvidence = outputBudgetEvidence(candidate);
        if (outputBudgetEvidence(baseline).isBlank() && !candidateOutputBudgetEvidence.isBlank()) {
            regressions.add(caseId + ": output budget limited: " + candidateOutputBudgetEvidence);
        }
        String candidateSafetyBoundaryEvidence = safetyBoundaryEvidence(candidate);
        if (safetyBoundaryEvidence(baseline).isBlank() && !candidateSafetyBoundaryEvidence.isBlank()) {
            regressions.add(caseId + ": safety boundary regression: " + candidateSafetyBoundaryEvidence);
        }
        if (rubricChainDelta >= SCORE_DELTA_THRESHOLD) {
            improvements.add(caseId + ": rubricChainScore +" + format(rubricChainDelta));
        }
        if (rubricChainDelta <= -SCORE_DELTA_THRESHOLD) {
            regressions.add(caseId + ": rubricChainScore " + format(rubricChainDelta));
        }
        if (traceDelta >= SCORE_DELTA_THRESHOLD) {
            improvements.add(caseId + ": modelTraceQualityScore +" + format(traceDelta));
        }
        if (traceDelta <= -SCORE_DELTA_THRESHOLD) {
            regressions.add(caseId + ": modelTraceQualityScore " + format(traceDelta));
        }
        if (intelligenceDelta >= SCORE_DELTA_THRESHOLD) {
            improvements.add(caseId + ": intelligenceQualityScore +" + format(intelligenceDelta));
        }
        if (intelligenceDelta <= -SCORE_DELTA_THRESHOLD) {
            regressions.add(caseId + ": intelligenceQualityScore " + format(intelligenceDelta));
        }
        if (educationAgentDelta >= SCORE_DELTA_THRESHOLD) {
            improvements.add(caseId + ": educationAgentQualityScore +" + format(educationAgentDelta));
        }
        if (educationAgentDelta <= -SCORE_DELTA_THRESHOLD) {
            regressions.add(caseId + ": educationAgentQualityScore " + format(educationAgentDelta));
        }
        if (feedbackDelta >= SCORE_DELTA_THRESHOLD) {
            improvements.add(caseId + ": studentFeedbackQualityScore +" + format(feedbackDelta));
        }
        if (feedbackDelta <= -SCORE_DELTA_THRESHOLD) {
            regressions.add(caseId + ": studentFeedbackQualityScore " + format(feedbackDelta));
        }
        if (!newlyPassed.isEmpty()) {
            improvements.add(caseId + ": newly passed native trace metrics " + String.join("|", newlyPassed));
        }
        if (!newlyFailed.isEmpty()) {
            regressions.add(caseId + ": newly failed native trace metrics " + String.join("|", newlyFailed));
        }
        if (!newlyPassedIntelligence.isEmpty()) {
            improvements.add(caseId + ": newly passed intelligence metrics "
                    + String.join("|", newlyPassedIntelligence));
        }
        if (!newlyFailedIntelligence.isEmpty()) {
            regressions.add(caseId + ": newly failed intelligence metrics "
                    + String.join("|", newlyFailedIntelligence));
        }
        if (!newlyPassedEducationAgent.isEmpty()) {
            improvements.add(caseId + ": newly passed education agent metrics "
                    + String.join("|", newlyPassedEducationAgent));
        }
        if (!newlyFailedEducationAgent.isEmpty()) {
            regressions.add(caseId + ": newly failed education agent metrics "
                    + String.join("|", newlyFailedEducationAgent));
        }
        if (!newlyPassedStudentFeedback.isEmpty()) {
            improvements.add(caseId + ": newly passed student feedback metrics "
                    + String.join("|", newlyPassedStudentFeedback));
        }
        if (!newlyFailedStudentFeedback.isEmpty()) {
            regressions.add(caseId + ": newly failed student feedback metrics "
                    + String.join("|", newlyFailedStudentFeedback));
        }
        return LiveModelEvalComparisonReport.CaseComparison.builder()
                .caseId(caseId)
                .baselineStatus(baseline == null ? "" : baseline.getStatus())
                .candidateStatus(candidate == null ? "" : candidate.getStatus())
                .baselineCountedAsModel(countedAsModel(baseline))
                .candidateCountedAsModel(countedAsModel(candidate))
                .baselineFallbackUsed(Boolean.TRUE.equals(baseline == null ? null : baseline.getFallbackUsed()))
                .candidateFallbackUsed(Boolean.TRUE.equals(candidate == null ? null : candidate.getFallbackUsed()))
                .baselineRubricChainScore(score(baseline, ScoreKind.RUBRIC_CHAIN))
                .candidateRubricChainScore(score(candidate, ScoreKind.RUBRIC_CHAIN))
                .rubricChainScoreDelta(rubricChainDelta)
                .baselineFailedRubricStages(baseline == null || baseline.getRubricChainFailedStages() == null
                        ? List.of()
                        : baseline.getRubricChainFailedStages())
                .candidateFailedRubricStages(candidate == null || candidate.getRubricChainFailedStages() == null
                        ? List.of()
                        : candidate.getRubricChainFailedStages())
                .baselineModelTraceQualityScore(score(baseline, ScoreKind.MODEL_TRACE))
                .candidateModelTraceQualityScore(score(candidate, ScoreKind.MODEL_TRACE))
                .modelTraceQualityScoreDelta(traceDelta)
                .baselineIntelligenceQualityScore(score(baseline, ScoreKind.INTELLIGENCE))
                .candidateIntelligenceQualityScore(score(candidate, ScoreKind.INTELLIGENCE))
                .intelligenceQualityScoreDelta(intelligenceDelta)
                .baselineEducationAgentQualityScore(score(baseline, ScoreKind.EDUCATION_AGENT))
                .candidateEducationAgentQualityScore(score(candidate, ScoreKind.EDUCATION_AGENT))
                .educationAgentQualityScoreDelta(educationAgentDelta)
                .baselineStudentFeedbackQualityScore(score(baseline, ScoreKind.STUDENT_FEEDBACK))
                .candidateStudentFeedbackQualityScore(score(candidate, ScoreKind.STUDENT_FEEDBACK))
                .studentFeedbackQualityScoreDelta(feedbackDelta)
                .baselineFailedIntelligenceMetrics(baselineFailedIntelligenceMetrics)
                .candidateFailedIntelligenceMetrics(candidateFailedIntelligenceMetrics)
                .newlyPassedIntelligenceMetrics(newlyPassedIntelligence)
                .newlyFailedIntelligenceMetrics(newlyFailedIntelligence)
                .baselineFailedModelTraceMetrics(baselineFailedMetrics)
                .candidateFailedModelTraceMetrics(candidateFailedMetrics)
                .newlyPassedModelTraceMetrics(newlyPassed)
                .newlyFailedModelTraceMetrics(newlyFailed)
                .baselineFailedEducationAgentMetrics(baselineFailedEducationAgentMetrics)
                .candidateFailedEducationAgentMetrics(candidateFailedEducationAgentMetrics)
                .newlyPassedEducationAgentMetrics(newlyPassedEducationAgent)
                .newlyFailedEducationAgentMetrics(newlyFailedEducationAgent)
                .baselineFailedStudentFeedbackMetrics(baselineFailedStudentFeedbackMetrics)
                .candidateFailedStudentFeedbackMetrics(candidateFailedStudentFeedbackMetrics)
                .newlyPassedStudentFeedbackMetrics(newlyPassedStudentFeedback)
                .newlyFailedStudentFeedbackMetrics(newlyFailedStudentFeedback)
                .improvementSignals(improvements)
                .regressionSignals(regressions)
                .build();
    }

    private LiveModelEvalComparisonReport.QualitySnapshot qualitySnapshot(LiveModelEvalReport report) {
        return LiveModelEvalComparisonReport.QualitySnapshot.builder()
                .completedCount(safeInt(report == null ? null : report.getCompletedCount()))
                .partialCount(safeInt(report == null ? null : report.getPartialCount()))
                .fallbackCount(safeInt(report == null ? null : report.getFallbackCount()))
                .timeoutCount(safeInt(report == null ? null : report.getTimeoutCount()))
                .latencyBudgetExceededCount(safeInt(report == null ? null : report.getLatencyBudgetExceededCount()))
                .safetyCategoryCounts(report == null || report.getSafetyCategoryCounts() == null
                        ? Map.of()
                        : report.getSafetyCategoryCounts())
                .rubricChainEvaluatedCount(safeInt(report == null ? null : report.getRubricChainEvaluatedCount()))
                .rubricChainPassedCount(safeInt(report == null ? null : report.getRubricChainPassedCount()))
                .rubricChainAverageScore(safeDouble(report == null ? null : report.getRubricChainAverageScore()))
                .intelligenceCompletedCount(safeInt(report == null ? null : report.getIntelligenceCompletedCount()))
                .intelligenceQualityPassedCount(safeInt(report == null ? null : report.getIntelligenceQualityPassedCount()))
                .intelligenceQualityAverageScore(safeDouble(report == null ? null : report.getIntelligenceQualityAverageScore()))
                .educationAgentCompletedCount(safeInt(report == null ? null : report.getEducationAgentCompletedCount()))
                .educationAgentQualityPassedCount(safeInt(report == null ? null : report.getEducationAgentQualityPassedCount()))
                .educationAgentQualityAverageScore(safeDouble(report == null ? null : report.getEducationAgentQualityAverageScore()))
                .modelTraceCompletedCount(safeInt(report == null ? null : report.getModelTraceCompletedCount()))
                .modelTraceQualityPassedCount(safeInt(report == null ? null : report.getModelTraceQualityPassedCount()))
                .modelTraceMetricPassedCount(safeInt(report == null ? null : report.getModelTraceMetricPassedCount()))
                .modelTraceMetricTotalCount(safeInt(report == null ? null : report.getModelTraceMetricTotalCount()))
                .modelTraceMetricPassRate(ratio(
                        safeInt(report == null ? null : report.getModelTraceMetricPassedCount()),
                        safeInt(report == null ? null : report.getModelTraceMetricTotalCount())))
                .modelTraceQualityAverageScore(safeDouble(report == null ? null : report.getModelTraceQualityAverageScore()))
                .studentFeedbackCompletedCount(safeInt(report == null ? null : report.getStudentFeedbackCompletedCount()))
                .studentFeedbackQualityPassedCount(safeInt(report == null ? null : report.getStudentFeedbackQualityPassedCount()))
                .studentFeedbackQualityAverageScore(safeDouble(report == null ? null : report.getStudentFeedbackQualityAverageScore()))
                .build();
    }

    private LiveModelEvalComparisonReport.QualityDelta qualityDelta(
            LiveModelEvalComparisonReport.QualitySnapshot baseline,
            LiveModelEvalComparisonReport.QualitySnapshot candidate) {
        return LiveModelEvalComparisonReport.QualityDelta.builder()
                .completedCountDelta(intDelta(baseline.getCompletedCount(), candidate.getCompletedCount()))
                .fallbackCountDelta(intDelta(baseline.getFallbackCount(), candidate.getFallbackCount()))
                .latencyBudgetExceededCountDelta(intDelta(
                        baseline.getLatencyBudgetExceededCount(),
                        candidate.getLatencyBudgetExceededCount()))
                .safetyCategoryCountDelta(metricDelta(
                        baseline.getSafetyCategoryCounts(),
                        candidate.getSafetyCategoryCounts()))
                .rubricChainEvaluatedCountDelta(intDelta(
                        baseline.getRubricChainEvaluatedCount(),
                        candidate.getRubricChainEvaluatedCount()))
                .rubricChainPassedCountDelta(intDelta(
                        baseline.getRubricChainPassedCount(),
                        candidate.getRubricChainPassedCount()))
                .rubricChainAverageScoreDelta(doubleDelta(
                        baseline.getRubricChainAverageScore(),
                        candidate.getRubricChainAverageScore()))
                .intelligenceCompletedCountDelta(intDelta(
                        baseline.getIntelligenceCompletedCount(),
                        candidate.getIntelligenceCompletedCount()))
                .intelligenceQualityPassedCountDelta(intDelta(
                        baseline.getIntelligenceQualityPassedCount(),
                        candidate.getIntelligenceQualityPassedCount()))
                .intelligenceQualityAverageScoreDelta(doubleDelta(
                        baseline.getIntelligenceQualityAverageScore(),
                        candidate.getIntelligenceQualityAverageScore()))
                .educationAgentCompletedCountDelta(intDelta(
                        baseline.getEducationAgentCompletedCount(),
                        candidate.getEducationAgentCompletedCount()))
                .educationAgentQualityPassedCountDelta(intDelta(
                        baseline.getEducationAgentQualityPassedCount(),
                        candidate.getEducationAgentQualityPassedCount()))
                .educationAgentQualityAverageScoreDelta(doubleDelta(
                        baseline.getEducationAgentQualityAverageScore(),
                        candidate.getEducationAgentQualityAverageScore()))
                .modelTraceCompletedCountDelta(intDelta(
                        baseline.getModelTraceCompletedCount(),
                        candidate.getModelTraceCompletedCount()))
                .modelTraceQualityPassedCountDelta(intDelta(
                        baseline.getModelTraceQualityPassedCount(),
                        candidate.getModelTraceQualityPassedCount()))
                .modelTraceMetricPassRateDelta(doubleDelta(
                        baseline.getModelTraceMetricPassRate(),
                        candidate.getModelTraceMetricPassRate()))
                .modelTraceQualityAverageScoreDelta(doubleDelta(
                        baseline.getModelTraceQualityAverageScore(),
                        candidate.getModelTraceQualityAverageScore()))
                .studentFeedbackCompletedCountDelta(intDelta(
                        baseline.getStudentFeedbackCompletedCount(),
                        candidate.getStudentFeedbackCompletedCount()))
                .studentFeedbackQualityPassedCountDelta(intDelta(
                        baseline.getStudentFeedbackQualityPassedCount(),
                        candidate.getStudentFeedbackQualityPassedCount()))
                .studentFeedbackQualityAverageScoreDelta(doubleDelta(
                        baseline.getStudentFeedbackQualityAverageScore(),
                        candidate.getStudentFeedbackQualityAverageScore()))
                .build();
    }

    private List<String> improvementSignals(LiveModelEvalComparisonReport.QualityDelta delta,
                                            Map<String, Integer> intelligenceMetricFailDelta,
                                            Map<String, Integer> metricFailDelta,
                                            Map<String, Integer> educationAgentMetricFailDelta,
                                            Map<String, Integer> studentFeedbackMetricFailDelta,
                                            List<LiveModelEvalComparisonReport.CaseComparison> cases) {
        List<String> signals = new ArrayList<>();
        if (delta.getRubricChainAverageScoreDelta() > 0.0) {
            signals.add("rubricChainAverageScore +" + format(delta.getRubricChainAverageScoreDelta()));
        }
        if (delta.getRubricChainPassedCountDelta() > 0) {
            signals.add("rubricChainPassedCount +" + delta.getRubricChainPassedCountDelta());
        }
        if (delta.getModelTraceQualityAverageScoreDelta() > 0.0) {
            signals.add("modelTraceQualityAverageScore +" + format(delta.getModelTraceQualityAverageScoreDelta()));
        }
        if (delta.getModelTraceMetricPassRateDelta() > 0.0) {
            signals.add("modelTraceMetricPassRate +" + format(delta.getModelTraceMetricPassRateDelta()));
        }
        if (delta.getIntelligenceQualityAverageScoreDelta() > 0.0) {
            signals.add("intelligenceQualityAverageScore +" + format(delta.getIntelligenceQualityAverageScoreDelta()));
        }
        if (delta.getEducationAgentQualityAverageScoreDelta() > 0.0) {
            signals.add("educationAgentQualityAverageScore +" + format(delta.getEducationAgentQualityAverageScoreDelta()));
        }
        if (delta.getStudentFeedbackQualityAverageScoreDelta() > 0.0) {
            signals.add("studentFeedbackQualityAverageScore +" + format(delta.getStudentFeedbackQualityAverageScoreDelta()));
        }
        if (delta.getSafetyCategoryCountDelta() != null) {
            delta.getSafetyCategoryCountDelta().entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue() < 0)
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> "safetyCategoryCount " + entry.getKey() + " " + entry.getValue())
                    .forEach(signals::add);
        }
        intelligenceMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() < 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "intelligenceMetricFailCount " + entry.getKey() + " " + entry.getValue())
                .forEach(signals::add);
        metricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() < 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "modelTraceMetricFailCount " + entry.getKey() + " " + entry.getValue())
                .forEach(signals::add);
        educationAgentMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() < 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "educationAgentMetricFailCount " + entry.getKey() + " " + entry.getValue())
                .forEach(signals::add);
        studentFeedbackMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() < 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "studentFeedbackMetricFailCount " + entry.getKey() + " " + entry.getValue())
                .forEach(signals::add);
        cases.stream()
                .flatMap(entry -> entry.getImprovementSignals().stream())
                .limit(12)
                .forEach(signals::add);
        return signals.stream().distinct().toList();
    }

    private List<String> regressionSignals(LiveModelEvalComparisonReport.QualityDelta delta,
                                           List<String> baselineOnlyCaseIds,
                                           Map<String, Integer> intelligenceMetricFailDelta,
                                           Map<String, Integer> metricFailDelta,
                                           Map<String, Integer> educationAgentMetricFailDelta,
                                           Map<String, Integer> studentFeedbackMetricFailDelta,
                                           List<LiveModelEvalComparisonReport.CaseComparison> cases) {
        List<String> signals = new ArrayList<>();
        baselineOnlyCaseIds.forEach(caseId -> signals.add(caseId + ": missing from candidate report"));
        if (delta.getFallbackCountDelta() > 0) {
            signals.add("fallbackCount +" + delta.getFallbackCountDelta());
        }
        if (delta.getLatencyBudgetExceededCountDelta() > 0) {
            signals.add("latencyBudgetExceededCount +" + delta.getLatencyBudgetExceededCountDelta());
        }
        if (delta.getRubricChainAverageScoreDelta() < -SCORE_DELTA_THRESHOLD) {
            signals.add("rubricChainAverageScore " + format(delta.getRubricChainAverageScoreDelta()));
        }
        if (delta.getRubricChainPassedCountDelta() < 0) {
            signals.add("rubricChainPassedCount " + delta.getRubricChainPassedCountDelta());
        }
        if (delta.getSafetyCategoryCountDelta() != null) {
            delta.getSafetyCategoryCountDelta().entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> "safetyCategoryCount " + entry.getKey() + " +" + entry.getValue())
                    .forEach(signals::add);
        }
        if (delta.getModelTraceQualityAverageScoreDelta() < -SCORE_DELTA_THRESHOLD) {
            signals.add("modelTraceQualityAverageScore " + format(delta.getModelTraceQualityAverageScoreDelta()));
        }
        if (delta.getModelTraceMetricPassRateDelta() < -SCORE_DELTA_THRESHOLD) {
            signals.add("modelTraceMetricPassRate " + format(delta.getModelTraceMetricPassRateDelta()));
        }
        if (delta.getIntelligenceQualityAverageScoreDelta() < -SCORE_DELTA_THRESHOLD) {
            signals.add("intelligenceQualityAverageScore " + format(delta.getIntelligenceQualityAverageScoreDelta()));
        }
        if (delta.getEducationAgentQualityAverageScoreDelta() < -SCORE_DELTA_THRESHOLD) {
            signals.add("educationAgentQualityAverageScore " + format(delta.getEducationAgentQualityAverageScoreDelta()));
        }
        if (delta.getStudentFeedbackQualityAverageScoreDelta() < -SCORE_DELTA_THRESHOLD) {
            signals.add("studentFeedbackQualityAverageScore " + format(delta.getStudentFeedbackQualityAverageScoreDelta()));
        }
        intelligenceMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "intelligenceMetricFailCount " + entry.getKey() + " +" + entry.getValue())
                .forEach(signals::add);
        metricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "modelTraceMetricFailCount " + entry.getKey() + " +" + entry.getValue())
                .forEach(signals::add);
        educationAgentMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "educationAgentMetricFailCount " + entry.getKey() + " +" + entry.getValue())
                .forEach(signals::add);
        studentFeedbackMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "studentFeedbackMetricFailCount " + entry.getKey() + " +" + entry.getValue())
                .forEach(signals::add);
        cases.stream()
                .flatMap(entry -> entry.getRegressionSignals().stream())
                .limit(12)
                .forEach(signals::add);
        return signals.stream().distinct().toList();
    }

    private String recommendation(List<String> improvements,
                                  List<String> regressions,
                                  List<String> comparableCaseIds) {
        if (comparableCaseIds == null || comparableCaseIds.isEmpty()) {
            return "NOT_COMPARABLE";
        }
        if (regressions != null && !regressions.isEmpty()) {
            return "KEEP_BASELINE";
        }
        if (improvements != null && !improvements.isEmpty()) {
            return "PROMOTE_CANDIDATE";
        }
        return "NO_CLEAR_WIN";
    }

    private LiveModelEvalComparisonReport.IterationAdvice iterationAdvice(
            String recommendation,
            List<String> baselineOnlyCaseIds,
            List<String> candidateOnlyCaseIds,
            LiveModelEvalComparisonReport.QualityDelta delta,
            Map<String, Integer> intelligenceMetricFailDelta,
            Map<String, Integer> metricFailDelta,
            Map<String, Integer> educationAgentMetricFailDelta,
            Map<String, Integer> studentFeedbackMetricFailDelta,
            List<String> improvementSignals,
            List<String> regressionSignals,
            List<LiveModelEvalComparisonReport.CaseComparison> cases) {
        List<String> blockedReasons = blockedPromotionReasons(
                recommendation,
                baselineOnlyCaseIds,
                delta,
                intelligenceMetricFailDelta,
                metricFailDelta,
                educationAgentMetricFailDelta,
                studentFeedbackMetricFailDelta,
                regressionSignals
        );
        List<LiveModelEvalComparisonReport.IterationAction> promptActions = new ArrayList<>();
        List<LiveModelEvalComparisonReport.IterationAction> standardLibraryActions = new ArrayList<>();
        List<LiveModelEvalComparisonReport.IterationAction> runtimeActions = new ArrayList<>();
        List<LiveModelEvalComparisonReport.IterationAction> evalDataActions = new ArrayList<>();
        List<LiveModelEvalComparisonReport.IterationAction> priorityActions = new ArrayList<>();

        if ("NOT_COMPARABLE".equals(recommendation)) {
            evalDataActions.add(action(
                    "EVAL_DATA",
                    "P0",
                    "补齐可比较 live eval 样本",
                    "baseline 与 candidate 没有可比较 case，不能判断外接模型是否真的变强。",
                    List.of("comparableCaseCount=0"),
                    List.of("online-judge/src/test/resources/diagnosis-eval-fixtures/complex-student-submission-cases.json"),
                    "重新跑同一批 complex-live case，并确认两份 report 的 caseId 有交集。"
            ));
        }
        if (baselineOnlyCaseIds != null && !baselineOnlyCaseIds.isEmpty()) {
            evalDataActions.add(action(
                    "EVAL_DATA",
                    "P0",
                    "保持 baseline 与 candidate 的代表集一致",
                    "缺失 case 会让能力对比偏向 candidate，尤其会掩盖单条复杂样本退化。",
                    baselineOnlyCaseIds.stream()
                            .map(caseId -> caseId + ": missing from candidate report")
                            .limit(6)
                            .toList(),
                    List.of("online-judge/src/test/java/com/onlinejudge/submission/application/ModelDiagnosisEvalTest.java"),
                    "用相同 AI_EVAL_FULL、AI_EVAL_RUNTIME_PROFILE 与 case filter 重跑 candidate。"
            ));
        }
        if (candidateOnlyCaseIds != null && !candidateOnlyCaseIds.isEmpty()) {
            evalDataActions.add(action(
                    "EVAL_DATA",
                    "P2",
                    "不要把 candidate-only 样本当作能力提升证据",
                    "candidate-only 样本可以作为探索信号，但不能证明同一代表集上的模型能力提升。",
                    candidateOnlyCaseIds.stream().limit(6).toList(),
                    List.of("target/ai-eval-reports/live-model-eval-comparison-*.json"),
                    "先沉淀为下一轮 baseline，再做同 case 对比。"
            ));
        }
        if (delta.getFallbackCountDelta() > 0 || containsSignal(regressionSignals, "fallback")) {
            runtimeActions.add(action(
                    "RUNTIME_PROFILE",
                    "P0",
                    "先修复真实模型完成率退化",
                    "fallback 增加说明 candidate 没有稳定调用外接模型，不能把本地兜底结果算成 AI 能力提升。",
                    matchingSignals(regressionSignals, "fallback", "candidate no longer counted"),
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/ExternalModelAgentRuntime.java"),
                    "先跑 2 条 smoke，确认 status=MODEL_COMPLETED、fallbackUsed=false、modelCompleted=true。"
            ));
        }
        if (containsSignal(regressionSignals, "external runtime blocked")
                || containsSignal(regressionSignals, "INSUFFICIENT_QUOTA")
                || containsSignal(regressionSignals, "RATE_LIMIT")) {
            List<String> runtimeBlockerEvidence = matchingSignals(
                    regressionSignals,
                    "external runtime blocked",
                    "INSUFFICIENT_QUOTA",
                    "RATE_LIMIT"
            );
            runtimeActions.add(action(
                    "RUNTIME_PROFILE",
                    "P0",
                    "先解除外接模型运行条件阻塞",
                    "配额不足或限流会让 candidate 无法产生真实模型判断；这类报告不能证明 prompt 变差或变强。",
                    runtimeBlockerEvidence,
                    List.of(
                            "online-judge/src/test/java/com/onlinejudge/submission/application/ModelDiagnosisEvalTest.java",
                            "target/ai-eval-reports/live-model-eval-*.json"
                    ),
                    "补足配额或降低调用频率后重跑同一批 case，确认 failureReason 不再包含 INSUFFICIENT_QUOTA 或 RATE_LIMIT。"
            ));
            evalDataActions.add(action(
                    "EVAL_DATA",
                    "P0",
                    "不要用运行条件受阻报告评估模型智能",
                    "该 candidate 的模型输出主要来自 fallback，无法代表外接大模型教育 agent 能力。",
                    runtimeBlockerEvidence,
                    List.of("target/ai-eval-reports/live-model-eval-comparison-*.json"),
                    "只把 modelCompleted=true 且 fallbackUsed=false 的 case 计入 prompt/标准库能力结论。"
            ));
        }
        if (delta.getLatencyBudgetExceededCountDelta() > 0 || containsSignal(regressionSignals, "latencyBudget")) {
            runtimeActions.add(action(
                    "RUNTIME_PROFILE",
                    "P1",
                    "压低 candidate 的上下文和输出预算",
                    "latency budget 退化会让真实学生链路变慢，也会放大输出截断风险。",
                    matchingSignals(regressionSignals, "latencyBudget", "SLOW_RESPONSE"),
                    List.of(
                            "online-judge/src/main/java/com/onlinejudge/submission/application/ExternalModelAgentRuntime.java",
                            "online-judge/src/test/java/com/onlinejudge/submission/application/OfflineRuntimeProfileEvalReportFactory.java"
                    ),
                    "运行 offline runtime profile eval，确认 auto/low-latency 请求体更小且证据锚点保留。"
            ));
        }
        if (containsSignal(regressionSignals, "output budget limited")
                || containsSignal(regressionSignals, "OUTPUT_TRUNCATED")
                || containsSignal(regressionSignals, "streamFinishReason=length")) {
            runtimeActions.add(action(
                    "RUNTIME_PROFILE",
                    "P0",
                    "先修复输出截断再判断模型能力",
                    "输出截断会让模型原生教育判断缺字段或变成 partial，不能把这种失败直接理解成模型不会分析。",
                    matchingSignals(regressionSignals, "output budget limited", "OUTPUT_TRUNCATED", "streamFinishReason=length"),
                    List.of(
                            "online-judge/src/main/java/com/onlinejudge/submission/application/AiReportService.java",
                            "online-judge/src/test/java/com/onlinejudge/submission/application/ModelDiagnosisEvalTest.java"
                    ),
                    "提高 AI_EVAL_MAX_OUTPUT_TOKENS 或缩短 prompt/schema 后重跑同 case，确认 streamFinishReason!=length 且 status=MODEL_COMPLETED。"
            ));
            promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P1",
                    "压缩单调用输出 schema",
                    "如果 maxOutputTokens 已经足够，仍出现 OUTPUT_TRUNCATED，说明 prompt 或 JSON 字段过长，需要把模型输出预算留给主错因、证据和下一步动作。",
                    matchingSignals(regressionSignals, "output budget limited", "OUTPUT_TRUNCATED", "streamFinishReason=length"),
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑 comparison，确认 output budget limited signal 消失，modelTraceQualityScore 不下降。"
            ));
        }
        if (containsSignal(regressionSignals, "safety boundary regression")
                || containsSignal(regressionSignals, "SAFETY_RISK")
                || containsSignal(regressionSignals, "answerLeakRisk=HIGH")
                || containsSignal(regressionSignals, "safetyPassed=false")
                || containsSignal(regressionSignals, "safetyCategoryCount")) {
            List<String> safetyEvidence = matchingSignals(
                    regressionSignals,
                    "safety boundary regression",
                    "SAFETY_RISK",
                    "answerLeakRisk=HIGH",
                    "safetyPassed=false",
                    "safetyCategoryCount"
            );
            promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "收紧不泄题表达边界",
                    "候选模型已经出现安全边界退化，下一轮应先让模型减少直接替换式修法、完整答案、隐藏测试猜测和公式化泄题表达。",
                    safetyEvidence,
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 safetyPassed=true、answerLeakRisk!=HIGH，且 noFullSolutionLeak 与 modelTraceMetric:nativeSafetyBoundary 通过。"
            ));
            standardLibraryActions.add(action(
                    "STANDARD_LIBRARY",
                    "P0",
                    "把安全退化样式加入 safetyBoundaryRules",
                    "真实模型输出中的泄题风险要沉淀为标准库禁止表达，让后续模型调用在生成前就知道边界。",
                    safetyEvidence,
                    List.of(
                            "online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java",
                            "online-judge/src/main/java/com/onlinejudge/submission/application/ModelOutputSafetyPolicy.java"
                    ),
                    "补充安全样式后跑 ModelOutputValidatorTest，并确认安全证据任务不过度误伤。"
            ));
            addSafetyCategoryActions(safetyEvidence, promptActions, standardLibraryActions);
        }
        if (containsSignal(regressionSignals, "intelligenceQualityAverageScore")
                || containsSignal(regressionSignals, "intelligenceQualityScore")) {
            promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "复核外接模型综合教育智能退化",
                    "综合智能分下降说明模型可能在主错因、证据、教学动作或安全边界中至少一项变弱，下一轮应先定位具体失败指标再改 prompt。",
                    matchingSignals(regressionSignals, "intelligenceQualityAverageScore", "intelligenceQualityScore"),
                    List.of(
                            "online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java",
                            "online-judge/src/test/java/com/onlinejudge/submission/application/ComplexDiagnosisQualityScorer.java"
                    ),
                    "查看 live-model-eval-comparison 的 failedMetrics，再重跑同 case 确认 intelligenceQualityAverageScore 不再下降。"
            ));
        }
        if (containsSignal(regressionSignals, "educationAgentQualityAverageScore")
                || containsSignal(regressionSignals, "educationAgentQualityScore")) {
            promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "强化模型教师式判断顺序",
                    "教育 agent 质量下降说明模型没有稳定做到先选主错因、再解释证据、再压低次要信号、最后给下一步动作。",
                    matchingSignals(regressionSignals, "educationAgentQualityAverageScore", "educationAgentQualityScore"),
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 educationAgentQualityAverageScore 恢复，且主错因说明、教学优先级、下一步动作均通过。"
            ));
            standardLibraryActions.add(action(
                    "STANDARD_LIBRARY",
                    "P1",
                    "补充基础错因证据与提示条目",
                    "标准库需要把退化 case 对应的错因证据、学生提示和教师解释沉淀为基础层条目，帮助模型在多错因场景稳定做主次取舍。",
                    matchingSignals(regressionSignals, "educationAgentQualityAverageScore", "educationAgentQualityScore"),
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                    "补充 basicCauses 或 improvementPoints 后跑 StandardLibraryPackBuilderTest，并用同一 candidate report 对比 educationAgentQualityAverageScore。"
            ));
        }
        if (containsSignal(regressionSignals, "studentFeedbackQualityAverageScore")
                || containsSignal(regressionSignals, "studentFeedbackQualityScore")) {
            promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "把模型判断稳定转成学生可执行反馈",
                    "学生反馈质量下降说明模型可能找到了问题但表达不可行动、证据不清或提升点覆盖当前主错因。",
                    matchingSignals(regressionSignals, "studentFeedbackQualityAverageScore", "studentFeedbackQualityScore"),
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 studentFeedbackQualityAverageScore 恢复，学生可见输出包含当前错误点、证据和一个可验证下一步。"
            ));
        }

        metricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .forEach(entry -> addMetricRegressionActions(
                        entry.getKey(),
                        regressionEvidenceForMetric(entry.getKey(), entry.getValue(), cases),
                        promptActions,
                        standardLibraryActions
                ));
        intelligenceMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .forEach(entry -> addIntelligenceMetricRegressionActions(
                        entry.getKey(),
                        regressionEvidenceForIntelligenceMetric(entry.getKey(), entry.getValue(), cases),
                        promptActions,
                        standardLibraryActions
                ));
        educationAgentMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .forEach(entry -> addEducationAgentMetricRegressionActions(
                        entry.getKey(),
                        regressionEvidenceForEducationAgentMetric(entry.getKey(), entry.getValue(), cases),
                        promptActions,
                        standardLibraryActions
                ));
        studentFeedbackMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .forEach(entry -> addStudentFeedbackMetricRegressionActions(
                        entry.getKey(),
                        regressionEvidenceForStudentFeedbackMetric(entry.getKey(), entry.getValue(), cases),
                        promptActions,
                        standardLibraryActions
                ));

        if ("PROMOTE_CANDIDATE".equals(recommendation) && blockedReasons.isEmpty()) {
            priorityActions.add(action(
                    "BASELINE",
                    "P1",
                    "候选链路可提升为新 live eval baseline",
                    "candidate 在同一代表集上出现能力提升且没有退化信号，可以沉淀为下一轮对比基线。",
                    improvementSignals == null ? List.of() : improvementSignals.stream().limit(8).toList(),
                    List.of("target/ai-eval-reports/live-model-eval-*.json"),
                    "将 candidate report 路径作为新的 AI_EVAL_MODEL_BASELINE_REPORT，并保留 comparison report。"
            ));
        }
        if ("NO_CLEAR_WIN".equals(recommendation)) {
            priorityActions.add(action(
                    "EVAL_DATA",
                    "P2",
                    "扩大同 case 对比后再决定是否改 prompt",
                    "当前没有明确进步或退化，贸然改 prompt 容易引入不可解释抖动。",
                    List.of("recommendation=NO_CLEAR_WIN"),
                    List.of("target/ai-eval-reports/live-model-eval-comparison-*.json"),
                    "优先增加同 bugPattern 的 live case 或重复跑一轮，确认是否只是模型随机性。"
            ));
        }

        List<LiveModelEvalComparisonReport.IterationAction> allActions = new ArrayList<>();
        allActions.addAll(runtimeActions);
        allActions.addAll(promptActions);
        allActions.addAll(standardLibraryActions);
        allActions.addAll(evalDataActions);
        if (priorityActions.isEmpty()) {
            priorityActions.addAll(allActions.stream().limit(4).toList());
        }
        return LiveModelEvalComparisonReport.IterationAdvice.builder()
                .overallDecision(recommendation)
                .candidatePromotionAllowed("PROMOTE_CANDIDATE".equals(recommendation) && blockedReasons.isEmpty())
                .blockedPromotionReasons(blockedReasons)
                .priorityActions(distinctActions(priorityActions))
                .promptActions(distinctActions(promptActions))
                .standardLibraryActions(distinctActions(standardLibraryActions))
                .runtimeActions(distinctActions(runtimeActions))
                .evalDataActions(distinctActions(evalDataActions))
                .build();
    }

    private List<String> blockedPromotionReasons(String recommendation,
                                                 List<String> baselineOnlyCaseIds,
                                                 LiveModelEvalComparisonReport.QualityDelta delta,
                                                 Map<String, Integer> intelligenceMetricFailDelta,
                                                 Map<String, Integer> metricFailDelta,
                                                 Map<String, Integer> educationAgentMetricFailDelta,
                                                 Map<String, Integer> studentFeedbackMetricFailDelta,
                                                 List<String> regressionSignals) {
        List<String> reasons = new ArrayList<>();
        if (!"PROMOTE_CANDIDATE".equals(recommendation)) {
            reasons.add("recommendation=" + recommendation);
        }
        if (baselineOnlyCaseIds != null && !baselineOnlyCaseIds.isEmpty()) {
            reasons.add("candidate missing baseline cases: " + String.join("|", baselineOnlyCaseIds.stream().limit(6).toList()));
        }
        if (delta.getFallbackCountDelta() > 0) {
            reasons.add("fallbackCount increased by " + delta.getFallbackCountDelta());
        }
        if (delta.getLatencyBudgetExceededCountDelta() > 0) {
            reasons.add("latencyBudgetExceededCount increased by " + delta.getLatencyBudgetExceededCountDelta());
        }
        intelligenceMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> "intelligence metric regressed: " + entry.getKey() + " +" + entry.getValue())
                .forEach(reasons::add);
        metricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> "native trace metric regressed: " + entry.getKey() + " +" + entry.getValue())
                .forEach(reasons::add);
        educationAgentMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> "education agent metric regressed: " + entry.getKey() + " +" + entry.getValue())
                .forEach(reasons::add);
        studentFeedbackMetricFailDelta.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> "student feedback metric regressed: " + entry.getKey() + " +" + entry.getValue())
                .forEach(reasons::add);
        if (regressionSignals != null && regressionSignals.stream().anyMatch(signal -> signal.contains("candidate no longer counted"))) {
            reasons.add("candidate has cases no longer counted as real external model intelligence");
        }
        if (regressionSignals != null && regressionSignals.stream().anyMatch(signal -> signal.contains("external runtime blocked"))) {
            reasons.add("candidate external runtime blocked; rerun after quota or budget guard is cleared");
        }
        if (regressionSignals != null && regressionSignals.stream().anyMatch(signal -> signal.contains("output budget limited"))) {
            reasons.add("candidate has output budget limited cases");
        }
        if (regressionSignals != null && regressionSignals.stream().anyMatch(signal -> signal.contains("safety boundary regression"))) {
            reasons.add("candidate has safety boundary regression cases");
        }
        if (regressionSignals != null && regressionSignals.stream().anyMatch(signal -> signal.contains("safetyCategoryCount"))) {
            reasons.add("candidate safety category count increased");
        }
        if (regressionSignals != null && regressionSignals.stream().anyMatch(signal -> signal.contains("intelligenceQualityAverageScore"))) {
            reasons.add("candidate intelligence quality average score regressed");
        }
        if (regressionSignals != null && regressionSignals.stream().anyMatch(signal -> signal.contains("educationAgentQualityAverageScore"))) {
            reasons.add("candidate education agent quality average score regressed");
        }
        if (regressionSignals != null && regressionSignals.stream().anyMatch(signal -> signal.contains("studentFeedbackQualityAverageScore"))) {
            reasons.add("candidate student feedback quality average score regressed");
        }
        return reasons.stream().distinct().toList();
    }

    private void addMetricRegressionActions(String metric,
                                            List<String> evidence,
                                            List<LiveModelEvalComparisonReport.IterationAction> promptActions,
                                            List<LiveModelEvalComparisonReport.IterationAction> standardLibraryActions) {
        switch (metric) {
            case "nativeRootCauseDecisionChecklistApplied" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "要求模型显式应用主错因决策步骤",
                        "该指标退化说明模型可能没有在原生判断里体现定位失败证据、连接代码行为、比较根因、压低干扰和生成可观察动作这五步。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 modelTraceMetric:nativeRootCauseDecisionChecklistApplied 恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P0",
                        "校准 rootCauseDecisionChecklist 和多信号样例",
                        "标准库需要让模型把五步主错因决策落实到可见输出，而不是只知道规则列表。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "确认 full/compact standardLibrary 都保留 rootCauseDecisionChecklist，并补充复杂多信号校准样例后重跑 comparison。"
                ));
            }
            case "nativePrimaryReasoningGrounded" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "强化主错因说明必须绑定证据",
                        "该指标退化说明模型可能在解释主因时泛泛而谈，没有把所选根因、失败行为和 evidenceRefs 绑在一起。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 modelTraceMetric:nativePrimaryReasoningGrounded 恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P1",
                        "补强主错因扎根的判断范式",
                        "标准库需要给模型更稳定的 root cause -> evidence -> failed behavior 推理形状。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "检查 compact standardLibrary 仍保留 modelTraceMetric:nativePrimaryReasoningGrounded 自检项。"
                ));
            }
            case "nativeTeachingPriorityClear" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "要求 teachingPriority 先解释第一教学焦点",
                        "该指标退化说明模型可能罗列多个问题，但没有说明学生当前最该先处理什么。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 modelTraceMetric:nativeTeachingPriorityClear 恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P1",
                        "补充主错因证据条目",
                        "用基础层条目的 evidenceSignals 和 teacherExplanation 表达：first failed evidence 优先于风格、变量名和表面复杂度。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "新增或调整 basicCauses 后跑 StandardLibraryPackBuilderTest，并确认 modelTraceMetric:nativeTeachingPriorityClear。"
                ));
            }
            case "nativeSecondarySignalsBalanced" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P1",
                        "要求次要信号说明为什么不是主因",
                        "该指标退化说明模型可能被样例特判、debug 分支或表面复杂度带偏。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 modelTraceMetric:nativeSecondarySignalsBalanced 恢复通过，secondaryIssues/distractorNotes 不抢占主因。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P0",
                        "补充干扰信号知识条目",
                        "标准库应在相关基础错因中覆盖同类 distractor 的 code pattern 和教师解释，帮助模型在多错因里保持主次取舍。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "确认 basicCauses 覆盖样例特判、debug、表面复杂度至少一种干扰，并验证 modelTraceMetric:nativeSecondarySignalsBalanced。"
                ));
            }
            case "nativeNextActionObservable" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P1",
                        "把下一步限制为可观察学习动作",
                        "该指标退化说明模型可能给出替换式修法，而不是让学生做比较、追踪、估算或构造反例。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 modelTraceMetric:nativeNextActionObservable 恢复通过，nextLearningAction 是可验证动作且绑定 evidenceRefs。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P1",
                        "细化 studentActionRules 的动作范式",
                        "标准库需要继续约束下一步动作，不让模型直接泄露修法。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "跑 PromptTemplateRegistryTest 与 StandardLibraryPackBuilderTest，并确认 modelTraceMetric:nativeNextActionObservable。"
                ));
            }
            case "nativeSafetyBoundary" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "收紧不泄题表达边界",
                        "该指标退化说明模型可能输出完整代码、直接替换结构、隐藏测试猜测或 transition formula。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑 prompt safety fixtures，确认 noFullSolutionLeak 与 modelTraceMetric:nativeSafetyBoundary 通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P0",
                        "把安全退化样式加入 safetyBoundaryRules",
                        "标准库需要把真实退化样式沉淀成模型可执行的禁止表达。",
                        evidence,
                        List.of(
                                "online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java",
                                "online-judge/src/main/java/com/onlinejudge/submission/application/ModelOutputSafetyPolicy.java"
                        ),
                        "跑 ModelOutputValidatorTest，确认 modelTraceMetric:nativeSafetyBoundary 对真实泄题拒绝、安全证据任务不过度误伤。"
                ));
            }
            default -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P2",
                    "复核未知原生 trace 指标退化",
                    "comparison report 出现当前映射表未覆盖的模型 trace 退化，需要先归类再改协议。",
                    evidence,
                    List.of("online-judge/src/test/java/com/onlinejudge/submission/application/ComplexDiagnosisQualityScorer.java"),
                    "先补 metric -> action 映射测试，再调整 prompt 或标准库。"
            ));
        }
    }

    private void addIntelligenceMetricRegressionActions(
            String metric,
            List<String> evidence,
            List<LiveModelEvalComparisonReport.IterationAction> promptActions,
            List<LiveModelEvalComparisonReport.IterationAction> standardLibraryActions) {
        switch (metric) {
            case "autonomousRootCauseDiscovery" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "强化自主主错因发现",
                        "该指标退化说明外接模型没有独立找对当前失败的根因，可能只是复述表面现象或跟随次要信号。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 intelligenceMetric:autonomousRootCauseDiscovery 恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P0",
                        "补充主错因证据库",
                        "标准库需要把 first failed evidence、代码行为和候选根因的证据模式讲得更明确，帮助模型自主选主因。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "补充 basicCauses 的 evidenceSignals、commonCodePatterns 或 teacherExplanation 后重跑 comparison。"
                ));
            }
            case "teachingDecisionQuality" -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "强化教学优先级判断",
                    "该指标退化说明模型也许找到了问题，但没有把它转成当前最该先教的学习焦点。",
                    evidence,
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 intelligenceMetric:teachingDecisionQuality 恢复通过。"
            ));
            case "complexSignalPrioritization" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P1",
                        "要求复杂信号先排序再输出",
                        "该指标退化说明模型面对多错因代码时没有稳定选择最能解释当前失败的信号。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 intelligenceMetric:complexSignalPrioritization 恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P1",
                        "补充复杂多信号错因条目",
                        "标准库需要补齐多错因学生代码中主错因和次要信号的证据差异，避免模型平均铺开所有问题。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "新增或调整 basicCauses 后重跑 comparison，确认 complexSignalPrioritization 通过。"
                ));
            }
            case "distractorResistance" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P1",
                        "要求显式忽略干扰信号",
                        "该指标退化说明模型被变量名、debug、样例特判或表面复杂度带偏，没有抵抗干扰。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 intelligenceMetric:distractorResistance 恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P0",
                        "补充干扰抵抗知识条目",
                        "标准库应把真实 distractor 形态沉淀为 code pattern 或 teacherExplanation，要求模型说明为什么它们不是主因。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "补充相关 basicCauses 后重跑同 case，确认 distractorResistance 通过。"
                ));
            }
            case "evidenceGroundedReasoning" -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "强化证据扎根推理",
                    "该指标退化说明模型判断没有绑定真实判题证据或代码证据，容易变成泛泛分析。",
                    evidence,
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 intelligenceMetric:evidenceGroundedReasoning 恢复通过且 evidenceRefs 命中 requiredEvidenceRefs。"
            ));
            case "modelSafetyAndBoundary" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "收紧综合智能安全边界",
                        "该指标退化说明模型的教学智能越过不泄题边界，可能给出完整答案、直接修法或隐藏测试猜测。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 intelligenceMetric:modelSafetyAndBoundary 与 safetyPassed 均恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P0",
                        "沉淀综合智能安全边界样式",
                        "泄题样式应沉淀到提示词安全边界和 ModelOutputSafetyPolicy，标准库只保留错因和提升点知识。",
                        evidence,
                        List.of(
                                "online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java",
                                "online-judge/src/main/java/com/onlinejudge/submission/application/ModelOutputSafetyPolicy.java"
                        ),
                        "跑 ModelOutputValidatorTest，并重跑同 case 确认 modelSafetyAndBoundary 通过。"
                ));
            }
            default -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P2",
                    "复核未知综合智能指标退化",
                    "comparison report 出现当前映射表未覆盖的综合智能退化，需要先归类再改协议。",
                    evidence,
                    List.of("online-judge/src/test/java/com/onlinejudge/submission/application/ComplexDiagnosisQualityScorer.java"),
                    "先补 intelligence metric -> action 映射测试，再调整 prompt 或标准库。"
            ));
        }
    }

    private void addEducationAgentMetricRegressionActions(
            String metric,
            List<String> evidence,
            List<LiveModelEvalComparisonReport.IterationAction> promptActions,
            List<LiveModelEvalComparisonReport.IterationAction> standardLibraryActions) {
        switch (metric) {
            case "primaryReasoningGrounded" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "要求教师式主因说明绑定证据",
                        "该指标退化说明模型学生反馈可能像老师一样在说问题，但没有把主错因、失败行为和 evidenceRefs 绑在一起。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 educationAgentMetric:primaryReasoningGrounded 恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P1",
                        "补强 root cause 到 evidence 的教师表达范式",
                        "标准库需要让模型把主错因解释写成学生能验证的因果链，而不是泛泛描述。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "补充校准样例后重跑同 case，确认主因说明引用 requiredEvidenceRefs。"
                ));
            }
            case "blockingPriorityClear" -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "要求先讲第一教学焦点",
                    "该指标退化说明模型没有清楚告诉学生当前最该先处理的 blocking issue，容易让多个建议并列打散注意力。",
                    evidence,
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 educationAgentMetric:blockingPriorityClear 恢复通过，第一条 blocking issue 是当前主因。"
            ));
            case "secondarySignalsBalanced" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P1",
                        "要求教师式压低次要信号",
                        "该指标退化说明模型可能把 debug、风格、样例特判或复杂度等信号讲得太重，压过当前失败主因。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 educationAgentMetric:secondarySignalsBalanced 恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P1",
                        "补充多信号主次取舍知识",
                        "标准库应给相关基础错因补足证据模式和教师解释，帮助模型像老师一样先稳住教学重点。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "调整 basicCauses 后重跑同 case，确认 secondarySignalsBalanced 通过。"
                ));
            }
            case "nextActionObservable" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "要求教师式下一步可观察",
                        "该指标退化说明模型给了建议，但学生不能用一个小实验、追踪或比较动作验证自己是否理解。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 educationAgentMetric:nextActionObservable 恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P1",
                        "扩展 teachingActions 的验证动作",
                        "标准库需要提供更多可观察动作范式，让模型把建议落成学生可做的小任务。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "确认 teachingActions 包含比较输出、追踪变量、构造最小反例等动作后重跑。"
                ));
            }
            case "safeTeachingBoundary" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "收紧教师式提示不泄题边界",
                        "该指标退化说明模型像老师一样解释时越过了边界，可能给出完整答案、直接修法或隐藏测试猜测。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 educationAgentMetric:safeTeachingBoundary 和 noSolutionLeak 均通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P0",
                        "把教师式泄题样式沉淀为安全规则",
                        "标准库需要约束模型即便进行教学解释，也只能给观察动作和证据，不给替换式答案。",
                        evidence,
                        List.of(
                                "online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java",
                                "online-judge/src/main/java/com/onlinejudge/submission/application/ModelOutputSafetyPolicy.java"
                        ),
                        "跑 ModelOutputValidatorTest，确认教师式泄题被拒绝且安全提示不过度误伤。"
                ));
            }
            default -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P2",
                    "复核未知教育 agent 指标退化",
                    "comparison report 出现当前映射表未覆盖的教育 agent 退化，需要先归类再改教师式判断协议。",
                    evidence,
                    List.of("online-judge/src/test/java/com/onlinejudge/submission/application/ComplexDiagnosisQualityScorer.java"),
                    "先补 educationAgent metric -> action 映射测试，再调整 prompt 或标准库。"
            ));
        }
    }

    private void addStudentFeedbackMetricRegressionActions(
            String metric,
            List<String> evidence,
            List<LiveModelEvalComparisonReport.IterationAction> promptActions,
            List<LiveModelEvalComparisonReport.IterationAction> standardLibraryActions) {
        switch (metric) {
            case "blockingPrimaryHit" -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "让学生反馈第一屏命中当前主错因",
                    "该指标退化说明模型原生判断可能存在，但学生可见 blockingIssues 没有把当前最该先改的问题讲清楚。",
                    evidence,
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 studentFeedbackMetric:blockingPrimaryHit 恢复通过，blockingIssues 首项对应主错因。"
            ));
            case "secondaryIssueBalanced" -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P1",
                    "把次要问题放到主错因之后",
                    "该指标退化说明模型可能把风格、debug、边缘提升点或表面复杂度放得太重，干扰学生先修当前失败。",
                    evidence,
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 studentFeedbackMetric:secondaryIssueBalanced 通过，secondaryIssues 不抢占 blockingIssues。"
            ));
            case "improvementOpportunityUseful" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P1",
                        "区分继续提升点与当前错误点",
                        "该指标退化说明模型把当前主错因重复包装成提升建议，或提升建议缺少可迁移的教学价值。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 studentFeedbackMetric:improvementOpportunityUseful 恢复通过，提升点不覆盖当前主错因。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P1",
                        "校准 improvementTaxonomy 的学生表达",
                        "标准库需要帮助模型把复杂度、测试习惯、边界意识、鲁棒性等提升方向讲成独立于当前 blocking issue 的学习建议。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "跑 StandardLibraryPackBuilderTest，并用同 case 验证 improvementOpportunityUseful 不再退化。"
                ));
            }
            case "evidenceGrounded" -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "要求学生反馈绑定真实 evidenceRefs",
                    "该指标退化说明学生端说明可能脱离了判题证据或代码证据，容易变成泛泛建议。",
                    evidence,
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 studentFeedbackMetric:evidenceGrounded 恢复通过，学生可见证据来自 requiredEvidenceRefs。"
            ));
            case "studentActionable" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "把下一步改成可验证学习动作",
                        "该指标退化说明学生看完反馈后仍不知道具体比较什么、追踪什么或构造什么自测。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 studentFeedbackMetric:studentActionable 恢复通过，nextLearningAction 同时包含 task 和 checkQuestion。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P1",
                        "扩充 teachingActions 的可观察动作范式",
                        "标准库应给模型更多观察式动作选择，例如逐字符比较输出、追踪变量、构造最小反例和估算复杂度。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                        "确认 teachingActions 保留观察式动作，并重跑同 case 的 studentActionable 指标。"
                ));
            }
            case "noSolutionLeak" -> {
                promptActions.add(action(
                        "PROMPT_PROTOCOL",
                        "P0",
                        "收紧学生反馈不泄题边界",
                        "该指标退化说明学生可见反馈可能包含完整答案、直接替换式修法、隐藏测试猜测或公式结构泄露。",
                        evidence,
                        List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                        "重跑同 case，确认 studentFeedbackMetric:noSolutionLeak 和 safetyPassed 都恢复通过。"
                ));
                standardLibraryActions.add(action(
                        "STANDARD_LIBRARY",
                        "P0",
                        "把学生反馈泄题样式沉淀进安全策略",
                        "真实学生端泄题表达应加入 PromptTemplateRegistry 和 ModelOutputSafetyPolicy，避免模型把教育反馈变成替学生写答案。",
                        evidence,
                        List.of(
                                "online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java",
                                "online-judge/src/main/java/com/onlinejudge/submission/application/ModelOutputSafetyPolicy.java"
                        ),
                        "跑 ModelOutputValidatorTest，确认学生反馈泄题被拒绝且普通提示不误伤。"
                ));
            }
            case "fallbackHonesty" -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P2",
                    "复核 fallback 与模型输出归因",
                    "该指标退化说明学生反馈可能混淆了真实外接模型能力和本地兜底输出，需要先确认报告归因。",
                    evidence,
                    List.of(
                            "online-judge/src/main/java/com/onlinejudge/submission/application/AiReportService.java",
                            "online-judge/src/test/java/com/onlinejudge/submission/application/ModelDiagnosisEvalTest.java"
                    ),
                    "确认 fallbackUsed=true 的 case 不计入真实外接模型 student feedback 能力分。"
            ));
            default -> promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P2",
                    "复核未知学生反馈指标退化",
                    "comparison report 出现当前映射表未覆盖的学生反馈退化，需要先归类再改学生端表达协议。",
                    evidence,
                    List.of("online-judge/src/test/java/com/onlinejudge/submission/application/ComplexDiagnosisQualityScorer.java"),
                    "先补 studentFeedback metric -> action 映射测试，再调整 prompt 或标准库。"
            ));
        }
    }

    private void addSafetyCategoryActions(List<String> safetyEvidence,
                                          List<LiveModelEvalComparisonReport.IterationAction> promptActions,
                                          List<LiveModelEvalComparisonReport.IterationAction> standardLibraryActions) {
        if (containsSignal(safetyEvidence, "COMPLETE_CODE_LEAK")) {
            promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "禁止模型输出完整代码块或可复制函数",
                    "完整代码泄露说明模型把教学反馈变成了替学生完成代码，prompt 需要明确只允许观察动作、局部现象和反例构造。",
                    matchingSignals(safetyEvidence, "COMPLETE_CODE_LEAK"),
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认输出不包含 ```、def、int main 或完整函数结构。"
            ));
        }
        if (containsSignal(safetyEvidence, "DIRECT_FIX_LEAK")) {
            promptActions.add(action(
                    "PROMPT_PROTOCOL",
                    "P0",
                    "把直接修法改写成观察式下一步",
                    "直接替换式修法会削弱学生自主分析，prompt 应要求把“改成什么”改写为“先比较什么、追踪什么、构造什么”。",
                    matchingSignals(safetyEvidence, "DIRECT_FIX_LEAK"),
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/PromptTemplateRegistry.java"),
                    "重跑同 case，确认 nextLearningAction 是可验证动作，不包含直接改成、替换为、答案如下。"
            ));
        }
        if (containsSignal(safetyEvidence, "HIDDEN_TEST_GUESS")) {
            standardLibraryActions.add(action(
                    "STANDARD_LIBRARY",
                    "P0",
                    "禁止猜测隐藏测试数据",
                    "隐藏测试猜测会破坏评测边界，标准库需要要求模型只讨论公开失败行为、代码证据和可泛化反例。",
                    matchingSignals(safetyEvidence, "HIDDEN_TEST_GUESS"),
                    List.of("online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java"),
                    "重跑同 case，确认提示不出现隐藏测试输入/输出猜测，只要求构造自测反例。"
            ));
        }
        if (containsSignal(safetyEvidence, "FORMULA_OR_STRUCTURE_LEAK")) {
            standardLibraryActions.add(action(
                    "STANDARD_LIBRARY",
                    "P1",
                    "沉淀公式和结构泄露样式",
                    "公式或循环结构泄露说明模型越过了教学动作边界，标准库需要把常见算法结构、转移式和关键循环形状列为禁止直接给出。",
                    matchingSignals(safetyEvidence, "FORMULA_OR_STRUCTURE_LEAK"),
                    List.of(
                            "online-judge/src/main/java/com/onlinejudge/submission/application/StandardLibraryPackBuilder.java",
                            "online-judge/src/main/java/com/onlinejudge/submission/application/ModelOutputSafetyPolicy.java"
                    ),
                    "补充 safetyBoundaryRules 后跑 ModelOutputValidatorTest，确认公式/结构泄露被拒绝且普通证据描述不误伤。"
            ));
        }
    }

    private List<String> regressionEvidenceForMetric(String metric,
                                                     int count,
                                                     List<LiveModelEvalComparisonReport.CaseComparison> cases) {
        List<String> evidence = new ArrayList<>();
        evidence.add("modelTraceMetricFailCount " + metric + " +" + count);
        if (cases != null) {
            cases.stream()
                    .filter(entry -> entry.getNewlyFailedModelTraceMetrics() != null
                            && entry.getNewlyFailedModelTraceMetrics().contains(metric))
                    .map(entry -> entry.getCaseId() + ": newly failed " + metric)
                    .limit(5)
                    .forEach(evidence::add);
        }
        return evidence.stream().distinct().toList();
    }

    private List<String> regressionEvidenceForIntelligenceMetric(
            String metric,
            int count,
            List<LiveModelEvalComparisonReport.CaseComparison> cases) {
        List<String> evidence = new ArrayList<>();
        evidence.add("intelligenceMetricFailCount " + metric + " +" + count);
        if (cases != null) {
            cases.stream()
                    .filter(entry -> entry.getNewlyFailedIntelligenceMetrics() != null
                            && entry.getNewlyFailedIntelligenceMetrics().contains(metric))
                    .map(entry -> entry.getCaseId() + ": newly failed " + metric)
                    .limit(5)
                    .forEach(evidence::add);
        }
        return evidence.stream().distinct().toList();
    }

    private List<String> regressionEvidenceForEducationAgentMetric(
            String metric,
            int count,
            List<LiveModelEvalComparisonReport.CaseComparison> cases) {
        List<String> evidence = new ArrayList<>();
        evidence.add("educationAgentMetricFailCount " + metric + " +" + count);
        if (cases != null) {
            cases.stream()
                    .filter(entry -> entry.getNewlyFailedEducationAgentMetrics() != null
                            && entry.getNewlyFailedEducationAgentMetrics().contains(metric))
                    .map(entry -> entry.getCaseId() + ": newly failed " + metric)
                    .limit(5)
                    .forEach(evidence::add);
        }
        return evidence.stream().distinct().toList();
    }

    private List<String> regressionEvidenceForStudentFeedbackMetric(
            String metric,
            int count,
            List<LiveModelEvalComparisonReport.CaseComparison> cases) {
        List<String> evidence = new ArrayList<>();
        evidence.add("studentFeedbackMetricFailCount " + metric + " +" + count);
        if (cases != null) {
            cases.stream()
                    .filter(entry -> entry.getNewlyFailedStudentFeedbackMetrics() != null
                            && entry.getNewlyFailedStudentFeedbackMetrics().contains(metric))
                    .map(entry -> entry.getCaseId() + ": newly failed " + metric)
                    .limit(5)
                    .forEach(evidence::add);
        }
        return evidence.stream().distinct().toList();
    }

    private boolean containsSignal(List<String> signals, String token) {
        return signals != null && signals.stream().anyMatch(signal -> signal.contains(token));
    }

    private String outputBudgetEvidence(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return "";
        }
        String status = safeString(entry.getStatus());
        String finishReason = safeString(entry.getStreamFinishReason());
        String failureReason = safeString(entry.getFailureReason());
        String failureStage = safeString(entry.getFailureStage());
        List<String> evidence = new ArrayList<>();
        if ("MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(status)) {
            evidence.add("status=MODEL_PARTIAL_COMPLETED");
        }
        if ("length".equalsIgnoreCase(finishReason)) {
            evidence.add("streamFinishReason=length");
        }
        if (failureReason.contains("OUTPUT_TRUNCATED")) {
            evidence.add("failureReason=OUTPUT_TRUNCATED");
        }
        if (failureStage.contains("OUTPUT_TRUNCATED")) {
            evidence.add("failureStage=OUTPUT_TRUNCATED");
        }
        return String.join(", ", evidence);
    }

    private String externalRuntimeBlockerEvidence(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return "";
        }
        String failureReason = safeString(entry.getFailureReason());
        String failureStage = safeString(entry.getFailureStage());
        List<String> evidence = new ArrayList<>();
        addRuntimeBlockerEvidence(evidence, "failureReason", failureReason);
        addRuntimeBlockerEvidence(evidence, "failureStage", failureStage);
        return String.join(", ", evidence);
    }

    private void addRuntimeBlockerEvidence(List<String> evidence, String field, String value) {
        if (value.contains("INSUFFICIENT_QUOTA")) {
            evidence.add(field + "=INSUFFICIENT_QUOTA");
        }
        if (value.contains("RATE_LIMIT")) {
            evidence.add(field + "=RATE_LIMIT");
        }
        if (value.contains("TIMEOUT")) {
            evidence.add(field + "=TIMEOUT");
        }
    }

    private String safetyBoundaryEvidence(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return "";
        }
        List<String> evidence = new ArrayList<>();
        if (Boolean.FALSE.equals(entry.getSafetyPassed())) {
            evidence.add("safetyPassed=false");
        }
        String answerLeakRisk = entry.getModelOutput() == null
                ? ""
                : safeString(entry.getModelOutput().getAnswerLeakRisk());
        if ("HIGH".equalsIgnoreCase(answerLeakRisk)) {
            evidence.add("answerLeakRisk=HIGH");
        }
        String failureReason = safeString(entry.getFailureReason());
        String failureStage = safeString(entry.getFailureStage());
        if (failureReason.contains("SAFETY_RISK")) {
            evidence.add("failureReason=SAFETY_RISK");
        }
        if (failureStage.contains("SAFETY_RISK")) {
            evidence.add("failureStage=SAFETY_RISK");
        }
        List<String> failedMetrics = normalizeMetrics(entry.getModelTraceFailedMetrics(), "modelTraceMetric:");
        if (failedMetrics.contains("nativeSafetyBoundary")) {
            evidence.add("modelTraceMetric=nativeSafetyBoundary");
        }
        List<String> safetyCategories = LiveModelEvalSafetyCategoryClassifier.classify(entry);
        if (!safetyCategories.isEmpty()) {
            evidence.add("safetyCategories=" + String.join("|", safetyCategories));
        }
        return String.join(", ", evidence);
    }

    private List<String> matchingSignals(List<String> signals, String... tokens) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        return signals.stream()
                .filter(signal -> {
                    for (String token : tokens) {
                        if (signal.contains(token)) {
                            return true;
                        }
                    }
                    return false;
                })
                .limit(8)
                .toList();
    }

    private LiveModelEvalComparisonReport.IterationAction action(String area,
                                                                 String priority,
                                                                 String title,
                                                                 String rationale,
                                                                 List<String> evidenceSignals,
                                                                 List<String> targetFiles,
                                                                 String validationHint) {
        return LiveModelEvalComparisonReport.IterationAction.builder()
                .area(area)
                .priority(priority)
                .title(title)
                .rationale(rationale)
                .evidenceSignals(evidenceSignals == null ? List.of() : evidenceSignals)
                .targetFiles(targetFiles == null ? List.of() : targetFiles)
                .validationHint(validationHint)
                .build();
    }

    private List<LiveModelEvalComparisonReport.IterationAction> distinctActions(
            List<LiveModelEvalComparisonReport.IterationAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        return actions.stream()
                .collect(Collectors.toMap(
                        action -> action.getArea() + ":" + action.getTitle(),
                        Function.identity(),
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private Map<String, Integer> metricDelta(Map<String, Integer> baseline, Map<String, Integer> candidate) {
        Set<String> keys = new LinkedHashSet<>();
        if (baseline != null) {
            keys.addAll(baseline.keySet());
        }
        if (candidate != null) {
            keys.addAll(candidate.keySet());
        }
        return keys.stream()
                .sorted()
                .collect(Collectors.toMap(
                        Function.identity(),
                        key -> safeInt(candidate == null ? null : candidate.get(key))
                                - safeInt(baseline == null ? null : baseline.get(key)),
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<String> normalizeMetrics(List<String> metrics, String prefix) {
        if (metrics == null) {
            return List.of();
        }
        return metrics.stream()
                .filter(this::hasText)
                .map(String::trim)
                .map(value -> value.startsWith(prefix) ? value.substring(prefix.length()) : value)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> sortedDifference(Set<String> left, Set<String> right) {
        if (left == null || left.isEmpty()) {
            return List.of();
        }
        Set<String> safeRight = right == null ? Set.of() : right;
        return left.stream()
                .filter(value -> !safeRight.contains(value))
                .sorted()
                .toList();
    }

    private Double score(LiveModelEvalReport.Entry entry, ScoreKind kind) {
        if (entry == null) {
            return 0.0;
        }
        LiveModelEvalReport.QualityScore score = entry.getQualityScore();
        if (score != null) {
            return switch (kind) {
                case RUBRIC_CHAIN -> safeDouble(score.getRubricChainScore());
                case MODEL_TRACE -> safeDouble(score.getModelTraceQualityScore());
                case INTELLIGENCE -> safeDouble(score.getIntelligenceQualityScore());
                case EDUCATION_AGENT -> safeDouble(score.getEducationAgentQualityScore());
                case STUDENT_FEEDBACK -> safeDouble(score.getStudentFeedbackQualityScore());
            };
        }
        return switch (kind) {
            case RUBRIC_CHAIN -> safeDouble(entry.getRubricChainScore());
            case MODEL_TRACE -> safeDouble(entry.getModelTraceQualityScore());
            case INTELLIGENCE -> safeDouble(entry.getIntelligenceQualityScore());
            case EDUCATION_AGENT -> safeDouble(entry.getEducationAgentQualityScore());
            case STUDENT_FEEDBACK -> safeDouble(entry.getStudentFeedbackQualityScore());
        };
    }

    private boolean countedAsModel(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return false;
        }
        if (entry.getModelJudgment() != null && entry.getModelJudgment().getCountedAsIntelligence() != null) {
            return Boolean.TRUE.equals(entry.getModelJudgment().getCountedAsIntelligence());
        }
        return Boolean.TRUE.equals(entry.getModelCompleted())
                && !Boolean.TRUE.equals(entry.getFallbackUsed());
    }

    private double delta(Double baseline, Double candidate) {
        return doubleDelta(safeDouble(baseline), safeDouble(candidate));
    }

    private int intDelta(Integer baseline, Integer candidate) {
        return safeInt(candidate) - safeInt(baseline);
    }

    private double doubleDelta(Double baseline, Double candidate) {
        return safeDouble(candidate) - safeDouble(baseline);
    }

    private double ratio(int passed, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return (double) passed / total;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum ScoreKind {
        RUBRIC_CHAIN,
        MODEL_TRACE,
        INTELLIGENCE,
        EDUCATION_AGENT,
        STUDENT_FEEDBACK
    }
}
