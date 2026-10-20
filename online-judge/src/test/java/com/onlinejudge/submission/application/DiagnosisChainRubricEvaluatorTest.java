package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisChainRubricEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DiagnosisChainRubricEvaluator evaluator = new DiagnosisChainRubricEvaluator();

    @Test
    void generatedComplexFixturesProduceCompleteRubrics() throws IOException {
        List<ComplexStudentSubmissionEvalFixtureLoader.Fixture> fixtures =
                new ComplexStudentSubmissionEvalFixtureLoader(objectMapper).loadDefault();

        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(210);
        assertThat(fixtures.stream().map(DiagnosisQualityRubric::from))
                .allSatisfy(rubric -> {
                    assertThat(rubric).isNotNull();
                    assertThat(rubric.complete()).as(rubric.caseId()).isTrue();
                });
    }

    @Test
    void completeDiagnosisChainPasses() {
        DiagnosisQualityRubric rubric = rubric();

        DiagnosisChainRubricEvaluator.Result result = evaluator.evaluate(rubric, correctAnalysis(), true, false);

        assertThat(result.evaluated()).isTrue();
        assertThat(result.overallVerdict()).isTrue();
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.failedReasons()).isEmpty();
    }

    @Test
    void evidenceMissFailsEvenWhenRootCauseTagIsCorrect() {
        SubmissionAnalysisResponse analysis = correctAnalysis();
        analysis.setEvidenceRefs(List.of("wrong:evidence"));
        analysis.getModelEducationTrace().setEvidenceRefs(List.of("wrong:evidence"));
        analysis.getModelEducationTrace().setNextLearningActionEvidenceRefs(List.of("wrong:evidence"));
        analysis.getModelEducationTrace().getSecondaryIssues().get(0).setEvidenceRefs(List.of("wrong:evidence"));
        analysis.getStudentFeedback().getBlockingIssues().get(0).setEvidenceRefs(List.of("wrong:evidence"));
        analysis.getStudentFeedback().getSecondaryIssues().get(0).setEvidenceRefs(List.of("wrong:evidence"));
        analysis.getStudentFeedback().getNextLearningAction().setEvidenceRefs(List.of("wrong:evidence"));

        DiagnosisChainRubricEvaluator.Result result = evaluator.evaluate(rubric(), analysis, true, false);

        assertThat(result.overallVerdict()).isFalse();
        assertThat(result.evidenceVerdict().passed()).isFalse();
        assertThat(result.rootCauseVerdict().passed()).isTrue();
        assertThat(result.failedStages()).contains("evidence");
    }

    @Test
    void rootCauseMissFailsEvenWithGoodTeachingAction() {
        SubmissionAnalysisResponse analysis = correctAnalysis();
        analysis.setIssueTags(List.of("OUTPUT_FORMAT"));
        analysis.setFineGrainedTags(List.of("OUTPUT_FORMAT_DETAIL"));
        analysis.getModelEducationTrace().setPrimaryIssueTag("OUTPUT_FORMAT");
        analysis.getModelEducationTrace().setFineGrainedTag("OUTPUT_FORMAT_DETAIL");

        DiagnosisChainRubricEvaluator.Result result = evaluator.evaluate(rubric(), analysis, true, false);

        assertThat(result.overallVerdict()).isFalse();
        assertThat(result.rootCauseVerdict().passed()).isFalse();
        assertThat(result.teachingVerdict().passed()).isTrue();
        assertThat(result.failedStages()).contains("rootCause");
    }

    @Test
    void overweightedSecondaryIssueFailsDistractorStage() {
        SubmissionAnalysisResponse analysis = correctAnalysis();
        analysis.setFineGrainedTags(List.of("OUTPUT_FORMAT_DETAIL", "INPUT_PARSING"));
        analysis.setSummary("debug 输出是首要问题，必须先处理，输入读取之后再说。");

        DiagnosisChainRubricEvaluator.Result result = evaluator.evaluate(rubric(), analysis, true, false);

        assertThat(result.overallVerdict()).isFalse();
        assertThat(result.distractorVerdict().passed()).isFalse();
        assertThat(result.failedReasons()).anyMatch(reason -> reason.startsWith("distractor:"));
    }

    @Test
    void answerLeakFailsSafetyStage() {
        SubmissionAnalysisResponse analysis = correctAnalysis();
        analysis.setSummary("完整代码如下：def solve(): pass。");
        analysis.setAnswerLeakRisk("HIGH");

        DiagnosisChainRubricEvaluator.Result result = evaluator.evaluate(rubric(), analysis, true, false);

        assertThat(result.overallVerdict()).isFalse();
        assertThat(result.safetyVerdict().passed()).isFalse();
        assertThat(result.failedStages()).contains("safety");
    }

    @Test
    void fallbackIsNotEvaluated() {
        DiagnosisChainRubricEvaluator.Result result = evaluator.evaluate(rubric(), correctAnalysis(), false, true);

        assertThat(result.complexCase()).isTrue();
        assertThat(result.evaluated()).isFalse();
        assertThat(result.score()).isZero();
    }

    private DiagnosisQualityRubric rubric() {
        return new DiagnosisQualityRubric(
                "complex-live-test",
                "IO_FORMAT",
                "INPUT_PARSING",
                "first failed case 少读了一组查询。",
                "generator:multi-query-prefix:input_parsing",
                List.of("generator:multi-query-prefix:input_parsing", "judge:first_failed_case:1"),
                List.of("q 行查询", "少处理一组"),
                List.of("完整代码", "参考答案", "隐藏测试点"),
                List.of(new DiagnosisQualityRubric.SecondarySignal(
                        "OUTPUT_FORMAT",
                        "secondary",
                        "debug 输出不是 first failed case 的主因")),
                List.of("debug 输出"),
                "先核对输入读取次数。"
        );
    }

    private SubmissionAnalysisResponse correctAnalysis() {
        return SubmissionAnalysisResponse.builder()
                .summary("先看 first failed case：q 行查询少处理一组，debug 输出只是次要信号，不是主因。")
                .issueTags(List.of("IO_FORMAT"))
                .fineGrainedTags(List.of("INPUT_PARSING"))
                .evidenceRefs(List.of("generator:multi-query-prefix:input_parsing", "judge:first_failed_case:1"))
                .answerLeakRisk("LOW")
                .modelEducationTrace(SubmissionAnalysisResponse.ModelEducationTrace.builder()
                        .primaryIssueTag("IO_FORMAT")
                        .fineGrainedTag("INPUT_PARSING")
                        .evidenceRefs(List.of("generator:multi-query-prefix:input_parsing", "judge:first_failed_case:1"))
                        .primaryReasoning("first failed case 显示 q 行查询少处理一组。")
                        .secondaryIssues(List.of(SubmissionAnalysisResponse.ModelEducationIssueNote.builder()
                                .message("debug 输出是次要信号，不应优先。")
                                .issueTag("OUTPUT_FORMAT")
                                .evidenceRefs(List.of("judge:first_failed_case:1"))
                                .build()))
                        .teachingPriority("先核对输入读取次数。")
                        .nextLearningAction("数一数题目要求几组查询，再对比代码实际处理了几组。")
                        .nextLearningActionEvidenceRefs(List.of("generator:multi-query-prefix:input_parsing"))
                        .answerLeakRisk("LOW")
                        .build())
                .studentFeedback(SubmissionAnalysisResponse.StudentFeedback.builder()
                        .summary("当前优先处理输入读取。")
                        .blockingIssues(List.of(SubmissionAnalysisResponse.FeedbackIssue.builder()
                                .priority(1)
                                .title("当前最需要先处理的问题")
                                .studentMessage("q 行查询少处理一组。")
                                .evidence("first failed case")
                                .nextAction("先数一数代码实际读了几组查询。")
                                .issueTag("IO_FORMAT")
                                .fineGrainedTag("INPUT_PARSING")
                                .evidenceRefs(List.of("generator:multi-query-prefix:input_parsing"))
                                .build()))
                        .secondaryIssues(List.of(SubmissionAnalysisResponse.SecondaryIssue.builder()
                                .title("次要信号")
                                .studentMessage("debug 输出也要清理。")
                                .whyNotPrimary("它不是主因，先放到后面。")
                                .issueTag("OUTPUT_FORMAT")
                                .evidenceRefs(List.of("judge:first_failed_case:1"))
                                .build()))
                        .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                                .action("COMPARE_INPUT_SPEC")
                                .task("数一数题目要求几组查询，并核对代码实际处理了几组。")
                                .checkQuestion("题目要求几组，代码处理几组？")
                                .evidenceRefs(List.of("generator:multi-query-prefix:input_parsing"))
                                .answerLeakRisk("LOW")
                                .build())
                        .build())
                .build();
    }
}
