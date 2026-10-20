package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class LearningActionEvidenceAnalyzerTest {

    private final LearningActionEvidenceAnalyzer analyzer = new LearningActionEvidenceAnalyzer(
            new DiagnosisReportReader(new ObjectMapper(), new DiagnosisTaxonomy())
    );

    @Test
    void observesExecutionWhenFollowupIsAccepted() {
        Submission intervention = submission(11L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(12L, Submission.Verdict.ACCEPTED, 8);

        StudentTrajectoryResponse.LearningActionEvidence evidence = analyzer.summarizeByInterventionSubmission(
                List.of(followup, intervention),
                Map.of(11L, analysis(11L, "VARIABLE_TRACE")),
                Map.of(11L, impact("FOLLOWUP_ACCEPTED", 12L))
        ).get(11L);

        assertThat(evidence.getExecutionStatus()).isEqualTo("OBSERVED");
        assertThat(evidence.getStatusLabel()).contains("已观察");
        assertThat(evidence.getEvidenceRefs()).contains("followup:submission:12", "impact:FOLLOWUP_ACCEPTED");
        assertThat(evidence.getNextAdjustment()).contains("复盘");
    }

    @Test
    void marksContradictedWhenSameIssuePersists() {
        Submission intervention = submission(21L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(22L, Submission.Verdict.WRONG_ANSWER, 8);

        StudentTrajectoryResponse.LearningActionEvidence evidence = analyzer.summarizeByInterventionSubmission(
                List.of(followup, intervention),
                Map.of(
                        21L, analysis(21L, "MIN_CASE_TRACE"),
                        22L, analysisWithoutIntervention(22L)
                ),
                Map.of(21L, impact("SAME_ISSUE", 22L))
        ).get(21L);

        assertThat(evidence.getExecutionStatus()).isEqualTo("CONTRADICTED");
        assertThat(evidence.getObservedEvidence()).contains("可能没有真正完成");
        assertThat(evidence.getNextAdjustment()).contains("降低提示粒度");
    }

    @Test
    void waitsWhenThereIsNoFollowupSubmission() {
        Submission intervention = submission(31L, Submission.Verdict.WRONG_ANSWER, 0);

        StudentTrajectoryResponse.LearningActionEvidence evidence = analyzer.summarizeByInterventionSubmission(
                List.of(intervention),
                Map.of(31L, analysis(31L, "COUNTEREXAMPLE")),
                Map.of()
        ).get(31L);

        assertThat(evidence.getExecutionStatus()).isEqualTo("NOT_OBSERVED");
        assertThat(evidence.getStatusLabel()).contains("等待");
        assertThat(evidence.getConfidence()).isEqualTo(0.5);
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

    private SubmissionAnalysis analysis(Long submissionId, String interventionType) {
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
                          "learningInterventionPlan": {
                            "interventionType": "%s",
                            "goal": "Make the diagnosis observable.",
                            "studentTask": "Trace one minimal case.",
                            "checkQuestion": "Where does it diverge?",
                            "completionSignal": "Student provides expected and actual values.",
                            "evidenceRefs": ["eval:intervention"],
                            "estimatedMinutes": 7,
                            "answerLeakRisk": "LOW"
                          }
                        }
                        """.formatted(interventionType))
                .build();
    }

    private SubmissionAnalysis analysisWithoutIntervention(Long submissionId) {
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
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build();
    }

    private StudentTrajectoryResponse.LearningInterventionImpact impact(String status, Long followupSubmissionId) {
        return StudentTrajectoryResponse.LearningInterventionImpact.builder()
                .interventionSubmissionId(11L)
                .followupSubmissionId(followupSubmissionId)
                .status(status)
                .build();
    }
}
