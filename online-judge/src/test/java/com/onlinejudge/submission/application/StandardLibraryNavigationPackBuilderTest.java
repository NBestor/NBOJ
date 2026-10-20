package com.onlinejudge.submission.application;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardApplicationScenario;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;

class StandardLibraryNavigationPackBuilderTest {

    @Test
    void buildsSelectedStructuredPackFromAiNavigationOutput() {
        AiStandardLibraryService libraryService = mock(AiStandardLibraryService.class);
        InformaticsKnowledgeNodeRepository knowledgeRepository = mock(InformaticsKnowledgeNodeRepository.class);
        StandardLibraryNavigationPackBuilder builder =
                new StandardLibraryNavigationPackBuilder(libraryService, knowledgeRepository);

        when(libraryService.findFormalItemAsLegacy(AiStandardLibraryLayer.SKILL_UNIT, "SK_QUEUE_CIRCULAR_INDEX"))
                .thenReturn(Optional.of(skill()));
        when(libraryService.findFormalItemAsLegacy(AiStandardLibraryLayer.MISTAKE_POINT, "MP_QUEUE_REAR_WITHOUT_MOD"))
                .thenReturn(Optional.of(mistake()));
        when(libraryService.findFormalItemAsLegacy(AiStandardLibraryLayer.IMPROVEMENT_POINT, "IP_QUEUE_BOUNDARY_TRACE"))
                .thenReturn(Optional.of(improvement()));
        when(knowledgeRepository.findByCode("DS.QUEUE.CIRCULAR.index_wrap"))
                .thenReturn(Optional.of(InformaticsKnowledgeNode.builder()
                        .code("DS.QUEUE.CIRCULAR.index_wrap")
                        .type(InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                        .name("循环队列下标回绕")
                        .path("数据结构 / 队列与循环队列 / 循环队列下标回绕")
                        .description("队首、队尾下标在固定容量数组中推进后需要回绕。")
                        .enabled(true)
                        .build()));
        when(libraryService.findRelevantApplicationScenarios(anySet(), anySet(), eq(12)))
                .thenReturn(IntStream.range(0, 14)
                        .mapToObj(this::applicationScenario)
                        .toList());

        StandardLibraryPack pack = builder.build(StandardLibraryNavigationOutput.builder()
                .status("DONE")
                .selectedPaths(List.of(StandardLibraryNavigationOutput.SelectedPath.builder()
                        .knowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                        .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                        .mistakePointCode("MP_QUEUE_REAR_WITHOUT_MOD")
                        .improvementPointCode("IP_QUEUE_BOUNDARY_TRACE")
                        .libraryFit("HIT")
                        .evidenceRefs(List.of("code:line:18"))
                        .confidence(0.84)
                        .reason("队尾推进没有回绕。")
                        .build()))
                .build());

        assertThat(pack.getStandardLibraryNavigationSummary().getStatus()).isEqualTo("AI_NAVIGATION");
        assertThat(pack.getKnowledgeAnchors())
                .extracting(StandardLibraryPack.KnowledgeAnchorOption::getId)
                .containsExactly("DS.QUEUE.CIRCULAR.index_wrap");
        assertThat(pack.getSkillUnits())
                .extracting(StandardLibraryPack.SkillUnitOption::getId)
                .containsExactly("SK_QUEUE_CIRCULAR_INDEX");
        assertThat(pack.getMistakePoints())
                .extracting(StandardLibraryPack.MistakePointOption::getId)
                .containsExactly("MP_QUEUE_REAR_WITHOUT_MOD");
        assertThat(pack.getImprovementPoints())
                .extracting(StandardLibraryPack.ImprovementPointOption::getId)
                .containsExactly("IP_QUEUE_BOUNDARY_TRACE");
        assertThat(pack.getApplicationScenarios()).hasSize(12);
        assertThat(pack.getApplicationScenarios())
                .allSatisfy(scenario -> {
                    assertThat(scenario.getSkillUnitCode()).isEqualTo("SK_QUEUE_CIRCULAR_INDEX");
                    assertThat(scenario.getObservableEvidence()).isNotBlank();
                    assertThat(scenario.getConstraintProfile()).isNotBlank();
                });
        assertThat(pack.getKnowledgeGroups()).singleElement().satisfies(group -> {
            assertThat(group.getId()).isEqualTo("DS.QUEUE.CIRCULAR.index_wrap");
            assertThat(group.getName()).isEqualTo("循环队列下标回绕");
            assertThat(group.getSkillUnits()).singleElement().satisfies(skillGroup -> {
                assertThat(skillGroup.getSkillUnit().getId()).isEqualTo("SK_QUEUE_CIRCULAR_INDEX");
                assertThat(skillGroup.getMistakePoints())
                        .extracting(StandardLibraryPack.MistakePointOption::getId)
                        .containsExactly("MP_QUEUE_REAR_WITHOUT_MOD");
                assertThat(skillGroup.getImprovementPoints())
                        .extracting(StandardLibraryPack.ImprovementPointOption::getId)
                        .containsExactly("IP_QUEUE_BOUNDARY_TRACE");
                assertThat(skillGroup.getApplicationScenarios())
                        .extracting(StandardLibraryPack.ApplicationScenarioOption::getContextType)
                        .containsExactly("CLASSROOM", "CONTEST");
            });
        });
    }

    private AiStandardLibraryItem skill() {
        return AiStandardLibraryItem.builder()
                .layer(AiStandardLibraryLayer.SKILL_UNIT)
                .code("SK_QUEUE_CIRCULAR_INDEX")
                .category("队列与循环队列")
                .name("循环队列下标维护")
                .description("维护 fixed-size 数组里的 front/rear 下标。")
                .primaryKnowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                .knowledgeNodeCodes("DS.QUEUE.CIRCULAR.index_wrap")
                .build();
    }

    private AiStandardLibraryItem mistake() {
        return AiStandardLibraryItem.builder()
                .layer(AiStandardLibraryLayer.MISTAKE_POINT)
                .code("MP_QUEUE_REAR_WITHOUT_MOD")
                .category("循环队列")
                .name("队尾更新未取模")
                .description("rear 推进后没有回到数组范围内。")
                .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                .primaryKnowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                .knowledgeNodeCodes("DS.QUEUE.CIRCULAR.index_wrap")
                .mistakeType("INDEX_WRAP")
                .commonMisconception("只把 rear 当作递增计数，没有把数组容量作为循环边界。")
                .build();
    }

    private AiStandardLibraryItem improvement() {
        return AiStandardLibraryItem.builder()
                .layer(AiStandardLibraryLayer.IMPROVEMENT_POINT)
                .code("IP_QUEUE_BOUNDARY_TRACE")
                .category("边界追踪")
                .name("用小容量队列追踪回绕")
                .description("通过容量很小的队列观察 front/rear 多次推进后的状态。")
                .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                .abilityPoint("SK_QUEUE_CIRCULAR_INDEX")
                .primaryKnowledgeNodeCode("DS.QUEUE.CIRCULAR.index_wrap")
                .knowledgeNodeCodes("DS.QUEUE.CIRCULAR.index_wrap")
                .whenToUse("当循环队列错在下标推进时，用小容量样例验证回绕。")
                .studentBenefit("把抽象队列操作落到数组下标状态上。")
                .relatedItems("MP_QUEUE_REAR_WITHOUT_MOD")
                .build();
    }

    private AiStandardApplicationScenario applicationScenario(int index) {
        boolean classroom = index % 2 == 0;
        return AiStandardApplicationScenario.builder()
                .code("SC_QUEUE_" + index)
                .transferPairCode("PAIR_QUEUE_" + index / 2)
                .contextType(classroom ? "CLASSROOM" : "CONTEST")
                .learningPhase(classroom ? "GUIDED_PRACTICE" : "IMPLEMENTATION")
                .title((classroom ? "课堂" : "竞赛") + "循环队列回绕场景 " + index)
                .knowledgePointCode("DS.QUEUE.CIRCULAR.index_wrap")
                .skillUnitCode("SK_QUEUE_CIRCULAR_INDEX")
                .linkedMistakeCodes("MP_QUEUE_REAR_WITHOUT_MOD")
                .linkedImprovementCodes("IP_QUEUE_BOUNDARY_TRACE")
                .taskContext("在固定容量数组中连续执行入队和出队，观察队首与队尾跨越数组末端。")
                .studentTask("记录每一步队首、队尾、元素数量和实际数组位置。")
                .observableEvidence("学生轨迹中的所有下标都位于容量范围内且逻辑顺序保持。")
                .commonFailure("队尾递增后没有回绕，或把空与满状态混淆。")
                .teacherMove("要求学生解释跨越数组末端时逻辑位置和物理下标的对应。")
                .studentCheck("使用容量为三的队列连续绕回两次，轨迹是否仍合法。")
                .constraintProfile("课堂使用小容量轨迹，竞赛使用大量操作和固定内存。")
                .successCriteria("所有操作结果与朴素队列一致，下标不越界。")
                .transferNote("从小容量可视化轨迹迁移到大规模操作序列。")
                .difficultyLevel("INTERMEDIATE")
                .applicableLanguages("PYTHON\nCPP17")
                .sourceFramework(classroom ? "MOE_HIGH_SCHOOL_IT_2020" : "CCF_NOI_2025")
                .sourceReference("https://example.test/official-standard")
                .reviewStatus("INFERRED_REVIEWED")
                .sortOrder(index)
                .enabled(true)
                .libraryVersion("test-fixture")
                .build();
    }
}
