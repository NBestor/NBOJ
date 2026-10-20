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

class AiStandardLibraryV8ExpansionSeedsTest {

    @Test
    void v8ExpansionAddsHandwrittenSkillsMistakesAndImprovements() {
        List<AiStandardLibrarySeed> v8Seeds = v8Seeds();
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> v8SkillCodes = v8Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> v8MistakeCodes = v8Seeds.stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());

        assertThat(v8Seeds).hasSizeGreaterThanOrEqualTo(30);
        assertThat(v8Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT))
                .hasSizeGreaterThanOrEqualTo(6);
        assertThat(v8Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT))
                .hasSizeGreaterThanOrEqualTo(18);
        assertThat(v8Seeds.stream().filter(seed -> seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT))
                .hasSizeGreaterThanOrEqualTo(6);

        assertThat(v8Seeds)
                .allSatisfy(seed -> {
                    assertThat(seed.code()).startsWith(seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT
                            ? "IP_V8_"
                            : seed.layer() == AiStandardLibraryLayer.SKILL_UNIT ? "SK_V8_" : "MP_V8_");
                    assertThat(seed.name()).doesNotContain("理解或应用偏差");
                    assertThat(seed.description()).isNotBlank();
                    assertThat(seed.knowledgeNodeCodes()).isNotEmpty();
                    assertThat(seed.knowledgeNodeCodes()).allMatch(knowledgeCodes::contains);
                    assertThat(seed.prerequisiteKnowledgeCodes()).allMatch(knowledgeCodes::contains);
                    if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v8SkillCodes);
                        assertThat(seed.commonMisconception()).isNotBlank();
                    }
                    if (seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
                        assertThat(seed.skillUnitCode()).isIn(v8SkillCodes);
                        assertThat(seed.whenToUse()).isNotBlank();
                        assertThat(seed.studentBenefit()).isNotBlank();
                        assertThat(seed.teacherExplanation()).isNotBlank();
                        assertThat(seed.relatedItems()).isNotEmpty();
                        assertThat(seed.relatedItems()).allMatch(v8MistakeCodes::contains);
                    }
                });
    }

    @Test
    void v8ExpansionCoversRepresentativeFineGrainedGaps() {
        Map<String, AiStandardLibrarySeed> seedsByCode = v8Seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode).containsKeys(
                "SK_V8_ARRAY_UPDATE_OLD_NEW_CONTRACT",
                "MP_V8_ARRAY_UPDATE_OVERWRITES_SOURCE_BEFORE_READ",
                "MP_V8_STRING_FIND_SENTINEL_USED_AS_INDEX",
                "MP_V8_SIM_SIMULTANEOUS_EVENT_APPLIED_SEQUENTIALLY",
                "MP_V8_GRAPH_MULTICASE_ADJACENCY_NOT_CLEARED",
                "MP_V8_TWO_POINTERS_DUPLICATE_SKIPPED_BEFORE_COUNT",
                "MP_V8_SUBMIT_OVERFLOW_CHECK_ONLY_ON_FINAL_ANSWER",
                "IP_V8_GRAPH_STORAGE_LIFECYCLE_CHECKLIST",
                "IP_V8_SUBMISSION_RISK_CHECKLIST"
        );

        assertThat(seedsByCode.get("MP_V8_ARRAY_UPDATE_OVERWRITES_SOURCE_BEFORE_READ").description())
                .contains("旧值").contains("新值");
        assertThat(seedsByCode.get("MP_V8_STRING_FIND_SENTINEL_USED_AS_INDEX").commonMisconception())
                .contains("找不到");
        assertThat(seedsByCode.get("MP_V8_SIM_SIMULTANEOUS_EVENT_APPLIED_SEQUENTIALLY").description())
                .contains("同时生效").contains("新状态");
        assertThat(seedsByCode.get("MP_V8_GRAPH_MULTICASE_ADJACENCY_NOT_CLEARED").description())
                .contains("上一组").contains("邻接表");
        assertThat(seedsByCode.get("IP_V8_SUBMISSION_RISK_CHECKLIST").studentBenefit())
                .contains("空状态").contains("最大值").contains("内存规模");
    }

    private List<AiStandardLibrarySeed> v8Seeds() {
        return AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.code().startsWith("SK_V8_")
                        || seed.code().startsWith("MP_V8_")
                        || seed.code().startsWith("IP_V8_"))
                .toList();
    }
}
