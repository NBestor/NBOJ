package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.dto.AiQualityOverviewResponse;
import com.onlinejudge.classroom.dto.CoachImpactResponse;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.dto.RecommendationEffectivenessResponse;
import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.submission.domain.SubmissionAnalysis;

import java.util.List;
import java.util.Map;

class AiQualityMetrics {

    static final double LOW_CONFIDENCE_THRESHOLD = 0.6;

    private final long analyzedSubmissionCount;
    private final long correctionCount;
    private final long evalCandidateCount;
    private final long lowConfidenceCount;
    private final long highLeakRiskCount;
    private final long promptSafetyIncidentCount;
    private final long promptSafetyDowngradeCount;
    private final long promptSafetyHighRiskDowngradeCount;
    private final long promptSafetyCoachRiskCount;
    private final long modelFallbackCount;
    private final long modelPartialCount;
    private final long modelRuntimeFailureCount;
    private final long modelCompletedCount;
    private final long missingEvidenceRefCount;
    private final long learningActionEvidenceCount;
    private final long learningActionObservedCount;
    private final long learningActionPartiallyObservedCount;
    private final long learningActionContradictedCount;
    private final long learningActionNotObservedCount;
    private final long coachAnsweredCount;
    private final long coachVerifiableAnswerCount;
    private final long coachNeedsEvidenceCount;
    private final long coachSafetyRiskCount;
    private final long coachTransferReadyCount;
    private final long coachFollowupImpactCount;
    private final long coachFollowupAcceptedCount;
    private final long coachFollowupShiftedCount;
    private final long coachFollowupSameIssueCount;
    private final long coachFollowupVerdictChangedCount;
    private final long coachFollowupNoClearChangeCount;
    private final long coachFollowupAwaitingCount;
    private final long recommendationUniqueCount;
    private final long recommendationClickedWithoutSubmissionCount;
    private final long recommendationUnresolvedLearningSignalCount;
    private final long recommendationTeacherInterventionRecommendedCount;
    private final long recommendationActionEvidenceCount;
    private final long recommendationActionFulfilledCount;
    private final long recommendationActionWaitingDiagnosisCount;
    private final long teacherInterventionExecutedCount;
    private final long teacherInterventionWaitingFollowupCount;
    private final long teacherInterventionImprovedCount;
    private final long teacherInterventionShiftedCount;
    private final long teacherInterventionStillStuckCount;
    private final long teacherInterventionEscalationCount;
    private final long postAcTransferSignalCount;
    private final long postAcTransferPendingCount;
    private final long postAcTransferEvidencedCount;
    private final long postAcTransferReadyCount;
    private final long postAcTransferVerifiedCount;
    private final long recurringMisconceptionSignalCount;
    private final long recurringMisconceptionWatchCount;
    private final long recurringMisconceptionRecurringCount;
    private final long recurringMisconceptionEscalationCount;
    private final long selfExplanationSignalCount;
    private final long selfExplanationNeedsCoachingCount;
    private final long selfExplanationSafetyRiskCount;
    private final long selfExplanationEvidenceGroundedCount;
    private final long selfExplanationTransferReadyCount;
    private final long aiDependencySignalCount;
    private final long aiDependencyIndependentCount;
    private final long aiDependencyEffectiveCount;
    private final long aiDependencyDenseCount;
    private final long aiDependencyRiskCount;
    private final long aiDependencyTeacherFadeCount;
    private final long masteryGrowthSignalCount;
    private final long masteryGrowthGrowingCount;
    private final long masteryGrowthTransferConfirmedCount;
    private final long masteryGrowthPlateauCount;
    private final long masteryGrowthRegressionCount;
    private final long masteryGrowthSpiralReviewCount;
    private final long teachingActionSignalCount;
    private final long teachingActionHighRiskCount;
    private final long teachingActionTeacherCount;
    private final long teachingActionEvidenceReadyCount;
    private final long teachingActionCandidateCount;
    private final long teacherCalibrationSignalCount;
    private final long teacherCalibrationSupportedCount;
    private final long teacherCalibrationAppliedCount;
    private final long teacherCalibrationConflictCount;
    private final long classTeachingStrategySignalCount;
    private final long classTeachingStrategyActionableCount;
    private final long classTeachingStrategyMissingEvidenceCount;
    private final long classTeachingStrategyMissingExitTicketCount;
    private final long classTeachingStrategyGroupPlanCount;
    private final long classTeachingStrategyFeedbackCount;
    private final long classTeachingStrategyWaitingFollowupCount;
    private final long classTeachingStrategyImprovedCount;
    private final long classTeachingStrategyShiftedCount;
    private final long classTeachingStrategyStillStuckCount;
    private final long classTeachingStrategyEscalationCount;

