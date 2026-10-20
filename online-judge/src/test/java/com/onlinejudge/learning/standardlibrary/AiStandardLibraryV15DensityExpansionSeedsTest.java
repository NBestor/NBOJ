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

class AiStandardLibraryV15DensityExpansionSeedsTest {

    private static final List<String> EXPECTED_SKILL_CODES = List.of(
            "SK_V15_STACK_EXPRESSION_ORDER",
            "SK_V15_QUEUE_CIRCULAR_STATE",
            "SK_V15_BINARY_TREE_TRAVERSAL_SEQUENCE",
            "SK_V15_BINARY_TREE_ARRAY_INDEX",
            "SK_V15_WINDOW_COUNT_INVARIANT",
            "SK_V15_BINARY_INDEX_BOUNDARY",
            "SK_V15_RECURSION_DIVIDE_MERGE"
    );

    @Test
    void v15DensityExpansionAddsHighFrequencyWeakAreaCoverage() {
        List<AiStandardLibrarySeed> v15Seeds = v15Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> knowledgePointCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .filter(seed -> seed.type() == InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> skillCodes = v15Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> mistakeCodes = v15Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v15Seeds).hasSize(35);
        assertThat(skillCodes).containsExactlyInAnyOrderElementsOf(EXPECTED_SKILL_CODES);
        assertThat(v15Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSize(7);
        assertThat(v15Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSize(21);
        assertThat(v15Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT))
                .hasSize(7);

        assertThat(v15Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT
                            ? "IP_V15_"
                            : seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V15_" : "MP_V15_");
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
    void v15RepresentativeItemsAreConcreteAndTargetHighSchoolHotspots() {
        Map<String, AiStandardLibrarySeed> seedsByCode = v15Seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode.get("MP_V15_STACK_POP_BEFORE_EMPTY_CHECK").description())
                .contains("栈非空").contains("栈顶");
        assertThat(seedsByCode.get("MP_V15_CIRCULAR_QUEUE_FULL_EMPTY_CONFLATED").description())
                .contains("队空").contains("队满");
        assertThat(seedsByCode.get("MP_V15_TREE_INORDER_SPLIT_BOUNDARY_OFF").description())
                .contains("中序").contains("根");
        assertThat(seedsByCode.get("MP_V15_TREE_ARRAY_CHILD_FORMULA_BASE_MIXED").description())
                .contains("0 开始").contains("1 基");
        assertThat(seedsByCode.get("MP_V15_WINDOW_VALID_COUNT_NOT_RESTORED").description())
                .contains("left").contains("计数");
        assertThat(seedsByCode.get("MP_V15_BINARY_TEMPLATE_MIXES_CLOSED_AND_HALF_OPEN").description())
                .contains("闭区间").contains("半开区间");
        assertThat(seedsByCode.get("MP_V15_RECURSION_MERGE_OMITS_ONE_BRANCH").description())
                .contains("左右子问题").contains("父层");

        assertThat(seedsByCode.get("IP_V15_BINARY_TREE_SEQUENCE_SPLIT_TABLE").studentBenefit())
                .contains("当前根").contains("中序区间");
        assertThat(seedsByCode.get("IP_V15_QUEUE_STATE_TABLE").teacherExplanation())
                .contains("职责混用").contains("空满条件");
    }

    private List<AiStandardLibrarySeed> v15Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V15_")
                        || seed.code().startsWith("MP_V15_")
                        || seed.code().startsWith("IP_V15_"))
                .toList();
    }
}
