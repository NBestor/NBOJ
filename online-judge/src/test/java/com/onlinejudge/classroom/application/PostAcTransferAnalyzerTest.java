package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostAcTransferAnalyzerTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final DiagnosisReportReader diagnosisReportReader = new DiagnosisReportReader(new ObjectMapper(), taxonomy);
    private final PostAcTransferAnalyzer analyzer = new PostAcTransferAnalyzer(diagnosisReportReader, taxonomy);

    @Test
    void marksAcceptedAfterFailureAsReflectionNeeded() {
        Submission failed = submission(11L, 101L, Submission.Verdict.WRONG_ANSWER, 1);
        Submission accepted = submission(12L, 101L, Submission.Verdict.ACCEPTED, 2);

        var signal = analyzer.analyzeTasks(
                List.of(accepted, failed),
                Map.of(failed.getId(), analysis(failed.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")),
                Map.of(),
                Map.of(101L, problem(101L, "数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")))
        ).get(101L);

        assertThat(signal.getPhase()).isEqualTo(PostAcTransferAnalyzer.PHASE_REFLECTION_NEEDED);
        assertThat(signal.getTargetAbility()).isEqualTo("循环与边界");
        assertThat(signal.getTargetTags()).contains("OFF_BY_ONE");
        assertThat(signal.getEvidenceRefs()).contains("accepted-submission:12");
        assertThat(signal.isNeedsTeacherAttention()).isTrue();
    }

    @Test
    void usesCoachEvidenceAsTransferReady() {
        Submission failed = submission(21L, 101L, Submission.Verdict.WRONG_ANSWER, 1);
        Submission accepted = submission(22L, 101L, Submission.Verdict.ACCEPTED, 2);
        CoachInteractionSummaryResponse coach = CoachInteractionSummaryResponse.builder()
                .submissionId(failed.getId())
                .answered(true)
                .answerQualitySignal(CoachInteractionSummaryResponse.CoachAnswerQualitySignal.builder()
                        .qualityLevel("TRANSFER_READY")
                        .qualityLabel("可复盘迁移")
                        .verifiable(true)
                        .actionStatus("READY_TO_TRANSFER")
                        .summary("学生能解释边界规律和迁移条件。")
                        .build())
                .build();

        var signal = analyzer.analyzeTasks(
                List.of(accepted, failed),
                Map.of(failed.getId(), analysis(failed.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")),
                Map.of(failed.getId(), coach),
                Map.of(101L, problem(101L, "数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")))
        ).get(101L);

        assertThat(signal.getPhase()).isEqualTo(PostAcTransferAnalyzer.PHASE_TRANSFER_READY);
        assertThat(signal.getEvidenceRefs()).contains("coach:submission:21:TRANSFER_READY");
        assertThat(signal.isNeedsTeacherAttention()).isFalse();
    }

    @Test
    void detectsLaterAcceptedProblemAsTransferVerified() {
        Submission firstFail = submission(31L, 101L, Submission.Verdict.WRONG_ANSWER, 1);
        Submission firstAccepted = submission(32L, 101L, Submission.Verdict.ACCEPTED, 2);
        Submission transferAccepted = submission(33L, 102L, Submission.Verdict.ACCEPTED, 3);

        var signals = analyzer.analyzeTasks(
                List.of(transferAccepted, firstAccepted, firstFail),
                Map.of(firstFail.getId(), analysis(firstFail.getId(), "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")),
                Map.of(),
                Map.of(
                        101L, problem(101L, "数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")),
                        102L, problem(102L, "边界统计", List.of("数组"), List.of("边界漏判"), List.of("最大规模"))
                )
        );

        assertThat(signals.get(101L).getPhase()).isEqualTo(PostAcTransferAnalyzer.PHASE_TRANSFER_VERIFIED);
        assertThat(signals.get(101L).getEvidenceRefs()).contains("transfer-submission:33");
    }

    private Submission submission(Long id, Long problemId, Submission.Verdict verdict, int minuteOffset) {
        return Submission.builder()
                .id(id)
                .assignmentId(7L)
                .studentProfileId(1L)
                .problemId(problemId)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
                .build();
    }

    private Problem problem(Long id, String title, List<String> knowledgePoints, List<String> commonMistakes, List<String> boundaryTypes) {
        return Problem.builder()
                .id(id)
                .title(title)
                .description(title)
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .knowledgePoints(knowledgePoints)
                .commonMistakes(commonMistakes)
                .boundaryTypes(boundaryTypes)
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
