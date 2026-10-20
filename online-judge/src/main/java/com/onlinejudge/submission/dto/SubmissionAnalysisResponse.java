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
public class SubmissionAnalysisResponse {
    private String analysisSchemaVersion;
    private String evidenceSchemaVersion;
    private String taxonomyVersion;
    private Long submissionId;
    private String sourceType;
    private String scenario;
    private String headline;
    private String summary;
    private List<String> issueTags;
    private List<String> fineGrainedTags;
    private List<String> abilityPoints;
    private List<String> focusPoints;
    private List<String> fixDirections;
    private List<String> evidenceRefs;
    private String studentHint;
    private StudentHintPlan studentHintPlan;
    private StudentFeedback studentFeedback;
    private StudentFeedbackView studentFeedbackView;
    private LearningInterventionPlan learningInterventionPlan;
    private String teacherNote;
    private String progressSignal;
    private LearningTrajectorySignal learningTrajectorySignal;
    private Double confidence;
    private LearningActionEvidence learningActionEvidence;
    private TeacherCalibrationSignal teacherCalibrationSignal;
    private String uncertainty;
    private String diagnosticTrace;
    private ModelEducationTrace modelEducationTrace;
    private CaseUnderstanding caseUnderstanding;
    private List<BasicLayerAdvice> basicLayerAdvice;
    private List<ImprovementLayerAdvice> improvementLayerAdvice;
    private AiInvocation aiInvocation;
    private String answerLeakRisk;
    private String wrongSolution;
    private String correctSolution;
    private List<LineIssue> lineIssues;
    private FailedCaseSnapshot firstFailedCase;
    private String reportMarkdown;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedCaseSnapshot {
        private Integer testCaseNumber;
        private boolean hidden;
        private String input;
        private String expectedOutput;
        private String actualOutput;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineIssue {
        private Integer lineNumber;
        private String error;
        private String suggestion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentHintPlan {
        private String hintLevel;
        private String problemType;
        private String evidenceAnchor;
        private String nextAction;
        private String coachQuestion;
        private String teachingAction;
        private List<String> evidenceRefs;
        private String answerLeakRisk;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentFeedback {
        private String summary;
        private List<FeedbackIssue> blockingIssues;
        private List<SecondaryIssue> secondaryIssues;
        private List<ImprovementOpportunity> improvementOpportunities;
        private NextLearningAction nextLearningAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackIssue {
        private Integer priority;
        private String title;
        private String studentMessage;
        private String evidence;
        private String nextAction;
        private String issueTag;
        private String fineGrainedTag;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecondaryIssue {
        private String title;
        private String studentMessage;
        private String whyNotPrimary;
        private String issueTag;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImprovementOpportunity {
        private String title;
        private String category;
        private String studentMessage;
        private String benefit;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextLearningAction {
        private String hintLevel;
        private String action;
        private String task;
        private String checkQuestion;
        private List<String> evidenceRefs;
        private String answerLeakRisk;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentFeedbackView {
        private String status;
        private String primaryAction;
        private List<FeedbackViewItem> repairItems;
        private List<FeedbackViewItem> improvementItems;
        private String nextQuestion;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackViewItem {
        private String title;
        private String body;
        private String kind;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningTrajectorySignal {
        private String phase;
        private String label;
        private String evidenceRef;
        private String summary;
        private String nextFocus;
        private boolean needsTeacherAttention;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningInterventionPlan {
        private String interventionType;
        private String goal;
        private String studentTask;
        private String checkQuestion;
        private String completionSignal;
        private List<String> evidenceRefs;
        private Integer estimatedMinutes;
        private String answerLeakRisk;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningActionEvidence {
        private String expectedActionType;
        private String executionStatus;
        private String observedEvidence;
        private Double confidence;
        private List<String> evidenceRefs;
        private String nextAdjustment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeacherCalibrationSignal {
        private String status;
        private String summary;
        private String originalIssueTag;
        private String originalFineGrainedTag;
        private String correctedIssueTag;
        private String correctedFineGrainedTag;
        private Long correctionCount;
        private Double confidenceAdjustment;
        private List<String> evidenceRefs;
        private String recommendedAction;
        private boolean needsTeacherReview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelEducationTrace {
        private String source;
        private String primaryIssueTag;
        private String fineGrainedTag;
        private List<String> evidenceRefs;
        private String primaryReasoning;
        private List<ModelEducationIssueNote> secondaryIssues;
        private List<ModelEducationIssueNote> distractorNotes;
        private String teachingPriority;
        private List<String> improvementCategories;
        private String nextLearningAction;
        private List<String> nextLearningActionEvidenceRefs;
        private Double confidence;
        private String uncertainty;
        private Boolean needsMoreEvidence;
        private String answerLeakRisk;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelEducationIssueNote {
        private String title;
        private String message;
        private String issueTag;
        private String fineGrainedTag;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseUnderstanding {
        private String problemGoal;
        private String codeIntent;
        private String behaviorGap;
        private String primaryEvidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasicLayerAdvice {
        private String issueId;
        private String mistakePointId;
        private String skillUnitId;
        private String title;
        private String whatHappened;
        private String whyItMatters;
        private String studentAction;
        private String checkQuestion;
        private List<String> evidenceRefs;
        private Double confidence;
        private List<String> knowledgePath;
        private String knowledgePathStatus;
        private String provisionalNodeCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImprovementLayerAdvice {
        private String issueId;
        private String improvementPointId;
        private String skillUnitId;
        private String title;
        private String currentLimit;
        private String suggestion;
        private String studentBenefit;
        private List<String> evidenceRefs;
        private Double confidence;
        private List<String> knowledgePath;
        private String knowledgePathStatus;
        private String provisionalNodeCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiInvocation {
        private String provider;
        private String model;
        private String modelVersion;
        private String promptVersion;
        private String agentVersion;
        private String analysisSchemaVersion;
        private String evidenceSchemaVersion;
        private String taxonomyVersion;
        private String status;
        private boolean fallbackUsed;
        private String runtimeMode;
        private String runtimeProfile;
        private Integer requestBytes;
        private Boolean requestCompact;
        private String failureStage;
        private String failureReason;
        private String transportMode;
        private Integer streamChunkCount;
        private Integer streamContentChunkCount;
        private Integer streamReasoningChunkCount;
        private Integer streamInvalidChunkCount;
        private String streamFinishReason;
        private Boolean streamFallbackRetryUsed;
        private Boolean standardLibraryNavigationEnabled;
        private String standardLibraryNavigationStatus;
        private Integer standardLibraryNavigationSelectedCount;
        private String standardLibraryNavigationFailureReason;
        private String adviceGenerationStatus;
        private String adviceGenerationFailureReason;
        private Integer basicAdviceCount;
        private Integer improvementAdviceCount;
        private String advicePromptVersion;
        private String diagnosisPromptVersion;
        private Integer studentReportLength;
        private String answerLeakRisk;
        private String libraryFit;
        private String diagnosisLibraryFit;
        private List<String> diagnosisSoftFixes;
        private List<String> diagnosisHardFailures;
    }
}
