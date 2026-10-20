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

class MasteryGrowthAnalyzerTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final DiagnosisReportReader diagnosisReportReader = new DiagnosisReportReader(new ObjectMapper(), taxonomy);
    private final MasteryGrowthAnalyzer analyzer = new MasteryGrowthAnalyzer(
            diagnosisReportReader,
            taxonomy,
            new AbilitySignalAnalyzer(diagnosisReportReader, taxonomy)
    );

    @Test
    void detectsGrowingAfterFailureThenAccepted() {
        var signal = analyzer.analyze(
                List.of(
                        submission(1L, 101L, Submission.Verdict.ACCEPTED, 1),
                        submission(2L, 101L, Submission.Verdict.WRONG_ANSWER, 6)
                ),
                Map.of(2L, analysis(2L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"))
        );

        assertThat(signal.getStatus()).isEqualTo(MasteryGrowthAnalyzer.STATUS_GROWING);
        assertThat(signal.getRecentAcceptedCount()).isEqualTo(1);
        assertThat(signal.getGrowthScore()).isGreaterThan(0.5);
        assertThat(signal.getRecommendedAction()).contains("迁移验证");
    }

    @Test
    void detectsTransferConfirmedAcrossAcceptedProblems() {
        var signal = analyzer.analyze(
                List.of(
                        submission(1L, 101L, Submission.Verdict.ACCEPTED, 1),
                        submission(2L, 102L, Submission.Verdict.ACCEPTED, 3),
                        submission(3L, 101L, Submission.Verdict.WRONG_ANSWER, 7)
                ),
                Map.of(
                        1L, analysis(1L, "[\"GENERALIZATION_CHECK\"]", "[]"),
                        2L, analysis(2L, "[\"GENERALIZATION_CHECK\"]", "[]"),
                        3L, analysis(3L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                )
        );

        assertThat(signal.getStatus()).isEqualTo(MasteryGrowthAnalyzer.STATUS_TRANSFER_CONFIRMED);
        assertThat(signal.getCrossProblemEvidenceCount()).isEqualTo(2);
        assertThat(signal.getFocusAbility()).isEqualTo("迁移泛化");
    }

    @Test
    void detectsPlateauForRepeatedFailuresOnSameProblem() {
        var signal = analyzer.analyze(
                List.of(
                        submission(1L, 101L, Submission.Verdict.WRONG_ANSWER, 1),
                        submission(2L, 101L, Submission.Verdict.WRONG_ANSWER, 3),
                        submission(3L, 101L, Submission.Verdict.WRONG_ANSWER, 7)
                ),
                Map.of(
                        1L, analysis(1L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        2L, analysis(2L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        3L, analysis(3L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                )
        );

        assertThat(signal.getStatus()).isEqualTo(MasteryGrowthAnalyzer.STATUS_PLATEAU);
        assertThat(signal.getPlateauCount()).isGreaterThanOrEqualTo(3);
        assertThat(analyzer.isRisk(signal)).isTrue();
    }

    @Test
    void detectsRegressionAfterAcceptedThenFailures() {
        var signal = analyzer.analyze(
                List.of(
                        submission(1L, 101L, Submission.Verdict.WRONG_ANSWER, 1),
                        submission(2L, 101L, Submission.Verdict.WRONG_ANSWER, 3),
                        submission(3L, 101L, Submission.Verdict.ACCEPTED, 8)
                ),
                Map.of(
                        1L, analysis(1L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        2L, analysis(2L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                )
        );

        assertThat(signal.getStatus()).isEqualTo(MasteryGrowthAnalyzer.STATUS_REGRESSION);
        assertThat(signal.getRegressionCount()).isEqualTo(2);
        assertThat(signal.getRecommendedAction()).contains("对比");
    }

    @Test
    void detectsSpiralReviewNeededAcrossProblems() {
        var signal = analyzer.analyze(
                List.of(
                        submission(1L, 101L, Submission.Verdict.WRONG_ANSWER, 1),
                        submission(2L, 102L, Submission.Verdict.WRONG_ANSWER, 3),
                        submission(3L, 103L, Submission.Verdict.WRONG_ANSWER, 7)
                ),
                Map.of(
                        1L, analysis(1L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        2L, analysis(2L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        3L, analysis(3L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]")
                )
        );

        assertThat(signal.getStatus()).isEqualTo(MasteryGrowthAnalyzer.STATUS_SPIRAL_REVIEW_NEEDED);
        assertThat(signal.getFocusAbility()).isEqualTo("循环与边界");
        assertThat(signal.getEvidenceRefs()).contains("fine_tag:OFF_BY_ONE");
        assertThat(signal.isNeedsTeacherAttention()).isTrue();
    }

    private Submission submission(Long id, Long problemId, Submission.Verdict verdict, int minutesAgo) {
        return Submission.builder()
                .id(id)
                .studentProfileId(1L)
                .assignmentId(7L)
                .problemId(problemId)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0).minusMinutes(minutesAgo))
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
