package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.application.AbilitySignalAnalyzer;
import com.onlinejudge.classroom.application.StudentRecommendationEventService;
import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionAnalysisServiceHistoryEvidenceTest {

    @Test
    void analysisGenerationDoesNotWrapExternalModelCallsInDatabaseTransaction() throws NoSuchMethodException {
        Transactional transactional = SubmissionAnalysisService.class
                .getMethod("generateAndStoreAnalysis", com.onlinejudge.problem.domain.Problem.class, Submission.class, List.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    void historyEvidenceCarriesPreviousLearningActionFeedback() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionAnalysisRepository analysisRepository = mock(SubmissionAnalysisRepository.class);
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        DiagnosisReportReader reportReader = new DiagnosisReportReader(new ObjectMapper(), taxonomy);
        SubmissionAnalysisService service = new SubmissionAnalysisService(
                submissionRepository,
                null,
                null,
                analysisRepository,
                null,
                new ObjectMapper(),
                null,
                null,
                taxonomy,
                null,
                reportReader,
                null,
                null,
                null,
                null,
                null
        );
        Submission current = submission(22L, Submission.Verdict.WRONG_ANSWER, 10);
        Submission previous = submission(21L, Submission.Verdict.WRONG_ANSWER, 0);
        SubmissionAnalysis previousAnalysis = SubmissionAnalysis.builder()
                .submissionId(21L)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("diagnosis")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"],
                          "learningInterventionPlan": {
                            "interventionType": "MIN_CASE_TRACE",
                            "studentTask": "Trace one minimal failing input.",
                            "completionSignal": "Student identifies the first divergence.",
                            "evidenceRefs": ["eval:intervention"]
                          },
                          "learningActionEvidence": {
                            "expectedActionType": "MIN_CASE_TRACE",
                            "executionStatus": "NOT_OBSERVED",
                            "observedEvidence": "Waiting for follow-up.",
                            "confidence": 0.5,
                            "evidenceRefs": ["eval:intervention"],
                            "nextAdjustment": "Wait for follow-up."
                          }
                        }
                        """)
                .build();

        when(submissionRepository.findByAssignmentIdAndProblemIdAndStudentProfileIdOrderBySubmittedAtDesc(7L, 101L, 41L))
                .thenReturn(List.of(previous));
        when(analysisRepository.findBySubmissionIdIn(List.of(21L))).thenReturn(List.of(previousAnalysis));

        DiagnosisEvidencePackage.HistoryEvidence history = service.buildHistoryEvidenceForTest(current);

        assertThat(history.getPreviousInterventionType()).isEqualTo("MIN_CASE_TRACE");
        assertThat(history.getPreviousInterventionTask()).isEqualTo("Trace one minimal failing input.");
        assertThat(history.getPreviousInterventionCompletionSignal()).isEqualTo("Student identifies the first divergence.");
        assertThat(history.getPreviousLearningActionStatus()).isEqualTo("CONTRADICTED");
        assertThat(history.getPreviousLearningActionConfidence()).isEqualTo(0.74);
        assertThat(history.getPreviousLearningActionEvidenceRefs())
                .contains("eval:intervention", "followup:submission:22", "action:CONTRADICTED");
        assertThat(history.getPreviousLearningActionSummary()).contains("WRONG_ANSWER");
        assertThat(history.getPreviousLearningActionNextAdjustment()).contains("降低提示粒度");
    }

    @Test
    void learningMemoryCarriesTeacherCorrectionTargetAndKnowledgePath() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionAnalysisRepository analysisRepository = mock(SubmissionAnalysisRepository.class);
        TeacherDiagnosisCorrectionRepository correctionRepository = mock(TeacherDiagnosisCorrectionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        DiagnosisReportReader reportReader = new DiagnosisReportReader(objectMapper, taxonomy);
        SubmissionAnalysisService service = new SubmissionAnalysisService(
                submissionRepository,
                null,
                null,
                analysisRepository,
                null,
                objectMapper,
                null,
                null,
                taxonomy,
                null,
                reportReader,
                null,
                null,
                new AbilitySignalAnalyzer(reportReader, taxonomy),
                correctionRepository,
                null
        );
        Submission current = submission(22L, Submission.Verdict.WRONG_ANSWER, 10);
        Submission previous = submission(21L, Submission.Verdict.WRONG_ANSWER, 0);
        SubmissionAnalysis previousAnalysis = SubmissionAnalysis.builder()
                .submissionId(21L)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("边界问题")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build();
        TeacherDiagnosisCorrection correction = TeacherDiagnosisCorrection.builder()
                .submissionId(21L)
                .originalIssueTag("BOUNDARY_CONDITION")
                .originalFineGrainedTag("OFF_BY_ONE")
                .correctedIssueTag("IO_FORMAT")
                .correctedFineGrainedTag("INPUT_PARSING")
                .correctionType("KNOWLEDGE_PATH")
                .targetIssueId("I2")
                .correctedKnowledgePath("基础语法 / 输入输出 / 多组数据读取")
                .targetEvidenceRef("code:line:4")
                .teacherNote("路径归类错误，不只是标签错误。")
                .correctedAt(LocalDateTime.of(2026, 5, 24, 10, 5))
                .build();

        when(submissionRepository.findByStudentProfileIdInOrderBySubmittedAtDesc(List.of(41L)))
                .thenReturn(List.of(previous));
        when(analysisRepository.findBySubmissionIdIn(List.of(21L))).thenReturn(List.of(previousAnalysis));
        when(correctionRepository.findBySubmissionIdIn(List.of(21L))).thenReturn(List.of(correction));

        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory =
                service.buildLearningMemorySnapshotForTest(current);

        assertThat(memory.getTeacherCalibrationPatterns()).singleElement().satisfies(pattern -> {
            assertThat(pattern.getCorrectionType()).isEqualTo("KNOWLEDGE_PATH");
            assertThat(pattern.getTargetIssueId()).isEqualTo("I2");
            assertThat(pattern.getCorrectedKnowledgePath()).contains("多组数据读取");
            assertThat(pattern.getTargetEvidenceRef()).isEqualTo("code:line:4");
            assertThat(pattern.getLatestTeacherNote()).contains("路径归类错误");
        });
    }

    @Test
    void existingAnalysisBackfillFailureDoesNotBlockReuse() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionAnalysisRepository analysisRepository = mock(SubmissionAnalysisRepository.class);
        StudentRecommendationEventService recommendationEventService = mock(StudentRecommendationEventService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SubmissionAnalysisService service = new SubmissionAnalysisService(
                submissionRepository,
                null,
                null,
                analysisRepository,
                null,
                objectMapper,
                null,
                null,
                new DiagnosisTaxonomy(),
                null,
                new DiagnosisReportReader(objectMapper, new DiagnosisTaxonomy()),
                null,
                recommendationEventService,
                null,
                null,
                null
        );
        Submission submission = submission(22L, Submission.Verdict.WRONG_ANSWER, 10);
        SubmissionAnalysis analysis = SubmissionAnalysis.builder()
                .submissionId(22L)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("diagnosis")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "sourceType": "TEST",
                          "summary": "summary"
                        }
                        """)
                .build();
        when(submissionRepository.findById(22L)).thenReturn(Optional.of(submission));
        when(analysisRepository.findBySubmissionId(22L)).thenReturn(Optional.of(analysis));
        doThrow(new IllegalStateException("backfill down"))
                .when(recommendationEventService).backfillSubmissionAnalysis(submission, analysis);

        assertThatCode(() -> service.generateAndStoreAnalysisForSubmission(22L))
                .doesNotThrowAnyException();
        verify(recommendationEventService).backfillSubmissionAnalysis(submission, analysis);
    }

    private Submission submission(Long id, Submission.Verdict verdict, int minutesAfter) {
        return Submission.builder()
                .id(id)
                .assignmentId(7L)
                .studentProfileId(41L)
                .problemId(101L)
                .languageId(71)
                .languageName("Python 3")
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 5, 24, 10, 0).plusMinutes(minutesAfter))
                .build();
    }
}
