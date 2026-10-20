package com.onlinejudge.learning.standardlibrary;

import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeed;
import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeedCatalog;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibrarySeed;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibrarySeedCatalog;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AiStandardLibraryV10AbsorptionSeedsTest {

    private static final List<String> ABSORBED_KNOWLEDGE_CODES = List.of(
            "BASIC.TYPE.VARIABLE.声明与初始化",
            "BASIC.TYPE.FLOAT.整数除法误用",
            "BASIC.EXPR.LOGIC.条件组合",
            "BASIC.FUNCTION.RETURN.输出与返回分离",
            "MATH.NUMBER.MOD.负数取模修正",
            "MATH.COUNT.PERM.顺序是否重要",
            "ENG.DEBUG.SAMPLE.最小反例",
            "ENG.COMPLEXITY.SPACE.数组规模"
    );

    @Test
    void v10AbsorptionAddsStructuredHandwrittenItemsFromGeneratedFallbackTopics() {
        List<AiStandardLibrarySeed> v10Seeds = v10Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> v10SkillCodes = v10Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> v10MistakeCodes = v10Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v10Seeds).hasSizeGreaterThanOrEqualTo(40);
        assertThat(v10Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSizeGreaterThanOrEqualTo(8);
        assertThat(v10Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSizeGreaterThanOrEqualTo(24);
        assertThat(v10Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT))
                .hasSizeGreaterThanOrEqualTo(8);

        assertThat(v10Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT
                            ? "IP_V10_"
                            : seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V10_" : "MP_V10_");
                    assertThat(seed.name()).doesNotContain("理解或应用偏差");
                    String combinedText = String.join("\n",
                            seed.name(),
                            seed.description(),
                            seed.studentExplanation(),
                            seed.commonMisconception(),
                            seed.studentBenefit());
                    assertThat(combinedText)
                            .doesNotContain("适用条件混用")
                            .doesNotContain("没有把知识点定义、适用条件或边界要求准确落实");
                    assertThat(seed.knowledgeNodeCodes()).isNotEmpty();
                    assertThat(seed.knowledgeNodeCodes()).allMatch(knowledgeCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v10SkillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                    if (seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v10SkillCodes);
                        assertThat(seed.relatedItems()).isNotEmpty();
                        assertThat(seed.relatedItems()).allMatch(v10MistakeCodes::contains);
                        assertThat(seed.whenToUse()).isNotBlank();
                        assertThat(seed.studentBenefit()).isNotBlank();
                        assertThat(seed.teacherExplanation()).isNotBlank();
                    }
                });
    }

    @Test
    void v10AbsorptionCoversSelectedGeneratedFallbackKnowledgePoints() {
        List<AiStandardLibrarySeed> v10Seeds = v10Seeds();
        Map<String, AiStandardLibrarySeed> seedsByCode = v10Seeds.stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(ABSORBED_KNOWLEDGE_CODES)
                .allSatisfy(knowledgeCode -> assertThat(v10Seeds)
                        .as("V10 should absorb fallback knowledge " + knowledgeCode)
                        .anySatisfy(seed -> assertThat(seed.knowledgeNodeCodes()).contains(knowledgeCode)));

        assertThat(seedsByCode.get("MP_V10_VARIABLE_READ_BEFORE_VALID_ASSIGNMENT").description())
                .contains("某条路径没有给变量赋值").contains("旧值");
        assertThat(seedsByCode.get("MP_V10_INTEGER_DIVISION_BEFORE_FLOAT_CAST").description())
                .contains("两个整数先相除").contains("截断");
        assertThat(seedsByCode.get("MP_V10_AND_OR_CONDITION_GROUPING_WRONG").description())
                .contains("A 且").contains("缺少括号");
        assertThat(seedsByCode.get("MP_V10_FUNCTION_MUTATES_ARGUMENT_UNEXPECTEDLY").description())
                .contains("数组").contains("调用方");
        assertThat(seedsByCode.get("MP_V10_NEGATIVE_MOD_USED_AS_ARRAY_INDEX").description())
                .contains("负数取模").contains("数组下标");
        assertThat(seedsByCode.get("MP_V10_COUNT_ORDER_IMPORTANCE_MISMODELED").commonMisconception())
                .contains("交换两个选择").contains("同一个方案");
        assertThat(seedsByCode.get("MP_V10_DEBUG_COUNTEREXAMPLE_NOT_MINIMIZED").description())
                .contains("最少元素").contains("第一处偏差");
        assertThat(seedsByCode.get("MP_V10_SPACE_TABLE_SIZE_NOT_ESTIMATED").description())
                .contains("单元素字节").contains("内存");
        assertThat(seedsByCode.get("IP_V10_COMPLEXITY_BUDGET_SHEET").studentBenefit())
                .contains("操作次数").contains("总内存").contains("滚动优化");
    }

    private List<AiStandardLibrarySeed> v10Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V10_")
                        || seed.code().startsWith("MP_V10_")
                        || seed.code().startsWith("IP_V10_"))
                .toList();
    }
}
