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

class AiStandardLibraryV9ExpansionSeedsTest {

    @Test
    void v9ExpansionAddsStructuredSkillsMistakesAndImprovements() {
        List<AiStandardLibrarySeed> v9Seeds = v9Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> v9SkillCodes = v9Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> v9MistakeCodes = v9Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v9Seeds).hasSizeGreaterThanOrEqualTo(50);
        assertThat(v9Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSizeGreaterThanOrEqualTo(10);
        assertThat(v9Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSizeGreaterThanOrEqualTo(30);
        assertThat(v9Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT))
                .hasSizeGreaterThanOrEqualTo(10);

        assertThat(v9Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT
                            ? "IP_V9_"
                            : seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V9_" : "MP_V9_");
                    assertThat(seed.name()).doesNotContain("理解或应用偏差");
                    assertThat(seed.description()).isNotBlank();
                    assertThat(seed.knowledgeNodeCodes()).isNotEmpty();
                    assertThat(seed.knowledgeNodeCodes()).allMatch(knowledgeCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v9SkillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                    if (seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v9SkillCodes);
                        assertThat(seed.whenToUse()).isNotBlank();
                        assertThat(seed.studentBenefit()).isNotBlank();
                        assertThat(seed.teacherExplanation()).isNotBlank();
                        assertThat(seed.relatedItems()).isNotEmpty();
                        assertThat(seed.relatedItems()).allMatch(v9MistakeCodes::contains);
                    }
                });
    }

    @Test
    void v9ExpansionCoversAdditionalHighFrequencyDiagnosticTopics() {
        Map<String, AiStandardLibrarySeed> seedsByCode = v9Seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode).containsKeys(
                "SK_V9_WINDOW_VALIDITY_COUNT_CONTRACT",
                "MP_V9_WINDOW_COUNT_NOT_DECREMENTED_ON_LEFT_MOVE",
                "MP_V9_DSU_CHECKS_PARENT_WITHOUT_FIND",
                "MP_V9_BACKTRACK_FORGETS_RESTORE_AFTER_CHOICE",
                "MP_V9_HEAP_STALE_STATE_NOT_SKIPPED",
                "MP_V9_TOPO_CYCLE_NOT_DETECTED",
                "MP_V9_PREFIX_QUERY_FORGETS_LEFT_MINUS_ONE",
                "MP_V9_BINARY_CHECK_DIRECTION_REVERSED",
                "MP_V9_TREE_PARENT_REVISIT_CAUSES_CYCLE",
                "MP_V9_MAP_DEFAULT_VALUE_SKIPS_FIRST_COUNT",
                "MP_V9_OUTPUT_PATH_RECONSTRUCTION_REVERSED",
                "IP_V9_OUTPUT_SAMPLE_DIFF_CHECKLIST"
        );

        assertThat(seedsByCode.get("MP_V9_WINDOW_COUNT_NOT_DECREMENTED_ON_LEFT_MOVE").description())
                .contains("left").contains("过期状态");
        assertThat(seedsByCode.get("MP_V9_DSU_CHECKS_PARENT_WITHOUT_FIND").commonMisconception())
                .contains("路径压缩").contains("多层");
        assertThat(seedsByCode.get("MP_V9_BACKTRACK_FORGETS_RESTORE_AFTER_CHOICE").description())
                .contains("兄弟分支").contains("上一分支状态");
        assertThat(seedsByCode.get("MP_V9_HEAP_STALE_STATE_NOT_SKIPPED").description())
                .contains("旧距离").contains("惰性校验");
        assertThat(seedsByCode.get("MP_V9_TOPO_CYCLE_NOT_DETECTED").description())
                .contains("输出节点数").contains("总节点数");
        assertThat(seedsByCode.get("MP_V9_PREFIX_QUERY_FORGETS_LEFT_MINUS_ONE").description())
                .contains("[l,r]").contains("左侧数据");
        assertThat(seedsByCode.get("MP_V9_BINARY_CHECK_DIRECTION_REVERSED").commonMisconception())
                .contains("true").contains("机械套模板");
        assertThat(seedsByCode.get("MP_V9_TREE_PARENT_REVISIT_CAUSES_CYCLE").description())
                .contains("parent").contains("无限递归");
        assertThat(seedsByCode.get("MP_V9_MAP_DEFAULT_VALUE_SKIPS_FIRST_COUNT").description())
                .contains("第一次出现").contains("频次");
        assertThat(seedsByCode.get("MP_V9_OUTPUT_PATH_RECONSTRUCTION_REVERSED").description())
                .contains("起点到终点").contains("漏掉起点");
        assertThat(seedsByCode.get("IP_V9_OUTPUT_SAMPLE_DIFF_CHECKLIST").studentBenefit())
                .contains("逐字符对比").contains("多方案并列").contains("无解");
    }

    private List<AiStandardLibrarySeed> v9Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V9_")
                        || seed.code().startsWith("MP_V9_")
                        || seed.code().startsWith("IP_V9_"))
                .toList();
    }
}
