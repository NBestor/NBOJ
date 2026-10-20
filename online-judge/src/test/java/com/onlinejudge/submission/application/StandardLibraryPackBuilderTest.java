package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardLibraryPackBuilderTest {

    private final StandardLibraryPackBuilder builder =
            new StandardLibraryPackBuilder(new DiagnosisTaxonomy());

    @Test
    void buildsBasicAndImprovementKnowledgeLayersWithoutRuntimeProtocols() throws Exception {
        ModelDiagnosisBrief brief = ModelDiagnosisBrief.builder()
                .allowedIssueTags(List.of("IO_FORMAT", "TIME_COMPLEXITY", "ALGORITHM_STRATEGY"))
                .allowedFineGrainedTags(List.of("INPUT_PARSING", "BRUTE_FORCE_LIMIT", "GREEDY_ASSUMPTION"))
                .build();

        StandardLibraryPack pack = builder.build(brief);

        assertThat(pack.getBasicCauses())
                .extracting(StandardLibraryPack.BasicCauseOption::getId)
                .contains(
                        "IO_FORMAT",
                        "INPUT_PARSING",
                        "TIME_COMPLEXITY",
                        "BRUTE_FORCE_LIMIT",
                        "ALGORITHM_STRATEGY",
                        "GREEDY_ASSUMPTION",
                        "NEEDS_MORE_EVIDENCE"
                );
        assertThat(pack.getBasicCauses())
                .filteredOn(cause -> "IO_FORMAT".equals(cause.getId()))
                .singleElement()
                .satisfies(cause -> {
                    assertThat(cause.getCategory()).isEqualTo("输入输出");
                    assertThat(cause.getEvidenceSignals()).contains("wrong output shape", "input format");
                    assertThat(cause.getHintL1()).contains("输入输出格式");
                    assertThat(cause.getHintL2()).contains("读几组");
                    assertThat(cause.getHintL3()).contains("最小样例");
                });
        assertThat(pack.getImprovementPoints())
                .extracting(StandardLibraryPack.ImprovementPointOption::getId)
                .contains("COMPLEXITY", "ALGORITHM_MODELING", "GREEDY_PROOF", "TESTING_HABIT");
        assertThat(pack.getImprovementPoints())
                .filteredOn(point -> "COMPLEXITY".equals(point.getId()))
                .singleElement()
                .satisfies(point -> {
                    assertThat(point.getCategory()).isEqualTo("复杂度");
                    assertThat(point.getHintL1()).contains("核心循环");
                    assertThat(point.getRelatedBasicCauses()).contains("TIME_COMPLEXITY", "BRUTE_FORCE_LIMIT");
                });
        assertThat(pack.getIssueTags())
                .extracting(StandardLibraryPack.TagOption::getId)
                .contains("IO_FORMAT", "TIME_COMPLEXITY", "ALGORITHM_STRATEGY", "NEEDS_MORE_EVIDENCE");
        assertThat(pack.getFineGrainedTags())
                .extracting(StandardLibraryPack.TagOption::getId)
                .contains("INPUT_PARSING", "BRUTE_FORCE_LIMIT", "GREEDY_ASSUMPTION");
        assertThat(pack.getTeachingActions())
                .extracting(StandardLibraryPack.TeachingActionOption::getId)
                .contains("COMPARE_INPUT_SPEC", "COUNT_COMPLEXITY", "CHECK_INVARIANT", "COLLECT_EVIDENCE");

        String json = new ObjectMapper().writeValueAsString(pack);
        assertThat(json)
                .contains("\"basicCauses\"")
                .contains("\"improvementPoints\"")
                .doesNotContain("decisionProtocol")
                .doesNotContain("educationAgentProtocol")
                .doesNotContain("studentFeedbackRules")
                .doesNotContain("judgmentCalibrationExamples")
                .doesNotContain("safetyRules")
                .doesNotContain("uncertaintyOptions");
    }

    @Test
    void briefAllowedTagsDriveStandardLibraryEntries() {
        ModelDiagnosisBrief brief = ModelDiagnosisBrief.builder()
                .allowedIssueTags(List.of("BOUNDARY_CONDITION"))
                .build();

        StandardLibraryPack pack = builder.build(brief);

        assertThat(pack.getBasicCauses())
                .extracting(StandardLibraryPack.BasicCauseOption::getId)
                .contains("BOUNDARY_CONDITION", "NEEDS_MORE_EVIDENCE")
                .doesNotContain("OUTPUT_FORMAT_DETAIL");
        assertThat(pack.getTeachingActions())
                .extracting(StandardLibraryPack.TeachingActionOption::getId)
                .contains("ASK_MIN_CASE", "COLLECT_EVIDENCE")
                .doesNotContain("COMPARE_OUTPUT");
        assertThat(pack.getImprovementPoints())
                .extracting(StandardLibraryPack.ImprovementPointOption::getId)
                .contains("TESTING_HABIT", "BOUNDARY_AWARENESS")
                .doesNotContain("CODE_ORGANIZATION");
    }

    @Test
    void defaultSliceStillCarriesEvidenceCollectionAndReviewKnowledge() {
        StandardLibraryPack pack = builder.build(ModelDiagnosisBrief.builder().build());

        assertThat(pack.getBasicCauses())
                .extracting(StandardLibraryPack.BasicCauseOption::getId)
                .containsExactly("NEEDS_MORE_EVIDENCE");
        assertThat(pack.getImprovementPoints())
                .extracting(StandardLibraryPack.ImprovementPointOption::getId)
                .contains("TESTING_HABIT", "TRANSFER_REVIEW");
        assertThat(pack.getImprovementTags())
                .extracting(StandardLibraryPack.ImprovementTagOption::getId)
                .contains("TESTING_HABIT", "TRANSFER_REVIEW");
    }

    @Test
    void runtimePlanUsesAiNavigationPromptStackWithoutPrebuiltLibraryPack() {
        ExternalModelAgentRuntime runtime = new ExternalModelAgentRuntime(
                new ModelDiagnosisBriefBuilder(),
                builder,
                new PromptTemplateRegistry(),
                new ModelOutputValidator()
        );

        ExternalModelAgentRuntime.RuntimePlan standardPlan = runtime.prepare(
                DiagnosisEvidencePackage.builder().build(),
                null,
                ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD
        );
        ExternalModelAgentRuntime.RuntimePlan ignoredProfilePlan = runtime.prepare(
                DiagnosisEvidencePackage.builder().build(),
                null,
                "low-latency"
        );

        assertThat(standardPlan.getStandardLibraryPack()).isNull();
        assertThat(standardPlan.getFreeDiagnosisPrompt().getVersion()).isEqualTo(PromptTemplateRegistry.FREE_DIAGNOSIS_V2);
        assertThat(standardPlan.getStandardLibraryNavigationPrompt().getVersion())
                .isEqualTo(PromptTemplateRegistry.STANDARD_LIBRARY_NAVIGATION_V1);
        assertThat(standardPlan.getAdvicePrompt().getVersion()).isEqualTo(PromptTemplateRegistry.DIAGNOSIS_REPORT_V4);
        assertThat(ignoredProfilePlan.getRuntimeProfile()).isEqualTo(ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD);
        assertThat(ignoredProfilePlan.isRequestCompact()).isFalse();
        assertThat(ignoredProfilePlan.getStandardLibraryPack()).isNull();
        assertThat(ignoredProfilePlan.getAdvicePrompt().getVersion()).isEqualTo(PromptTemplateRegistry.DIAGNOSIS_REPORT_V4);
        assertThat(ignoredProfilePlan.getAdvicePrompt().getSystemPrompt())
                .contains("diagnosis-report-v4")
                .contains("自由诊断")
                .contains("标准库挂接结果")
                .contains("navigationResult")
                .contains("standardLibrary")
                .contains("studentReport")
                .contains("libraryGrowth.candidates")
                .contains("每个学生可见判断都要有证据引用");
    }

    private StandardLibraryPack.MistakePointOption mistake(String id) {
        return StandardLibraryPack.MistakePointOption.builder()
                .id(id)
                .category("循环边界")
                .name(id)
                .description("边界易错点描述".repeat(10))
                .skillUnitCode("SK_RANGE_BOUNDARY")
                .mistakeType("OFF_BY_ONE")
                .commonMisconception("误解循环边界。".repeat(10))
                .primaryKnowledgeNodeCode("BASIC.LOOP.BOUNDARY")
                .knowledgeNodeCodes(List.of("BASIC.LOOP.BOUNDARY"))
                .relatedKnowledgeNodeCodes(List.of("BASIC.LOOP.FOR"))
                .applicableLanguages(List.of("PYTHON", "CPP17", "JAVA", "GO"))
                .build();
    }

    private StandardLibraryPack.ImprovementPointOption improvement(String id) {
        return StandardLibraryPack.ImprovementPointOption.builder()
                .id(id)
                .category("边界验证")
                .name(id)
                .description("提升点描述".repeat(10))
                .whenToUse("修复基础问题后，用多类边界样例验证模板。".repeat(8))
                .studentBenefit("提升迁移能力。".repeat(8))
                .abilityPoint("SK_RANGE_BOUNDARY")
                .relatedBasicCauses(List.of("MP_BOUNDARY_1"))
                .build();
    }
}
