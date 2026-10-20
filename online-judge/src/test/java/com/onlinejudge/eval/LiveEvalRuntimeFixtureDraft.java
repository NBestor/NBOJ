package com.onlinejudge.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveEvalRuntimeFixtureDraft {

    private String name;
    private String sourceReportType;
    private String caseId;
    private String assistantType;
    private String stage;
    private String model;
    private String promptVersion;
    private String status;
    private Boolean fallbackUsed;
    private Boolean completedOutput;
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
    private Boolean offlineProfileEvalRecommended;
    private String offlineProfileReportPattern;
    private String offlineProfileCaseId;
    private List<String> offlineProfileRequiredChecks;
    private Boolean recoverySmokeRecommended;
    private String recoverySmokeCaseId;
    private String recoverySmokeRuntimeProfile;
    private String recoverySmokeCommandHint;
    private List<String> recoverySmokeRequiredChecks;
    private String failureType;
    private String failureStage;
    private String failureReason;
    private String expectedRuntimeAction;
    private List<String> evidenceRefs;
    private List<String> mustMention;
    private List<String> mustNotMention;
    private String teacherExpectation;
    private String outputSummary;
    private String iterationSuggestion;
}
