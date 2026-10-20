package com.onlinejudge.submission.application;

import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdviceGenerationOutputValidatorTest {

    private final AdviceGenerationOutputValidator validator = new AdviceGenerationOutputValidator();

    @Test
    void acceptsValidAdviceOutput() {
        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                validOutput(),
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.NONE);
    }

    @Test
    void acceptsDiagnosisReportV2WithStateDefinitionAtL3() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .executionGate(AdviceGenerationOutput.ExecutionGate.builder()
                        .state("SHOW_TO_STUDENT")
                        .priority("BASIC_FIRST")
                        .reason("存在基础层阻塞问题。")
                        .build())
                .diagnosisDecision(AdviceGenerationOutput.DiagnosisDecision.builder()
                        .libraryFit("HIT")
                        .anchors(List.of(AdviceGenerationOutput.DiagnosisAnchor.builder()
                                .id("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .type("MISTAKE_POINT")
                                .role("PRIMARY")
                                .confidence(0.9)
                                .evidenceRefs(List.of("code:range_excludes_n"))
                                .reason("循环取值范围和题目端点要求不一致。")
                                .build()))
                        .build())
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：先把 f[i][j] 这类状态含义想清楚，再检查循环端点是否覆盖题目要求。")
                        .improvementLayerText("提高层：修好后用最小值和端点值做边界自测。")
                        .nextActionText("下一步：手推 n=1、n=2 时循环变量实际出现过哪些值。")
                        .build())
                .basicLayerAdvice(validOutput().getBasicLayerAdvice())
                .improvementLayerAdvice(validOutput().getImprovementLayerAdvice())
                .studentSummary("这次重点是边界和状态含义。")
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsDiagnosisReportV2WhenStudentReportReplacesAdviceArrays() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：循环范围和题目边界要求没有完全对齐。")
                        .improvementLayerText("提高层：修好后补充端点附近的自测。")
                        .nextActionText("下一步：手推一个最小样例，确认循环变量是否覆盖端点。")
                        .build())
                .studentSummary("这次重点是循环边界。")
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("studentReport is summary only");
    }

    @Test
    void rejectsGenericEvidenceAliasesForDiagnosisReportV2() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .diagnosisDecision(AdviceGenerationOutput.DiagnosisDecision.builder()
                        .libraryFit("PARTIAL")
                        .anchors(List.of(AdviceGenerationOutput.DiagnosisAnchor.builder()
                                .id("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .type("MISTAKE_POINT")
                                .role("PRIMARY")
                                .confidence(0.82)
                                .evidenceRefs(List.of("sourceCode", "problemConstraints"))
                                .reason("模型发现了库里没有精确覆盖的边界错因。")
                                .build()))
                        .build())
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：代码的循环范围和题目边界条件没有完全对齐。")
                        .improvementLayerText("提高层：修好后补充最小值和端点值自测。")
                        .nextActionText("下一步：先手推一个最小样例，确认循环变量是否覆盖端点。")
                        .build())
                .studentSummary("这次重点是边界条件。")
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.INVALID_EVIDENCE_REF);
        assertThat(result.getMessage()).contains("sourceCode");
    }

    @Test
    void acceptsDiagnosisReportV2ImprovementWithoutEvidenceRefs() {
        AdviceGenerationOutput output = validDiagnosisReportV2("HIT", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");
        output.setImprovementLayerAdvice(List.of(AdviceGenerationOutput.ImprovementLayerAdvice.builder()
                .improvementPointId("IP_BOUNDARY_MIN_CASE_TEST")
                .skillUnitId("SK_RANGE_BOUNDARY")
                .title("补充端点自测")
                .currentLimit("当前更需要在修复后形成边界自测习惯。")
                .suggestion("通过基础问题后，把端点形态加入自测清单。")
                .studentBenefit("下次能更早发现边界类问题。")
                .evidenceRefs(List.of())
                .confidence(0.72)
                .build()));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(output.getImprovementLayerAdvice()).singleElement()
                .satisfies(item -> assertThat(item.getEvidenceRefs()).isEmpty());
    }

    @Test
    void acceptsDiagnosisReportV2CodeRangeAndEvidenceAliases() {
        AdviceGenerationOutput output = validDiagnosisReportV2("HIT",
                "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                "MISTAKE_POINT");
        output.getDiagnosisDecision().getAnchors().get(0)
                .setEvidenceRefs(List.of("code:line:3-5", "judge:first_failed_case:case1"));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(output.getDiagnosisDecision().getAnchors()).singleElement()
                .satisfies(anchor -> assertThat(anchor.getEvidenceRefs())
                        .containsExactly("code:range:3-5", "judge:first_failed_case"));
        assertThat(result.getSoftFixes())
                .contains("evidenceRef alias code:line:3-5 -> code:range:3-5")
                .contains("evidenceRef alias judge:first_failed_case:case1 -> judge:first_failed_case");
    }

    @Test
    void fillsDiagnosisReportV2AnchorEvidenceFromPrimaryEvidence() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL",
                "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                "MISTAKE_POINT");
        output.setCaseUnderstanding(AdviceGenerationOutput.CaseUnderstanding.builder()
                .problemGoal("计算加权距离和。")
                .codeIntent("使用换根 DP。")
                .behaviorGap("公式中的权值方向不一致。")
                .primaryEvidenceRef("code:line:3-5")
                .build());
        output.getDiagnosisDecision().getAnchors().get(0).setEvidenceRefs(List.of());

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(output.getDiagnosisDecision().getAnchors()).singleElement()
                .satisfies(anchor -> assertThat(anchor.getEvidenceRefs())
                        .containsExactly("code:range:3-5"));
        assertThat(result.getSoftFixes())
                .contains("caseUnderstanding.primaryEvidenceRef alias code:line:3-5 -> code:range:3-5")
                .contains("diagnosisDecision anchor evidenceRefs filled from primary evidence");
    }

    @Test
    void softDowngradesDiagnosisReportV2HitWithoutKnownAnchorId() {
        AdviceGenerationOutput output = validDiagnosisReportV2("HIT", null, "OUT_OF_LIBRARY");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(output.getDiagnosisDecision().getLibraryFit()).isEqualTo("PARTIAL");
        assertThat(result.getSoftFixes())
                .contains("diagnosisDecision.libraryFit downgraded HIT -> PARTIAL because no known anchor remained");
    }

    @Test
    void softConvertsDiagnosisReportV2UnknownAnchorIdToOutOfLibrary() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "MP_UNKNOWN_MODEL_HALLUCINATION", "MISTAKE_POINT");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes())
                .contains("diagnosisDecision anchor converted to OUT_OF_LIBRARY: MP_UNKNOWN_MODEL_HALLUCINATION");
        assertThat(output.getDiagnosisDecision().getAnchors()).singleElement()
                .satisfies(anchor -> {
                    assertThat(anchor.getType()).isEqualTo("OUT_OF_LIBRARY");
                    assertThat(anchor.getId()).isNull();
                });
    }

    @Test
    void softNormalizesDiagnosisReportV2MissWithKnownAnchorIdToPartial() {
        AdviceGenerationOutput output = validDiagnosisReportV2("MISS", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(output.getDiagnosisDecision().getLibraryFit()).isEqualTo("PARTIAL");
        assertThat(result.getSoftFixes())
                .contains("diagnosisDecision.libraryFit normalized: MISS -> PARTIAL because known anchor exists");
    }

    @Test
    void normalizesDiagnosisReportV2ConceptAliasToStandardLibraryId() {
        AdviceGenerationOutput output = validDiagnosisReportV2("HIT", "RECURSION_BASE_CASE", "MISTAKE_POINT");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                richPack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(output.getDiagnosisDecision().getAnchors()).singleElement()
                .satisfies(anchor -> assertThat(anchor.getId()).isEqualTo("MP_RECURSION_EXIT_MISSING"));
        assertThat(result.getSoftFixes())
                .contains("diagnosisDecision anchor id normalized: RECURSION_BASE_CASE -> MP_RECURSION_EXIT_MISSING");
    }

    @Test
    void normalizesDiagnosisReportV2LibraryFitSynonyms() {
        AdviceGenerationOutput output = validDiagnosisReportV2("OUT_OF_LIBRARY", null, "OUT_OF_LIBRARY");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(output.getDiagnosisDecision().getLibraryFit()).isEqualTo("MISS");
        assertThat(result.getSoftFixes())
                .contains("diagnosisDecision.libraryFit normalized: OUT_OF_LIBRARY -> MISS");
    }

    @Test
    void softDropsOutOfLibraryFindingWithInvalidEvidence() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");
        output.getDiagnosisDecision().setOutOfLibraryFindings(List.of(
                AdviceGenerationOutput.OutOfLibraryFinding.builder()
                        .name("模型发现的库外错因")
                        .reason("这个发现本身可以保留给候选池，但证据引用写坏了。")
                        .evidenceRefs(List.of("sourceCode"))
                        .confidence(0.7)
                        .build()
        ));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(output.getDiagnosisDecision().getOutOfLibraryFindings()).isEmpty();
        assertThat(result.getSoftFixes()).contains(
                "diagnosisDecision.outOfLibraryFinding dropped: diagnosisDecision.outOfLibraryFindings.evidenceRefs contains invalid evidenceRef=sourceCode");
    }

    @Test
    void acceptsDiagnosisReportV2DiagnosisCandidatesWithKnownAndOutOfLibraryItems() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");
        output.setDiagnosisCandidates(List.of(
                AdviceGenerationOutput.DiagnosisCandidate.builder()
                        .name("循环边界没有覆盖题目端点")
                        .layer("BASIC")
                        .libraryFit("HIT")
                        .anchorId("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                        .anchorType("MISTAKE_POINT")
                        .libraryPath(libraryPath())
                        .role("PRIMARY")
                        .evidenceRefs(List.of("code:range_excludes_n"))
                        .reason("代码证据显示循环范围和题目端点不一致。")
                        .confidence(0.91)
                        .build(),
                AdviceGenerationOutput.DiagnosisCandidate.builder()
                        .name("边界自测意识不足")
                        .layer("IMPROVEMENT")
                        .libraryFit("OUT_OF_LIBRARY")
                        .anchorId(null)
                        .anchorType("OUT_OF_LIBRARY")
                        .libraryPath(List.of("基础层", "自测与反例构造", "边界自测习惯"))
                        .role("SECONDARY")
                        .evidenceRefs(List.of("judge:first_failed_case"))
                        .reason("当前标准库没有精确覆盖这类自测习惯表达。")
                        .confidence(0.7)
                        .build()
        ));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void softConvertsUnknownDiagnosisCandidateAnchorIdToOutOfLibrary() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");
        output.setDiagnosisCandidates(List.of(AdviceGenerationOutput.DiagnosisCandidate.builder()
                .name("模型自己编造的错因")
                .layer("BASIC")
                .libraryFit("HIT")
                .anchorId("MP_MODEL_MADE_UP")
                .anchorType("MISTAKE_POINT")
                .libraryPath(libraryPath())
                .role("PRIMARY")
                .evidenceRefs(List.of("code:range_excludes_n"))
                .reason("这个 id 不在标准库里。")
                .confidence(0.8)
                .build()));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).contains("diagnosisCandidate converted to OUT_OF_LIBRARY: MP_MODEL_MADE_UP");
        assertThat(output.getDiagnosisCandidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.getLibraryFit()).isEqualTo("OUT_OF_LIBRARY");
                    assertThat(candidate.getAnchorType()).isEqualTo("OUT_OF_LIBRARY");
                    assertThat(candidate.getAnchorId()).isNull();
                });
    }

    @Test
    void softClearsUnknownStandardLibraryIdsInDiagnosisReportV2AdviceItems() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");
        output.setBasicLayerAdvice(List.of(AdviceGenerationOutput.BasicLayerAdvice.builder()
                .mistakePointId("MP_MODEL_MADE_UP")
                .skillUnitId("SK_MODEL_MADE_UP")
                .title("边界判断需要复核")
                .whatHappened("当前循环范围和题目端点要求没有完全对齐。")
                .whyItMatters("端点漏处理会让可见样例的实际输出和预期不一致。")
                .studentAction("先手推一个最小样例，记录循环变量实际出现过哪些值。")
                .checkQuestion("最后一个应该处理的值有没有被覆盖？")
                .evidenceRefs(List.of("code:range_excludes_n"))
                .confidence(0.82)
                .build()));
        output.setImprovementLayerAdvice(List.of(AdviceGenerationOutput.ImprovementLayerAdvice.builder()
                .improvementPointId("IP_MODEL_MADE_UP")
                .skillUnitId("SK_MODEL_MADE_UP_IMPROVEMENT")
                .title("补充端点自测")
                .currentLimit("当前自测还没有充分覆盖端点形态。")
                .suggestion("修好基础问题后，再补充最小值和端点附近的自测。")
                .studentBenefit("能更早发现开闭区间和边界遗漏。")
                .evidenceRefs(List.of("judge:first_failed_case"))
                .confidence(0.73)
                .build()));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes())
                .contains("basicLayerAdvice.mistakePointId cleared: MP_MODEL_MADE_UP")
                .contains("basicLayerAdvice.skillUnitId cleared: SK_MODEL_MADE_UP")
                .contains("improvementLayerAdvice.improvementPointId cleared: IP_MODEL_MADE_UP")
                .contains("improvementLayerAdvice.skillUnitId cleared: SK_MODEL_MADE_UP_IMPROVEMENT");
        assertThat(output.getBasicLayerAdvice()).singleElement().satisfies(item -> {
            assertThat(item.getMistakePointId()).isNull();
            assertThat(item.getSkillUnitId()).isNull();
        });
        assertThat(output.getImprovementLayerAdvice()).singleElement().satisfies(item -> {
            assertThat(item.getImprovementPointId()).isNull();
            assertThat(item.getSkillUnitId()).isNull();
        });
    }

    @Test
    void softClearsOutOfLibraryDiagnosisCandidateAnchorId() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");
        output.setDiagnosisCandidates(List.of(AdviceGenerationOutput.DiagnosisCandidate.builder()
                .name("库外发现不应绑定旧 id")
                .layer("BASIC")
                .libraryFit("OUT_OF_LIBRARY")
                .anchorId("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                .anchorType("OUT_OF_LIBRARY")
                .libraryPath(List.of("基础层", "库外发现", "候选错因"))
                .role("PRIMARY")
                .evidenceRefs(List.of("code:range_excludes_n"))
                .reason("库外发现必须留空 id。")
                .confidence(0.8)
                .build()));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes())
                .contains("diagnosisCandidate anchorId cleared for OUT_OF_LIBRARY: MP_RANGE_RIGHT_ENDPOINT_MISSING");
        assertThat(output.getDiagnosisCandidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.getAnchorId()).isNull());
    }

    @Test
    void softRepairsDiagnosisCandidateWholeCodeEvidenceAlias() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");
        output.setDiagnosisCandidates(List.of(AdviceGenerationOutput.DiagnosisCandidate.builder()
                .name("候选证据引用整体代码")
                .layer("BASIC")
                .libraryFit("HIT")
                .anchorId("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                .anchorType("MISTAKE_POINT")
                .libraryPath(libraryPath())
                .role("PRIMARY")
                .evidenceRefs(List.of("keyCodeExcerpt"))
                .reason("模型把证据说明当成 evidenceRef。")
                .confidence(0.8)
                .build()));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).contains("evidenceRef alias keyCodeExcerpt -> code:range:1-20");
        assertThat(output.getDiagnosisCandidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.getEvidenceRefs())
                        .containsExactly("code:range:1-20"));
    }

    @Test
    void softRepairsExtraDetailEvidenceRefsForDiagnosisCandidates() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");
        output.setDiagnosisCandidates(List.of(AdviceGenerationOutput.DiagnosisCandidate.builder()
                .name("循环边界没有覆盖题目端点")
                .layer("BASIC")
                .libraryFit("HIT")
                .anchorId("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                .anchorType("MISTAKE_POINT")
                .libraryPath(libraryPath())
                .role("PRIMARY")
                .evidenceRefs(List.of("code:range_excludes_n:line3"))
                .reason("代码证据显示循环范围和题目端点不一致。")
                .confidence(0.91)
                .build()));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).contains("evidenceRef alias code:range_excludes_n:line3 -> code:range_excludes_n");
        assertThat(output.getDiagnosisCandidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.getEvidenceRefs())
                        .containsExactly("code:range_excludes_n"));
    }

    @Test
    void softDropsDiagnosisCandidateWithoutLibraryPath() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MISTAKE_POINT");
        output.setDiagnosisCandidates(List.of(AdviceGenerationOutput.DiagnosisCandidate.builder()
                .name("缺少标准库路径的候选")
                .layer("BASIC")
                .libraryFit("HIT")
                .anchorId("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                .anchorType("MISTAKE_POINT")
                .role("PRIMARY")
                .evidenceRefs(List.of("code:range_excludes_n"))
                .reason("候选必须说明自己挂在哪条教学路径上。")
                .confidence(0.8)
                .build()));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).contains("diagnosisCandidate dropped: missing libraryPath");
        assertThat(output.getDiagnosisCandidates()).isEmpty();
    }

    @Test
    void normalizesLibraryGrowthCandidateToTeacherReview() {
        AdviceGenerationOutput output = validDiagnosisReportV2("PARTIAL", "SK_RANGE_BOUNDARY", "SKILL_UNIT");
        output.setDiagnosisCandidates(List.of(AdviceGenerationOutput.DiagnosisCandidate.builder()
                .name("优惠券跨层转移未使用折扣边权")
                .layer("BASIC")
                .libraryFit("PARTIAL")
                .anchorId("SK_RANGE_BOUNDARY")
                .anchorType("SKILL_UNIT")
                .libraryPath(List.of("图论", "最短路", "分层图状态转移"))
                .role("PRIMARY")
                .evidenceRefs(List.of("code:range_excludes_n"))
                .reason("标准库有分层状态方向，但缺少优惠边权折半这个具体错因。")
                .confidence(0.87)
                .build()));
        output.setLibraryGrowth(AdviceGenerationOutput.LibraryGrowth.builder()
                .candidates(List.of(AdviceGenerationOutput.LibraryGrowthCandidate.builder()
                        .name("优惠券跨层转移未使用折扣边权")
                        .suggestedPath(List.of("图论", "最短路", "分层图状态转移"))
                        .similarExistingItems(List.of("SK_RANGE_BOUNDARY"))
                        .errorSymptom("程序输出偏大，优惠券没有真正降低边权。")
                        .typicalCodePattern("优惠转移分支仍累计原始 weight。")
                        .studentExplanation("先核对使用优惠券的那条转移，边权是否真的按题意变化。")
                        .reason("具体错因不在当前标准库里，应交给老师审核。")
                        .status("PROPOSED")
                        .confidence(0.84)
                        .build()))
                .build());

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes())
                .contains("libraryGrowth candidate status normalized: PROPOSED -> NEEDS_REVIEW");
        assertThat(output.getLibraryGrowth().getCandidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.getStatus()).isEqualTo("NEEDS_REVIEW");
                    assertThat(candidate.getEvidenceStatus()).isEqualTo("NO_DIRECT_CODE_EVIDENCE");
                    assertThat(candidate.getEvidenceRefs()).isNullOrEmpty();
                });
    }

    @Test
    void softRepairsEvidenceRefsWithExtraDetailForDiagnosisReportV2() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .diagnosisDecision(AdviceGenerationOutput.DiagnosisDecision.builder()
                        .libraryFit("HIT")
                        .anchors(List.of(AdviceGenerationOutput.DiagnosisAnchor.builder()
                                .id("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .type("MISTAKE_POINT")
                                .role("PRIMARY")
                                .confidence(0.9)
                                .evidenceRefs(List.of("judge:first_failed_case:1", "code:range_excludes_n:line3"))
                                .reason("模型补充了证据细节，但前缀仍对应合法证据。")
                                .build()))
                        .build())
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：循环取值范围和题目要求的端点没有对齐。")
                        .improvementLayerText("提高层：修好后补充最小值和端点值自测。")
                        .nextActionText("下一步：手推 n=1、n=2 时循环变量实际出现过哪些值。")
                        .build())
                .basicLayerAdvice(validOutput().getBasicLayerAdvice())
                .improvementLayerAdvice(validOutput().getImprovementLayerAdvice())
                .studentSummary("这次重点是循环边界。")
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes())
                .contains("evidenceRef alias judge:first_failed_case:1 -> judge:first_failed_case")
                .contains("evidenceRef alias code:range_excludes_n:line3 -> code:range_excludes_n");
        assertThat(output.getDiagnosisDecision().getAnchors()).singleElement()
                .satisfies(anchor -> assertThat(anchor.getEvidenceRefs())
                        .containsExactly("judge:first_failed_case", "code:range_excludes_n"));
    }

    @Test
    void rejectsVerdictEvidenceAliasForDiagnosisReportV2() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .diagnosisDecision(AdviceGenerationOutput.DiagnosisDecision.builder()
                        .libraryFit("HIT")
                        .anchors(List.of(AdviceGenerationOutput.DiagnosisAnchor.builder()
                                .id("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .type("MISTAKE_POINT")
                                .role("PRIMARY")
                                .confidence(0.9)
                                .evidenceRefs(List.of("verdict:wrong-answer"))
                                .reason("判题结果显示当前行为与期望不一致。")
                                .build()))
                        .build())
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：循环取值范围和题目要求的端点没有对齐。")
                        .improvementLayerText("提高层：修好后补充最小值和端点值自测。")
                        .nextActionText("下一步：手推一个最小样例，确认循环变量是否覆盖端点。")
                        .build())
                .studentSummary("这次重点是循环边界。")
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.INVALID_EVIDENCE_REF);
        assertThat(result.getMessage()).contains("verdict:wrong-answer");
    }

    @Test
    void softClearsUnknownStandardLibraryIdInLegacyAdvice() {
        AdviceGenerationOutput output = validOutput();
        output.getBasicLayerAdvice().get(0).setMistakePointId("MP_UNKNOWN");
        output.getBasicLayerAdvice().get(0).setSkillUnitId("SK_UNKNOWN");
        output.getImprovementLayerAdvice().get(0).setImprovementPointId("IP_UNKNOWN");
        output.getImprovementLayerAdvice().get(0).setSkillUnitId("SK_UNKNOWN_IMPROVEMENT");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes())
                .contains("basicLayerAdvice.mistakePointId cleared: MP_UNKNOWN")
                .contains("basicLayerAdvice.skillUnitId cleared: SK_UNKNOWN")
                .contains("improvementLayerAdvice.improvementPointId cleared: IP_UNKNOWN")
                .contains("improvementLayerAdvice.skillUnitId cleared: SK_UNKNOWN_IMPROVEMENT");
        assertThat(output.getBasicLayerAdvice().get(0).getMistakePointId()).isNull();
        assertThat(output.getBasicLayerAdvice().get(0).getSkillUnitId()).isNull();
        assertThat(output.getImprovementLayerAdvice().get(0).getImprovementPointId()).isNull();
        assertThat(output.getImprovementLayerAdvice().get(0).getSkillUnitId()).isNull();
    }

    @Test
    void acceptsLegacyBasicCauseIdWhenStandardLibraryNavigationFallsBack() {
        AdviceGenerationOutput output = validOutput();
        output.getBasicLayerAdvice().get(0).setMistakePointId("LOOP_BOUNDARY");
        output.getBasicLayerAdvice().get(0).setSkillUnitId(null);
        output.getImprovementLayerAdvice().get(0).setSkillUnitId(null);

        StandardLibraryPack legacyPack = StandardLibraryPack.builder()
                .schemaVersion(StandardLibraryPack.SCHEMA_VERSION)
                .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                .basicCauses(List.of(StandardLibraryPack.BasicCauseOption.builder()
                        .id("LOOP_BOUNDARY")
                        .name("循环边界")
                        .build()))
                .improvementPoints(List.of(StandardLibraryPack.ImprovementPointOption.builder()
                        .id("TESTING_HABIT")
                        .name("自测与反例构造")
                        .build()))
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                legacyPack
        );

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsInvalidEvidenceRef() {
        AdviceGenerationOutput output = validOutput();
        output.getBasicLayerAdvice().get(0).setEvidenceRefs(List.of("invented:evidence"));

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.INVALID_EVIDENCE_REF);
    }

    @Test
    void allowsUnsafeDirectAnswerTextAsSoftFix() {
        AdviceGenerationOutput output = validOutput();
        output.getBasicLayerAdvice().get(0).setStudentAction("直接改成 range(1, n + 1)。");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).anyMatch(value -> value.contains("answer leak allowed"));
    }

    @Test
    void allowsDiagnosisReportV2DirectLineEditTextAsSoftFix() {
        AdviceGenerationOutput output = unsafeReportOutput(
                "基础层：第 3 行把读取放进循环，就能覆盖所有查询。",
                "提高层：修好后补充边界样例。",
                "下一步：第 3 行设为0后再提交。",
                "这次重点是输入读取。");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).anyMatch(value -> value.contains("answer leak allowed"));
    }

    @Test
    void rejectsDiagnosisReportV2InventedNumericExample() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：你的程序在输入 [3,5,4] 时输出 9，但预期是 12。")
                        .improvementLayerText("提高层：描述相邻大数这类反例形态即可。")
                        .nextActionText("下一步：重述 dp[i] 的含义。")
                        .build())
                .studentSummary("这次重点是状态定义。")
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.INVALID_JSON);
        assertThat(result.getMessage()).contains("numeric example");
    }

    @Test
    void allowsDiagnosisReportV2ChineseDpFormulaLeakAsSoftFix() {
        AdviceGenerationOutput output = unsafeReportOutput(
                "基础层：选择当前位置时要加上前前位置的最大和。",
                "提高层：先描述状态含义。",
                "下一步：手推状态含义。",
                "这次重点是状态定义。");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).anyMatch(value -> value.contains("answer leak allowed"));
    }

    @Test
    void allowsDiagnosisReportV2DpFormulaFragmentsAsSoftFix() {
        AdviceGenerationOutput output = unsafeReportOutput(
                "基础层：你写了 skip_current = dp[i-1] 和 take_current = values[i]。",
                "提高层：之后可以做空间压缩。",
                "下一步：检查转移来源。",
                "这次重点是状态定义。");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).anyMatch(value -> value.contains("answer leak allowed"));
    }

    @Test
    void allowsDiagnosisReportV2NaturalLanguageDirectFixesFromLiveReviewAsSoftFix() {
        AdviceGenerationOutput output = unsafeReportOutput(
                "基础层：在每组处理开始之前手动把 best 和 cur 都设回 0。",
                "提高层：之后再整理多组数据习惯。",
                "下一步：拿题目样例再测一次。",
                "这次重点是状态重置。");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).anyMatch(value -> value.contains("answer leak allowed"));
    }

    @Test
    void allowsDiagnosisReportV2InitializationLocationFixesAsSoftFix() {
        AdviceGenerationOutput output = unsafeReportOutput(
                "基础层：检查状态变量是否残留。",
                "提高层：建议养成在循环内部初始化局部状态的习惯，或者在每轮循环开始时显式清空全局状态。",
                "下一步：手推第二组开始时变量的值。",
                "这次重点是变量生命周期。");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).anyMatch(value -> value.contains("answer leak allowed"));
    }

    @Test
    void allowsDiagnosisReportV2DpSelectOrSkipModelingHintAsSoftFix() {
        AdviceGenerationOutput output = unsafeReportOutput(
                "基础层：你只比较了不选当前和只选当前这两种情况。",
                "提高层：之后可以用两个变量滚动更新。",
                "下一步：手推可见失败样例。",
                "这次重点是状态定义。");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).anyMatch(value -> value.contains("answer leak allowed"));
    }

    @Test
    void allowsDiagnosisReportV2GreedyOptimalCombinationAndAlternativeTutorialAsSoftFix() {
        AdviceGenerationOutput output = unsafeReportOutput(
                "基础层：可见样例正确最少只需2枚，用两个3。",
                "提高层：可以定义状态表示凑到某金额所需的最少硬币数，然后从小到大枚举金额和每种面值进行转移。",
                "下一步：手推局部选择是否可靠。",
                "这次重点是验证贪心假设。");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).anyMatch(value -> value.contains("answer leak allowed"));
    }

    @Test
    void allowsDiagnosisReportV2HiddenCaseSpecificLogicLeakAsSoftFix() {
        AdviceGenerationOutput output = unsafeReportOutput(
                "基础层：你的代码目前只验证了字符串首尾字符是否相同。",
                "提高层：构造首尾相同但中间不同的测试数据。",
                "下一步：检查中间字符是否也被纳入比较范围。",
                "这次重点是隐藏测试泛化。");

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSoftFixes()).anyMatch(value -> value.contains("answer leak allowed"));
    }

    @Test
    void acceptsDiagnosisReportV2VisibleInputNumericExample() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：可见输入包含 [2,7,9,3,1]，实际输出与预期不一致。")
                        .improvementLayerText("提高层：描述相邻大数这类反例形态即可。")
                        .nextActionText("下一步：手推可见输入中的状态变化。")
                        .build())
                .basicLayerAdvice(validOutput().getBasicLayerAdvice())
                .improvementLayerAdvice(validOutput().getImprovementLayerAdvice())
                .studentSummary("这次重点是状态定义。")
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsEmptyBasicAdviceForNonAcceptedSubmission() {
        AdviceGenerationOutput output = validOutput();
        output.setBasicLayerAdvice(List.of());

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("basicLayerAdvice");
    }

    @Test
    void allowsEmptyBasicAdviceForAcceptedSubmission() {
        AdviceGenerationOutput output = validOutput();
        output.setBasicLayerAdvice(List.of());

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("ACCEPTED"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void softTrimsOverlongDiagnosisReportV2StudentText() {
        AdviceGenerationOutput output = AdviceGenerationOutput.builder()
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：".repeat(220))
                        .improvementLayerText("提高层：修好后做边界测试。")
                        .nextActionText("下一步：手推一个最小样例。")
                        .build())
                .basicLayerAdvice(validOutput().getBasicLayerAdvice())
                .improvementLayerAdvice(validOutput().getImprovementLayerAdvice())
                .studentSummary("报告过长。")
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                output,
                brief("WRONG_ANSWER"),
                pack()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(output.getStudentReport().getBasicLayerText()).hasSize(360);
        assertThat(output.getStudentReport().getBasicLayerText()).endsWith("…");
        assertThat(result.getSoftFixes()).contains("studentReport.basicLayerText trimmed to 360 chars");
    }

    private AdviceGenerationOutput validOutput() {
        return AdviceGenerationOutput.builder()
                .caseUnderstanding(AdviceGenerationOutput.CaseUnderstanding.builder()
                        .problemGoal("输出 1 到 n 的整数和。")
                        .codeIntent("学生想用循环累加。")
                        .behaviorGap("循环实际没有覆盖题目要求的末端。")
                        .primaryEvidenceRef("code:range_excludes_n")
                        .build())
                .basicLayerAdvice(List.of(AdviceGenerationOutput.BasicLayerAdvice.builder()
                        .mistakePointId("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                        .skillUnitId("SK_RANGE_BOUNDARY")
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
                        .skillUnitId("SK_RANGE_BOUNDARY")
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

    private AdviceGenerationOutput validDiagnosisReportV2(String libraryFit, String anchorId, String anchorType) {
        return AdviceGenerationOutput.builder()
                .diagnosisDecision(AdviceGenerationOutput.DiagnosisDecision.builder()
                        .libraryFit(libraryFit)
                        .anchors(List.of(AdviceGenerationOutput.DiagnosisAnchor.builder()
                                .id(anchorId)
                                .type(anchorType)
                                .role("PRIMARY")
                                .confidence(0.86)
                                .evidenceRefs(List.of("code:range_excludes_n"))
                                .reason("循环范围和题目端点要求不一致。")
                                .build()))
                        .build())
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText("基础层：循环范围和题目边界要求没有完全对齐。")
                        .improvementLayerText("提高层：修好后补充端点附近的自测。")
                        .nextActionText("下一步：手推一个最小样例，确认循环变量是否覆盖端点。")
                        .build())
                .basicLayerAdvice(validOutput().getBasicLayerAdvice())
                .improvementLayerAdvice(validOutput().getImprovementLayerAdvice())
                .studentSummary("这次重点是循环边界。")
                .build();
    }

    private AdviceGenerationOutput unsafeReportOutput(String basic, String improvement, String nextAction, String summary) {
        return AdviceGenerationOutput.builder()
                .studentReport(AdviceGenerationOutput.StudentReport.builder()
                        .hintLevel("L3")
                        .basicLayerText(basic)
                        .improvementLayerText(improvement)
                        .nextActionText(nextAction)
                        .build())
                .basicLayerAdvice(validOutput().getBasicLayerAdvice())
                .improvementLayerAdvice(validOutput().getImprovementLayerAdvice())
                .studentSummary(summary)
                .build();
    }

    private ModelDiagnosisBrief brief(String verdict) {
        return ModelDiagnosisBrief.builder()
                .schemaVersion(ModelDiagnosisBrief.SCHEMA_VERSION)
                .verdict(verdict)
                .sourceCodeLineCount(20)
                .visibleCaseFacts(List.of(ModelDiagnosisBrief.VisibleCaseFact.builder()
                        .testCaseNumber(1)
                        .hidden(false)
                        .inputPreview("5\n2 7 9 3 1")
                        .actualOutputPreview("9")
                        .expectedOutputPreview("12")
                        .build()))
                .evidenceRefs(List.of("code:range_excludes_n", "judge:first_failed_case"))
                .build();
    }

    private StandardLibraryPack pack() {
        return StandardLibraryPack.builder()
                .schemaVersion(StandardLibraryPack.SCHEMA_VERSION)
                .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                .mistakePoints(List.of(StandardLibraryPack.MistakePointOption.builder()
                        .id("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                        .name("循环右边界漏取")
                        .build()))
                .skillUnits(List.of(StandardLibraryPack.SkillUnitOption.builder()
                        .id("SK_RANGE_BOUNDARY")
                        .name("循环边界")
                        .build()))
                .improvementPoints(List.of(StandardLibraryPack.ImprovementPointOption.builder()
                        .id("TESTING_HABIT")
                        .name("自测与反例构造")
                        .build()))
                .build();
    }

    private List<String> libraryPath() {
        return List.of("基础层", "循环结构", "循环边界", "循环右边界漏取");
    }

    private StandardLibraryPack richPack() {
        return StandardLibraryPack.builder()
                .schemaVersion(StandardLibraryPack.SCHEMA_VERSION)
                .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                .mistakePoints(List.of(
                        StandardLibraryPack.MistakePointOption.builder()
                                .id("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .name("循环右边界漏取")
                                .build(),
                        StandardLibraryPack.MistakePointOption.builder()
                                .id("MP_RECURSION_EXIT_MISSING")
                                .name("递归出口缺失")
                                .description("递归没有正确处理终止条件或最小子问题。")
                                .build()
                ))
                .skillUnits(List.of(
                        StandardLibraryPack.SkillUnitOption.builder()
                                .id("SK_RANGE_BOUNDARY")
                                .name("循环边界")
                                .build(),
                        StandardLibraryPack.SkillUnitOption.builder()
                                .id("SK_RECURSION_BASE_CASE")
                                .name("递归终止条件")
                                .build()
                ))
                .improvementPoints(List.of(StandardLibraryPack.ImprovementPointOption.builder()
                        .id("TESTING_HABIT")
                        .name("自测与反例构造")
                        .build()))
                .build();
    }
}
