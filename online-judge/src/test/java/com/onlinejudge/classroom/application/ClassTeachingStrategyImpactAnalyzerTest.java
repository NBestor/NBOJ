package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import com.onlinejudge.classroom.dto.AssignmentOverviewResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClassTeachingStrategyImpactAnalyzerTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final DiagnosisReportReader diagnosisReportReader = new DiagnosisReportReader(new ObjectMapper(), taxonomy);
    private final ClassTeachingStrategyImpactAnalyzer analyzer = new ClassTeachingStrategyImpactAnalyzer(diagnosisReportReader);

    @Test
    void noFeedbackAsksTeacherToConfirmStrategy() {
        var impact = analyzer.analyze(signal(), null, List.of(), Map.of(), List.of());

        assertThat(impact.getStatus()).isEqualTo(ClassTeachingStrategyImpactAnalyzer.STATUS_NO_FEEDBACK);
        assertThat(impact.getRecommendedAction()).contains("采纳");
        assertThat(impact.getEvidenceRefs()).contains("class_strategy:strategy:7:whole-class-mini-lesson:off-by-one");
    }

    @Test
    void dismissedFeedbackStopsImpactEvaluation() {
        var impact = analyzer.analyze(
                signal(),
                feedback(ClassReviewFeedbackService.ACTION_DISMISSED, 0),
                List.of(),
                Map.of(),
                List.of("OFF_BY_ONE")
        );

        assertThat(impact.getStatus()).isEqualTo(ClassTeachingStrategyImpactAnalyzer.STATUS_DISMISSED);
        assertThat(impact.getFeedbackActionType()).isEqualTo(ClassReviewFeedbackService.ACTION_DISMISSED);
    }

    @Test
    void acceptedFeedbackWithoutFollowupWaitsForEvidence() {
        var impact = analyzer.analyze(
                signal(),
                feedback(ClassReviewFeedbackService.ACTION_ACCEPTED, 0),
                List.of(submission(11L, Submission.Verdict.WRONG_ANSWER, -5)),
                Map.of(),
                List.of("OFF_BY_ONE")
        );

        assertThat(impact.getStatus()).isEqualTo(ClassTeachingStrategyImpactAnalyzer.STATUS_WAITING_FOLLOWUP);
        assertThat(impact.getSummary()).contains("还没有观察到");
    }

    @Test
    void acceptedFollowupMarksImproved() {
        Submission followup = submission(12L, Submission.Verdict.ACCEPTED, 10);

        var impact = analyzer.analyze(
                signal(),
                feedback(ClassReviewFeedbackService.ACTION_ACCEPTED, 0),
                List.of(followup),
                Map.of(),
                List.of("OFF_BY_ONE")
        );

        assertThat(impact.getStatus()).isEqualTo(ClassTeachingStrategyImpactAnalyzer.STATUS_IMPROVED);
        assertThat(impact.getFollowupSubmissionId()).isEqualTo(12L);
        assertThat(impact.isNeedsEscalation()).isFalse();
    }

    @Test
    void sameTagAfterFeedbackMarksStillStuck() {
        Submission followup = submission(13L, Submission.Verdict.WRONG_ANSWER, 10);
        SubmissionAnalysis analysis = analysis(13L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]");

        var impact = analyzer.analyze(
                signal(),
                feedback(ClassReviewFeedbackService.ACTION_MODIFIED, 0),
                List.of(followup),
                Map.of(13L, analysis),
                List.of("OFF_BY_ONE")
        );

        assertThat(impact.getStatus()).isEqualTo(ClassTeachingStrategyImpactAnalyzer.STATUS_STILL_STUCK);
        assertThat(impact.isNeedsEscalation()).isTrue();
        assertThat(impact.getMatchedTags()).contains("OFF_BY_ONE");
    }

    @Test
    void differentTagAfterFeedbackMarksShifted() {
        Submission followup = submission(14L, Submission.Verdict.WRONG_ANSWER, 10);
        SubmissionAnalysis analysis = analysis(14L, "[\"IO_FORMAT\"]", "[\"INPUT_PARSING\"]");

        var impact = analyzer.analyze(
                signal(),
                feedback(ClassReviewFeedbackService.ACTION_ACCEPTED, 0),
                List.of(followup),
                Map.of(14L, analysis),
                List.of("OFF_BY_ONE")
        );

        assertThat(impact.getStatus()).isEqualTo(ClassTeachingStrategyImpactAnalyzer.STATUS_SHIFTED);
        assertThat(impact.getMatchedTags()).contains("INPUT_PARSING");
    }

    private AssignmentOverviewResponse.ClassTeachingStrategySignal signal() {
        return AssignmentOverviewResponse.ClassTeachingStrategySignal.builder()
                .strategyKey("strategy:7:whole-class-mini-lesson:off-by-one")
                .status(ClassTeachingStrategyAnalyzer.STATUS_WHOLE_CLASS_MINI_LESSON)
                .focusAbility("循环与边界")
                .focusTag("OFF_BY_ONE")
                .evidenceRefs(List.of("submission:11"))
                .sourceSignals(List.of("class_issue:OFF_BY_ONE"))
                .build();
    }

    private ClassReviewFeedback feedback(String actionType, int minuteOffset) {
        return ClassReviewFeedback.builder()
                .id(21L)
                .assignmentId(7L)
                .suggestionKey("strategy:7:whole-class-mini-lesson:off-by-one")
                .actionType(actionType)
                .evidenceTags("[\"OFF_BY_ONE\"]")
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
                .build();
    }

    private Submission submission(Long id, Submission.Verdict verdict, int minuteOffset) {
        return Submission.builder()
                .id(id)
                .assignmentId(7L)
                .studentProfileId(100L + id)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
                .build();
    }

    private SubmissionAnalysis analysis(Long submissionId, String issueTags, String fineTags) {
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("analysis")
                .summary("analysis")
                .reportMarkdown("analysis")
                .reportJson("""
                        {
                          "issueTags": %s,
                          "fineGrainedTags": %s
                        }
                        """.formatted(issueTags, fineTags))
                .build();
    }
}
