package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.StudentAiFeedback;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.domain.StudentAiFeedbackRevision;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionEvidenceBackfillBatch;
import com.onlinejudge.submission.persistence.StudentAiFeedbackEventRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRevisionRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.persistence.SubmissionEvidenceBackfillBatchRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionEvidenceBackfillServiceTest {

    @Test
    void repeatedBackfillDoesNotDuplicateFeedbackVersion() throws Exception {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionAnalysisRepository analysisRepository = mock(SubmissionAnalysisRepository.class);
        StudentAiFeedbackRepository feedbackRepository = mock(StudentAiFeedbackRepository.class);
        StudentAiFeedbackRevisionRepository revisionRepository = mock(StudentAiFeedbackRevisionRepository.class);
        StudentAiFeedbackEventRepository eventRepository = mock(StudentAiFeedbackEventRepository.class);
        SubmissionDiagnosisFactRepository factRepository = mock(SubmissionDiagnosisFactRepository.class);
        SubmissionEvidenceBackfillBatchRepository batchRepository = mock(SubmissionEvidenceBackfillBatchRepository.class);
        SubmissionDiagnosisFactProjector projector = mock(SubmissionDiagnosisFactProjector.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        Submission submission = Submission.builder().id(7L).assignmentId(3L).studentProfileId(null).problemId(9L)
                .languageId(71).sourceCode("print(1)").verdict(Submission.Verdict.WRONG_ANSWER).build();
        SubmissionAnalysis analysis = SubmissionAnalysis.builder().id(8L).submissionId(7L).analysisSource("TEST")
                .scenario("WA").headline("边界错误").summary("摘要").reportMarkdown("报告")
                .reportJson(objectMapper.writeValueAsString(com.onlinejudge.submission.dto.SubmissionAnalysisResponse.builder()
                        .submissionId(7L)
                        .basicLayerAdvice(List.of(com.onlinejudge.submission.dto.SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                                .issueId("I1").title("边界错误").knowledgePath(List.of()).knowledgePathStatus("UNCLASSIFIED").build()))
                        .build()))
                .build();
        StudentAiFeedback feedback = StudentAiFeedback.builder().id(10L).submissionId(7L).status("READY").source("MODEL")
                .feedbackJson("{}").build();
        StudentAiFeedbackEvent event = StudentAiFeedbackEvent.builder().id(11L).submissionId(7L)
                .eventType(StudentAiFeedbackEvent.EVENT_READY).build();
        AtomicReference<StudentAiFeedbackRevision> storedRevision = new AtomicReference<>();

        when(submissionRepository.findAll()).thenReturn(List.of(submission));
        when(analysisRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(analysis));
        when(feedbackRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(feedback));
        when(eventRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(event));
        when(factRepository.findByAnalysisId(8L)).thenReturn(List.of());
        when(projector.project(any(), any())).thenReturn(
                new SubmissionDiagnosisFactProjector.ProjectionResult(1, 0),
                new SubmissionDiagnosisFactProjector.ProjectionResult(0, 1));
        when(revisionRepository.findBySubmissionIdAndGenerationKey(any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(storedRevision.get()));
        when(revisionRepository.findTopBySubmissionIdOrderByVersionNumberDesc(7L))
                .thenAnswer(invocation -> Optional.ofNullable(storedRevision.get()));
        when(revisionRepository.save(any())).thenAnswer(invocation -> {
            StudentAiFeedbackRevision revision = invocation.getArgument(0);
            revision.setId(12L);
            storedRevision.set(revision);
            return revision;
        });
        when(feedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(any())).thenAnswer(invocation -> {
            SubmissionEvidenceBackfillBatch record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        SubmissionEvidenceBackfillService service = new SubmissionEvidenceBackfillService(
                submissionRepository, analysisRepository, feedbackRepository, revisionRepository, eventRepository,
                factRepository, batchRepository, projector, objectMapper
        );

        var first = service.backfill(0L, 100);
        var second = service.backfill(0L, 100);

        assertThat(first.getIdentityMissingCount()).isEqualTo(1);
        assertThat(first.getFeedbackVersionCreatedCount()).isEqualTo(1);
        assertThat(first.getDiagnosisFactCreatedCount()).isEqualTo(1);
        assertThat(first.getFeedbackEventLinkedCount()).isEqualTo(1);
        assertThat(second.getFeedbackVersionCreatedCount()).isZero();
        assertThat(second.getFeedbackEventLinkedCount()).isZero();
        assertThat(storedRevision.get().getVersionNumber()).isEqualTo(1);
        assertThat(event.getFeedbackRevisionId()).isEqualTo(12L);
    }
}
