package com.onlinejudge.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAiFeedbackResponse {
    private Long submissionId;
    private String status;
    private String source;
    private LocalDateTime generatedAt;
    private Long latencyMs;
    private List<FeedbackItem> repairItems;
    private List<FeedbackItem> improvementItems;
    private List<IssueLifecycleItem> issueChanges;
    private IssueChangeSummary issueChangeSummary;
    private SubmissionGrowthSummaryResponse growthSummary;
    private StudentReport studentReport;
    private String nextQuestion;
    private Safety safety;
    private List<String> evidenceRefs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackItem {
        private String title;
        private String body;
        private String kind;
        private String libraryItemId;
        private String skillUnitId;
        private String mistakePointId;
        private String improvementPointId;
        private String libraryFit;
        private List<String> knowledgePath;
        private String knowledgePathStatus;
        private String provisionalNodeCode;
        private List<EvidenceSnippet> evidenceSnippets;
        private List<String> evidenceRefs;
        private List<String> qualitySignals;
        private String normalizedPointKey;
        private String pointKeySource;
        private String changeStatus;
        private List<String> personalLabels;
        private Long rawOccurrenceCount;
        private Long effectiveOccurrenceCount;
        private Long consecutiveEffectiveCount;
        private Long affectedProblemCount;
        private Long previousSubmissionId;
        private List<Long> lifecycleEvidenceSubmissionIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueLifecycleItem {
        private String normalizedPointKey;
        private String pointKeySource;
        private String title;
        private String factType;
        private String displayCategory;
        private String changeStatus;
        private List<String> personalLabels;
        private long rawOccurrenceCount;
        private long effectiveOccurrenceCount;
        private long consecutiveEffectiveCount;
        private long affectedProblemCount;
        private boolean effectiveAttempt;
        private Long previousSubmissionId;
        private Long currentSubmissionId;
        private Long firstSeenSubmissionId;
        private Long lastSeenSubmissionId;
        private List<Long> evidenceSubmissionIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueChangeSummary {
        private long persistedCount;
        private long newCount;
        private long recurringCount;
        private long notObservedCount;
        private long recoveredCount;
        private long uncomparableCount;
        private long improvementCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceSnippet {
        private String evidenceRef;
        private Integer lineNumber;
        private Integer lineEnd;
        private String code;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentReport {
        private String basicLayerText;
        private String improvementLayerText;
        private String nextActionText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Safety {
        private String answerLeakRisk;
        private List<String> blockedReasons;
    }
}
