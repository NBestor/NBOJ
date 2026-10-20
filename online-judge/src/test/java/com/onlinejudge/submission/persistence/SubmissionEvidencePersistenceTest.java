package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.StudentAiFeedbackRevision;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.domain.SubmissionIssueTransition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SubmissionEvidencePersistenceTest {

    @Autowired
    StudentAiFeedbackRevisionRepository revisionRepository;

    @Autowired
    SubmissionDiagnosisFactRepository factRepository;

    @Autowired
    SubmissionIssueTransitionRepository transitionRepository;

    @Test
    void storesMultipleFeedbackVersionsAndAllKnowledgePathStatuses() {
        revisionRepository.save(revision("generation-1", 1, "FAILED"));
        revisionRepository.save(revision("generation-2", 2, "READY"));

        assertThat(revisionRepository.findBySubmissionIdOrderByVersionNumberDesc(7L))
                .extracting(StudentAiFeedbackRevision::getVersionNumber)
                .containsExactly(2, 1);
        assertThat(revisionRepository.findBySubmissionIdAndGenerationKey(7L, "generation-2")).isPresent();

        List<String> statuses = List.of("FORMAL", "PROVISIONAL", "INFERRED", "UNCLASSIFIED");
        for (int index = 0; index < statuses.size(); index++) {
            factRepository.save(SubmissionDiagnosisFact.builder()
                    .submissionId(7L)
                    .analysisId(8L)
                    .factKey("fact-" + index)
                    .issueId("I" + index)
                    .factType("REPAIR")
                    .displayCategory("REPAIR")
                    .normalizedPointKey("mistake:point-key-v1:mp-" + index)
                    .pointKeySource("FORMAL_ID")
                    .pointKeyVersion("point-key-v1")
                    .title("问题" + index)
                    .knowledgePathJson(index == 3 ? "[]" : "[\"基础\",\"循环\"]")
                    .knowledgePathStatus(statuses.get(index))
                    .libraryFit(index == 0 ? "HIT" : "UNKNOWN")
                    .projectionStatus("READY")
                    .build());
        }

        assertThat(factRepository.findByAnalysisId(8L))
                .extracting(SubmissionDiagnosisFact::getKnowledgePathStatus)
                .containsExactlyInAnyOrderElementsOf(statuses);
        assertThat(factRepository.existsByFactKey("fact-0")).isTrue();

        transitionRepository.save(SubmissionIssueTransition.builder()
                .transitionKey("7:mistake:point-key-v1:mp-0:NEW")
                .studentProfileId(11L)
                .assignmentId(5L)
                .problemId(3L)
                .currentSubmissionId(7L)
                .normalizedPointKey("mistake:point-key-v1:mp-0")
                .pointKeySource("FORMAL_ID")
                .factType("REPAIR")
                .displayCategory("REPAIR")
                .transitionType("NEW")
                .personalLabel("SINGLE_OBSERVATION")
                .rawOccurrenceCount(1)
                .effectiveOccurrenceCount(1)
                .consecutiveEffectiveCount(1)
                .affectedProblemCount(1)
                .effectiveAttempt(true)
                .projectionVersion("issue-lifecycle-v1")
                .build());
        assertThat(transitionRepository.findByCurrentSubmissionIdOrderByDisplayCategoryAscTitleAsc(7L))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getTransitionType()).isEqualTo("NEW");
                    assertThat(item.getEffectiveOccurrenceCount()).isEqualTo(1);
                });
    }

    private StudentAiFeedbackRevision revision(String generationKey, int version, String status) {
        return StudentAiFeedbackRevision.builder()
                .submissionId(7L)
                .versionNumber(version)
                .generationKey(generationKey)
                .status(status)
                .source("MODEL")
                .feedbackJson("{}")
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
