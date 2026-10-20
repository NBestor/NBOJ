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

class AbilitySignalAnalyzerTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final AbilitySignalAnalyzer analyzer = new AbilitySignalAnalyzer(
            new DiagnosisReportReader(new ObjectMapper(), taxonomy),
            taxonomy
    );

    @Test
    void summarizesRepeatedAbilityAcrossProblems() {
        Submission first = submission(1L, 101L, Submission.Verdict.WRONG_ANSWER, 3);
        Submission second = submission(2L, 102L, Submission.Verdict.WRONG_ANSWER, 2);
        Submission acceptedReview = submission(3L, 103L, Submission.Verdict.ACCEPTED, 1);

        Map<Long, SubmissionAnalysis> analyses = Map.of(
                1L, analysis(1L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                2L, analysis(2L, "[\"LOOP_BOUNDARY\"]", "[]"),
                3L, analysis(3L, "[\"GENERALIZATION_CHECK\"]", "[]")
        );

        List<AbilitySignalAnalyzer.AbilitySignal> signals = analyzer.summarize(List.of(first, second, acceptedReview), analyses);

        assertThat(signals).first()
                .extracting(
                        AbilitySignalAnalyzer.AbilitySignal::getAbilityPoint,
                        AbilitySignalAnalyzer.AbilitySignal::getTaskCount,
                        AbilitySignalAnalyzer.AbilitySignal::getSubmissionCount
                )
                .containsExactly("循环与边界", 2L, 2L);
        assertThat(signals.get(0).getEvidenceTags()).containsExactly("LOOP_BOUNDARY", "OFF_BY_ONE");
        assertThat(analyzer.hasCrossProblemGap(List.of(first, second, acceptedReview), analyses)).isTrue();
        assertThat(analyzer.buildCrossProblemSummary(List.of(first, second, acceptedReview), analyses))
                .contains("同一作业内多题集中在：循环与边界");
    }

    @Test
    void fallsBackToReviewSignalsWhenFailuresDoNotHaveTags() {
        Submission accepted = submission(1L, 101L, Submission.Verdict.ACCEPTED, 1);
        Map<Long, SubmissionAnalysis> analyses = Map.of(
                1L, analysis(1L, "[\"GENERALIZATION_CHECK\"]", "[]")
        );

        List<AbilitySignalAnalyzer.AbilitySignal> signals = analyzer.summarize(List.of(accepted), analyses);

        assertThat(signals).first()
                .extracting(AbilitySignalAnalyzer.AbilitySignal::getAbilityPoint)
                .isEqualTo("迁移泛化");
        assertThat(analyzer.buildCrossProblemSummary(List.of(accepted), analyses))
                .contains("通过后复盘");
    }

    @Test
    void usesEvidenceFallbackWhenTagsAreUnknown() {
        Submission failed = submission(1L, 101L, Submission.Verdict.WRONG_ANSWER, 1);
        Map<Long, SubmissionAnalysis> analyses = Map.of(
                1L, analysis(1L, "[\"NOT_REAL\"]", "[\"ALSO_NOT_REAL\"]")
        );

        assertThat(analyzer.summarize(List.of(failed), analyses)).first()
                .extracting(AbilitySignalAnalyzer.AbilitySignal::getAbilityPoint)
                .isEqualTo("问题定位");
        assertThat(analyzer.hasCrossProblemGap(List.of(failed), analyses)).isFalse();
    }

    private Submission submission(Long id, Long problemId, Submission.Verdict verdict, int minutesAgo) {
        return Submission.builder()
                .id(id)
                .problemId(problemId)
                .verdict(verdict)
                .sourceCode("print(1)")
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
