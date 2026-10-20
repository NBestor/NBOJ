package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherActionPriorityAnalyzerTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final TeacherActionPriorityAnalyzer analyzer = new TeacherActionPriorityAnalyzer(
            new DiagnosisReportReader(new ObjectMapper(), taxonomy),
            taxonomy
    );

    @Test
    void prioritizesIssuesWithExecutionAndImpactRisk() {
        Submission first = submission(11L, 1L);
        Submission second = submission(12L, 2L);
        Map<String, TeacherActionPriorityAnalyzer.PrioritySignal> signals = analyzer.summarize(
                List.of(first, second),
                Map.of(
                        11L, analysis(11L, 0.52, "REPEATED_STUCK"),
                        12L, analysis(12L, 0.85, "FIRST_ATTEMPT")
                ),
                Map.of(11L, StudentTrajectoryResponse.LearningInterventionImpact.builder()
                        .status("SAME_ISSUE")
                        .build()),
                Map.of(11L, StudentTrajectoryResponse.LearningActionEvidence.builder()
                        .executionStatus("CONTRADICTED")
                        .build()),
                Map.of(12L, CoachInteractionSummaryResponse.builder()
                        .answerQualitySignal(CoachInteractionSummaryResponse.CoachAnswerQualitySignal.builder()
                                .qualityLevel("DIRECTION_ONLY")
                                .build())
                        .build())
        );

        TeacherActionPriorityAnalyzer.PrioritySignal signal = signals.get("OFF_BY_ONE");

        assertThat(signal).isNotNull();
        assertThat(signal.getAffectedStudentCount()).isEqualTo(2);
        assertThat(signal.getRepeatedOrEscalatedCount()).isEqualTo(1);
        assertThat(signal.getUnexecutedActionCount()).isEqualTo(1);
        assertThat(signal.getUnresolvedAfterInterventionCount()).isEqualTo(1);
        assertThat(signal.getLowConfidenceCount()).isEqualTo(1);
        assertThat(signal.getLowQualityCoachAnswerCount()).isEqualTo(1);
        assertThat(signal.getLabel()).isEqualTo("优先课堂干预");
        assertThat(signal.getReason()).contains("学习动作未被观察到有效执行", "干预后仍未解决", "coach 回答缺少可验证证据");
    }

    private Submission submission(Long id, Long studentProfileId) {
        return Submission.builder()
                .id(id)
                .assignmentId(7L)
                .studentProfileId(studentProfileId)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(id))
                .build();
    }

    private SubmissionAnalysis analysis(Long submissionId, double confidence, String phase) {
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("diagnosis")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"],
                          "confidence": %s,
                          "learningTrajectorySignal": {
                            "phase": "%s",
                            "label": "phase",
                            "needsTeacherAttention": %s
                          }
                        }
                        """.formatted(confidence, phase, "REPEATED_STUCK".equals(phase)))
                .build();
    }
}
