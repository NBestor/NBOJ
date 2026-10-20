package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdviceGenerationFeedbackMapperTest {

    private final AdviceGenerationFeedbackMapper mapper = new AdviceGenerationFeedbackMapper();
    private final ModelOutputValidator validator = new ModelOutputValidator();

    @Test
    void mapsDiagnosisReportV2NaturalSectionsToLegacyStudentFeedback() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .diagnosisDecision(AdviceGenerationOutput.DiagnosisDecision.builder()
                        .libraryFit("PARTIAL")
                        .anchors(List.of(AdviceGenerationOutput.DiagnosisAnchor.builder()
                                .id("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .type("MISTAKE_POINT")
                                .role("PRIMARY")
                                .confidence(0.86)
                                .evidenceRefs(List.of("code:range_excludes_n"))
                                .reason("候选方向对，但题目还需要结合最小样例手推。")
                                .build()))
                        .build())
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：这次最主要的问题不是求和思路，而是循环覆盖范围和题目要求没有对齐。建议先拿 n=1、n=2 手推循环变量实际出现过哪些值，再判断最后一个数有没有被处理。")
                        .improvementLayerText("提高层：修好后可以把最小值、端点值、最大值附近样例当成固定自测清单，这会帮你更快发现开闭区间类问题。")
                        .nextActionText("下一步：不要急着改代码，先在纸上写出 n=1、n=2 时循环变量序列，再和题目要求逐项对照。")
                        .build())
                .studentSummary("这次重点是循环边界和边界样例意识。")
                .build();

        StandardLibraryPack pack = StandardLibraryPack.builder()
                .teachingActions(List.of(StandardLibraryPack.TeachingActionOption.builder()
                        .id("TRACE_VARIABLES")
                        .label("手推变量")
                        .build()))
                .build();

        SubmissionAnalysisResponse.StudentFeedback feedback = mapper.toStudentFeedback(output, pack);

        assertThat(feedback.getBlockingIssues()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getStudentMessage()).contains("基础层：这次最主要的问题");
                    assertThat(item.getStudentMessage()).contains("n=1、n=2");
                    assertThat(item.getFineGrainedTag()).isEqualTo("MP_RANGE_RIGHT_ENDPOINT_MISSING");
                    assertThat(item.getEvidenceRefs()).containsExactly("code:range_excludes_n");
                });
        assertThat(feedback.getImprovementOpportunities()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getStudentMessage()).contains("提高层：修好后可以");
                    assertThat(item.getBenefit()).isEqualTo(item.getStudentMessage());
                    assertThat(item.getEvidenceRefs()).isEmpty();
                });
        assertThat(feedback.getNextLearningAction().getHintLevel()).isEqualTo("L3");
        assertThat(feedback.getNextLearningAction().getTask()).contains("下一步：不要急着改代码");
        assertThat(feedback.getNextLearningAction().getCheckQuestion()).contains("下一步：不要急着改代码");
    }

    @Test
    void diagnosisReportV2KeepsMultipleStructuredAdviceItems() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .diagnosisDecision(AdviceGenerationOutput.DiagnosisDecision.builder()
                        .libraryFit("PARTIAL")
                        .anchors(List.of(AdviceGenerationOutput.DiagnosisAnchor.builder()
                                .id("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .type("MISTAKE_POINT")
                                .role("PRIMARY")
                                .confidence(0.86)
                                .evidenceRefs(List.of("code:range_excludes_n"))
                                .reason("循环边界证据明确。")
                                .build()))
                        .build())
                .basicLayerAdvice(List.of(
                        AdviceGenerationOutput.BasicLayerAdvice.builder()
                                .mistakePointId("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .skillUnitId("SK_RANGE_BOUNDARY")
                                .title("循环右边界漏取")
                                .whatHappened("循环没有覆盖题目要求的末端。")
                                .whyItMatters("端点漏处理会让结果偏小。")
                                .studentAction("先手推循环变量实际出现过哪些值。")
                                .checkQuestion("最后一个数有没有进入循环？")
                                .evidenceRefs(List.of("code:range_excludes_n"))
                                .confidence(0.9)
                                .build(),
                        AdviceGenerationOutput.BasicLayerAdvice.builder()
                                .mistakePointId(null)
                                .skillUnitId("SK_RANGE_BOUNDARY")
                                .title("失败样例没有被对照")
                                .whatHappened("可见失败样例已经暴露实际输出与期望不一致。")
                                .whyItMatters("不对照样例就容易只凭感觉修改。")
                                .studentAction("把实际输出和期望输出逐项写在旁边。")
                                .checkQuestion("第一处差异来自哪个循环取值？")
                                .evidenceRefs(List.of("judge:first_failed_case"))
                                .confidence(0.78)
                                .build()
                ))
                .improvementLayerAdvice(List.of(
                        AdviceGenerationOutput.ImprovementLayerAdvice.builder()
                                .improvementPointId("IP_BOUNDARY_MIN_CASE_TEST")
                                .skillUnitId("SK_RANGE_BOUNDARY")
                                .title("补充端点自测")
                                .currentLimit("当前缺少端点意识。")
                                .suggestion("修复后加入最小值和端点附近自测。")
                                .studentBenefit("更容易发现开闭区间问题。")
                                .evidenceRefs(List.of("code:range_excludes_n"))
                                .confidence(0.8)
                                .build(),
                        AdviceGenerationOutput.ImprovementLayerAdvice.builder()
                                .improvementPointId("IP_VISIBLE_CASE_TRACE")
                                .skillUnitId("SK_RANGE_BOUNDARY")
                                .title("保留手推记录")
                                .currentLimit("调试时缺少可复盘的变量序列。")
                                .suggestion("每次改动前后保留一份变量取值记录。")
                                .studentBenefit("能判断修改是否真的解决同一类问题。")
                                .evidenceRefs(List.of("judge:first_failed_case"))
                                .confidence(0.74)
                                .build()
                ))
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：这次重点是循环范围和失败样例对照。")
                        .improvementLayerText("提高层：修复后补充边界自测和手推记录。")
                        .nextActionText("先写出循环变量序列。")
                        .build())
                .studentSummary("这次有两个基础层检查点和两个提高层方向。")
                .build();

        StandardLibraryPack pack = StandardLibraryPack.builder()
                .improvementPoints(List.of(
                        StandardLibraryPack.ImprovementPointOption.builder()
                                .id("IP_BOUNDARY_MIN_CASE_TEST")
                                .category("TESTING_HABIT")
                                .name("端点自测")
                                .build(),
                        StandardLibraryPack.ImprovementPointOption.builder()
                                .id("IP_VISIBLE_CASE_TRACE")
                                .category("TRACE_REVIEW")
                                .name("手推记录")
                                .build()
                ))
                .improvementTags(List.of(
                        StandardLibraryPack.ImprovementTagOption.builder()
                                .id("TESTING_HABIT")
                                .label("自测习惯")
                                .build(),
                        StandardLibraryPack.ImprovementTagOption.builder()
                                .id("TRACE_REVIEW")
                                .label("复盘追踪")
                                .build()
                ))
                .teachingActions(List.of(StandardLibraryPack.TeachingActionOption.builder()
                        .id("TRACE_VARIABLES")
                        .label("手推变量")
                        .build()))
                .build();

        SubmissionAnalysisResponse.StudentFeedback feedback = mapper.toStudentFeedback(output, pack);

        assertThat(feedback.getBlockingIssues())
                .hasSize(2)
                .extracting(SubmissionAnalysisResponse.FeedbackIssue::getTitle)
                .containsExactly("循环右边界漏取", "失败样例没有被对照");
        assertThat(feedback.getImprovementOpportunities())
                .hasSize(2)
                .extracting(SubmissionAnalysisResponse.ImprovementOpportunity::getCategory)
                .containsExactly("TESTING_HABIT", "TRACE_REVIEW");
        assertThat(feedback.getNextLearningAction().getTask()).isEqualTo("先写出循环变量序列。");
    }

    @Test
    void mapsFineGrainedImprovementPointToLegacyImprovementTagCategory() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .caseUnderstanding(AdviceGenerationOutput.CaseUnderstanding.builder()
                        .problemGoal("输出 1 到 n 的整数和。")
                        .codeIntent("学生使用循环累加。")
                        .behaviorGap("循环没有覆盖题目要求的端点。")
                        .primaryEvidenceRef("code:range_excludes_n")
                        .build())
                .basicLayerAdvice(List.of(AdviceGenerationOutput.BasicLayerAdvice.builder()
                        .mistakePointId("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                        .skillUnitId("SK_RANGE_BOUNDARY")
                        .title("循环右边界漏取")
                        .whatHappened("循环范围和题目要求不一致。")
                        .whyItMatters("少处理端点会影响求和结果。")
                        .studentAction("手推 n=1 时循环变量实际出现过哪些值。")
                        .checkQuestion("最后一个需要处理的数有没有进入循环？")
                        .evidenceRefs(List.of("code:range_excludes_n"))
                        .confidence(0.9)
                        .build()))
                .improvementLayerAdvice(List.of(AdviceGenerationOutput.ImprovementLayerAdvice.builder()
                        .improvementPointId("IP_BOUNDARY_MIN_CASE_TEST")
                        .skillUnitId("SK_RANGE_BOUNDARY")
                        .title("补充最小边界自测")
                        .currentLimit("这次暴露的是边界验证不足。")
                        .suggestion("修复后先补测最小输入和端点附近输入。")
                        .studentBenefit("能更早发现开闭区间问题。")
                        .evidenceRefs(List.of("code:range_excludes_n"))
                        .confidence(0.82)
                        .build()))
                .nextStepPlan(List.of(AdviceGenerationOutput.NextStepAdvice.builder()
                        .step(1)
                        .target("手推循环变量取值。")
                        .reason("这是当前阻塞通过的主要问题。")
                        .evidenceRef("code:range_excludes_n")
                        .build()))
                .studentSummary("这次主要卡在循环边界和最小样例验证。")
                .build();

        StandardLibraryPack pack = StandardLibraryPack.builder()
                .improvementPoints(List.of(StandardLibraryPack.ImprovementPointOption.builder()
                        .id("IP_BOUNDARY_MIN_CASE_TEST")
                        .category("BOUNDARY_AWARENESS")
                        .name("最小边界自测")
                        .build()))
                .improvementTags(List.of(
                        StandardLibraryPack.ImprovementTagOption.builder()
                                .id("TESTING_HABIT")
                                .label("自测习惯")
                                .build(),
                        StandardLibraryPack.ImprovementTagOption.builder()
                                .id("BOUNDARY_AWARENESS")
                                .label("边界意识")
                                .build()
                ))
                .teachingActions(List.of(StandardLibraryPack.TeachingActionOption.builder()
                        .id("TRACE_VARIABLES")
                        .label("手推变量")
                        .build()))
                .build();
        ModelDiagnosisBrief brief = ModelDiagnosisBrief.builder()
                .verdict("WRONG_ANSWER")
                .evidenceRefs(List.of("code:range_excludes_n"))
                .build();

        SubmissionAnalysisResponse.StudentFeedback feedback = mapper.toStudentFeedback(output, pack);

        assertThat(feedback.getImprovementOpportunities()).singleElement()
                .satisfies(item -> assertThat(item.getCategory()).isEqualTo("BOUNDARY_AWARENESS"));
        assertThat(validator.validateStudentFeedback(feedback, brief, pack).isValid()).isTrue();
    }
}
