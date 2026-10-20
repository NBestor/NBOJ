package com.onlinejudge.submission.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisEvidencePackageBuilderTest {

    private final DiagnosisEvidencePackageBuilder builder = new DiagnosisEvidencePackageBuilder();

    @Test
    void buildsStableEvidencePackageWithJudgeFactsAndPolicy() {
        Problem problem = Problem.builder()
                .id(7L)
                .title("两数和")
                .description("输入两个整数，输出和")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .aiPromptDirection("关注输入输出格式")
                .knowledgePoints(List.of("前缀和", "输入读取"))
                .algorithmStrategies(List.of("预处理"))
                .commonMistakes(List.of("把 n q 读成一个 n"))
                .boundaryTypes(List.of("多组查询", "最小 n"))
                .build();
        Submission submission = Submission.builder()
                .id(21L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode("""
                        a, b = input().split()
                        print(a + b)
                        """)
                .compileOutput(null)
                .errorMessage(null)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                        .testCaseNumber(2)
                        .hidden(true)
                        .build())
                .build();
        List<SubmissionCaseResult> cases = List.of(
                SubmissionCaseResult.builder()
                        .testCaseNumber(1)
                        .passed(true)
                        .hidden(false)
                        .actualOutput("3")
                        .expectedOutput("3")
                        .executionTime(0.01)
                        .memoryUsed(1024)
                        .build(),
                SubmissionCaseResult.builder()
                        .testCaseNumber(2)
                        .testCaseId(202L)
                        .passed(false)
                        .hidden(true)
                        .actualOutput("wrong hidden output")
                        .expectedOutput("hidden expected")
                        .executionTime(0.02)
                        .memoryUsed(1024)
                        .testSemanticCode("TCI_HIDDEN_BOUNDARY")
                        .testIntentType("BOUNDARY")
                        .testIntentTitle("最小规模边界")
                        .testIntentSummary("覆盖输入规模处在最小合法边界时的程序行为。")
                        .testLearningObjective("能检查初始化、空循环和边界返回是否保持定义一致。")
                        .testContestRole("CORRECTNESS_GUARD")
                        .testRevealPolicy("AI_GENERALIZED")
                        .build()
        );

        DiagnosisEvidencePackage evidence = builder.build(
                problem,
                submission,
                cases,
                baseline,
                Assignment.HintPolicy.L3
        );

        assertThat(evidence.getSchemaVersion()).isEqualTo(DiagnosisEvidencePackage.SCHEMA_VERSION);
        assertThat(evidence.getSubmission().getId()).isEqualTo(21L);
        assertThat(evidence.getSubmission().getVerdict()).isEqualTo("WRONG_ANSWER");
        assertThat(evidence.getSubmission().getSourceCodeWithLineNumbers()).contains("1: a, b = input().split()");
        assertThat(evidence.getSubmission().getSourceCodeLineCount()).isEqualTo(3);
        assertThat(evidence.getProblem().getTitle()).isEqualTo("两数和");
        assertThat(evidence.getProblem().getKnowledgePoints()).contains("前缀和", "输入读取");
        assertThat(evidence.getProblem().getCommonMistakes()).contains("把 n q 读成一个 n");
        assertThat(evidence.getJudgeFacts().getPassedCount()).isEqualTo(1);
        assertThat(evidence.getJudgeFacts().getTotalCount()).isEqualTo(2);
        assertThat(evidence.getJudgeFacts().getHiddenFailureObserved()).isTrue();
        assertThat(evidence.getJudgeFacts().getFirstFailedCase().getTestCaseNumber()).isEqualTo(2);
        assertThat(evidence.getJudgeFacts().getCaseResultsSummary().get(1).getActualOutputPreview()).isEqualTo("[隐藏测试点]");
        assertThat(evidence.getJudgeFacts().getCaseResultsSummary().get(1))
                .satisfies(summary -> {
                    assertThat(summary.getTestCaseId()).isEqualTo(202L);
                    assertThat(summary.getSemanticCode()).isEqualTo("TCI_HIDDEN_BOUNDARY");
                    assertThat(summary.getIntentType()).isEqualTo("BOUNDARY");
                    assertThat(summary.getIntentSummary()).contains("最小合法边界");
                    assertThat(summary.getInputPreview()).isEqualTo("[隐藏测试点]");
                    assertThat(summary.getActualOutputPreview()).isEqualTo("[隐藏测试点]");
                    assertThat(summary.getExpectedOutputPreview()).isEqualTo("[隐藏测试点]");
                });
        assertThat(evidence.getPolicy().getHintPolicy()).isEqualTo("L3");
        assertThat(evidence.getPolicy().getAllowedHintLevels()).containsExactly("L1", "L2", "L3");
        assertThat(evidence.getHistory().getRecentIssueTags()).isEmpty();
    }

    @Test
    void withholdsHiddenSemanticTextWhenRevealPolicyIsNotGeneralized() {
        SubmissionCaseResult hidden = SubmissionCaseResult.builder()
                .testCaseNumber(1)
                .testCaseId(99L)
                .passed(false)
                .hidden(true)
                .inputSnapshot("secret input")
                .expectedOutput("secret expected")
                .actualOutput("secret actual")
                .testSemanticCode("TCI_UNSAFE")
                .testIntentSummary("unsafe semantic text")
                .testRevealPolicy("PUBLIC_EXAMPLE")
                .build();

        DiagnosisEvidencePackage evidence = builder.build(
                Problem.builder().id(1L).title("题目").description("描述").build(),
                Submission.builder().id(1L).languageName("Python 3").sourceCode("print(1)").build(),
                List.of(hidden),
                SubmissionAnalysisResponse.builder().build(),
                null
        );

        assertThat(evidence.getJudgeFacts().getCaseResultsSummary()).singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getSemanticCode()).isNull();
                    assertThat(summary.getIntentSummary()).isNull();
                    assertThat(summary.getInputPreview()).isEqualTo("[隐藏测试点]");
                });
    }

    @Test
    void keepsHistoryEvidenceWhenProvided() {
        DiagnosisEvidencePackage.HistoryEvidence history = DiagnosisEvidencePackage.HistoryEvidence.builder()
                .previousVerdict("WRONG_ANSWER")
                .previousIssueTags(List.of("BOUNDARY_CONDITION"))
                .previousFineGrainedTags(List.of("OFF_BY_ONE"))
                .recentIssueTags(List.of("BOUNDARY_CONDITION", "BOUNDARY_CONDITION"))
                .recentFineGrainedTags(List.of("OFF_BY_ONE"))
                .repeatedIssueTag("BOUNDARY_CONDITION")
                .repeatedIssueCount(2L)
                .transitionSignal("最近仍反复出现同类问题：边界条件")
                .previousInterventionType("MIN_CASE_TRACE")
                .previousInterventionTask("Trace one minimal failing input.")
                .previousInterventionCompletionSignal("Student identifies the first divergent variable.")
                .previousLearningActionStatus("CONTRADICTED")
                .previousLearningActionConfidence(0.74)
                .previousLearningActionEvidenceRefs(List.of("eval:intervention", "followup:submission:2"))
                .previousLearningActionSummary("The same issue remained after the previous action.")
                .previousLearningActionNextAdjustment("Shrink the next task.")
                .build();
        DiagnosisEvidencePackage evidence = builder.build(
                Problem.builder().id(1L).title("题目").description("描述").build(),
                Submission.builder().id(1L).languageName("Python 3").sourceCode("print(1)").build(),
                List.of(),
                SubmissionAnalysisResponse.builder().build(),
                null,
                history
        );

        assertThat(evidence.getPolicy().getHintPolicy()).isEqualTo("L2");
        assertThat(evidence.getHistory().getPreviousVerdict()).isEqualTo("WRONG_ANSWER");
        assertThat(evidence.getHistory().getRepeatedIssueTag()).isEqualTo("BOUNDARY_CONDITION");
        assertThat(evidence.getHistory().getTransitionSignal()).contains("边界条件");
        assertThat(evidence.getHistory().getPreviousInterventionType()).isEqualTo("MIN_CASE_TRACE");
        assertThat(evidence.getHistory().getPreviousLearningActionStatus()).isEqualTo("CONTRADICTED");
        assertThat(evidence.getHistory().getPreviousLearningActionEvidenceRefs())
                .contains("eval:intervention", "followup:submission:2");
    }

    @Test
    void keepsLearningMemorySnapshotWhenProvided() {
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory =
                DiagnosisEvidencePackage.StudentLearningMemorySnapshot.builder()
                        .studentProfileId(9L)
                        .observedSubmissionCount(6)
                        .recurringIssueTags(List.of(DiagnosisEvidencePackage.MemoryTagStat.builder()
                                .tag("IO_FORMAT")
                                .count(3L)
                                .evidenceSubmissionIds(List.of(1L, 2L, 3L))
                                .build()))
                        .abilityFocus(List.of(DiagnosisEvidencePackage.AbilityFocus.builder()
                                .abilityPoint("题意读取")
                                .submissionCount(3L)
                                .problemCount(2L)
                                .evidenceTags(List.of("IO_FORMAT"))
                                .build()))
                        .recentTrend("最近 3 次历史提交仍未通过")
                        .evidenceRefs(List.of("memory:student:9", "memory:recurring_issue:IO_FORMAT"))
                        .build();

        DiagnosisEvidencePackage evidence = builder.build(
                Problem.builder().id(1L).title("题目").description("描述").build(),
                Submission.builder().id(1L).languageName("Python 3").sourceCode("print(1)").build(),
                List.of(),
                SubmissionAnalysisResponse.builder().build(),
                null,
                null,
                memory
        );

        assertThat(evidence.getLearningMemory()).isNotNull();
        assertThat(evidence.getLearningMemory().getStudentProfileId()).isEqualTo(9L);
        assertThat(evidence.getLearningMemory().getRecurringIssueTags()).singleElement()
                .satisfies(stat -> {
                    assertThat(stat.getTag()).isEqualTo("IO_FORMAT");
                    assertThat(stat.getCount()).isEqualTo(3L);
                });
        assertThat(evidence.getLearningMemory().getEvidenceRefs()).contains("memory:student:9");
    }
}
