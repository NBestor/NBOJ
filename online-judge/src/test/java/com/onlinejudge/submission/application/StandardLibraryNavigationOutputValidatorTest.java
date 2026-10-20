package com.onlinejudge.submission.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardLibraryNavigationOutputValidatorTest {

    private final StandardLibraryNavigationOutputValidator validator = new StandardLibraryNavigationOutputValidator();

    @Test
    void acceptsDoneNavigationWithKnownAnchorsAndEvidence() {
        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                StandardLibraryNavigationOutput.builder()
                        .status("DONE")
                        .selectedPaths(List.of(StandardLibraryNavigationOutput.SelectedPath.builder()
                                .knowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                                .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                                .mistakePointCode("MP_QUEUE_REAR_WITHOUT_MOD")
                                .libraryFit("HIT")
                                .reason("证据显示队尾推进后没有回绕。")
                                .evidenceRefs(List.of("code:line:18"))
                                .confidence(0.86)
                                .build()))
                        .unresolvedGaps(List.of())
                        .build(),
                brief(),
                pack());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getStage()).isEqualTo("STANDARD_LIBRARY_NAVIGATION");
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.NONE);
    }

    @Test
    void acceptsNavigationWithoutEvidenceRefs() {
        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                StandardLibraryNavigationOutput.builder()
                        .status("DONE")
                        .selectedPaths(List.of(StandardLibraryNavigationOutput.SelectedPath.builder()
                                .knowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                                .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                                .mistakePointCode("MP_QUEUE_REAR_WITHOUT_MOD")
                                .libraryFit("HIT")
                                .reason("逐层挂接只负责标准库定位，证据由自由诊断和建议阶段校验。")
                                .confidence(0.86)
                                .build()))
                        .build(),
                brief(),
                pack());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.NONE);
    }

    @Test
    void acceptsCodeRangeAndNormalizesEvidenceAliases() {
        StandardLibraryNavigationOutput output = StandardLibraryNavigationOutput.builder()
                .status("DONE")
                .selectedPaths(List.of(StandardLibraryNavigationOutput.SelectedPath.builder()
                        .knowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                        .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                        .mistakePointCode("MP_QUEUE_REAR_WITHOUT_MOD")
                        .libraryFit("HIT")
                        .reason("证据显示队尾推进后没有回绕。")
                        .evidenceRefs(List.of("code:line:3-5", "judge:first_failed_case:case1"))
                        .confidence(0.86)
                        .build()))
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(output, brief(), pack());

        assertThat(result.isValid()).isTrue();
        assertThat(output.getSelectedPaths()).singleElement()
                .satisfies(path -> assertThat(path.getEvidenceRefs())
                        .containsExactly("code:range:3-5", "judge:first_failed_case"));
        assertThat(result.getSoftFixes())
                .contains("evidenceRef alias code:line:3-5 -> code:range:3-5")
                .contains("evidenceRef alias judge:first_failed_case:case1 -> judge:first_failed_case");
    }

    @Test
    void acceptsBareCodeLineAndRangeEvidenceAliases() {
        StandardLibraryNavigationOutput output = StandardLibraryNavigationOutput.builder()
                .status("DONE")
                .selectedPaths(List.of(StandardLibraryNavigationOutput.SelectedPath.builder()
                        .knowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                        .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                        .mistakePointCode("MP_QUEUE_REAR_WITHOUT_MOD")
                        .libraryFit("HIT")
                        .reason("模型用 code:N 和 code:A-B 简写引用源码证据。")
                        .evidenceRefs(List.of("code:18", "code:3-5"))
                        .confidence(0.86)
                        .build()))
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(output, brief(), pack());

        assertThat(result.isValid()).isTrue();
        assertThat(output.getSelectedPaths()).singleElement()
                .satisfies(path -> assertThat(path.getEvidenceRefs())
                        .containsExactly("code:line:18", "code:range:3-5"));
        assertThat(result.getSoftFixes())
                .contains("evidenceRef alias code:18 -> code:line:18")
                .contains("evidenceRef alias code:3-5 -> code:range:3-5");
    }

    @Test
    void acceptsKeyCodeExcerptEvidenceAliases() {
        StandardLibraryNavigationOutput output = StandardLibraryNavigationOutput.builder()
                .status("DONE")
                .selectedPaths(List.of(StandardLibraryNavigationOutput.SelectedPath.builder()
                        .knowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                        .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                        .mistakePointCode("MP_QUEUE_REAR_WITHOUT_MOD")
                        .libraryFit("HIT")
                        .reason("模型用 keyCodeExcerpt:A-B 引用带行号代码片段。")
                        .evidenceRefs(List.of("keyCodeExcerpt:147-149", "keyCodeExcerpt:148"))
                        .confidence(0.86)
                        .build()))
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(output, longCodeBrief(), pack());

        assertThat(result.isValid()).isTrue();
        assertThat(output.getSelectedPaths()).singleElement()
                .satisfies(path -> assertThat(path.getEvidenceRefs())
                        .containsExactly("code:range:147-149", "code:line:148"));
    }

    @Test
    void acceptsModelStyleCodeLineAndJudgeCaseAliases() {
        StandardLibraryNavigationOutput output = StandardLibraryNavigationOutput.builder()
                .status("DONE")
                .selectedPaths(List.of(StandardLibraryNavigationOutput.SelectedPath.builder()
                        .knowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                        .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                        .mistakePointCode("MP_QUEUE_REAR_WITHOUT_MOD")
                        .libraryFit("HIT")
                        .reason("模型用 code:line_18_comment 和 judge:case_1_output 引用证据。")
                        .evidenceRefs(List.of("code:line_18_comment", "judge:case_1_output"))
                        .confidence(0.86)
                        .build()))
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(output, brief(), pack());

        assertThat(result.isValid()).isTrue();
        assertThat(output.getSelectedPaths()).singleElement()
                .satisfies(path -> assertThat(path.getEvidenceRefs())
                        .containsExactly("code:line:18", "judge:first_failed_case"));
        assertThat(result.getSoftFixes())
                .contains("evidenceRef alias code:line_18_comment -> code:line:18")
                .contains("evidenceRef alias judge:case_1_output -> judge:first_failed_case");
    }

    @Test
    void acceptsSourceLineEvidenceAlias() {
        StandardLibraryNavigationOutput output = StandardLibraryNavigationOutput.builder()
                .status("NO_MATCH")
                .unresolvedGaps(List.of(StandardLibraryNavigationOutput.UnresolvedGap.builder()
                        .name("长代码中模型引用源码行号")
                        .reason("模型使用 source:line 形式引用证据。")
                        .evidenceRefs(List.of("source:line 18", "source:lines:3-5"))
                        .confidence(0.64)
                        .build()))
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(output, brief(), pack());

        assertThat(result.isValid()).isTrue();
        assertThat(output.getUnresolvedGaps()).singleElement()
                .satisfies(gap -> assertThat(gap.getEvidenceRefs())
                        .containsExactly("code:line:18", "code:range:3-5"));
        assertThat(result.getSoftFixes())
                .contains("evidenceRef alias source:line 18 -> code:line:18")
                .contains("evidenceRef alias source:lines:3-5 -> code:range:3-5");
    }

    @Test
    void acceptsLiveModelShorthandCodeAndSourceAliases() {
        StandardLibraryNavigationOutput output = StandardLibraryNavigationOutput.builder()
                .status("NO_MATCH")
                .unresolvedGaps(List.of(StandardLibraryNavigationOutput.UnresolvedGap.builder()
                        .name("真实模型行号缩写")
                        .reason("模型使用 code:L 和 source:N 形式引用长代码。")
                        .evidenceRefs(List.of("code:L118", "code:L113-L120", "source:49", "source:98-105"))
                        .confidence(0.64)
                        .build()))
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(output, longCodeBrief(), pack());

        assertThat(result.isValid()).isTrue();
        assertThat(output.getUnresolvedGaps()).singleElement()
                .satisfies(gap -> assertThat(gap.getEvidenceRefs())
                        .containsExactly("code:line:118", "code:range:113-120",
                                "code:line:49", "code:range:98-105"));
        assertThat(result.getSoftFixes())
                .contains("evidenceRef alias code:L118 -> code:line:118")
                .contains("evidenceRef alias code:L113-L120 -> code:range:113-120")
                .contains("evidenceRef alias source:49 -> code:line:49")
                .contains("evidenceRef alias source:98-105 -> code:range:98-105");
    }

    @Test
    void acceptsLiveModelWholeCodeAndVisibleCaseAliases() {
        StandardLibraryNavigationOutput output = StandardLibraryNavigationOutput.builder()
                .status("NO_MATCH")
                .unresolvedGaps(List.of(StandardLibraryNavigationOutput.UnresolvedGap.builder()
                        .name("真实模型整体证据别名")
                        .reason("模型使用 keyCodeExcerpt 和 visibleCaseFacts 引用整体代码与可见样例。")
                        .evidenceRefs(List.of("keyCodeExcerpt", "visibleCaseFacts"))
                        .confidence(0.64)
                        .build()))
                .build();

        ExternalModelStagePayloads.StageValidationResult result = validator.validate(output, longCodeBrief(), pack());

        assertThat(result.isValid()).isTrue();
        assertThat(output.getUnresolvedGaps()).singleElement()
                .satisfies(gap -> assertThat(gap.getEvidenceRefs())
                        .containsExactly("code:range:1-226", "judge:first_failed_case"));
        assertThat(result.getSoftFixes())
                .contains("evidenceRef alias keyCodeExcerpt -> code:range:1-226")
                .contains("evidenceRef alias visibleCaseFacts -> judge:first_failed_case");
    }

    @Test
    void ignoresStaleSelectedBranchesAfterDoneNavigation() {
        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                StandardLibraryNavigationOutput.builder()
                        .status("DONE")
                        .selectedBranches(List.of(StandardLibraryNavigationOutput.SelectedBranch.builder()
                                .knowledgeNodeCode("STALE.NOT_VISIBLE")
                                .reason("上一轮残留分支，不参与最终锚定。")
                                .evidenceRefs(List.of("code:line:18"))
                                .confidence(0.7)
                                .build()))
                        .selectedPaths(List.of(StandardLibraryNavigationOutput.SelectedPath.builder()
                                .knowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                                .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                                .mistakePointCode("MP_QUEUE_REAR_WITHOUT_MOD")
                                .libraryFit("HIT")
                                .reason("证据显示队尾推进后没有回绕。")
                                .evidenceRefs(List.of("code:line:18"))
                                .confidence(0.86)
                                .build()))
                        .unresolvedGaps(List.of())
                        .build(),
                brief(),
                pack());

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsUnknownStandardLibraryAnchor() {
        ExternalModelStagePayloads.StageValidationResult result = validator.validate(
                StandardLibraryNavigationOutput.builder()
                        .status("DONE")
                        .selectedPaths(List.of(StandardLibraryNavigationOutput.SelectedPath.builder()
                                .knowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                                .skillUnitCode("SK_NOT_EXISTS")
                                .libraryFit("HIT")
                                .reason("未知能力点。")
                                .evidenceRefs(List.of("code:line:18"))
                                .confidence(0.7)
                                .build()))
                        .build(),
                brief(),
                pack());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ModelStageFailureReason.INVALID_TAG);
    }

    @Test
    void rejectsContinueWithoutBranchAndInvalidEvidence() {
        ExternalModelStagePayloads.StageValidationResult noBranch = validator.validate(
                StandardLibraryNavigationOutput.builder()
                        .status("CONTINUE")
                        .selectedBranches(List.of())
                        .build(),
                brief(),
                pack());

        assertThat(noBranch.isValid()).isFalse();
        assertThat(noBranch.getMessage()).contains("selectedBranches");

        ExternalModelStagePayloads.StageValidationResult invalidEvidence = validator.validate(
                StandardLibraryNavigationOutput.builder()
                        .status("NO_MATCH")
                        .unresolvedGaps(List.of(StandardLibraryNavigationOutput.UnresolvedGap.builder()
                                .name("库外错因")
                                .reason("证据引用不存在。")
                                .evidenceRefs(List.of("code:line:999"))
                                .confidence(0.5)
                                .build()))
                        .build(),
                brief(),
                pack());

        assertThat(invalidEvidence.isValid()).isFalse();
        assertThat(invalidEvidence.getFailureReason()).isEqualTo(ModelStageFailureReason.INVALID_EVIDENCE_REF);
    }

    private ModelDiagnosisBrief brief() {
        return ModelDiagnosisBrief.builder()
                .verdict("RUNTIME_ERROR")
                .sourceCodeLineCount(40)
                .evidenceRefs(List.of("code:line:18", "judge:case:2", "judge:first_failed_case"))
                .build();
    }

    private ModelDiagnosisBrief longCodeBrief() {
        return ModelDiagnosisBrief.builder()
                .verdict("WRONG_ANSWER")
                .sourceCodeLineCount(226)
                .evidenceRefs(List.of("judge:first_failed_case"))
                .build();
    }

    private StandardLibraryPack pack() {
        return StandardLibraryPack.builder()
                .knowledgeAnchors(List.of(StandardLibraryPack.KnowledgeAnchorOption.builder()
                        .id("DS.QUEUE.CIRCULAR.index_wrap")
                        .name("循环队列下标回绕")
                        .path("队列与循环队列 / 循环队列下标维护")
                        .build()))
                .skillUnits(List.of(StandardLibraryPack.SkillUnitOption.builder()
                        .id("SK_QUEUE_CIRCULAR_INDEX")
                        .name("循环队列下标维护")
                        .primaryKnowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                        .build()))
                .mistakePoints(List.of(StandardLibraryPack.MistakePointOption.builder()
                        .id("MP_QUEUE_REAR_WITHOUT_MOD")
                        .name("队尾更新未取模")
                        .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                        .primaryKnowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                        .build()))
                .improvementPoints(List.of(StandardLibraryPack.ImprovementPointOption.builder()
                        .id("IP_QUEUE_BOUNDARY_TRACE")
                        .name("用小容量队列追踪边界")
                        .abilityPoint("SK_QUEUE_CIRCULAR_INDEX")
                        .build()))
                .build();
    }
}
