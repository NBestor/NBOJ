package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DiagnosisEvalFixtureDraftResponse {
    private Long assignmentId;
    private int candidateCount;
    private int fixtureCount;
    private int interventionFixtureCount;
    private int safetyFixtureCount;
    private int runtimeFixtureCount;
    private String summary;
    private List<FixtureDraft> fixtures;
    private List<InterventionFixtureDraft> interventionFixtures;
    private List<SafetyFixtureDraft> safetyFixtures;
    private List<RuntimeFixtureDraft> runtimeFixtures;

    @Data
    @Builder
    public static class FixtureDraft {
        private String name;
        private String source;
        private Long correctionId;
        private Long submissionId;
        private ProblemDraft problem;
        private SubmissionDraft submission;
        private List<CaseResultDraft> caseResults;
        private AnalysisDraft analysis;
        private TeacherCorrectionDraft teacherCorrection;
        private List<String> expectedIssueTags;
        private List<String> expectedFineTags;
        private List<String> mustMention;
        private List<String> mustNotMention;
        private SourceMaterialDraft sourceMaterial;
        private QualityDraft quality;
    }

    @Data
    @Builder
    public static class ProblemDraft {
        private Long id;
        private String title;
        private String description;
        private String difficulty;
        private Integer timeLimit;
        private Integer memoryLimit;
    }

    @Data
    @Builder
    public static class SubmissionDraft {
        private String languageName;
        private String verdict;
        private String sourceCode;
    }

    @Data
    @Builder
    public static class CaseResultDraft {
        private Integer testCaseNumber;
        private Boolean passed;
        private Boolean hidden;
        private String inputSnapshot;
        private String actualOutput;
        private String expectedOutput;
        private Double executionTime;
        private Integer memoryUsed;
    }

    @Data
    @Builder
    public static class AnalysisDraft {
        private String scenario;
        private List<String> originalIssueTags;
        private List<String> originalFineGrainedTags;
        private String analysisHeadline;
    }

    @Data
    @Builder
    public static class TeacherCorrectionDraft {
        private String correctedIssueTag;
        private String correctedFineGrainedTag;
        private String teacherNote;
    }

    @Data
    @Builder
    public static class SourceMaterialDraft {
        private String localFolder;
        private List<String> artifacts;
        private String anonymizationNote;
    }

    @Data
    @Builder
    public static class QualityDraft {
        private String bugPattern;
        private String misconception;
        private String expectedStudentMove;
        private String evalPurpose;
    }

    @Data
    @Builder
    public static class InterventionFixtureDraft {
        private String name;
        private String source;
        private String suggestionKey;
        private String title;
        private String targetAbility;
        private String feedbackActionType;
        private String feedbackNote;
        private String impactStatus;
        private String impactSummary;
        private Long followupSubmissionId;
        private String followupVerdict;
        private List<String> evidenceTags;
        private List<String> evidenceRefs;
        private List<String> mustMention;
        private List<String> mustNotMention;
        private List<String> expectedTeachingActions;
        private SourceMaterialDraft sourceMaterial;
        private QualityDraft quality;
    }

    @Data
    @Builder
    public static class SafetyFixtureDraft {
        private String name;
        private String source;
        private Long submissionId;
        private ProblemDraft problem;
        private SubmissionDraft submission;
        private AnalysisDraft analysis;
        private String riskLevel;
        private List<String> riskSources;
        private List<String> blockedReasons;
        private String originalHintPreview;
        private String safeHintPreview;
        private List<String> evidenceRefs;
        private List<String> mustMention;
        private List<String> mustNotMention;
        private String expectedSafetyAction;
        private SourceMaterialDraft sourceMaterial;
        private QualityDraft quality;
    }

    @Data
    @Builder
    public static class RuntimeFixtureDraft {
        private String name;
        private String source;
        private Long submissionId;
        private ProblemDraft problem;
        private SubmissionDraft submission;
        private AnalysisDraft analysis;
        private String runtimeMode;
        private String status;
        private Boolean fallbackUsed;
        private String transportMode;
        private Integer streamChunkCount;
        private Integer streamContentChunkCount;
        private Integer streamReasoningChunkCount;
        private Integer streamInvalidChunkCount;
        private String streamFinishReason;
        private Boolean streamFallbackRetryUsed;
        private String failureType;
        private String failureStage;
        private String failureReason;
        private String expectedRuntimeAction;
        private Boolean recoverySmokeRecommended;
        private String recoverySmokeCaseId;
        private String recoverySmokeRuntimeProfile;
        private String recoverySmokeCommandHint;
        private List<String> recoverySmokeRequiredChecks;
        private List<String> evidenceRefs;
        private List<String> mustMention;
        private List<String> mustNotMention;
        private SourceMaterialDraft sourceMaterial;
        private QualityDraft quality;
    }
}
