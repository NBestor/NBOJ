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

class AiStandardLibraryV14HighSchoolTermSeedsTest {

    private static final List<String> EXPECTED_SKILL_CODES = List.of(
            "SK_V14_SORT_PASS_INVARIANT",
            "SK_V14_ARENA_EXTREME_SCAN",
            "SK_V14_RUN_LENGTH_ENCODING",
            "SK_V14_MAJORITY_VOTE",
            "SK_V14_STATE_MARKING",
            "SK_V14_LINKED_LIST_POINTER_ORDER",
            "SK_V14_INTERVAL_SCHEDULING",
            "SK_V14_COUNTING_SORT_RANGE"
    );

    @Test
    void v14HighSchoolTermSeedsAddUnifiedInformaticsEntries() {
        List<AiStandardLibrarySeed> v14Seeds = v14Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> knowledgePointCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .filter(seed -> seed.type() == InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> v14SkillCodes = v14Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> v14MistakeCodes = v14Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v14Seeds).hasSizeGreaterThanOrEqualTo(32);
        assertThat(v14SkillCodes).containsAll(EXPECTED_SKILL_CODES);
        assertThat(v14Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSizeGreaterThanOrEqualTo(8);
        assertThat(v14Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSizeGreaterThanOrEqualTo(16);
        assertThat(v14Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT))
                .hasSizeGreaterThanOrEqualTo(8);

        assertThat(v14Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT
                            ? "IP_V14_"
                            : seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V14_" : "MP_V14_");
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
                    assertThat(seed.knowledgeNodeCodes())
                            .as(seed.code() + " 至少要有一个知识点锚点")
                            .anyMatch(knowledgePointCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v14SkillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                    if (seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v14SkillCodes);
                        assertThat(seed.relatedItems()).isNotEmpty();
                        assertThat(seed.relatedItems()).allMatch(v14MistakeCodes::contains);
                        assertThat(seed.whenToUse()).isNotBlank();
                        assertThat(seed.studentBenefit()).isNotBlank();
                        assertThat(seed.teacherExplanation()).isNotBlank();
                    }
                });
    }

    @Test
    void v14RepresentativeItemsUseHighSchoolTermsAndConcreteSymptoms() {
        Map<String, AiStandardLibrarySeed> seedsByCode = v14Seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode.get("SK_V14_SORT_PASS_INVARIANT").name())
                .contains("冒泡排序", "选择排序", "计数排序");
        assertThat(seedsByCode.get("MP_V14_BUBBLE_PASS_BOUNDARY_INCLUDES_SORTED_SUFFIX").description())
                .contains("冒泡").contains("已排序后缀");
        assertThat(seedsByCode.get("MP_V14_ARENA_INITIALIZES_WITH_ZERO_OUTSIDE_VALUE_RANGE").name())
                .contains("擂台法");
        assertThat(seedsByCode.get("MP_V14_RLE_MISSES_LAST_RUN_FLUSH").description())
                .contains("游程编码").contains("最后一段");
        assertThat(seedsByCode.get("SK_V14_MAJORITY_VOTE").description())
                .contains("多数投票算法").contains("二次验证");
        assertThat(seedsByCode.get("MP_V14_STATE_FLAG_NOT_RESET_AFTER_SEGMENT_END").description())
                .contains("状态标记").contains("重置");
        assertThat(seedsByCode.get("SK_V14_LINKED_LIST_POINTER_ORDER").description())
                .contains("链表").contains("next").contains("prev");
        assertThat(seedsByCode.get("MP_V14_INTERVAL_SCHEDULE_SORTS_BY_START_INSTEAD_OF_END").description())
                .contains("区间调度").contains("右端点");
        assertThat(seedsByCode.get("MP_V14_COUNTING_SORT_IGNORES_VALUE_OFFSET").description())
                .contains("计数排序").contains("偏移");

        assertThat(seedsByCode.get("IP_V14_RLE_SEGMENT_TABLE").studentBenefit())
                .contains("字符段").contains("长度").contains("输出内容");
        assertThat(seedsByCode.get("IP_V14_INTERVAL_ENDPOINT_CASES").studentBenefit())
                .contains("刚好相接").contains("包含关系");
    }

    private List<AiStandardLibrarySeed> v14Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V14_")
                        || seed.code().startsWith("MP_V14_")
                        || seed.code().startsWith("IP_V14_"))
                .toList();
    }
}
