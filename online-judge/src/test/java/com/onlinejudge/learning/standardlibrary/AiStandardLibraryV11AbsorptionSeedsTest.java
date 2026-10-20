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

class AiStandardLibraryV11AbsorptionSeedsTest {

    private static final List<String> ABSORBED_KNOWLEDGE_CODES = List.of(
            "BASIC.IO.STDIN.混合数字与字符串读取",
            "BASIC.TYPE.CHAR_BOOL.字符数字转换",
            "BASIC.EXPR.PRIORITY.位运算优先级",
            "BASIC.STRING.SUBSTRING.长度参数",
            "BASIC.ARRAY.MATRIX.方向遍历",
            "DS.GRAPH.MODEL.有向无向",
            "BASIC.RECURSION.STATE.回溯恢复",
            "CONTEST.SUBMIT.REVIEW.最小反例构造"
    );

    @Test
    void v11AbsorptionAddsSecondBatchOfHandwrittenFallbackMaterial() {
        List<AiStandardLibrarySeed> v11Seeds = v11Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> v11SkillCodes = v11Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> v11MistakeCodes = v11Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v11Seeds).hasSizeGreaterThanOrEqualTo(40);
        assertThat(v11Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSizeGreaterThanOrEqualTo(8);
        assertThat(v11Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSizeGreaterThanOrEqualTo(24);
        assertThat(v11Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT))
                .hasSizeGreaterThanOrEqualTo(8);

        assertThat(v11Seeds)
                .allSatisfy(seed -> {
                    String text = String.join("\n",
                            seed.name(),
                            seed.description(),
                            seed.studentExplanation(),
                            seed.commonMisconception(),
                            seed.studentBenefit());
                    assertThat(seed.code()).contains("_V11_");
                    assertThat(text)
                            .doesNotContain("适用条件混用")
                            .doesNotContain("理解或应用偏差")
                            .doesNotContain("没有把知识点定义、适用条件或边界要求准确落实");
                    assertThat(seed.knowledgeNodeCodes()).isNotEmpty();
                    assertThat(seed.knowledgeNodeCodes()).allMatch(knowledgeCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v11SkillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                    if (seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v11SkillCodes);
                        assertThat(seed.relatedItems()).isNotEmpty();
                        assertThat(seed.relatedItems()).allMatch(v11MistakeCodes::contains);
                        assertThat(seed.whenToUse()).isNotBlank();
                        assertThat(seed.studentBenefit()).isNotBlank();
                    }
                });
    }

    @Test
    void v11AbsorptionCoversSelectedFallbackMaterialWithConcreteTeachingSemantics() {
        List<AiStandardLibrarySeed> v11Seeds = v11Seeds();
        Map<String, AiStandardLibrarySeed> seedsByCode = v11Seeds.stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(ABSORBED_KNOWLEDGE_CODES)
                .allSatisfy(knowledgeCode -> assertThat(v11Seeds)
                        .as("V11 should absorb fallback knowledge " + knowledgeCode)
                        .anySatisfy(seed -> assertThat(seed.knowledgeNodeCodes()).contains(knowledgeCode)));

        assertThat(seedsByCode.get("MP_V11_MIXED_NUMERIC_STRING_READ_DESYNC").description())
                .contains("换行残留").contains("字段");
        assertThat(seedsByCode.get("MP_V11_DIGIT_CHAR_USED_AS_NUMBER").description())
                .contains("字符编码值").contains("字符串拼接");
        assertThat(seedsByCode.get("MP_V11_BIT_PRIORITY_COMPARE_BEFORE_SHIFT").description())
                .contains("比较").contains("移位");
        assertThat(seedsByCode.get("MP_V11_SUBSTRING_LENGTH_TREATED_AS_END_INDEX").description())
                .contains("第二个参数").contains("右端下标");
        assertThat(seedsByCode.get("MP_V11_MATRIX_BOUNDARY_CHECK_AFTER_ACCESS").description())
                .contains("先访问").contains("越界");
        assertThat(seedsByCode.get("MP_V11_UNDIRECTED_EDGE_ADDED_ONLY_ONE_WAY").commonMisconception())
                .contains("输入的一行边").contains("两条邻接记录");
        assertThat(seedsByCode.get("MP_V11_BACKTRACK_STATE_NOT_RESTORED").description())
                .contains("visited").contains("撤销");
        assertThat(seedsByCode.get("IP_V11_SUBMISSION_REVIEW_CARD").studentBenefit())
                .contains("失败输入").contains("首个偏差").contains("检查项");
    }

    private List<AiStandardLibrarySeed> v11Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().contains("_V11_"))
                .toList();
    }
}
