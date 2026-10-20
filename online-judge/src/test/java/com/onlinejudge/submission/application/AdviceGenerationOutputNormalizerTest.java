package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdviceGenerationOutputNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdviceGenerationOutputNormalizer normalizer = new AdviceGenerationOutputNormalizer();
    private final AdviceGenerationOutputValidator validator = new AdviceGenerationOutputValidator();

    @Test
    void movesLegacyFineTagFromSkillUnitIdToMistakePointId() {
        AdviceGenerationOutput output = validOutput();
        output.getBasicLayerAdvice().get(0).setMistakePointId(null);
        output.getBasicLayerAdvice().get(0).setSkillUnitId("OFF_BY_ONE");

        AdviceGenerationOutput normalized = normalizer.normalize(output, pack());

        assertThat(normalized.getBasicLayerAdvice()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getMistakePointId()).isEqualTo("OFF_BY_ONE");
                    assertThat(item.getSkillUnitId()).isNull();
                });
        assertThat(validator.validate(normalized, brief(), pack()).isValid()).isTrue();
    }

    @Test
    void clearsUnknownImprovementAndSkillIdsWithoutChangingAdviceText() {
        AdviceGenerationOutput output = validOutput();
        output.getImprovementLayerAdvice().get(0).setImprovementPointId("边界意识");
        output.getImprovementLayerAdvice().get(0).setSkillUnitId("OFF_BY_ONE");

        AdviceGenerationOutput normalized = normalizer.normalize(output, pack());

        assertThat(normalized.getImprovementLayerAdvice()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getImprovementPointId()).isNull();
                    assertThat(item.getSkillUnitId()).isNull();
                    assertThat(item.getSuggestion()).contains("最小值");
                });
        assertThat(validator.validate(normalized, brief(), pack()).isValid()).isTrue();
    }

    @Test
    void rewritesDpStudentReportLeakIntoSafePrompt() {
        AdviceGenerationOutput output = validOutput();
        output.setStudentReport(AdviceGenerationOutput.StudentReport.builder()
                .hintLevel("L3")
                .basicLayerText("基础层：你写了 skip_current = dp[i-1] 和 take_current = values[i]。")
                .improvementLayerText("提高层：之后可以做空间压缩。")
                .nextActionText("下一步：检查前驱状态。")
                .build());
        output.setStudentSummary("这次重点是状态定义。");

        AdviceGenerationOutput normalized = normalizer.normalize(output, pack());

        assertThat(normalized.getStudentReport().getBasicLayerText())
                .contains("状态含义")
                .doesNotContain("dp[i", "skip_current", "take_current", "转移来源");
        assertThat(normalized.getStudentReport().getImprovementLayerText())
                .doesNotContain("空间压缩");
        assertThat(normalized.getStudentReport().getNextActionText())
                .contains("可见失败样例");
        assertThat(validator.validate(normalized, brief(), pack()).isValid()).isTrue();
    }

    @Test
    void parsesStringOutOfLibraryFindingsAsObjects() throws Exception {
        AdviceGenerationOutput output = objectMapper.readValue("""
                {
                  "diagnosisDecision": {
                    "libraryFit": "OUT_OF_LIBRARY",
                    "outOfLibraryFindings": ["rollback_components_restore_error"]
                  }
                }
                """, AdviceGenerationOutput.class);

        assertThat(output.getDiagnosisDecision().getOutOfLibraryFindings()).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.getName()).isEqualTo("rollback_components_restore_error");
                    assertThat(finding.getReason()).isEqualTo("rollback_components_restore_error");
                });
    }

    @Test
    void expandsTextOnlyAdviceItemsIntoStructuredAdvice() throws Exception {
        AdviceGenerationOutput output = objectMapper.readValue("""
                {
                  "caseUnderstanding": {
                    "problemGoal": "离线处理动态图连通性。",
                    "codeIntent": "使用线段树分治和可回滚并查集。",
                    "behaviorGap": "删除后查询仍然连通。",
                    "primaryEvidenceRef": "judge:first_failed_case"
                  },
                  "basicLayerAdvice": [{
                    "text": "检查 RollbackDSU 的 rollback 方法，确保状态能准确恢复。",
                    "evidenceRefs": ["code:line:88", "code:line:95"]
                  }, {
                    "text": "审查 remove_edge 中边存在区间的右边界。",
                    "evidenceRefs": ["code:line:160"]
                  }],
                  "improvementLayerAdvice": [{
                    "text": "编写针对并查集回滚的单元测试。",
                    "evidenceRefs": ["judge:first_failed_case"]
                  }, {
                    "text": "打印线段树分治关键时刻的边集合。",
                    "evidenceRefs": ["code:line:106"]
                  }],
                  "studentSummary": "先从回滚和区间边界入手。"
                }
                """, AdviceGenerationOutput.class);

        AdviceGenerationOutput normalized = normalizer.normalize(output, StandardLibraryPack.builder().build());

        assertThat(normalized.getBasicLayerAdvice()).hasSize(2)
                .allSatisfy(item -> {
                    assertThat(item.getTitle()).isNotBlank();
                    assertThat(item.getStudentAction()).isNotBlank();
                    assertThat(item.getCheckQuestion()).isNotBlank();
                });
        assertThat(normalized.getImprovementLayerAdvice()).hasSize(2)
                .allSatisfy(item -> {
                    assertThat(item.getTitle()).isNotBlank();
                    assertThat(item.getSuggestion()).isNotBlank();
                    assertThat(item.getStudentBenefit()).isNotBlank();
                });
        assertThat(validator.validate(normalized, longCodeBrief(), StandardLibraryPack.builder().build()).isValid())
                .isTrue();
    }

    @Test
    void expandsStringAdviceItemsIntoStructuredAdvice() throws Exception {
        AdviceGenerationOutput output = objectMapper.readValue("""
                {
                  "caseUnderstanding": {
                    "problemGoal": "分层最短路。",
                    "codeIntent": "维护 dist[node][used]。",
                    "behaviorGap": "优惠券没有降低路径费用。",
                    "primaryEvidenceRef": "judge:first_failed_case"
                  },
                  "basicLayerAdvice": [
                    "检查 should_skip_state 函数中的剪枝逻辑，确保它能区分不同优惠券使用数量下的状态。"
                  ],
                  "improvementLayerAdvice": [
                    "补充一个同一节点不同 used 层的手推样例。"
                  ],
                  "nextStepPlan": [
                    "先画出 dist[node][used] 的状态表。"
                  ],
                  "studentSummary": "先检查分层状态。"
                }
                """, AdviceGenerationOutput.class);

        AdviceGenerationOutput normalized = normalizer.normalize(output, StandardLibraryPack.builder().build());

        assertThat(normalized.getBasicLayerAdvice()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getText()).contains("should_skip_state");
                    assertThat(item.getTitle()).isNotBlank();
                    assertThat(item.getStudentAction()).isNotBlank();
                });
        assertThat(normalized.getImprovementLayerAdvice()).singleElement()
                .satisfies(item -> assertThat(item.getSuggestion()).contains("手推样例"));
        assertThat(normalized.getNextStepPlan()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getTarget()).contains("状态表");
                    assertThat(item.getReason()).contains("状态表");
                });
        assertThat(validator.validate(normalized, longCodeBrief(), StandardLibraryPack.builder().build()).isValid())
                .isTrue();
    }

    @Test
    void stripsRepeatedTitleFromStudentVisibleAdviceFields() {
        AdviceGenerationOutput output = validOutput();
        output.getBasicLayerAdvice().get(0).setTitle("检查 should_skip_state 函数的剪枝逻辑");
        output.getBasicLayerAdvice().get(0).setWhatHappened(
                "检查 should_skip_state 函数的剪枝逻辑：同一节点不同 used 层被合并判断。");
        output.getBasicLayerAdvice().get(0).setStudentAction(
                "检查 should_skip_state 函数的剪枝逻辑，先手推同一节点不同 used 层的 dist 值。");
        output.getImprovementLayerAdvice().get(0).setTitle("线段树 lazy 累积检查");
        output.getImprovementLayerAdvice().get(0).setSuggestion(
                "线段树 lazy 累积检查：连续两次区间加后观察 lazy 和 max 的变化。");

        AdviceGenerationOutput normalized = normalizer.normalize(output, pack());

        assertThat(normalized.getBasicLayerAdvice()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getWhatHappened()).isEqualTo("同一节点不同 used 层被合并判断。");
                    assertThat(item.getStudentAction()).isEqualTo("先手推同一节点不同 used 层的 dist 值");
                });
        assertThat(normalized.getImprovementLayerAdvice()).singleElement()
                .satisfies(item -> assertThat(item.getSuggestion())
                        .isEqualTo("连续两次区间加后观察 lazy 和 max 的变化"));
    }

    @Test
    void sanitizesDirectFixToneAndKeepsOneSmallNextStep() {
        AdviceGenerationOutput output = validOutput();
        output.getBasicLayerAdvice().get(0).setStudentAction(
                "直接修改 should_skip_state 函数，然后再处理答案统计。");
        output.getImprovementLayerAdvice().get(0).setSuggestion("直接调整 lazy 标记，然后补自测。");
        output.setNextStepPlan(List.of(
                AdviceGenerationOutput.NextStepAdvice.builder()
                        .step(1)
                        .target("先画出 dist[node][used] 状态表，然后修改剪枝条件。")
                        .reason("先观察状态表，然后再动代码。")
                        .evidenceRef("code:range_excludes_n")
                        .build(),
                AdviceGenerationOutput.NextStepAdvice.builder()
                        .step(2)
                        .target("再检查答案统计。")
                        .reason("第二步。")
                        .evidenceRef("code:range_excludes_n")
                        .build()
        ));

        AdviceGenerationOutput normalized = normalizer.normalize(output, pack());
        String visible = String.join(" ",
                normalized.getBasicLayerAdvice().get(0).getStudentAction(),
                normalized.getImprovementLayerAdvice().get(0).getSuggestion(),
                normalized.getNextStepPlan().get(0).getTarget(),
                normalized.getNextStepPlan().get(0).getReason());

        assertThat(visible).doesNotContain("直接修改", "直接改成", "完整代码", "替换为");
        assertThat(normalized.getBasicLayerAdvice().get(0).getStudentAction())
                .isEqualTo("检查 should_skip_state 函数");
        assertThat(normalized.getImprovementLayerAdvice().get(0).getSuggestion())
                .isEqualTo("检查 lazy 标记");
        assertThat(normalized.getNextStepPlan()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getStep()).isEqualTo(1);
                    assertThat(item.getTarget()).isEqualTo("先画出 dist[node][used] 状态表");
                    assertThat(item.getReason()).isEqualTo("先观察状态表");
                });
        assertThat(validator.validate(normalized, brief(), pack()).isValid()).isTrue();
    }

    @Test
    void repairsLiveAdviceItemsThatUseBasicFieldNames() throws Exception {
        AdviceGenerationOutput output = objectMapper.readValue("""
                {
                  "studentReport": {
                    "hintLevel": "L3",
                    "basicLayerText": "先检查线段树 lazy 累积。",
                    "improvementLayerText": "再补一个连续区间更新 trace。",
                    "nextActionText": "画出 lazy 和 max 的变化表。"
                  },
                  "caseUnderstanding": {
                    "problemGoal": "维护区间加和区间最大值。",
                    "codeIntent": "使用懒标记线段树。",
                    "behaviorGap": "连续更新后最大值错误。",
                    "primaryEvidenceRef": "code:range_excludes_n"
                  },
                  "basicLayerAdvice": [{
                    "title": "懒标记覆盖而非累积",
                    "whatHappened": "apply_add 中 lazy 被覆盖，连续两次区间加后前一次更新会丢失。",
                    "studentAction": "连续两次区间加后观察 lazy 和 max 的变化。",
                    "evidenceRefs": ["code:range_excludes_n"]
                  }],
                  "improvementLayerAdvice": [{
                    "title": "调试懒标记传播过程",
                    "whatHappened": "懒标记传播过程缺少可复盘记录。",
                    "studentAction": "设计一组简单操作序列，记录每一步 lazy 状态变化。",
                    "evidenceRefs": ["code:range_excludes_n"]
                  }],
                  "nextStepPlan": ["画出 lazy 和 max 的变化表。"],
                  "studentSummary": "先看 lazy 累积。"
                }
                """, AdviceGenerationOutput.class);

        AdviceGenerationOutput normalized = normalizer.normalize(output, pack());

        assertThat(normalized.getBasicLayerAdvice()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getWhyItMatters()).isNotBlank();
                    assertThat(item.getCheckQuestion()).isNotBlank();
                });
        assertThat(normalized.getImprovementLayerAdvice()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getCurrentLimit()).contains("懒标记传播过程");
                    assertThat(item.getSuggestion()).contains("简单操作序列");
                    assertThat(item.getStudentBenefit()).isNotBlank();
                });
        assertThat(validator.validate(normalized, brief(), pack()).isValid()).isTrue();
    }

    @Test
    void cleansLiveSafetyRetryLeakPhrasesBeforeValidation() {
        AdviceGenerationOutput output = validOutput();
        output.setStudentReport(AdviceGenerationOutput.StudentReport.builder()
                .hintLevel("L3")
                .basicLayerText("先检查回滚状态和时间区间。")
                .improvementLayerText("再复盘线段树分治中的时间片隔离。")
                .nextActionText("选择一个节点手动追踪换根过程，观察当前公式如何影响最终答案。")
                .build());
        output.getBasicLayerAdvice().get(0).setWhatHappened(
                "在remove_edge方法中，将边的结束时间设为当前时间，而实际上该边应在时间t-1之前仍然有效。");
        output.setNextStepPlan(List.of(AdviceGenerationOutput.NextStepAdvice.builder()
                .step(1)
                .target("选择一个节点手动追踪换根过程，观察当前公式如何影响最终答案。")
                .reason("观察当前公式如何影响最终答案。")
                .evidenceRef("code:range_excludes_n")
                .build()));

        AdviceGenerationOutput normalized = normalizer.normalize(output, pack());
        String visible = String.join(" ",
                normalized.getStudentReport().getNextActionText(),
                normalized.getBasicLayerAdvice().get(0).getWhatHappened(),
                normalized.getNextStepPlan().get(0).getTarget(),
                normalized.getNextStepPlan().get(0).getReason());

        assertThat(visible)
                .doesNotContain("最终答案", "将边的结束时间设为", "应在时间t-1", "直接改成", "替换为");
        assertThat(validator.validate(normalized, brief(), pack()).isValid()).isTrue();
    }

    private AdviceGenerationOutput validOutput() {
        return AdviceGenerationOutput.builder()
                .caseUnderstanding(AdviceGenerationOutput.CaseUnderstanding.builder()
                        .problemGoal("输出 1 到 n 的整数和。")
                        .codeIntent("学生使用循环累加。")
                        .behaviorGap("循环实际没有覆盖题目要求的末端。")
                        .primaryEvidenceRef("code:range_excludes_n")
                        .build())
                .basicLayerAdvice(List.of(AdviceGenerationOutput.BasicLayerAdvice.builder()
                        .mistakePointId("OFF_BY_ONE")
                        .skillUnitId(null)
                        .title("循环右边界漏取")
                        .whatHappened("当前循环范围没有覆盖题目要求的最后一个数。")
                        .whyItMatters("少处理端点会让求和结果偏小。")
                        .studentAction("先手推 n=1 和 n=2 时循环变量实际出现过哪些值。")
                        .checkQuestion("最后一个应该被处理的数有没有进入循环？")
                        .evidenceRefs(List.of("code:range_excludes_n"))
                        .confidence(0.92)
                        .build()))
                .improvementLayerAdvice(List.of(AdviceGenerationOutput.ImprovementLayerAdvice.builder()
                        .improvementPointId("TESTING_HABIT")
                        .skillUnitId(null)
                        .title("补充边界样例意识")
                        .currentLimit("这类问题不是算法方向错，而是边界验证不足。")
                        .suggestion("修复后补测最小值、端点值和最大值附近样例。")
                        .studentBenefit("能更早发现开闭区间和下标边界问题。")
                        .evidenceRefs(List.of("code:range_excludes_n"))
                        .confidence(0.8)
                        .build()))
                .nextStepPlan(List.of(AdviceGenerationOutput.NextStepAdvice.builder()
                        .step(1)
                        .target("手推循环变量取值。")
                        .reason("这是当前阻塞通过的主要问题。")
                        .evidenceRef("code:range_excludes_n")
                        .build()))
                .studentSummary("这次主要卡在循环边界和题目要求范围没有对齐。")
                .build();
    }

    private ModelDiagnosisBrief brief() {
        return ModelDiagnosisBrief.builder()
                .schemaVersion(ModelDiagnosisBrief.SCHEMA_VERSION)
                .verdict("WRONG_ANSWER")
                .evidenceRefs(List.of("code:range_excludes_n"))
                .build();
    }

    private ModelDiagnosisBrief longCodeBrief() {
        return ModelDiagnosisBrief.builder()
                .schemaVersion(ModelDiagnosisBrief.SCHEMA_VERSION)
                .verdict("WRONG_ANSWER")
                .sourceCodeLineCount(220)
                .evidenceRefs(List.of("judge:first_failed_case"))
                .build();
    }

    private StandardLibraryPack pack() {
        return StandardLibraryPack.builder()
                .basicCauses(List.of(StandardLibraryPack.BasicCauseOption.builder()
                        .id("OFF_BY_ONE")
                        .name("差一位错误")
                        .build()))
                .improvementPoints(List.of(StandardLibraryPack.ImprovementPointOption.builder()
                        .id("TESTING_HABIT")
                        .category("TESTING_HABIT")
                        .name("自测与反例构造")
                        .build()))
                .improvementTags(List.of(StandardLibraryPack.ImprovementTagOption.builder()
                        .id("TESTING_HABIT")
                        .label("自测习惯")
                        .build()))
                .build();
    }
}
