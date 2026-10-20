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

class AiStandardLibraryV13QualityExpansionSeedsTest {

    private static final List<String> EXPECTED_SKILL_CODES = List.of(
            "SK_V13_ARRAY_UPDATE_SNAPSHOT_CONTRACT",
            "SK_V13_STRING_MATCH_BOUNDARY_CONTRACT",
            "SK_V13_MATRIX_PREFIX_INCLUSION_CONTRACT",
            "SK_V13_DP_STATE_TRANSITION_ORDER_CONTRACT",
            "SK_V13_GREEDY_PROOF_COUNTEREXAMPLE_CONTRACT",
            "SK_V13_BITMASK_STATE_TRANSITION_CONTRACT",
            "SK_V13_SHORTEST_DISTANCE_RELAXATION_CONTRACT",
            "SK_V13_ENUMERATION_PRUNING_CONTRACT",
            "SK_V13_FUNCTION_PARAMETER_SIDE_EFFECT_CONTRACT",
            "SK_V13_LINKED_SIMULATION_BOUNDARY_CONTRACT",
            "SK_V13_GEOMETRY_COORD_DISTANCE_CONTRACT",
            "SK_V13_PRIME_FACTOR_RANGE_CONTRACT"
    );

    @Test
    void v13QualityExpansionAddsStructuredHandwrittenItemsWithoutTemplatePadding() {
        List<AiStandardLibrarySeed> v13Seeds = v13Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> v13SkillCodes = v13Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> v13MistakeCodes = v13Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v13Seeds).hasSizeGreaterThanOrEqualTo(60);
        assertThat(v13SkillCodes).containsAll(EXPECTED_SKILL_CODES);
        assertThat(v13Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSizeGreaterThanOrEqualTo(12);
        assertThat(v13Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSizeGreaterThanOrEqualTo(36);
        assertThat(v13Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT))
                .hasSizeGreaterThanOrEqualTo(12);

        assertThat(v13Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT
                            ? "IP_V13_"
                            : seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V13_" : "MP_V13_");
                    String text = String.join("\n",
                            seed.name(),
                            seed.description(),
                            seed.studentExplanation(),
                            seed.commonMisconception(),
                            seed.studentBenefit(),
                            seed.teacherExplanation());
                    assertThat(text)
                            .doesNotContain("理解或应用偏差")
                            .doesNotContain("适用条件混用")
                            .doesNotContain("代码落点不清")
                            .doesNotContain("没有把知识点定义、适用条件或边界要求准确落实");
                    assertThat(seed.knowledgeNodeCodes()).isNotEmpty();
                    assertThat(seed.knowledgeNodeCodes()).allMatch(knowledgeCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v13SkillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                    if (seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v13SkillCodes);
                        assertThat(seed.relatedItems()).isNotEmpty();
                        assertThat(seed.relatedItems()).allMatch(v13MistakeCodes::contains);
                        assertThat(seed.whenToUse()).isNotBlank();
                        assertThat(seed.studentBenefit()).isNotBlank();
                        assertThat(seed.teacherExplanation()).isNotBlank();
                    }
                });
    }

    @Test
    void v13RepresentativeItemsContainConcreteSymptomsAndPracticeActions() {
        Map<String, AiStandardLibrarySeed> seedsByCode = v13Seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode.get("MP_V13_ARRAY_UPDATE_READS_ALREADY_MODIFIED_VALUE").description())
                .contains("同一轮更新").contains("新值");
        assertThat(seedsByCode.get("MP_V13_STRING_MATCH_SKIPS_OVERLAP").description())
                .contains("重叠").contains("漏掉");
        assertThat(seedsByCode.get("MP_V13_MATRIX_PREFIX_MISSES_OVERLAP_SUBTRACTION").description())
                .contains("重复计算").contains("减回");
        assertThat(seedsByCode.get("MP_V13_DP_LOOP_ORDER_READS_CURRENT_ROUND_VALUE").description())
                .contains("本轮刚更新").contains("重复使用");
        assertThat(seedsByCode.get("MP_V13_GREEDY_LOCAL_CHOICE_WITHOUT_EXCHANGE_ARGUMENT").description())
                .contains("小反例").contains("局部");
        assertThat(seedsByCode.get("MP_V13_BITMASK_SHIFT_USES_ONE_BASED_INDEX").description())
                .contains("1 << id").contains("越界");
        assertThat(seedsByCode.get("MP_V13_DIJKSTRA_PROCESSES_STALE_HEAP_ENTRY").description())
                .contains("较旧的较大距离").contains("无效松弛");
        assertThat(seedsByCode.get("MP_V13_ENUM_COMPLEXITY_IGNORES_NESTED_BRANCH_FACTOR").description())
                .contains("分支数").contains("超过限制");
        assertThat(seedsByCode.get("MP_V13_FUNCTION_MUTABLE_PARAM_REUSED_ACROSS_BRANCHES").description())
                .contains("被污染的状态");
        assertThat(seedsByCode.get("MP_V13_LINK_DELETE_UPDATES_ONLY_ONE_DIRECTION").description())
                .contains("next").contains("prev");
        assertThat(seedsByCode.get("MP_V13_GEOMETRY_ROW_COL_TREATED_AS_XY").description())
                .contains("row/col").contains("x/y");
        assertThat(seedsByCode.get("MP_V13_PRIME_ONE_CLASSIFIED_AS_PRIME").commonMisconception())
                .contains("大于 1");

        assertThat(seedsByCode.get("IP_V13_ARRAY_UPDATE_STATE_TABLE").studentBenefit())
                .contains("旧数组").contains("新数组").contains("提交时机");
        assertThat(seedsByCode.get("IP_V13_ENUMERATION_DECISION_TREE").studentBenefit())
                .contains("前三层决策树").contains("剪枝原因");
        assertThat(seedsByCode.get("IP_V13_NUMBER_THEORY_BOUNDARY_SET").studentBenefit())
                .contains("完全平方数").contains("多次查询");
    }

    private List<AiStandardLibrarySeed> v13Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V13_")
                        || seed.code().startsWith("MP_V13_")
                        || seed.code().startsWith("IP_V13_"))
                .toList();
    }
}
