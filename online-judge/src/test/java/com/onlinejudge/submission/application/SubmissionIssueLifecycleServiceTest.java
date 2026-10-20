package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.domain.SubmissionIssueTransition;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.persistence.SubmissionIssueTransitionRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionIssueLifecycleServiceTest {

    @Test
    void projectsCompleteLifecycleAndExcludesIdenticalCodeFromEffectiveWeight() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionAnalysisRepository analysisRepository = mock(SubmissionAnalysisRepository.class);
        SubmissionDiagnosisFactRepository factRepository = mock(SubmissionDiagnosisFactRepository.class);
        SubmissionIssueTransitionRepository transitionRepository = mock(SubmissionIssueTransitionRepository.class);
        SubmissionEvidenceProperties properties = new SubmissionEvidenceProperties();
        SubmissionIssueLifecycleService service = new SubmissionIssueLifecycleService(
                submissionRepository,
                analysisRepository,
                factRepository,
                transitionRepository,
                new IssuePointKeyFactory(),
                properties,
                new ObjectMapper()
        );
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        Submission first = submission(1L, "same", Submission.Verdict.WRONG_ANSWER, base);
        Submission duplicate = submission(2L, "same", Submission.Verdict.WRONG_ANSWER, base.plusMinutes(1));
        Submission notObserved = submission(3L, "changed-1", Submission.Verdict.WRONG_ANSWER, base.plusMinutes(2));
        Submission recurred = submission(4L, "changed-2", Submission.Verdict.WRONG_ANSWER, base.plusMinutes(3));
        Submission recovered = submission(5L, "fixed", Submission.Verdict.ACCEPTED, base.plusMinutes(4));
        Submission incomparable = submission(6L, "unknown", Submission.Verdict.WRONG_ANSWER, base.plusMinutes(5));
        List<Submission> scope = List.of(first, duplicate, notObserved, recurred, recovered, incomparable);
        when(submissionRepository.findById(6L)).thenReturn(Optional.of(incomparable));
        when(submissionRepository.findByAssignmentIdAndProblemIdAndStudentProfileIdOrderBySubmittedAtDesc(7L, 21L, 11L))
                .thenReturn(scope);
        when(factRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(
                fact(101L, 1L), fact(102L, 2L), fact(104L, 4L)
        ));
        when(analysisRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(
                analysis(1L), analysis(2L), analysis(3L), analysis(4L), analysis(5L)
        ));
        when(transitionRepository.findByCurrentSubmissionIdIn(anyList())).thenReturn(List.of());

        var result = service.rebuildForSubmission(6L);

        assertThat(result.transitionCount()).isEqualTo(6);
        assertThat(result.incomparableCount()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionIssueTransition>> captor = ArgumentCaptor.forClass(List.class);
        verify(transitionRepository).saveAll(captor.capture());
        List<SubmissionIssueTransition> transitions = captor.getValue();
        assertThat(transitions).extracting(SubmissionIssueTransition::getTransitionType)
                .containsExactly("NEW", "PERSISTED", "NOT_OBSERVED", "RECURRED", "RECOVERED", "UNCOMPARABLE");
        assertThat(transitions.get(1).getRawOccurrenceCount()).isEqualTo(2);
        assertThat(transitions.get(1).getEffectiveOccurrenceCount()).isEqualTo(1);
        assertThat(transitions.get(1).isEffectiveAttempt()).isFalse();
        assertThat(transitions.get(3).getPersonalLabel()).isEqualTo("RECURRING_ERROR");
        assertThat(transitions.get(4).getPersonalLabel()).isEqualTo("RECOVERED");
    }

    @Test
    void keepsLastEffectiveComparableBaselineAcrossIncompleteSubmission() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionAnalysisRepository analysisRepository = mock(SubmissionAnalysisRepository.class);
        SubmissionDiagnosisFactRepository factRepository = mock(SubmissionDiagnosisFactRepository.class);
        SubmissionIssueTransitionRepository transitionRepository = mock(SubmissionIssueTransitionRepository.class);
        SubmissionIssueLifecycleService service = new SubmissionIssueLifecycleService(
                submissionRepository,
                analysisRepository,
                factRepository,
                transitionRepository,
                new IssuePointKeyFactory(),
                new SubmissionEvidenceProperties(),
                new ObjectMapper()
        );
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        Submission first = submission(1L, "v1", Submission.Verdict.WRONG_ANSWER, base);
        Submission incomplete = submission(2L, "unknown", Submission.Verdict.WRONG_ANSWER, base.plusMinutes(1));
        Submission followup = submission(3L, "v2", Submission.Verdict.WRONG_ANSWER, base.plusMinutes(2));
        when(factRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(fact(101L, 1L), fact(103L, 3L)));
        when(analysisRepository.findBySubmissionIdIn(anyList())).thenReturn(List.of(analysis(1L), analysis(3L)));
        when(transitionRepository.findByCurrentSubmissionIdIn(anyList())).thenReturn(List.of());

        service.rebuildScope(List.of(first, incomplete, followup));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionIssueTransition>> captor = ArgumentCaptor.forClass(List.class);
        verify(transitionRepository).saveAll(captor.capture());
        SubmissionIssueTransition followupTransition = captor.getValue().stream()
                .filter(item -> item.getCurrentSubmissionId().equals(3L))
                .findFirst()
                .orElseThrow();
        assertThat(followupTransition.getPreviousSubmissionId()).isEqualTo(1L);
        assertThat(followupTransition.getTransitionType()).isEqualTo("PERSISTED");
    }

    @Test
    void upgradesTextFingerprintToStableProvisionalIdentityIdempotently() {
        SubmissionDiagnosisFactRepository factRepository = mock(SubmissionDiagnosisFactRepository.class);
        SubmissionIssueLifecycleService service = new SubmissionIssueLifecycleService(
                mock(SubmissionRepository.class),
                mock(SubmissionAnalysisRepository.class),
                factRepository,
                mock(SubmissionIssueTransitionRepository.class),
                new IssuePointKeyFactory(),
                new SubmissionEvidenceProperties(),
                new ObjectMapper()
        );
        SubmissionDiagnosisFact fact = SubmissionDiagnosisFact.builder()
                .id(88L)
                .submissionId(9L)
                .analysisId(10L)
                .factKey("10:REPAIR:I1:0")
                .factType("REPAIR")
                .displayCategory("REPAIR")
                .normalizedPointKey("text:point-key-v1:legacy")
                .pointKeySource("TEXT_FINGERPRINT")
                .pointKeyVersion(IssuePointKeyFactory.VERSION)
                .title("Dijkstra 陈旧状态")
                .provisionalNodeCode("MP_AI_DIJKSTRA_STALE")
                .knowledgePathJson("[\"算法\",\"Dijkstra\",\"陈旧状态\"]")
                .knowledgePathStatus("PROVISIONAL")
                .libraryFit("PARTIAL")
                .projectionStatus("READY")
                .build();

        assertThat(service.normalizeFacts(List.of(fact), false)).isEqualTo(1);
        assertThat(fact.getNormalizedPointKey()).isEqualTo("provisional:point-key-v1:mp-ai-dijkstra-stale");
        assertThat(fact.getPointKeySource()).isEqualTo("PROVISIONAL_ID");
        verify(factRepository).save(fact);
        assertThat(service.normalizeFacts(List.of(fact), false)).isZero();
    }

    private Submission submission(Long id, String source, Submission.Verdict verdict, LocalDateTime submittedAt) {
        return Submission.builder()
                .id(id)
                .assignmentId(7L)
                .problemId(21L)
                .studentProfileId(11L)
                .languageId(71)
                .sourceCode(source)
                .verdict(verdict)
                .submittedAt(submittedAt)
                .build();
    }

    private SubmissionDiagnosisFact fact(Long id, Long submissionId) {
        return SubmissionDiagnosisFact.builder()
                .id(id)
                .submissionId(submissionId)
                .analysisId(1000L + submissionId)
                .factKey("fact-" + id)
                .factType("REPAIR")
                .displayCategory("REPAIR")
                .normalizedPointKey("mistake:point-key-v1:mp-boundary")
                .pointKeySource("FORMAL_ID")
                .pointKeyVersion(IssuePointKeyFactory.VERSION)
                .title("边界错误")
                .mistakePointId("MP_BOUNDARY")
                .knowledgePathJson("[]")
                .knowledgePathStatus("FORMAL")
                .projectionStatus("READY")
                .build();
    }

    private SubmissionAnalysis analysis(Long submissionId) {
        return SubmissionAnalysis.builder().id(1000L + submissionId).submissionId(submissionId).build();
    }
}
