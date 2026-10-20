package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringMisconceptionAnalyzerTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final DiagnosisReportReader diagnosisReportReader = new DiagnosisReportReader(new ObjectMapper(), taxonomy);
    private final RecurringMisconceptionAnalyzer analyzer = new RecurringMisconceptionAnalyzer(diagnosisReportReader, taxonomy);

    @Test
    void detectsCrossProblemRecurringFineGrainedMisconception() {
        Submission first = submission(11L, 7L, 101L, 1);
        Submission second = submission(12L, 7L, 102L, 2);

        var signal = analyzer.analyze(
                List.of(second, first),
                Map.of(
                        first.getId(), analysis(first.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        second.getId(), analysis(second.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                )
        );

        assertThat(signal.getStatus()).isEqualTo(RecurringMisconceptionAnalyzer.STATUS_RECURRING);
        assertThat(signal.getFineGrainedTag()).isEqualTo("OFF_BY_ONE");
        assertThat(signal.getAbilityPoint()).isEqualTo("循环与边界");
        assertThat(signal.getProblemCount()).isEqualTo(2);
        assertThat(signal.getEvidenceRefs()).contains("recurring-misconception:submission:12");
    }

    @Test
    void singleProblemRepeatedMisconceptionOnlyNeedsWatch() {
        Submission first = submission(21L, 7L, 101L, 1);
        Submission second = submission(22L, 7L, 101L, 2);
        Submission third = submission(23L, 7L, 101L, 3);

        var signal = analyzer.analyze(
                List.of(third, second, first),
                Map.of(
                        first.getId(), analysis(first.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        second.getId(), analysis(second.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        third.getId(), analysis(third.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                )
        );

        assertThat(signal.getStatus()).isEqualTo(RecurringMisconceptionAnalyzer.STATUS_WATCH);
        assertThat(signal.getProblemCount()).isEqualTo(1);
        assertThat(analyzer.isActionable(signal)).isFalse();
    }

    @Test
    void singleProblemFourFailuresStillDoesNotEscalateToLongTermRecurring() {
        Submission first = submission(41L, 7L, 101L, 1);
        Submission second = submission(42L, 7L, 101L, 2);
        Submission third = submission(43L, 7L, 101L, 3);
        Submission fourth = submission(44L, 7L, 101L, 4);

        var signal = analyzer.analyze(
                List.of(fourth, third, second, first),
                Map.of(
                        first.getId(), analysis(first.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        second.getId(), analysis(second.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        third.getId(), analysis(third.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        fourth.getId(), analysis(fourth.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                )
        );

        assertThat(signal.getStatus()).isEqualTo(RecurringMisconceptionAnalyzer.STATUS_WATCH);
        assertThat(signal.getProblemCount()).isEqualTo(1);
        assertThat(signal.isNeedsTeacherAttention()).isFalse();
        assertThat(analyzer.isActionable(signal)).isFalse();
    }

    @Test
    void crossAssignmentRecurringMisconceptionEscalates() {
        Submission first = submission(31L, 7L, 101L, 1);
        Submission second = submission(32L, 8L, 102L, 2);

        var signal = analyzer.analyze(
                List.of(second, first),
                Map.of(
                        first.getId(), analysis(first.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        second.getId(), analysis(second.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                )
        );

        assertThat(signal.getStatus()).isEqualTo(RecurringMisconceptionAnalyzer.STATUS_ESCALATE);
        assertThat(signal.isNeedsTeacherAttention()).isTrue();
        assertThat(signal.getAssignmentCount()).isEqualTo(2);
    }

    private Submission submission(Long id, Long assignmentId, Long problemId, int minuteOffset) {
        return Submission.builder()
                .id(id)
                .assignmentId(assignmentId)
                .studentProfileId(1L)
                .problemId(problemId)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
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
}
