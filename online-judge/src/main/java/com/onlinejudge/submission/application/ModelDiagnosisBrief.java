package com.onlinejudge.submission.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelDiagnosisBrief {

    public static final String SCHEMA_VERSION = "model-diagnosis-brief-v1";

    private String schemaVersion;
    private String problemBrief;
    private String problemConstraints;
    private String verdict;
    private String language;
    private String keyCodeExcerpt;
    private Integer sourceCodeLineCount;
    private SubmissionAnalysisResponse.FailedCaseSnapshot firstFailedCase;
    private List<VisibleCaseFact> visibleCaseFacts;
    private List<TestIntentFact> testIntentFacts;
    private List<String> evidenceRefs;
    private List<String> allowedIssueTags;
    private List<String> allowedFineGrainedTags;
    private String learningTrajectorySummary;
    private String learningMemorySummary;
    private String teacherCalibrationSummary;
    private HiddenDataBoundary hiddenDataBoundary;
    private String uncertainty;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VisibleCaseFact {
        private Integer testCaseNumber;
        private Boolean passed;
        private Boolean hidden;
        private Double executionTime;
        private Integer memoryUsed;
        private String inputPreview;
        private String actualOutputPreview;
        private String expectedOutputPreview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TestIntentFact {
        private Integer testCaseNumber;
        private Boolean passed;
        private Boolean hidden;
        private String evidenceRef;
        private String semanticCode;
        private String intentType;
        private String intentTitle;
        private String intentSummary;
        private String learningObjective;
        private String contestRole;
        private String revealPolicy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HiddenDataBoundary {
        private Boolean hiddenFailureObserved;
        private Boolean hiddenInputVisible;
        private String policy;
    }
}
