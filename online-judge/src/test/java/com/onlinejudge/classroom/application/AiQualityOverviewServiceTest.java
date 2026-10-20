package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.HintSafetyCheck;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.ClassReviewFeedbackRepository;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import com.onlinejudge.classroom.persistence.HintSafetyCheckRepository;
import com.onlinejudge.classroom.persistence.StudentRecommendationEventRepository;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionHistoryProjection;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import com.onlinejudge.leaderboard.persistence.ProblemSubmissionStatsProjection;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class AiQualityOverviewServiceTest {

    private final FakeAssignmentRepository assignmentRepository = new FakeAssignmentRepository();
    private final FakeSubmissionRepository submissionRepository = new FakeSubmissionRepository();
    private final FakeSubmissionAnalysisRepository analysisRepository = new FakeSubmissionAnalysisRepository();
    private final FakeTeacherDiagnosisCorrectionRepository correctionRepository = new FakeTeacherDiagnosisCorrectionRepository();
    private final FakeCoachPromptRepository coachPromptRepository = new FakeCoachPromptRepository();
    private final FakeHintSafetyCheckRepository hintSafetyCheckRepository = new FakeHintSafetyCheckRepository();
    private final FakeStudentRecommendationEventRepository recommendationEventRepository = new FakeStudentRecommendationEventRepository();
    private final FakeClassReviewFeedbackRepository classReviewFeedbackRepository = new FakeClassReviewFeedbackRepository();
    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DiagnosisReportReader diagnosisReportReader = new DiagnosisReportReader(objectMapper, taxonomy);
    private final AiQualityOverviewService service = new AiQualityOverviewService(
            assignmentRepository,
            submissionRepository,
            analysisRepository,
            correctionRepository,
            diagnosisReportReader,
            taxonomy,
            new CoachInteractionAnalyzer(coachPromptRepository, new CoachAnswerQualityAnalyzer()),
            new CoachImpactAnalyzer(diagnosisReportReader, taxonomy),
            new RecommendationEffectivenessService(recommendationEventRepository, objectMapper),
            new ClassReviewFeedbackService(classReviewFeedbackRepository, assignmentRepository, objectMapper),
            new TeacherInterventionImpactAnalyzer(diagnosisReportReader),
            new PostAcTransferAnalyzer(diagnosisReportReader, taxonomy),
            new RecurringMisconceptionAnalyzer(diagnosisReportReader, taxonomy),
            new SelfExplanationMasteryAnalyzer(new CoachAnswerQualityAnalyzer()),
            recommendationEventRepository,
            new AiDependencyAnalyzer(),
            new MasteryGrowthAnalyzer(
                    diagnosisReportReader,
                    taxonomy,
                    new AbilitySignalAnalyzer(diagnosisReportReader, taxonomy)
            ),
            new TeachingActionOrchestrator(),
            new ClassTeachingStrategyAnalyzer(),
            new ClassTeachingStrategyImpactAnalyzer(diagnosisReportReader),
            hintSafetyCheckRepository,
            new PromptSafetyIncidentAnalyzer()
    );

    @Test
    void summarizesAiQualitySignalsForAssignment() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(11L, 7L));
        submissionRepository.items.add(submission(12L, 7L));
        analysisRepository.save(analysis(11L, 0.55, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]",
                "MODEL_RUNTIME_FALLBACK", true, null, "single-call", "DIAGNOSIS_AND_ADVICE", "INSUFFICIENT_QUOTA",
                "stream", 0, 0, 0, false));
        analysisRepository.save(analysis(12L, 0.82, "HIGH", "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]",
                "MODEL_PARTIAL_COMPLETED", false, null, "single-call", "DIAGNOSIS_AND_ADVICE", "SAFETY_RISK"));
        correctionRepository.saved.add(correction(1L, 7L, 11L, "BOUNDARY_CONDITION", "OFF_BY_ONE", "IO_FORMAT", "INPUT_PARSING", true));

        var overview = service.buildOverview(7L);

        assertThat(overview.getAnalyzedSubmissionCount()).isEqualTo(2);
        assertThat(overview.getCorrectionCount()).isEqualTo(1);
        assertThat(overview.getEvalCandidateCount()).isEqualTo(1);
        assertThat(overview.getLowConfidenceCount()).isEqualTo(1);
        assertThat(overview.getHighLeakRiskCount()).isEqualTo(1);
        assertThat(overview.getModelFallbackCount()).isEqualTo(1);
        assertThat(overview.getModelPartialCount()).isEqualTo(1);
        assertThat(overview.getModelRuntimeFailureCount()).isEqualTo(1);
        assertThat(overview.getModelCompletedCount()).isZero();
        assertThat(overview.getCorrectionRate()).isEqualTo(50.0);
        assertThat(overview.getModelFallbackRate()).isEqualTo(50.0);
        assertThat(overview.getModelRuntimeFailureRate()).isEqualTo(50.0);
        assertThat(overview.getSummary()).contains("外部模型兜底");
        assertThat(overview.getQualityRiskSummary()).contains("在线效果可能没有真正使用外部大模型");
        assertThat(overview.getQualityDimensions()).extracting("dimension")
                .containsExactly(
                        "DIAGNOSIS_CONFIDENCE",
                        "EVIDENCE_GROUNDING",
                        "HINT_SAFETY",
                        "PROMPT_SAFETY_INCIDENT_LOOP",
                        "LEARNING_ACTION",
                        "MODEL_RUNTIME",
                        "TEACHER_CORRECTION",
                        "TEACHER_CALIBRATION_LOOP",
                        "COACH_UNDERSTANDING",
                        "COACH_FOLLOWUP_IMPACT_LOOP",
                        "RECOMMENDATION_LOOP",
                        "POST_AC_TRANSFER_LOOP",
                        "RECURRING_MISCONCEPTION_LOOP",
                        "SELF_EXPLANATION_MASTERY_LOOP",
                        "AI_DEPENDENCY_INDEPENDENCE_LOOP",
                        "MASTERY_GROWTH_LOOP",
                        "TEACHING_ACTION_ORCHESTRATION_LOOP",
                        "CLASS_TEACHING_STRATEGY_LOOP",
                        "TEACHER_INTERVENTION_LOOP"
                );
        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "MODEL_RUNTIME".equals(dimension.getDimension()))
                .first()
	                .satisfies(dimension -> {
	                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
	                    assertThat(dimension.getScore()).isEqualTo(25.0);
	                    assertThat(dimension.getEvidenceRefs()).contains("eval:submission:11");
	                    assertThat(dimension.getRecommendedAction()).contains("ModelScope 额度");
	                });
        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "HINT_SAFETY".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getEvidenceRefs()).contains("eval:submission:12");
                });
        assertThat(overview.getPromptSafetyIncidentSignal()).satisfies(signal -> {
            assertThat(signal.getStatus()).isEqualTo("ACTION_NEEDED");
            assertThat(signal.getHighLeakRiskCount()).isEqualTo(1);
            assertThat(signal.getSafetyDowngradeCount()).isZero();
            assertThat(signal.getEvidenceRefs()).contains("prompt_safety_source:DIAGNOSIS_HIGH_LEAK_RISK");
        });
        assertThat(overview.getRuntimeAttributionSignal()).satisfies(signal -> {
            assertThat(signal.getStatus()).isEqualTo("ACTION_NEEDED");
            assertThat(signal.getPrimaryFailureType()).isEqualTo("QUOTA_LIMIT");
            assertThat(signal.getPrimaryFailureReason()).isEqualTo("INSUFFICIENT_QUOTA");
            assertThat(signal.getPrimaryFailureStage()).isEqualTo("DIAGNOSIS_AND_ADVICE");
            assertThat(signal.getPrimaryTransportMode()).isEqualTo("stream");
            assertThat(signal.getModelPartialCount()).isEqualTo(1);
            assertThat(signal.getModelRuntimeFailureCount()).isEqualTo(1);
            assertThat(signal.getRuntimeFailureRate()).isEqualTo(50.0);
            assertThat(signal.getStreamNoContentCount()).isEqualTo(1);
            assertThat(signal.getStreamInvalidChunkCount()).isZero();
            assertThat(signal.getStreamFallbackRetryCount()).isZero();
            assertThat(signal.getRecoveryStatus()).isEqualTo("BLOCKED");
            assertThat(signal.isRecoverySmokeRecommended()).isTrue();
            assertThat(signal.getRecoverySmokeCaseId()).isEqualTo("submission:11");
            assertThat(signal.getRecoverySmokeRuntimeProfile()).isEqualTo("single-call");
            assertThat(signal.getRecoverySmokeRequiredChecks()).contains(
                    "status=MODEL_COMPLETED",
                    "fallbackUsed=false",
                    "modelHit=true",
                    "evidenceRefs present",
                    "answerLeakRisk!=HIGH",
                    "streamContentChunkCount>0"
            );
            assertThat(signal.getRecoveryCheckCount()).isEqualTo(6);
            assertThat(signal.getRecoveryPassedCheckCount()).isZero();
            assertThat(signal.getRecoveryBlockedReasonCount()).isGreaterThanOrEqualTo(4);
            assertThat(signal.getRecoveryBlockedReasons()).contains(
                    "recovery smoke pending: submission:11",
                    "submission:11: runtime failure",
                    "submission:11: model not completed",
                    "submission:11: stream content chunk missing",
                    "submission:11: INSUFFICIENT_QUOTA"
            );
            assertThat(signal.getQualityComparabilityStatus()).isEqualTo("NOT_COMPARABLE");
            assertThat(signal.getQualityComparabilityReasonCount()).isGreaterThanOrEqualTo(4);
            assertThat(signal.getQualityComparabilityReasons()).contains(
                    "current recovery blocked",
                    "model hits missing; fallback hits present",
                    "partial model outputs present",
                    "runtime failures still present"
            );
            assertThat(signal.getQualityComparabilitySummary()).contains("不能代表真实外部模型质量");
            assertThat(signal.getSummary()).contains("额度不足", "stream 请求已发出", "content chunk", "BLOCKED");
            assertThat(signal.getRecommendedAction()).contains("ModelScope 额度", "content chunk", "recovery smoke");
            assertThat(signal.getEvidenceRefs()).contains("eval:submission:11");
        });
        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "PROMPT_SAFETY_INCIDENT_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("高泄题风险诊断");
                    assertThat(dimension.getEvidenceRefs()).contains("prompt_safety_source:DIAGNOSIS_HIGH_LEAK_RISK");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .containsSubsequence("MODEL_RUNTIME", "HINT_SAFETY", "PROMPT_SAFETY_INCIDENT_LOOP");
        assertThat(overview.getImprovementPriorities()).first()
                .satisfies(priority -> {
                    assertThat(priority.getPriority()).isEqualTo("P1");
                    assertThat(priority.getDimension()).isEqualTo("MODEL_RUNTIME");
                    assertThat(priority.getSeverity()).isEqualTo("ACTION_NEEDED");
                    assertThat(priority.getRecommendedAction()).contains("ModelScope 额度");
                });
        assertThat(overview.getEvalReadiness())
                .satisfies(readiness -> {
                    assertThat(readiness.getStatus()).isEqualTo("READY");
                    assertThat(readiness.getCandidateCount()).isEqualTo(1);
                    assertThat(readiness.getModelQualityBaselineStatus()).isEqualTo("BLOCKED");
                    assertThat(readiness.getModelQualityBaselineReasons()).contains(
                            "current recovery blocked",
                            "model hits missing; fallback hits present"
                    );
                    assertThat(readiness.getModelQualityBaselineSummary()).contains("不可代表真实外部模型质量");
                    assertThat(readiness.getPriorityTags()).first()
                            .satisfies(tag -> assertThat(tag.getCorrectedTag()).isEqualTo("INPUT_PARSING"));
                    assertThat(readiness.getEvidenceRefs()).contains("eval_candidates:1", "model_quality_baseline:BLOCKED");
                });
        assertThat(overview.getCorrectedTags()).first()
                .satisfies(tag -> {
                    assertThat(tag.getOriginalTag()).isEqualTo("OFF_BY_ONE");
                    assertThat(tag.getCorrectedTag()).isEqualTo("INPUT_PARSING");
                    assertThat(tag.getOriginalLabel()).isEqualTo("差一位错误");
                    assertThat(tag.getCorrectedLabel()).isEqualTo("输入读取理解");
                });
	    }

    @Test
    void runtimeAttributionSignalClassifiesRateLimitFailures() {
        assignmentRepository.items.put(19L, Assignment.builder().id(19L).title("限流作业").build());
        submissionRepository.items.add(submission(191L, 19L));
        submissionRepository.items.add(submission(192L, 19L));
        analysisRepository.save(analysis(191L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]",
                "MODEL_RUNTIME_FALLBACK", true, null, "single-call", "DIAGNOSIS_AND_ADVICE", "RATE_LIMITED"));
        analysisRepository.save(analysis(192L, 0.81, "LOW", "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]",
                "MODEL_COMPLETED", false));

        var overview = service.buildOverview(19L);

        assertThat(overview.getRuntimeAttributionSignal()).satisfies(signal -> {
            assertThat(signal.getPrimaryFailureType()).isEqualTo("QUOTA_LIMIT");
            assertThat(signal.getPrimaryFailureReason()).isEqualTo("RATE_LIMITED");
            assertThat(signal.getPrimaryTransportMode()).isEmpty();
            assertThat(signal.getStreamNoContentCount()).isZero();
            assertThat(signal.getStreamInvalidChunkCount()).isZero();
            assertThat(signal.getStreamFallbackRetryCount()).isZero();
            assertThat(signal.getSummary()).contains("额度不足", "真实外部模型参与率受限");
            assertThat(signal.getRecommendedAction()).contains("ModelScope 额度", "计费状态");
            assertThat(signal.getEvidenceRefs()).contains("eval:submission:191");
        });
        assertThat(overview.getImprovementPriorities()).first()
                .satisfies(priority -> {
                    assertThat(priority.getDimension()).isEqualTo("MODEL_RUNTIME");
                    assertThat(priority.getRecommendedAction()).contains("ModelScope 额度");
                });
    }

    @Test
    void runtimeAttributionSignalClassifiesOutputTruncatedFailures() {
        assignmentRepository.items.put(20L, Assignment.builder().id(20L).title("输出截断作业").build());
        submissionRepository.items.add(submission(201L, 20L));
        submissionRepository.items.add(submission(202L, 20L));
        analysisRepository.save(analysis(201L, 0.76, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]",
                "MODEL_RUNTIME_FALLBACK", true, null, "single-call", "DIAGNOSIS_AND_ADVICE", "OUTPUT_TRUNCATED",
                "stream", 241, 77, 0, "length", false));
        analysisRepository.save(analysis(202L, 0.81, "LOW", "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]",
                "MODEL_COMPLETED", false));

        var overview = service.buildOverview(20L);

        assertThat(overview.getRuntimeAttributionSignal()).satisfies(signal -> {
            assertThat(signal.getPrimaryFailureType()).isEqualTo("OUTPUT_TRUNCATED");
            assertThat(signal.getPrimaryFailureReason()).isEqualTo("OUTPUT_TRUNCATED");
            assertThat(signal.getPrimaryFailureStage()).isEqualTo("DIAGNOSIS_AND_ADVICE");
            assertThat(signal.getPrimaryTransportMode()).isEqualTo("stream");
            assertThat(signal.getModelRuntimeFailureCount()).isEqualTo(1);
            assertThat(signal.getModelCompletedCount()).isEqualTo(1);
            assertThat(signal.getRuntimeFailureRate()).isEqualTo(50.0);
            assertThat(signal.getStreamNoContentCount()).isZero();
            assertThat(signal.getStreamInvalidChunkCount()).isZero();
            assertThat(signal.getStreamFallbackRetryCount()).isZero();
            assertThat(signal.getSummary()).contains("输出被 token 预算截断", "max tokens", "schema");
            assertThat(signal.getRecommendedAction()).contains("输出 token 预算", "JSON schema", "finish_reason=length", "max_tokens");
            assertThat(signal.getRecommendedAction()).doesNotContain("额度", "provider");
            assertThat(signal.getEvidenceRefs()).contains("eval:submission:201");
        });
        assertThat(overview.getImprovementPriorities()).first()
                .satisfies(priority -> {
                    assertThat(priority.getDimension()).isEqualTo("MODEL_RUNTIME");
                    assertThat(priority.getRecommendedAction()).contains("输出 token 预算", "max_tokens");
                    assertThat(priority.getRecommendedAction()).doesNotContain("额度", "provider");
                });
    }

    @Test
    void runtimeAttributionSignalMarksRecoveryRecoveredWhenLaterModelSamplePassesChecks() {
        assignmentRepository.items.put(21L, Assignment.builder().id(21L).title("恢复作业").build());
        submissionRepository.items.add(submission(211L, 21L));
        submissionRepository.items.add(submission(212L, 21L));
        analysisRepository.save(analysis(211L, 0.58, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]",
                "MODEL_RUNTIME_FALLBACK", true, null, "low-latency", "DIAGNOSIS_AND_ADVICE", "RATE_LIMITED",
                "stream", 0, 0, 0, false));
        analysisRepository.save(analysis(212L, 0.86, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]",
                "MODEL_COMPLETED", false, null, "low-latency", "", "",
                "stream", 12, 5, 0, false));

        var overview = service.buildOverview(21L);

        assertThat(overview.getRuntimeAttributionSignal()).satisfies(signal -> {
            assertThat(signal.getPrimaryFailureType()).isEqualTo("QUOTA_LIMIT");
            assertThat(signal.getRecoveryStatus()).isEqualTo("RECOVERED");
            assertThat(signal.isRecoverySmokeRecommended()).isFalse();
            assertThat(signal.getRecoverySmokeCaseId()).isEmpty();
            assertThat(signal.getRecoveryBlockedReasons()).isEmpty();
            assertThat(signal.getRecoverySmokeRequiredChecks()).contains("streamContentChunkCount>0");
            assertThat(signal.getRecoveryPassedChecks()).contains(
                    "status=MODEL_COMPLETED",
                    "fallbackUsed=false",
                    "modelHit=true",
                    "evidenceRefs present",
                    "answerLeakRisk!=HIGH",
                    "streamContentChunkCount>0",
                    "recoveryEvidenceRef=eval:submission:212"
            );
            assertThat(signal.getQualityComparabilityStatus()).isEqualTo("COMPARABLE");
            assertThat(signal.getQualityComparabilityReasonCount()).isZero();
            assertThat(signal.getQualityComparabilityReasons()).isEmpty();
            assertThat(signal.getQualityComparabilitySummary()).contains("可支持小批量模型质量对比");
            assertThat(signal.getSummary()).contains("RECOVERED", "恢复证据");
            assertThat(signal.getRecommendedAction()).contains("已有恢复证据", "无 fallback");
        });
        assertThat(overview.getEvalReadiness()).satisfies(readiness -> {
            assertThat(readiness.getModelQualityBaselineStatus()).isEqualTo("READY");
            assertThat(readiness.getModelQualityBaselineReasonCount()).isZero();
            assertThat(readiness.getModelQualityBaselineReasons()).isEmpty();
            assertThat(readiness.getModelQualityBaselineSummary()).contains("可沉淀为小批量模型质量 baseline");
        });
    }

    @Test
    void runtimeAttributionSignalMarksRecoveryNotApplicableForHealthyAssignments() {
        assignmentRepository.items.put(22L, Assignment.builder().id(22L).title("健康作业").build());
        submissionRepository.items.add(submission(221L, 22L));
        submissionRepository.items.add(submission(222L, 22L));
        analysisRepository.save(analysis(221L, 0.82, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]",
                "MODEL_COMPLETED", false, null, "low-latency", "", "",
                "stream", 20, 6, 0, false));
        analysisRepository.save(analysis(222L, 0.84, "LOW", "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]",
                "MODEL_COMPLETED", false));

        var overview = service.buildOverview(22L);

        assertThat(overview.getRuntimeAttributionSignal()).satisfies(signal -> {
            assertThat(signal.getStatus()).isEqualTo("HEALTHY");
            assertThat(signal.getPrimaryFailureType()).isEqualTo("NONE");
            assertThat(signal.getRecoveryStatus()).isEqualTo("NOT_APPLICABLE");
            assertThat(signal.isRecoverySmokeRecommended()).isFalse();
            assertThat(signal.getRecoveryCheckCount()).isZero();
            assertThat(signal.getRecoveryPassedChecks()).isEmpty();
            assertThat(signal.getRecoveryBlockedReasons()).isEmpty();
            assertThat(signal.getQualityComparabilityStatus()).isEqualTo("NOT_APPLICABLE");
            assertThat(signal.getQualityComparabilityReasonCount()).isZero();
            assertThat(signal.getQualityComparabilityReasons()).isEmpty();
            assertThat(signal.getQualityComparabilitySummary()).contains("没有需要解释");
            assertThat(signal.getSummary()).doesNotContain("BLOCKED", "RECOVERED");
        });
    }

    @Test
    void runtimeAttributionSignalMarksQualityComparabilityPartialForPartialOnlyAssignments() {
        assignmentRepository.items.put(23L, Assignment.builder().id(23L).title("部分完成作业").build());
        submissionRepository.items.add(submission(231L, 23L));
        submissionRepository.items.add(submission(232L, 23L));
        analysisRepository.save(analysis(231L, 0.83, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]",
                "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(232L, 0.80, "LOW", "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]",
                "MODEL_PARTIAL_COMPLETED", false, null, "low-latency", "DIAGNOSIS_AND_ADVICE", "SAFETY_RISK"));

        var overview = service.buildOverview(23L);

        assertThat(overview.getRuntimeAttributionSignal()).satisfies(signal -> {
            assertThat(signal.getStatus()).isEqualTo("WATCH");
            assertThat(signal.getPrimaryFailureType()).isEqualTo("PARTIAL_COMPLETION");
            assertThat(signal.getRecoveryStatus()).isEqualTo("RECOVERED");
            assertThat(signal.getQualityComparabilityStatus()).isEqualTo("PARTIAL");
            assertThat(signal.getQualityComparabilityReasons()).contains("partial model outputs present");
            assertThat(signal.getQualityComparabilitySummary()).contains("部分真实模型证据");
        });
        assertThat(overview.getEvalReadiness()).satisfies(readiness -> {
            assertThat(readiness.getModelQualityBaselineStatus()).isEqualTo("PARTIAL");
            assertThat(readiness.getModelQualityBaselineReasons()).contains("partial model outputs present");
            assertThat(readiness.getModelQualityBaselineSummary()).contains("部分真实外部模型质量证据");
        });
    }

    @Test
    void teacherCalibrationLoopReportsAppliedAndConflictSignals() {
        assignmentRepository.items.put(8L, Assignment.builder().id(8L).title("作业").build());
        submissionRepository.items.add(submission(21L, 8L));
        submissionRepository.items.add(submission(22L, 8L));
        analysisRepository.save(analysisWithTeacherCalibration(
                21L,
                "SUPPORTED",
                "[\"IO_FORMAT\"]",
                "[\"INPUT_PARSING\"]",
                "memory:teacher_calibration:input_parsing"));
        analysisRepository.save(analysisWithTeacherCalibration(
                22L,
                "CONFLICT_NEEDS_REVIEW",
                "[\"LOOP_BOUNDARY\"]",
                "[\"OFF_BY_ONE\"]",
                "memory:teacher_calibration:input_parsing"));
        correctionRepository.saved.add(correction(1L, 8L, 21L, "LOOP_BOUNDARY", "OFF_BY_ONE", "IO_FORMAT", "INPUT_PARSING", true));

        var overview = service.buildOverview(8L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "TEACHER_CALIBRATION_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("1 条诊断与教师校准冲突");
                    assertThat(dimension.getEvidenceRefs()).contains("memory:teacher_calibration:input_parsing");
                    assertThat(dimension.getRecommendedAction()).contains("CONFLICT_NEEDS_REVIEW");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("TEACHER_CALIBRATION_LOOP");
    }

    @Test
    void flagsContradictedLearningActionAsQualityPriority() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(21L, 7L));
        analysisRepository.save(analysis(
                21L,
                0.78,
                "LOW",
                "[\"BOUNDARY_CONDITION\"]",
                "[\"OFF_BY_ONE\"]",
                "MODEL_COMPLETED",
                false,
                "CONTRADICTED"
        ));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "LEARNING_ACTION".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("学习动作被后续证据反驳");
                    assertThat(dimension.getRecommendedAction()).contains("降低提示粒度");
                    assertThat(dimension.getEvidenceRefs()).contains("eval:submission:21");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("LEARNING_ACTION");
    }

    @Test
    void includesCoachUnderstandingQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(31L, 7L));
        submissionRepository.items.add(submission(32L, 7L));
        analysisRepository.save(analysis(31L, 0.78, "LOW", "[\"TIME_COMPLEXITY\"]", "[\"BRUTE_FORCE_LIMIT\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(32L, 0.81, "LOW", "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        coachPromptRepository.saved.add(coachPrompt(1L, 31L, "最大 n=200000 时双重循环次数大约是 n*n，会超时"));
        coachPromptRepository.saved.add(coachPrompt(2L, 32L, "我知道了，我改一下"));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "COACH_UNDERSTANDING".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("WATCH");
                    assertThat(dimension.getScore()).isEqualTo(50.0);
                    assertThat(dimension.getSummary()).contains("1/2");
                    assertThat(dimension.getEvidenceRefs()).contains("coach:submission:31:VERIFICATION_READY");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("COACH_UNDERSTANDING");
    }

    @Test
    void aggregatesPromptSafetyIncidentsFromDowngradesAndCoachRisk() {
        assignmentRepository.items.put(15L, Assignment.builder().id(15L).title("安全作业").build());
        submissionRepository.items.add(submission(181L, 15L));
        submissionRepository.items.add(submission(182L, 15L));
        analysisRepository.save(analysis(181L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(182L, 0.82, "LOW", "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]", "MODEL_COMPLETED", false));
        hintSafetyCheckRepository.saved.add(hintSafetyCheck(1L, 181L, "MEDIUM"));
        hintSafetyCheckRepository.saved.add(hintSafetyCheck(2L, 182L, "LOW"));
        coachPromptRepository.saved.add(coachPrompt(181L, 181L, "答案如下，直接改成完整代码里的边界判断", 1L));

        var overview = service.buildOverview(15L);

        assertThat(overview.getPromptSafetyIncidentSignal()).satisfies(signal -> {
            assertThat(signal.getStatus()).isEqualTo("ACTION_NEEDED");
            assertThat(signal.getPrimaryRiskSource()).isEqualTo("HINT_SAFETY_CHECK");
            assertThat(signal.getTotalIncidentCount()).isEqualTo(2);
            assertThat(signal.getHighLeakRiskCount()).isZero();
            assertThat(signal.getSafetyDowngradeCount()).isEqualTo(1);
            assertThat(signal.getCoachSafetyRiskCount()).isEqualTo(1);
            assertThat(signal.getSummary()).contains("提示被安全降级");
            assertThat(signal.getRecommendedAction()).contains("提示安全降级原因");
            assertThat(signal.getEvidenceRefs()).contains(
                    "hint_safety_check:1",
                    "hint_safety_submission:181",
                    "coach_safety:submission:181"
            );
            assertThat(signal.getEvidenceRefs()).doesNotContain("hint_safety_check:2");
        });
        assertThat(overview.getHighLeakRiskCount()).isZero();
        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "HINT_SAFETY".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> assertThat(dimension.getStatus()).isEqualTo("HEALTHY"));
        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "PROMPT_SAFETY_INCIDENT_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getEvidenceRefs()).contains("hint_safety_check:1");
                    assertThat(dimension.getRecommendedAction()).contains("安全回归 fixture");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("PROMPT_SAFETY_INCIDENT_LOOP");
    }

    @Test
    void includesCoachFollowupImpactLoopQualityDimension() {
        assignmentRepository.items.put(12L, Assignment.builder().id(12L).title("作业").build());
        submissionRepository.items.add(submission(171L, 12L, 1L, 201L,
                Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(172L, 12L, 1L, 201L,
                Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 13, 0)));
        analysisRepository.save(analysis(171L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(172L, 0.79, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        coachPromptRepository.saved.add(coachPrompt(171L, 171L, "最小样例 n=1 时循环应该执行一次", 1L));

        var overview = service.buildOverview(12L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "COACH_FOLLOWUP_IMPACT_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getScore()).isEqualTo(0.0);
                    assertThat(dimension.getSummary()).contains("仍有 1 个样本卡在同类错因");
                    assertThat(dimension.getEvidenceRefs()).contains(
                            "coach_impact:SAME_ISSUE:submission:171",
                            "followup_submission:172"
                    );
                    assertThat(dimension.getRecommendedAction()).contains("最小失败样例");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("COACH_FOLLOWUP_IMPACT_LOOP");
    }

    @Test
    void includesRecommendationLoopQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(41L, 7L));
        analysisRepository.save(analysis(41L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        recommendationEventRepository.items.add(recommendationEvent(
                "rec-step-down",
                7L,
                StudentRecommendationEventService.EVENT_EXPOSED,
                null,
                null
        ));
        recommendationEventRepository.items.add(recommendationEvent(
                "rec-step-down",
                7L,
                StudentRecommendationEventService.EVENT_SUBMITTED,
                Submission.Verdict.WRONG_ANSWER.name(),
                "OFF_BY_ONE"
        ));
        recommendationEventRepository.items.add(recommendationEvent(
                "rec-other-assignment",
                8L,
                StudentRecommendationEventService.EVENT_SUBMITTED,
                Submission.Verdict.WRONG_ANSWER.name(),
                "OFF_BY_ONE"
        ));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "RECOMMENDATION_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("学习信号未改善");
                    assertThat(dimension.getEvidenceRefs()).contains(
                            "recommendation:rec-step-down",
                            "recommendation-outcome:UNRESOLVED_SAME_FOCUS"
                    );
                    assertThat(dimension.getEvidenceRefs()).doesNotContain("recommendation:rec-other-assignment");
                    assertThat(dimension.getRecommendedAction()).contains("教师");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("RECOMMENDATION_LOOP");
    }

    @Test
    void includesTeacherInterventionLoopQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(51L, 7L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(52L, 7L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 13, 0)));
        analysisRepository.save(analysis(51L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(52L, 0.76, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        classReviewFeedbackRepository.saved.add(classReviewFeedback(
                7L,
                "review:7:ability:循环与边界:101:OFF_BY_ONE",
                ClassReviewFeedbackService.ACTION_ACCEPTED,
                101L,
                "[\"OFF_BY_ONE\"]",
                LocalDateTime.of(2026, 5, 18, 12, 0)
        ));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "TEACHER_INTERVENTION_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("仍有 1 条复盘建议命中同类错因");
                    assertThat(dimension.getRecommendedAction()).contains("降低课堂复盘颗粒度");
                    assertThat(dimension.getEvidenceRefs()).contains("teacher_intervention:ACCEPTED:STILL_STUCK");
                    assertThat(dimension.getEvidenceRefs()).contains("followup_submission:52");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("TEACHER_INTERVENTION_LOOP");
    }

    @Test
    void evalReadinessUsesTeacherInterventionImpactCandidates() {
        assignmentRepository.items.put(10L, Assignment.builder().id(10L).title("作业").build());
        submissionRepository.items.add(submission(151L, 10L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(152L, 10L, Submission.Verdict.ACCEPTED, LocalDateTime.of(2026, 5, 18, 13, 0)));
        analysisRepository.save(analysis(151L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        classReviewFeedbackRepository.saved.add(classReviewFeedback(
                10L,
                "review:10:ability:循环与边界:101:OFF_BY_ONE",
                ClassReviewFeedbackService.ACTION_ACCEPTED,
                101L,
                "[\"OFF_BY_ONE\"]",
                LocalDateTime.of(2026, 5, 18, 12, 0)
        ));

        var overview = service.buildOverview(10L);

        assertThat(overview.getEvalReadiness()).satisfies(readiness -> {
            assertThat(readiness.getStatus()).isEqualTo("READY");
            assertThat(readiness.getCandidateCount()).isZero();
            assertThat(readiness.getInterventionCandidateCount()).isEqualTo(1);
            assertThat(readiness.getInterventionImprovedCount()).isEqualTo(1);
            assertThat(readiness.getSummary()).contains("课堂介入");
            assertThat(readiness.getRecommendedAction()).contains("课堂介入 fixture");
            assertThat(readiness.getEvidenceRefs()).contains("teacher_intervention_eval:IMPROVED");
            assertThat(readiness.getEvidenceRefs()).contains("followup_submission:152");
        });
    }

    @Test
    void evalReadinessTreatsWaitingInterventionAsPartial() {
        assignmentRepository.items.put(11L, Assignment.builder().id(11L).title("作业").build());
        submissionRepository.items.add(submission(161L, 11L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        analysisRepository.save(analysis(161L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        classReviewFeedbackRepository.saved.add(classReviewFeedback(
                11L,
                "review:11:ability:循环与边界:101:OFF_BY_ONE",
                ClassReviewFeedbackService.ACTION_ACCEPTED,
                101L,
                "[\"OFF_BY_ONE\"]",
                LocalDateTime.of(2026, 5, 18, 12, 0)
        ));

        var overview = service.buildOverview(11L);

        assertThat(overview.getEvalReadiness()).satisfies(readiness -> {
            assertThat(readiness.getStatus()).isEqualTo("PARTIAL");
            assertThat(readiness.getInterventionCandidateCount()).isZero();
            assertThat(readiness.getInterventionWaitingFollowupCount()).isEqualTo(1);
            assertThat(readiness.getSummary()).contains("等待后续提交证据");
            assertThat(readiness.getRecommendedAction()).contains("等待后续提交");
        });
    }

    @Test
    void includesPostAcTransferLoopQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(61L, 7L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(62L, 7L, 1L, 101L, Submission.Verdict.ACCEPTED, LocalDateTime.of(2026, 5, 18, 10, 12)));
        analysisRepository.save(analysis(61L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "POST_AC_TRANSFER_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("已通过任务缺少复盘迁移证据");
                    assertThat(dimension.getEvidenceRefs()).contains("post_ac_transfer:REFLECTION_NEEDED:problem:101");
                    assertThat(dimension.getRecommendedAction()).contains("刚 AC");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("POST_AC_TRANSFER_LOOP");
    }

    @Test
    void includesSelfExplanationMasteryLoopQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(81L, 7L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(82L, 7L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 12)));
        analysisRepository.save(analysis(81L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(82L, 0.79, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        coachPromptRepository.saved.add(coachPrompt(81L, 81L, "知道了，我改一下", 1L));
        coachPromptRepository.saved.add(coachPrompt(82L, 82L, "懂了，我试试", 1L));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "SELF_EXPLANATION_MASTERY_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("WATCH");
                    assertThat(dimension.getSummary()).contains("自解释证据不足");
                    assertThat(dimension.getEvidenceRefs()).contains("self_explanation:NEEDS_COACHING");
                    assertThat(dimension.getRecommendedAction()).contains("SELF_EXPLANATION_PRACTICE");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("SELF_EXPLANATION_MASTERY_LOOP");
    }

    @Test
    void includesAiDependencyIndependenceLoopQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(91L, 7L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(92L, 7L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 12)));
        analysisRepository.save(analysis(91L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(92L, 0.79, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        coachPromptRepository.saved.add(coachPrompt(91L, 91L, "再给一个提示", 1L));
        coachPromptRepository.saved.add(coachPrompt(92L, 92L, "我还是不会", 1L));
        recommendationEventRepository.items.add(recommendationEvent("dep-1", 7L, 1L, 91L, StudentRecommendationEventService.EVENT_SUBMITTED, Submission.Verdict.WRONG_ANSWER.name(), "OFF_BY_ONE"));
        recommendationEventRepository.items.add(recommendationEvent("dep-2", 7L, 1L, 92L, StudentRecommendationEventService.EVENT_SUBMITTED, Submission.Verdict.WRONG_ANSWER.name(), "OFF_BY_ONE"));
        recommendationEventRepository.items.add(recommendationEvent("dep-3", 7L, 1L, null, StudentRecommendationEventService.EVENT_CLICKED, null, null));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "AI_DEPENDENCY_INDEPENDENCE_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("支架后仍未改善");
                    assertThat(dimension.getEvidenceRefs()).contains("ai_dependency:DEPENDENCY_RISK");
                    assertThat(dimension.getRecommendedAction()).contains("INDEPENDENT_ATTEMPT");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("AI_DEPENDENCY_INDEPENDENCE_LOOP");
    }

    @Test
    void includesRecurringMisconceptionLoopQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(71L, 7L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(72L, 7L, 1L, 102L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 12)));
        analysisRepository.save(analysis(71L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(72L, 0.79, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "RECURRING_MISCONCEPTION_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("WATCH");
                    assertThat(dimension.getSummary()).contains("跨题或跨作业复发误区");
                    assertThat(dimension.getEvidenceRefs()).contains("recurring_misconception:RECURRING:OFF_BY_ONE");
                    assertThat(dimension.getEvidenceRefs()).contains("recurring-misconception:submission:72");
                    assertThat(dimension.getRecommendedAction()).contains("MISCONCEPTION_REPAIR");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("RECURRING_MISCONCEPTION_LOOP");
    }

    @Test
    void includesMasteryGrowthLoopQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(101L, 7L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(102L, 7L, 1L, 102L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 12)));
        submissionRepository.items.add(submission(103L, 7L, 1L, 103L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 24)));
        analysisRepository.save(analysis(101L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(102L, 0.79, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(103L, 0.80, "LOW", "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "MASTERY_GROWTH_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("螺旋复习");
                    assertThat(dimension.getEvidenceRefs()).contains("mastery_growth:SPIRAL_REVIEW_NEEDED");
                    assertThat(dimension.getRecommendedAction()).contains("MASTERY_SPIRAL_REVIEW");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("MASTERY_GROWTH_LOOP");
    }

    @Test
    void includesTeachingActionOrchestrationLoopQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(111L, 7L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(112L, 7L, 1L, 102L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 12)));
        submissionRepository.items.add(submission(113L, 7L, 1L, 103L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 24)));
        analysisRepository.save(analysis(111L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(112L, 0.79, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(113L, 0.80, "LOW", "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "TEACHING_ACTION_ORCHESTRATION_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("高风险教学动作");
                    assertThat(dimension.getEvidenceRefs()).contains("teaching_action:SPIRAL_REVIEW:HIGH");
                    assertThat(dimension.getEvidenceRefs()).contains("mastery_growth:SPIRAL_REVIEW_NEEDED");
                    assertThat(dimension.getRecommendedAction()).contains("TEACHING_ACTION");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("TEACHING_ACTION_ORCHESTRATION_LOOP");
    }

    @Test
    void includesClassTeachingStrategyLoopQualityDimension() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("作业").build());
        submissionRepository.items.add(submission(121L, 7L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(122L, 7L, 2L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 10)));
        analysisRepository.save(analysis(121L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(122L, 0.80, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));

        var overview = service.buildOverview(7L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "CLASS_TEACHING_STRATEGY_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("WATCH");
                    assertThat(dimension.getSummary()).contains("缺少教师执行反馈");
                    assertThat(dimension.getEvidenceRefs()).contains("class_strategy:strategy:7:whole-class-mini-lesson:off-by-one");
                    assertThat(dimension.getRecommendedAction()).contains("采纳、调整或忽略");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("CLASS_TEACHING_STRATEGY_LOOP");
    }

    @Test
    void classTeachingStrategyLoopUsesImpactWhenStudentsRemainStuck() {
        assignmentRepository.items.put(9L, Assignment.builder().id(9L).title("作业").build());
        submissionRepository.items.add(submission(131L, 9L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(132L, 9L, 2L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 10)));
        submissionRepository.items.add(submission(133L, 9L, 1L, 101L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 40)));
        analysisRepository.save(analysis(131L, 0.78, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(132L, 0.80, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(133L, 0.81, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        classReviewFeedbackRepository.saved.add(ClassReviewFeedback.builder()
                .id(31L)
                .assignmentId(9L)
                .suggestionKey("strategy:9:whole-class-mini-lesson:off-by-one")
                .targetAbility("循环与边界")
                .evidenceTags("[\"OFF_BY_ONE\"]")
                .actionType(ClassReviewFeedbackService.ACTION_ACCEPTED)
                .teacherNote("已全班讲评")
                .createdBy("teacher")
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 20))
                .build());

        var overview = service.buildOverview(9L);

        assertThat(overview.getQualityDimensions()).filteredOn(dimension -> "CLASS_TEACHING_STRATEGY_LOOP".equals(dimension.getDimension()))
                .first()
                .satisfies(dimension -> {
                    assertThat(dimension.getStatus()).isEqualTo("ACTION_NEEDED");
                    assertThat(dimension.getSummary()).contains("仍命中原策略标签");
                    assertThat(dimension.getEvidenceRefs()).contains("class_strategy_impact:STILL_STUCK");
                    assertThat(dimension.getEvidenceRefs()).contains("followup_submission:133");
                    assertThat(dimension.getRecommendedAction()).contains("更小粒度复盘");
                });
        assertThat(overview.getImprovementPriorities()).extracting("dimension")
                .contains("CLASS_TEACHING_STRATEGY_LOOP");
    }

    private Submission submission(Long id, Long assignmentId) {
        return submission(id, assignmentId, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0));
    }

    private Submission submission(Long id, Long assignmentId, Submission.Verdict verdict, LocalDateTime submittedAt) {
        return submission(id, assignmentId, null, 101L, verdict, submittedAt);
    }

    private Submission submission(Long id,
                                  Long assignmentId,
                                  Long studentProfileId,
                                  Long problemId,
                                  Submission.Verdict verdict,
                                  LocalDateTime submittedAt) {
        return Submission.builder()
                .id(id)
                .assignmentId(assignmentId)
                .studentProfileId(studentProfileId)
                .problemId(problemId)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(submittedAt)
                .build();
    }

    private SubmissionAnalysis analysis(Long submissionId,
                                        double confidence,
                                        String leakRisk,
                                        String issueTags,
                                        String fineTags,
                                        String invocationStatus,
                                        boolean fallbackUsed) {
        return analysis(submissionId, confidence, leakRisk, issueTags, fineTags, invocationStatus, fallbackUsed, null);
    }

    private SubmissionAnalysis analysis(Long submissionId,
                                        double confidence,
                                        String leakRisk,
                                        String issueTags,
                                        String fineTags,
                                        String invocationStatus,
                                        boolean fallbackUsed,
                                        String learningActionStatus) {
        return analysis(submissionId, confidence, leakRisk, issueTags, fineTags, invocationStatus, fallbackUsed,
                learningActionStatus, "single-call", "", "");
    }

    private SubmissionAnalysis analysis(Long submissionId,
                                        double confidence,
                                        String leakRisk,
                                        String issueTags,
                                        String fineTags,
                                        String invocationStatus,
                                        boolean fallbackUsed,
                                        String learningActionStatus,
                                        String runtimeMode,
                                        String failureStage,
                                        String failureReason) {
        return analysis(submissionId, confidence, leakRisk, issueTags, fineTags, invocationStatus, fallbackUsed,
                learningActionStatus, runtimeMode, failureStage, failureReason, "", 0, 0, 0, false);
    }

    private SubmissionAnalysis analysis(Long submissionId,
                                        double confidence,
                                        String leakRisk,
                                        String issueTags,
                                        String fineTags,
                                        String invocationStatus,
                                        boolean fallbackUsed,
                                        String learningActionStatus,
                                        String runtimeMode,
                                        String failureStage,
                                        String failureReason,
                                        String transportMode,
                                        int streamChunkCount,
                                        int streamContentChunkCount,
                                        int streamInvalidChunkCount,
                                        boolean streamFallbackRetryUsed) {
        return analysis(submissionId, confidence, leakRisk, issueTags, fineTags, invocationStatus, fallbackUsed,
                learningActionStatus, runtimeMode, failureStage, failureReason, transportMode, streamChunkCount,
                streamContentChunkCount, streamInvalidChunkCount, "", streamFallbackRetryUsed);
    }

    private SubmissionAnalysis analysis(Long submissionId,
                                        double confidence,
                                        String leakRisk,
                                        String issueTags,
                                        String fineTags,
                                        String invocationStatus,
                                        boolean fallbackUsed,
                                        String learningActionStatus,
                                        String runtimeMode,
                                        String failureStage,
                                        String failureReason,
                                        String transportMode,
                                        int streamChunkCount,
                                        int streamContentChunkCount,
                                        int streamInvalidChunkCount,
                                        String streamFinishReason,
                                        boolean streamFallbackRetryUsed) {
        String learningActionJson = learningActionStatus == null ? "" : """
                          ,
                          "learningActionEvidence": {
                            "expectedActionType": "TRACE_VARIABLES",
                            "executionStatus": "%s",
                            "observedEvidence": "后续提交仍停留在相同错因。",
                            "confidence": 0.74,
                            "evidenceRefs": ["eval:submission:%s", "action:%s"],
                            "nextAdjustment": "降低提示粒度，要求最小样例。"
                          }
                """.formatted(learningActionStatus, submissionId, learningActionStatus);
        String transportTelemetryJson = transportMode == null || transportMode.isBlank() ? "" : """
                            ,
                            "transportMode": "%s",
                            "streamChunkCount": %s,
                            "streamContentChunkCount": %s,
                            "streamInvalidChunkCount": %s,
                            "streamFinishReason": "%s",
                            "streamFallbackRetryUsed": %s
                """.formatted(transportMode, streamChunkCount, streamContentChunkCount, streamInvalidChunkCount,
                streamFinishReason == null ? "" : streamFinishReason, streamFallbackRetryUsed);
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("诊断")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "issueTags": %s,
                          "fineGrainedTags": %s,
                          "confidence": %s,
                          "answerLeakRisk": "%s",
                          "evidenceRefs": ["eval:submission:%s"],
                          "aiInvocation": {
                            "provider": "modelscope",
                            "model": "deepseek-ai/DeepSeek-V4-Pro",
                            "promptVersion": "external-model-runtime-v2",
                            "agentVersion": "diagnostic-agent-v2",
                            "analysisSchemaVersion": "diagnosis-v2",
                            "evidenceSchemaVersion": "diagnosis-evidence-package-v1",
                            "taxonomyVersion": "taxonomy-v1",
                            "status": "%s",
                            "fallbackUsed": %s,
                            "runtimeMode": "%s",
                            "failureStage": "%s",
                            "failureReason": "%s"
                            %s
                          }
                          %s
                        }
                        """.formatted(issueTags, fineTags, confidence, leakRisk, submissionId, invocationStatus, fallbackUsed,
                        runtimeMode, failureStage, failureReason, transportTelemetryJson, learningActionJson))
                .build();
    }

    private SubmissionAnalysis analysisWithTeacherCalibration(Long submissionId,
                                                              String status,
                                                              String issueTags,
                                                              String fineTags,
                                                              String evidenceRef) {
        String correctedIssue = "CONFLICT_NEEDS_REVIEW".equals(status) ? "IO_FORMAT" : "IO_FORMAT";
        String correctedFine = "CONFLICT_NEEDS_REVIEW".equals(status) ? "INPUT_PARSING" : "INPUT_PARSING";
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("诊断")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "issueTags": %s,
                          "fineGrainedTags": %s,
                          "confidence": 0.78,
                          "answerLeakRisk": "LOW",
                          "evidenceRefs": ["eval:submission:%s", "%s"],
                          "teacherCalibrationSignal": {
                            "status": "%s",
                            "summary": "教师校准",
                            "originalIssueTag": "LOOP_BOUNDARY",
                            "originalFineGrainedTag": "OFF_BY_ONE",
                            "correctedIssueTag": "%s",
                            "correctedFineGrainedTag": "%s",
                            "correctionCount": 2,
                            "confidenceAdjustment": -0.15,
                            "evidenceRefs": ["%s"],
                            "recommendedAction": "教师复核",
                            "needsTeacherReview": %s
                          },
                          "aiInvocation": {
                            "provider": "modelscope",
                            "model": "deepseek-ai/DeepSeek-V4-Pro",
                            "promptVersion": "external-model-runtime-v2",
                            "agentVersion": "diagnostic-agent-v2",
                            "analysisSchemaVersion": "diagnosis-v2",
                            "evidenceSchemaVersion": "diagnosis-evidence-package-v1",
                            "taxonomyVersion": "taxonomy-v1",
                            "status": "MODEL_COMPLETED",
                            "fallbackUsed": false
                          }
                        }
                        """.formatted(issueTags, fineTags, submissionId, evidenceRef, status,
                        correctedIssue, correctedFine, evidenceRef, "CONFLICT_NEEDS_REVIEW".equals(status)))
                .build();
    }

    private TeacherDiagnosisCorrection correction(Long id,
                                                  Long assignmentId,
                                                  Long submissionId,
                                                  String originalIssue,
                                                  String originalFine,
                                                  String correctedIssue,
                                                  String correctedFine,
                                                  boolean evalCandidate) {
        return TeacherDiagnosisCorrection.builder()
                .id(id)
                .assignmentId(assignmentId)
                .submissionId(submissionId)
                .originalIssueTag(originalIssue)
                .originalFineGrainedTag(originalFine)
                .correctedIssueTag(correctedIssue)
                .correctedFineGrainedTag(correctedFine)
                .evalCandidate(evalCandidate)
                .correctedAt(LocalDateTime.of(2026, 5, 18, 11, 0))
                .build();
    }

    private CoachPrompt coachPrompt(Long id, Long submissionId, String answer) {
        return coachPrompt(id, submissionId, answer, null);
    }

    private CoachPrompt coachPrompt(Long id, Long submissionId, String answer, Long studentProfileId) {
        return CoachPrompt.builder()
                .id(id)
                .submissionId(submissionId)
                .studentProfileId(studentProfileId)
                .turnIndex(1)
                .hintPolicy("L2")
                .promptType("SOCRATIC_NEXT_STEP")
                .question("请补充证据。")
                .studentAnswer(answer)
                .coachFeedback("反馈")
                .answeredAt(LocalDateTime.of(2026, 5, 18, 12, 1))
                .createdAt(LocalDateTime.of(2026, 5, 18, 12, 0))
                .build();
    }

    private HintSafetyCheck hintSafetyCheck(Long id, Long submissionId, String riskLevel) {
        return HintSafetyCheck.builder()
                .id(id)
                .submissionId(submissionId)
                .riskLevel(riskLevel)
                .blockedReasonsJson("[\"疑似直接给出答案或完整改法\"]")
                .originalHint("完整代码")
                .safeHint("请先构造一个最小样例。")
                .checkedAt(LocalDateTime.of(2026, 5, 18, 12, 30).plusMinutes(id))
                .build();
    }

    private StudentRecommendationEvent recommendationEvent(String token,
                                                           Long assignmentId,
                                                           String eventType,
                                                           String verdict,
                                                           String fineTag) {
        return recommendationEvent(token, assignmentId, 9L,
                StudentRecommendationEventService.EVENT_SUBMITTED.equals(eventType) ? 9001L : null,
                eventType, verdict, fineTag);
    }

    private StudentRecommendationEvent recommendationEvent(String token,
                                                           Long assignmentId,
                                                           Long studentProfileId,
                                                           Long followupSubmissionId,
                                                           String eventType,
                                                           String verdict,
                                                           String fineTag) {
        return StudentRecommendationEvent.builder()
                .recommendationToken(token)
                .studentProfileId(studentProfileId)
                .type("REVIEW")
                .assignmentId(assignmentId)
                .problemId(101L)
                .focusAbility("循环与边界")
                .focusTags("[\"OFF_BY_ONE\"]")
                .strategy("STEP_DOWN_REVIEW")
                .learningHypothesis("学生收到推荐后仍卡在同类错因")
                .expectedCompletionSignal("后续提交不再命中同类错因")
                .riskLevel("HIGH")
                .fallbackAction("教师介入")
                .eventType(eventType)
                .followupSubmissionId(followupSubmissionId)
                .followupVerdict(verdict)
                .followupIssueTag(fineTag == null ? null : "BOUNDARY_CONDITION")
                .followupFineGrainedTag(fineTag)
                .createdAt(LocalDateTime.of(2026, 5, 18, 13, 0).plusMinutes(recommendationEventRepository.items.size()))
                .build();
    }

    private ClassReviewFeedback classReviewFeedback(Long assignmentId,
                                                    String suggestionKey,
                                                    String actionType,
                                                    Long exampleProblemId,
                                                    String evidenceTags,
                                                    LocalDateTime createdAt) {
        return ClassReviewFeedback.builder()
                .assignmentId(assignmentId)
                .suggestionKey(suggestionKey)
                .actionType(actionType)
                .targetAbility("循环与边界")
                .exampleProblemId(exampleProblemId)
                .evidenceTags(evidenceTags)
                .teacherNote("课堂复盘后仍需观察")
                .createdBy("teacher")
                .createdAt(createdAt)
                .build();
    }

    private static class FakeAssignmentRepository extends UnsupportedJpaRepository<Assignment, Long> implements AssignmentRepository {
        private final Map<Long, Assignment> items = new LinkedHashMap<>();

        @Override
        public Optional<Assignment> findById(Long id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public boolean existsById(Long id) {
            return items.containsKey(id);
        }

        @Override
        public List<Assignment> findAllByOrderByCreatedAtDesc() {
            return List.copyOf(items.values());
        }

        @Override
        public List<Assignment> findByClassGroupIdOrderByCreatedAtDesc(Long classGroupId) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getClassGroupId(), classGroupId))
                    .toList();
        }
    }

    private static class FakeSubmissionRepository extends UnsupportedJpaRepository<Submission, Long> implements SubmissionRepository {
        private final List<Submission> items = new ArrayList<>();

        @Override
        public List<Submission> findByProblemIdOrderBySubmittedAtDesc(Long problemId) {
            return List.of();
        }

        @Override
        public List<Submission> findByProblemIdOrderBySubmittedAtAsc(Long problemId) {
            return List.of();
        }

        @Override
        public List<Submission> findByAssignmentIdOrderBySubmittedAtDesc(Long assignmentId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .toList();
        }

        @Override
        public List<Submission> findByAssignmentIdIn(Collection<Long> assignmentIds) {
            return items.stream()
                    .filter(item -> assignmentIds.contains(item.getAssignmentId()))
                    .toList();
        }

        @Override
        public List<Submission> findByStudentProfileIdInOrderBySubmittedAtDesc(Collection<Long> studentProfileIds) {
            return List.of();
        }

        @Override
        public List<Submission> findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(Long assignmentId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<Submission> findByAssignmentIdAndProblemIdAndStudentProfileIdOrderBySubmittedAtDesc(Long assignmentId, Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<Submission> findTop10ByOrderBySubmittedAtDesc() {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findHistorySummariesByProblemId(Long problemId) {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findHistorySummariesByProblemIdAndStudentProfileId(Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findHistorySummariesByAssignmentIdAndProblemIdAndStudentProfileId(Long assignmentId, Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findPublicHistorySummariesByProblemIdAndStudentProfileId(Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findAnonymousHistorySummariesByProblemId(Long problemId) {
            return List.of();
        }

        @Override
        public List<ProblemSubmissionStatsProjection> summarizeByProblem() {
            return List.of();
        }

        @Override
        public long deleteByProblemId(Long problemId) {
            return 0;
        }
    }

    private static class FakeSubmissionAnalysisRepository extends UnsupportedJpaRepository<SubmissionAnalysis, Long> implements SubmissionAnalysisRepository {
        private final Map<Long, SubmissionAnalysis> bySubmissionId = new LinkedHashMap<>();

        @Override
        public SubmissionAnalysis save(SubmissionAnalysis analysis) {
            bySubmissionId.put(analysis.getSubmissionId(), analysis);
            return analysis;
        }

        @Override
        public Optional<SubmissionAnalysis> findBySubmissionId(Long submissionId) {
            return Optional.ofNullable(bySubmissionId.get(submissionId));
        }

        @Override
        public List<SubmissionAnalysis> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return bySubmissionId.entrySet()
                    .stream()
                    .filter(entry -> submissionIds.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
        }

        @Override
        public long deleteBySubmissionId(Long submissionId) {
            return bySubmissionId.remove(submissionId) == null ? 0 : 1;
        }

        @Override
        public long deleteBySubmissionIdIn(Collection<Long> submissionIds) {
            long before = bySubmissionId.size();
            submissionIds.forEach(bySubmissionId::remove);
            return before - bySubmissionId.size();
        }
    }

    private static class FakeTeacherDiagnosisCorrectionRepository extends UnsupportedJpaRepository<TeacherDiagnosisCorrection, Long>
            implements TeacherDiagnosisCorrectionRepository {
        private final List<TeacherDiagnosisCorrection> saved = new ArrayList<>();

        @Override
        public Optional<TeacherDiagnosisCorrection> findTopBySubmissionIdOrderByCorrectedAtDesc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .reduce((left, right) -> right);
        }

        @Override
        public List<TeacherDiagnosisCorrection> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }

        @Override
        public List<TeacherDiagnosisCorrection> findByAssignmentIdIn(Collection<Long> assignmentIds) {
            return saved.stream()
                    .filter(item -> assignmentIds.contains(item.getAssignmentId()))
                    .toList();
        }

        @Override
        public List<TeacherDiagnosisCorrection> findByAssignmentIdOrderByCorrectedAtDesc(Long assignmentId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .sorted(Comparator.comparing(TeacherDiagnosisCorrection::getCorrectedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<TeacherDiagnosisCorrection> findByAssignmentIdAndEvalCandidateTrueOrderByCorrectedAtDesc(Long assignmentId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .filter(TeacherDiagnosisCorrection::isEvalCandidate)
                    .toList();
        }
    }

    private static class FakeCoachPromptRepository extends UnsupportedJpaRepository<CoachPrompt, Long> implements CoachPromptRepository {
        private final List<CoachPrompt> saved = new ArrayList<>();

        @Override
        public Optional<CoachPrompt> findTopBySubmissionIdOrderByCreatedAtDesc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .reduce((left, right) -> right);
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .toList();
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }
    }

    private static class FakeHintSafetyCheckRepository extends UnsupportedJpaRepository<HintSafetyCheck, Long>
            implements HintSafetyCheckRepository {
        private final List<HintSafetyCheck> saved = new ArrayList<>();

        @Override
        public Optional<HintSafetyCheck> findTopBySubmissionIdOrderByCheckedAtDesc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .max(Comparator.comparing(HintSafetyCheck::getCheckedAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        }

        @Override
        public List<HintSafetyCheck> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }
    }

    private static class FakeStudentRecommendationEventRepository extends UnsupportedJpaRepository<StudentRecommendationEvent, Long>
            implements StudentRecommendationEventRepository {
        private final List<StudentRecommendationEvent> items = new ArrayList<>();

        @Override
        public List<StudentRecommendationEvent> findByStudentProfileIdOrderByCreatedAtDesc(Long studentProfileId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getStudentProfileId(), studentProfileId))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<StudentRecommendationEvent> findTop500ByOrderByCreatedAtDesc() {
            return items.stream()
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .limit(500)
                    .toList();
        }

        @Override
        public List<StudentRecommendationEvent> findTop500ByAssignmentIdOrderByCreatedAtDesc(Long assignmentId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .limit(500)
                    .toList();
        }

        @Override
        public List<StudentRecommendationEvent> findByFollowupSubmissionIdAndEventTypeOrderByCreatedAtDesc(Long followupSubmissionId, String eventType) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getFollowupSubmissionId(), followupSubmissionId))
                    .filter(item -> Objects.equals(item.getEventType(), eventType))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public Optional<StudentRecommendationEvent> findTopByRecommendationTokenAndEventTypeOrderByCreatedAtDesc(String recommendationToken, String eventType) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getRecommendationToken(), recommendationToken))
                    .filter(item -> Objects.equals(item.getEventType(), eventType))
                    .max(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        }
    }

    private static class FakeClassReviewFeedbackRepository extends UnsupportedJpaRepository<ClassReviewFeedback, Long>
            implements ClassReviewFeedbackRepository {
        private final List<ClassReviewFeedback> saved = new ArrayList<>();

        @Override
        public List<ClassReviewFeedback> findByAssignmentIdOrderByCreatedAtDesc(Long assignmentId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .sorted(Comparator.comparing(ClassReviewFeedback::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<ClassReviewFeedback> findByAssignmentIdIn(Collection<Long> assignmentIds) {
            return saved.stream()
                    .filter(item -> assignmentIds.contains(item.getAssignmentId()))
                    .toList();
        }

        @Override
        public Optional<ClassReviewFeedback> findTopByAssignmentIdAndSuggestionKeyOrderByCreatedAtDesc(Long assignmentId, String suggestionKey) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .filter(item -> Objects.equals(item.getSuggestionKey(), suggestionKey))
                    .max(Comparator.comparing(ClassReviewFeedback::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        }
    }

    private abstract static class UnsupportedJpaRepository<T, ID> {
        public List<T> findAll() { throw unsupported(); }
        public List<T> findAllById(Iterable<ID> ids) { throw unsupported(); }
        public <S extends T> S save(S entity) { throw unsupported(); }
        public <S extends T> List<S> saveAll(Iterable<S> entities) { throw unsupported(); }
        public Optional<T> findById(ID id) { throw unsupported(); }
        public boolean existsById(ID id) { throw unsupported(); }
        public long count() { throw unsupported(); }
        public void deleteById(ID id) { throw unsupported(); }
        public void delete(T entity) { throw unsupported(); }
        public void deleteAllById(Iterable<? extends ID> ids) { throw unsupported(); }
        public void deleteAll(Iterable<? extends T> entities) { throw unsupported(); }
        public void deleteAll() { throw unsupported(); }
        public void flush() { throw unsupported(); }
        public <S extends T> S saveAndFlush(S entity) { throw unsupported(); }
        public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) { throw unsupported(); }
        public void deleteAllInBatch(Iterable<T> entities) { throw unsupported(); }
        public void deleteAllByIdInBatch(Iterable<ID> ids) { throw unsupported(); }
        public void deleteAllInBatch() { throw unsupported(); }
        public T getOne(ID id) { throw unsupported(); }
        public T getById(ID id) { throw unsupported(); }
        public T getReferenceById(ID id) { throw unsupported(); }
        public List<T> findAll(Sort sort) { throw unsupported(); }
        public Page<T> findAll(Pageable pageable) { throw unsupported(); }
        public <S extends T> Optional<S> findOne(Example<S> example) { throw unsupported(); }
        public <S extends T> List<S> findAll(Example<S> example) { throw unsupported(); }
        public <S extends T> List<S> findAll(Example<S> example, Sort sort) { throw unsupported(); }
        public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) { throw unsupported(); }
        public <S extends T> long count(Example<S> example) { throw unsupported(); }
        public <S extends T> boolean exists(Example<S> example) { throw unsupported(); }
        public <S extends T, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw unsupported(); }
        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not used in this test");
        }
    }
}
