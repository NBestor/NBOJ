package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StudentFeedbackViewAssemblerTest {

    private final StudentFeedbackViewAssembler assembler = new StudentFeedbackViewAssembler();

    @Test
    void buildsStudentFacingViewFromStructuredFeedback() {
        SubmissionAnalysisResponse.StudentFeedbackView view = assembler.assemble(analysis(false));

        assertThat(view).isNotNull();
        assertThat(view.getStatus()).isEqualTo("READY");
        assertThat(view.getRepairItems()).singleElement().satisfies(item -> {
            assertThat(item.getTitle()).isEqualTo("输入没有完整读取");
            assertThat(item.getBody()).contains("公开样例");
            assertThat(item.getBody()).doesNotContain("当前最需要", "本地可验证反馈");
            assertThat(item.getEvidenceRefs()).containsExactly("rule:input");
        });
        assertThat(view.getImprovementItems()).singleElement().satisfies(item -> {
            assertThat(item.getTitle()).isEqualTo("测试习惯");
            assertThat(item.getBody()).contains("自测");
        });
        assertThat(view.getNextQuestion()).contains("第二次读取");
        assertThat(view.getEvidenceRefs()).contains("case:first_failed", "rule:input");
    }

    @Test
    void showsUnavailableViewWhenModelFailed() {
        SubmissionAnalysisResponse.StudentFeedbackView view = assembler.assemble(analysis(true));

        assertThat(view).isNotNull();
        assertThat(view.getStatus()).isEqualTo("AI_UNAVAILABLE");
        assertThat(view.getRepairItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getTitle()).isEqualTo("AI 暂不可用");
                    assertThat(item.getBody())
                            .contains("AI 暂不可用", "稍后重试")
                            .doesNotContain("深度诊断", "伪装")
                            .doesNotContain("公开样例");
                });
        assertThat(view.getImprovementItems()).isEmpty();
        assertThat(view.getNextQuestion()).isNull();
    }

    @Test
    void showsNaturalDpFallbackToStudent() {
        SubmissionAnalysisResponse analysis = analysis(true);
        analysis.getStudentFeedback().getBlockingIssues().get(0)
                .setTitle("当前最需要先处理的问题");
        analysis.getStudentFeedback().getBlockingIssues().get(0)
                .setStudentMessage("这次更像是动态规划的状态含义或转移来源没有先定清楚，先用一句话写出每个状态表示什么。");
        analysis.getStudentFeedback().getBlockingIssues().get(0)
                .setNextAction("先用一句话写出每个状态表示什么，再检查转移是否只依赖已经算好的状态。");
        analysis.getStudentFeedback().getImprovementOpportunities().get(0)
                .setCategory("DP_MODELING");
        analysis.getStudentFeedback().getImprovementOpportunities().get(0)
                .setStudentMessage("修正后，把状态含义、初始状态和转移来源分别写成一句话，再用最小样例逐格核对。");
        analysis.getStudentFeedback().getImprovementOpportunities().get(0)
                .setBenefit("这样能避免只会写转移式，却说不清每个状态到底代表什么。");

        SubmissionAnalysisResponse.StudentFeedbackView view = assembler.assemble(analysis);

        assertThat(view).isNotNull();
        assertThat(view.getStatus()).isEqualTo("AI_UNAVAILABLE");
        assertThat(view.getRepairItems()).singleElement()
                .satisfies(item -> assertThat(item.getBody())
                        .contains("AI 暂不可用", "稍后重试")
                        .doesNotContain("深度诊断", "伪装")
                        .doesNotContain("状态表示什么", "转移", "当前最需要先处理的是"));
        assertThat(view.getImprovementItems()).isEmpty();
    }

    @Test
    void v2NaturalReportKeepsBasicTextAndNextActionSeparate() {
        SubmissionAnalysisResponse analysis = analysis(false);
        analysis.getAiInvocation().setPromptVersion(PromptTemplateRegistry.DIAGNOSIS_REPORT_V2);
        analysis.getStudentFeedback().getBlockingIssues().get(0)
                .setStudentMessage("基础层：循环范围和题目边界要求没有完全对齐。先手推最小样例，确认端点是否进入循环。");
        analysis.getStudentFeedback().getBlockingIssues().get(0)
                .setNextAction("下一步：用 n=1 和 n=2 写出循环变量序列，再和题目要求逐项对照。");
        analysis.getStudentFeedback().getImprovementOpportunities().get(0)
                .setStudentMessage("提高层：修好后把最小值、端点值、最大值附近样例加入固定自测清单。");
        analysis.getStudentFeedback().getImprovementOpportunities().get(0)
                .setTitle("边界样例意识");

        SubmissionAnalysisResponse.StudentFeedbackView view = assembler.assemble(analysis);

        assertThat(view.getRepairItems()).singleElement()
                .satisfies(item -> assertThat(item.getBody())
                        .contains("基础层：循环范围")
                        .doesNotContain("下一步："));
        assertThat(view.getPrimaryAction()).contains("基础层：循环范围");
        assertThat(view.getNextQuestion()).contains("第二次读取");
        assertThat(view.getImprovementItems()).singleElement()
                .satisfies(item -> assertThat(item.getTitle()).isEqualTo("边界样例意识"));
    }

    @Test
    void keepsAllEvidenceBackedFeedbackItems() {
        SubmissionAnalysisResponse analysis = analysis(false);
        analysis.getStudentFeedback().setBlockingIssues(List.of(
                SubmissionAnalysisResponse.FeedbackIssue.builder()
                        .title("输入没有完整读取")
                        .studentMessage("先核对题面每一行输入和代码读取次数是否一致。")
                        .issueTag("IO_FORMAT")
                        .fineGrainedTag("INPUT_PARSING")
                        .evidenceRefs(List.of("rule:input"))
                        .build(),
                SubmissionAnalysisResponse.FeedbackIssue.builder()
                        .title("输出目标要复核")
                        .studentMessage("再确认程序最后输出的是题目要求的目标量。")
                        .issueTag("OUTPUT_GOAL")
                        .fineGrainedTag("OUTPUT_TARGET")
                        .evidenceRefs(List.of("case:first_failed"))
                        .build()
        ));
        analysis.getStudentFeedback().setImprovementOpportunities(List.of(
                SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                        .category("TESTING_HABIT")
                        .studentMessage("补一个和样例结构不同的小输入。")
                        .evidenceRefs(List.of("case:first_failed"))
                        .build(),
                SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                        .category("TRACE_VARIABLES")
                        .studentMessage("记录读取后的变量值，对照题面含义。")
                        .evidenceRefs(List.of("rule:input"))
                        .build(),
                SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                        .category("PROBLEM_READING")
                        .studentMessage("修改前先用一句话复述输入和输出分别是什么。")
                        .evidenceRefs(List.of("problem:statement"))
                        .build()
        ));

        SubmissionAnalysisResponse.StudentFeedbackView view = assembler.assemble(analysis);

        assertThat(view.getRepairItems())
                .hasSize(2)
                .extracting(SubmissionAnalysisResponse.FeedbackViewItem::getTitle)
                .containsExactly("输入没有完整读取", "输出目标要复核");
        assertThat(view.getImprovementItems())
                .hasSize(3)
                .extracting(SubmissionAnalysisResponse.FeedbackViewItem::getTitle)
                .containsExactly("测试习惯", "进阶", "进阶");
    }

    @Test
    void keepsAnswerLeakTextVisibleWhenFeedbackIsOtherwiseUsable() {
        SubmissionAnalysisResponse analysis = analysis(false);
        analysis.getStudentFeedback().getBlockingIssues().get(0).setNextAction("直接改成完整代码如下");
        analysis.getStudentFeedback().getImprovementOpportunities().get(0).setStudentMessage("答案如下，替换为正确写法");

        SubmissionAnalysisResponse.StudentFeedbackView view = assembler.assemble(analysis);

        assertThat(view).isNotNull();
        assertThat(view.getRepairItems()).singleElement()
                .satisfies(item -> assertThat(item.getBody()).contains("直接改成完整代码如下"));
        assertThat(view.getImprovementItems()).singleElement()
                .satisfies(item -> assertThat(item.getBody()).contains("答案如下，替换为正确写法"));
        assertThat(view.getPrimaryAction()).contains("直接改成完整代码如下");
        assertThat(view.getNextQuestion()).contains("第二次读取");
        assertThat(viewText(view)).contains("完整代码", "答案如下", "替换为");
    }

    @Test
    void keepsModelSafetyLeakMarkersFromStudentView() {
        SubmissionAnalysisResponse analysis = analysis(false);
        analysis.getStudentFeedback().getBlockingIssues().get(0)
                .setStudentMessage("你的代码目前只验证了字符串首尾字符是否相同。");
        analysis.getStudentFeedback().getBlockingIssues().get(0)
                .setNextAction("检查首尾相同但中间不同的情况。");
        analysis.getStudentFeedback().getImprovementOpportunities().get(0)
                .setStudentMessage("构造首尾相同但中间不同的测试数据。");

        SubmissionAnalysisResponse.StudentFeedbackView view = assembler.assemble(analysis);

        assertThat(view).isNotNull();
        assertThat(view.getRepairItems()).singleElement()
                .satisfies(item -> assertThat(item.getBody()).contains("首尾相同但中间不同"));
        assertThat(view.getImprovementItems()).singleElement()
                .satisfies(item -> assertThat(item.getBody()).contains("首尾相同但中间不同"));
        assertThat(view.getPrimaryAction()).contains("检查首尾相同但中间不同的情况");
        assertThat(viewText(view)).contains("首尾相同但中间不同");
    }

    @Test
    void remainsStableUnderConcurrentAssembly() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<SubmissionAnalysisResponse.StudentFeedbackView>> jobs = IntStream.range(0, 160)
                    .mapToObj(index -> (Callable<SubmissionAnalysisResponse.StudentFeedbackView>) () -> assembler.assemble(analysis(index % 5 == 0)))
                    .toList();

            List<SubmissionAnalysisResponse.StudentFeedbackView> views = executor.invokeAll(jobs)
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();

            assertThat(views).allSatisfy(view -> {
                assertThat(view).isNotNull();
                assertThat(view.getStatus()).isIn("READY", "AI_UNAVAILABLE");
                assertThat(viewText(view)).doesNotContain("本地可验证反馈");
                if ("AI_UNAVAILABLE".equals(view.getStatus())) {
                    assertThat(viewText(view)).contains("AI 暂不可用");
                } else {
                    assertThat(viewText(view)).doesNotContain("AI 暂不可用");
                }
            });
            assertThat(views).filteredOn(view -> "AI_UNAVAILABLE".equals(view.getStatus())).hasSize(32);
        } finally {
            executor.shutdownNow();
        }
    }

    private SubmissionAnalysisResponse analysis(boolean fallbackUsed) {
        return SubmissionAnalysisResponse.builder()
                .submissionId(1L)
                .evidenceRefs(List.of("case:first_failed"))
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .status(fallbackUsed ? "MODEL_FAILED" : "MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .build())
                .studentFeedback(SubmissionAnalysisResponse.StudentFeedback.builder()
                        .summary("AI 暂不可用，先看本地反馈。")
                        .blockingIssues(List.of(SubmissionAnalysisResponse.FeedbackIssue.builder()
                                .title("输入没有完整读取")
                                .studentMessage("当前最需要先处理的问题是输入读取。")
                                .nextAction("先把公开样例的每一行 input 手推清楚。")
                                .issueTag("IO_FORMAT")
                                .fineGrainedTag("INPUT_PARSING")
                                .evidenceRefs(List.of("rule:input"))
                                .build()))
                        .improvementOpportunities(List.of(SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                                .category("TESTING_HABIT")
                                .studentMessage("补一个和样例结构不同的自测。")
                                .benefit("自测能更早发现输入格式问题。")
                                .evidenceRefs(List.of("case:first_failed"))
                                .build()))
                        .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                                .task("先确认输入结构。")
                                .checkQuestion("第二次读取时应该拿到什么？")
                                .evidenceRefs(List.of("rule:input"))
                                .answerLeakRisk("LOW")
                                .build())
                        .build())
                .build();
    }

    private String viewText(SubmissionAnalysisResponse.StudentFeedbackView view) {
        StringBuilder builder = new StringBuilder();
        if (view.getRepairItems() != null) {
            view.getRepairItems().forEach(item -> builder.append(item.getTitle()).append(item.getBody()));
        }
        if (view.getImprovementItems() != null) {
            view.getImprovementItems().forEach(item -> builder.append(item.getTitle()).append(item.getBody()));
        }
        builder.append(view.getPrimaryAction()).append(view.getNextQuestion());
        return builder.toString();
    }
}
