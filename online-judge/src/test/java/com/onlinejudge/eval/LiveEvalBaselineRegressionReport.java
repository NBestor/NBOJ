package com.onlinejudge.eval;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveEvalBaselineRegressionReport {

    private String sourceReportType;
    private String baselineReportPath;
    private String currentReportPath;
    private Integer baselineCaseCount;
    private Integer currentCaseCount;
    private Integer comparedCaseCount;
    private Integer violationCount;
    private String status;
    private Integer currentFinalIssueTagHitCount;
    private Integer currentFinalFineTagHitCount;
    private Integer currentModelIssueTagHitCount;
    private Integer currentModelFineTagHitCount;
    private Integer currentFallbackIssueTagHitCount;
    private Integer currentFallbackFineTagHitCount;
    private String currentRecoveryStatus;
    private Integer currentRecoveryCheckCount;
    private Integer currentRecoveryPassedCheckCount;
    private Integer currentRecoveryBlockedReasonCount;
    private List<String> currentRecoveryBlockedReasons;
    private String comparabilityStatus;
    private Integer comparabilityReasonCount;
    private List<String> comparabilityReasons;
    private List<String> violations;

    public String consoleSummary() {
        return "status=" + safe(status)
                + ", comparability=" + safe(comparabilityStatus)
                + ", reasons=" + number(comparabilityReasonCount)
                + ", compared=" + number(comparedCaseCount) + "/" + number(baselineCaseCount)
                + ", violations=" + number(violationCount);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }
}
