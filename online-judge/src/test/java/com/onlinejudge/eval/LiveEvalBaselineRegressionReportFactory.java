package com.onlinejudge.eval;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.onlinejudge.submission.application.LiveModelEvalReport;

public class LiveEvalBaselineRegressionReportFactory {

    public LiveEvalBaselineRegressionReport fromAssistant(AssistantLiveEvalReport current,
                                                          List<LiveEvalQualityBaselineDraft> baselines,
                                                          String baselineReportPath,
                                                          String currentReportPath,
                                                          List<String> violations) {
        Set<String> currentCaseIds = current == null || current.getEntries() == null
                ? Set.of()
                : current.getEntries()
                .stream()
                .map(AssistantLiveEvalReport.Entry::getCaseId)
                .filter(LiveEvalBaselineRegressionReportFactory::hasText)
                .collect(Collectors.toSet());
        LiveEvalBaselineRegressionReport report = build(
                "assistant-live-eval",
                baselineReportPath,
                currentReportPath,
                baselines,
                currentCaseIds,
                current == null || current.getEntries() == null ? 0 : current.getEntries().size(),
                violations
        );
        applyComparability(report, false);
        return report;
    }

    public LiveEvalBaselineRegressionReport fromModel(LiveModelEvalReport current,
                                                      List<LiveEvalQualityBaselineDraft> baselines,
                                                      String baselineReportPath,
                                                      String currentReportPath,
                                                      List<String> violations) {
        Set<String> currentCaseIds = current == null || current.getEntries() == null
                ? Set.of()
                : current.getEntries()
                .stream()
                .map(LiveModelEvalReport.Entry::getCaseId)
                .filter(LiveEvalBaselineRegressionReportFactory::hasText)
                .collect(Collectors.toSet());
        LiveEvalBaselineRegressionReport report = build(
                "live-model-eval",
                baselineReportPath,
                currentReportPath,
                baselines,
                currentCaseIds,
                current == null || current.getEntries() == null ? 0 : current.getEntries().size(),
                violations
        );
        if (current != null) {
            report.setCurrentFinalIssueTagHitCount(current.getIssueTagHitCount());
            report.setCurrentFinalFineTagHitCount(current.getFineTagHitCount());
            report.setCurrentModelIssueTagHitCount(current.getModelIssueTagHitCount());
            report.setCurrentModelFineTagHitCount(current.getModelFineTagHitCount());
            report.setCurrentFallbackIssueTagHitCount(current.getFallbackIssueTagHitCount());
            report.setCurrentFallbackFineTagHitCount(current.getFallbackFineTagHitCount());
            report.setCurrentRecoveryStatus(current.getRecoveryStatus());
            report.setCurrentRecoveryCheckCount(current.getRecoveryCheckCount());
            report.setCurrentRecoveryPassedCheckCount(current.getRecoveryPassedCheckCount());
            report.setCurrentRecoveryBlockedReasonCount(current.getRecoveryBlockedReasonCount());
            report.setCurrentRecoveryBlockedReasons(current.getRecoveryBlockedReasons() == null
                    ? List.of()
                    : List.copyOf(current.getRecoveryBlockedReasons()));
        }
        applyComparability(report, true);
        return report;
    }

    private LiveEvalBaselineRegressionReport build(String sourceReportType,
                                                   String baselineReportPath,
                                                   String currentReportPath,
                                                   List<LiveEvalQualityBaselineDraft> baselines,
                                                   Set<String> currentCaseIds,
                                                   int currentCaseCount,
                                                   List<String> violations) {
        List<String> baselineCaseIds = baselines == null
                ? List.of()
                : baselines.stream()
                .map(LiveEvalQualityBaselineDraft::getCaseId)
                .filter(LiveEvalBaselineRegressionReportFactory::hasText)
                .distinct()
                .toList();
        int comparedCaseCount = (int) baselineCaseIds.stream()
                .filter(currentCaseIds::contains)
                .count();
        List<String> safeViolations = violations == null ? List.of() : List.copyOf(violations);
        return LiveEvalBaselineRegressionReport.builder()
                .sourceReportType(sourceReportType)
                .baselineReportPath(baselineReportPath)
                .currentReportPath(currentReportPath)
                .baselineCaseCount(baselineCaseIds.size())
                .currentCaseCount(currentCaseCount)
                .comparedCaseCount(comparedCaseCount)
                .violationCount(safeViolations.size())
                .status(safeViolations.isEmpty() ? "PASSED" : "FAILED")
                .violations(safeViolations)
                .build();
    }

    private void applyComparability(LiveEvalBaselineRegressionReport report, boolean modelReport) {
        if (report == null) {
            return;
        }
        List<String> reasons = comparabilityReasons(report, modelReport);
        report.setComparabilityReasons(reasons);
        report.setComparabilityReasonCount(reasons.size());
        report.setComparabilityStatus(comparabilityStatus(report, reasons));
    }

    private List<String> comparabilityReasons(LiveEvalBaselineRegressionReport report, boolean modelReport) {
        List<String> reasons = new java.util.ArrayList<>();
        if (report.getBaselineCaseCount() == null || report.getBaselineCaseCount() == 0) {
            reasons.add("baseline missing");
        }
        if (report.getCurrentCaseCount() == null || report.getCurrentCaseCount() == 0) {
            reasons.add("current report missing");
        }
        if (report.getComparedCaseCount() == null || report.getComparedCaseCount() == 0) {
            reasons.add("no compared cases");
        }
        if (report.getViolationCount() != null && report.getViolationCount() > 0) {
            reasons.add("violations present");
        }
        if (modelReport && "BLOCKED".equalsIgnoreCase(report.getCurrentRecoveryStatus())) {
            reasons.add("current recovery blocked");
            if (report.getCurrentRecoveryBlockedReasons() != null) {
                report.getCurrentRecoveryBlockedReasons().stream()
                        .filter(LiveEvalBaselineRegressionReportFactory::hasText)
                        .limit(4)
                        .forEach(reasons::add);
            }
        }
        int modelHits = safeInt(report.getCurrentModelIssueTagHitCount()) + safeInt(report.getCurrentModelFineTagHitCount());
        int fallbackHits = safeInt(report.getCurrentFallbackIssueTagHitCount()) + safeInt(report.getCurrentFallbackFineTagHitCount());
        if (modelReport && modelHits == 0 && fallbackHits > 0) {
            reasons.add("model hits missing; fallback hits present");
        }
        return reasons.stream().distinct().toList();
    }

    private String comparabilityStatus(LiveEvalBaselineRegressionReport report, List<String> reasons) {
        List<String> safeReasons = reasons == null ? List.of() : reasons;
        if (safeReasons.contains("current recovery blocked")
                || safeReasons.contains("no compared cases")
                || safeReasons.contains("current report missing")
                || safeReasons.contains("baseline missing")
                || safeReasons.contains("model hits missing; fallback hits present")) {
            return "NOT_COMPARABLE";
        }
        if (!safeReasons.isEmpty()
                || (report.getComparedCaseCount() != null
                && report.getBaselineCaseCount() != null
                && report.getComparedCaseCount() < report.getBaselineCaseCount())) {
            return "PARTIAL";
        }
        return "COMPARABLE";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
