package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.dto.StudentAiFeedbackObservabilityResponse;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.StudentAiFeedback;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.dto.StudentAiFeedbackResponse;
import com.onlinejudge.submission.persistence.StudentAiFeedbackEventRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentAiFeedbackObservabilityServiceTest {

    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final SubmissionAnalysisRepository analysisRepository = mock(SubmissionAnalysisRepository.class);
    private final StudentAiFeedbackRepository feedbackRepository = mock(StudentAiFeedbackRepository.class);
    private final StudentAiFeedbackEventRepository eventRepository = mock(StudentAiFeedbackEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final StudentAiFeedbackObservabilityService service = new StudentAiFeedbackObservabilityService(
            assignmentRepository,
            submissionRepository,
            analysisRepository,
            feedbackRepository,
            eventRepository,
            new StudentAiFeedbackImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            objectMapper
    );

    @Test
    void summarizesAssignmentFeedbackReadinessLatencyAndImpact() throws Exception {
        when(assignmentRepository.existsById(7L)).thenReturn(true);
        List<Submission> submissions = List.of(
                submission(11L, 101L, Submission.Verdict.WRONG_ANSWER, 0),
                submission(12L, 101L, Submission.Verdict.ACCEPTED, 8),
                submission(13L, 102L, Submission.Verdict.RUNTIME_ERROR, 2),
                submission(14L, 103L, Submission.Verdict.COMPILATION_ERROR, 4)
        );
        when(submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(7L)).thenReturn(submissions);
        when(feedbackRepository.findBySubmissionIdIn(List.of(11L, 12L, 13L, 14L))).thenReturn(List.of(
                feedback(11L, "READY", "MODEL", 420L, null),
                feedback(13L, "FAILED", "NONE", null, "MODEL_PARSE_ERROR")
        ));
        when(eventRepository.findBySubmissionIdIn(List.of(11L, 12L, 13L, 14L))).thenReturn(List.of(
                viewedEvent(31L, 11L, 2),
                failedEvent(32L, 13L, "MODEL_PARSE_ERROR")
        ));
        when(analysisRepository.findBySubmissionIdIn(List.of(11L, 12L, 13L, 14L))).thenReturn(List.of(
                analysis(11L, "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]")
        ));

        StudentAiFeedbackObservabilityResponse response = service.buildForAssignment(7L);

        assertThat(response.getSubmissionCount()).isEqualTo(4);
        assertThat(response.getFailedSubmissionCount()).isEqualTo(3);
        assertThat(response.getFeedbackRecordCount()).isEqualTo(2);
        assertThat(response.getModelReadyCount()).isEqualTo(1);
        assertThat(response.getFeedbackFailedCount()).isEqualTo(1);
        assertThat(response.getViewedCount()).isEqualTo(1);
        assertThat(response.getModelReadyRate()).isEqualTo(33.33);
        assertThat(response.getViewRate()).isEqualTo(100.0);
        assertThat(response.getLatencySampleCount()).isEqualTo(1);
        assertThat(response.getP50LatencyMs()).isEqualTo(420L);
        assertThat(response.getP95LatencyMs()).isEqualTo(420L);
        assertThat(response.getFailureReasons()).singleElement().satisfies(reason -> {
            assertThat(reason.getReason()).isEqualTo("MODEL_PARSE_ERROR");
            assertThat(reason.getCount()).isEqualTo(1);
        });
        assertThat(response.getImpactStats()).singleElement().satisfies(stat -> {
            assertThat(stat.getStatus()).isEqualTo("IMPROVED_AFTER_AI");
            assertThat(stat.getLabel()).isEqualTo("查看后改善");
            assertThat(stat.getCount()).isEqualTo(1);
        });
        assertThat(response.getSummary()).contains("改善");
        assertThat(response.getRecommendedAction()).contains("持续比较");
    }

    @Test
    void expandsFullChainFailureReasonFromStoredAiInvocation() throws Exception {
        when(assignmentRepository.existsById(7L)).thenReturn(true);
        List<Submission> submissions = List.of(
                submission(21L, 101L, Submission.Verdict.WRONG_ANSWER, 0)
        );
        when(submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(7L)).thenReturn(submissions);
        when(feedbackRepository.findBySubmissionIdIn(List.of(21L))).thenReturn(List.of(
                feedback(21L, "FAILED", "AI_UNAVAILABLE", null, "FULL_CHAIN_FAILED")
        ));
        when(eventRepository.findBySubmissionIdIn(List.of(21L))).thenReturn(List.of());
        when(analysisRepository.findBySubmissionIdIn(List.of(21L))).thenReturn(List.of(
                failedAnalysis(21L, "FREE_DIAGNOSIS", "OUTPUT_TRUNCATED")
        ));

        StudentAiFeedbackObservabilityResponse response = service.buildForAssignment(7L);

        assertThat(response.getFailureReasons()).singleElement().satisfies(reason -> {
            assertThat(reason.getReason()).isEqualTo("FREE_DIAGNOSIS:OUTPUT_TRUNCATED");
            assertThat(reason.getCount()).isEqualTo(1);
        });
    }

    private StudentAiFeedback feedback(Long submissionId, String status, String source, Long latencyMs, String failureReason) throws Exception {
        StudentAiFeedbackResponse response = StudentAiFeedbackResponse.builder()
                .submissionId(submissionId)
                .status(status)
                .source(source)
                .latencyMs(latencyMs)
                .repairItems(List.of())
                .improvementItems(List.of())
                .evidenceRefs(List.of())
                .build();
        return StudentAiFeedback.builder()
                .submissionId(submissionId)
                .status(status)
                .source(source)
                .feedbackJson(objectMapper.writeValueAsString(response))
                .failureReason(failureReason)
                .build();
    }

    private Submission submission(Long id, Long problemId, Submission.Verdict verdict, int minutesAfter) {
        return Submission.builder()
                .id(id)
                .assignmentId(7L)
                .studentProfileId(41L)
                .problemId(problemId)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minutesAfter))
                .build();
    }

    private StudentAiFeedbackEvent viewedEvent(Long id, Long submissionId, int minutesAfter) {
        return StudentAiFeedbackEvent.builder()
                .id(id)
                .submissionId(submissionId)
                .studentProfileId(41L)
                .assignmentId(7L)
                .problemId(101L)
                .eventType(StudentAiFeedbackEvent.EVENT_VIEWED)
                .feedbackStatus("READY")
                .feedbackSource("MODEL")
                .answerLeakRisk("LOW")
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minutesAfter))
                .build();
    }

    private StudentAiFeedbackEvent failedEvent(Long id, Long submissionId, String reason) {
        return StudentAiFeedbackEvent.builder()
                .id(id)
                .submissionId(submissionId)
                .assignmentId(7L)
                .eventType(StudentAiFeedbackEvent.EVENT_FAILED)
                .feedbackStatus("FAILED")
                .failureReason(reason)
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 1))
                .build();
    }

    private SubmissionAnalysis analysis(Long submissionId, String issueTags, String fineTags) {
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("diagnosis")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "issueTags": %s,
                          "fineGrainedTags": %s
                        }
                        """.formatted(issueTags, fineTags))
                .build();
    }

    private SubmissionAnalysis failedAnalysis(Long submissionId, String stage, String reason) {
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("diagnosis")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "aiInvocation": {
                            "status": "MODEL_FAILED",
                            "failureStage": "%s",
                            "failureReason": "%s"
                          }
                        }
                        """.formatted(stage, reason))
                .build();
    }
}
