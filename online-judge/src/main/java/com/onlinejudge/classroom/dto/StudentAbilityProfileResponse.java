package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StudentAbilityProfileResponse {
    private StudentProfileResponse student;
    private List<Long> mergedStudentProfileIds;
    private long totalSubmissions;
    private long problemCount;
    private long assignmentCount;
    private long failedSubmissionCount;
    private String primaryAbilityFocus;
    private String summary;
    private String trendSignal;
    private String recommendationEffectSummary;
    private String coachImpactSummary;
    private RecurringMisconceptionSignal recurringMisconceptionSignal;
    private SelfExplanationMasterySignal selfExplanationMasterySignal;
    private AiDependencySignal aiDependencySignal;
    private MasteryGrowthSignal masteryGrowthSignal;
    private TeachingActionDecision teachingActionDecision;
    private CoachInteractionSummaryResponse latestCoachInteraction;
    private CoachImpactResponse latestCoachImpact;
    private List<AbilityStat> abilityGaps;
    private List<ProfileStat> knowledgeFocus;
    private List<ProfileStat> commonMistakeFocus;
    private List<ProfileStat> boundaryFocus;
    private FineGrainedLearningProfile fineGrainedProfile;
    private List<ReviewCard> reviewCards;

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
    public static class ProfileStat {
        private String label;
        private long count;
        private List<Long> evidenceProblemIds;
    }

    @Data
    @Builder
    public static class FineGrainedLearningProfile {
        private String aiContextSummary;
        private List<ProfileStat> issueTagFocus;
        private List<ProfileStat> fineGrainedTagFocus;
        private List<ProfileStat> abilityPointFocus;
        private List<ProfileStat> focusPointFocus;
    }

    @Data
    @Builder
    public static class ReviewCard {
        private Long submissionId;
        private Long problemId;
        private String problemTitle;
        private String verdict;
        private String primaryIssueTag;
        private String primaryFineGrainedTag;
        private String abilityPoint;
        private String summary;
        private String nextAction;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    public static class RecurringMisconceptionSignal {
        private String status;
        private String label;
        private String summary;
        private String misconceptionTag;
        private String fineGrainedTag;
        private String abilityPoint;
        private long problemCount;
        private long assignmentCount;
        private long submissionCount;
        private List<String> evidenceRefs;
        private List<Long> evidenceProblemIds;
        private String recommendedAction;
        private boolean needsTeacherAttention;
    }

    @Data
    @Builder
    public static class SelfExplanationMasterySignal {
        private String status;
        private String label;
        private String summary;
        private Double evidenceCompleteness;
        private long answeredTurnCount;
        private long verifiableAnswerCount;
        private long transferReadyCount;
        private long vagueAnswerCount;
        private long safetyRiskCount;
        private List<String> evidenceTypes;
        private List<String> evidenceRefs;
        private String recommendedAction;
        private boolean needsTeacherAttention;
    }

    @Data
    @Builder
    public static class AiDependencySignal {
        private String status;
        private String label;
        private String summary;
        private Double independenceScore;
        private long coachPromptCount;
        private long answeredCoachCount;
        private long recommendationClickCount;
        private long recommendationSubmissionCount;
        private long independentSubmissionCount;
        private long independentAcceptedCount;
        private long scaffoldedAcceptedCount;
        private List<String> dependencyEvidenceRefs;
        private String recommendedAction;
        private boolean needsTeacherAttention;
    }

    @Data
    @Builder
    public static class MasteryGrowthSignal {
        private String status;
        private String label;
        private String summary;
        private Double growthScore;
        private String focusAbility;
        private String focusTag;
        private String fineGrainedTag;
        private long recentSubmissionCount;
        private long recentAcceptedCount;
        private long recentFailedCount;
        private long crossProblemEvidenceCount;
        private long regressionCount;
        private long plateauCount;
        private List<String> evidenceRefs;
        private String recommendedAction;
        private boolean needsTeacherAttention;
    }

    @Data
    @Builder
    public static class TeachingActionDecision {
        private String actionType;
        private String actor;
        private Integer priority;
        private String riskLevel;
        private String title;
        private String summary;
        private String primaryReason;
        private String recommendedAction;
        private String fallbackAction;
        private List<String> evidenceRefs;
        private List<String> sourceSignals;
        private Integer candidateCount;
        private boolean needsTeacherAttention;
    }
}
