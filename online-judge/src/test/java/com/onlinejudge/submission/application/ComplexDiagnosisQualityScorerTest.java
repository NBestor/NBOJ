package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComplexDiagnosisQualityScorerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ComplexDiagnosisQualityScorer scorer = new ComplexDiagnosisQualityScorer();

    @Test
    void scoresAllComplexMetricsWhenAnalysisMatchesFixtureTruth() throws IOException {
        ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture = firstComplexFixture();
        SubmissionAnalysisResponse analysis = alignedAnalysis(fixture);

        ComplexDiagnosisQualityScorer.Result result = scorer.score(fixture, analysis);

        assertThat(result.complexCase()).isTrue();
        assertThat(result.passed()).isTrue();
        assertThat(result.passedMetricCount()).isEqualTo(6);
        assertThat(result.totalMetricCount()).isEqualTo(6);
        assertThat(result.failedMetrics()).isEmpty();
        assertThat(result.passedMetricSignals()).containsExactly(
                "complexMetric:primaryRootCauseHit",
                "complexMetric:teachingPriorityCorrect",
                "complexMetric:secondaryIssuesNotOverweighted",
                "complexMetric:distractingSignalsIgnored",
                "complexMetric:evidenceGrounded",
                "complexMetric:noFullSolutionLeak"
        );

        ComplexDiagnosisQualityScorer.IntelligenceResult intelligence = scorer.intelligence(result, true, false);
        ComplexDiagnosisQualityScorer.EducationAgentQualityResult educationAgentQuality =
                scorer.educationAgentQuality(fixture, analysis, true, false);
        ComplexDiagnosisQualityScorer.ModelTraceQualityResult modelTraceQuality =
                scorer.modelEducationTrace(fixture, analysisWithNativeTrace(fixture), true, false);
        ComplexDiagnosisQualityScorer.StudentFeedbackResult studentFeedback = scorer.studentFeedback(fixture, analysis, true, false);

        assertThat(intelligence.evaluated()).isTrue();
        assertThat(intelligence.passed()).isTrue();
        assertThat(intelligence.passedMetricSignals()).containsExactly(
                "intelligenceMetric:autonomousRootCauseDiscovery",
                "intelligenceMetric:teachingDecisionQuality",
                "intelligenceMetric:complexSignalPrioritization",
                "intelligenceMetric:distractorResistance",
                "intelligenceMetric:evidenceGroundedReasoning",
                "intelligenceMetric:modelSafetyAndBoundary"
        );
        assertThat(educationAgentQuality.evaluated()).isTrue();
        assertThat(educationAgentQuality.passed()).isTrue();
        assertThat(educationAgentQuality.passedMetricSignals()).containsExactly(
                "educationAgentMetric:primaryReasoningGrounded",
                "educationAgentMetric:blockingPriorityClear",
                "educationAgentMetric:secondarySignalsBalanced",
                "educationAgentMetric:nextActionObservable",
                "educationAgentMetric:safeTeachingBoundary"
        );
        assertThat(modelTraceQuality.evaluated()).isTrue();
        assertThat(modelTraceQuality.passed()).isTrue();
        assertThat(modelTraceQuality.passedMetricSignals()).containsExactly(
                "modelTraceMetric:nativeRootCauseDecisionChecklistApplied",
                "modelTraceMetric:nativePrimaryReasoningGrounded",
                "modelTraceMetric:nativeTeachingPriorityClear",
                "modelTraceMetric:nativeSecondarySignalsBalanced",
                "modelTraceMetric:nativeNextActionObservable",
                "modelTraceMetric:nativeSafetyBoundary"
        );
        assertThat(studentFeedback.evaluated()).isTrue();
        assertThat(studentFeedback.passed()).isTrue();
        assertThat(studentFeedback.passedMetricSignals()).contains("studentFeedbackMetric:blockingPrimaryHit",
                "studentFeedbackMetric:improvementOpportunityUseful",
                "studentFeedbackMetric:studentActionable");
    }

    @Test
    void intelligenceScoreExcludesFallbackEvenWhenLocalComplexMetricsWouldPass() throws Exception {
        var fixture = firstComplexFixture();
        SubmissionAnalysisResponse analysis = alignedAnalysis(fixture);
        ComplexDiagnosisQualityScorer.Result complex = scorer.score(fixture, analysis);

        ComplexDiagnosisQualityScorer.IntelligenceResult intelligence = scorer.intelligence(complex, false, true);
        ComplexDiagnosisQualityScorer.EducationAgentQualityResult educationAgentQuality =
                scorer.educationAgentQuality(fixture, analysis, false, true);
        ComplexDiagnosisQualityScorer.ModelTraceQualityResult modelTraceQuality =
                scorer.modelEducationTrace(fixture, analysisWithNativeTrace(fixture), false, true);

        assertThat(complex.passed()).isTrue();
        assertThat(intelligence.complexCase()).isTrue();
        assertThat(intelligence.evaluated()).isFalse();
        assertThat(intelligence.passed()).isFalse();
        assertThat(intelligence.passedMetricCount()).isZero();
        assertThat(intelligence.totalMetricCount()).isZero();
        assertThat(intelligence.passedMetricSignals()).isEmpty();
        assertThat(educationAgentQuality.complexCase()).isTrue();
        assertThat(educationAgentQuality.evaluated()).isFalse();
        assertThat(educationAgentQuality.passedMetricCount()).isZero();
        assertThat(educationAgentQuality.totalMetricCount()).isZero();
        assertThat(modelTraceQuality.complexCase()).isTrue();
        assertThat(modelTraceQuality.evaluated()).isFalse();
        assertThat(modelTraceQuality.passedMetricCount()).isZero();
        assertThat(modelTraceQuality.totalMetricCount()).isZero();
    }

    @Test
    void modelTraceQualityFailsWhenNativeModelJudgmentIsWeakOrUnsafe() throws IOException {
        ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture = firstComplexFixture();
        SubmissionAnalysisResponse analysis = SubmissionAnalysisResponse.builder()
                .modelEducationTrace(SubmissionAnalysisResponse.ModelEducationTrace.builder()
                        .source("diagnosisDecision")
                        .primaryIssueTag(fixture.primaryRootCause().issueTag())
                        .fineGrainedTag(fixture.primaryRootCause().fineGrainedTag())
                        .evidenceRefs(List.of("invented:evidence"))
                        .primaryReasoning("这里有一些问题。")
                        .secondaryIssues(List.of(SubmissionAnalysisResponse.ModelEducationIssueNote.builder()
                                .message("必须先改这个次要问题。")
                                .issueTag("OUTPUT_FORMAT")
                                .evidenceRefs(fixture.requiredEvidenceRefs())
                                .build()))
                        .teachingPriority("继续看看。")
                        .nextLearningAction("直接改成完整代码。")
                        .nextLearningActionEvidenceRefs(List.of())
                        .answerLeakRisk("LOW")
                        .build())
                .answerLeakRisk("LOW")
                .build();

        ComplexDiagnosisQualityScorer.ModelTraceQualityResult result =
                scorer.modelEducationTrace(fixture, analysis, true, false);

        assertThat(result.evaluated()).isTrue();
        assertThat(result.passed()).isFalse();
        assertThat(result.failedMetrics()).contains(
                "nativeRootCauseDecisionChecklistApplied",
                "nativePrimaryReasoningGrounded",
                "nativeTeachingPriorityClear",
                "nativeSecondarySignalsBalanced",
                "nativeNextActionObservable",
                "nativeSafetyBoundary"
        );
    }

    @Test
    void modelTraceQualityFailsWhenRootCauseChecklistIsNotApplied() throws IOException {
        ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture = firstComplexFixture();
        SubmissionAnalysisResponse analysis = SubmissionAnalysisResponse.builder()
                .modelEducationTrace(SubmissionAnalysisResponse.ModelEducationTrace.builder()
                        .source("diagnosisDecision")
                        .primaryIssueTag(fixture.primaryRootCause().issueTag())
                        .fineGrainedTag(fixture.primaryRootCause().fineGrainedTag())
                        .evidenceRefs(fixture.requiredEvidenceRefs())
                        .primaryReasoning("先抓 " + fixture.mustMention().get(0)
                                + "，因为它是当前主因。")
                        .teachingPriority(fixture.expectedTeachingPriority())
                        .secondaryIssues(List.of())
                        .distractorNotes(List.of())
                        .nextLearningAction("请对比 first failed case 并验证这个判断。")
                        .nextLearningActionEvidenceRefs(fixture.requiredEvidenceRefs())
                        .answerLeakRisk("LOW")
                        .build())
                .answerLeakRisk("LOW")
                .build();

        ComplexDiagnosisQualityScorer.ModelTraceQualityResult result =
                scorer.modelEducationTrace(fixture, analysis, true, false);

        assertThat(result.evaluated()).isTrue();
        assertThat(result.passed()).isFalse();
        assertThat(result.failedMetrics()).contains("nativeRootCauseDecisionChecklistApplied");
        assertThat(result.metrics()).containsEntry("nativePrimaryReasoningGrounded", true);
        assertThat(result.metrics()).containsEntry("nativeTeachingPriorityClear", true);
        assertThat(result.metrics()).containsEntry("nativeNextActionObservable", true);
    }

    @Test
    void educationAgentQualityFailsWhenFieldsExistButTeachingJudgmentIsWeak() throws IOException {
        ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture = firstComplexFixture();
        SubmissionAnalysisResponse analysis = SubmissionAnalysisResponse.builder()
                .issueTags(List.of(fixture.primaryRootCause().issueTag()))
                .fineGrainedTags(List.of(fixture.primaryRootCause().fineGrainedTag()))
                .evidenceRefs(fixture.requiredEvidenceRefs())
                .studentFeedback(SubmissionAnalysisResponse.StudentFeedback.builder()
                        .summary("这里有一些问题。")
                        .blockingIssues(List.of(SubmissionAnalysisResponse.FeedbackIssue.builder()
                                .priority(1)
                                .title("当前问题")
                                .studentMessage("可以看看代码。")
                                .evidence("不够具体")
                                .nextAction("继续检查。")
                                .issueTag(fixture.primaryRootCause().issueTag())
                                .fineGrainedTag(fixture.primaryRootCause().fineGrainedTag())
                                .evidenceRefs(List.of("invented:evidence"))
                                .build()))
                        .secondaryIssues(List.of(SubmissionAnalysisResponse.SecondaryIssue.builder()
                                .title("另一个问题")
                                .studentMessage("必须先改这个次要问题。")
                                .whyNotPrimary("")
                                .issueTag("BOUNDARY_CONDITION")
                                .evidenceRefs(fixture.requiredEvidenceRefs())
                                .build()))
                        .improvementOpportunities(List.of(SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                                .category("TESTING_HABIT")
                                .studentMessage("之后可以自测。")
                                .benefit("有帮助。")
                                .evidenceRefs(fixture.requiredEvidenceRefs())
                                .build()))
                        .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                                .hintLevel("L2")
                                .action("CHECK")
                                .task("继续检查。")
                                .checkQuestion("检查了吗？")
                                .evidenceRefs(List.of())
                                .answerLeakRisk("LOW")
                                .build())
                        .build())
                .answerLeakRisk("LOW")
                .build();

        ComplexDiagnosisQualityScorer.EducationAgentQualityResult result =
                scorer.educationAgentQuality(fixture, analysis, true, false);

        assertThat(result.evaluated()).isTrue();
        assertThat(result.passed()).isFalse();
        assertThat(result.failedMetrics()).contains(
                "primaryReasoningGrounded",
                "secondarySignalsBalanced",
                "nextActionObservable"
        );
        assertThat(result.metrics()).containsEntry("blockingPriorityClear", true);
    }

    @Test
    void reportsFailedMetricsWhenAnalysisOverweightsSecondarySignalsAndLeaksAnswer() throws IOException {
        ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture = firstComplexFixture();
        String secondaryFineTag = "OFF_BY_ONE".equals(fixture.primaryRootCause().fineGrainedTag())
                ? "INPUT_PARSING"
                : "OFF_BY_ONE";
        SubmissionAnalysisResponse analysis = SubmissionAnalysisResponse.builder()
                .issueTags(List.of("BOUNDARY_CONDITION"))
                .fineGrainedTags(List.of(secondaryFineTag, fixture.primaryRootCause().fineGrainedTag()))
                .evidenceRefs(List.of("invented:evidence"))
                .headline("把次要边界问题放在最前面")
                .summary(fixture.distractingSignals().get(0))
                .studentHint("完整代码如下：def solve(): pass")
                .answerLeakRisk("HIGH")
                .build();

        ComplexDiagnosisQualityScorer.Result result = scorer.score(fixture, analysis);

        assertThat(result.passed()).isFalse();
        assertThat(result.failedMetrics()).contains(
                "teachingPriorityCorrect",
                "secondaryIssuesNotOverweighted",
                "distractingSignalsIgnored",
                "evidenceGrounded",
                "noFullSolutionLeak"
        );
        assertThat(result.metrics()).containsEntry("primaryRootCauseHit", true);
        assertThat(result.score()).isBetween(0.0, 1.0);
    }

    private ComplexStudentSubmissionEvalFixtureLoader.Fixture firstComplexFixture() throws IOException {
        return new ComplexStudentSubmissionEvalFixtureLoader(objectMapper).loadDefault().get(0);
    }

    private SubmissionAnalysisResponse alignedAnalysis(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture) {
        return SubmissionAnalysisResponse.builder()
                .issueTags(List.of(fixture.primaryRootCause().issueTag()))
                .fineGrainedTags(List.of(fixture.primaryRootCause().fineGrainedTag()))
                .evidenceRefs(fixture.requiredEvidenceRefs())
                .headline(fixture.primaryRootCause().fineGrainedTag() + " 是首要问题")
                .summary("先围绕 " + fixture.mustMention().get(0) + " 做最小证据核对。")
                .studentHint("先检查 " + fixture.mustMention().get(1) + "，先不要急着改程序。")
                .studentFeedback(SubmissionAnalysisResponse.StudentFeedback.builder()
                        .summary("这次主要问题是 " + fixture.primaryRootCause().fineGrainedTag())
                        .blockingIssues(List.of(SubmissionAnalysisResponse.FeedbackIssue.builder()
                                .priority(1)
                                .title("当前最需要先处理的问题")
                                .studentMessage("先核对 " + fixture.mustMention().get(0) + " 和 " + fixture.mustMention().get(1))
                                .evidence("first failed case")
                                .nextAction("数一数代码实际处理了几组输入。")
                                .issueTag(fixture.primaryRootCause().issueTag())
                                .fineGrainedTag(fixture.primaryRootCause().fineGrainedTag())
                                .evidenceRefs(fixture.requiredEvidenceRefs())
                                .build()))
                        .secondaryIssues(List.of(SubmissionAnalysisResponse.SecondaryIssue.builder()
                                .title("可能的次要问题")
                                .studentMessage("次要信号先放到后面观察。")
                                .whyNotPrimary("它不是 first failed case 的主因。")
                                .issueTag("BOUNDARY_CONDITION")
                                .evidenceRefs(fixture.requiredEvidenceRefs())
                                .build()))
                        .improvementOpportunities(List.of(SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                                .category("TESTING_HABIT")
                                .studentMessage("通过后补一个不同于样例的小自测。")
                                .benefit("能确认修复不只覆盖眼前数据。")
                                .evidenceRefs(fixture.requiredEvidenceRefs())
                                .build()))
                        .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                                .hintLevel("L2")
                                .action("COMPARE_INPUT_SPEC")
                                .task("数一数代码实际处理了几组输入。")
                                .checkQuestion("题面要求几组，代码处理几组？")
                                .evidenceRefs(fixture.requiredEvidenceRefs())
                                .answerLeakRisk("LOW")
                                .build())
                        .build())
                .answerLeakRisk("LOW")
                .build();
    }

    private SubmissionAnalysisResponse analysisWithNativeTrace(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture) {
        return SubmissionAnalysisResponse.builder()
                .issueTags(List.of(fixture.primaryRootCause().issueTag()))
                .fineGrainedTags(List.of(fixture.primaryRootCause().fineGrainedTag()))
                .evidenceRefs(fixture.requiredEvidenceRefs())
                .headline(fixture.primaryRootCause().fineGrainedTag() + " 是首要问题")
                .summary("先围绕 " + fixture.mustMention().get(0) + " 做最小证据核对。")
                .studentHint("先检查 " + fixture.mustMention().get(1) + "，先不要急着改程序。")
                .modelEducationTrace(SubmissionAnalysisResponse.ModelEducationTrace.builder()
                        .source("diagnosisDecision")
                        .primaryIssueTag(fixture.primaryRootCause().issueTag())
                        .fineGrainedTag(fixture.primaryRootCause().fineGrainedTag())
                        .evidenceRefs(fixture.requiredEvidenceRefs())
                        .primaryReasoning("模型原生判断先抓 " + fixture.mustMention().get(0)
                                + "，因为 first failed case 暴露的是 "
                                + fixture.primaryRootCause().fineGrainedTag())
                        .secondaryIssues(List.of(SubmissionAnalysisResponse.ModelEducationIssueNote.builder()
                                .message("debug 输出是次要信号，不是主因。")
                                .issueTag("OUTPUT_FORMAT")
                                .evidenceRefs(fixture.requiredEvidenceRefs())
                                .build()))
                        .distractorNotes(List.of(SubmissionAnalysisResponse.ModelEducationIssueNote.builder()
                                .message("变量命名是干扰信号，不应优先。")
                                .evidenceRefs(fixture.requiredEvidenceRefs())
                                .build()))
                        .teachingPriority(fixture.expectedTeachingPriority())
                        .improvementCategories(List.of("TESTING_HABIT"))
                        .nextLearningAction("数一数题面要求和代码实际处理的数量，并用 first failed case 验证。")
                        .nextLearningActionEvidenceRefs(fixture.requiredEvidenceRefs())
                        .answerLeakRisk("LOW")
                        .build())
                .answerLeakRisk("LOW")
                .build();
    }
}
