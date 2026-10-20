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

class AiStandardLibraryV6ExpansionSeedsTest {

    @Test
    void v6ExpansionIsLargeStructuredAndLinked() {
        List<AiStandardLibrarySeed> v6Seeds = v6Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> v6SkillCodes = v6Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v6Seeds).hasSizeGreaterThanOrEqualTo(52);
        assertThat(v6Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSizeGreaterThanOrEqualTo(13);
        assertThat(v6Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSizeGreaterThanOrEqualTo(39);

        assertThat(v6Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V6_" : "MP_V6_");
                    assertThat(seed.name()).doesNotContain("理解或应用偏差");
                    assertThat(seed.description()).isNotBlank();
                    assertThat(seed.knowledgeNodeCodes()).isNotEmpty();
                    assertThat(seed.knowledgeNodeCodes()).allMatch(knowledgeCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v6SkillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                });
    }

    @Test
    void v6ExpansionCoversHighValueInformaticsTopics() {
        Map<String, AiStandardLibrarySeed> seedsByCode = v6Seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode).containsKeys(
                "MP_V6_DP_01_KNAPSACK_FORWARD_REUSES_ITEM",
                "MP_V6_BINARY_LOWER_UPPER_BOUND_MIXED",
                "MP_V6_DIFF_RIGHT_PLUS_ONE_MISSING",
                "MP_V6_GREEDY_INTERVAL_SORTS_BY_LEFT_ENDPOINT",
                "MP_V6_ENUM_OBJECT_RANGE_MISSING_CASE",
                "MP_V6_DIJKSTRA_NEGATIVE_EDGE_USED",
                "MP_V6_HEAP_PRIORITY_DIRECTION_REVERSED",
                "MP_V6_COUNT_ORDER_IMPORTANCE_MISREAD",
                "MP_V6_SEARCH_INVALID_STATE_FILTER_TOO_LATE",
                "MP_V6_INTERVAL_TOUCHING_BOUNDARY_MERGED_WRONG",
                "MP_V6_TLE_OPTIMIZES_CONSTANT_NOT_COMPLEXITY",
                "MP_V6_GRID_DIRECTION_DELTA_PAIR_SWAPPED",
                "MP_V6_IO_TREATED_T_AS_DATA_VALUE",
                "MP_V6_FUNCTION_MISSING_RETURN_ON_BRANCH"
        );

        assertThat(seedsByCode.get("MP_V6_DP_01_KNAPSACK_FORWARD_REUSES_ITEM").description())
                .contains("当前物品").contains("选了多次");
        assertThat(seedsByCode.get("MP_V6_DIJKSTRA_NEGATIVE_EDGE_USED").description())
                .contains("负权").contains("Dijkstra");
        assertThat(seedsByCode.get("MP_V6_GRID_OBSTACLE_CHECK_AFTER_VISIT").commonMisconception())
                .contains("合法性判断");
    }

    private List<AiStandardLibrarySeed> v6Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V6_") || seed.code().startsWith("MP_V6_"))
                .toList();
    }
}
