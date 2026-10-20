package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.SubmissionIssueTransition;
import com.onlinejudge.submission.dto.StudentAiFeedbackResponse;
import com.onlinejudge.submission.persistence.SubmissionIssueTransitionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentIssueLifecycleReadServiceTest {

    @Test
    void enrichesCurrentItemsAndKeepsRecoveredHistoricalItems() {
        SubmissionIssueTransitionRepository repository = mock(SubmissionIssueTransitionRepository.class);
        SubmissionEvidenceProperties properties = new SubmissionEvidenceProperties();
        StudentIssueLifecycleReadService service = new StudentIssueLifecycleReadService(
                repository,
                new IssuePointKeyFactory(),
                properties,
                new ObjectMapper()
        );
        SubmissionIssueTransition current = transition(
                "mistake:point-key-v1:mp-boundary", "NEW", "边界错误", 31L, 2, 2
        );
        SubmissionIssueTransition recovered = transition(
                "mistake:point-key-v1:mp-input", "RECOVERED", "输入读取", null, 4, 3
        );
        when(repository.findByCurrentSubmissionIdOrderByDisplayCategoryAscTitleAsc(9L))
                .thenReturn(List.of(current, recovered));
        when(repository.findByStudentProfileIdOrderByCurrentSubmissionIdAsc(11L))
                .thenReturn(List.of(current, recovered));
        StudentAiFeedbackResponse feedback = StudentAiFeedbackResponse.builder()
                .submissionId(9L)
                .status("READY")
                .repairItems(List.of(StudentAiFeedbackResponse.FeedbackItem.builder()
                        .title("边界错误")
                        .mistakePointId("MP_BOUNDARY")
                        .knowledgePath(List.of("循环", "边界"))
                        .build()))
                .improvementItems(List.of())
                .build();

        service.hydrate(9L, feedback);

        assertThat(feedback.getRepairItems()).singleElement().satisfies(item -> {
            assertThat(item.getNormalizedPointKey()).isEqualTo("mistake:point-key-v1:mp-boundary");
            assertThat(item.getChangeStatus()).isEqualTo("NEW");
            assertThat(item.getEffectiveOccurrenceCount()).isEqualTo(2);
        });
        assertThat(feedback.getIssueChanges()).hasSize(2);
        assertThat(feedback.getIssueChanges()).extracting(StudentAiFeedbackResponse.IssueLifecycleItem::getChangeStatus)
                .containsExactly("NEW", "RECOVERED");
        assertThat(feedback.getIssueChangeSummary().getNewCount()).isEqualTo(1);
        assertThat(feedback.getIssueChangeSummary().getRecoveredCount()).isEqualTo(1);
    }

    private SubmissionIssueTransition transition(
            String key,
            String status,
            String title,
            Long currentFactId,
            long raw,
            long effective
    ) {
        return SubmissionIssueTransition.builder()
                .studentProfileId(11L)
                .assignmentId(7L)
                .problemId(21L)
                .currentSubmissionId(9L)
                .previousSubmissionId(8L)
                .currentFactId(currentFactId)
                .normalizedPointKey(key)
                .pointKeySource("FORMAL_ID")
                .factType("REPAIR")
                .displayCategory("REPAIR")
                .title(title)
                .transitionType(status)
                .personalLabel("NEW".equals(status) ? "SINGLE_OBSERVATION" : "RECOVERED")
                .rawOccurrenceCount(raw)
                .effectiveOccurrenceCount(effective)
                .consecutiveEffectiveCount("NEW".equals(status) ? 1 : 0)
                .affectedProblemCount(1)
                .effectiveAttempt(true)
                .evidenceSubmissionIdsJson("[7,8,9]")
                .projectionVersion("issue-lifecycle-v1")
                .build();
    }
}
