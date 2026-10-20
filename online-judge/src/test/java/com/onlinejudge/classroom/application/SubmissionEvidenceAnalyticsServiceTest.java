package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.persistence.StudentAiFeedbackEventRepository;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.application.SubmissionGrowthSummaryService;
import com.onlinejudge.submission.dto.SubmissionGrowthSummaryResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionEvidenceAnalyticsServiceTest {

    @Test
    void teacherCorrectionOverridesEffectiveIssueAndPathWithoutChangingRawFact() {
        SubmissionDiagnosisFactRepository factRepository = mock(SubmissionDiagnosisFactRepository.class);
        TeacherDiagnosisCorrectionRepository correctionRepository = mock(TeacherDiagnosisCorrectionRepository.class);
        StudentAiFeedbackEventRepository eventRepository = mock(StudentAiFeedbackEventRepository.class);
        SubmissionEvidenceAnalyticsService service = new SubmissionEvidenceAnalyticsService(
                factRepository, correctionRepository, eventRepository, new ObjectMapper()
        );
        Submission submission = submission(5L, 11L, Submission.Verdict.WRONG_ANSWER, LocalDateTime.now());
        SubmissionDiagnosisFact raw = fact(5L, 105L);
        TeacherDiagnosisCorrection correction = TeacherDiagnosisCorrection.builder()
                .id(77L)
                .submissionId(5L)
                .correctedIssueTag("INPUT_FORMAT")
                .correctedFineGrainedTag("MULTI_CASE_INPUT")
                .correctionType("KNOWLEDGE_PATH")
                .correctedKnowledgePath("基础语法 / 输入输出 / 多组数据 / 漏读组数")
                .build();
        when(factRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(raw));
        when(correctionRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(correction));
        when(eventRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of());

        var summary = service.summarize(List.of(submission), List.of(student(11L)), Map.of(5L, analysis(105L, 5L)));

        assertThat(summary.knowledgePathStats().stream()
                .filter(item -> "mistakePoint".equals(item.getGranularity()))
                .findFirst()).get().satisfies(item -> {
                    assertThat(item.getLabel()).isEqualTo("漏读组数");
                    assertThat(item.getNormalizedIssueId()).isEqualTo("MULTI_CASE_INPUT");
                    assertThat(item.getPathStatus()).isEqualTo("PROVISIONAL");
                    assertThat(item.getSource()).isEqualTo("TEACHER_OVERRIDE");
                    assertThat(item.getTeacherCorrectionId()).isEqualTo(77L);
                });
        assertThat(raw.getMistakePointId()).isEqualTo("MP_BOUNDARY");
        assertThat(raw.getKnowledgePathJson()).contains("边界处理");
    }

    @Test
    void separatesRosterAndAttemptsAndComputesRepeatAndRecoveryEvidence() {
        SubmissionDiagnosisFactRepository factRepository = mock(SubmissionDiagnosisFactRepository.class);
        TeacherDiagnosisCorrectionRepository correctionRepository = mock(TeacherDiagnosisCorrectionRepository.class);
        StudentAiFeedbackEventRepository eventRepository = mock(StudentAiFeedbackEventRepository.class);
        SubmissionEvidenceAnalyticsService service = new SubmissionEvidenceAnalyticsService(
                factRepository, correctionRepository, eventRepository, new ObjectMapper()
        );
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        Submission first = submission(1L, 11L, Submission.Verdict.WRONG_ANSWER, base);
        Submission second = submission(2L, 11L, Submission.Verdict.WRONG_ANSWER, base.plusMinutes(5));
        Submission accepted = submission(3L, 11L, Submission.Verdict.ACCEPTED, base.plusMinutes(10));
        Submission anonymous = submission(4L, null, Submission.Verdict.WRONG_ANSWER, base.plusMinutes(15));
        when(factRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(fact(1L, 101L), fact(2L, 102L)));
        when(correctionRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of());
        when(eventRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(
                StudentAiFeedbackEvent.builder()
                        .submissionId(2L)
                        .eventType(StudentAiFeedbackEvent.EVENT_VIEWED)
                        .createdAt(base.plusMinutes(6))
                        .build()
        ));

        var summary = service.summarize(
                List.of(first, second, accepted, anonymous),
                List.of(student(11L), student(12L)),
                Map.of(1L, analysis(101L, 1L), 2L, analysis(102L, 2L), 3L, analysis(103L, 3L))
        );

        assertThat(summary.rosterStudentCount()).isEqualTo(2);
        assertThat(summary.submittedStudentCount()).isEqualTo(1);
        assertThat(summary.unsubmittedStudentCount()).isEqualTo(1);
        assertThat(summary.attemptCount()).isEqualTo(3);
        assertThat(summary.studentPassRate()).isEqualTo(1.0);
        assertThat(summary.attemptPassRate()).isEqualTo(0.3333);
        assertThat(summary.dataCompleteness().getIdentityMissingCount()).isEqualTo(1);
        assertThat(summary.knowledgePathStats().stream()
                .filter(item -> "mistakePoint".equals(item.getGranularity()))
                .findFirst()).get().satisfies(item -> {
                    assertThat(item.getErrorOccurrenceCount()).isEqualTo(2);
                    assertThat(item.getRawOccurrenceCount()).isEqualTo(2);
                    assertThat(item.getEffectiveWeightedOccurrenceCount()).isEqualTo(1);
                    assertThat(item.getAffectedStudentCount()).isEqualTo(1);
                    assertThat(item.getRepeatedStudentCount()).isZero();
                    assertThat(item.getRepeatedStudentIds()).isEmpty();
                    assertThat(item.getAffectedProblemCount()).isEqualTo(1);
                    assertThat(item.getRecoveredStudentCount()).isEqualTo(1);
                    assertThat(item.getUnresolvedStudentCount()).isZero();
                    assertThat(item.getRecoveryRate()).isEqualTo(1.0);
                    assertThat(item.getDifficultyClassification()).isEqualTo("OCCASIONAL_INDIVIDUAL");
                });
        assertThat(summary.recoverySummary().getRecoveryNumerator()).isEqualTo(1);
        assertThat(summary.recoverySummary().getRecoveryDenominator()).isEqualTo(1);
        assertThat(summary.recoverySummary().getSameIssueCount()).isZero();
        assertThat(summary.recoverySummary().getRecoveredCount()).isEqualTo(1);
        assertThat(summary.recoverySummary().getFeedbackViewedRecoveredCount()).isZero();
        assertThat(summary.recentStates().get(11L).getStatus()).isEqualTo("RECENTLY_RECOVERED");
    }

    @Test
    void reusesStudentGrowthProjectionForRepeatedAndResolvedStudentSets() {
        SubmissionDiagnosisFactRepository factRepository = mock(SubmissionDiagnosisFactRepository.class);
        TeacherDiagnosisCorrectionRepository correctionRepository = mock(TeacherDiagnosisCorrectionRepository.class);
        StudentAiFeedbackEventRepository eventRepository = mock(StudentAiFeedbackEventRepository.class);
        SubmissionGrowthSummaryService growthService = mock(SubmissionGrowthSummaryService.class);
        SubmissionEvidenceAnalyticsService service = new SubmissionEvidenceAnalyticsService(
                factRepository, correctionRepository, eventRepository, new ObjectMapper()
        );
        service.setGrowthSummaryService(growthService);
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        Submission first = submission(1L, 11L, Submission.Verdict.WRONG_ANSWER, base);
        Submission repeated = submission(2L, 11L, Submission.Verdict.WRONG_ANSWER, base.plusMinutes(5));
        Submission accepted = submission(3L, 11L, Submission.Verdict.ACCEPTED, base.plusMinutes(10));
        when(factRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(fact(1L, 101L), fact(2L, 102L)));
        when(correctionRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of());
        when(eventRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of());
        when(growthService.summarize(anyList())).thenReturn(Map.of(
                1L, growth(1L, true, "NEW"),
                2L, growth(2L, true, "PERSISTED"),
                3L, growth(3L, true, "RECOVERED")
        ));

        var summary = service.summarize(
                List.of(first, repeated, accepted),
                List.of(student(11L)),
                Map.of(1L, analysis(101L, 1L), 2L, analysis(102L, 2L), 3L, analysis(103L, 3L))
        );

        assertThat(summary.knowledgePathStats().stream()
                .filter(item -> "mistakePoint".equals(item.getGranularity()))
                .findFirst()).get().satisfies(item -> {
                    assertThat(item.getEffectiveWeightedOccurrenceCount()).isEqualTo(2);
                    assertThat(item.getRepeatedStudentIds()).containsExactly(11L);
                    assertThat(item.getResolvedStudentIds()).containsExactly(11L);
                    assertThat(item.getUnresolvedStudentCount()).isZero();
                });
    }

    private SubmissionGrowthSummaryResponse growth(Long submissionId, boolean effective, String status) {
        return SubmissionGrowthSummaryResponse.builder()
                .submissionId(submissionId)
                .growthState("COMPLETED")
                .effectiveAttempt(effective)
                .comparable(true)
                .issueSignals(List.of(SubmissionGrowthSummaryResponse.IssueSignal.builder()
                        .normalizedPointKey("MP_BOUNDARY")
                        .title("边界错误")
                        .displayCategory("REPAIR")
                        .changeStatus(status)
                        .build()))
                .build();
    }

    private Submission submission(Long id, Long studentId, Submission.Verdict verdict, LocalDateTime time) {
        return Submission.builder()
                .id(id)
                .assignmentId(7L)
                .studentProfileId(studentId)
                .problemId(21L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(time)
                .build();
    }

    private SubmissionDiagnosisFact fact(Long submissionId, Long analysisId) {
        return SubmissionDiagnosisFact.builder()
                .id(submissionId)
                .submissionId(submissionId)
                .analysisId(analysisId)
                .factKey("fact-" + submissionId)
                .factType("REPAIR")
                .title("边界错误")
                .mistakePointId("MP_BOUNDARY")
                .knowledgePathJson("[\"基础\",\"循环\",\"边界处理\",\"边界错误\"]")
                .knowledgePathStatus("FORMAL")
                .libraryFit("HIT")
                .projectionStatus("READY")
                .build();
    }

    private StudentProfile student(Long id) {
        return StudentProfile.builder().id(id).classGroupId(3L).displayName("学生" + id).identityKey("student:" + id).build();
    }

    private SubmissionAnalysis analysis(Long id, Long submissionId) {
        return SubmissionAnalysis.builder().id(id).submissionId(submissionId).build();
    }
}
