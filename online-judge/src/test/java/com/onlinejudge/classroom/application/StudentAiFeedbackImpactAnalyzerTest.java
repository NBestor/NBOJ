package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAiFeedbackImpactAnalyzerTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final StudentAiFeedbackImpactAnalyzer analyzer = new StudentAiFeedbackImpactAnalyzer(
            new DiagnosisReportReader(new ObjectMapper(), taxonomy),
            taxonomy
    );

    @Test
    void marksImprovedWhenFeedbackViewIsFollowedByAcceptedSubmission() {
        Submission feedbackSubmission = submission(11L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(12L, Submission.Verdict.ACCEPTED, 8);

        StudentTrajectoryResponse.AiFeedbackImpact impact = analyzer.summarizeByFeedbackSubmission(
                List.of(followup, feedbackSubmission),
                Map.of(11L, analysis(11L, "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]")),
                List.of(viewedEvent(31L, 11L, 2))
        ).get(11L);

        assertThat(impact.getStatus()).isEqualTo("IMPROVED_AFTER_AI");
        assertThat(impact.getStatusLabel()).isEqualTo("查看建议后改善");
        assertThat(impact.getFollowupSubmissionId()).isEqualTo(12L);
        assertThat(impact.getSummary()).contains("观察到改善");
        assertThat(impact.isNeedsTeacherAttention()).isFalse();
    }

    @Test
    void marksSameIssueWhenFeedbackViewIsFollowedBySameFineTag() {
        Submission feedbackSubmission = submission(21L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(22L, Submission.Verdict.WRONG_ANSWER, 8);

        StudentTrajectoryResponse.AiFeedbackImpact impact = analyzer.summarizeByFeedbackSubmission(
                List.of(followup, feedbackSubmission),
                Map.of(
                        21L, analysis(21L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        22L, analysis(22L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                ),
                List.of(viewedEvent(32L, 21L, 2))
        ).get(21L);

        assertThat(impact.getStatus()).isEqualTo("SAME_ISSUE_AFTER_AI");
        assertThat(impact.getFollowupFineGrainedTag()).isEqualTo("OFF_BY_ONE");
        assertThat(impact.isNeedsTeacherAttention()).isTrue();
        assertThat(impact.getSummary()).contains("教师");
    }

    @Test
    void waitsForFollowupWhenFeedbackWasViewedButNoLaterSubmissionExists() {
        Submission feedbackSubmission = submission(41L, Submission.Verdict.RUNTIME_ERROR, 0);

        StudentTrajectoryResponse.AiFeedbackImpact impact = analyzer.summarizeByFeedbackSubmission(
                List.of(feedbackSubmission),
                Map.of(41L, analysis(41L, "[\"RUNTIME_STABILITY\"]", "[\"EMPTY_INPUT_GUARD\"]")),
                List.of(viewedEvent(33L, 41L, 2))
        ).get(41L);

        assertThat(impact.getStatus()).isEqualTo("AWAITING_FOLLOWUP");
        assertThat(impact.getFollowupSubmissionId()).isNull();
        assertThat(impact.getSummary()).contains("还没有同题后续提交");
    }

    private Submission submission(Long id, Submission.Verdict verdict, int minutesAfter) {
        return Submission.builder()
                .id(id)
                .assignmentId(7L)
                .studentProfileId(41L)
                .problemId(101L)
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
}
