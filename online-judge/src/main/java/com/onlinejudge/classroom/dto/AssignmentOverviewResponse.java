package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AssignmentOverviewResponse {
    private AssignmentResponse assignment;
    private long rosterStudentCount;
    private long participantCount;
    private long submittedStudentCount;
    private long unsubmittedStudentCount;
    private long completedRequiredStudentCount;
    private long requiredProblemCount;
    private long attemptCount;
    private long passedAttemptCount;
    private Double studentPassRate;
    private Double attemptPassRate;
    private DataCompleteness dataCompleteness;
    private List<KnowledgePathStat> knowledgePathStats;
    private RecoverySummary recoverySummary;
    private long strugglingStudentCount;
    private long postAcTransferPendingCount;
    private String postAcTransferSummary;
    private long recurringMisconceptionStudentCount;
    private String recurringMisconceptionSummary;
    private long selfExplanationWeakStudentCount;
    private String selfExplanationSummary;
    private CoachAnswerQualityClassSummary coachAnswerQualitySummary;
    private CoachFollowupImpactSummary coachFollowupImpactSummary;
    private long aiDependencyRiskStudentCount;
    private String aiDependencySummary;
    private long masteryGrowthRiskStudentCount;
    private String masteryGrowthSummary;
    private long teachingActionRiskStudentCount;
    private String teachingActionSummary;
    private ClassTeachingStrategySignal classTeachingStrategySignal;
    private List<ProgressTrendPoint> progressTrend;
    private List<ProblemSummary> problemSummaries;
    private List<IssueStat> topIssues;
    private List<AbilityStat> classAbilityWeaknesses;
    private List<ClassReviewSuggestion> classReviewSuggestions;
    private List<StudentProgressSummary> students;

    @Data
    @Builder
    public static class IssueStat {
        private String label;
        private long count;
        private String explanation;
        private String abilityPoint;
        private String recommendedHintPolicy;
        private String interventionSuggestion;
        private Double actionPriorityScore;
        private String actionPriorityLabel;
        private String actionPriorityReason;
        private long affectedStudentCount;
        private long repeatedStudentCount;
        private long unexecutedActionCount;
        private long unresolvedAfterInterventionCount;
    }

    @Data
    @Builder
    public static class StudentProgressSummary {
        private Long studentProfileId;
        private String displayName;
        private String studentNo;
        private boolean currentRoster;
        private String rosterHistoryLabel;
        private long attemptCount;
        private long passedCount;
        private Long latestSubmissionId;
        private String latestVerdict;
        private String latestIssue;
        private String latestIssueTag;
        private String latestFineGrainedIssue;
        private String latestProgressSignal;
        private Double latestConfidence;
        private String latestUncertainty;
        private String latestAnswerLeakRisk;
        private TeacherDiagnosisCorrectionResponse latestCorrection;
        private CoachInteractionSummaryResponse latestCoachInteraction;
        private CoachImpactResponse latestCoachImpact;
        private StudentTrajectoryResponse.LearningActionEvidence latestLearningActionEvidence;
        private StudentTrajectoryResponse.PostAcTransferSignal postAcTransferSignal;
        private StudentAbilityProfileResponse.RecurringMisconceptionSignal recurringMisconceptionSignal;
        private StudentAbilityProfileResponse.SelfExplanationMasterySignal selfExplanationMasterySignal;
        private StudentAbilityProfileResponse.AiDependencySignal aiDependencySignal;
        private StudentAbilityProfileResponse.MasteryGrowthSignal masteryGrowthSignal;
        private StudentAbilityProfileResponse.TeachingActionDecision teachingActionDecision;
        private String primaryAbilityFocus;
        private String crossProblemSummary;
        private List<AbilityStat> abilitySummary;
        private String repeatedIssueTag;
        private String repeatedFineGrainedTag;
        private long repeatedIssueCount;
        private String attentionReason;
        private List<AttentionEvidence> attentionEvidence;
        private StudentRecentState recentLearningState;
        private boolean needsAttention;
    }

    @Data
    @Builder
    public static class ProgressTrendPoint {
        private LocalDateTime submittedAt;
        private long submittedStudentCount;
        private long passedStudentCount;
        private long submissionCount;
    }

    @Data
    @Builder
    public static class ProblemSummary {
        private Long problemId;
        private String title;
        private String difficulty;
        private Integer orderIndex;
        private boolean required;
        private Long classStudentCount;
        private long submittedStudentCount;
        private long submissionCount;
        private long effectiveAttemptCount;
        private long passedStudentCount;
        private long firstPassStudentCount;
        private long passedAttemptCount;
        private Double submissionRate;
        private Double passRate;
        private Double studentPassRate;
        private Double attemptPassRate;
        private Double averageAttempts;
        private Double medianEffectiveAttempts;
        private long attentionStudentCount;
        private String statusLabel;
        private DataCompleteness dataCompleteness;
        private List<KnowledgePathStat> knowledgePathStats;
        private RecoverySummary recoverySummary;
        private List<IssueStat> topIssues;
        private List<AbilityStat> abilityWeaknesses;
        private List<HintLevelStat> hintLevelDistribution;
        private List<ProblemStudentSummary> students;
    }

    @Data
    @Builder
    public static class ProblemStudentSummary {
        private Long studentProfileId;
        private String displayName;
        private String studentNo;
        private long attemptCount;
        private long effectiveAttemptCount;
        private long passedCount;
        private Long latestSubmissionId;
        private String latestVerdict;
        private LocalDateTime latestSubmittedAt;
        private String latestIssue;
        private String latestIssueTag;
        private String latestFineGrainedIssue;
        private String abilityPoint;
        private String latestHintLevel;
        private String latestHintAction;
        private String latestProgressSignal;
        private Double latestConfidence;
        private com.onlinejudge.submission.dto.SubmissionGrowthSummaryResponse latestGrowthSummary;
        private StudentTrajectoryResponse.AiFeedbackImpact latestAiFeedbackImpact;
        private StudentRecentState recentLearningState;
        private boolean needsAttention;
    }

    @Data
    @Builder
    public static class DataCompleteness {
        private long totalSubmissionCount;
        private long legalIdentityCount;
        private long identityMissingCount;
        private long invalidContextCount;
        private long analysisReadyCount;
        private long analysisMissingCount;
        private long diagnosisFactCount;
        private long diagnosedSubmissionCount;
        private long unclassifiedFactCount;
        private long feedbackEventSubmissionCount;
        private long completeSubmissionCount;
        private Double completeRate;
    }

    @Data
    @Builder
    public static class KnowledgePathNode {
        private String label;
        private String kind;
    }

    @Data
    @Builder
    public static class KnowledgePathStat {
        private String id;
        private String label;
        private String granularity;
        private String normalizedIssueId;
        private List<KnowledgePathNode> path;
        private String pathStatus;
        private String libraryFit;
        private String source;
        private Long teacherCorrectionId;
        private long errorOccurrenceCount;
        private long rawOccurrenceCount;
        private long effectiveWeightedOccurrenceCount;
        private long affectedStudentCount;
        private long repeatedStudentCount;
        private long unresolvedStudentCount;
        private long recurringStudentCount;
        private long recoveredStudentCount;
        private long recoveryNumerator;
        private long recoveryDenominator;
        private Double recoveryRate;
        private String difficultyClassification;
        private long affectedProblemCount;
        private List<Long> affectedStudentIds;
        private List<Long> repeatedStudentIds;
        private List<Long> resolvedStudentIds;
        private List<Long> affectedProblemIds;
        private List<Long> evidenceSubmissionIds;
        private List<SubmissionEvidenceRef> evidenceSamples;
    }

    @Data
    @Builder
    public static class SubmissionEvidenceRef {
        private Long submissionId;
        private Long studentProfileId;
        private Long problemId;
        private String verdict;
        private LocalDateTime submittedAt;
    }

    @Data
    @Builder
    public static class RecoverySummary {
        private long recoveryNumerator;
        private long recoveryDenominator;
        private long comparableSampleCount;
        private long recoveredCount;
        private long sameIssueCount;
        private long shiftedCount;
        private long regressedCount;
        private long verdictChangedCount;
        private long noClearChangeCount;
        private long awaitingFollowupCount;
        private long feedbackViewedComparableCount;
        private long feedbackViewedRecoveredCount;
        private Double recoveryRate;
        private Double feedbackViewedRecoveryRate;
        private List<SubmissionChange> evidence;
    }

    @Data
    @Builder
    public static class SubmissionChange {
        private Long studentProfileId;
        private Long problemId;
        private Long beforeSubmissionId;
        private Long afterSubmissionId;
        private String beforeVerdict;
        private String afterVerdict;
        private List<String> beforeIssueIds;
        private List<String> afterIssueIds;
        private String status;
        private boolean feedbackViewed;
    }

    @Data
    @Builder
    public static class StudentRecentState {
        private String status;
        private String evidenceStatus;
        private long independentSubmissionCount;
        private long problemCount;
        private String repeatedIssueId;
        private long repeatedIssueCount;
        private long repeatedIssueProblemCount;
        private String latestChangeStatus;
        private List<Long> evidenceSubmissionIds;
        private List<StudentIssueTrajectory> issueTrajectories;
    }

    @Data
    @Builder
    public static class StudentIssueTrajectory {
        private String normalizedPointKey;
        private String label;
        private String factType;
        private String currentStatus;
        private List<String> personalLabels;
        private long rawOccurrenceCount;
        private long effectiveOccurrenceCount;
        private long consecutiveEffectiveCount;
        private long affectedProblemCount;
        private long recurringCount;
        private Long firstSeenSubmissionId;
        private Long lastSeenSubmissionId;
        private List<Long> affectedProblemIds;
        private List<Long> evidenceSubmissionIds;
    }

    @Data
    @Builder
    public static class HintLevelStat {
        private String hintLevel;
        private long count;
    }

    @Data
    @Builder
    public static class AbilityStat {
        private String abilityPoint;
        private long taskCount;
        private long submissionCount;
        private List<String> evidenceTags;
    }

    @Data
    @Builder
    public static class ClassReviewSuggestion {
        private String suggestionKey;
        private String title;
        private String targetAbility;
        private Long exampleProblemId;
        private String exampleProblemTitle;
        private List<String> evidenceTags;
        private List<Long> evidenceSubmissionIds;
        private String guidingQuestion;
        private String action;
        private String evidenceSummary;
        private ClassReviewFeedbackSummary latestFeedback;
        private TeacherInterventionImpact interventionImpact;
    }

    @Data
    @Builder
    public static class CoachAnswerQualityClassSummary {
        private long promptedCount;
        private long answeredCount;
        private long verifiableCount;
        private long transferReadyCount;
        private long evidenceInsufficientCount;
        private long safetyRiskCount;
        private long coachSafetyRejectionCount;
        private long teacherAttentionCount;
        private String dominantGap;
        private String summary;
        private String recommendedAction;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    public static class CoachFollowupImpactSummary {
        private long impactedCount;
        private long acceptedCount;
        private long shiftedCount;
        private long sameIssueCount;
        private long verdictChangedCount;
        private long noClearChangeCount;
        private long awaitingFollowupCount;
        private String dominantOutcome;
        private String summary;
        private String recommendedAction;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    public static class ClassReviewFeedbackSummary {
        private String actionType;
        private String teacherNote;
        private String createdBy;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class TeacherInterventionImpact {
        private String status;
        private String statusLabel;
        private String summary;
        private String recommendedAction;
        private boolean needsEscalation;
        private String feedbackActionType;
        private LocalDateTime feedbackAt;
        private Long followupSubmissionId;
        private String followupVerdict;
        private List<Long> evidenceSubmissionIds;
        private List<String> matchedTags;
    }

    @Data
    @Builder
    public static class ClassTeachingStrategySignal {
        private String strategyKey;
        private String status;
        private String statusLabel;
        private String strategyType;
        private String title;
        private String summary;
        private String focusAbility;
        private String focusTag;
        private String focusLabel;
        private long affectedStudentCount;
        private Double affectedStudentRatio;
        private Integer priority;
        private String riskLevel;
        private String teacherAction;
        private String exitTicket;
        private List<ClassTeachingStrategyGroup> groups;
        private List<String> evidenceRefs;
        private List<String> sourceSignals;
        private ClassTeachingStrategyImpact impact;
    }

    @Data
    @Builder
    public static class ClassTeachingStrategyImpact {
        private String status;
        private String statusLabel;
        private String summary;
        private String recommendedAction;
        private boolean needsEscalation;
        private String feedbackActionType;
        private LocalDateTime feedbackAt;
        private Long followupSubmissionId;
        private String followupVerdict;
        private List<String> evidenceRefs;
        private List<String> matchedTags;
    }

    @Data
    @Builder
    public static class ClassTeachingStrategyGroup {
        private String groupType;
        private String title;
        private List<Long> studentProfileIds;
        private List<String> studentNames;
        private String focus;
        private String action;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    public static class AttentionEvidence {
        private Long submissionId;
        private Long problemId;
        private String verdict;
        private LocalDateTime submittedAt;
        private String issueTag;
        private String fineGrainedTag;
        private String abilityPoint;
        private String headline;
        private String reason;
    }
}