    private AiQualityMetrics(long analyzedSubmissionCount,
                             long correctionCount,
                             long evalCandidateCount,
                             long lowConfidenceCount,
                             long highLeakRiskCount,
                             long promptSafetyIncidentCount,
                             long promptSafetyDowngradeCount,
                             long promptSafetyHighRiskDowngradeCount,
                             long promptSafetyCoachRiskCount,
                             long modelFallbackCount,
                             long modelPartialCount,
                             long modelRuntimeFailureCount,
                             long modelCompletedCount,
                             long missingEvidenceRefCount,
                             long learningActionEvidenceCount,
                             long learningActionObservedCount,
                             long learningActionPartiallyObservedCount,
                             long learningActionContradictedCount,
                             long learningActionNotObservedCount,
                             long coachAnsweredCount,
                             long coachVerifiableAnswerCount,
                             long coachNeedsEvidenceCount,
                             long coachSafetyRiskCount,
                             long coachTransferReadyCount,
                             long coachFollowupImpactCount,
                             long coachFollowupAcceptedCount,
                             long coachFollowupShiftedCount,
                             long coachFollowupSameIssueCount,
                             long coachFollowupVerdictChangedCount,
                             long coachFollowupNoClearChangeCount,
                             long coachFollowupAwaitingCount,
                             long recommendationUniqueCount,
                             long recommendationClickedWithoutSubmissionCount,
                             long recommendationUnresolvedLearningSignalCount,
                             long recommendationTeacherInterventionRecommendedCount,
                             long recommendationActionEvidenceCount,
                             long recommendationActionFulfilledCount,
                             long recommendationActionWaitingDiagnosisCount,
                             long teacherInterventionExecutedCount,
                             long teacherInterventionWaitingFollowupCount,
                             long teacherInterventionImprovedCount,
                             long teacherInterventionShiftedCount,
                             long teacherInterventionStillStuckCount,
                             long teacherInterventionEscalationCount,
                             long postAcTransferSignalCount,
                             long postAcTransferPendingCount,
                             long postAcTransferEvidencedCount,
                             long postAcTransferReadyCount,
                             long postAcTransferVerifiedCount,
                             long recurringMisconceptionSignalCount,
                             long recurringMisconceptionWatchCount,
                             long recurringMisconceptionRecurringCount,
                             long recurringMisconceptionEscalationCount,
                             long selfExplanationSignalCount,
                             long selfExplanationNeedsCoachingCount,
                             long selfExplanationSafetyRiskCount,
                             long selfExplanationEvidenceGroundedCount,
                             long selfExplanationTransferReadyCount,
                             long aiDependencySignalCount,
                             long aiDependencyIndependentCount,
                             long aiDependencyEffectiveCount,
                             long aiDependencyDenseCount,
                             long aiDependencyRiskCount,
                             long aiDependencyTeacherFadeCount,
                             long masteryGrowthSignalCount,
                             long masteryGrowthGrowingCount,
                             long masteryGrowthTransferConfirmedCount,
                             long masteryGrowthPlateauCount,
                             long masteryGrowthRegressionCount,
                             long masteryGrowthSpiralReviewCount,
                             long teachingActionSignalCount,
                             long teachingActionHighRiskCount,
                             long teachingActionTeacherCount,
                             long teachingActionEvidenceReadyCount,
                             long teachingActionCandidateCount,
                             long teacherCalibrationSignalCount,
                             long teacherCalibrationSupportedCount,
                             long teacherCalibrationAppliedCount,
                             long teacherCalibrationConflictCount,
                             long classTeachingStrategySignalCount,
                             long classTeachingStrategyActionableCount,
                             long classTeachingStrategyMissingEvidenceCount,
                             long classTeachingStrategyMissingExitTicketCount,
                             long classTeachingStrategyGroupPlanCount,
                             long classTeachingStrategyFeedbackCount,
                             long classTeachingStrategyWaitingFollowupCount,
                             long classTeachingStrategyImprovedCount,
                             long classTeachingStrategyShiftedCount,
                             long classTeachingStrategyStillStuckCount,
                             long classTeachingStrategyEscalationCount) {
        this.analyzedSubmissionCount = analyzedSubmissionCount;
        this.correctionCount = correctionCount;
        this.evalCandidateCount = evalCandidateCount;
        this.lowConfidenceCount = lowConfidenceCount;
        this.highLeakRiskCount = highLeakRiskCount;
        this.promptSafetyIncidentCount = promptSafetyIncidentCount;
        this.promptSafetyDowngradeCount = promptSafetyDowngradeCount;
        this.promptSafetyHighRiskDowngradeCount = promptSafetyHighRiskDowngradeCount;
        this.promptSafetyCoachRiskCount = promptSafetyCoachRiskCount;
        this.modelFallbackCount = modelFallbackCount;
        this.modelPartialCount = modelPartialCount;
        this.modelRuntimeFailureCount = modelRuntimeFailureCount;
        this.modelCompletedCount = modelCompletedCount;
        this.missingEvidenceRefCount = missingEvidenceRefCount;
        this.learningActionEvidenceCount = learningActionEvidenceCount;
        this.learningActionObservedCount = learningActionObservedCount;
        this.learningActionPartiallyObservedCount = learningActionPartiallyObservedCount;
        this.learningActionContradictedCount = learningActionContradictedCount;
        this.learningActionNotObservedCount = learningActionNotObservedCount;
        this.coachAnsweredCount = coachAnsweredCount;
        this.coachVerifiableAnswerCount = coachVerifiableAnswerCount;
        this.coachNeedsEvidenceCount = coachNeedsEvidenceCount;
        this.coachSafetyRiskCount = coachSafetyRiskCount;
        this.coachTransferReadyCount = coachTransferReadyCount;
        this.coachFollowupImpactCount = coachFollowupImpactCount;
        this.coachFollowupAcceptedCount = coachFollowupAcceptedCount;
        this.coachFollowupShiftedCount = coachFollowupShiftedCount;
        this.coachFollowupSameIssueCount = coachFollowupSameIssueCount;
        this.coachFollowupVerdictChangedCount = coachFollowupVerdictChangedCount;
        this.coachFollowupNoClearChangeCount = coachFollowupNoClearChangeCount;
        this.coachFollowupAwaitingCount = coachFollowupAwaitingCount;
        this.recommendationUniqueCount = recommendationUniqueCount;
        this.recommendationClickedWithoutSubmissionCount = recommendationClickedWithoutSubmissionCount;
        this.recommendationUnresolvedLearningSignalCount = recommendationUnresolvedLearningSignalCount;
        this.recommendationTeacherInterventionRecommendedCount = recommendationTeacherInterventionRecommendedCount;
        this.recommendationActionEvidenceCount = recommendationActionEvidenceCount;
        this.recommendationActionFulfilledCount = recommendationActionFulfilledCount;
        this.recommendationActionWaitingDiagnosisCount = recommendationActionWaitingDiagnosisCount;
        this.teacherInterventionExecutedCount = teacherInterventionExecutedCount;
        this.teacherInterventionWaitingFollowupCount = teacherInterventionWaitingFollowupCount;
        this.teacherInterventionImprovedCount = teacherInterventionImprovedCount;
        this.teacherInterventionShiftedCount = teacherInterventionShiftedCount;
        this.teacherInterventionStillStuckCount = teacherInterventionStillStuckCount;
        this.teacherInterventionEscalationCount = teacherInterventionEscalationCount;
        this.postAcTransferSignalCount = postAcTransferSignalCount;
        this.postAcTransferPendingCount = postAcTransferPendingCount;
        this.postAcTransferEvidencedCount = postAcTransferEvidencedCount;
        this.postAcTransferReadyCount = postAcTransferReadyCount;
        this.postAcTransferVerifiedCount = postAcTransferVerifiedCount;
        this.recurringMisconceptionSignalCount = recurringMisconceptionSignalCount;
        this.recurringMisconceptionWatchCount = recurringMisconceptionWatchCount;
        this.recurringMisconceptionRecurringCount = recurringMisconceptionRecurringCount;
        this.recurringMisconceptionEscalationCount = recurringMisconceptionEscalationCount;
        this.selfExplanationSignalCount = selfExplanationSignalCount;
        this.selfExplanationNeedsCoachingCount = selfExplanationNeedsCoachingCount;
        this.selfExplanationSafetyRiskCount = selfExplanationSafetyRiskCount;
        this.selfExplanationEvidenceGroundedCount = selfExplanationEvidenceGroundedCount;
        this.selfExplanationTransferReadyCount = selfExplanationTransferReadyCount;
        this.aiDependencySignalCount = aiDependencySignalCount;
        this.aiDependencyIndependentCount = aiDependencyIndependentCount;
        this.aiDependencyEffectiveCount = aiDependencyEffectiveCount;
        this.aiDependencyDenseCount = aiDependencyDenseCount;
        this.aiDependencyRiskCount = aiDependencyRiskCount;
        this.aiDependencyTeacherFadeCount = aiDependencyTeacherFadeCount;
        this.masteryGrowthSignalCount = masteryGrowthSignalCount;
        this.masteryGrowthGrowingCount = masteryGrowthGrowingCount;
        this.masteryGrowthTransferConfirmedCount = masteryGrowthTransferConfirmedCount;
        this.masteryGrowthPlateauCount = masteryGrowthPlateauCount;
        this.masteryGrowthRegressionCount = masteryGrowthRegressionCount;
        this.masteryGrowthSpiralReviewCount = masteryGrowthSpiralReviewCount;
        this.teachingActionSignalCount = teachingActionSignalCount;
        this.teachingActionHighRiskCount = teachingActionHighRiskCount;
        this.teachingActionTeacherCount = teachingActionTeacherCount;
        this.teachingActionEvidenceReadyCount = teachingActionEvidenceReadyCount;
        this.teachingActionCandidateCount = teachingActionCandidateCount;
        this.teacherCalibrationSignalCount = teacherCalibrationSignalCount;
        this.teacherCalibrationSupportedCount = teacherCalibrationSupportedCount;
        this.teacherCalibrationAppliedCount = teacherCalibrationAppliedCount;
        this.teacherCalibrationConflictCount = teacherCalibrationConflictCount;
        this.classTeachingStrategySignalCount = classTeachingStrategySignalCount;
        this.classTeachingStrategyActionableCount = classTeachingStrategyActionableCount;
        this.classTeachingStrategyMissingEvidenceCount = classTeachingStrategyMissingEvidenceCount;
        this.classTeachingStrategyMissingExitTicketCount = classTeachingStrategyMissingExitTicketCount;
        this.classTeachingStrategyGroupPlanCount = classTeachingStrategyGroupPlanCount;
        this.classTeachingStrategyFeedbackCount = classTeachingStrategyFeedbackCount;
        this.classTeachingStrategyWaitingFollowupCount = classTeachingStrategyWaitingFollowupCount;
        this.classTeachingStrategyImprovedCount = classTeachingStrategyImprovedCount;
        this.classTeachingStrategyShiftedCount = classTeachingStrategyShiftedCount;
        this.classTeachingStrategyStillStuckCount = classTeachingStrategyStillStuckCount;
        this.classTeachingStrategyEscalationCount = classTeachingStrategyEscalationCount;
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader) {
        return from(analyses, corrections, diagnosisReportReader, Map.of());
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, null);
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness, null);
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness, teacherInterventionSummary, List.of());
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary,
                                 List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness,
                teacherInterventionSummary, postAcTransferSignals, List.of());
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary,
                                 List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals,
                                 List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness,
                teacherInterventionSummary, postAcTransferSignals, recurringMisconceptionSignals, List.of());
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary,
                                 List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals,
                                 List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals,
                                 List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> selfExplanationSignals) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness,
                teacherInterventionSummary, postAcTransferSignals, recurringMisconceptionSignals, selfExplanationSignals, List.of());
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary,
                                 List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals,
                                 List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals,
                                 List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> selfExplanationSignals,
                                 List<StudentAbilityProfileResponse.AiDependencySignal> aiDependencySignals) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness,
                teacherInterventionSummary, postAcTransferSignals, recurringMisconceptionSignals,
                selfExplanationSignals, aiDependencySignals, List.of());
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary,
                                 List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals,
                                 List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals,
                                 List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> selfExplanationSignals,
                                 List<StudentAbilityProfileResponse.AiDependencySignal> aiDependencySignals,
                                 List<StudentAbilityProfileResponse.MasteryGrowthSignal> masteryGrowthSignals) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness,
                teacherInterventionSummary, postAcTransferSignals, recurringMisconceptionSignals,
                selfExplanationSignals, aiDependencySignals, masteryGrowthSignals, List.of());
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary,
                                 List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals,
                                 List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals,
                                 List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> selfExplanationSignals,
                                 List<StudentAbilityProfileResponse.AiDependencySignal> aiDependencySignals,
                                 List<StudentAbilityProfileResponse.MasteryGrowthSignal> masteryGrowthSignals,
                                 List<StudentAbilityProfileResponse.TeachingActionDecision> teachingActionDecisions) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness,
                teacherInterventionSummary, postAcTransferSignals, recurringMisconceptionSignals,
                selfExplanationSignals, aiDependencySignals, masteryGrowthSignals, teachingActionDecisions, null);
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary,
                                 List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals,
                                 List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals,
                                 List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> selfExplanationSignals,
                                 List<StudentAbilityProfileResponse.AiDependencySignal> aiDependencySignals,
                                 List<StudentAbilityProfileResponse.MasteryGrowthSignal> masteryGrowthSignals,
                                 List<StudentAbilityProfileResponse.TeachingActionDecision> teachingActionDecisions,
                                 com.onlinejudge.classroom.dto.AssignmentOverviewResponse.ClassTeachingStrategySignal classTeachingStrategySignal) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness,
                teacherInterventionSummary, postAcTransferSignals, recurringMisconceptionSignals, selfExplanationSignals,
                aiDependencySignals, masteryGrowthSignals, teachingActionDecisions, classTeachingStrategySignal, Map.of());
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary,
                                 List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals,
                                 List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals,
                                 List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> selfExplanationSignals,
                                 List<StudentAbilityProfileResponse.AiDependencySignal> aiDependencySignals,
                                 List<StudentAbilityProfileResponse.MasteryGrowthSignal> masteryGrowthSignals,
                                 List<StudentAbilityProfileResponse.TeachingActionDecision> teachingActionDecisions,
                                 com.onlinejudge.classroom.dto.AssignmentOverviewResponse.ClassTeachingStrategySignal classTeachingStrategySignal,
                                 Map<Long, CoachImpactResponse> coachImpacts) {
        return from(analyses, corrections, diagnosisReportReader, coachInteractions, recommendationEffectiveness,
                teacherInterventionSummary, postAcTransferSignals, recurringMisconceptionSignals,
                selfExplanationSignals, aiDependencySignals, masteryGrowthSignals, teachingActionDecisions,
                classTeachingStrategySignal, coachImpacts, null);
    }

    static AiQualityMetrics from(List<SubmissionAnalysis> analyses,
                                 List<TeacherDiagnosisCorrection> corrections,
                                 DiagnosisReportReader diagnosisReportReader,
                                 Map<Long, CoachInteractionSummaryResponse> coachInteractions,
                                 RecommendationEffectivenessResponse recommendationEffectiveness,
                                 TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary,
                                 List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals,
                                 List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals,
                                 List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> selfExplanationSignals,
                                 List<StudentAbilityProfileResponse.AiDependencySignal> aiDependencySignals,
                                 List<StudentAbilityProfileResponse.MasteryGrowthSignal> masteryGrowthSignals,
                                 List<StudentAbilityProfileResponse.TeachingActionDecision> teachingActionDecisions,
                                 com.onlinejudge.classroom.dto.AssignmentOverviewResponse.ClassTeachingStrategySignal classTeachingStrategySignal,
                                 Map<Long, CoachImpactResponse> coachImpacts,
                                 AiQualityOverviewResponse.PromptSafetyIncidentSignal promptSafetyIncidentSignal) {
        List<SubmissionAnalysis> safeAnalyses = analyses == null ? List.of() : analyses;
        List<TeacherDiagnosisCorrection> safeCorrections = corrections == null ? List.of() : corrections;
        Map<Long, CoachInteractionSummaryResponse> safeCoachInteractions = coachInteractions == null ? Map.of() : coachInteractions;
        List<CoachImpactResponse> safeCoachImpacts = coachImpacts == null ? List.of() : coachImpacts.values()
                .stream()
                .filter(impact -> impact != null && impact.getStatus() != null && !impact.getStatus().isBlank())
                .toList();
        List<StudentTrajectoryResponse.PostAcTransferSignal> safePostAcTransferSignals =
                postAcTransferSignals == null ? List.of() : postAcTransferSignals.stream()
                        .filter(signal -> signal != null && signal.getPhase() != null && !signal.getPhase().isBlank())
                        .toList();
        List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> safeRecurringMisconceptionSignals =
                recurringMisconceptionSignals == null ? List.of() : recurringMisconceptionSignals.stream()
                        .filter(signal -> signal != null && signal.getStatus() != null && !signal.getStatus().isBlank())
                        .toList();
        List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> safeSelfExplanationSignals =
                selfExplanationSignals == null ? List.of() : selfExplanationSignals.stream()
                        .filter(signal -> signal != null && signal.getStatus() != null && !signal.getStatus().isBlank())
                        .toList();
        List<StudentAbilityProfileResponse.AiDependencySignal> safeAiDependencySignals =
                aiDependencySignals == null ? List.of() : aiDependencySignals.stream()
                        .filter(signal -> signal != null && signal.getStatus() != null && !signal.getStatus().isBlank())
                        .toList();
        List<StudentAbilityProfileResponse.MasteryGrowthSignal> safeMasteryGrowthSignals =
                masteryGrowthSignals == null ? List.of() : masteryGrowthSignals.stream()
                        .filter(signal -> signal != null && signal.getStatus() != null && !signal.getStatus().isBlank())
                        .toList();
        List<StudentAbilityProfileResponse.TeachingActionDecision> safeTeachingActionDecisions =
                teachingActionDecisions == null ? List.of() : teachingActionDecisions.stream()
                        .filter(decision -> decision != null && decision.getActionType() != null && !decision.getActionType().isBlank())
                        .toList();
        long lowConfidenceCount = safeAnalyses.stream()
                .filter(analysis -> {
                    Double confidence = diagnosisReportReader.confidence(analysis);
                    return confidence == null || confidence < LOW_CONFIDENCE_THRESHOLD;
                })
                .count();
        long highLeakRiskCount = safeAnalyses.stream()
                .filter(analysis -> "HIGH".equalsIgnoreCase(diagnosisReportReader.answerLeakRisk(analysis)))
                .count();
        long modelFallbackCount = safeAnalyses.stream()
                .filter(analysis -> {
                    DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
                    return invocation != null && invocation.fallbackUsed();
                })
                .count();
        long modelPartialCount = safeAnalyses.stream()
                .filter(analysis -> {
                    DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
                    return invocation != null && "MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(invocation.status());
                })
                .count();
        long modelRuntimeFailureCount = safeAnalyses.stream()
                .filter(analysis -> {
                    DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
                    return isRuntimeFailure(invocation);
                })
                .count();
        long modelCompletedCount = safeAnalyses.stream()
                .filter(analysis -> {
                    DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
                    return invocation != null && "MODEL_COMPLETED".equalsIgnoreCase(invocation.status());
                })
                .count();
        long missingEvidenceRefCount = safeAnalyses.stream()
                .filter(analysis -> diagnosisReportReader.evidenceRefs(analysis).isEmpty())
                .count();
        List<DiagnosisReportReader.TeacherCalibrationSignalSnapshot> teacherCalibrationSignals = safeAnalyses.stream()
                .map(diagnosisReportReader::teacherCalibrationSignal)
                .filter(signal -> signal != null && signal.status() != null && !signal.status().isBlank())
                .toList();
        long teacherCalibrationSupportedCount = teacherCalibrationSignals.stream()
                .filter(signal -> "SUPPORTED".equals(signal.status()))
                .count();
        long teacherCalibrationAppliedCount = teacherCalibrationSignals.stream()
                .filter(signal -> "APPLIED".equals(signal.status()))
                .count();
        long teacherCalibrationConflictCount = teacherCalibrationSignals.stream()
                .filter(signal -> "CONFLICT_NEEDS_REVIEW".equals(signal.status()))
                .count();
        List<DiagnosisReportReader.LearningActionEvidenceSnapshot> actionEvidence = safeAnalyses.stream()
                .map(diagnosisReportReader::learningActionEvidence)
                .filter(evidence -> evidence != null && evidence.executionStatus() != null && !evidence.executionStatus().isBlank())
                .toList();
        long learningActionObservedCount = actionEvidence.stream()
                .filter(evidence -> "OBSERVED".equals(evidence.executionStatus()))
                .count();
        long learningActionPartiallyObservedCount = actionEvidence.stream()
                .filter(evidence -> "PARTIALLY_OBSERVED".equals(evidence.executionStatus()))
                .count();
        long learningActionContradictedCount = actionEvidence.stream()
                .filter(evidence -> "CONTRADICTED".equals(evidence.executionStatus()))
                .count();
        long learningActionNotObservedCount = actionEvidence.stream()
                .filter(evidence -> "NOT_OBSERVED".equals(evidence.executionStatus()))
                .count();
        List<CoachInteractionSummaryResponse.CoachAnswerQualitySignal> coachSignals = safeCoachInteractions.values()
                .stream()
                .filter(summary -> summary != null && summary.getAnswerQualitySignal() != null)
                .map(CoachInteractionSummaryResponse::getAnswerQualitySignal)
                .toList();
        long coachAnsweredCount = safeCoachInteractions.values().stream()
                .filter(summary -> summary != null && summary.isAnswered())
                .count();
        long coachVerifiableAnswerCount = coachSignals.stream()
                .filter(signal -> Boolean.TRUE.equals(signal.getVerifiable()))
                .count();
        long coachNeedsEvidenceCount = coachSignals.stream()
                .filter(signal -> "NEEDS_EVIDENCE".equals(signal.getActionStatus())
                        || "NOT_ANSWERED".equals(signal.getActionStatus()))
                .count();
        long coachSafetyRiskCount = coachSignals.stream()
                .filter(signal -> "SAFETY_RISK".equals(signal.getActionStatus()))
                .count();
        long coachTransferReadyCount = coachSignals.stream()
                .filter(signal -> "READY_TO_TRANSFER".equals(signal.getActionStatus()))
                .count();
        long coachFollowupAcceptedCount = safeCoachImpacts.stream()
                .filter(impact -> "FOLLOWUP_ACCEPTED".equals(impact.getStatus()))
                .count();
        long coachFollowupShiftedCount = safeCoachImpacts.stream()
                .filter(impact -> "ISSUE_SHIFTED".equals(impact.getStatus()))
                .count();
        long coachFollowupSameIssueCount = safeCoachImpacts.stream()
                .filter(impact -> "SAME_ISSUE".equals(impact.getStatus()))
                .count();
        long coachFollowupVerdictChangedCount = safeCoachImpacts.stream()
                .filter(impact -> "VERDICT_CHANGED".equals(impact.getStatus()))
                .count();
        long coachFollowupNoClearChangeCount = safeCoachImpacts.stream()
                .filter(impact -> "NO_CLEAR_CHANGE".equals(impact.getStatus()))
                .count();
        long coachFollowupAwaitingCount = safeCoachImpacts.stream()
                .filter(impact -> "AWAITING_FOLLOWUP".equals(impact.getStatus()))
                .count();
        long postAcTransferPendingCount = safePostAcTransferSignals.stream()
                .filter(signal -> "JUST_ACCEPTED".equals(signal.getPhase()) || "REFLECTION_NEEDED".equals(signal.getPhase()))
                .count();
        long postAcTransferEvidencedCount = safePostAcTransferSignals.stream()
                .filter(signal -> "REFLECTION_EVIDENCED".equals(signal.getPhase()))
                .count();
        long postAcTransferReadyCount = safePostAcTransferSignals.stream()
                .filter(signal -> "TRANSFER_READY".equals(signal.getPhase()))
                .count();
        long postAcTransferVerifiedCount = safePostAcTransferSignals.stream()
                .filter(signal -> "TRANSFER_VERIFIED".equals(signal.getPhase()))
                .count();
        long recurringWatchCount = safeRecurringMisconceptionSignals.stream()
                .filter(signal -> "WATCH".equals(signal.getStatus()))
                .count();
        long recurringCount = safeRecurringMisconceptionSignals.stream()
                .filter(signal -> "RECURRING".equals(signal.getStatus()))
                .count();
        long recurringEscalationCount = safeRecurringMisconceptionSignals.stream()
                .filter(signal -> "ESCALATE".equals(signal.getStatus()))
                .count();
        long selfExplanationNeedsCoachingCount = safeSelfExplanationSignals.stream()
                .filter(signal -> "NEEDS_COACHING".equals(signal.getStatus()) || "EMERGING".equals(signal.getStatus()))
                .count();
        long selfExplanationSafetyRiskCount = safeSelfExplanationSignals.stream()
                .filter(signal -> "SAFETY_RISK".equals(signal.getStatus()))
                .count();
        long selfExplanationEvidenceGroundedCount = safeSelfExplanationSignals.stream()
                .filter(signal -> "EVIDENCE_GROUNDED".equals(signal.getStatus()))
                .count();
        long selfExplanationTransferReadyCount = safeSelfExplanationSignals.stream()
                .filter(signal -> "TRANSFER_READY".equals(signal.getStatus()))
                .count();
        long aiDependencyIndependentCount = safeAiDependencySignals.stream()
                .filter(signal -> AiDependencyAnalyzer.STATUS_INDEPENDENT_PROGRESS.equals(signal.getStatus()))
                .count();
        long aiDependencyEffectiveCount = safeAiDependencySignals.stream()
                .filter(signal -> AiDependencyAnalyzer.STATUS_SCAFFOLD_EFFECTIVE.equals(signal.getStatus()))
                .count();
        long aiDependencyDenseCount = safeAiDependencySignals.stream()
                .filter(signal -> AiDependencyAnalyzer.STATUS_SCAFFOLD_DENSE.equals(signal.getStatus()))
                .count();
        long aiDependencyRiskCount = safeAiDependencySignals.stream()
                .filter(signal -> AiDependencyAnalyzer.STATUS_DEPENDENCY_RISK.equals(signal.getStatus()))
                .count();
        long aiDependencyTeacherFadeCount = safeAiDependencySignals.stream()
                .filter(signal -> AiDependencyAnalyzer.STATUS_TEACHER_FADE_REVIEW.equals(signal.getStatus()))
                .count();
        long masteryGrowthGrowingCount = safeMasteryGrowthSignals.stream()
                .filter(signal -> MasteryGrowthAnalyzer.STATUS_GROWING.equals(signal.getStatus()))
                .count();
        long masteryGrowthTransferConfirmedCount = safeMasteryGrowthSignals.stream()
                .filter(signal -> MasteryGrowthAnalyzer.STATUS_TRANSFER_CONFIRMED.equals(signal.getStatus()))
                .count();
        long masteryGrowthPlateauCount = safeMasteryGrowthSignals.stream()
                .filter(signal -> MasteryGrowthAnalyzer.STATUS_PLATEAU.equals(signal.getStatus()))
                .count();
        long masteryGrowthRegressionCount = safeMasteryGrowthSignals.stream()
                .filter(signal -> MasteryGrowthAnalyzer.STATUS_REGRESSION.equals(signal.getStatus()))
                .count();
        long masteryGrowthSpiralReviewCount = safeMasteryGrowthSignals.stream()
                .filter(signal -> MasteryGrowthAnalyzer.STATUS_SPIRAL_REVIEW_NEEDED.equals(signal.getStatus()))
                .count();
        long teachingActionHighRiskCount = safeTeachingActionDecisions.stream()
                .filter(decision -> "HIGH".equals(decision.getRiskLevel()) || decision.isNeedsTeacherAttention())
                .count();
        long teachingActionTeacherCount = safeTeachingActionDecisions.stream()
                .filter(decision -> "TEACHER".equals(decision.getActor()))
                .count();
        long teachingActionEvidenceReadyCount = safeTeachingActionDecisions.stream()
                .filter(decision -> decision.getEvidenceRefs() != null && !decision.getEvidenceRefs().isEmpty())
                .count();
        long teachingActionCandidateCount = safeTeachingActionDecisions.stream()
                .map(StudentAbilityProfileResponse.TeachingActionDecision::getCandidateCount)
                .filter(count -> count != null && count > 0)
                .mapToLong(Integer::longValue)
                .sum();
        boolean hasClassStrategySignal = classTeachingStrategySignal != null
                && classTeachingStrategySignal.getStatus() != null
                && !classTeachingStrategySignal.getStatus().isBlank();
        boolean actionableClassStrategy = hasClassStrategySignal
                && !ClassTeachingStrategyAnalyzer.STATUS_NO_SIGNAL.equals(classTeachingStrategySignal.getStatus())
                && !ClassTeachingStrategyAnalyzer.STATUS_WATCH.equals(classTeachingStrategySignal.getStatus());
        com.onlinejudge.classroom.dto.AssignmentOverviewResponse.ClassTeachingStrategyImpact classStrategyImpact =
                classTeachingStrategySignal == null ? null : classTeachingStrategySignal.getImpact();
        String classStrategyImpactStatus = classStrategyImpact == null || classStrategyImpact.getStatus() == null
                ? ""
                : classStrategyImpact.getStatus();
        boolean classStrategyHasFeedback = classStrategyImpact != null
                && classStrategyImpact.getFeedbackActionType() != null
                && !classStrategyImpact.getFeedbackActionType().isBlank();
        return new AiQualityMetrics(
                safeAnalyses.size(),
                safeCorrections.size(),
                safeCorrections.stream().filter(TeacherDiagnosisCorrection::isEvalCandidate).count(),
                lowConfidenceCount,
                highLeakRiskCount,
                promptSafetyIncidentSignal == null ? 0 : promptSafetyIncidentSignal.getTotalIncidentCount(),
                promptSafetyIncidentSignal == null ? 0 : promptSafetyIncidentSignal.getSafetyDowngradeCount(),
                promptSafetyIncidentSignal == null ? 0 : promptSafetyIncidentSignal.getHighRiskSafetyDowngradeCount(),
                promptSafetyIncidentSignal == null ? 0 : promptSafetyIncidentSignal.getCoachSafetyRiskCount(),
                modelFallbackCount,
                modelPartialCount,
                modelRuntimeFailureCount,
                modelCompletedCount,
                missingEvidenceRefCount,
                actionEvidence.size(),
                learningActionObservedCount,
                learningActionPartiallyObservedCount,
                learningActionContradictedCount,
                learningActionNotObservedCount,
                coachAnsweredCount,
                coachVerifiableAnswerCount,
                coachNeedsEvidenceCount,
                coachSafetyRiskCount,
                coachTransferReadyCount,
                safeCoachImpacts.size(),
                coachFollowupAcceptedCount,
                coachFollowupShiftedCount,
                coachFollowupSameIssueCount,
                coachFollowupVerdictChangedCount,
                coachFollowupNoClearChangeCount,
                coachFollowupAwaitingCount,
                recommendationEffectiveness == null ? 0 : recommendationEffectiveness.getUniqueRecommendationCount(),
                recommendationEffectiveness == null ? 0 : recommendationEffectiveness.getClickedWithoutSubmissionCount(),
                recommendationEffectiveness == null ? 0 : recommendationEffectiveness.getUnresolvedLearningSignalCount(),
                recommendationEffectiveness == null ? 0 : recommendationEffectiveness.getTeacherInterventionRecommendedCount(),
                recommendationEffectiveness == null || recommendationEffectiveness.getActionEvidenceSignals() == null
                        ? 0
                        : recommendationEffectiveness.getActionEvidenceSignals().size(),
                recommendationEffectiveness == null || recommendationEffectiveness.getActionEvidenceSignals() == null
                        ? 0
                        : recommendationEffectiveness.getActionEvidenceSignals().stream()
                        .filter(signal -> RecommendationActionEvidenceAnalyzer.OUTCOME_CONTRACT_FULFILLED.equals(signal.getOutcome()))
                        .count(),
                recommendationEffectiveness == null || recommendationEffectiveness.getActionEvidenceSignals() == null
                        ? 0
                        : recommendationEffectiveness.getActionEvidenceSignals().stream()
                        .filter(signal -> RecommendationActionEvidenceAnalyzer.OUTCOME_WAITING_DIAGNOSIS.equals(signal.getOutcome()))
                        .count(),
                teacherInterventionSummary == null ? 0 : teacherInterventionSummary.getExecutedCount(),
                teacherInterventionSummary == null ? 0 : teacherInterventionSummary.getWaitingFollowupCount(),
                teacherInterventionSummary == null ? 0 : teacherInterventionSummary.getImprovedCount(),
                teacherInterventionSummary == null ? 0 : teacherInterventionSummary.getShiftedCount(),
                teacherInterventionSummary == null ? 0 : teacherInterventionSummary.getStillStuckCount(),
                teacherInterventionSummary == null ? 0 : teacherInterventionSummary.getEscalationCount(),
                safePostAcTransferSignals.size(),
                postAcTransferPendingCount,
                postAcTransferEvidencedCount,
                postAcTransferReadyCount,
                postAcTransferVerifiedCount,
                safeRecurringMisconceptionSignals.size(),
                recurringWatchCount,
                recurringCount,
                recurringEscalationCount,
                safeSelfExplanationSignals.size(),
                selfExplanationNeedsCoachingCount,
                selfExplanationSafetyRiskCount,
                selfExplanationEvidenceGroundedCount,
                selfExplanationTransferReadyCount,
                safeAiDependencySignals.size(),
                aiDependencyIndependentCount,
                aiDependencyEffectiveCount,
                aiDependencyDenseCount,
                aiDependencyRiskCount,
                aiDependencyTeacherFadeCount,
                safeMasteryGrowthSignals.size(),
                masteryGrowthGrowingCount,
                masteryGrowthTransferConfirmedCount,
                masteryGrowthPlateauCount,
                masteryGrowthRegressionCount,
                masteryGrowthSpiralReviewCount,
                safeTeachingActionDecisions.size(),
                teachingActionHighRiskCount,
                teachingActionTeacherCount,
                teachingActionEvidenceReadyCount,
                teachingActionCandidateCount,
                teacherCalibrationSignals.size(),
                teacherCalibrationSupportedCount,
                teacherCalibrationAppliedCount,
                teacherCalibrationConflictCount,
                hasClassStrategySignal ? 1 : 0,
                actionableClassStrategy ? 1 : 0,
                actionableClassStrategy && (classTeachingStrategySignal.getEvidenceRefs() == null
                        || classTeachingStrategySignal.getEvidenceRefs().isEmpty()) ? 1 : 0,
                actionableClassStrategy && (classTeachingStrategySignal.getExitTicket() == null
                        || classTeachingStrategySignal.getExitTicket().isBlank()) ? 1 : 0,
                classTeachingStrategySignal != null && classTeachingStrategySignal.getGroups() != null
                        && !classTeachingStrategySignal.getGroups().isEmpty() ? 1 : 0,
                classStrategyHasFeedback ? 1 : 0,
                ClassTeachingStrategyImpactAnalyzer.STATUS_WAITING_FOLLOWUP.equals(classStrategyImpactStatus) ? 1 : 0,
                ClassTeachingStrategyImpactAnalyzer.STATUS_IMPROVED.equals(classStrategyImpactStatus) ? 1 : 0,
                ClassTeachingStrategyImpactAnalyzer.STATUS_SHIFTED.equals(classStrategyImpactStatus) ? 1 : 0,
                ClassTeachingStrategyImpactAnalyzer.STATUS_STILL_STUCK.equals(classStrategyImpactStatus) ? 1 : 0,
                classStrategyImpact != null && classStrategyImpact.isNeedsEscalation() ? 1 : 0
        );
    }

    private static boolean isRuntimeFailure(DiagnosisReportReader.AiInvocationSnapshot invocation) {
        return invocation != null
                && ("MODEL_RUNTIME_FALLBACK".equalsIgnoreCase(invocation.status())
                || "MODEL_FAILED".equalsIgnoreCase(invocation.status())
                || invocation.fallbackUsed());
    }

    long analyzedSubmissionCount() {
        return analyzedSubmissionCount;
    }

    long correctionCount() {
        return correctionCount;
    }

    long evalCandidateCount() {
        return evalCandidateCount;
    }

    long lowConfidenceCount() {
        return lowConfidenceCount;
    }

    long highLeakRiskCount() {
        return highLeakRiskCount;
    }

    long promptSafetyIncidentCount() {
        return promptSafetyIncidentCount;
    }

    long promptSafetyDowngradeCount() {
        return promptSafetyDowngradeCount;
    }

    long promptSafetyHighRiskDowngradeCount() {
        return promptSafetyHighRiskDowngradeCount;
    }

    long promptSafetyCoachRiskCount() {
        return promptSafetyCoachRiskCount;
    }

    long modelFallbackCount() {
        return modelFallbackCount;
    }

    long modelPartialCount() {
        return modelPartialCount;
    }

    long modelRuntimeFailureCount() {
        return modelRuntimeFailureCount;
    }

    long modelCompletedCount() {
        return modelCompletedCount;
    }

    long missingEvidenceRefCount() {
        return missingEvidenceRefCount;
    }

    long learningActionEvidenceCount() {
        return learningActionEvidenceCount;
    }

    long learningActionObservedCount() {
        return learningActionObservedCount;
    }

    long learningActionPartiallyObservedCount() {
        return learningActionPartiallyObservedCount;
    }

    long learningActionContradictedCount() {
        return learningActionContradictedCount;
    }

    long learningActionNotObservedCount() {
        return learningActionNotObservedCount;
    }

    long coachAnsweredCount() {
        return coachAnsweredCount;
    }

    long coachVerifiableAnswerCount() {
        return coachVerifiableAnswerCount;
    }

    long coachNeedsEvidenceCount() {
        return coachNeedsEvidenceCount;
    }

    long coachSafetyRiskCount() {
        return coachSafetyRiskCount;
    }

    long coachTransferReadyCount() {
        return coachTransferReadyCount;
    }

    long coachFollowupImpactCount() {
        return coachFollowupImpactCount;
    }

    long coachFollowupAcceptedCount() {
        return coachFollowupAcceptedCount;
    }

    long coachFollowupShiftedCount() {
        return coachFollowupShiftedCount;
    }

    long coachFollowupSameIssueCount() {
        return coachFollowupSameIssueCount;
    }

    long coachFollowupVerdictChangedCount() {
        return coachFollowupVerdictChangedCount;
    }

    long coachFollowupNoClearChangeCount() {
        return coachFollowupNoClearChangeCount;
    }

    long coachFollowupAwaitingCount() {
        return coachFollowupAwaitingCount;
    }

    long recommendationUniqueCount() {
        return recommendationUniqueCount;
    }

    long recommendationClickedWithoutSubmissionCount() {
        return recommendationClickedWithoutSubmissionCount;
    }

    long recommendationUnresolvedLearningSignalCount() {
        return recommendationUnresolvedLearningSignalCount;
    }

    long recommendationTeacherInterventionRecommendedCount() {
        return recommendationTeacherInterventionRecommendedCount;
    }

    long recommendationActionEvidenceCount() {
        return recommendationActionEvidenceCount;
    }

    long recommendationActionFulfilledCount() {
        return recommendationActionFulfilledCount;
    }

    long recommendationActionWaitingDiagnosisCount() {
        return recommendationActionWaitingDiagnosisCount;
    }

    long teacherInterventionExecutedCount() {
        return teacherInterventionExecutedCount;
    }

    long teacherInterventionWaitingFollowupCount() {
        return teacherInterventionWaitingFollowupCount;
    }

    long teacherInterventionImprovedCount() {
        return teacherInterventionImprovedCount;
    }

    long teacherInterventionShiftedCount() {
        return teacherInterventionShiftedCount;
    }

    long teacherInterventionStillStuckCount() {
        return teacherInterventionStillStuckCount;
    }

    long teacherInterventionEscalationCount() {
        return teacherInterventionEscalationCount;
    }

    long postAcTransferSignalCount() {
        return postAcTransferSignalCount;
    }

    long postAcTransferPendingCount() {
        return postAcTransferPendingCount;
    }

    long postAcTransferEvidencedCount() {
        return postAcTransferEvidencedCount;
    }

    long postAcTransferReadyCount() {
        return postAcTransferReadyCount;
    }

    long postAcTransferVerifiedCount() {
        return postAcTransferVerifiedCount;
    }

    long recurringMisconceptionSignalCount() {
        return recurringMisconceptionSignalCount;
    }

    long recurringMisconceptionWatchCount() {
        return recurringMisconceptionWatchCount;
    }

    long recurringMisconceptionRecurringCount() {
        return recurringMisconceptionRecurringCount;
    }

    long recurringMisconceptionEscalationCount() {
        return recurringMisconceptionEscalationCount;
    }

    long selfExplanationSignalCount() {
        return selfExplanationSignalCount;
    }

    long selfExplanationNeedsCoachingCount() {
        return selfExplanationNeedsCoachingCount;
    }

    long selfExplanationSafetyRiskCount() {
        return selfExplanationSafetyRiskCount;
    }

    long selfExplanationEvidenceGroundedCount() {
        return selfExplanationEvidenceGroundedCount;
    }

    long selfExplanationTransferReadyCount() {
        return selfExplanationTransferReadyCount;
    }

    long aiDependencySignalCount() {
        return aiDependencySignalCount;
    }

    long aiDependencyIndependentCount() {
        return aiDependencyIndependentCount;
    }

    long aiDependencyEffectiveCount() {
        return aiDependencyEffectiveCount;
    }

    long aiDependencyDenseCount() {
        return aiDependencyDenseCount;
    }

    long aiDependencyRiskCount() {
        return aiDependencyRiskCount;
    }

    long aiDependencyTeacherFadeCount() {
        return aiDependencyTeacherFadeCount;
    }

    long masteryGrowthSignalCount() {
        return masteryGrowthSignalCount;
    }

    long masteryGrowthGrowingCount() {
        return masteryGrowthGrowingCount;
    }

    long masteryGrowthTransferConfirmedCount() {
        return masteryGrowthTransferConfirmedCount;
    }

    long masteryGrowthPlateauCount() {
        return masteryGrowthPlateauCount;
    }

    long masteryGrowthRegressionCount() {
        return masteryGrowthRegressionCount;
    }

    long masteryGrowthSpiralReviewCount() {
        return masteryGrowthSpiralReviewCount;
    }

    long teachingActionSignalCount() {
        return teachingActionSignalCount;
    }

    long teachingActionHighRiskCount() {
        return teachingActionHighRiskCount;
    }

    long teachingActionTeacherCount() {
        return teachingActionTeacherCount;
    }

    long teachingActionEvidenceReadyCount() {
        return teachingActionEvidenceReadyCount;
    }

    long teachingActionCandidateCount() {
        return teachingActionCandidateCount;
    }

    long teacherCalibrationSignalCount() {
        return teacherCalibrationSignalCount;
    }

    long teacherCalibrationSupportedCount() {
        return teacherCalibrationSupportedCount;
    }

    long teacherCalibrationAppliedCount() {
        return teacherCalibrationAppliedCount;
    }

    long teacherCalibrationConflictCount() {
        return teacherCalibrationConflictCount;
    }

    long classTeachingStrategySignalCount() {
        return classTeachingStrategySignalCount;
    }

    long classTeachingStrategyActionableCount() {
        return classTeachingStrategyActionableCount;
    }

    long classTeachingStrategyMissingEvidenceCount() {
        return classTeachingStrategyMissingEvidenceCount;
    }

    long classTeachingStrategyMissingExitTicketCount() {
        return classTeachingStrategyMissingExitTicketCount;
    }

    long classTeachingStrategyGroupPlanCount() {
        return classTeachingStrategyGroupPlanCount;
    }

    long classTeachingStrategyFeedbackCount() {
        return classTeachingStrategyFeedbackCount;
    }

    long classTeachingStrategyWaitingFollowupCount() {
        return classTeachingStrategyWaitingFollowupCount;
    }

    long classTeachingStrategyImprovedCount() {
        return classTeachingStrategyImprovedCount;
    }

    long classTeachingStrategyShiftedCount() {
        return classTeachingStrategyShiftedCount;
    }

    long classTeachingStrategyStillStuckCount() {
        return classTeachingStrategyStillStuckCount;
    }

    long classTeachingStrategyEscalationCount() {
        return classTeachingStrategyEscalationCount;
    }

    double correctionRate() {
        return rate(correctionCount, analyzedSubmissionCount);
    }

    double lowConfidenceRate() {
        return rate(lowConfidenceCount, analyzedSubmissionCount);
    }

    double highLeakRiskRate() {
        return rate(highLeakRiskCount, analyzedSubmissionCount);
    }

    double promptSafetyIncidentRate() {
        return rate(promptSafetyIncidentCount, analyzedSubmissionCount);
    }

    double modelFallbackRate() {
        return rate(modelFallbackCount, analyzedSubmissionCount);
    }

    double modelRuntimeFailureRate() {
        return rate(modelRuntimeFailureCount, analyzedSubmissionCount);
    }

    double missingEvidenceRefRate() {
        return rate(missingEvidenceRefCount, analyzedSubmissionCount);
    }

    double learningActionContradictedRate() {
        return rate(learningActionContradictedCount, learningActionEvidenceCount);
    }

    double coachVerifiableAnswerRate() {
        return rate(coachVerifiableAnswerCount, coachAnsweredCount);
    }

    double coachFollowupImprovementRate() {
        return rate(coachFollowupAcceptedCount + coachFollowupShiftedCount + coachFollowupVerdictChangedCount, coachFollowupImpactCount);
    }

    double recommendationUnresolvedLearningSignalRate() {
        return rate(recommendationUnresolvedLearningSignalCount, recommendationUniqueCount);
    }

    double teacherInterventionImprovementRate() {
        return rate(teacherInterventionImprovedCount + teacherInterventionShiftedCount, teacherInterventionExecutedCount);
    }

    double postAcTransferPendingRate() {
        return rate(postAcTransferPendingCount, postAcTransferSignalCount);
    }

    static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return Math.round((numerator * 1000.0 / denominator)) / 10.0;
    }
}
