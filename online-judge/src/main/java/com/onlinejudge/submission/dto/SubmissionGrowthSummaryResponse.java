package com.onlinejudge.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionGrowthSummaryResponse {
    private Long submissionId;
    private String growthState;
    private String ruleVersion;
    private boolean effectiveAttempt;
    private boolean comparable;
    private Long comparisonSubmissionId;
    private Long duplicateOfSubmissionId;
    private Integer passedTestCases;
    private Integer totalTestCases;
    private Integer previousPassedTestCases;
    private Integer previousTotalTestCases;
    private Integer passedTestCaseDelta;
    private long persistedCount;
    private long newCount;
    private long recurringCount;
    private long notObservedCount;
    private long recoveredCount;
    private long uncomparableCount;
    private long improvementCount;
    private long unresolvedCount;
    private String priorityIssueTitle;
    private String priorityIssueStatus;
    private String dataCompletenessStatus;
    private List<IssueSignal> issueSignals;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueSignal {
        private String normalizedPointKey;
        private String title;
        private String displayCategory;
        private String changeStatus;
        private String pointKeySource;
        private String knowledgePathStatus;
        private List<String> knowledgePath;
        private long rawOccurrenceCount;
        private long effectiveOccurrenceCount;
        private List<Long> evidenceSubmissionIds;
    }
}
