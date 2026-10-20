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

class AiStandardLibraryV12ArchiveValueSeedsTest {

    private static final List<String> ABSORBED_DIRECT_CODES = List.of(
            "BASIC.LOOP.CONTROL.break_使用",
            "BASIC.ARRAY.PREFIX.前缀和定义",
            "BASIC.STRING.BUILD.删除替换",
            "BASIC.BRANCH.IF.多分支链"
    );

    private static final List<String> ABSORBED_REWRITE_CODES = List.of(
            "DS.LINEAR.STACK.括号匹配",
            "DS.SET_MAP.HASH.字符串键",
            "ENG.DEBUG.TRACE.DP_表变化",
            "CONTEST.SUBMIT.CHECKLIST.初始化检查"
    );

    @Test
    void v12ArchiveValueExtractionAddsHandwrittenItemsFromDirectAndRewriteBuckets() {
        List<AiStandardLibrarySeed> v12Seeds = v12Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> v12SkillCodes = v12Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> v12MistakeCodes = v12Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v12Seeds).hasSizeGreaterThanOrEqualTo(40);
        assertThat(v12Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSizeGreaterThanOrEqualTo(8);
        assertThat(v12Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSizeGreaterThanOrEqualTo(24);
        assertThat(v12Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT))
                .hasSizeGreaterThanOrEqualTo(8);

        assertThat(v12Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT
                            ? "IP_V12_"
                            : seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V12_" : "MP_V12_");
                    String text = String.join("\n",
                            seed.name(),
                            seed.description(),
                            seed.studentExplanation(),
                            seed.commonMisconception(),
                            seed.studentBenefit());
                    assertThat(text)
                            .doesNotContain("适用条件混用")
                            .doesNotContain("理解或应用偏差")
                            .doesNotContain("没有把知识点定义、适用条件或边界要求准确落实")
                            .doesNotContain("代码落点不清");
                    assertThat(seed.knowledgeNodeCodes()).isNotEmpty();
                    assertThat(seed.knowledgeNodeCodes()).allMatch(knowledgeCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v12SkillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                    if (seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v12SkillCodes);
                        assertThat(seed.relatedItems()).isNotEmpty();
                        assertThat(seed.relatedItems()).allMatch(v12MistakeCodes::contains);
                        assertThat(seed.whenToUse()).isNotBlank();
                        assertThat(seed.studentBenefit()).isNotBlank();
                        assertThat(seed.teacherExplanation()).isNotBlank();
                    }
                });
    }

    @Test
    void v12ArchiveValueExtractionKeepsAbsorbedFallbackIdeasInHandwrittenSeeds() {
        List<AiStandardLibrarySeed> v12Seeds = v12Seeds();
        Map<String, AiStandardLibrarySeed> seedsByCode = v12Seeds.stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(ABSORBED_DIRECT_CODES)
                .allSatisfy(knowledgeCode -> {
                    assertThat(v12Seeds)
                            .as("V12 should absorb direct fallback value " + knowledgeCode)
                            .anySatisfy(seed -> assertThat(seed.knowledgeNodeCodes()).contains(knowledgeCode));
                });
        assertThat(ABSORBED_REWRITE_CODES)
                .allSatisfy(knowledgeCode -> {
                    assertThat(v12Seeds)
                            .as("V12 should rewrite fallback type value " + knowledgeCode)
                            .anySatisfy(seed -> assertThat(seed.knowledgeNodeCodes()).contains(knowledgeCode));
                });

        assertThat(seedsByCode.get("MP_V12_CONTINUE_SKIPS_REQUIRED_MAINTENANCE").description())
                .contains("计数").contains("下标").contains("读入进度");
        assertThat(seedsByCode.get("MP_V12_DIFF_STOP_POSITION_MISSING").description())
                .contains("右端后一位").contains("撤销");
        assertThat(seedsByCode.get("MP_V12_STRING_DELETE_SKIPS_NEXT_CHAR").description())
                .contains("删除").contains("下一个待检查字符");
        assertThat(seedsByCode.get("MP_V12_STACK_TOP_ACCESSED_WHEN_EMPTY").description())
                .contains("空栈").contains("top");
        assertThat(seedsByCode.get("MP_V12_HASH_KEY_MISSING_NORMALIZATION").description())
                .contains("大小写").contains("key");
        assertThat(seedsByCode.get("MP_V12_DP_TRACE_OMITS_TRANSITION_SOURCE").description())
                .contains("上一层状态").contains("转移");
        assertThat(seedsByCode.get("IP_V12_SUBMISSION_INITIALIZATION_AUDIT").studentBenefit())
                .contains("每组重置").contains("全局预处理").contains("分支前必赋值");
    }

    private List<AiStandardLibrarySeed> v12Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V12_")
                        || seed.code().startsWith("MP_V12_")
                        || seed.code().startsWith("IP_V12_"))
                .toList();
    }
}
