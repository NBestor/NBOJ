package com.onlinejudge.submission.application;

import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelOutputValidatorTest {

    private final ModelOutputValidator validator = new ModelOutputValidator();

    @Test
    void acceptsValidStudentFeedback() {
        Fixture fixture = fixture();

        ExternalModelStagePayloads.StageValidationResult result = validator.validateStudentFeedback(
                validStudentFeedback("TESTING_HABIT", "列出 range 产生的 i。"),
                fixture.brief(),
                fixture.pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.NONE);
    }

    @Test
    void acceptsCodeRangeEvidenceInStudentFeedback() {
        Fixture fixture = largeFixture();
        SubmissionAnalysisResponse.StudentFeedback feedback = validStudentFeedback(
                "TESTING_HABIT",
                "列出 range 产生的 i。"
        );
        feedback.getBlockingIssues().get(0).setEvidenceRefs(List.of("code:range:2-4"));
        feedback.getNextLearningAction().setEvidenceRefs(List.of("code:line:3-5"));

        ExternalModelStagePayloads.StageValidationResult result = validator.validateStudentFeedback(
                feedback,
                fixture.brief(),
                fixture.pack()
        );

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsInvalidStudentFeedbackImprovementCategory() {
        Fixture fixture = fixture();

        ExternalModelStagePayloads.StageValidationResult result = validator.validateStudentFeedback(
                validStudentFeedback("MADE_UP_IMPROVEMENT", "列出 range 产生的 i。"),
                fixture.brief(),
                fixture.pack()
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.INVALID_TAG);
    }

    @Test
    void allowsUnsafeStudentFeedbackWhenRiskIsAnswerLeakOnly() {
        Fixture fixture = fixture();

        ExternalModelStagePayloads.StageValidationResult result = validator.validateStudentFeedback(
                validStudentFeedback("TESTING_HABIT", "直接改成 range(1, n + 1)。"),
                fixture.brief(),
                fixture.pack()
        );

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void runtimePlanCarriesBriefAndAiNavigationPromptVersions() {
        Fixture fixture = fixture();
        ExternalModelAgentRuntime runtime = new ExternalModelAgentRuntime(
                new ModelDiagnosisBriefBuilder(),
                new StandardLibraryPackBuilder(new DiagnosisTaxonomy()),
                new PromptTemplateRegistry(),
                validator
        );

        ExternalModelAgentRuntime.RuntimePlan plan = runtime.prepare(
                fixture.evidencePackage(),
                null
        );

        assertThat(plan.getBrief().getSchemaVersion()).isEqualTo(ModelDiagnosisBrief.SCHEMA_VERSION);
        assertThat(plan.getStandardLibraryPack()).isNull();
        assertThat(plan.getFreeDiagnosisPrompt().getVersion()).isEqualTo(PromptTemplateRegistry.FREE_DIAGNOSIS_V2);
        assertThat(plan.getStandardLibraryNavigationPrompt().getVersion())
                .isEqualTo(PromptTemplateRegistry.STANDARD_LIBRARY_NAVIGATION_V1);
        assertThat(plan.getAdvicePrompt().getVersion()).isEqualTo(PromptTemplateRegistry.DIAGNOSIS_REPORT_V4);
        assertThat(plan.getRuntimeProfile()).isEqualTo("standard");
        assertThat(plan.isRequestCompact()).isFalse();
    }

    @Test
    void ignoredRuntimeProfileStillKeepsFullLargeSubmissionContext() {
        ExternalModelAgentRuntime runtime = new ExternalModelAgentRuntime(
                new ModelDiagnosisBriefBuilder(),
                new StandardLibraryPackBuilder(new DiagnosisTaxonomy()),
                new PromptTemplateRegistry(),
                validator
        );

        ExternalModelAgentRuntime.RuntimePlan smallPlan = runtime.prepare(
                fixture().evidencePackage(),
                null,
                "auto"
        );
        ExternalModelAgentRuntime.RuntimePlan largePlan = runtime.prepare(
                largeFixture().evidencePackage(),
                null,
                "auto"
        );

        assertThat(smallPlan.getRuntimeProfile()).isEqualTo("standard");
        assertThat(smallPlan.isRequestCompact()).isFalse();
        assertThat(largePlan.getRuntimeProfile()).isEqualTo("standard");
        assertThat(largePlan.isRequestCompact()).isFalse();
        assertThat(largePlan.getBrief().getKeyCodeExcerpt()).doesNotContain("truncated for model");
    }

    private SubmissionAnalysisResponse.StudentFeedback validStudentFeedback(String improvementCategory,
                                                                            String nextAction) {
        return SubmissionAnalysisResponse.StudentFeedback.builder()
                .summary("这次主要问题是循环边界没有覆盖题目要求。")
                .blockingIssues(List.of(SubmissionAnalysisResponse.FeedbackIssue.builder()
                        .priority(1)
                        .title("当前最需要先处理的问题")
                        .studentMessage("循环边界和题目要求不一致，先手推最小样例。")
                        .evidence("judge:first_failed_case")
                        .nextAction(nextAction)
                        .issueTag("SK_LOOP_BOUNDARY")
                        .fineGrainedTag("MP_RANGE_RIGHT_ENDPOINT")
                        .evidenceRefs(List.of("judge:first_failed_case"))
                        .build()))
                .secondaryIssues(List.of())
                .improvementOpportunities(List.of(SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                        .category(improvementCategory)
                        .studentMessage("通过后补一个最小边界自测。")
                        .benefit("能提前发现边界遗漏。")
                        .evidenceRefs(List.of("judge:first_failed_case"))
                        .build()))
                .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                        .hintLevel("L2")
                        .action("TRACE_VARIABLES")
                        .task(nextAction)
                        .checkQuestion("当 n=1 时循环执行几次？")
                        .evidenceRefs(List.of("judge:first_failed_case"))
                        .answerLeakRisk("LOW")
                        .build())
                .build();
    }

    private Fixture fixture() {
        DiagnosisEvidencePackage evidencePackage = DiagnosisEvidencePackage.builder()
                .problem(DiagnosisEvidencePackage.ProblemEvidence.builder()
                        .title("Sum 1 to n")
                        .description("Input n and output the sum from 1 to n.")
                        .build())
                .submission(DiagnosisEvidencePackage.SubmissionEvidence.builder()
                        .language("Python 3")
                        .verdict("WRONG_ANSWER")
                        .sourceCodeWithLineNumbers("1: for i in range(1, n):")
                        .sourceCodeLineCount(1)
                        .build())
                .judgeFacts(DiagnosisEvidencePackage.JudgeFacts.builder()
                        .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                                .testCaseNumber(1)
                                .hidden(false)
                                .input("3")
                                .expectedOutput("6")
                                .actualOutput("3")
                                .build())
                        .build())
                .build();
        ModelDiagnosisBrief brief = new ModelDiagnosisBriefBuilder().build(evidencePackage, null);
        StandardLibraryPack pack = new StandardLibraryPackBuilder(new DiagnosisTaxonomy()).build(brief);
        return new Fixture(evidencePackage, brief, pack);
    }

    private Fixture largeFixture() {
        String source = """
                n = int(input())
                total = 0
                for i in range(1, n):
                    total += i
                print(total)
                """.repeat(80);
        DiagnosisEvidencePackage evidencePackage = DiagnosisEvidencePackage.builder()
                .problem(DiagnosisEvidencePackage.ProblemEvidence.builder()
                        .title("Sum 1 to n")
                        .description("Input n and output the sum from 1 to n.".repeat(50))
                        .build())
                .submission(DiagnosisEvidencePackage.SubmissionEvidence.builder()
                        .language("Python 3")
                        .verdict("WRONG_ANSWER")
                        .sourceCode(source)
                        .sourceCodeWithLineNumbers(source)
                        .sourceCodeLineCount(80 * 5)
                        .build())
                .judgeFacts(DiagnosisEvidencePackage.JudgeFacts.builder()
                        .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                                .testCaseNumber(1)
                                .hidden(false)
                                .input("3")
                                .expectedOutput("6")
                                .actualOutput("3")
                                .build())
                        .build())
                .build();
        ModelDiagnosisBrief brief = new ModelDiagnosisBriefBuilder().build(evidencePackage, null);
        StandardLibraryPack pack = new StandardLibraryPackBuilder(new DiagnosisTaxonomy()).build(brief);
        return new Fixture(evidencePackage, brief, pack);
    }

    private record Fixture(DiagnosisEvidencePackage evidencePackage,
                           ModelDiagnosisBrief brief,
                           StandardLibraryPack pack) {
    }
}
