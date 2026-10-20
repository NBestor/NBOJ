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

class AiStandardLibraryV7DetailSeedsTest {

    @Test
    void v7ExpansionIsGranularStructuredAndLinked() {
        List<AiStandardLibrarySeed> v7Seeds = v7Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> v7SkillCodes = v7Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v7Seeds).hasSizeGreaterThanOrEqualTo(40);
        assertThat(v7Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSizeGreaterThanOrEqualTo(10);
        assertThat(v7Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSizeGreaterThanOrEqualTo(30);

        assertThat(v7Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V7_" : "MP_V7_");
                    assertThat(seed.name()).doesNotContain("理解或应用偏差");
                    assertThat(seed.description()).isNotBlank();
                    assertThat(seed.knowledgeNodeCodes()).isNotEmpty();
                    assertThat(seed.knowledgeNodeCodes()).allMatch(knowledgeCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v7SkillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                });
    }

    @Test
    void v7ExpansionCoversRequestedFineGrainedTopics() {
        Map<String, AiStandardLibrarySeed> seedsByCode = v7Seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode).containsKeys(
                "MP_V7_DP_COUNT_EMPTY_SCHEME_OMITTED",
                "MP_V7_DP_BITMASK_NEXT_STATE_NOT_UPDATED",
                "MP_V7_BINARY_FLOAT_STOPS_BY_EQUALITY",
                "MP_V7_DIFF_RECTANGLE_CORNER_UPDATE_INCOMPLETE",
                "MP_V7_BFS_MULTI_SOURCE_ENQUEUES_ONLY_ONE_START",
                "MP_V7_DIJKSTRA_STALE_HEAP_STATE_NOT_SKIPPED",
                "MP_V7_SUBSTRING_LENGTH_USED_AS_END_INDEX",
                "MP_V7_MAP_KEY_MISSING_STATE_DIMENSION",
                "MP_V7_LONG_LONG_CAST_AFTER_MULTIPLICATION",
                "MP_V7_MULTICASE_GRAPH_NOT_CLEARED_BETWEEN_CASES"
        );

        assertThat(seedsByCode.get("MP_V7_DP_COUNT_EMPTY_SCHEME_OMITTED").description())
                .contains("空方案").contains("初始化为 1");
        assertThat(seedsByCode.get("MP_V7_BFS_MULTI_SOURCE_ENQUEUES_ONLY_ONE_START").description())
                .contains("多个").contains("第 0 层");
        assertThat(seedsByCode.get("MP_V7_MAP_KEY_MISSING_STATE_DIMENSION").commonMisconception())
                .contains("位置").contains("方向");
        assertThat(seedsByCode.get("MP_V7_LONG_LONG_CAST_AFTER_MULTIPLICATION").description())
                .contains("int").contains("long long");
        assertThat(seedsByCode.get("MP_V7_MULTICASE_GRAPH_NOT_CLEARED_BETWEEN_CASES").description())
                .contains("上一组").contains("旧边");
    }

    private List<AiStandardLibrarySeed> v7Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V7_") || seed.code().startsWith("MP_V7_"))
                .toList();
    }
}
