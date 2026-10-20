package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.dto.CoachImpactResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoachImpactAnalyzerTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CoachImpactAnalyzer analyzer = new CoachImpactAnalyzer(
            new DiagnosisReportReader(objectMapper, taxonomy),
            taxonomy
    );

    @Test
    void marksFollowupAcceptedAfterAnsweredCoachPrompt() {
        Submission coached = submission(11L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(12L, Submission.Verdict.ACCEPTED, 8);

        CoachImpactResponse impact = analyzer.summarizeByCoachedSubmission(
                List.of(followup, coached),
                Map.of(11L, analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")),
                List.of(prompt(1L, 11L, "我会手推 n=1"))
        ).get(11L);

        assertThat(impact.getStatus()).isEqualTo("FOLLOWUP_ACCEPTED");
        assertThat(impact.getStatusLabel()).isEqualTo("追问后通过");
        assertThat(impact.getFollowupSubmissionId()).isEqualTo(12L);
        assertThat(impact.getSummary()).contains("下一次同题提交已通过");
    }

    @Test
    void marksSameIssueWhenFollowupStillHasOriginalFineTag() {
        Submission coached = submission(21L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(22L, Submission.Verdict.WRONG_ANSWER, 8);

        CoachImpactResponse impact = analyzer.summarizeByCoachedSubmission(
                List.of(followup, coached),
                Map.of(
                        21L, analysis(21L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        22L, analysis(22L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                ),
                List.of(prompt(2L, 21L, "我会检查最后一次循环"))
        ).get(21L);

        assertThat(impact.getStatus()).isEqualTo("SAME_ISSUE");
        assertThat(impact.getSummary()).contains("仍命中");
    }

    @Test
    void waitsForFollowupWhenAnsweredButNoLaterSubmissionExists() {
        Submission coached = submission(31L, Submission.Verdict.WRONG_ANSWER, 0);

        CoachImpactResponse impact = analyzer.summarizeByCoachedSubmission(
                List.of(coached),
                Map.of(31L, analysis(31L, "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]")),
                List.of(prompt(3L, 31L, "我会对照输入行"))
        ).get(31L);

        assertThat(impact.getStatus()).isEqualTo("AWAITING_FOLLOWUP");
        assertThat(impact.getFollowupSubmissionId()).isNull();
    }

    @Test
    void ignoresSubmissionsBeforeStudentActuallyAnsweredPrompt() {
        Submission coached = submission(41L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission beforeAnswer = submission(42L, Submission.Verdict.ACCEPTED, 8);
        Submission afterAnswer = submission(43L, Submission.Verdict.WRONG_ANSWER, 16);

        CoachImpactResponse impact = analyzer.summarizeByCoachedSubmission(
                List.of(afterAnswer, beforeAnswer, coached),
                Map.of(
                        41L, analysis(41L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        43L, analysis(43L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                ),
                List.of(prompt(
                        4L,
                        41L,
                        "我在 10:12 才回答追问",
                        LocalDateTime.of(2026, 5, 18, 10, 4),
                        LocalDateTime.of(2026, 5, 18, 10, 12)
                ))
        ).get(41L);

        assertThat(impact.getFollowupSubmissionId()).isEqualTo(43L);
        assertThat(impact.getStatus()).isEqualTo("SAME_ISSUE");
        assertThat(impact.getAnsweredAt()).isEqualTo(LocalDateTime.of(2026, 5, 18, 10, 12));
    }

    @Test
    void fallsBackToPromptCreatedAtForLegacyAnsweredPrompts() {
        Submission coached = submission(51L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(52L, Submission.Verdict.ACCEPTED, 8);

        CoachImpactResponse impact = analyzer.summarizeByCoachedSubmission(
                List.of(followup, coached),
                Map.of(51L, analysis(51L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")),
                List.of(prompt(5L, 51L, "旧数据没有回答时间"))
        ).get(51L);

        assertThat(impact.getStatus()).isEqualTo("FOLLOWUP_ACCEPTED");
        assertThat(impact.getAnsweredAt()).isEqualTo(LocalDateTime.of(2026, 5, 18, 10, 4));
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

    private SubmissionAnalysis analysis(Long submissionId, String issueTags, String fineTags) {
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
                          "fineGrainedTags": %s
                        }
                        """.formatted(issueTags, fineTags))
                .build();
    }

    private CoachPrompt prompt(Long id, Long submissionId, String answer) {
        return prompt(id, submissionId, answer, LocalDateTime.of(2026, 5, 18, 10, 4), null);
    }

    private CoachPrompt prompt(Long id, Long submissionId, String answer, LocalDateTime createdAt, LocalDateTime answeredAt) {
        return CoachPrompt.builder()
                .id(id)
                .submissionId(submissionId)
                .turnIndex(1)
                .hintPolicy("L2")
                .promptType("SOCRATIC_NEXT_STEP")
                .question("请补一个最小样例")
                .studentAnswer(answer)
                .coachFeedback("回答包含证据")
                .answeredAt(answeredAt)
                .createdAt(createdAt)
                .build();
    }
}
