package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StudentTrajectoryResponse {
    private AssignmentResponse assignment;
    private StudentProfileResponse student;
    private int totalTasks;
    private int completedTasks;
    private int totalAttempts;
    private String stageTransition;
    private String repeatedIssueTag;
    private String repeatedFineGrainedTag;
    private long repeatedIssueCount;
    private String nextStep;
    private String attentionReason;
    private String improvementSignal;
    private LearningTrajectorySignal latestLearningTrajectorySignal;
    private LearningInterventionPlan latestLearningInterventionPlan;
    private LearningInterventionImpact latestLearningInterventionImpact;
    private LearningActionEvidence latestLearningActionEvidence;
    private AiFeedbackImpact latestAiFeedbackImpact;
    private PostAcTransferSignal postAcTransferSignal;
    private StudentAbilityProfileResponse.SelfExplanationMasterySignal selfExplanationMasterySignal;
    private StudentAbilityProfileResponse.AiDependencySignal aiDependencySignal;
    private StudentAbilityProfileResponse.MasteryGrowthSignal masteryGrowthSignal;
    private StudentAbilityProfileResponse.TeachingActionDecision teachingActionDecision;
    private String primaryAbilityFocus;
    private String crossProblemSummary;
    private CoachInteractionSummaryResponse latestCoachInteraction;
    private CoachImpactResponse latestCoachImpact;
    private List<IssueStat> recentIssueDistribution;
    private List<IssueStat> recentFineGrainedIssueDistribution;
    private List<AbilityStat> abilitySummary;
    private List<TaskTrajectory> tasks;

    @Data
    @Builder
    public static class IssueStat {
        private String label;
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
    public static class TaskTrajectory {
        private Long problemId;
        private String title;
        private String difficulty;
        private int attemptCount;
        private boolean passed;
        private String latestVerdict;
        private String latestProgressSignal;
        private LearningTrajectorySignal latestLearningTrajectorySignal;
        private LearningInterventionPlan latestLearningInterventionPlan;
        private LearningInterventionImpact latestLearningInterventionImpact;
        private LearningActionEvidence latestLearningActionEvidence;
        private AiFeedbackImpact latestAiFeedbackImpact;
        private PostAcTransferSignal postAcTransferSignal;
        private String latestHint;
        private String latestImprovementSignal;
        private CoachInteractionSummaryResponse latestCoachInteraction;
        private CoachImpactResponse latestCoachImpact;
        private List<SubmissionPoint> submissions;
    }

    @Data
    @Builder
    public static class SubmissionPoint {
        private Long submissionId;
        private String verdict;
        private LocalDateTime submittedAt;
        private List<String> issueTags;
        private List<String> fineGrainedTags;
        private String progressSignal;
        private LearningTrajectorySignal learningTrajectorySignal;
        private LearningInterventionPlan learningInterventionPlan;
        private LearningInterventionImpact learningInterventionImpact;
        private LearningActionEvidence learningActionEvidence;
        private AiFeedbackImpact aiFeedbackImpact;
        private String improvementSignal;
        private CoachInteractionSummaryResponse coachInteraction;
        private CoachImpactResponse coachImpact;
    }

    @Data
    @Builder
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
    public static class LearningInterventionImpact {
        private Long interventionSubmissionId;
        private Long followupSubmissionId;
        private Long problemId;
        private String interventionType;
        private String status;
        private String statusLabel;
        private String summary;
        private String previousVerdict;
        private String followupVerdict;
        private String previousIssueTag;
        private String previousFineGrainedTag;
        private String followupIssueTag;
        private String followupFineGrainedTag;
        private LocalDateTime plannedAt;
        private LocalDateTime followupSubmittedAt;
    }

    @Data
    @Builder
    public static class LearningActionEvidence {
        private String expectedActionType;
        private String executionStatus;
        private String statusLabel;
        private String observedEvidence;
        private Double confidence;
        private List<String> evidenceRefs;
        private String nextAdjustment;
    }

    @Data
    @Builder
    public static class AiFeedbackImpact {
        private Long feedbackSubmissionId;
        private Long followupSubmissionId;
        private Long problemId;
        private String status;
        private String statusLabel;
        private String summary;
        private String feedbackStatus;
        private LocalDateTime feedbackViewedAt;
        private String previousVerdict;
        private String followupVerdict;
        private String previousIssueTag;
        private String previousFineGrainedTag;
        private String followupIssueTag;
        private String followupFineGrainedTag;
        private List<String> evidenceRefs;
        private boolean needsTeacherAttention;
    }

    @Data
    @Builder
    public static class PostAcTransferSignal {
        private String phase;
        private String label;
        private String summary;
        private List<String> evidenceRefs;
        private String recommendedAction;
        private String targetAbility;
        private List<String> targetTags;
        private Long problemId;
        private String problemTitle;
        private boolean needsTeacherAttention;
    }
}
