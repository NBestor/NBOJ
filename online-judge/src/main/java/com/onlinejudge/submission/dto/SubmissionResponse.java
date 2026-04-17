package com.onlinejudge.submission.dto;

import com.onlinejudge.submission.domain.Submission;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SubmissionResponse {
    private Long id;
    private Long problemId;
    private String problemTitle;
    private Integer languageId;
    private String languageName;
    private String sourceCode;
    private Submission.Verdict verdict;
    private Double executionTime;
    private Integer memoryUsed;
    private String output;
    private String compileOutput;
    private String errorMessage;
    private LocalDateTime submittedAt;
    private String analysisStatus;
    private SubmissionAnalysisResponse analysis;
    private List<TestCaseResult> testCaseResults;

    @Data
    @Builder
    public static class TestCaseResult {
        private Integer testCaseNumber;
        private boolean passed;
        private String actualOutput;
        private String expectedOutput;
        private Double executionTime;
        private Integer memoryUsed;
        private boolean hidden;
    }
}

