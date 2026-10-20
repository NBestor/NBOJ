package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.submission.domain.Submission;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiDependencyAnalyzerTest {

    private final AiDependencyAnalyzer analyzer = new AiDependencyAnalyzer();

    @Test
    void detectsIndependentProgress() {
        var signal = analyzer.analyze(
                List.of(submission(1L, Submission.Verdict.ACCEPTED, 1), submission(2L, Submission.Verdict.WRONG_ANSWER, 2)),
                List.of(),
                List.of()
        );

        assertThat(signal.getStatus()).isEqualTo(AiDependencyAnalyzer.STATUS_INDEPENDENT_PROGRESS);
        assertThat(signal.getIndependentSubmissionCount()).isEqualTo(2);
        assertThat(signal.getIndependentAcceptedCount()).isEqualTo(1);
        assertThat(signal.getIndependenceScore()).isGreaterThan(0.5);
    }

    @Test
    void detectsEffectiveScaffoldWithIndependentAttempt() {
        var signal = analyzer.analyze(
                List.of(submission(1L, Submission.Verdict.ACCEPTED, 1), submission(2L, Submission.Verdict.WRONG_ANSWER, 2)),
                List.of(prompt(11L, 2L)),
                List.of(submittedEvent(21L, 1L, Submission.Verdict.ACCEPTED, 1))
        );

        assertThat(signal.getStatus()).isEqualTo(AiDependencyAnalyzer.STATUS_SCAFFOLD_EFFECTIVE);
        assertThat(signal.getScaffoldedAcceptedCount()).isEqualTo(1);
        assertThat(signal.getIndependentSubmissionCount()).isEqualTo(1);
    }

    @Test
    void detectsDependencyRiskAfterDenseScaffoldFailures() {
        var signal = analyzer.analyze(
                List.of(submission(1L, Submission.Verdict.WRONG_ANSWER, 1), submission(2L, Submission.Verdict.WRONG_ANSWER, 2)),
                List.of(prompt(11L, 1L), prompt(12L, 1L), prompt(13L, 2L)),
                List.of(
                        clickedEvent(20L, 1),
                        clickedEvent(21L, 2),
                        submittedEvent(22L, 1L, Submission.Verdict.WRONG_ANSWER, 3),
                        submittedEvent(23L, 2L, Submission.Verdict.WRONG_ANSWER, 4)
                )
        );

        assertThat(signal.getStatus()).isEqualTo(AiDependencyAnalyzer.STATUS_DEPENDENCY_RISK);
        assertThat(signal.getRecommendationSubmissionCount()).isEqualTo(2);
        assertThat(signal.getRecommendedAction()).contains("独立尝试");
    }

    @Test
    void escalatesLongTermDenseScaffoldWithoutIndependentSubmission() {
        var signal = analyzer.analyze(
                List.of(submission(1L, Submission.Verdict.WRONG_ANSWER, 1), submission(2L, Submission.Verdict.WRONG_ANSWER, 2)),
                List.of(prompt(11L, 1L), prompt(12L, 1L), prompt(13L, 2L), prompt(14L, 2L)),
                List.of(
                        clickedEvent(20L, 1),
                        clickedEvent(21L, 2),
                        submittedEvent(22L, 1L, Submission.Verdict.WRONG_ANSWER, 3),
                        submittedEvent(23L, 2L, Submission.Verdict.WRONG_ANSWER, 4)
                )
        );

        assertThat(signal.getStatus()).isEqualTo(AiDependencyAnalyzer.STATUS_TEACHER_FADE_REVIEW);
        assertThat(signal.isNeedsTeacherAttention()).isTrue();
    }

    private Submission submission(Long id, Submission.Verdict verdict, int minuteOffset) {
        return Submission.builder()
                .id(id)
                .studentProfileId(1L)
                .assignmentId(7L)
                .problemId(101L)
                .verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
                .build();
    }

    private CoachPrompt prompt(Long id, Long submissionId) {
        return CoachPrompt.builder()
                .id(id)
                .submissionId(submissionId)
                .studentProfileId(1L)
                .question("先写一个最小样例。")
                .studentAnswer("我再问一下")
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(id.intValue()))
                .answeredAt(LocalDateTime.of(2026, 5, 18, 10, 1).plusMinutes(id.intValue()))
                .build();
    }

    private StudentRecommendationEvent clickedEvent(Long id, int minuteOffset) {
        return StudentRecommendationEvent.builder()
                .id(id)
                .recommendationToken("rec-" + id)
                .studentProfileId(1L)
                .type("REVIEW")
                .assignmentId(7L)
                .eventType(StudentRecommendationEventService.EVENT_CLICKED)
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
                .build();
    }

    private StudentRecommendationEvent submittedEvent(Long id,
                                                      Long followupSubmissionId,
                                                      Submission.Verdict verdict,
                                                      int minuteOffset) {
        return StudentRecommendationEvent.builder()
                .id(id)
                .recommendationToken("rec-" + id)
                .studentProfileId(1L)
                .type("REVIEW")
                .assignmentId(7L)
                .eventType(StudentRecommendationEventService.EVENT_SUBMITTED)
                .followupSubmissionId(followupSubmissionId)
                .followupVerdict(verdict.name())
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
                .build();
    }
}
