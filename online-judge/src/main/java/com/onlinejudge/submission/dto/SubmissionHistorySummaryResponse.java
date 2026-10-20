package com.onlinejudge.submission.dto;

import com.onlinejudge.submission.domain.Submission;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubmissionHistorySummaryResponse {
    private Long id;
    private Long problemId;
    private Long assignmentId;
    private Long studentProfileId;
    private String problemTitle;
    private Integer languageId;
    private String languageName;
    private Submission.Verdict verdict;
    private Double executionTime;
    private Integer memoryUsed;
    private LocalDateTime submittedAt;
    private Integer passedTestCases;
    private Integer totalTestCases;
    private String analysisStatus;
    private String analysisSourceType;
    private String analysisHeadline;
    private String analysisSummary;
    private String feedbackStatus;
    private String feedbackSource;
    private String feedbackFailureReason;
    private Long feedbackRevisionId;
    private String dataCompletenessStatus;
    private SubmissionGrowthSummaryResponse growthSummary;
}
