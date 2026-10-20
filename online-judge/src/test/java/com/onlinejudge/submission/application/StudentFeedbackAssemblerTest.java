package com.onlinejudge.submission.application;

import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentFeedbackAssemblerTest {

    private final StudentFeedbackAssembler assembler =
            new StudentFeedbackAssembler(new DiagnosisTaxonomy());

    @Test
    void assemblesActionableFeedbackForPublicWrongAnswer() {
        SubmissionAnalysisResponse analysis = baseAnalysis("WA")
                .issueTags(List.of("IO_FORMAT"))
                .fineGrainedTags(List.of("INPUT_PARSING"))
                .summary("程序在公开测试点输出行数不足。")
                .evidenceRefs(List.of("case:first_failed", "verdict:wrong_answer"))
                .studentHintPlan(null)
                .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                        .testCaseNumber(1)
                        .hidden(false)
                        .input("2\n1\n2\n")
                        .expectedOutput("1\n2\n")
                        .actualOutput("1\n")
                        .build())
                .build();

        SubmissionAnalysisResponse.StudentFeedback feedback =
                assembler.assemble(analysis, null, true);

        assertBasicContract(feedback);
        assertThat(feedback.getSummary()).contains("本地可验证反馈");
        assertThat(feedback.getBlockingIssues().get(0).getStudentMessage()).contains("读取次数", "输出行数");
        assertThat(feedback.getBlockingIssues().get(0).getEvidence()).contains("期望输出", "实际只输出");
        assertThat(feedback.getImprovementOpportunities())
                .allSatisfy(item -> assertThat(item.getEvidenceRefs()).isEmpty());
        assertThat(feedback.getNextLearningAction().getTask()).contains("读取操作");
        assertStudentSafe(feedback);
    }

    @Test
    void avoidsHiddenTestSpeculationForHiddenWrongAnswer() {
        SubmissionAnalysisResponse analysis = baseAnalysis("WA")
                .issueTags(List.of("BOUNDARY_CONDITION"))
                .fineGrainedTags(List.of("EDGE_CASE_MISSING"))
                .summary("公开样例通过，但程序首次失败出现在隐藏测试点。")
                .evidenceRefs(List.of("verdict:wrong_answer"))
                .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                        .testCaseNumber(8)
                        .hidden(true)
                        .build())
                .build();

        SubmissionAnalysisResponse.StudentFeedback feedback =
                assembler.assemble(analysis, null, true);

        assertBasicContract(feedback);
        String text = feedbackText(feedback);
        assertThat(text).contains("边界");
        assertThat(feedback.getBlockingIssues().get(0).getStudentMessage()).doesNotContain("这次已经通过", "复盘");
        assertThat(text).doesNotContain("隐藏测试是", "隐藏数据", "直接改成", "完整代码");
        assertStudentSafe(feedback);
    }

    @Test
    void givesNaturalRuleFallbackForDpStateIssue() {
        SubmissionAnalysisResponse analysis = baseAnalysis("WA")
                .issueTags(List.of("ALGORITHM_STRATEGY"))
                .fineGrainedTags(List.of("DP_STATE_DESIGN"))
                .summary("当前状态没有覆盖题目判断所需的信息。")
                .studentHintPlan(null)
                .build();

        SubmissionAnalysisResponse.StudentFeedback feedback =
                assembler.assemble(analysis, null, true);

        assertBasicContract(feedback);
        String text = feedbackText(feedback);
        assertThat(feedback.getBlockingIssues().get(0).getStudentMessage())
                .contains("状态含义", "依赖的信息")
                .doesNotContain("当前最需要先处理的是", "转移来源");
        assertThat(text).contains("DP_MODELING", "初始状态", "最小样例");
        assertThat(text).doesNotContain("最大输入规模", "样例结构不同", "代码整理成更清楚");
        assertStudentSafe(feedback);
    }

    @Test
    void turnsAcceptedSubmissionIntoReviewAndImprovementFeedback() {
        SubmissionAnalysisResponse analysis = baseAnalysis("AC")
                .issueTags(List.of("ACCEPTED"))
                .summary("当前提交已经通过。")
                .evidenceRefs(List.of("verdict:accepted"))
                .build();

        SubmissionAnalysisResponse.StudentFeedback feedback =
                assembler.assemble(analysis, null, true);

        assertBasicContract(feedback);
        assertThat(feedback.getBlockingIssues().get(0).getStudentMessage()).contains("复盘");
        assertThat(feedback.getImprovementOpportunities()).isNotEmpty();
        assertThat(feedback.getNextLearningAction().getTask()).isNotBlank();
        assertStudentSafe(feedback);
    }

    @Test
    void keepsContractEvenWhenEvidenceIsSparse() {
        SubmissionAnalysisResponse analysis = baseAnalysis("UNKNOWN")
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .summary("")
                .evidenceRefs(List.of())
                .studentHintPlan(null)
                .build();

        SubmissionAnalysisResponse.StudentFeedback feedback =
                assembler.assemble(analysis, null, true);

        assertBasicContract(feedback);
        assertThat(feedback.getBlockingIssues().get(0).getIssueTag()).isEqualTo("NEEDS_MORE_EVIDENCE");
        assertThat(feedback.getNextLearningAction().getTask()).contains("最小证据");
        assertStudentSafe(feedback);
    }

    private SubmissionAnalysisResponse.SubmissionAnalysisResponseBuilder baseAnalysis(String scenario) {
        return SubmissionAnalysisResponse.builder()
                .submissionId(1L)
                .scenario(scenario)
                .headline("系统反馈")
                .summary("先根据当前结果定位问题。")
                .issueTags(List.of("LOOP_BOUNDARY"))
                .fineGrainedTags(List.of("OFF_BY_ONE"))
                .studentHintPlan(SubmissionAnalysisResponse.StudentHintPlan.builder()
                        .hintLevel("L2")
                        .teachingAction("TRACE_VARIABLES")
                        .nextAction("选第一个失败样例，追踪关键变量。")
                        .coachQuestion("关键变量从哪里开始偏离？")
                        .evidenceRefs(List.of("case:first_failed"))
                        .answerLeakRisk("LOW")
                        .build())
                .answerLeakRisk("LOW");
    }

    private void assertBasicContract(SubmissionAnalysisResponse.StudentFeedback feedback) {
        assertThat(feedback).isNotNull();
        assertThat(feedback.getSummary()).isNotBlank();
        assertThat(feedback.getBlockingIssues()).isNotEmpty();
        assertThat(feedback.getBlockingIssues().get(0).getStudentMessage()).isNotBlank();
        assertThat(feedback.getBlockingIssues().get(0).getNextAction()).isNotBlank();
        assertThat(feedback.getNextLearningAction()).isNotNull();
        assertThat(feedback.getNextLearningAction().getTask()).isNotBlank();
        assertThat(feedback.getNextLearningAction().getAnswerLeakRisk()).isEqualTo("LOW");
    }

    private void assertStudentSafe(SubmissionAnalysisResponse.StudentFeedback feedback) {
        assertThat(feedbackText(feedback)).doesNotContain(
                "完整代码",
                "参考代码",
                "答案如下",
                "直接改成",
                "替换为",
                "hidden test"
        );
    }

    private String feedbackText(SubmissionAnalysisResponse.StudentFeedback feedback) {
        StringBuilder builder = new StringBuilder();
        builder.append(feedback.getSummary()).append('\n');
        feedback.getBlockingIssues().forEach(issue -> builder
                .append(issue.getTitle()).append('\n')
                .append(issue.getStudentMessage()).append('\n')
                .append(issue.getEvidence()).append('\n')
                .append(issue.getNextAction()).append('\n'));
        if (feedback.getSecondaryIssues() != null) {
            feedback.getSecondaryIssues().forEach(issue -> builder
                    .append(issue.getTitle()).append('\n')
                    .append(issue.getStudentMessage()).append('\n')
                    .append(issue.getWhyNotPrimary()).append('\n'));
        }
        if (feedback.getImprovementOpportunities() != null) {
            feedback.getImprovementOpportunities().forEach(item -> builder
                    .append(item.getCategory()).append('\n')
                    .append(item.getStudentMessage()).append('\n')
                    .append(item.getBenefit()).append('\n'));
        }
        builder.append(feedback.getNextLearningAction().getTask()).append('\n')
                .append(feedback.getNextLearningAction().getCheckQuestion());
        return builder.toString();
    }
}
