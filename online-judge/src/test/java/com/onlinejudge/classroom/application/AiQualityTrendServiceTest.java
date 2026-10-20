package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.HintSafetyCheck;
import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.ClassReviewFeedbackRepository;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import com.onlinejudge.classroom.persistence.HintSafetyCheckRepository;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.leaderboard.persistence.ProblemSubmissionStatsProjection;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionHistoryProjection;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class AiQualityTrendServiceTest {

    private final FakeAssignmentRepository assignmentRepository = new FakeAssignmentRepository();
    private final FakeSubmissionRepository submissionRepository = new FakeSubmissionRepository();
    private final FakeSubmissionAnalysisRepository analysisRepository = new FakeSubmissionAnalysisRepository();
    private final FakeTeacherDiagnosisCorrectionRepository correctionRepository = new FakeTeacherDiagnosisCorrectionRepository();
    private final FakeClassReviewFeedbackRepository classReviewFeedbackRepository = new FakeClassReviewFeedbackRepository();
    private final FakeHintSafetyCheckRepository hintSafetyCheckRepository = new FakeHintSafetyCheckRepository();
    private final FakeCoachPromptRepository coachPromptRepository = new FakeCoachPromptRepository();
    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiQualityTrendService service = new AiQualityTrendService(
            assignmentRepository,
            submissionRepository,
            analysisRepository,
            correctionRepository,
            classReviewFeedbackRepository,
            hintSafetyCheckRepository,
            coachPromptRepository,
            new DiagnosisReportReader(objectMapper, taxonomy),
            taxonomy
    );

    @Test
    void summarizesQualityTrendAcrossAssignments() {
        assignmentRepository.items.put(7L, Assignment.builder().id(7L).title("边界作业").build());
        assignmentRepository.items.put(8L, Assignment.builder().id(8L).title("输入作业").build());
        submissionRepository.items.add(submission(11L, 7L));
        submissionRepository.items.add(submission(12L, 7L));
        submissionRepository.items.add(submission(21L, 8L));
        analysisRepository.save(analysis(11L, 0.55, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(12L, 0.82, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_PARTIAL_COMPLETED", false,
                "single-call", "DIAGNOSIS_AND_ADVICE", "SAFETY_RISK"));
        analysisRepository.save(analysis(21L, 0.72, "HIGH", "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]", "MODEL_RUNTIME_FALLBACK", true,
                "single-call", "DIAGNOSIS_AND_ADVICE", "INSUFFICIENT_QUOTA",
                "stream", 0, 0, 2, false));
        correctionRepository.saved.add(correction(1L, 7L, 11L, "BOUNDARY_CONDITION", "OFF_BY_ONE", "IO_FORMAT", "INPUT_PARSING", true));
        correctionRepository.saved.add(correction(2L, 8L, 21L, "BOUNDARY_CONDITION", "OFF_BY_ONE", "IO_FORMAT", "INPUT_PARSING", false));

        var trend = service.buildTrend();

        assertThat(trend.getAssignmentCount()).isEqualTo(2);
        assertThat(trend.getAnalyzedSubmissionCount()).isEqualTo(3);
        assertThat(trend.getCorrectionCount()).isEqualTo(2);
        assertThat(trend.getEvalCandidateCount()).isEqualTo(1);
        assertThat(trend.getLowConfidenceCount()).isEqualTo(1);
        assertThat(trend.getHighLeakRiskCount()).isEqualTo(1);
        assertThat(trend.getModelCompletedCount()).isEqualTo(1);
        assertThat(trend.getModelPartialCount()).isEqualTo(1);
        assertThat(trend.getModelRuntimeFailureCount()).isEqualTo(1);
        assertThat(trend.getModelRuntimeFailureRate()).isEqualTo(33.3);
        assertThat(trend.getCorrectionRate()).isEqualTo(66.7);
        assertThat(trend.getSummary()).contains("高泄题风险");
        assertThat(trend.getAssignments()).extracting("assignmentTitle").containsExactly("边界作业", "输入作业");
        assertThat(trend.getAssignments().get(0).getCorrectionRate()).isEqualTo(50.0);
        assertThat(trend.getAssignments().get(0).getModelCompletedCount()).isEqualTo(1);
        assertThat(trend.getAssignments().get(0).getModelPartialCount()).isEqualTo(1);
        assertThat(trend.getAssignments().get(0).getModelRuntimeFailureCount()).isZero();
        assertThat(trend.getAssignments().get(1).getModelRuntimeFailureCount()).isEqualTo(1);
        assertThat(trend.getCorrectedTags()).first()
                .satisfies(tag -> {
                    assertThat(tag.getTag()).isEqualTo("INPUT_PARSING");
                    assertThat(tag.getLabel()).isEqualTo("输入读取理解");
                    assertThat(tag.getCount()).isEqualTo(2);
                    assertThat(tag.getEvalCandidateCount()).isEqualTo(1);
                });
        assertThat(trend.getEvalNeededTags()).first()
                .satisfies(tag -> {
                    assertThat(tag.getTag()).isEqualTo("INPUT_PARSING");
                    assertThat(tag.getCount()).isEqualTo(1);
                });
        assertThat(trend.getSourceSegments()).hasSize(3);
        assertThat(trend.getSourceSegments()).filteredOn(segment -> "MODEL_COMPLETED".equals(segment.getStatus()))
                .first()
                .satisfies(segment -> {
                    assertThat(segment.getSourceType()).isEqualTo("TEST");
                    assertThat(segment.getProvider()).isEqualTo("ModelScope");
                    assertThat(segment.getModelVersion()).isEqualTo("MiniMax/MiniMax-M2.7");
                    assertThat(segment.getPromptVersion()).isEqualTo("submission-diagnosis-prompt-v2");
                    assertThat(segment.getAgentVersion()).isEqualTo("diagnostic-agent-v2");
                    assertThat(segment.getRuntimeMode()).isEqualTo("single-call");
                    assertThat(segment.getVersionLabel()).contains("submission-diagnosis-prompt-v2");
                    assertThat(segment.getAnalyzedSubmissionCount()).isEqualTo(1);
                    assertThat(segment.getCorrectionCount()).isEqualTo(1);
                    assertThat(segment.getLowConfidenceCount()).isEqualTo(1);
                    assertThat(segment.getHighLeakRiskCount()).isZero();
                    assertThat(segment.getFallbackCount()).isZero();
                    assertThat(segment.getModelCompletedCount()).isEqualTo(1);
                    assertThat(segment.getModelPartialCount()).isZero();
                    assertThat(segment.getModelRuntimeFailureCount()).isZero();
                    assertThat(segment.getCorrectionRate()).isEqualTo(100.0);
                });
        assertThat(trend.getSourceSegments()).filteredOn(segment -> "MODEL_PARTIAL_COMPLETED".equals(segment.getStatus()))
                .first()
                .satisfies(segment -> {
                    assertThat(segment.getRuntimeMode()).isEqualTo("single-call");
                    assertThat(segment.getFailureStage()).isEqualTo("DIAGNOSIS_AND_ADVICE");
                    assertThat(segment.getFailureReason()).isEqualTo("SAFETY_RISK");
                    assertThat(segment.getModelPartialCount()).isEqualTo(1);
                    assertThat(segment.getModelRuntimeFailureCount()).isZero();
                    assertThat(segment.getAnalyzedSubmissionCount()).isEqualTo(1);
                    assertThat(segment.getQualityComparabilityStatus()).isEqualTo("NOT_COMPARABLE");
                    assertThat(segment.getQualityComparabilityReasons()).contains(
                            "current recovery blocked",
                            "partial model outputs present"
                    );
                });
        assertThat(trend.getSourceSegments()).filteredOn(segment -> "MODEL_RUNTIME_FALLBACK".equals(segment.getStatus()))
                .first()
                .satisfies(segment -> {
                    assertThat(segment.getStatus()).isEqualTo("MODEL_RUNTIME_FALLBACK");
                    assertThat(segment.getRuntimeMode()).isEqualTo("single-call");
                    assertThat(segment.getFailureStage()).isEqualTo("DIAGNOSIS_AND_ADVICE");
                    assertThat(segment.getFailureReason()).isEqualTo("INSUFFICIENT_QUOTA");
                    assertThat(segment.getTransportMode()).isEqualTo("stream");
                    assertThat(segment.getFallbackCount()).isEqualTo(1);
                    assertThat(segment.getModelRuntimeFailureCount()).isEqualTo(1);
                    assertThat(segment.getModelRuntimeFailureRate()).isEqualTo(100.0);
                    assertThat(segment.getStreamNoContentCount()).isEqualTo(1);
                    assertThat(segment.getStreamInvalidChunkCount()).isEqualTo(2);
                    assertThat(segment.getStreamFallbackRetryCount()).isZero();
                    assertThat(segment.getRecoveryStatus()).isEqualTo("BLOCKED");
                    assertThat(segment.getRecoveryCheckCount()).isEqualTo(6);
                    assertThat(segment.getRecoveryPassedCheckCount()).isZero();
                    assertThat(segment.getRecoverySmokeRequiredChecks()).contains(
                            "status=MODEL_COMPLETED",
                            "fallbackUsed=false",
                            "modelHit=true",
                            "evidenceRefs present",
                            "answerLeakRisk!=HIGH",
                            "streamContentChunkCount>0"
                    );
                    assertThat(segment.getRecoveryBlockedReasons()).contains(
                            "submission:21: runtime failure",
                            "submission:21: model not completed",
                            "submission:21: safety failed",
                            "submission:21: stream content chunk missing",
                            "submission:21: INSUFFICIENT_QUOTA"
                    );
                    assertThat(segment.getQualityComparabilityStatus()).isEqualTo("NOT_COMPARABLE");
                    assertThat(segment.getQualityComparabilityReasonCount()).isGreaterThanOrEqualTo(4);
                    assertThat(segment.getQualityComparabilityReasons()).contains(
                            "current recovery blocked",
                            "model hits missing; fallback hits present",
                            "runtime failures still present"
                    );
                    assertThat(segment.getQualityComparabilitySummary()).contains("不能代表真实外部模型质量");
                    assertThat(segment.getAnalyzedSubmissionCount()).isEqualTo(1);
                    assertThat(segment.getCorrectionCount()).isEqualTo(1);
                    assertThat(segment.getHighLeakRiskCount()).isEqualTo(1);
                });
    }

    @Test
    void marksRecoveredSourceQualityComparableAndPartialWhenSameSourceHasRecoveredModelSample() {
        assignmentRepository.items.put(37L, Assignment.builder().id(37L).title("恢复趋势作业").build());
        submissionRepository.items.add(submission(371L, 37L));
        submissionRepository.items.add(submission(372L, 37L));
        analysisRepository.save(analysis(371L, 0.80, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]",
                "MODEL_PARTIAL_COMPLETED", false, "low-latency", "DIAGNOSIS_AND_ADVICE", "SAFETY_RISK",
                "stream", 0, 0, 0, false));
        analysisRepository.save(analysis(372L, 0.86, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]",
                "MODEL_COMPLETED", false, "low-latency", "", "",
                "stream", 12, 6, 0, false));

        var trend = service.buildTrend();

        assertThat(trend.getSourceSegments()).filteredOn(segment -> "MODEL_COMPLETED".equals(segment.getStatus()))
                .first()
                .satisfies(segment -> {
                    assertThat(segment.getRecoveryStatus()).isEqualTo("RECOVERED");
                    assertThat(segment.getQualityComparabilityStatus()).isEqualTo("COMPARABLE");
                    assertThat(segment.getQualityComparabilityReasonCount()).isZero();
                    assertThat(segment.getQualityComparabilityReasons()).isEmpty();
                    assertThat(segment.getQualityComparabilitySummary()).contains("可支持小批量模型质量对比");
                });
        assertThat(trend.getSourceSegments()).filteredOn(segment -> "MODEL_PARTIAL_COMPLETED".equals(segment.getStatus()))
                .first()
                .satisfies(segment -> {
                    assertThat(segment.getRecoveryStatus()).isEqualTo("RECOVERED");
                    assertThat(segment.getQualityComparabilityStatus()).isEqualTo("PARTIAL");
                    assertThat(segment.getQualityComparabilityReasons()).contains("partial model outputs present");
                    assertThat(segment.getQualityComparabilitySummary()).contains("部分真实模型证据");
                });
    }

    @Test
    void summarizesInterventionEvalTrendAcrossAssignments() {
        assignmentRepository.items.put(17L, Assignment.builder().id(17L).title("改善作业").build());
        assignmentRepository.items.put(18L, Assignment.builder().id(18L).title("仍卡作业").build());
        assignmentRepository.items.put(19L, Assignment.builder().id(19L).title("等待作业").build());
        submissionRepository.items.add(submission(31L, 17L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(32L, 17L, Submission.Verdict.ACCEPTED, LocalDateTime.of(2026, 5, 18, 13, 0)));
        submissionRepository.items.add(submission(41L, 18L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        submissionRepository.items.add(submission(42L, 18L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 13, 0)));
        submissionRepository.items.add(submission(51L, 19L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0)));
        analysisRepository.save(analysis(31L, 0.82, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(41L, 0.82, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(42L, 0.80, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(51L, 0.80, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        classReviewFeedbackRepository.saved.add(classReviewFeedback(17L, "review:17:ability:循环与边界:101:OFF_BY_ONE",
                ClassReviewFeedbackService.ACTION_ACCEPTED, 101L, "[\"OFF_BY_ONE\"]", LocalDateTime.of(2026, 5, 18, 12, 0)));
        classReviewFeedbackRepository.saved.add(classReviewFeedback(18L, "review:18:ability:循环与边界:101:OFF_BY_ONE",
                ClassReviewFeedbackService.ACTION_MODIFIED, 101L, "[\"OFF_BY_ONE\"]", LocalDateTime.of(2026, 5, 18, 12, 0)));
        classReviewFeedbackRepository.saved.add(classReviewFeedback(19L, "review:19:ability:循环与边界:101:OFF_BY_ONE",
                ClassReviewFeedbackService.ACTION_ACCEPTED, 101L, "[\"OFF_BY_ONE\"]", LocalDateTime.of(2026, 5, 18, 12, 0)));

        var trend = service.buildTrend();

        assertThat(trend.getInterventionEvalCandidateCount()).isEqualTo(2);
        assertThat(trend.getInterventionImprovedCount()).isEqualTo(1);
        assertThat(trend.getInterventionStillStuckCount()).isEqualTo(1);
        assertThat(trend.getInterventionWaitingFollowupCount()).isEqualTo(1);
        assertThat(trend.getSummary()).contains("仍卡同类问题");
        assertThat(trend.getAssignments()).filteredOn(point -> Objects.equals(point.getAssignmentId(), 17L))
                .first()
                .satisfies(point -> {
                    assertThat(point.getInterventionEvalCandidateCount()).isEqualTo(1);
                    assertThat(point.getInterventionImprovedCount()).isEqualTo(1);
                });
        assertThat(trend.getAssignments()).filteredOn(point -> Objects.equals(point.getAssignmentId(), 18L))
                .first()
                .satisfies(point -> {
                    assertThat(point.getInterventionEvalCandidateCount()).isEqualTo(1);
                    assertThat(point.getInterventionStillStuckCount()).isEqualTo(1);
                    assertThat(point.getSummary()).contains("仍卡同类问题");
                });
        assertThat(trend.getAssignments()).filteredOn(point -> Objects.equals(point.getAssignmentId(), 19L))
                .first()
                .satisfies(point -> {
                    assertThat(point.getInterventionEvalCandidateCount()).isZero();
                    assertThat(point.getInterventionWaitingFollowupCount()).isEqualTo(1);
                });
    }

    @Test
    void summarizesPromptSafetyTrendAcrossAssignmentsAndSourceSegments() {
        assignmentRepository.items.put(27L, Assignment.builder().id(27L).title("安全作业 A").build());
        assignmentRepository.items.put(28L, Assignment.builder().id(28L).title("安全作业 B").build());
        submissionRepository.items.add(submission(61L, 27L));
        submissionRepository.items.add(submission(62L, 27L));
        submissionRepository.items.add(submission(71L, 28L));
        analysisRepository.save(analysis(61L, 0.82, "LOW", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(62L, 0.84, "HIGH", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]", "MODEL_COMPLETED", false));
        analysisRepository.save(analysis(71L, 0.86, "LOW", "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]", "MODEL_COMPLETED", false));
        hintSafetyCheckRepository.saved.add(safetyCheck(1001L, 61L, "MEDIUM"));
        hintSafetyCheckRepository.saved.add(safetyCheck(1002L, 62L, "LOW"));
        hintSafetyCheckRepository.saved.add(safetyCheck(1003L, 71L, "HIGH"));
        coachPromptRepository.saved.add(coachPrompt(2001L, 71L, "SAFETY_REJECTED", "HIGH"));
        coachPromptRepository.saved.add(coachPrompt(2002L, 61L, "", ""));

        var trend = service.buildTrend();

        assertThat(trend.getPromptSafetyIncidentCount()).isEqualTo(4);
        assertThat(trend.getPromptSafetyDowngradeCount()).isEqualTo(2);
        assertThat(trend.getPromptSafetyHighRiskDowngradeCount()).isEqualTo(1);
        assertThat(trend.getCoachSafetyRejectionCount()).isEqualTo(1);
        assertThat(trend.getPromptSafetyIncidentRate()).isEqualTo(133.3);
        assertThat(trend.getAssignments()).filteredOn(point -> Objects.equals(point.getAssignmentId(), 27L))
                .first()
                .satisfies(point -> {
                    assertThat(point.getPromptSafetyIncidentCount()).isEqualTo(2);
                    assertThat(point.getPromptSafetyDowngradeCount()).isEqualTo(1);
                    assertThat(point.getPromptSafetyHighRiskDowngradeCount()).isZero();
                    assertThat(point.getCoachSafetyRejectionCount()).isZero();
                    assertThat(point.getPromptSafetyIncidentRate()).isEqualTo(100.0);
                    assertThat(point.getSummary()).contains("高泄题风险");
                });
        assertThat(trend.getAssignments()).filteredOn(point -> Objects.equals(point.getAssignmentId(), 28L))
                .first()
                .satisfies(point -> {
                    assertThat(point.getPromptSafetyIncidentCount()).isEqualTo(2);
                    assertThat(point.getPromptSafetyDowngradeCount()).isEqualTo(1);
                    assertThat(point.getPromptSafetyHighRiskDowngradeCount()).isEqualTo(1);
                    assertThat(point.getCoachSafetyRejectionCount()).isEqualTo(1);
                    assertThat(point.getSummary()).contains("安全降级", "Coach 安全回退");
                });
        assertThat(trend.getSourceSegments()).first()
                .satisfies(segment -> {
                    assertThat(segment.getAnalyzedSubmissionCount()).isEqualTo(3);
                    assertThat(segment.getHighLeakRiskCount()).isEqualTo(1);
                    assertThat(segment.getPromptSafetyIncidentCount()).isEqualTo(4);
                    assertThat(segment.getPromptSafetyDowngradeCount()).isEqualTo(2);
                    assertThat(segment.getPromptSafetyHighRiskDowngradeCount()).isEqualTo(1);
                    assertThat(segment.getCoachSafetyRejectionCount()).isEqualTo(1);
                    assertThat(segment.getPromptSafetyIncidentRate()).isEqualTo(133.3);
                });
    }

    private Submission submission(Long id, Long assignmentId) {
        return submission(id, assignmentId, Submission.Verdict.WRONG_ANSWER, LocalDateTime.of(2026, 5, 18, 10, 0));
    }

    private Submission submission(Long id, Long assignmentId, Submission.Verdict verdict, LocalDateTime submittedAt) {
        return Submission.builder()
                .id(id)
                .assignmentId(assignmentId)
                .problemId(101L)
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
                                        String status,
                                        boolean fallbackUsed) {
        return analysis(submissionId, confidence, leakRisk, issueTags, fineTags, status, fallbackUsed,
                "single-call", "", "");
    }

    private SubmissionAnalysis analysis(Long submissionId,
                                        double confidence,
                                        String leakRisk,
                                        String issueTags,
                                        String fineTags,
                                        String status,
                                        boolean fallbackUsed,
                                        String runtimeMode,
                                        String failureStage,
                                        String failureReason) {
        return analysis(submissionId, confidence, leakRisk, issueTags, fineTags, status, fallbackUsed,
                runtimeMode, failureStage, failureReason, "", 0, 0, 0, false);
    }

    private SubmissionAnalysis analysis(Long submissionId,
                                        double confidence,
                                        String leakRisk,
                                        String issueTags,
                                        String fineTags,
                                        String status,
                                        boolean fallbackUsed,
                                        String runtimeMode,
                                        String failureStage,
                                        String failureReason,
                                        String transportMode,
                                        int streamChunkCount,
                                        int streamContentChunkCount,
                                        int streamInvalidChunkCount,
                                        boolean streamFallbackRetryUsed) {
        String transportTelemetryJson = transportMode == null || transportMode.isBlank() ? "" : """
                            ,
                            "transportMode": "%s",
                            "streamChunkCount": %s,
                            "streamContentChunkCount": %s,
                            "streamInvalidChunkCount": %s,
                            "streamFallbackRetryUsed": %s
                """.formatted(transportMode, streamChunkCount, streamContentChunkCount, streamInvalidChunkCount,
                streamFallbackRetryUsed);
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
                          "diagnosticTrace": "diagnostic-agent-v2 signals=2 evidenceRefs=3 source=TEST model=completed",
                          "aiInvocation": {
                            "provider": "ModelScope",
                            "model": "MiniMax/MiniMax-M2.7",
                            "modelVersion": "MiniMax/MiniMax-M2.7",
                            "promptVersion": "submission-diagnosis-prompt-v2",
                            "agentVersion": "diagnostic-agent-v2",
                            "analysisSchemaVersion": "diagnosis-v1",
                            "evidenceSchemaVersion": "diagnosis-evidence-v1",
                            "taxonomyVersion": "diagnosis-taxonomy-v1",
                            "status": "%s",
                            "fallbackUsed": %s,
                            "runtimeMode": "%s",
                            "failureStage": "%s",
                            "failureReason": "%s"
                            %s
                          }
                        }
                        """.formatted(issueTags, fineTags, confidence, leakRisk, submissionId, status, fallbackUsed, runtimeMode, failureStage,
                        failureReason, transportTelemetryJson))
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

    private ClassReviewFeedback classReviewFeedback(Long assignmentId,
                                                    String suggestionKey,
                                                    String actionType,
                                                    Long exampleProblemId,
                                                    String evidenceTags,
                                                    LocalDateTime createdAt) {
        return ClassReviewFeedback.builder()
                .assignmentId(assignmentId)
                .suggestionKey(suggestionKey)
                .targetAbility("循环与边界")
                .exampleProblemId(exampleProblemId)
                .evidenceTags(evidenceTags)
                .actionType(actionType)
                .teacherNote("已采纳课堂介入建议")
                .createdBy("teacher")
                .createdAt(createdAt)
                .build();
    }

    private HintSafetyCheck safetyCheck(Long id, Long submissionId, String riskLevel) {
        return HintSafetyCheck.builder()
                .id(id)
                .submissionId(submissionId)
                .riskLevel(riskLevel)
                .blockedReasonsJson("[\"直接给出完整改法\"]")
                .originalHint("直接改成完整答案")
                .safeHint("先比较样例输出和变量变化")
                .checkedAt(LocalDateTime.of(2026, 5, 18, 12, 30))
                .build();
    }

    private CoachPrompt coachPrompt(Long id, Long submissionId, String failureReason, String leakRisk) {
        return CoachPrompt.builder()
                .id(id)
                .submissionId(submissionId)
                .assignmentId(99L)
                .studentProfileId(7L)
                .turnIndex(1)
                .hintPolicy("L2")
                .promptType("SOCRATIC_NEXT_STEP")
                .question("请先补一个最小样例。")
                .modelFailureReason(failureReason)
                .modelAnswerLeakRisk(leakRisk)
                .createdAt(LocalDateTime.of(2026, 5, 18, 12, 40))
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
        public Optional<ClassReviewFeedback> findTopByAssignmentIdAndSuggestionKeyOrderByCreatedAtDesc(Long assignmentId,
                                                                                                       String suggestionKey) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .filter(item -> Objects.equals(item.getSuggestionKey(), suggestionKey))
                    .sorted(Comparator.comparing(ClassReviewFeedback::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .findFirst();
        }
    }

    private static class FakeHintSafetyCheckRepository extends UnsupportedJpaRepository<HintSafetyCheck, Long>
            implements HintSafetyCheckRepository {
        private final List<HintSafetyCheck> saved = new ArrayList<>();

        @Override
        public Optional<HintSafetyCheck> findTopBySubmissionIdOrderByCheckedAtDesc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .sorted(Comparator.comparing(HintSafetyCheck::getCheckedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .findFirst();
        }

        @Override
        public List<HintSafetyCheck> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }
    }

    private static class FakeCoachPromptRepository extends UnsupportedJpaRepository<CoachPrompt, Long>
            implements CoachPromptRepository {
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
                    .sorted(Comparator
                            .comparing(CoachPrompt::getTurnIndex, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(CoachPrompt::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                    .toList();
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
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
