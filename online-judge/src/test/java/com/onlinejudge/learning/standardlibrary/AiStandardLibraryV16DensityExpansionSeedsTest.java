package com.onlinejudge.learning.standardlibrary;

import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeed;
import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeedCatalog;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;
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

class AiStandardLibraryV16DensityExpansionSeedsTest {

    private static final List<String> EXPECTED_SKILL_CODES = List.of(
            "SK_V16_LINKED_LIST_PREV_NEXT",
            "SK_V16_INTERVAL_SELECT_MERGE",
            "SK_V16_PREFIX_SUM_RANGE_QUERY",
            "SK_V16_COUNTING_SORT_FREQUENCY",
            "SK_V16_RUN_LENGTH_ENCODING",
            "SK_V16_MAJORITY_VOTE_CANDIDATE",
            "SK_V16_PRIME_TRIAL_DIVISION",
            "SK_V16_TOPOLOGICAL_DEPENDENCY"
    );

    @Test
    void v16DensityExpansionAddsSharedHighSchoolAndContestCoverage() {
        List<AiStandardLibrarySeed> v16Seeds = v16Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> knowledgePointCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .filter(seed -> seed.type() == InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> skillCodes = v16Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> mistakeCodes = v16Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v16Seeds).hasSize(40);
        assertThat(skillCodes).containsExactlyInAnyOrderElementsOf(EXPECTED_SKILL_CODES);
        assertThat(v16Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSize(8);
        assertThat(v16Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSize(24);
        assertThat(v16Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT))
                .hasSize(8);

        assertThat(v16Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT
                            ? "IP_V16_"
                            : seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V16_" : "MP_V16_");
                    assertThat(seed.knowledgeNodeCodes()).isNotEmpty();
                    assertThat(seed.knowledgeNodeCodes()).allMatch(knowledgeCodes::contains);
                    assertThat(seed.knowledgeNodeCodes())
                            .as(seed.code() + " 至少要有一个知识点锚点")
                            .anyMatch(knowledgePointCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
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
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(skillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                    if (seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(skillCodes);
                        assertThat(seed.relatedItems()).isNotEmpty();
                        assertThat(seed.relatedItems()).allMatch(mistakeCodes::contains);
                        assertThat(seed.whenToUse()).isNotBlank();
                        assertThat(seed.studentBenefit()).isNotBlank();
                        assertThat(seed.teacherExplanation()).isNotBlank();
                    }
                });
    }

    @Test
    void v16RepresentativeItemsUseConcreteHighFrequencySymptoms() {
        Map<String, AiStandardLibrarySeed> seedsByCode = v16Seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode.get("MP_V16_LIST_DELETE_ONLY_MOVES_CURRENT").description())
                .contains("前驱").contains("next");
        assertThat(seedsByCode.get("MP_V16_INTERVAL_TOUCH_BOUNDARY_CLASSIFIED_WRONG").description())
                .contains("端点相接").contains("共存");
        assertThat(seedsByCode.get("MP_V16_PREFIX_QUERY_OFF_BY_ONE").description())
                .contains("[l,r]").contains("单点区间");
        assertThat(seedsByCode.get("MP_V16_COUNT_ARRAY_VALUE_OFFSET_MISSING").description())
                .contains("负数").contains("偏移");
        assertThat(seedsByCode.get("MP_V16_RLE_LAST_RUN_NOT_FLUSHED").description())
                .contains("字符变化").contains("最后一段");
        assertThat(seedsByCode.get("MP_V16_MAJORITY_VOTE_NO_FINAL_VERIFY").description())
                .contains("没有保证").contains("二次验证");
        assertThat(seedsByCode.get("MP_V16_PRIME_LOOP_STOPS_BEFORE_SQRT_FACTOR").description())
                .contains("完全平方数").contains("平方根");
        assertThat(seedsByCode.get("MP_V16_TOPO_EDGE_DIRECTION_REVERSED").description())
                .contains("前置任务").contains("边");

        assertThat(seedsByCode.get("IP_V16_TOPO_INDEGREE_TABLE").studentBenefit())
                .contains("入度").contains("处理数量");
        assertThat(seedsByCode.get("IP_V16_INTERVAL_ENDPOINT_TABLE").teacherExplanation())
                .contains("题目目标").contains("比较符号");
    }

    private List<AiStandardLibrarySeed> v16Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V16_")
                        || seed.code().startsWith("MP_V16_")
                        || seed.code().startsWith("IP_V16_"))
                .toList();
    }
}
