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

class LearningInterventionImpactAnalyzerTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final LearningInterventionImpactAnalyzer analyzer = new LearningInterventionImpactAnalyzer(
            new DiagnosisReportReader(new ObjectMapper(), taxonomy),
            taxonomy
    );

    @Test
    void marksFollowupAcceptedAfterLearningIntervention() {
        Submission intervention = submission(11L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(12L, Submission.Verdict.ACCEPTED, 8);

        StudentTrajectoryResponse.LearningInterventionImpact impact = analyzer.summarizeByInterventionSubmission(
                List.of(followup, intervention),
                Map.of(11L, analysis(11L, "VARIABLE_TRACE", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"))
        ).get(11L);

        assertThat(impact.getStatus()).isEqualTo("FOLLOWUP_ACCEPTED");
        assertThat(impact.getStatusLabel()).isEqualTo("干预后通过");
        assertThat(impact.getFollowupSubmissionId()).isEqualTo(12L);
        assertThat(impact.getInterventionType()).isEqualTo("VARIABLE_TRACE");
        assertThat(impact.getSummary()).contains("观察性改善信号");
    }

    @Test
    void marksSameIssueWhenFollowupStillHasOriginalFineTag() {
        Submission intervention = submission(21L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(22L, Submission.Verdict.WRONG_ANSWER, 8);

        StudentTrajectoryResponse.LearningInterventionImpact impact = analyzer.summarizeByInterventionSubmission(
                List.of(followup, intervention),
                Map.of(
                        21L, analysis(21L, "MIN_CASE_TRACE", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        22L, analysisWithoutIntervention(22L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]")
                )
        ).get(21L);

        assertThat(impact.getStatus()).isEqualTo("SAME_ISSUE");
        assertThat(impact.getFollowupFineGrainedTag()).isEqualTo("OFF_BY_ONE");
        assertThat(impact.getSummary()).contains("更小样例");
    }

    @Test
    void marksIssueShiftedWhenFollowupChangesDiagnosis() {
        Submission intervention = submission(31L, Submission.Verdict.WRONG_ANSWER, 0);
        Submission followup = submission(32L, Submission.Verdict.WRONG_ANSWER, 8);

        StudentTrajectoryResponse.LearningInterventionImpact impact = analyzer.summarizeByInterventionSubmission(
                List.of(followup, intervention),
                Map.of(
                        31L, analysis(31L, "COMPARE_SUBMISSIONS", "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"),
                        32L, analysisWithoutIntervention(32L, "[\"IO_FORMAT\"]", "[\"OUTPUT_FORMAT_DETAIL\"]")
                )
        ).get(31L);

        assertThat(impact.getStatus()).isEqualTo("ISSUE_SHIFTED");
        assertThat(impact.getFollowupIssueTag()).isEqualTo("IO_FORMAT");
        assertThat(impact.getFollowupFineGrainedTag()).isEqualTo("OUTPUT_FORMAT_DETAIL");
    }

    @Test
    void waitsForFollowupWhenNoLaterSubmissionExists() {
        Submission intervention = submission(41L, Submission.Verdict.WRONG_ANSWER, 0);

        StudentTrajectoryResponse.LearningInterventionImpact impact = analyzer.summarizeByInterventionSubmission(
                List.of(intervention),
                Map.of(41L, analysis(41L, "COUNTEREXAMPLE", "[\"ALGORITHM_STRATEGY\"]", "[\"GREEDY_ASSUMPTION\"]"))
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

    private SubmissionAnalysis analysis(Long submissionId, String interventionType, String issueTags, String fineTags) {
        return analysisJson(submissionId, """
                {
                  "issueTags": %s,
                  "fineGrainedTags": %s,
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
                """.formatted(issueTags, fineTags, interventionType));
    }

    private SubmissionAnalysis analysisWithoutIntervention(Long submissionId, String issueTags, String fineTags) {
        return analysisJson(submissionId, """
                {
                  "issueTags": %s,
                  "fineGrainedTags": %s
                }
                """.formatted(issueTags, fineTags));
    }

    private SubmissionAnalysis analysisJson(Long submissionId, String reportJson) {
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("diagnosis")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson(reportJson)
                .build();
    }
}
