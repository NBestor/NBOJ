package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.HintSafetyCheck;
import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.dto.AssignmentOverviewResponse;
import com.onlinejudge.classroom.dto.AiQualityOverviewResponse;
import com.onlinejudge.classroom.dto.CoachImpactResponse;
import com.onlinejudge.classroom.dto.RecommendationEffectivenessResponse;
import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.HintSafetyCheckRepository;
import com.onlinejudge.classroom.persistence.StudentRecommendationEventRepository;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AiQualityOverviewService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final TeacherDiagnosisCorrectionRepository teacherDiagnosisCorrectionRepository;
    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;
    private final CoachInteractionAnalyzer coachInteractionAnalyzer;
    private final CoachImpactAnalyzer coachImpactAnalyzer;
    private final RecommendationEffectivenessService recommendationEffectivenessService;
    private final ClassReviewFeedbackService classReviewFeedbackService;
    private final TeacherInterventionImpactAnalyzer teacherInterventionImpactAnalyzer;
    private final PostAcTransferAnalyzer postAcTransferAnalyzer;
    private final RecurringMisconceptionAnalyzer recurringMisconceptionAnalyzer;
    private final SelfExplanationMasteryAnalyzer selfExplanationMasteryAnalyzer;
    private final StudentRecommendationEventRepository recommendationEventRepository;
    private final AiDependencyAnalyzer aiDependencyAnalyzer;
    private final MasteryGrowthAnalyzer masteryGrowthAnalyzer;
    private final TeachingActionOrchestrator teachingActionOrchestrator;
    private final ClassTeachingStrategyAnalyzer classTeachingStrategyAnalyzer;
    private final ClassTeachingStrategyImpactAnalyzer classTeachingStrategyImpactAnalyzer;
    private final HintSafetyCheckRepository hintSafetyCheckRepository;
    private final PromptSafetyIncidentAnalyzer promptSafetyIncidentAnalyzer;

    public AiQualityOverviewService(AssignmentRepository assignmentRepository,
                                    SubmissionRepository submissionRepository,
                                    SubmissionAnalysisRepository submissionAnalysisRepository,
                                    TeacherDiagnosisCorrectionRepository teacherDiagnosisCorrectionRepository,
                                    DiagnosisReportReader diagnosisReportReader,
                                    DiagnosisTaxonomy diagnosisTaxonomy) {
        this(assignmentRepository, submissionRepository, submissionAnalysisRepository, teacherDiagnosisCorrectionRepository,
                diagnosisReportReader, diagnosisTaxonomy,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public AiQualityOverviewService(AssignmentRepository assignmentRepository,
                                    SubmissionRepository submissionRepository,
                                    SubmissionAnalysisRepository submissionAnalysisRepository,
                                    TeacherDiagnosisCorrectionRepository teacherDiagnosisCorrectionRepository,
                                    DiagnosisReportReader diagnosisReportReader,
                                    DiagnosisTaxonomy diagnosisTaxonomy,
                                    CoachInteractionAnalyzer coachInteractionAnalyzer) {
        this(assignmentRepository, submissionRepository, submissionAnalysisRepository, teacherDiagnosisCorrectionRepository,
                diagnosisReportReader, diagnosisTaxonomy,
                coachInteractionAnalyzer,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public AiQualityOverviewService(AssignmentRepository assignmentRepository,
                                    SubmissionRepository submissionRepository,
                                    SubmissionAnalysisRepository submissionAnalysisRepository,
                                    TeacherDiagnosisCorrectionRepository teacherDiagnosisCorrectionRepository,
                                    DiagnosisReportReader diagnosisReportReader,
                                    DiagnosisTaxonomy diagnosisTaxonomy,
                                    CoachInteractionAnalyzer coachInteractionAnalyzer,
                                    RecommendationEffectivenessService recommendationEffectivenessService) {
        this(assignmentRepository, submissionRepository, submissionAnalysisRepository, teacherDiagnosisCorrectionRepository,
                diagnosisReportReader, diagnosisTaxonomy,
                coachInteractionAnalyzer,
                null,
                recommendationEffectivenessService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Autowired
    public AiQualityOverviewService(AssignmentRepository assignmentRepository,
                                    SubmissionRepository submissionRepository,
                                    SubmissionAnalysisRepository submissionAnalysisRepository,
                                    TeacherDiagnosisCorrectionRepository teacherDiagnosisCorrectionRepository,
                                    DiagnosisReportReader diagnosisReportReader,
                                    DiagnosisTaxonomy diagnosisTaxonomy,
                                    CoachInteractionAnalyzer coachInteractionAnalyzer,
                                    CoachImpactAnalyzer coachImpactAnalyzer,
                                    RecommendationEffectivenessService recommendationEffectivenessService,
                                    ClassReviewFeedbackService classReviewFeedbackService,
                                    TeacherInterventionImpactAnalyzer teacherInterventionImpactAnalyzer,
                                    PostAcTransferAnalyzer postAcTransferAnalyzer,
                                    RecurringMisconceptionAnalyzer recurringMisconceptionAnalyzer,
                                    SelfExplanationMasteryAnalyzer selfExplanationMasteryAnalyzer,
                                    StudentRecommendationEventRepository recommendationEventRepository,
                                    AiDependencyAnalyzer aiDependencyAnalyzer,
                                    MasteryGrowthAnalyzer masteryGrowthAnalyzer,
                                    TeachingActionOrchestrator teachingActionOrchestrator,
                                    ClassTeachingStrategyAnalyzer classTeachingStrategyAnalyzer,
                                    ClassTeachingStrategyImpactAnalyzer classTeachingStrategyImpactAnalyzer,
                                    HintSafetyCheckRepository hintSafetyCheckRepository,
                                    PromptSafetyIncidentAnalyzer promptSafetyIncidentAnalyzer) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.submissionAnalysisRepository = submissionAnalysisRepository;
        this.teacherDiagnosisCorrectionRepository = teacherDiagnosisCorrectionRepository;
        this.diagnosisReportReader = diagnosisReportReader;
        this.diagnosisTaxonomy = diagnosisTaxonomy;
        this.coachInteractionAnalyzer = coachInteractionAnalyzer;
        this.coachImpactAnalyzer = coachImpactAnalyzer;
        this.recommendationEffectivenessService = recommendationEffectivenessService;
        this.classReviewFeedbackService = classReviewFeedbackService;
        this.teacherInterventionImpactAnalyzer = teacherInterventionImpactAnalyzer;
        this.postAcTransferAnalyzer = postAcTransferAnalyzer;
        this.recurringMisconceptionAnalyzer = recurringMisconceptionAnalyzer;
        this.selfExplanationMasteryAnalyzer = selfExplanationMasteryAnalyzer;
        this.recommendationEventRepository = recommendationEventRepository;
        this.aiDependencyAnalyzer = aiDependencyAnalyzer;
        this.masteryGrowthAnalyzer = masteryGrowthAnalyzer;
        this.teachingActionOrchestrator = teachingActionOrchestrator;
        this.classTeachingStrategyAnalyzer = classTeachingStrategyAnalyzer;
        this.classTeachingStrategyImpactAnalyzer = classTeachingStrategyImpactAnalyzer;
        this.hintSafetyCheckRepository = hintSafetyCheckRepository;
        this.promptSafetyIncidentAnalyzer = promptSafetyIncidentAnalyzer == null
                ? new PromptSafetyIncidentAnalyzer()
                : promptSafetyIncidentAnalyzer;
    }

    public AiQualityOverviewResponse buildOverview(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new IllegalArgumentException("作业不存在: " + assignmentId);
        }
        List<Submission> submissions = submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId);
        List<Long> submissionIds = submissions.stream()
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .toList();
        List<SubmissionAnalysis> analyses = submissionIds.isEmpty()
                ? List.of()
                : submissionAnalysisRepository.findBySubmissionIdIn(submissionIds);
        List<TeacherDiagnosisCorrection> corrections = teacherDiagnosisCorrectionRepository.findByAssignmentIdOrderByCorrectedAtDesc(assignmentId);
        Map<Long, com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse> coachInteractions =
                coachInteractionAnalyzer == null ? Map.of() : coachInteractionAnalyzer.summarize(submissionIds);
        List<CoachPrompt> coachPrompts = coachInteractionAnalyzer == null
                ? List.of()
                : coachInteractionAnalyzer.findPrompts(submissionIds);
        List<HintSafetyCheck> safetyChecks = loadHintSafetyChecks(submissionIds);
        AiQualityOverviewResponse.PromptSafetyIncidentSignal promptSafetyIncidentSignal =
                promptSafetyIncidentAnalyzer.analyze(analyses, safetyChecks, coachInteractions, diagnosisReportReader);
        Map<Long, CoachImpactResponse> coachImpacts = buildCoachImpacts(submissions, analyses, coachPrompts);
        RecommendationEffectivenessResponse recommendationEffectiveness =
                recommendationEffectivenessService == null ? null : recommendationEffectivenessService.buildOverview(assignmentId);
        List<AssignmentOverviewResponse.TeacherInterventionImpact> teacherInterventionImpacts =
                buildTeacherInterventionImpacts(assignmentId, submissions, analyses);
        TeacherInterventionImpactAnalyzer.Summary teacherInterventionSummary =
                teacherInterventionImpactAnalyzer == null
                        ? TeacherInterventionImpactAnalyzer.Summary.builder().build()
                        : teacherInterventionImpactAnalyzer.summarize(teacherInterventionImpacts);
        List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals =
                buildPostAcTransferSignals(submissions, analyses, coachInteractions);
        List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals =
                buildRecurringMisconceptionSignals(submissions, analyses);
        List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> selfExplanationSignals =
                buildSelfExplanationSignals(submissions, coachPrompts);
        List<StudentAbilityProfileResponse.AiDependencySignal> aiDependencySignals =
                buildAiDependencySignals(submissions, coachPrompts, assignmentId);
        List<StudentAbilityProfileResponse.MasteryGrowthSignal> masteryGrowthSignals =
                buildMasteryGrowthSignals(submissions, analyses);
        List<StudentAbilityProfileResponse.TeachingActionDecision> teachingActionDecisions =
                buildTeachingActionDecisions(
                        submissions,
                        analyses,
                        coachInteractions,
                        coachPrompts,
                        assignmentId
                );
        AssignmentOverviewResponse.ClassTeachingStrategySignal classTeachingStrategySignal =
                buildClassTeachingStrategySignal(assignmentId, submissions, analyses);
        AiQualityMetrics metrics = AiQualityMetrics.from(
                analyses,
                corrections,
                diagnosisReportReader,
                coachInteractions,
                recommendationEffectiveness,
                teacherInterventionSummary,
                postAcTransferSignals,
                recurringMisconceptionSignals,
                selfExplanationSignals,
                aiDependencySignals,
                masteryGrowthSignals,
                teachingActionDecisions,
                classTeachingStrategySignal,
                coachImpacts,
                promptSafetyIncidentSignal);
        AiQualityOverviewResponse.RuntimeAttributionSignal runtimeAttributionSignal =
                buildRuntimeAttributionSignal(metrics, analyses);
        List<AiQualityOverviewResponse.TagCorrectionStat> correctedTags = buildCorrectedTags(corrections);
        List<AiQualityOverviewResponse.QualityDimension> qualityDimensions =
                buildQualityDimensions(metrics, analyses, corrections, coachInteractions, recommendationEffectiveness,
                        teacherInterventionImpacts, postAcTransferSignals, recurringMisconceptionSignals, selfExplanationSignals,
                        aiDependencySignals, masteryGrowthSignals, teachingActionDecisions, classTeachingStrategySignal, coachImpacts,
                        promptSafetyIncidentSignal, runtimeAttributionSignal);
        List<AiQualityOverviewResponse.ImprovementPriority> improvementPriorities = buildImprovementPriorities(qualityDimensions);

        return AiQualityOverviewResponse.builder()
                .assignmentId(assignmentId)
                .analyzedSubmissionCount(metrics.analyzedSubmissionCount())
                .correctionCount(metrics.correctionCount())
                .evalCandidateCount(metrics.evalCandidateCount())
                .lowConfidenceCount(metrics.lowConfidenceCount())
                .highLeakRiskCount(metrics.highLeakRiskCount())
                .modelFallbackCount(metrics.modelFallbackCount())
                .modelPartialCount(metrics.modelPartialCount())
                .modelRuntimeFailureCount(metrics.modelRuntimeFailureCount())
                .modelCompletedCount(metrics.modelCompletedCount())
                .correctionRate(metrics.correctionRate())
                .lowConfidenceRate(metrics.lowConfidenceRate())
                .highLeakRiskRate(metrics.highLeakRiskRate())
                .modelFallbackRate(metrics.modelFallbackRate())
                .modelRuntimeFailureRate(metrics.modelRuntimeFailureRate())
                .summary(buildSummary(metrics))
                .qualityRiskSummary(buildQualityRiskSummary(metrics))
                .promptSafetyIncidentSignal(promptSafetyIncidentSignal)
                .runtimeAttributionSignal(runtimeAttributionSignal)
                .qualityDimensions(qualityDimensions)
                .improvementPriorities(improvementPriorities)
                .evalReadiness(buildEvalReadiness(metrics, correctedTags, teacherInterventionImpacts,
                        classTeachingStrategySignal, runtimeAttributionSignal))
                .correctedTags(correctedTags)
                .build();
    }

    private List<AiQualityOverviewResponse.QualityDimension> buildQualityDimensions(AiQualityMetrics metrics,
                                                                                    List<SubmissionAnalysis> analyses,
                                                                                    List<TeacherDiagnosisCorrection> corrections,
                                                                                    Map<Long, com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse> coachInteractions,
                                                                                    RecommendationEffectivenessResponse recommendationEffectiveness,
                                                                                    List<AssignmentOverviewResponse.TeacherInterventionImpact> teacherInterventionImpacts,
                                                                                    List<StudentTrajectoryResponse.PostAcTransferSignal> postAcTransferSignals,
                                                                                    List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> recurringMisconceptionSignals,
                                                                                    List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> selfExplanationSignals,
                                                                                    List<StudentAbilityProfileResponse.AiDependencySignal> aiDependencySignals,
                                                                                    List<StudentAbilityProfileResponse.MasteryGrowthSignal> masteryGrowthSignals,
                                                                                    List<StudentAbilityProfileResponse.TeachingActionDecision> teachingActionDecisions,
                                                                                    AssignmentOverviewResponse.ClassTeachingStrategySignal classTeachingStrategySignal,
                                                                                    Map<Long, CoachImpactResponse> coachImpacts,
                                                                                    AiQualityOverviewResponse.PromptSafetyIncidentSignal promptSafetyIncidentSignal,
                                                                                    AiQualityOverviewResponse.RuntimeAttributionSignal runtimeAttributionSignal) {
        return List.of(
                dimension(
                        "DIAGNOSIS_CONFIDENCE",
                        "诊断置信",
                        statusByCount(metrics.lowConfidenceCount(), metrics.analyzedSubmissionCount(), 0.25),
                        scoreFromBadRate(metrics.lowConfidenceCount(), metrics.analyzedSubmissionCount()),
                        metrics.lowConfidenceCount() == 0
                                ? "诊断置信度暂无明显告警。"
                                : "存在 " + metrics.lowConfidenceCount() + " 条低置信诊断，需要补充证据或人工抽查。",
                        evidenceRefsForLowConfidence(analyses),
                        metrics.lowConfidenceCount() == 0
                                ? "继续观察低置信样本比例。"
                                : "优先抽查低置信样本的证据包和错因标签。"
                ),
                dimension(
                        "EVIDENCE_GROUNDING",
                        "证据引用",
                        statusByCount(metrics.missingEvidenceRefCount(), metrics.analyzedSubmissionCount(), 0.2),
                        scoreFromBadRate(metrics.missingEvidenceRefCount(), metrics.analyzedSubmissionCount()),
                        metrics.missingEvidenceRefCount() == 0
                                ? "诊断输出均保留了可追踪证据引用。"
                                : "存在 " + metrics.missingEvidenceRefCount() + " 条诊断缺少 evidenceRefs。",
                        evidenceRefsForMissingEvidence(analyses),
                        metrics.missingEvidenceRefCount() == 0
                                ? "继续保持 evidenceRefs 校验。"
                                : "将缺少证据引用的样本加入人工复核或 eval。"
                ),
                dimension(
                        "HINT_SAFETY",
                        "提示安全",
                        statusByCount(metrics.highLeakRiskCount(), metrics.analyzedSubmissionCount(), 0.1),
                        scoreFromBadRate(metrics.highLeakRiskCount(), metrics.analyzedSubmissionCount()),
                        metrics.highLeakRiskCount() == 0
                                ? "学生提示暂无高泄题风险。"
                                : "存在 " + metrics.highLeakRiskCount() + " 条高泄题风险输出。",
                        evidenceRefsForHighLeakRisk(analyses),
                        metrics.highLeakRiskCount() == 0
                                ? "继续执行提示安全降级与记录。"
                                : "先人工复核高风险提示，再开放给学生。"
                ),
                dimension(
                        "PROMPT_SAFETY_INCIDENT_LOOP",
                        "提示安全事件闭环",
                        promptSafetyIncidentStatus(promptSafetyIncidentSignal),
                        promptSafetyIncidentScore(promptSafetyIncidentSignal, metrics.analyzedSubmissionCount()),
                        promptSafetyIncidentSummary(promptSafetyIncidentSignal),
                        promptSafetyIncidentEvidenceRefs(promptSafetyIncidentSignal),
                        promptSafetyIncidentRecommendedAction(promptSafetyIncidentSignal)
                ),
                dimension(
                        "LEARNING_ACTION",
                        "学习动作",
                        learningActionStatus(metrics),
                        learningActionScore(metrics),
                        learningActionSummary(metrics),
                        evidenceRefsForLearningAction(analyses),
                        learningActionRecommendedAction(metrics)
                ),
                dimension(
                        "MODEL_RUNTIME",
                        "模型运行",
                        runtimeAttributionStatus(runtimeAttributionSignal),
                        runtimeAttributionScore(runtimeAttributionSignal, metrics),
                        runtimeAttributionSummary(runtimeAttributionSignal),
                        runtimeAttributionEvidenceRefs(runtimeAttributionSignal, analyses),
                        runtimeAttributionRecommendedAction(runtimeAttributionSignal)
                ),
                dimension(
                        "TEACHER_CORRECTION",
                        "教师纠错沉淀",
                        teacherCorrectionStatus(metrics),
                        teacherCorrectionScore(metrics),
                        metrics.correctionCount() == 0
                                ? "还没有教师纠错样本，暂不能判断真实课堂误判模式。"
                                : "已有 " + metrics.correctionCount() + " 条教师纠错，其中 "
                                + metrics.evalCandidateCount() + " 条可进入 eval 候选。",
                        evidenceRefsForCorrections(corrections),
                        metrics.evalCandidateCount() > 0
                                ? "优先把 eval candidate 沉淀为回归 fixture。"
                                : "请将高价值教师纠错标记为 eval candidate。"
                ),
                dimension(
                        "TEACHER_CALIBRATION_LOOP",
                        "教师校准闭环",
                        teacherCalibrationLoopStatus(metrics),
                        teacherCalibrationLoopScore(metrics),
                        teacherCalibrationLoopSummary(metrics),
                        evidenceRefsForTeacherCalibration(analyses),
                        teacherCalibrationLoopRecommendedAction(metrics)
                ),
                dimension(
                        "COACH_UNDERSTANDING",
                        "Coach 理解证据",
                        coachUnderstandingStatus(metrics),
                        coachUnderstandingScore(metrics),
                        coachUnderstandingSummary(metrics),
                        evidenceRefsForCoachUnderstanding(coachInteractions),
                        coachUnderstandingRecommendedAction(metrics)
                ),
                dimension(
                        "COACH_FOLLOWUP_IMPACT_LOOP",
                        "Coach 后续成效闭环",
                        coachFollowupImpactLoopStatus(metrics),
                        coachFollowupImpactLoopScore(metrics),
                        coachFollowupImpactLoopSummary(metrics),
                        evidenceRefsForCoachFollowupImpactLoop(coachImpacts),
                        coachFollowupImpactLoopRecommendedAction(metrics)
                ),
                dimension(
                        "RECOMMENDATION_LOOP",
                        "推荐学习闭环",
                        recommendationLoopStatus(metrics),
                        recommendationLoopScore(metrics),
                        recommendationLoopSummary(metrics),
                        evidenceRefsForRecommendationLoop(recommendationEffectiveness),
                        recommendationLoopRecommendedAction(metrics)
                ),
                dimension(
                        "POST_AC_TRANSFER_LOOP",
                        "AC 后迁移闭环",
                        postAcTransferLoopStatus(metrics),
                        postAcTransferLoopScore(metrics),
                        postAcTransferLoopSummary(metrics),
                        evidenceRefsForPostAcTransferLoop(postAcTransferSignals),
                        postAcTransferLoopRecommendedAction(metrics)
                ),
                dimension(
                        "RECURRING_MISCONCEPTION_LOOP",
                        "复发误区闭环",
                        recurringMisconceptionLoopStatus(metrics),
                        recurringMisconceptionLoopScore(metrics),
                        recurringMisconceptionLoopSummary(metrics),
                        evidenceRefsForRecurringMisconceptionLoop(recurringMisconceptionSignals),
                        recurringMisconceptionLoopRecommendedAction(metrics)
                ),
                dimension(
                        "SELF_EXPLANATION_MASTERY_LOOP",
                        "自解释能力闭环",
                        selfExplanationLoopStatus(metrics),
                        selfExplanationLoopScore(metrics),
                        selfExplanationLoopSummary(metrics),
                        evidenceRefsForSelfExplanationLoop(selfExplanationSignals),
                        selfExplanationLoopRecommendedAction(metrics)
                ),
                dimension(
                        "AI_DEPENDENCY_INDEPENDENCE_LOOP",
                        "AI 支架自主性闭环",
                        aiDependencyLoopStatus(metrics),
                        aiDependencyLoopScore(metrics),
                        aiDependencyLoopSummary(metrics),
                        evidenceRefsForAiDependencyLoop(aiDependencySignals),
                        aiDependencyLoopRecommendedAction(metrics)
                ),
                dimension(
                        "MASTERY_GROWTH_LOOP",
                        "长期成长闭环",
                        masteryGrowthLoopStatus(metrics),
                        masteryGrowthLoopScore(metrics),
                        masteryGrowthLoopSummary(metrics),
                        evidenceRefsForMasteryGrowthLoop(masteryGrowthSignals),
                        masteryGrowthLoopRecommendedAction(metrics)
                ),
                dimension(
                        "TEACHING_ACTION_ORCHESTRATION_LOOP",
                        "教学动作编排闭环",
                        teachingActionLoopStatus(metrics),
                        teachingActionLoopScore(metrics),
                        teachingActionLoopSummary(metrics),
                        evidenceRefsForTeachingActionLoop(teachingActionDecisions),
                        teachingActionLoopRecommendedAction(metrics)
                ),
                dimension(
                        "CLASS_TEACHING_STRATEGY_LOOP",
                        "班级教学策略闭环",
                        classTeachingStrategyLoopStatus(metrics),
                        classTeachingStrategyLoopScore(metrics),
                        classTeachingStrategyLoopSummary(metrics),
                        evidenceRefsForClassTeachingStrategyLoop(classTeachingStrategySignal),
                        classTeachingStrategyLoopRecommendedAction(metrics)
                ),
                dimension(
                        "TEACHER_INTERVENTION_LOOP",
                        "教师介入闭环",
                        teacherInterventionLoopStatus(metrics),
                        teacherInterventionLoopScore(metrics),
                        teacherInterventionLoopSummary(metrics),
                        evidenceRefsForTeacherInterventionLoop(teacherInterventionImpacts),
                        teacherInterventionLoopRecommendedAction(metrics)
                )
        );
    }

    private AiQualityOverviewResponse.QualityDimension dimension(String dimension,
                                                                 String label,
                                                                 String status,
                                                                 double score,
                                                                 String summary,
                                                                 List<String> evidenceRefs,
                                                                 String recommendedAction) {
        return AiQualityOverviewResponse.QualityDimension.builder()
                .dimension(dimension)
                .label(label)
                .status(status)
                .score(score)
                .summary(summary)
                .evidenceRefs(evidenceRefs)
                .recommendedAction(recommendedAction)
                .build();
    }

    private List<AiQualityOverviewResponse.ImprovementPriority> buildImprovementPriorities(
            List<AiQualityOverviewResponse.QualityDimension> dimensions) {
        List<AiQualityOverviewResponse.ImprovementPriority> priorities = new ArrayList<>();
        List<AiQualityOverviewResponse.QualityDimension> sorted = dimensions == null ? List.of() : dimensions.stream()
                .filter(dimension -> !"HEALTHY".equals(dimension.getStatus()))
                .sorted(Comparator
                        .comparingInt((AiQualityOverviewResponse.QualityDimension dimension) -> severityRank(dimension.getStatus()))
                        .reversed()
                        .thenComparingInt(dimension -> dimensionPriorityRank(dimension.getDimension()))
                        .thenComparing(AiQualityOverviewResponse.QualityDimension::getScore))
                .toList();
        int index = 1;
        for (AiQualityOverviewResponse.QualityDimension dimension : sorted) {
            priorities.add(AiQualityOverviewResponse.ImprovementPriority.builder()
                    .priority("P" + index++)
                    .dimension(dimension.getDimension())
                    .severity(dimension.getStatus())
                    .reason(dimension.getSummary())
                    .recommendedAction(dimension.getRecommendedAction())
                    .evidenceRefs(dimension.getEvidenceRefs())
                    .build());
        }
        return priorities;
    }

    private AiQualityOverviewResponse.EvalReadiness buildEvalReadiness(
            AiQualityMetrics metrics,
            List<AiQualityOverviewResponse.TagCorrectionStat> correctedTags,
            List<AssignmentOverviewResponse.TeacherInterventionImpact> teacherInterventionImpacts,
            AssignmentOverviewResponse.ClassTeachingStrategySignal classTeachingStrategySignal,
            AiQualityOverviewResponse.RuntimeAttributionSignal runtimeAttributionSignal) {
        List<AiQualityOverviewResponse.TagCorrectionStat> priorityTags = correctedTags == null ? List.of() : correctedTags.stream()
                .limit(3)
                .toList();
        InterventionEvalReadiness interventionReadiness =
                interventionEvalReadiness(teacherInterventionImpacts, classTeachingStrategySignal);
        ModelQualityBaselineReadiness modelQualityBaselineReadiness =
                modelQualityBaselineReadiness(runtimeAttributionSignal);
        String status;
        String summary;
        String recommendedAction;
        if (metrics.evalCandidateCount() > 0 && interventionReadiness.candidateCount() > 0) {
            status = "READY";
            summary = "已有 " + metrics.evalCandidateCount() + " 条教师纠错 eval candidate 和 "
                    + interventionReadiness.candidateCount() + " 条课堂介入成效候选，可进入回归评测沉淀。";
            recommendedAction = "同时沉淀诊断 fixture 和课堂介入 fixture，覆盖错因判断与教学建议成效。";
        } else if (metrics.evalCandidateCount() > 0) {
            status = "READY";
            summary = "已有 " + metrics.evalCandidateCount() + " 条教师纠错被标记为 eval candidate，可进入回归评测沉淀。";
            recommendedAction = "优先沉淀教师校正诊断 fixture，并继续积累课堂介入成效样本。";
        } else if (interventionReadiness.candidateCount() > 0) {
            status = "READY";
            summary = "已有 " + interventionReadiness.candidateCount() + " 条课堂介入或班级策略成效候选，可沉淀教学建议回归评测。";
            recommendedAction = "优先沉淀课堂介入 fixture，覆盖改善、错因转移和仍卡同类问题。";
        } else if (interventionReadiness.waitingFollowupCount() > 0) {
            status = "PARTIAL";
            summary = "已有课堂介入反馈，但 " + interventionReadiness.waitingFollowupCount() + " 条还在等待后续提交证据。";
            recommendedAction = "等待后续提交或补充课堂观察后，再把介入成效沉淀为 eval。";
        } else if (metrics.correctionCount() > 0 || metrics.highLeakRiskCount() > 0 || metrics.lowConfidenceCount() > 0) {
            status = "PARTIAL";
            summary = "已有风险或纠错样本，但还缺少明确 eval candidate，需要老师筛选沉淀。";
            recommendedAction = "请教师筛选高风险或纠错样本，标记为 eval candidate。";
        } else if (metrics.analyzedSubmissionCount() == 0) {
            status = "NO_SAMPLE";
            summary = "当前作业还没有 AI 诊断样本，暂不能沉淀评测。";
            recommendedAction = "等待学生提交和诊断完成后，再筛选可回归样本。";
        } else {
            status = "INSUFFICIENT_SIGNAL";
            summary = "当前样本暂无明显风险信号，继续收集教师纠错和低置信样本。";
            recommendedAction = "继续观察教师校正、课堂介入反馈和后续提交成效。";
        }
        List<String> evidenceRefs = new ArrayList<>();
        evidenceRefs.add("teacher_corrections:" + metrics.correctionCount());
        evidenceRefs.add("eval_candidates:" + metrics.evalCandidateCount());
        evidenceRefs.add("risk_samples:" + (metrics.highLeakRiskCount() + metrics.lowConfidenceCount()));
        evidenceRefs.addAll(interventionReadiness.evidenceRefs());
        evidenceRefs.add("model_quality_baseline:" + modelQualityBaselineReadiness.status());
        return AiQualityOverviewResponse.EvalReadiness.builder()
                .status(status)
                .summary(summary)
                .candidateCount(metrics.evalCandidateCount())
                .correctionCount(metrics.correctionCount())
                .interventionCandidateCount(interventionReadiness.candidateCount())
                .interventionWaitingFollowupCount(interventionReadiness.waitingFollowupCount())
                .interventionImprovedCount(interventionReadiness.improvedCount())
                .interventionShiftedCount(interventionReadiness.shiftedCount())
                .interventionStillStuckCount(interventionReadiness.stillStuckCount())
                .modelQualityBaselineStatus(modelQualityBaselineReadiness.status())
                .modelQualityBaselineSummary(modelQualityBaselineReadiness.summary())
                .modelQualityBaselineReasonCount(modelQualityBaselineReadiness.reasons().size())
                .modelQualityBaselineReasons(modelQualityBaselineReadiness.reasons())
                .recommendedAction(recommendedAction)
                .priorityTags(priorityTags)
                .evidenceRefs(evidenceRefs.stream().filter(ref -> ref != null && !ref.isBlank()).distinct().limit(8).toList())
                .build();
    }

    private ModelQualityBaselineReadiness modelQualityBaselineReadiness(
            AiQualityOverviewResponse.RuntimeAttributionSignal runtimeAttributionSignal) {
        if (runtimeAttributionSignal == null
                || runtimeAttributionSignal.getQualityComparabilityStatus() == null
                || runtimeAttributionSignal.getQualityComparabilityStatus().isBlank()) {
            return new ModelQualityBaselineReadiness(
                    "NOT_APPLICABLE",
                    "当前作业没有需要沉淀为外部模型质量 baseline 的运行上下文。",
                    List.of()
            );
        }
        List<String> reasons = runtimeAttributionSignal.getQualityComparabilityReasons() == null
                ? List.of()
                : runtimeAttributionSignal.getQualityComparabilityReasons().stream()
                .filter(reason -> reason != null && !reason.isBlank())
                .distinct()
                .limit(8)
                .toList();
        return switch (runtimeAttributionSignal.getQualityComparabilityStatus().toUpperCase(Locale.ROOT)) {
            case "COMPARABLE" -> new ModelQualityBaselineReadiness(
                    "READY",
                    "当前已有可比的真实外部模型完成样本，可沉淀为小批量模型质量 baseline。",
                    reasons
            );
            case "PARTIAL" -> new ModelQualityBaselineReadiness(
                    "PARTIAL",
                    "当前只有部分真实外部模型质量证据，适合先观察或沉淀 runtime fixture，暂不宜作为完整质量 baseline。",
                    reasons
            );
            case "NOT_COMPARABLE" -> new ModelQualityBaselineReadiness(
                    "BLOCKED",
                    "当前样本不可代表真实外部模型质量；先恢复 provider、减少 fallback 或补充无 fallback 模型命中后再沉淀质量 baseline。",
                    reasons
            );
            default -> new ModelQualityBaselineReadiness(
                    "NOT_APPLICABLE",
                    "当前作业没有需要沉淀为外部模型质量 baseline 的运行上下文。",
                    List.of()
            );
        };
    }

    private InterventionEvalReadiness interventionEvalReadiness(
            List<AssignmentOverviewResponse.TeacherInterventionImpact> teacherInterventionImpacts,
            AssignmentOverviewResponse.ClassTeachingStrategySignal classTeachingStrategySignal) {
        long waiting = 0;
        long improved = 0;
        long shifted = 0;
        long stillStuck = 0;
        List<String> refs = new ArrayList<>();
        for (AssignmentOverviewResponse.TeacherInterventionImpact impact : teacherInterventionImpacts == null
                ? List.<AssignmentOverviewResponse.TeacherInterventionImpact>of()
                : teacherInterventionImpacts) {
            if (impact == null) {
                continue;
            }
            String status = impact.getStatus();
            if (TeacherInterventionImpactAnalyzer.STATUS_WAITING_FOLLOWUP.equals(status)) {
                waiting++;
            } else if (TeacherInterventionImpactAnalyzer.STATUS_IMPROVED.equals(status)) {
                improved++;
            } else if (TeacherInterventionImpactAnalyzer.STATUS_SHIFTED.equals(status)) {
                shifted++;
            } else if (TeacherInterventionImpactAnalyzer.STATUS_STILL_STUCK.equals(status)) {
                stillStuck++;
            }
            if (isInterventionEvalCandidate(status)) {
                refs.add("teacher_intervention_eval:" + status);
                if (impact.getFollowupSubmissionId() != null) {
                    refs.add("followup_submission:" + impact.getFollowupSubmissionId());
                }
            }
        }
        AssignmentOverviewResponse.ClassTeachingStrategyImpact strategyImpact =
                classTeachingStrategySignal == null ? null : classTeachingStrategySignal.getImpact();
        if (strategyImpact != null) {
            String status = strategyImpact.getStatus();
            if (ClassTeachingStrategyImpactAnalyzer.STATUS_WAITING_FOLLOWUP.equals(status)) {
                waiting++;
            } else if (ClassTeachingStrategyImpactAnalyzer.STATUS_IMPROVED.equals(status)) {
                improved++;
            } else if (ClassTeachingStrategyImpactAnalyzer.STATUS_SHIFTED.equals(status)) {
                shifted++;
            } else if (ClassTeachingStrategyImpactAnalyzer.STATUS_STILL_STUCK.equals(status)) {
                stillStuck++;
            }
            if (isInterventionEvalCandidate(status)) {
                refs.add("class_strategy_eval:" + status);
                if (classTeachingStrategySignal.getStrategyKey() != null) {
                    refs.add("class_strategy:" + classTeachingStrategySignal.getStrategyKey());
                }
                if (strategyImpact.getFollowupSubmissionId() != null) {
                    refs.add("followup_submission:" + strategyImpact.getFollowupSubmissionId());
                }
            }
        }
        return new InterventionEvalReadiness(
                improved + shifted + stillStuck,
                waiting,
                improved,
                shifted,
                stillStuck,
                refs.stream().distinct().limit(6).toList()
        );
    }

    private boolean isInterventionEvalCandidate(String status) {
        return TeacherInterventionImpactAnalyzer.STATUS_IMPROVED.equals(status)
                || TeacherInterventionImpactAnalyzer.STATUS_SHIFTED.equals(status)
                || TeacherInterventionImpactAnalyzer.STATUS_STILL_STUCK.equals(status)
                || ClassTeachingStrategyImpactAnalyzer.STATUS_IMPROVED.equals(status)
                || ClassTeachingStrategyImpactAnalyzer.STATUS_SHIFTED.equals(status)
                || ClassTeachingStrategyImpactAnalyzer.STATUS_STILL_STUCK.equals(status);
    }

    private record InterventionEvalReadiness(long candidateCount,
                                             long waitingFollowupCount,
                                             long improvedCount,
                                             long shiftedCount,
                                             long stillStuckCount,
                                             List<String> evidenceRefs) {
    }

    private record ModelQualityBaselineReadiness(String status,
                                                 String summary,
                                                 List<String> reasons) {
        private ModelQualityBaselineReadiness {
            status = status == null || status.isBlank() ? "NOT_APPLICABLE" : status;
            summary = summary == null ? "" : summary;
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    private static class RuntimeAttributionAccumulator {
        private final String type;
        private long count;
        private String failureReason = "";
        private String failureStage = "";
        private String transportMode = "";
        private String streamFinishReason = "";
        private long streamNoContentCount;
        private long streamInvalidChunkCount;
        private long streamFallbackRetryCount;
        private final List<String> evidenceRefs = new ArrayList<>();

        private RuntimeAttributionAccumulator(String type) {
            this.type = type;
        }

        private long count() {
            return count;
        }
    }

    private static class RecoveryStatusSummary {
        private final String status;
        private final List<String> requiredChecks;
        private final List<String> passedChecks;
        private final List<String> blockedReasons;
        private final boolean smokeRecommended;
        private final String smokeCaseId;
        private final String runtimeProfile;

        private RecoveryStatusSummary(String status,
                                      List<String> requiredChecks,
                                      List<String> passedChecks,
                                      List<String> blockedReasons,
                                      boolean smokeRecommended,
                                      String smokeCaseId,
                                      String runtimeProfile) {
            this.status = status;
            this.requiredChecks = requiredChecks == null ? List.of() : List.copyOf(requiredChecks);
            this.passedChecks = passedChecks == null ? List.of() : List.copyOf(passedChecks);
            this.blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
            this.smokeRecommended = smokeRecommended;
            this.smokeCaseId = smokeCaseId == null ? "" : smokeCaseId;
            this.runtimeProfile = runtimeProfile == null ? "" : runtimeProfile;
        }

        private static RecoveryStatusSummary notApplicable() {
            return new RecoveryStatusSummary("NOT_APPLICABLE", List.of(), List.of(), List.of(), false, "", "");
        }

        private static RecoveryStatusSummary recovered(List<String> requiredChecks, String evidenceRef) {
            List<String> checks = requiredChecks == null ? List.of() : requiredChecks;
            List<String> passed = new ArrayList<>(checks);
            if (evidenceRef != null && !evidenceRef.isBlank()) {
                passed.add("recoveryEvidenceRef=" + evidenceRef);
            }
            return new RecoveryStatusSummary("RECOVERED", checks, passed, List.of(), false, "", "");
        }

        private static RecoveryStatusSummary blocked(List<String> requiredChecks,
                                                     List<String> blockedReasons,
                                                     String smokeCaseId,
                                                     String runtimeProfile) {
            return new RecoveryStatusSummary("BLOCKED", requiredChecks, List.of(), blockedReasons, true,
                    smokeCaseId, runtimeProfile);
        }
    }

    private record QualityComparabilitySummary(String status,
                                               String summary,
                                               List<String> reasons) {
        private QualityComparabilitySummary {
            status = status == null || status.isBlank() ? "NOT_APPLICABLE" : status;
            summary = summary == null ? "" : summary;
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    private List<HintSafetyCheck> loadHintSafetyChecks(List<Long> submissionIds) {
        if (hintSafetyCheckRepository == null || submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }
        return hintSafetyCheckRepository.findBySubmissionIdIn(submissionIds);
    }

    private List<AiQualityOverviewResponse.TagCorrectionStat> buildCorrectedTags(List<TeacherDiagnosisCorrection> corrections) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, TeacherDiagnosisCorrection> sampleByKey = new LinkedHashMap<>();
        for (TeacherDiagnosisCorrection correction : corrections) {
            String original = firstNonBlank(correction.getOriginalFineGrainedTag(), correction.getOriginalIssueTag(), "UNKNOWN");
            String corrected = firstNonBlank(correction.getCorrectedFineGrainedTag(), correction.getCorrectedIssueTag(), "UNKNOWN");
            String key = original + "->" + corrected;
            counts.put(key, counts.getOrDefault(key, 0L) + 1L);
            sampleByKey.putIfAbsent(key, correction);
        }
        return counts.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    TeacherDiagnosisCorrection sample = sampleByKey.get(entry.getKey());
                    String original = firstNonBlank(sample.getOriginalFineGrainedTag(), sample.getOriginalIssueTag(), "UNKNOWN");
                    String corrected = firstNonBlank(sample.getCorrectedFineGrainedTag(), sample.getCorrectedIssueTag(), "UNKNOWN");
                    return AiQualityOverviewResponse.TagCorrectionStat.builder()
                            .originalTag(original)
                            .originalLabel(diagnosisTaxonomy.label(original))
                            .correctedTag(corrected)
                            .correctedLabel(diagnosisTaxonomy.label(corrected))
                            .count(entry.getValue())
                            .build();
                })
                .toList();
    }

    private String buildSummary(AiQualityMetrics metrics) {
        if (metrics.analyzedSubmissionCount() == 0) {
            return "当前作业还没有 AI 诊断样本。";
        }
        if (metrics.modelRuntimeFailureCount() > 0) {
            return "存在外部模型兜底或运行失败，建议先检查模型调用与 prompt 输出稳定性。";
        }
        if (metrics.modelPartialCount() > 0) {
            return "存在外部模型部分完成样本，建议复核第二阶段教学提示是否稳定。";
        }
        if (metrics.highLeakRiskCount() > 0) {
            return "存在高泄题风险诊断，建议先复核这些提交再开放给学生。";
        }
        if (metrics.promptSafetyIncidentCount() > 0) {
            return "存在提示安全事件，建议复核安全降级或 Coach 对话证据。";
        }
        if (metrics.correctionCount() > 0) {
            return "已有教师校正样例，建议优先沉淀进模型 eval。";
        }
        if (metrics.lowConfidenceCount() > 0) {
            return "存在低置信度诊断，适合人工抽查补证据。";
        }
        return "当前 AI 诊断暂无明显质量告警，继续观察教师校正和低置信度样本。";
    }

    private String buildQualityRiskSummary(AiQualityMetrics metrics) {
        if (metrics.analyzedSubmissionCount() == 0) {
            return "暂无可分析的模型质量风险。";
        }
        if (metrics.modelRuntimeFailureCount() > 0) {
            return "模型调用质量风险最高：存在兜底或运行失败，在线效果可能没有真正使用外部大模型。";
        }
        if (metrics.modelPartialCount() > 0) {
            return "模型阶段质量风险较高：诊断可能完成，但教学提示阶段存在部分失败。";
        }
        if (metrics.highLeakRiskCount() > 0) {
            return "教学安全风险较高：存在高泄题风险输出，需要优先人工复核。";
        }
        if (metrics.promptSafetyIncidentCount() > 0) {
            return "提示安全闭环存在观察信号：已有安全降级或 Coach 安全风险，应沉淀为安全评测样本。";
        }
        if (metrics.correctionCount() > 0) {
            return "教学准确性风险可沉淀：教师校正样例应进入评测集和标准库迭代。";
        }
        if (metrics.lowConfidenceCount() > 0) {
            return "证据充分性风险存在：低置信度样本适合作为人工抽检队列。";
        }
        return "模型调用和教学输出暂未出现明显质量风险。";
    }

    private String statusByCount(long badCount, long totalCount, double actionThreshold) {
        if (totalCount <= 0 || badCount <= 0) {
            return "HEALTHY";
        }
        double rate = badCount / (double) totalCount;
        if (rate >= actionThreshold) {
            return "ACTION_NEEDED";
        }
        return "WATCH";
    }

    private double scoreFromBadRate(long badCount, long totalCount) {
        if (totalCount <= 0) {
            return 100.0;
        }
        double score = 100.0 - (badCount * 100.0 / totalCount);
        return Math.round(score * 10.0) / 10.0;
    }

    private String promptSafetyIncidentStatus(AiQualityOverviewResponse.PromptSafetyIncidentSignal signal) {
        if (signal == null || signal.getStatus() == null || signal.getStatus().isBlank()) {
            return "HEALTHY";
        }
        return signal.getStatus();
    }

    private double promptSafetyIncidentScore(AiQualityOverviewResponse.PromptSafetyIncidentSignal signal,
                                             long analyzedSubmissionCount) {
        if (signal == null || analyzedSubmissionCount <= 0) {
            return 100.0;
        }
        double weightedBad = signal.getHighLeakRiskCount()
                + signal.getHighRiskSafetyDowngradeCount()
                + signal.getCoachSafetyRiskCount()
                + Math.max(0, signal.getSafetyDowngradeCount() - signal.getHighRiskSafetyDowngradeCount()) * 0.5;
        double score = 100.0 - weightedBad * 100.0 / analyzedSubmissionCount;
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String promptSafetyIncidentSummary(AiQualityOverviewResponse.PromptSafetyIncidentSignal signal) {
        if (signal == null || signal.getSummary() == null || signal.getSummary().isBlank()) {
            return "暂未观察到提示安全事件，继续保持安全降级和证据引用校验。";
        }
        return signal.getSummary();
    }

    private List<String> promptSafetyIncidentEvidenceRefs(AiQualityOverviewResponse.PromptSafetyIncidentSignal signal) {
        return signal == null || signal.getEvidenceRefs() == null ? List.of() : signal.getEvidenceRefs();
    }

    private String promptSafetyIncidentRecommendedAction(AiQualityOverviewResponse.PromptSafetyIncidentSignal signal) {
        if (signal == null || signal.getRecommendedAction() == null || signal.getRecommendedAction().isBlank()) {
            return "继续观察提示安全事件，并保留安全降级记录作为后续评测样本。";
        }
        return signal.getRecommendedAction();
    }

    private AiQualityOverviewResponse.RuntimeAttributionSignal buildRuntimeAttributionSignal(
            AiQualityMetrics metrics,
            List<SubmissionAnalysis> analyses) {
        if (metrics.analyzedSubmissionCount() == 0) {
            return null;
        }
        Map<String, RuntimeAttributionAccumulator> accumulators = new LinkedHashMap<>();
        List<SubmissionAnalysis> safeAnalyses = analyses == null ? List.of() : analyses;
        for (SubmissionAnalysis analysis : safeAnalyses) {
            DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
            if (invocation == null) {
                continue;
            }
            boolean runtimeFailure = isRuntimeFailure(invocation);
            boolean partial = "MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(invocation.status());
            if (!runtimeFailure && !partial) {
                continue;
            }
            String type = runtimeFailure
                    ? runtimeFailureType(invocation)
                    : "PARTIAL_COMPLETION";
            RuntimeAttributionAccumulator accumulator = accumulators.computeIfAbsent(type, RuntimeAttributionAccumulator::new);
            accumulator.count++;
            if (accumulator.failureReason.isBlank()) {
                accumulator.failureReason = firstNonBlank(invocation.failureReason(), invocation.status(), type);
            }
            if (accumulator.failureStage.isBlank()) {
                accumulator.failureStage = firstNonBlank(invocation.failureStage(), invocation.runtimeMode(), "UNKNOWN_STAGE");
            }
            if (accumulator.transportMode.isBlank()) {
                accumulator.transportMode = firstNonBlank(invocation.transportMode(), "");
            }
            if (accumulator.streamFinishReason.isBlank()) {
                accumulator.streamFinishReason = firstNonBlank(invocation.streamFinishReason(), "");
            }
            if (isStreamNoContent(invocation)) {
                accumulator.streamNoContentCount++;
            }
            accumulator.streamInvalidChunkCount += Math.max(0, invocation.streamInvalidChunkCount());
            if (invocation.streamFallbackRetryUsed()) {
                accumulator.streamFallbackRetryCount++;
            }
            List<String> refs = diagnosisReportReader.evidenceRefs(analysis);
            accumulator.evidenceRefs.add(refs.isEmpty() ? "runtime_attribution:submission:" + analysis.getSubmissionId() : refs.get(0));
        }
        RecoveryStatusSummary recovery = buildRecoveryStatusSummary(safeAnalyses, !accumulators.isEmpty());
        QualityComparabilitySummary comparability = buildQualityComparabilitySummary(metrics, recovery);
        if (accumulators.isEmpty()) {
            return AiQualityOverviewResponse.RuntimeAttributionSignal.builder()
                    .status("HEALTHY")
                    .primaryFailureType("NONE")
                    .primaryTransportMode("")
                    .modelCompletedCount(metrics.modelCompletedCount())
                    .modelPartialCount(metrics.modelPartialCount())
                    .modelRuntimeFailureCount(metrics.modelRuntimeFailureCount())
                    .modelFallbackCount(metrics.modelFallbackCount())
                    .runtimeFailureRate(metrics.modelRuntimeFailureRate())
                    .streamNoContentCount(0)
                    .streamInvalidChunkCount(0)
                    .streamFallbackRetryCount(0)
                    .recoveryStatus(recovery.status)
                    .recoveryCheckCount(recovery.requiredChecks.size())
                    .recoveryPassedCheckCount(recovery.passedChecks.size())
                    .recoveryBlockedReasonCount(recovery.blockedReasons.size())
                    .recoveryPassedChecks(recovery.passedChecks)
                    .recoveryBlockedReasons(recovery.blockedReasons)
                    .recoverySmokeRecommended(recovery.smokeRecommended)
                    .recoverySmokeCaseId(recovery.smokeCaseId)
                    .recoverySmokeRuntimeProfile(recovery.runtimeProfile)
                    .recoverySmokeRequiredChecks(recovery.requiredChecks)
                    .qualityComparabilityStatus(comparability.status())
                    .qualityComparabilitySummary(comparability.summary())
                    .qualityComparabilityReasonCount(comparability.reasons().size())
                    .qualityComparabilityReasons(comparability.reasons())
                    .summary("外部模型运行暂无明显失败，继续观察 fallback、partial 和 live eval 趋势。")
                    .recommendedAction("继续保持结构化归因记录，并定期对照 live eval 完成率。")
                    .evidenceRefs(List.of())
                    .build();
        }
        RuntimeAttributionAccumulator primary = accumulators.values().stream()
                .sorted(Comparator
                        .comparingLong(RuntimeAttributionAccumulator::count)
                        .reversed()
                        .thenComparingInt(accumulator -> runtimeFailureTypeRank(accumulator.type)))
                .findFirst()
                .orElseThrow();
        return AiQualityOverviewResponse.RuntimeAttributionSignal.builder()
                .status(metrics.modelRuntimeFailureCount() > 0 ? "ACTION_NEEDED" : "WATCH")
                .primaryFailureType(primary.type)
                .primaryFailureReason(primary.failureReason)
                .primaryFailureStage(primary.failureStage)
                .primaryTransportMode(primary.transportMode)
                .modelCompletedCount(metrics.modelCompletedCount())
                .modelPartialCount(metrics.modelPartialCount())
                .modelRuntimeFailureCount(metrics.modelRuntimeFailureCount())
                .modelFallbackCount(metrics.modelFallbackCount())
                .primaryFailureCount(primary.count)
                .runtimeFailureRate(metrics.modelRuntimeFailureRate())
                .streamNoContentCount(primary.streamNoContentCount)
                .streamInvalidChunkCount(primary.streamInvalidChunkCount)
                .streamFallbackRetryCount(primary.streamFallbackRetryCount)
                .recoveryStatus(recovery.status)
                .recoveryCheckCount(recovery.requiredChecks.size())
                .recoveryPassedCheckCount(recovery.passedChecks.size())
                .recoveryBlockedReasonCount(recovery.blockedReasons.size())
                .recoveryPassedChecks(recovery.passedChecks)
                .recoveryBlockedReasons(recovery.blockedReasons)
                .recoverySmokeRecommended(recovery.smokeRecommended)
                .recoverySmokeCaseId(recovery.smokeCaseId)
                .recoverySmokeRuntimeProfile(recovery.runtimeProfile)
                .recoverySmokeRequiredChecks(recovery.requiredChecks)
                .qualityComparabilityStatus(comparability.status())
                .qualityComparabilitySummary(comparability.summary())
                .qualityComparabilityReasonCount(comparability.reasons().size())
                .qualityComparabilityReasons(comparability.reasons())
                .summary(withRecoverySummary(runtimeAttributionSummary(primary, metrics), recovery))
                .recommendedAction(withRecoveryAction(runtimeAttributionAction(primary), recovery))
                .evidenceRefs(primary.evidenceRefs.stream()
                        .filter(ref -> ref != null && !ref.isBlank())
                        .distinct()
                        .limit(5)
                        .toList())
                .build();
    }

    private String runtimeAttributionStatus(AiQualityOverviewResponse.RuntimeAttributionSignal signal) {
        if (signal == null || signal.getStatus() == null || signal.getStatus().isBlank()) {
            return "HEALTHY";
        }
        return signal.getStatus();
    }

    private double runtimeAttributionScore(AiQualityOverviewResponse.RuntimeAttributionSignal signal,
                                           AiQualityMetrics metrics) {
        if (signal == null) {
            return scoreFromBadRate(metrics.modelRuntimeFailureCount(), metrics.analyzedSubmissionCount());
        }
        if (metrics.analyzedSubmissionCount() <= 0) {
            return 100.0;
        }
        double weightedBad = metrics.modelRuntimeFailureCount() + Math.max(0, metrics.modelPartialCount()) * 0.5;
        double score = 100.0 - weightedBad * 100.0 / metrics.analyzedSubmissionCount();
        return Math.round(Math.max(0.0, Math.min(100.0, score)) * 10.0) / 10.0;
    }

    private String runtimeAttributionSummary(AiQualityOverviewResponse.RuntimeAttributionSignal signal) {
        if (signal == null || signal.getSummary() == null || signal.getSummary().isBlank()) {
            return "外部模型运行暂无明显失败。";
        }
        return signal.getSummary();
    }

    private List<String> runtimeAttributionEvidenceRefs(AiQualityOverviewResponse.RuntimeAttributionSignal signal,
                                                        List<SubmissionAnalysis> analyses) {
        if (signal == null || signal.getEvidenceRefs() == null || signal.getEvidenceRefs().isEmpty()) {
            return evidenceRefsForRuntime(analyses);
        }
        return signal.getEvidenceRefs();
    }

    private String runtimeAttributionRecommendedAction(AiQualityOverviewResponse.RuntimeAttributionSignal signal) {
        if (signal == null || signal.getRecommendedAction() == null || signal.getRecommendedAction().isBlank()) {
            return "继续观察 fallback 和 partial 比例。";
        }
        return signal.getRecommendedAction();
    }

    private QualityComparabilitySummary buildQualityComparabilitySummary(AiQualityMetrics metrics,
                                                                         RecoveryStatusSummary recovery) {
        List<String> reasons = new ArrayList<>();
        String recoveryStatus = recovery == null ? "NOT_APPLICABLE" : recovery.status;
        if ("BLOCKED".equals(recoveryStatus)) {
            reasons.add("current recovery blocked");
            if (recovery != null) {
                reasons.addAll(recovery.blockedReasons.stream()
                        .filter(reason -> reason != null && !reason.isBlank())
                        .limit(4)
                        .toList());
            }
        }
        if (metrics.modelCompletedCount() <= 0 && metrics.modelFallbackCount() > 0) {
            reasons.add("model hits missing; fallback hits present");
        }
        if (metrics.modelPartialCount() > 0) {
            reasons.add("partial model outputs present");
        }
        if (!"RECOVERED".equals(recoveryStatus) && metrics.modelRuntimeFailureCount() > 0) {
            reasons.add("runtime failures still present");
        }
        List<String> distinctReasons = reasons.stream()
                .filter(reason -> reason != null && !reason.isBlank())
                .distinct()
                .limit(8)
                .toList();
        if ("BLOCKED".equals(recoveryStatus) || (metrics.modelCompletedCount() <= 0 && metrics.modelFallbackCount() > 0)) {
            return new QualityComparabilitySummary(
                    "NOT_COMPARABLE",
                    "当前样本不能代表真实外部模型质量；需要先恢复 provider 或确认无 fallback 的模型命中后再做质量对比。",
                    distinctReasons
            );
        }
        if (metrics.modelPartialCount() > 0) {
            return new QualityComparabilitySummary(
                    "PARTIAL",
                    "当前已有部分真实模型证据，但仍混有 partial 或运行失败样本，只适合做小范围质量观察。",
                    distinctReasons
            );
        }
        if ("RECOVERED".equals(recoveryStatus) && metrics.modelCompletedCount() > 0) {
            return new QualityComparabilitySummary(
                    "COMPARABLE",
                    "当前已有通过恢复检查的真实外部模型完成样本，可支持小批量模型质量对比。",
                    List.of()
            );
        }
        if (metrics.modelRuntimeFailureCount() > 0) {
            return new QualityComparabilitySummary(
                    "PARTIAL",
                    "当前已有外部模型运行证据，但仍混有运行失败样本，只适合做小范围质量观察。",
                    distinctReasons
            );
        }
        return new QualityComparabilitySummary(
                "NOT_APPLICABLE",
                "当前作业没有需要解释的外部模型质量对比上下文。",
                List.of()
        );
    }

    private RecoveryStatusSummary buildRecoveryStatusSummary(List<SubmissionAnalysis> analyses,
                                                             boolean hasRuntimeRecoveryContext) {
        List<SubmissionAnalysis> safeAnalyses = analyses == null ? List.of() : analyses;
        List<SubmissionAnalysis> contextAnalyses = safeAnalyses.stream()
                .filter(this::isRecoveryContextAnalysis)
                .toList();
        if (!hasRuntimeRecoveryContext && contextAnalyses.isEmpty()) {
            return RecoveryStatusSummary.notApplicable();
        }
        boolean streamRequired = contextAnalyses.stream()
                .map(diagnosisReportReader::aiInvocation)
                .anyMatch(invocation -> invocation != null && "stream".equalsIgnoreCase(invocation.transportMode()));
        List<String> requiredChecks = recoveryRequiredChecks(streamRequired);
        for (SubmissionAnalysis analysis : safeAnalyses) {
            if (isRecoveredModelSample(analysis, streamRequired)) {
                return RecoveryStatusSummary.recovered(requiredChecks, recoveryEvidenceRef(analysis));
            }
        }
        SubmissionAnalysis smokeSource = contextAnalyses.isEmpty() ? null : contextAnalyses.get(0);
        DiagnosisReportReader.AiInvocationSnapshot smokeInvocation =
                smokeSource == null ? null : diagnosisReportReader.aiInvocation(smokeSource);
        List<String> blockedReasons = new ArrayList<>();
        if (smokeSource != null) {
            blockedReasons.add("recovery smoke pending: submission:" + smokeSource.getSubmissionId());
        }
        for (SubmissionAnalysis analysis : contextAnalyses.isEmpty() ? safeAnalyses : contextAnalyses) {
            blockedReasons.addAll(recoveryBlockedReasons(analysis, streamRequired));
        }
        return RecoveryStatusSummary.blocked(
                requiredChecks,
                blockedReasons.stream()
                        .filter(reason -> reason != null && !reason.isBlank())
                        .distinct()
                        .limit(8)
                        .toList(),
                smokeSource == null ? "" : "submission:" + smokeSource.getSubmissionId(),
                firstNonBlank(smokeInvocation == null ? "" : smokeInvocation.runtimeMode(), "standard")
        );
    }

    private boolean isRecoveryContextAnalysis(SubmissionAnalysis analysis) {
        DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
        if (invocation == null) {
            return false;
        }
        return isRuntimeFailure(invocation)
                || "MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(invocation.status())
                || invocation.fallbackUsed();
    }

    private boolean isRecoveredModelSample(SubmissionAnalysis analysis, boolean streamRequired) {
        DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
        if (invocation == null || !"MODEL_COMPLETED".equalsIgnoreCase(invocation.status()) || invocation.fallbackUsed()) {
            return false;
        }
        if (!hasModelHit(analysis) || diagnosisReportReader.evidenceRefs(analysis).isEmpty()) {
            return false;
        }
        if ("HIGH".equalsIgnoreCase(diagnosisReportReader.answerLeakRisk(analysis))) {
            return false;
        }
        if (!streamRequired) {
            return true;
        }
        return "stream".equalsIgnoreCase(invocation.transportMode())
                && invocation.streamContentChunkCount() > 0;
    }

    private boolean hasModelHit(SubmissionAnalysis analysis) {
        return !diagnosisReportReader.fineGrainedTags(analysis).isEmpty()
                || !diagnosisReportReader.issueTags(analysis).isEmpty();
    }

    private List<String> recoveryBlockedReasons(SubmissionAnalysis analysis, boolean streamRequired) {
        DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
        if (invocation == null) {
            return List.of("submission:" + analysis.getSubmissionId() + ": missing aiInvocation");
        }
        List<String> reasons = new ArrayList<>();
        String prefix = "submission:" + analysis.getSubmissionId() + ": ";
        if (isRuntimeFailure(invocation)) {
            reasons.add(prefix + "runtime failure");
        }
        if (!"MODEL_COMPLETED".equalsIgnoreCase(invocation.status())) {
            reasons.add(prefix + "model not completed");
        }
        if (!hasModelHit(analysis)) {
            reasons.add(prefix + "missing model hit");
        }
        if (diagnosisReportReader.evidenceRefs(analysis).isEmpty()) {
            reasons.add(prefix + "missing evidence");
        }
        if ("HIGH".equalsIgnoreCase(diagnosisReportReader.answerLeakRisk(analysis))) {
            reasons.add(prefix + "safety failed");
        }
        if (streamRequired
                && "stream".equalsIgnoreCase(invocation.transportMode())
                && invocation.streamContentChunkCount() <= 0) {
            reasons.add(prefix + "stream content chunk missing");
        }
        String failureReason = firstNonBlank(invocation.failureReason(), "");
        if (!failureReason.isBlank()) {
            reasons.add(prefix + sanitizeRecoveryFailureReason(failureReason));
        }
        return reasons;
    }

    private List<String> recoveryRequiredChecks(boolean streamRequired) {
        List<String> checks = new ArrayList<>(List.of(
                "status=MODEL_COMPLETED",
                "fallbackUsed=false",
                "modelHit=true",
                "evidenceRefs present",
                "answerLeakRisk!=HIGH"
        ));
        if (streamRequired) {
            checks.add("streamContentChunkCount>0");
        }
        return List.copyOf(checks);
    }

    private String recoveryEvidenceRef(SubmissionAnalysis analysis) {
        List<String> refs = diagnosisReportReader.evidenceRefs(analysis);
        if (!refs.isEmpty()) {
            return refs.get(0);
        }
        return "submission:" + analysis.getSubmissionId();
    }

    private String sanitizeRecoveryFailureReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120);
    }

    private String withRecoverySummary(String base, RecoveryStatusSummary recovery) {
        if (recovery == null || "NOT_APPLICABLE".equals(recovery.status)) {
            return base;
        }
        if ("RECOVERED".equals(recovery.status)) {
            return base + " 当前外部模型恢复验证为 RECOVERED：已观察到真实外部模型完成样本通过 recovery checks，可作为恢复证据。";
        }
        String reason = recovery.blockedReasons.isEmpty() ? "缺少通过恢复检查的样本" : recovery.blockedReasons.get(0);
        return base + " 当前外部模型恢复验证仍为 BLOCKED：" + reason + "。";
    }

    private String withRecoveryAction(String base, RecoveryStatusSummary recovery) {
        if (recovery == null || "NOT_APPLICABLE".equals(recovery.status)) {
            return base;
        }
        if ("RECOVERED".equals(recovery.status)) {
            return base + " 已有恢复证据，继续用小批量 live eval 观察是否稳定保持无 fallback 和有证据输出。";
        }
        return base + " 恢复前先运行单条 recovery smoke，确认 status=MODEL_COMPLETED、fallbackUsed=false、modelHit=true、evidenceRefs 存在"
                + (recovery.requiredChecks.contains("streamContentChunkCount>0") ? "、streamContentChunkCount>0" : "")
                + "。";
    }

    private String runtimeFailureType(DiagnosisReportReader.AiInvocationSnapshot invocation) {
        String reason = (firstNonBlank(invocation.failureReason(), invocation.status()) + " " + firstNonBlank(invocation.failureStage(), ""))
                .toUpperCase(Locale.ROOT);
        if (reason.contains("INSUFFICIENT_QUOTA") || reason.contains("QUOTA") || reason.contains("RATE_LIMIT")) {
            return "QUOTA_LIMIT";
        }
        if (reason.contains("SAFETY")) {
            return "SAFETY_REJECTED";
        }
        if (reason.contains("TIMEOUT")) {
            return "TIMEOUT";
        }
        if (reason.contains("OUTPUT_TRUNCATED") || reason.contains("TRUNCATED")) {
            return "OUTPUT_TRUNCATED";
        }
        if (reason.contains("INVALID") || reason.contains("VALIDATION") || reason.contains("JSON")) {
            return "VALIDATION_FAILED";
        }
        if (reason.contains("HTTP") || reason.contains("PROVIDER")) {
            return "PROVIDER_ERROR";
        }
        return "UNKNOWN_RUNTIME_FAILURE";
    }

    private int runtimeFailureTypeRank(String type) {
        return switch (type == null ? "" : type) {
            case "QUOTA_LIMIT" -> 1;
            case "SAFETY_REJECTED" -> 2;
            case "VALIDATION_FAILED" -> 3;
            case "OUTPUT_TRUNCATED" -> 4;
            case "TIMEOUT" -> 5;
            case "PROVIDER_ERROR" -> 6;
            case "PARTIAL_COMPLETION" -> 8;
            default -> 8;
        };
    }

    private String runtimeAttributionSummary(String type, long count, AiQualityMetrics metrics) {
        return switch (type == null ? "" : type) {
            case "QUOTA_LIMIT" -> "主导模型运行问题是额度不足，共 " + count + " 条；当前作业真实外部模型参与率受限。";
            case "SAFETY_REJECTED" -> "主导模型运行问题是安全拒绝或安全校验失败，共 " + count + " 条；需要复核提示安全边界。";
            case "VALIDATION_FAILED" -> "主导模型运行问题是输出契约或结构校验失败，共 " + count + " 条；需要复核 prompt 契约和解析校验。";
            case "OUTPUT_TRUNCATED" -> "主导模型运行问题是输出被 token 预算截断，共 " + count + " 条；需要复核 max tokens、schema 体积和单次调用策略。";
            case "TIMEOUT" -> "主导模型运行问题是超时，共 " + count + " 条；需要检查模型响应时延和超时配置。";
            case "PROVIDER_ERROR" -> "主导模型运行问题是 provider 或网络错误，共 " + count + " 条；需要检查外部服务稳定性。";
            case "PARTIAL_COMPLETION" -> "外部模型存在部分完成样本，共 " + metrics.modelPartialCount() + " 条；诊断可用但教学提示阶段仍需复核。";
            default -> "存在 " + metrics.modelRuntimeFailureCount() + " 条模型运行失败或兜底样本，失败原因尚未完整归类。";
        };
    }

    private String runtimeAttributionSummary(RuntimeAttributionAccumulator primary,
                                             AiQualityMetrics metrics) {
        String base = runtimeAttributionSummary(primary == null ? "" : primary.type,
                primary == null ? 0 : primary.count,
                metrics);
        if (primary == null) {
            return base;
        }
        if (primary.streamNoContentCount > 0) {
            return base + " 其中 " + primary.streamNoContentCount
                    + " 条是 stream 请求已发出但没有返回 content chunk。";
        }
        if (primary.streamInvalidChunkCount > 0) {
            return base + " 其中观察到 " + primary.streamInvalidChunkCount
                    + " 个不可解析 stream chunk。";
        }
        if (primary.streamFallbackRetryCount > 0) {
            return base + " 其中 " + primary.streamFallbackRetryCount
                    + " 条从 non-stream 回退到 stream 后恢复。";
        }
        return base;
    }

    private String runtimeAttributionAction(String type) {
        return switch (type == null ? "" : type) {
            case "QUOTA_LIMIT" -> "先检查 ModelScope 额度和计费状态；在恢复前降低 live eval 调用规模或继续使用 single-call 低预算路径。";
            case "SAFETY_REJECTED" -> "把对应样本沉淀为提示安全 fixture，复核 prompt 是否诱导直接给答案或越过教学边界。";
            case "VALIDATION_FAILED" -> "收窄输出 schema 和 prompt 契约，补充校验失败 fixture，优先修复结构化解析。";
            case "OUTPUT_TRUNCATED" -> "提高输出 token 预算或收缩 JSON schema/上下文；必要时切换 结构化重试 避免单次输出截断。";
            case "TIMEOUT" -> "降低单次上下文体积或调整超时阈值，再用小批量 live eval 验证响应时延。";
            case "PROVIDER_ERROR" -> "检查 provider 状态、网络和重试策略，并保留失败样本用于稳定性回归。";
            case "PARTIAL_COMPLETION" -> "保留可用诊断，同时复核教学提示阶段的安全和结构校验规则。";
            default -> "先查看 source segment 的 failureStage/failureReason，补充归因分类后再扩大外部模型评测。";
        };
    }

    private String runtimeAttributionAction(RuntimeAttributionAccumulator primary) {
        if (primary == null) {
            return runtimeAttributionAction("");
        }
        if ("QUOTA_LIMIT".equals(primary.type) && primary.streamNoContentCount > 0) {
            return "先检查 ModelScope 额度和计费状态；恢复前用单条 smoke 验证 stream 是否能返回 content chunk。";
        }
        if (primary.streamInvalidChunkCount > 0) {
            return "保留该样本作为 stream 解析 fixture，复核 SSE chunk 兼容性与 JSON 提取逻辑。";
        }
        if ("OUTPUT_TRUNCATED".equals(primary.type) && "length".equalsIgnoreCase(primary.streamFinishReason)) {
            return "提高输出 token 预算或收缩 JSON schema/上下文；当前 stream finish_reason=length，优先验证 max_tokens 是否不足。";
        }
        return runtimeAttributionAction(primary.type);
    }

    private boolean isStreamNoContent(DiagnosisReportReader.AiInvocationSnapshot invocation) {
        return invocation != null
                && "stream".equalsIgnoreCase(invocation.transportMode())
                && invocation.streamContentChunkCount() <= 0;
    }

    private String learningActionStatus(AiQualityMetrics metrics) {
        if (metrics.learningActionEvidenceCount() == 0) {
            return "WATCH";
        }
        if (metrics.learningActionContradictedCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.learningActionNotObservedCount() > metrics.learningActionObservedCount()) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double learningActionScore(AiQualityMetrics metrics) {
        if (metrics.learningActionEvidenceCount() == 0) {
            return 60.0;
        }
        double weightedGood = metrics.learningActionObservedCount()
                + metrics.learningActionPartiallyObservedCount() * 0.6
                + metrics.learningActionNotObservedCount() * 0.25;
        double score = weightedGood * 100.0 / metrics.learningActionEvidenceCount();
        return Math.round(score * 10.0) / 10.0;
    }

    private String learningActionSummary(AiQualityMetrics metrics) {
        if (metrics.learningActionEvidenceCount() == 0) {
            return "还没有足够后续提交或 coach 回答判断学习动作是否被执行。";
        }
        if (metrics.learningActionContradictedCount() > 0) {
            return "存在 " + metrics.learningActionContradictedCount() + " 条学习动作被后续证据反驳。";
        }
        if (metrics.learningActionObservedCount() > 0) {
            return "已有 " + metrics.learningActionObservedCount() + " 条学习动作被观察到有效执行。";
        }
        return "学习动作已有记录，但仍需要更多可观察产出确认效果。";
    }

    private String learningActionRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.learningActionContradictedCount() > 0) {
            return "降低提示粒度，要求最小样例、变量跟踪或教师介入检查。";
        }
        if (metrics.learningActionEvidenceCount() == 0) {
            return "等待后续同题提交或 coach 回答后再判断学习动作效果。";
        }
        if (metrics.learningActionNotObservedCount() > metrics.learningActionObservedCount()) {
            return "把学习动作改成更容易提交证据的可观察产出。";
        }
        return "把已观察到有效的学习动作沉淀为提示模板或 eval 断言。";
    }

    private String teacherCorrectionStatus(AiQualityMetrics metrics) {
        if (metrics.correctionCount() == 0) {
            return "WATCH";
        }
        if (metrics.evalCandidateCount() == 0) {
            return "ACTION_NEEDED";
        }
        return "HEALTHY";
    }

    private double teacherCorrectionScore(AiQualityMetrics metrics) {
        if (metrics.correctionCount() == 0) {
            return 60.0;
        }
        double score = metrics.evalCandidateCount() * 100.0 / metrics.correctionCount();
        return Math.round(score * 10.0) / 10.0;
    }

    private int severityRank(String status) {
        return switch (status == null ? "" : status) {
            case "ACTION_NEEDED" -> 3;
            case "WATCH" -> 2;
            default -> 1;
        };
    }

    private int dimensionPriorityRank(String dimension) {
        return switch (dimension == null ? "" : dimension) {
            case "MODEL_RUNTIME" -> 1;
            case "HINT_SAFETY" -> 2;
            case "PROMPT_SAFETY_INCIDENT_LOOP" -> 3;
            case "LEARNING_ACTION" -> 4;
            case "TEACHER_CALIBRATION_LOOP" -> 5;
            case "COACH_UNDERSTANDING" -> 6;
            case "COACH_FOLLOWUP_IMPACT_LOOP" -> 7;
            case "RECOMMENDATION_LOOP" -> 8;
            case "POST_AC_TRANSFER_LOOP" -> 9;
            case "RECURRING_MISCONCEPTION_LOOP" -> 10;
            case "SELF_EXPLANATION_MASTERY_LOOP" -> 11;
            case "AI_DEPENDENCY_INDEPENDENCE_LOOP" -> 12;
            case "MASTERY_GROWTH_LOOP" -> 13;
            case "TEACHING_ACTION_ORCHESTRATION_LOOP" -> 14;
            case "CLASS_TEACHING_STRATEGY_LOOP" -> 15;
            case "EVIDENCE_GROUNDING" -> 16;
            case "DIAGNOSIS_CONFIDENCE" -> 17;
            case "TEACHER_CORRECTION" -> 18;
            default -> 10;
        };
    }

    private String teacherCalibrationLoopStatus(AiQualityMetrics metrics) {
        if (metrics.correctionCount() == 0) {
            return "WATCH";
        }
        if (metrics.teacherCalibrationConflictCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.teacherCalibrationSignalCount() == 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.teacherCalibrationAppliedCount() > 0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double teacherCalibrationLoopScore(AiQualityMetrics metrics) {
        if (metrics.correctionCount() == 0) {
            return 60.0;
        }
        if (metrics.teacherCalibrationSignalCount() == 0) {
            return 35.0;
        }
        double supported = metrics.teacherCalibrationSupportedCount() * 1.0
                + metrics.teacherCalibrationAppliedCount() * 0.7;
        double penalty = metrics.teacherCalibrationConflictCount() * 30.0;
        double score = supported * 100.0 / Math.max(1, metrics.teacherCalibrationSignalCount()) - penalty;
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String teacherCalibrationLoopSummary(AiQualityMetrics metrics) {
        if (metrics.correctionCount() == 0) {
            return "还没有教师校正样本，暂不能形成教师校准闭环。";
        }
        if (metrics.teacherCalibrationSignalCount() == 0) {
            return "已有 " + metrics.correctionCount() + " 条教师校正，但近期诊断还没有教师校准信号。";
        }
        if (metrics.teacherCalibrationConflictCount() > 0) {
            return "存在 " + metrics.teacherCalibrationConflictCount() + " 条诊断与教师校准冲突，需要优先复核。";
        }
        return "已有 " + metrics.teacherCalibrationSignalCount() + " 条诊断引用教师校准，其中 "
                + metrics.teacherCalibrationSupportedCount() + " 条支持当前诊断，"
                + metrics.teacherCalibrationAppliedCount() + " 条作为辅助校准方向。";
    }

    private String teacherCalibrationLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.correctionCount() == 0) {
            return "先收集教师诊断校正，并标记高价值样本为 eval candidate。";
        }
        if (metrics.teacherCalibrationSignalCount() == 0) {
            return "检查教师校正是否进入学习记忆、诊断证据包和 ModelDiagnosisBrief。";
        }
        if (metrics.teacherCalibrationConflictCount() > 0) {
            return "优先抽查 CONFLICT_NEEDS_REVIEW 样本，确认当前证据是否应采用教师修正标签。";
        }
        if (metrics.teacherCalibrationAppliedCount() > 0) {
            return "把已应用的教师校准样本沉淀为诊断 eval，验证后续相似提交是否稳定命中修正标签。";
        }
        return "继续观察教师校准证据，并把稳定支持样本纳入回归评测。";
    }

    private String coachUnderstandingStatus(AiQualityMetrics metrics) {
        if (metrics.coachSafetyRiskCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.coachAnsweredCount() == 0) {
            return "WATCH";
        }
        if (metrics.coachVerifiableAnswerCount() == 0 || metrics.coachNeedsEvidenceCount() > metrics.coachVerifiableAnswerCount()) {
            return "ACTION_NEEDED";
        }
        if (metrics.coachVerifiableAnswerRate() < 70.0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double coachUnderstandingScore(AiQualityMetrics metrics) {
        if (metrics.coachAnsweredCount() == 0) {
            return 60.0;
        }
        double score = metrics.coachVerifiableAnswerRate() - metrics.coachSafetyRiskCount() * 25.0;
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String coachUnderstandingSummary(AiQualityMetrics metrics) {
        if (metrics.coachAnsweredCount() == 0) {
            return "还没有学生回答 Coach 追问，暂不能判断提示是否促进理解。";
        }
        if (metrics.coachSafetyRiskCount() > 0) {
            return "存在 " + metrics.coachSafetyRiskCount() + " 条 Coach 回答疑似越过证据层，需要人工复核。";
        }
        if (metrics.coachVerifiableAnswerCount() == 0) {
            return "学生已回答 Coach，但还没有形成可验证证据。";
        }
        return "已有 " + metrics.coachVerifiableAnswerCount() + "/" + metrics.coachAnsweredCount()
                + " 条 Coach 回答包含可验证证据，其中 " + metrics.coachTransferReadyCount() + " 条可进入复盘迁移。";
    }

    private String coachUnderstandingRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.coachSafetyRiskCount() > 0) {
            return "把 Coach 追问拉回证据层，只要求输入特征、输出对比或变量现象。";
        }
        if (metrics.coachAnsweredCount() == 0) {
            return "继续引导学生回答一个最小样例、变量轨迹或复杂度数量级。";
        }
        if (metrics.coachVerifiableAnswerCount() == 0 || metrics.coachNeedsEvidenceCount() > metrics.coachVerifiableAnswerCount()) {
            return "降低追问粒度，要求学生补一个可观察产出后再修改代码。";
        }
        return "把高质量 Coach 回答沉淀为可复用追问模板和通过后复盘动作。";
    }

    private String coachFollowupImpactLoopStatus(AiQualityMetrics metrics) {
        if (metrics.coachFollowupSameIssueCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.coachFollowupImpactCount() == 0 || metrics.coachFollowupAwaitingCount() > 0) {
            return "WATCH";
        }
        if (metrics.coachFollowupImprovementRate() < 60.0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double coachFollowupImpactLoopScore(AiQualityMetrics metrics) {
        if (metrics.coachFollowupImpactCount() == 0) {
            return 60.0;
        }
        double positive = metrics.coachFollowupAcceptedCount()
                + metrics.coachFollowupShiftedCount() * 0.7
                + metrics.coachFollowupVerdictChangedCount() * 0.45;
        double penalty = metrics.coachFollowupSameIssueCount() * 32.0
                + metrics.coachFollowupNoClearChangeCount() * 16.0
                + metrics.coachFollowupAwaitingCount() * 10.0;
        double score = positive * 100.0 / metrics.coachFollowupImpactCount() - penalty;
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String coachFollowupImpactLoopSummary(AiQualityMetrics metrics) {
        if (metrics.coachFollowupImpactCount() == 0) {
            return "还没有 Coach 追问后的同题后续提交样本，暂不能判断真实成效。";
        }
        if (metrics.coachFollowupSameIssueCount() > 0) {
            return "Coach 追问后仍有 " + metrics.coachFollowupSameIssueCount()
                    + " 个样本卡在同类错因，需要调整追问颗粒度。";
        }
        if (metrics.coachFollowupAwaitingCount() > 0) {
            return "已有 Coach 回答，但 " + metrics.coachFollowupAwaitingCount()
                    + " 个样本还缺同题后续提交成效证据。";
        }
        if (metrics.coachFollowupAcceptedCount() > 0) {
            return "已有 " + metrics.coachFollowupAcceptedCount()
                    + " 个 Coach 追问后的同题后续提交通过，可沉淀有效追问样本。";
        }
        if (metrics.coachFollowupShiftedCount() + metrics.coachFollowupVerdictChangedCount() > 0) {
            return "Coach 追问后有 " + (metrics.coachFollowupShiftedCount() + metrics.coachFollowupVerdictChangedCount())
                    + " 个样本进入新阶段，需要结合新诊断继续判断。";
        }
        return "Coach 追问后已有同题后续提交，但暂未观察到明确改善。";
    }

    private String coachFollowupImpactLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.coachFollowupSameIssueCount() > 0) {
            return "把仍卡同类问题的 Coach 样本加入回归评测，降低追问粒度到最小失败样例或输出对比。";
        }
        if (metrics.coachFollowupImpactCount() == 0 || metrics.coachFollowupAwaitingCount() > 0) {
            return "要求学生基于 Coach 回答完成一次同题最小修改提交，补齐后续成效证据。";
        }
        if (metrics.coachFollowupAcceptedCount() > 0) {
            return "沉淀追问后通过样本，复用到相同错因和能力点的 Coach 模板。";
        }
        return "继续观察错因转移和评测阶段变化，避免把表面变化误判为掌握。";
    }

    private String recommendationLoopStatus(AiQualityMetrics metrics) {
        if (metrics.recommendationTeacherInterventionRecommendedCount() > 0
                || metrics.recommendationUnresolvedLearningSignalCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.recommendationActionWaitingDiagnosisCount() > 0
                || metrics.recommendationUniqueCount() == 0
                || metrics.recommendationClickedWithoutSubmissionCount() > 0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double recommendationLoopScore(AiQualityMetrics metrics) {
        if (metrics.recommendationUniqueCount() == 0) {
            return 60.0;
        }
        double penalty = metrics.recommendationUnresolvedLearningSignalCount() * 35.0
                + metrics.recommendationTeacherInterventionRecommendedCount() * 25.0
                + metrics.recommendationClickedWithoutSubmissionCount() * 12.0
                + metrics.recommendationActionWaitingDiagnosisCount() * 8.0;
        double score = 100.0 - penalty / metrics.recommendationUniqueCount();
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String recommendationLoopSummary(AiQualityMetrics metrics) {
        if (metrics.recommendationUniqueCount() == 0) {
            return "还没有推荐使用数据，暂不能判断推荐是否促进学习迁移。";
        }
        if (metrics.recommendationUnresolvedLearningSignalCount() > 0) {
            return "推荐后仍有 " + metrics.recommendationUnresolvedLearningSignalCount()
                    + " 个学习信号未改善，其中 " + metrics.recommendationTeacherInterventionRecommendedCount()
                    + " 个建议教师介入。";
        }
        if (metrics.recommendationActionWaitingDiagnosisCount() > 0) {
            return "推荐后已有 " + metrics.recommendationActionWaitingDiagnosisCount()
                    + " 个行动证据等待诊断标签回填。";
        }
        if (metrics.recommendationClickedWithoutSubmissionCount() > 0) {
            return "有 " + metrics.recommendationClickedWithoutSubmissionCount()
                    + " 个推荐被点击或进入题目但没有形成后续提交。";
        }
        if (metrics.recommendationActionFulfilledCount() > 0) {
            return "已有 " + metrics.recommendationActionFulfilledCount()
                    + " 个推荐行动契约得到观察性兑现。";
        }
        return "推荐已形成使用证据，暂未发现同类错因未改善信号。";
    }

    private String recommendationLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.recommendationTeacherInterventionRecommendedCount() > 0) {
            return "把高风险推荐降级为最小样例复盘，并安排教师查看同类错因证据。";
        }
        if (metrics.recommendationUnresolvedLearningSignalCount() > 0) {
            return "下一轮推荐优先使用 STEP_DOWN_REVIEW，要求学生补充证据解释后再进入新题。";
        }
        if (metrics.recommendationActionWaitingDiagnosisCount() > 0) {
            return "等待后续诊断回填，再判断推荐行动契约是否兑现。";
        }
        if (metrics.recommendationClickedWithoutSubmissionCount() > 0) {
            return "检查推荐是否过大或起步成本过高，必要时改成更小复盘任务。";
        }
        if (metrics.recommendationUniqueCount() == 0) {
            return "等待推荐曝光、点击和后续提交形成学习效果证据。";
        }
        return "把有效策略按 byStrategy 分段沉淀为后续推荐评测断言。";
    }

    private String postAcTransferLoopStatus(AiQualityMetrics metrics) {
        if (metrics.postAcTransferPendingCount() > 0) {
            return metrics.postAcTransferPendingRate() >= 30.0 ? "ACTION_NEEDED" : "WATCH";
        }
        if (metrics.postAcTransferSignalCount() == 0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double postAcTransferLoopScore(AiQualityMetrics metrics) {
        if (metrics.postAcTransferSignalCount() == 0) {
            return 60.0;
        }
        double good = metrics.postAcTransferEvidencedCount()
                + metrics.postAcTransferReadyCount()
                + metrics.postAcTransferVerifiedCount() * 1.2;
        double score = good * 100.0 / metrics.postAcTransferSignalCount()
                - metrics.postAcTransferPendingCount() * 18.0;
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String postAcTransferLoopSummary(AiQualityMetrics metrics) {
        if (metrics.postAcTransferSignalCount() == 0) {
            return "还没有 AC 后样本，暂不能判断通过后的复盘迁移闭环。";
        }
        if (metrics.postAcTransferPendingCount() > 0) {
            return "存在 " + metrics.postAcTransferPendingCount()
                    + " 个已通过任务缺少复盘迁移证据，需要把 AC 转成可解释、可迁移的学习产出。";
        }
        if (metrics.postAcTransferVerifiedCount() > 0) {
            return "已有 " + metrics.postAcTransferVerifiedCount()
                    + " 个通过后任务形成迁移验证证据。";
        }
        return "AC 后已有复盘证据，但还需要更多同能力新题验证。";
    }

    private String postAcTransferLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.postAcTransferPendingCount() > 0) {
            return "为刚 AC 的学生安排一句话复盘、边界样例和同能力迁移题，避免通过后直接结束学习。";
        }
        if (metrics.postAcTransferSignalCount() == 0) {
            return "等待学生通过题目后，再收集复盘或迁移证据。";
        }
        if (metrics.postAcTransferVerifiedCount() == 0) {
            return "把已有复盘证据推进到同能力新题验证。";
        }
        return "把已验证的迁移样本沉淀为课堂复盘模板和推荐评测 fixture。";
    }

    private String recurringMisconceptionLoopStatus(AiQualityMetrics metrics) {
        if (metrics.recurringMisconceptionEscalationCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.recurringMisconceptionRecurringCount() > 0) {
            return "WATCH";
        }
        if (metrics.recurringMisconceptionSignalCount() == 0) {
            return "HEALTHY";
        }
        return "HEALTHY";
    }

    private double recurringMisconceptionLoopScore(AiQualityMetrics metrics) {
        if (metrics.recurringMisconceptionSignalCount() == 0) {
            return 100.0;
        }
        double penalty = metrics.recurringMisconceptionEscalationCount() * 35.0
                + metrics.recurringMisconceptionRecurringCount() * 18.0
                + metrics.recurringMisconceptionWatchCount() * 5.0;
        double score = 100.0 - penalty / Math.max(1, metrics.recurringMisconceptionSignalCount());
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String recurringMisconceptionLoopSummary(AiQualityMetrics metrics) {
        if (metrics.recurringMisconceptionEscalationCount() > 0) {
            return "存在 " + metrics.recurringMisconceptionEscalationCount()
                    + " 个复发误区需要教师介入，系统应把长期模式转成更小复盘动作。";
        }
        if (metrics.recurringMisconceptionRecurringCount() > 0) {
            return "存在 " + metrics.recurringMisconceptionRecurringCount()
                    + " 个跨题或跨作业复发误区，需要推荐修复任务。";
        }
        if (metrics.recurringMisconceptionWatchCount() > 0) {
            return "有 " + metrics.recurringMisconceptionWatchCount()
                    + " 个同题重复误区处于观察状态，暂未形成跨题复发。";
        }
        return "当前样本暂无明显长期复发误区。";
    }

    private String recurringMisconceptionLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.recurringMisconceptionEscalationCount() > 0) {
            return "优先把升级样本交给教师复盘，要求学生对比两道证据题的共同失败条件。";
        }
        if (metrics.recurringMisconceptionRecurringCount() > 0) {
            return "生成 MISCONCEPTION_REPAIR 推荐，先修复长期误区再继续加新题。";
        }
        if (metrics.recurringMisconceptionWatchCount() > 0) {
            return "继续观察下一题是否重复命中同类错因。";
        }
        return "继续收集跨题和跨作业诊断证据。";
    }

    private String selfExplanationLoopStatus(AiQualityMetrics metrics) {
        if (metrics.selfExplanationSafetyRiskCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.selfExplanationNeedsCoachingCount() > 0) {
            return metrics.selfExplanationNeedsCoachingCount() >= 2 ? "ACTION_NEEDED" : "WATCH";
        }
        if (metrics.selfExplanationSignalCount() == 0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double selfExplanationLoopScore(AiQualityMetrics metrics) {
        if (metrics.selfExplanationSignalCount() == 0) {
            return 60.0;
        }
        double good = metrics.selfExplanationEvidenceGroundedCount()
                + metrics.selfExplanationTransferReadyCount() * 1.2;
        double penalty = metrics.selfExplanationNeedsCoachingCount() * 18.0
                + metrics.selfExplanationSafetyRiskCount() * 35.0;
        double score = good * 100.0 / metrics.selfExplanationSignalCount() - penalty;
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String selfExplanationLoopSummary(AiQualityMetrics metrics) {
        if (metrics.selfExplanationSignalCount() == 0) {
            return "还没有学生自解释信号，暂不能判断提示是否沉淀为学生自己的证据解释。";
        }
        if (metrics.selfExplanationSafetyRiskCount() > 0) {
            return "存在 " + metrics.selfExplanationSafetyRiskCount()
                    + " 个自解释信号疑似越过证据层，需要教师示范如何描述证据。";
        }
        if (metrics.selfExplanationNeedsCoachingCount() > 0) {
            return "存在 " + metrics.selfExplanationNeedsCoachingCount()
                    + " 个学生自解释证据不足，需要安排更小解释练习。";
        }
        return "已有 " + (metrics.selfExplanationEvidenceGroundedCount() + metrics.selfExplanationTransferReadyCount())
                + " 个学生形成可验证或可迁移解释。";
    }

    private String selfExplanationLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.selfExplanationSafetyRiskCount() > 0) {
            return "先让教师示范只描述最小样例、变量变化和输出对比，避免讨论完整改法。";
        }
        if (metrics.selfExplanationNeedsCoachingCount() > 0) {
            return "生成 SELF_EXPLANATION_PRACTICE 推荐，要求学生补一个可检查证据后再继续改代码。";
        }
        if (metrics.selfExplanationSignalCount() == 0) {
            return "继续引导学生回答 Coach 追问，收集自解释证据。";
        }
        return "把高质量自解释样本沉淀为 Coach 追问模板和复盘评测 fixture。";
    }

    private String aiDependencyLoopStatus(AiQualityMetrics metrics) {
        if (metrics.aiDependencyTeacherFadeCount() > 0 || metrics.aiDependencyRiskCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.aiDependencyDenseCount() > 0 || metrics.aiDependencySignalCount() == 0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double aiDependencyLoopScore(AiQualityMetrics metrics) {
        if (metrics.aiDependencySignalCount() == 0) {
            return 60.0;
        }
        double positive = metrics.aiDependencyIndependentCount() + metrics.aiDependencyEffectiveCount() * 0.8;
        double penalty = metrics.aiDependencyDenseCount() * 16.0
                + metrics.aiDependencyRiskCount() * 28.0
                + metrics.aiDependencyTeacherFadeCount() * 38.0;
        double score = positive * 100.0 / metrics.aiDependencySignalCount() - penalty;
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String aiDependencyLoopSummary(AiQualityMetrics metrics) {
        if (metrics.aiDependencySignalCount() == 0) {
            return "还没有 AI 支架依赖度信号，暂不能判断提示是否正在退场。";
        }
        if (metrics.aiDependencyTeacherFadeCount() > 0) {
            return "存在 " + metrics.aiDependencyTeacherFadeCount()
                    + " 个学生长期高密度使用 AI 支架且缺少独立推进，需要教师撤支架复盘。";
        }
        if (metrics.aiDependencyRiskCount() > 0) {
            return "存在 " + metrics.aiDependencyRiskCount()
                    + " 个学生支架后仍未改善，需要先做独立尝试复盘。";
        }
        if (metrics.aiDependencyDenseCount() > 0) {
            return "存在 " + metrics.aiDependencyDenseCount()
                    + " 个学生 AI 支架使用偏密，需要观察是否能转成独立提交。";
        }
        return "已有 " + (metrics.aiDependencyIndependentCount() + metrics.aiDependencyEffectiveCount())
                + " 个学生表现出独立推进或有效支架退场。";
    }

    private String aiDependencyLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.aiDependencyTeacherFadeCount() > 0) {
            return "生成 TEACHER_SCAFFOLD_FADE_REVIEW 推荐，由教师示范如何拆提示并限制下一轮提示密度。";
        }
        if (metrics.aiDependencyRiskCount() > 0 || metrics.aiDependencyDenseCount() > 0) {
            return "生成 INDEPENDENT_ATTEMPT 推荐，要求学生先不新增提示完成一次最小独立尝试。";
        }
        if (metrics.aiDependencySignalCount() == 0) {
            return "继续收集 Coach、推荐和后续提交事件，再判断支架是否能退场。";
        }
        return "把独立推进样本沉淀为推荐策略和课堂复盘样例。";
    }

    private String masteryGrowthLoopStatus(AiQualityMetrics metrics) {
        if (metrics.masteryGrowthSpiralReviewCount() > 0 || metrics.masteryGrowthRegressionCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.masteryGrowthPlateauCount() > 0 || metrics.masteryGrowthSignalCount() == 0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double masteryGrowthLoopScore(AiQualityMetrics metrics) {
        if (metrics.masteryGrowthSignalCount() == 0) {
            return 60.0;
        }
        double positive = metrics.masteryGrowthGrowingCount() + metrics.masteryGrowthTransferConfirmedCount() * 1.2;
        double penalty = metrics.masteryGrowthPlateauCount() * 16.0
                + metrics.masteryGrowthRegressionCount() * 28.0
                + metrics.masteryGrowthSpiralReviewCount() * 36.0;
        double score = positive * 100.0 / metrics.masteryGrowthSignalCount() - penalty;
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String masteryGrowthLoopSummary(AiQualityMetrics metrics) {
        if (metrics.masteryGrowthSignalCount() == 0) {
            return "还没有长期成长信号，暂不能判断局部诊断是否沉淀为能力增长。";
        }
        if (metrics.masteryGrowthSpiralReviewCount() > 0) {
            return "存在 " + metrics.masteryGrowthSpiralReviewCount()
                    + " 个学生跨题重复停滞，需要螺旋复习而不是继续加提示。";
        }
        if (metrics.masteryGrowthRegressionCount() > 0) {
            return "存在 " + metrics.masteryGrowthRegressionCount()
                    + " 个学生出现通过后回退，需要对比提交差异。";
        }
        if (metrics.masteryGrowthPlateauCount() > 0) {
            return "存在 " + metrics.masteryGrowthPlateauCount()
                    + " 个学生近期成长停滞，需要降到最小样例复盘。";
        }
        return "已有 " + (metrics.masteryGrowthGrowingCount() + metrics.masteryGrowthTransferConfirmedCount())
                + " 个学生出现能力增长或迁移验证证据。";
    }

    private String masteryGrowthLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.masteryGrowthSpiralReviewCount() > 0) {
            return "生成 MASTERY_SPIRAL_REVIEW 推荐，把多题共同失败条件转成教师或学生复盘任务。";
        }
        if (metrics.masteryGrowthRegressionCount() > 0) {
            return "生成 MASTERY_REGRESSION_REPAIR 推荐，要求对比通过提交与当前失败提交。";
        }
        if (metrics.masteryGrowthPlateauCount() > 0) {
            return "生成 MASTERY_PLATEAU_REPAIR 推荐，把连续失败降为一个最小验证假设。";
        }
        if (metrics.masteryGrowthSignalCount() == 0) {
            return "继续收集跨题提交和诊断标签，再判断长期成长。";
        }
        return "把能力增长和迁移验证样本沉淀为推荐评测 fixture。";
    }

    private String teachingActionLoopStatus(AiQualityMetrics metrics) {
        if (metrics.teachingActionSignalCount() == 0) {
            return "WATCH";
        }
        if (metrics.teachingActionHighRiskCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.teachingActionCandidateCount() > metrics.teachingActionSignalCount()) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double teachingActionLoopScore(AiQualityMetrics metrics) {
        if (metrics.teachingActionSignalCount() == 0) {
            return 60.0;
        }
        double evidenceRate = metrics.teachingActionEvidenceReadyCount() * 100.0 / metrics.teachingActionSignalCount();
        double penalty = metrics.teachingActionHighRiskCount() * 18.0 + metrics.teachingActionTeacherCount() * 8.0;
        double score = evidenceRate - penalty / Math.max(1, metrics.teachingActionSignalCount());
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String teachingActionLoopSummary(AiQualityMetrics metrics) {
        if (metrics.teachingActionSignalCount() == 0) {
            return "还没有足够学生信号生成结构化教学动作。";
        }
        if (metrics.teachingActionHighRiskCount() > 0) {
            return "存在 " + metrics.teachingActionHighRiskCount()
                    + " 个高风险教学动作，其中 " + metrics.teachingActionTeacherCount()
                    + " 个需要教师执行或复盘。";
        }
        if (metrics.teachingActionCandidateCount() > metrics.teachingActionSignalCount()) {
            return "已有多个局部教育信号被编排为最高优先级动作，可继续观察排序是否符合课堂判断。";
        }
        return "教学动作编排已能把局部信号转成明确下一步动作。";
    }

    private String teachingActionLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.teachingActionHighRiskCount() > 0) {
            return "优先处理 TEACHING_ACTION_* 推荐，并核对来源信号与证据引用是否支持教师介入。";
        }
        if (metrics.teachingActionSignalCount() == 0) {
            return "继续收集提交、Coach、推荐和成长信号，再生成教学动作决策。";
        }
        if (metrics.teachingActionEvidenceReadyCount() < metrics.teachingActionSignalCount()) {
            return "补齐教学动作 evidenceRefs，保证教师能追溯为什么选择该动作。";
        }
        return "把当前编排动作沉淀为推荐策略和质量评测 fixture。";
    }

    private String classTeachingStrategyLoopStatus(AiQualityMetrics metrics) {
        if (metrics.analyzedSubmissionCount() == 0) {
            return "WATCH";
        }
        if (metrics.classTeachingStrategySignalCount() == 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.classTeachingStrategyMissingEvidenceCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.classTeachingStrategyStillStuckCount() > 0
                || metrics.classTeachingStrategyEscalationCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.classTeachingStrategyMissingExitTicketCount() > 0
                || metrics.classTeachingStrategyActionableCount() > 0 && metrics.classTeachingStrategyGroupPlanCount() == 0) {
            return "WATCH";
        }
        if (metrics.classTeachingStrategyActionableCount() > 0 && metrics.classTeachingStrategyFeedbackCount() == 0) {
            return "WATCH";
        }
        if (metrics.classTeachingStrategyWaitingFollowupCount() > 0
                || metrics.classTeachingStrategyShiftedCount() > 0) {
            return "WATCH";
        }
        if (metrics.classTeachingStrategyImprovedCount() > 0) {
            return "HEALTHY";
        }
        if (metrics.classTeachingStrategyActionableCount() == 0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double classTeachingStrategyLoopScore(AiQualityMetrics metrics) {
        if (metrics.analyzedSubmissionCount() == 0) {
            return 60.0;
        }
        if (metrics.classTeachingStrategySignalCount() == 0) {
            return 35.0;
        }
        double score = 70.0;
        if (metrics.classTeachingStrategyActionableCount() > 0) {
            score += 20.0;
        }
        if (metrics.classTeachingStrategyGroupPlanCount() > 0) {
            score += 5.0;
        }
        if (metrics.classTeachingStrategyFeedbackCount() == 0 && metrics.classTeachingStrategyActionableCount() > 0) {
            score -= 15.0;
        }
        if (metrics.classTeachingStrategyWaitingFollowupCount() > 0) {
            score -= 5.0;
        }
        if (metrics.classTeachingStrategyImprovedCount() > 0) {
            score += 10.0;
        }
        if (metrics.classTeachingStrategyShiftedCount() > 0) {
            score -= 10.0;
        }
        if (metrics.classTeachingStrategyStillStuckCount() > 0 || metrics.classTeachingStrategyEscalationCount() > 0) {
            score -= 35.0;
        }
        if (metrics.classTeachingStrategyMissingEvidenceCount() > 0) {
            score -= 35.0;
        }
        if (metrics.classTeachingStrategyMissingExitTicketCount() > 0) {
            score -= 20.0;
        }
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String classTeachingStrategyLoopSummary(AiQualityMetrics metrics) {
        if (metrics.analyzedSubmissionCount() == 0) {
            return "当前作业还没有 AI 诊断样本，暂不能生成班级教学策略。";
        }
        if (metrics.classTeachingStrategySignalCount() == 0) {
            return "已有诊断样本，但尚未形成结构化班级教学策略。";
        }
        if (metrics.classTeachingStrategyMissingEvidenceCount() > 0) {
            return "班级教学策略已生成，但缺少可追踪证据引用。";
        }
        if (metrics.classTeachingStrategyStillStuckCount() > 0) {
            return "班级策略已执行，但后续提交仍命中原策略标签，需要升级干预。";
        }
        if (metrics.classTeachingStrategyImprovedCount() > 0) {
            return "班级策略已形成教师反馈和后续改善证据。";
        }
        if (metrics.classTeachingStrategyShiftedCount() > 0) {
            return "班级策略执行后原问题有所转移，但仍需要围绕新错因复盘。";
        }
        if (metrics.classTeachingStrategyWaitingFollowupCount() > 0) {
            return "班级策略已被教师处理，正在等待后续提交验证成效。";
        }
        if (metrics.classTeachingStrategyActionableCount() > 0 && metrics.classTeachingStrategyFeedbackCount() == 0) {
            return "班级风险已转成可执行课堂策略，但还缺少教师执行反馈。";
        }
        if (metrics.classTeachingStrategyMissingExitTicketCount() > 0) {
            return "班级教学策略已生成，但还缺少课末退出题或验证任务。";
        }
        if (metrics.classTeachingStrategyActionableCount() > 0) {
            return "班级风险已转成可执行课堂策略，并包含分组或讲评行动。";
        }
        return "班级策略处于观察状态，继续收集是否需要全班或小组干预的证据。";
    }

    private String classTeachingStrategyLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.analyzedSubmissionCount() == 0) {
            return "等待学生提交和诊断完成后再生成课堂策略。";
        }
        if (metrics.classTeachingStrategySignalCount() == 0) {
            return "检查作业概览是否接入 ClassTeachingStrategyAnalyzer。";
        }
        if (metrics.classTeachingStrategyMissingEvidenceCount() > 0) {
            return "为班级策略补充提交、错因或学生状态 evidenceRefs。";
        }
        if (metrics.classTeachingStrategyStillStuckCount() > 0 || metrics.classTeachingStrategyEscalationCount() > 0) {
            return "将班级策略升级为更小粒度复盘或教师点对点检查，并要求学生补最小失败样例。";
        }
        if (metrics.classTeachingStrategyImprovedCount() > 0) {
            return "沉淀本次课堂策略为复盘模板，并设计一个同能力迁移检查。";
        }
        if (metrics.classTeachingStrategyShiftedCount() > 0) {
            return "重新查看后续提交的新错因，避免沿用原策略重复讲评。";
        }
        if (metrics.classTeachingStrategyWaitingFollowupCount() > 0) {
            return "等待后续提交或补充课堂执行观察，再判断策略是否有效。";
        }
        if (metrics.classTeachingStrategyActionableCount() > 0 && metrics.classTeachingStrategyFeedbackCount() == 0) {
            return "请教师在课堂策略区记录采纳、调整或忽略，让策略进入成效闭环。";
        }
        if (metrics.classTeachingStrategyMissingExitTicketCount() > 0) {
            return "为课堂策略补充一个不泄露答案的退出题或验证任务。";
        }
        if (metrics.classTeachingStrategyActionableCount() > 0 && metrics.classTeachingStrategyGroupPlanCount() == 0) {
            return "为可执行班级策略补充小组或全班讲评分组计划。";
        }
        return "把高质量班级策略沉淀为教师复盘模板，并观察后续提交是否改善。";
    }

    private String teacherInterventionLoopStatus(AiQualityMetrics metrics) {
        if (metrics.teacherInterventionStillStuckCount() > 0 || metrics.teacherInterventionEscalationCount() > 0) {
            return "ACTION_NEEDED";
        }
        if (metrics.teacherInterventionExecutedCount() == 0 || metrics.teacherInterventionWaitingFollowupCount() > 0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private double teacherInterventionLoopScore(AiQualityMetrics metrics) {
        if (metrics.teacherInterventionExecutedCount() == 0) {
            return 60.0;
        }
        double score = metrics.teacherInterventionImprovementRate()
                - metrics.teacherInterventionStillStuckCount() * 30.0
                - metrics.teacherInterventionWaitingFollowupCount() * 12.0;
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }

    private String teacherInterventionLoopSummary(AiQualityMetrics metrics) {
        if (metrics.teacherInterventionExecutedCount() == 0) {
            return "还没有教师采纳或调整课堂复盘建议，暂不能判断教师介入成效。";
        }
        if (metrics.teacherInterventionStillStuckCount() > 0) {
            return "教师介入后仍有 " + metrics.teacherInterventionStillStuckCount()
                    + " 条复盘建议命中同类错因，需要升级干预。";
        }
        if (metrics.teacherInterventionWaitingFollowupCount() > 0) {
            return "教师已执行 " + metrics.teacherInterventionExecutedCount()
                    + " 条复盘动作，其中 " + metrics.teacherInterventionWaitingFollowupCount()
                    + " 条还缺少后续提交证据。";
        }
        return "教师介入闭环已有改善或错因转移证据，可沉淀有效课堂动作。";
    }

    private String teacherInterventionLoopRecommendedAction(AiQualityMetrics metrics) {
        if (metrics.teacherInterventionStillStuckCount() > 0 || metrics.teacherInterventionEscalationCount() > 0) {
            return "降低课堂复盘颗粒度，要求更小失败样例或教师点对点检查。";
        }
        if (metrics.teacherInterventionExecutedCount() == 0) {
            return "先让教师对高优先级复盘建议执行采纳、调整或忽略。";
        }
        if (metrics.teacherInterventionWaitingFollowupCount() > 0) {
            return "等待学生后续提交，或请教师补充课堂执行后的观察证据。";
        }
        return "把已改善的教师介入动作整理为复盘模板和后续 eval 样本。";
    }

    private List<String> evidenceRefsForLowConfidence(List<SubmissionAnalysis> analyses) {
        return evidenceRefsForAnalyses(analyses, analysis -> {
            Double confidence = diagnosisReportReader.confidence(analysis);
            return confidence == null || confidence < AiQualityMetrics.LOW_CONFIDENCE_THRESHOLD;
        }, "low_confidence");
    }

    private List<String> evidenceRefsForMissingEvidence(List<SubmissionAnalysis> analyses) {
        return evidenceRefsForAnalyses(analyses, analysis -> diagnosisReportReader.evidenceRefs(analysis).isEmpty(), "missing_evidence");
    }

    private List<String> evidenceRefsForHighLeakRisk(List<SubmissionAnalysis> analyses) {
        return evidenceRefsForAnalyses(analyses, analysis -> "HIGH".equalsIgnoreCase(diagnosisReportReader.answerLeakRisk(analysis)), "high_leak_risk");
    }

    private List<String> evidenceRefsForRuntime(List<SubmissionAnalysis> analyses) {
        return evidenceRefsForAnalyses(analyses, analysis -> {
            DiagnosisReportReader.AiInvocationSnapshot invocation = diagnosisReportReader.aiInvocation(analysis);
            return isRuntimeFailure(invocation);
        }, "runtime_failure");
    }

    private boolean isRuntimeFailure(DiagnosisReportReader.AiInvocationSnapshot invocation) {
        return invocation != null
                && ("MODEL_RUNTIME_FALLBACK".equalsIgnoreCase(invocation.status())
                || "MODEL_FAILED".equalsIgnoreCase(invocation.status())
                || invocation.fallbackUsed());
    }

    private List<String> evidenceRefsForLearningAction(List<SubmissionAnalysis> analyses) {
        return evidenceRefsForAnalyses(analyses, analysis -> diagnosisReportReader.learningActionEvidence(analysis) != null, "learning_action");
    }

    private List<String> evidenceRefsForAnalyses(List<SubmissionAnalysis> analyses,
                                                 java.util.function.Predicate<SubmissionAnalysis> predicate,
                                                 String fallbackPrefix) {
        if (analyses == null) {
            return List.of();
        }
        return analyses.stream()
                .filter(predicate)
                .limit(5)
                .map(analysis -> {
                    List<String> refs = diagnosisReportReader.evidenceRefs(analysis);
                    if (!refs.isEmpty()) {
                        return refs.get(0);
                    }
                    return fallbackPrefix + ":submission:" + analysis.getSubmissionId();
                })
                .distinct()
                .toList();
    }

    private List<String> evidenceRefsForCorrections(List<TeacherDiagnosisCorrection> corrections) {
        if (corrections == null) {
            return List.of();
        }
        return corrections.stream()
                .limit(5)
                .map(correction -> "teacher_correction:" + correction.getSubmissionId())
                .distinct()
                .toList();
    }

    private List<String> evidenceRefsForTeacherCalibration(List<SubmissionAnalysis> analyses) {
        if (analyses == null) {
            return List.of();
        }
        return analyses.stream()
                .map(analysis -> {
                    DiagnosisReportReader.TeacherCalibrationSignalSnapshot signal =
                            diagnosisReportReader.teacherCalibrationSignal(analysis);
                    if (signal == null) {
                        return null;
                    }
                    if (signal.evidenceRefs() != null && !signal.evidenceRefs().isEmpty()) {
                        return signal.evidenceRefs().get(0);
                    }
                    return "teacher_calibration:submission:" + analysis.getSubmissionId();
                })
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .toList();
    }

    private List<String> evidenceRefsForCoachUnderstanding(
            Map<Long, com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse> coachInteractions) {
        if (coachInteractions == null || coachInteractions.isEmpty()) {
            return List.of();
        }
        return coachInteractions.values().stream()
                .filter(summary -> summary != null && summary.getAnswerQualitySignal() != null)
                .limit(5)
                .map(summary -> "coach:submission:" + summary.getSubmissionId()
                        + ":" + summary.getAnswerQualitySignal().getQualityLevel())
                .distinct()
                .toList();
    }

    private List<String> evidenceRefsForCoachFollowupImpactLoop(Map<Long, CoachImpactResponse> coachImpacts) {
        if (coachImpacts == null || coachImpacts.isEmpty()) {
            return List.of();
        }
        return coachImpacts.values().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CoachImpactResponse::getAnsweredAt,
                        Comparator.nullsLast(java.time.LocalDateTime::compareTo)).reversed())
                .flatMap(impact -> {
                    List<String> refs = new ArrayList<>();
                    if (impact.getCoachedSubmissionId() != null) {
                        refs.add("coach_impact:" + impact.getStatus() + ":submission:" + impact.getCoachedSubmissionId());
                    } else if (impact.getStatus() != null) {
                        refs.add("coach_impact:" + impact.getStatus());
                    }
                    if (impact.getFollowupSubmissionId() != null) {
                        refs.add("followup_submission:" + impact.getFollowupSubmissionId());
                    }
                    return refs.stream();
                })
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(6)
                .toList();
    }

    private List<String> evidenceRefsForRecommendationLoop(RecommendationEffectivenessResponse recommendationEffectiveness) {
        if (recommendationEffectiveness == null) {
            return List.of();
        }
        List<String> actionRefs = recommendationEffectiveness.getActionEvidenceSignals() == null
                ? List.of()
                : recommendationEffectiveness.getActionEvidenceSignals().stream()
                .filter(signal -> signal != null && (signal.isNeedsTeacherAttention()
                        || RecommendationActionEvidenceAnalyzer.OUTCOME_UNRESOLVED_SAME_FOCUS.equals(signal.getOutcome())
                        || RecommendationActionEvidenceAnalyzer.OUTCOME_NO_FOLLOWUP_SUBMISSION.equals(signal.getOutcome())
                        || RecommendationActionEvidenceAnalyzer.OUTCOME_WAITING_DIAGNOSIS.equals(signal.getOutcome())))
                .flatMap(signal -> signal.getEvidenceRefs() == null ? List.<String>of().stream() : signal.getEvidenceRefs().stream())
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(5)
                .toList();
        if (!actionRefs.isEmpty()) {
            return actionRefs;
        }
        if (recommendationEffectiveness.getFeedbackSignals() == null) {
            return List.of();
        }
        return recommendationEffectiveness.getFeedbackSignals().stream()
                .filter(signal -> signal != null && signal.getEvidenceTokens() != null)
                .flatMap(signal -> signal.getEvidenceTokens().stream()
                        .filter(token -> token != null && !token.isBlank())
                        .map(token -> "recommendation:" + token))
                .distinct()
                .limit(5)
                .toList();
    }

    private Map<Long, CoachImpactResponse> buildCoachImpacts(List<Submission> submissions,
                                                             List<SubmissionAnalysis> analyses,
                                                             List<CoachPrompt> coachPrompts) {
        if (coachImpactAnalyzer == null || submissions == null || submissions.isEmpty()
                || coachPrompts == null || coachPrompts.isEmpty()) {
            return Map.of();
        }
        Map<Long, SubmissionAnalysis> analysesBySubmission = analyses == null ? Map.of() : analyses.stream()
                .filter(analysis -> analysis != null && analysis.getSubmissionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionAnalysis::getSubmissionId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return coachImpactAnalyzer.summarizeByCoachedSubmission(submissions, analysesBySubmission, coachPrompts);
    }

    private List<StudentTrajectoryResponse.PostAcTransferSignal> buildPostAcTransferSignals(
            List<Submission> submissions,
            List<SubmissionAnalysis> analyses,
            Map<Long, com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse> coachInteractions) {
        if (postAcTransferAnalyzer == null || submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionAnalysis> analysesBySubmission = analyses == null ? Map.of() : analyses.stream()
                .filter(analysis -> analysis != null && analysis.getSubmissionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionAnalysis::getSubmissionId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return submissions.stream()
                .filter(submission -> submission.getStudentProfileId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        Submission::getStudentProfileId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .values()
                .stream()
                .flatMap(studentSubmissions -> postAcTransferAnalyzer.analyzeTasks(
                                studentSubmissions,
                                analysesBySubmission,
                                coachInteractions,
                                Map.of()
                        )
                        .values()
                        .stream())
                .filter(signal -> signal != null && !PostAcTransferAnalyzer.PHASE_NOT_ACCEPTED.equals(signal.getPhase()))
                .toList();
    }

    private List<String> evidenceRefsForPostAcTransferLoop(List<StudentTrajectoryResponse.PostAcTransferSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        return signals.stream()
                .filter(Objects::nonNull)
                .filter(signal -> postAcTransferAnalyzer == null || postAcTransferAnalyzer.isPending(signal)
                        || PostAcTransferAnalyzer.PHASE_TRANSFER_VERIFIED.equals(signal.getPhase()))
                .flatMap(signal -> {
                    List<String> refs = new ArrayList<>();
                    refs.add("post_ac_transfer:" + signal.getPhase() + ":problem:" + signal.getProblemId());
                    if (signal.getEvidenceRefs() != null) {
                        refs.addAll(signal.getEvidenceRefs());
                    }
                    return refs.stream();
                })
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> buildRecurringMisconceptionSignals(
            List<Submission> submissions,
            List<SubmissionAnalysis> analyses) {
        if (recurringMisconceptionAnalyzer == null || submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionAnalysis> analysesBySubmission = analyses == null ? Map.of() : analyses.stream()
                .filter(analysis -> analysis != null && analysis.getSubmissionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionAnalysis::getSubmissionId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return submissions.stream()
                .filter(submission -> submission.getStudentProfileId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        Submission::getStudentProfileId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .values()
                .stream()
                .map(studentSubmissions -> recurringMisconceptionAnalyzer.analyze(studentSubmissions, analysesBySubmission))
                .filter(signal -> signal != null && !RecurringMisconceptionAnalyzer.STATUS_NONE.equals(signal.getStatus()))
                .toList();
    }

    private List<String> evidenceRefsForRecurringMisconceptionLoop(
            List<StudentAbilityProfileResponse.RecurringMisconceptionSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        return signals.stream()
                .filter(Objects::nonNull)
                .flatMap(signal -> {
                    List<String> refs = new ArrayList<>();
                    refs.add("recurring_misconception:" + signal.getStatus() + ":" + firstNonBlank(signal.getFineGrainedTag(), signal.getMisconceptionTag(), "UNKNOWN"));
                    if (signal.getEvidenceRefs() != null) {
                        refs.addAll(signal.getEvidenceRefs());
                    }
                    return refs.stream();
                })
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> buildSelfExplanationSignals(
            List<Submission> submissions,
            List<CoachPrompt> prompts) {
        if (selfExplanationMasteryAnalyzer == null || submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CoachPrompt>> promptsByStudent = prompts == null ? Map.of() : prompts.stream()
                .filter(prompt -> prompt != null && prompt.getStudentProfileId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        CoachPrompt::getStudentProfileId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        return submissions.stream()
                .filter(submission -> submission.getStudentProfileId() != null)
                .map(Submission::getStudentProfileId)
                .distinct()
                .map(studentId -> selfExplanationMasteryAnalyzer.analyze(promptsByStudent.getOrDefault(studentId, List.of())))
                .filter(signal -> signal != null && !SelfExplanationMasteryAnalyzer.STATUS_NO_EVIDENCE.equals(signal.getStatus()))
                .toList();
    }

    private List<String> evidenceRefsForSelfExplanationLoop(
            List<StudentAbilityProfileResponse.SelfExplanationMasterySignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        return signals.stream()
                .filter(Objects::nonNull)
                .flatMap(signal -> {
                    List<String> refs = new ArrayList<>();
                    refs.add("self_explanation:" + signal.getStatus());
                    if (signal.getEvidenceRefs() != null) {
                        refs.addAll(signal.getEvidenceRefs());
                    }
                    return refs.stream();
                })
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private List<StudentAbilityProfileResponse.AiDependencySignal> buildAiDependencySignals(
            List<Submission> submissions,
            List<CoachPrompt> prompts,
            Long assignmentId) {
        if (aiDependencyAnalyzer == null || submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CoachPrompt>> promptsByStudent = prompts == null ? Map.of() : prompts.stream()
                .filter(prompt -> prompt != null && prompt.getStudentProfileId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        CoachPrompt::getStudentProfileId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Map<Long, List<Submission>> submissionsByStudent = submissions.stream()
                .filter(submission -> submission.getStudentProfileId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        Submission::getStudentProfileId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        return submissionsByStudent.entrySet()
                .stream()
                .map(entry -> aiDependencyAnalyzer.analyze(
                        entry.getValue(),
                        promptsByStudent.getOrDefault(entry.getKey(), List.of()),
                        recommendationEventRepository == null
                                ? List.of()
                                : recommendationEventRepository.findByStudentProfileIdOrderByCreatedAtDesc(entry.getKey())
                                .stream()
                                .filter(event -> assignmentId == null || Objects.equals(event.getAssignmentId(), assignmentId))
                                .toList()
                ))
                .filter(signal -> signal != null && !AiDependencyAnalyzer.STATUS_NO_SIGNAL.equals(signal.getStatus()))
                .toList();
    }

    private List<String> evidenceRefsForAiDependencyLoop(
            List<StudentAbilityProfileResponse.AiDependencySignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        return signals.stream()
                .filter(Objects::nonNull)
                .flatMap(signal -> {
                    List<String> refs = new ArrayList<>();
                    refs.add("ai_dependency:" + signal.getStatus());
                    if (signal.getDependencyEvidenceRefs() != null) {
                        refs.addAll(signal.getDependencyEvidenceRefs());
                    }
                    return refs.stream();
                })
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private List<StudentAbilityProfileResponse.MasteryGrowthSignal> buildMasteryGrowthSignals(
            List<Submission> submissions,
            List<SubmissionAnalysis> analyses) {
        if (masteryGrowthAnalyzer == null || submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionAnalysis> analysesBySubmission = analyses == null ? Map.of() : analyses.stream()
                .filter(analysis -> analysis != null && analysis.getSubmissionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionAnalysis::getSubmissionId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return submissions.stream()
                .filter(submission -> submission.getStudentProfileId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        Submission::getStudentProfileId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .values()
                .stream()
                .map(studentSubmissions -> masteryGrowthAnalyzer.analyze(studentSubmissions, analysesBySubmission))
                .filter(signal -> signal != null && !MasteryGrowthAnalyzer.STATUS_NO_SIGNAL.equals(signal.getStatus()))
                .toList();
    }

    private List<String> evidenceRefsForMasteryGrowthLoop(
            List<StudentAbilityProfileResponse.MasteryGrowthSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        return signals.stream()
                .filter(Objects::nonNull)
                .flatMap(signal -> {
                    List<String> refs = new ArrayList<>();
                    refs.add("mastery_growth:" + signal.getStatus());
                    if (signal.getEvidenceRefs() != null) {
                        refs.addAll(signal.getEvidenceRefs());
                    }
                    return refs.stream();
                })
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private List<StudentAbilityProfileResponse.TeachingActionDecision> buildTeachingActionDecisions(
            List<Submission> submissions,
            List<SubmissionAnalysis> analyses,
            Map<Long, com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse> coachInteractions,
            List<CoachPrompt> prompts,
            Long assignmentId) {
        if (teachingActionOrchestrator == null || submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionAnalysis> analysesBySubmission = analyses == null ? Map.of() : analyses.stream()
                .filter(analysis -> analysis != null && analysis.getSubmissionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionAnalysis::getSubmissionId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, List<CoachPrompt>> promptsByStudent = prompts == null ? Map.of() : prompts.stream()
                .filter(prompt -> prompt != null && prompt.getStudentProfileId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        CoachPrompt::getStudentProfileId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        return submissions.stream()
                .filter(submission -> submission.getStudentProfileId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        Submission::getStudentProfileId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<Submission> studentSubmissions = entry.getValue();
                    List<CoachPrompt> studentPrompts = promptsByStudent.getOrDefault(entry.getKey(), List.of());
                    StudentTrajectoryResponse.PostAcTransferSignal postAcTransferSignal =
                            postAcTransferAnalyzer == null
                                    ? null
                                    : postAcTransferAnalyzer.summarize(postAcTransferAnalyzer.analyzeTasks(
                                                    studentSubmissions,
                                                    analysesBySubmission,
                                                    coachInteractions,
                                                    Map.of()
                                            )
                                            .values()
                                            .stream()
                                            .toList());
                    StudentAbilityProfileResponse.RecurringMisconceptionSignal recurringMisconceptionSignal =
                            recurringMisconceptionAnalyzer == null
                                    ? null
                                    : recurringMisconceptionAnalyzer.analyze(studentSubmissions, analysesBySubmission);
                    StudentAbilityProfileResponse.SelfExplanationMasterySignal selfExplanationSignal =
                            selfExplanationMasteryAnalyzer == null ? null : selfExplanationMasteryAnalyzer.analyze(studentPrompts);
                    StudentAbilityProfileResponse.AiDependencySignal aiDependencySignal =
                            aiDependencyAnalyzer == null
                                    ? null
                                    : aiDependencyAnalyzer.analyze(
                                            studentSubmissions,
                                            studentPrompts,
                                            recommendationEventRepository == null
                                                    ? List.of()
                                                    : recommendationEventRepository.findByStudentProfileIdOrderByCreatedAtDesc(entry.getKey())
                                                    .stream()
                                                    .filter(event -> assignmentId == null || Objects.equals(event.getAssignmentId(), assignmentId))
                                                    .toList()
                                    );
                    StudentAbilityProfileResponse.MasteryGrowthSignal masteryGrowthSignal =
                            masteryGrowthAnalyzer == null ? null : masteryGrowthAnalyzer.analyze(studentSubmissions, analysesBySubmission);
                    return teachingActionOrchestrator.decide(
                            null,
                            null,
                            postAcTransferSignal,
                            recurringMisconceptionSignal,
                            selfExplanationSignal,
                            aiDependencySignal,
                            masteryGrowthSignal,
                            "继续根据最新诊断缩小失败样例。"
                    );
                })
                .toList();
    }

    private List<String> evidenceRefsForTeachingActionLoop(
            List<StudentAbilityProfileResponse.TeachingActionDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return List.of();
        }
        return decisions.stream()
                .filter(Objects::nonNull)
                .flatMap(decision -> {
                    List<String> refs = new ArrayList<>();
                    refs.add("teaching_action:" + decision.getActionType() + ":" + decision.getRiskLevel());
                    if (decision.getSourceSignals() != null) {
                        refs.addAll(decision.getSourceSignals());
                    }
                    if (decision.getEvidenceRefs() != null) {
                        refs.addAll(decision.getEvidenceRefs());
                    }
                    return refs.stream();
                })
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private AssignmentOverviewResponse.ClassTeachingStrategySignal buildClassTeachingStrategySignal(
            Long assignmentId,
            List<Submission> submissions,
            List<SubmissionAnalysis> analyses) {
        if (classTeachingStrategyAnalyzer == null) {
            return null;
        }
        List<AssignmentOverviewResponse.IssueStat> topIssues = buildStrategyTopIssues(analyses);
        List<AssignmentOverviewResponse.AbilityStat> abilityWeaknesses = buildStrategyAbilityWeaknesses(topIssues);
        AssignmentOverviewResponse.ClassTeachingStrategySignal signal =
                classTeachingStrategyAnalyzer.analyze(assignmentId, List.of(), topIssues, abilityWeaknesses, List.of());
        attachClassTeachingStrategyImpact(assignmentId, signal, submissions, analyses);
        return signal;
    }

    private void attachClassTeachingStrategyImpact(Long assignmentId,
                                                   AssignmentOverviewResponse.ClassTeachingStrategySignal signal,
                                                   List<Submission> submissions,
                                                   List<SubmissionAnalysis> analyses) {
        if (signal == null || classTeachingStrategyImpactAnalyzer == null || classReviewFeedbackService == null || assignmentId == null) {
            return;
        }
        ClassReviewFeedback feedback = classReviewFeedbackService.latestByAssignment(assignmentId)
                .stream()
                .filter(item -> Objects.equals(item.getSuggestionKey(), signal.getStrategyKey()))
                .findFirst()
                .orElse(null);
        Map<Long, SubmissionAnalysis> analysesBySubmission = analyses == null ? Map.of() : analyses.stream()
                .filter(analysis -> analysis != null && analysis.getSubmissionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionAnalysis::getSubmissionId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        signal.setImpact(classTeachingStrategyImpactAnalyzer.analyze(
                signal,
                feedback,
                submissions,
                analysesBySubmission,
                classReviewFeedbackService.evidenceTags(feedback)));
    }

    private List<AssignmentOverviewResponse.IssueStat> buildStrategyTopIssues(List<SubmissionAnalysis> analyses) {
        if (analyses == null || analyses.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (SubmissionAnalysis analysis : analyses) {
            List<String> tags = diagnosisReportReader.fineGrainedTags(analysis);
            if (tags.isEmpty()) {
                tags = diagnosisReportReader.issueTags(analysis);
            }
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                counts.put(tag, counts.getOrDefault(tag, 0L) + 1L);
            }
        }
        return counts.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> AssignmentOverviewResponse.IssueStat.builder()
                        .label(entry.getKey())
                        .count(entry.getValue())
                        .affectedStudentCount(entry.getValue())
                        .explanation(diagnosisTaxonomy.label(entry.getKey()))
                        .abilityPoint(resolveAbilityPoint(entry.getKey()))
                        .build())
                .toList();
    }

    private List<AssignmentOverviewResponse.AbilityStat> buildStrategyAbilityWeaknesses(
            List<AssignmentOverviewResponse.IssueStat> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, List<String>> tags = new LinkedHashMap<>();
        for (AssignmentOverviewResponse.IssueStat issue : issues) {
            String abilityPoint = firstNonBlank(issue.getAbilityPoint(), diagnosisTaxonomy.label(issue.getLabel()));
            counts.put(abilityPoint, counts.getOrDefault(abilityPoint, 0L) + issue.getCount());
            tags.computeIfAbsent(abilityPoint, ignored -> new ArrayList<>()).add(issue.getLabel());
        }
        return counts.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(entry -> AssignmentOverviewResponse.AbilityStat.builder()
                        .abilityPoint(entry.getKey())
                        .taskCount(1)
                        .submissionCount(entry.getValue())
                        .evidenceTags(tags.getOrDefault(entry.getKey(), List.of()).stream().distinct().limit(4).toList())
                        .build())
                .toList();
    }

    private String resolveAbilityPoint(String tagId) {
        DiagnosisTaxonomy.DiagnosisTag tag = diagnosisTaxonomy.get(tagId);
        return tag == null ? null : tag.getAbilityPoint();
    }

    private List<String> evidenceRefsForClassTeachingStrategyLoop(
            AssignmentOverviewResponse.ClassTeachingStrategySignal signal) {
        if (signal == null) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        refs.add("class_strategy:" + firstNonBlank(signal.getStrategyKey(), signal.getStatus()));
        if (signal.getEvidenceRefs() != null) {
            refs.addAll(signal.getEvidenceRefs());
        }
        if (signal.getSourceSignals() != null) {
            refs.addAll(signal.getSourceSignals());
        }
        if (signal.getImpact() != null) {
            refs.add("class_strategy_impact:" + signal.getImpact().getStatus());
            if (signal.getImpact().getFollowupSubmissionId() != null) {
                refs.add("followup_submission:" + signal.getImpact().getFollowupSubmissionId());
            }
            if (signal.getImpact().getEvidenceRefs() != null) {
                refs.addAll(signal.getImpact().getEvidenceRefs());
            }
            if (signal.getImpact().getMatchedTags() != null) {
                signal.getImpact().getMatchedTags().stream()
                        .map(tag -> "matched_tag:" + tag)
                        .forEach(refs::add);
            }
        }
        return refs.stream()
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private List<AssignmentOverviewResponse.TeacherInterventionImpact> buildTeacherInterventionImpacts(
            Long assignmentId,
            List<Submission> submissions,
            List<SubmissionAnalysis> analyses) {
        if (teacherInterventionImpactAnalyzer == null || classReviewFeedbackService == null || assignmentId == null) {
            return List.of();
        }
        Map<Long, SubmissionAnalysis> analysesBySubmission = analyses == null ? Map.of() : analyses.stream()
                .filter(analysis -> analysis != null && analysis.getSubmissionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionAnalysis::getSubmissionId,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return teacherInterventionImpactAnalyzer.analyzeFeedbacks(
                classReviewFeedbackService.latestByAssignment(assignmentId),
                submissions,
                analysesBySubmission,
                classReviewFeedbackService::evidenceTags);
    }

    private List<String> evidenceRefsForTeacherInterventionLoop(
            List<AssignmentOverviewResponse.TeacherInterventionImpact> impacts) {
        if (impacts == null || impacts.isEmpty()) {
            return List.of();
        }
        return impacts.stream()
                .filter(impact -> impact != null && impact.getFeedbackActionType() != null)
                .flatMap(impact -> {
                    List<String> refs = new ArrayList<>();
                    refs.add("teacher_intervention:" + impact.getFeedbackActionType() + ":" + impact.getStatus());
                    if (impact.getFollowupSubmissionId() != null) {
                        refs.add("followup_submission:" + impact.getFollowupSubmissionId());
                    }
                    if (impact.getEvidenceSubmissionIds() != null) {
                        impact.getEvidenceSubmissionIds().stream()
                                .filter(Objects::nonNull)
                                .map(id -> "evidence_submission:" + id)
                                .forEach(refs::add);
                    }
                    return refs.stream();
                })
                .distinct()
                .limit(5)
                .toList();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
