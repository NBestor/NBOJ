package com.onlinejudge.learning.standardlibrary;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardApplicationScenario;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardImprovementPoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardMistakePoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardSkillUnit;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryDiagnosticLayerResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationExpansionResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationNodeResponse;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardImprovementPointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardApplicationScenarioRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardMistakePointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-standard-library-navigation-service;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "TEACHER_PASSWORD=test-teacher-password",
        "TEACHER_SESSION_SECRET=test-teacher-session-secret-1234567890",
        "STUDENT_TOKEN_SECRET=test-student-token-secret-1234567890",
        "AI_ENABLED=false"
})
class AiStandardLibraryNavigationServiceTest {

    @Autowired
    AiStandardLibraryService service;

    @Autowired
    InformaticsKnowledgeNodeRepository knowledgeRepository;

    @Autowired
    AiStandardSkillUnitRepository skillUnitRepository;

    @Autowired
    AiStandardMistakePointRepository mistakePointRepository;

    @Autowired
    AiStandardImprovementPointRepository improvementPointRepository;

    @Autowired
    AiStandardApplicationScenarioRepository applicationScenarioRepository;

    @BeforeEach
    void seedMinimalNavigationLibrary() {
        if (!knowledgeRepository.existsByCode("BASIC")) {
            knowledgeRepository.saveAllAndFlush(List.of(
                    knowledgeNode("BASIC", null, InformaticsKnowledgeNodeType.DOMAIN,
                            "基础语法", "基础语法", 1),
                    knowledgeNode("DS", null, InformaticsKnowledgeNodeType.DOMAIN,
                            "数据结构", "数据结构", 2),
                    knowledgeNode("ALGO", null, InformaticsKnowledgeNodeType.DOMAIN,
                            "算法思想", "算法思想", 3),
                    knowledgeNode("MATH", null, InformaticsKnowledgeNodeType.DOMAIN,
                            "数学基础", "数学基础", 4),
                    knowledgeNode("ENG", null, InformaticsKnowledgeNodeType.DOMAIN,
                            "工程实践", "工程实践", 5),
                    knowledgeNode("CONTEST", null, InformaticsKnowledgeNodeType.DOMAIN,
                            "竞赛综合", "竞赛综合", 6),
                    knowledgeNode("DS.FIXTURE", "DS", InformaticsKnowledgeNodeType.CHAPTER,
                            "数据结构测试子节点", "数据结构 / 数据结构测试子节点", 1),
                    knowledgeNode("ALGO.FIXTURE", "ALGO", InformaticsKnowledgeNodeType.CHAPTER,
                            "算法思想测试子节点", "算法思想 / 算法思想测试子节点", 1),
                    knowledgeNode("MATH.FIXTURE", "MATH", InformaticsKnowledgeNodeType.CHAPTER,
                            "数学基础测试子节点", "数学基础 / 数学基础测试子节点", 1),
                    knowledgeNode("ENG.FIXTURE", "ENG", InformaticsKnowledgeNodeType.CHAPTER,
                            "工程实践测试子节点", "工程实践 / 工程实践测试子节点", 1),
                    knowledgeNode("CONTEST.FIXTURE", "CONTEST", InformaticsKnowledgeNodeType.CHAPTER,
                            "竞赛综合测试子节点", "竞赛综合 / 竞赛综合测试子节点", 1),
                    knowledgeNode("BASIC.FIXTURE", "BASIC", InformaticsKnowledgeNodeType.CHAPTER,
                            "基础语法测试子节点", "基础语法 / 基础语法测试子节点", 2)
            ));
        }
        if (!knowledgeRepository.existsByCode("BASIC.IO.MULTI_CASE.显式_T_组循环")) {
            knowledgeRepository.saveAllAndFlush(List.of(
                    knowledgeNode("BASIC.IO", "BASIC", InformaticsKnowledgeNodeType.CHAPTER,
                            "输入输出", "基础语法 / 输入输出", 1),
                    knowledgeNode("BASIC.IO.MULTI_CASE", "BASIC.IO", InformaticsKnowledgeNodeType.TOPIC,
                            "多组数据", "基础语法 / 输入输出 / 多组数据", 1),
                    knowledgeNode("BASIC.IO.MULTI_CASE.显式_T_组循环", "BASIC.IO.MULTI_CASE",
                            InformaticsKnowledgeNodeType.KNOWLEDGE_POINT,
                            "显式 T 组循环", "基础语法 / 输入输出 / 多组数据 / 显式 T 组循环", 1)
            ));
        }
        String pointCode = "BASIC.IO.MULTI_CASE.显式_T_组循环";
        String skillCode = "SK_MULTI_CASE_LOOP_FIXTURE";
        if (skillUnitRepository.findByCode(skillCode).isEmpty()) {
            skillUnitRepository.saveAndFlush(AiStandardSkillUnit.builder()
                    .code(skillCode)
                    .category("输入输出")
                    .name("显式 T 组循环读取")
                    .description("能把题面 T 组输入映射到循环读取。")
                    .learningGoal("确认每组数据都被读取和处理。")
                    .primaryKnowledgeNodeCode(pointCode)
                    .knowledgeNodeCodes(pointCode)
                    .masteryLevel("MEDIUM")
                    .applicableLanguages("PYTHON\nCPP17")
                    .enabled(true)
                    .libraryVersion("test-fixture")
                    .build());
        }
        if (mistakePointRepository.findByCode("MP_MULTI_CASE_MISSING_LOOP_FIXTURE").isEmpty()) {
            mistakePointRepository.saveAndFlush(AiStandardMistakePoint.builder()
                    .code("MP_MULTI_CASE_MISSING_LOOP_FIXTURE")
                    .category("输入输出")
                    .name("漏处理多组数据")
                    .description("只按一组样例读取，没有循环处理 T 组。")
                    .skillUnitCode(skillCode)
                    .mistakeType("IO_FORMAT")
                    .misconception("把样例形态当作完整输入结构。")
                    .symptom("多组数据时只输出一组结果。")
                    .repairStrategy("先数清楚每组输入和输出。")
                    .severity("HIGH")
                    .primaryKnowledgeNodeCode(pointCode)
                    .knowledgeNodeCodes(pointCode)
                    .applicableLanguages("PYTHON\nCPP17")
                    .enabled(true)
                    .libraryVersion("test-fixture")
                    .build());
        }
        if (improvementPointRepository.findByCode("IP_MULTI_CASE_TRACE_FIXTURE").isEmpty()) {
            improvementPointRepository.saveAndFlush(AiStandardImprovementPoint.builder()
                    .code("IP_MULTI_CASE_TRACE_FIXTURE")
                    .category("自测")
                    .name("多组输入 trace")
                    .description("用两组最小样例检查读取循环。")
                    .skillUnitCode(skillCode)
                    .primaryKnowledgeNodeCode(pointCode)
                    .knowledgeNodeCodes(pointCode)
                    .improvementGoal("建立输入结构对照习惯。")
                    .practiceStrategy("手推两组输入的读取顺序。")
                    .studentBenefit("能更早发现只处理一组数据的问题。")
                    .enabled(true)
                    .libraryVersion("test-fixture")
                    .build());
        }
        if (applicationScenarioRepository.findByCode("SC_MULTI_CASE_CLASSROOM_FIXTURE").isEmpty()) {
            applicationScenarioRepository.saveAllAndFlush(List.of(
                    applicationScenario(
                            "SC_MULTI_CASE_CLASSROOM_FIXTURE",
                            "CLASSROOM",
                            "GUIDED_PRACTICE",
                            "课堂：多组输入读取轨迹",
                            skillCode,
                            pointCode,
                            "MP_MULTI_CASE_MISSING_LOOP_FIXTURE",
                            "IP_MULTI_CASE_TRACE_FIXTURE",
                            10),
                    applicationScenario(
                            "SC_MULTI_CASE_CONTEST_FIXTURE",
                            "CONTEST",
                            "PROBLEM_READING",
                            "竞赛：多组输入结构核对",
                            skillCode,
                            pointCode,
                            "MP_MULTI_CASE_MISSING_LOOP_FIXTURE",
                            "IP_MULTI_CASE_TRACE_FIXTURE",
                            20)
            ));
        }
    }

    @Test
    void listsRootKnowledgeAreasForAiNavigation() {
        List<AiStandardLibraryNavigationNodeResponse> roots = service.listRootKnowledgeAreas();

        assertThat(roots)
                .extracting(AiStandardLibraryNavigationNodeResponse::getCode)
                .containsExactly("BASIC", "DS", "ALGO", "MATH", "ENG", "CONTEST");
        assertThat(roots)
                .allSatisfy(root -> {
                    assertThat(root.getParentCode()).isNull();
                    assertThat(root.isHasChildren()).isTrue();
                });
    }

    @Test
    void expandsOnlySelectedKnowledgeNodeChildren() {
        AiStandardLibraryNavigationExpansionResponse expansion =
                service.expandKnowledgeNode("BASIC.IO.MULTI_CASE");

        assertThat(expansion.getNode().getCode()).isEqualTo("BASIC.IO.MULTI_CASE");
        assertThat(expansion.getAncestors())
                .extracting(AiStandardLibraryNavigationNodeResponse::getCode)
                .containsExactly("BASIC", "BASIC.IO");
        assertThat(expansion.getChildren())
                .extracting(AiStandardLibraryNavigationNodeResponse::getCode)
                .contains("BASIC.IO.MULTI_CASE.显式_T_组循环");
        assertThat(expansion.getChildren())
                .extracting(AiStandardLibraryNavigationNodeResponse::getType)
                .containsOnly("KNOWLEDGE_POINT");
        assertThat(expansion.getChildPage()).isZero();
        assertThat(expansion.getChildTotal()).isGreaterThanOrEqualTo(expansion.getChildren().size());
    }

    @Test
    void expandsSelectedKnowledgeNodeChildrenWithBoundedPage() {
        AiStandardLibraryNavigationExpansionResponse firstPage = service.expandKnowledgeNode("BASIC", 0, 1);

        assertThat(firstPage.getChildren()).hasSize(1);
        assertThat(firstPage.getChildSize()).isEqualTo(1);
        assertThat(firstPage.getChildTotal()).isGreaterThan(1);
        assertThat(firstPage.isChildHasMore()).isTrue();
    }

    @Test
    void expandsDiagnosticLayerUnderKnowledgePointFromNormalizedLibrary() {
        AiStandardLibraryDiagnosticLayerResponse layer =
                service.expandDiagnosticLayer("BASIC.IO.MULTI_CASE.显式_T_组循环");

        assertThat(layer.getKnowledgePoint().getCode()).isEqualTo("BASIC.IO.MULTI_CASE.显式_T_组循环");
        assertThat(layer.getKnowledgePoint().getType()).isEqualTo("KNOWLEDGE_POINT");
        assertThat(layer.getSkillUnits()).isNotEmpty();
        assertThat(layer.getSkillUnits())
                .allSatisfy(skill -> {
                    assertThat(skill.getPrimaryKnowledgeNodeCode())
                            .isEqualTo("BASIC.IO.MULTI_CASE.显式_T_组循环");
                    assertThat(skill.getMistakePoints())
                            .allSatisfy(mistake -> assertThat(mistake.getSkillUnitCode()).isEqualTo(skill.getCode()));
                    assertThat(skill.getImprovementPoints())
                            .allSatisfy(improvement -> assertThat(improvement.getSkillUnitCode()).isEqualTo(skill.getCode()));
                });
        assertThat(layer.getSkillUnits())
                .anySatisfy(skill -> assertThat(skill.getMistakePoints()).isNotEmpty());
        assertThat(layer.getSkillUnits())
                .filteredOn(skill -> "SK_MULTI_CASE_LOOP_FIXTURE".equals(skill.getCode()))
                .singleElement()
                .satisfies(skill -> {
                    assertThat(skill.getApplicationScenarios())
                            .extracting(AiStandardLibraryDiagnosticLayerResponse.ApplicationScenario::getContextType)
                            .containsExactly("CLASSROOM", "CONTEST");
                    assertThat(skill.getApplicationScenarios())
                            .allSatisfy(scenario -> {
                                assertThat(scenario.getTransferPairCode())
                                        .isEqualTo("PAIR_MULTI_CASE_FIXTURE");
                                assertThat(scenario.getObservableEvidence()).isNotBlank();
                                assertThat(scenario.getSuccessCriteria()).isNotBlank();
                                assertThat(scenario.getLinkedMistakeCodes())
                                        .containsExactly("MP_MULTI_CASE_MISSING_LOOP_FIXTURE");
                                assertThat(scenario.getLinkedImprovementCodes())
                                        .containsExactly("IP_MULTI_CASE_TRACE_FIXTURE");
                            });
                });
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void returnsBatchTwoImprovementWithSkillAndRelatedMistakeFromPrimaryKnowledgePoint() {
        String pointCode = "BASIC.IO.MULTI_CASE.显式_T_组循环";
        String skillCode = "SK_DQ2_NAVIGATION_FIXTURE";
        String mistakeCode = "MP_DQ2_NAVIGATION_FIXTURE";
        String improvementCode = "IP_DQ2_NAVIGATION_FIXTURE";
        skillUnitRepository.saveAndFlush(AiStandardSkillUnit.builder()
                .code(skillCode)
                .category("输入输出")
                .name("多组变量生命周期测试能力")
                .description("能区分每组重置和跨组共享状态。")
                .learningGoal("为变量标注生命周期并验证第二组输入。")
                .primaryKnowledgeNodeCode(pointCode)
                .knowledgeNodeCodes(pointCode)
                .masteryLevel("MEDIUM")
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v2")
                .build());
        mistakePointRepository.saveAndFlush(AiStandardMistakePoint.builder()
                .code(mistakeCode)
                .category("输入输出")
                .name("每组状态在外层循环外初始化")
                .description("第二组继续使用第一组累计状态。")
                .skillUnitCode(skillCode)
                .mistakeType("STATE_LIFECYCLE")
                .misconception("没有区分跨组共享和每组临时状态。")
                .symptom("单组正确，多组从第二组开始偏差。")
                .repairStrategy("把每组状态初始化移动到组循环内部。")
                .severity("HIGH")
                .primaryKnowledgeNodeCode(pointCode)
                .knowledgeNodeCodes(pointCode)
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v2")
                .build());
        improvementPointRepository.saveAndFlush(AiStandardImprovementPoint.builder()
                .code(improvementCode)
                .category("提升点/多组输入")
                .name("给多组变量标注生命周期")
                .description("用两组不同输入检查状态是否按组重置。")
                .skillUnitCode(skillCode)
                .primaryKnowledgeNodeCode(pointCode)
                .knowledgeNodeCodes(pointCode)
                .relatedMistakeCodes(mistakeCode)
                .improvementGoal("区分跨组共享和每组重置状态。")
                .practiceStrategy("标注变量生命周期并手推第二组。")
                .studentBenefit("避免只在多组输入时出现状态串组。")
                .teacherExplanation("要求学生说明每个初始化语句所在层级。")
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v2")
                .build());

        AiStandardLibraryDiagnosticLayerResponse layer = service.expandDiagnosticLayer(pointCode);

        assertThat(layer.getSkillUnits())
                .filteredOn(skill -> skillCode.equals(skill.getCode()))
                .singleElement()
                .satisfies(skill -> {
                    assertThat(skill.getMistakePoints())
                            .extracting(AiStandardLibraryDiagnosticLayerResponse.MistakePoint::getCode)
                            .containsExactly(mistakeCode);
                    assertThat(skill.getImprovementPoints())
                            .singleElement()
                            .satisfies(improvement -> {
                                assertThat(improvement.getCode()).isEqualTo(improvementCode);
                                assertThat(improvement.getPrimaryKnowledgeNodeCode()).isEqualTo(pointCode);
                                assertThat(improvement.getRelatedMistakeCodes()).containsExactly(mistakeCode);
                            });
                });
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void returnsBatchThreeAlgorithmAndDataStructureClosuresFromPreciseKnowledgePoints() {
        String algorithmSkillPrimary = "ALGO.DQ3.BFS";
        String algorithmPoint = "ALGO.DQ3.DIJKSTRA";
        String dataStructureSkillPrimary = "DS.DQ3.STACK";
        String dataStructurePoint = "DS.DQ3.QUEUE";
        knowledgeRepository.saveAllAndFlush(List.of(
                knowledgeNode(algorithmSkillPrimary, "ALGO", InformaticsKnowledgeNodeType.KNOWLEDGE_POINT,
                        "BFS 最短路测试点", "算法思想 / BFS 最短路测试点", 20),
                knowledgeNode(algorithmPoint, "ALGO", InformaticsKnowledgeNodeType.KNOWLEDGE_POINT,
                        "Dijkstra 测试点", "算法思想 / Dijkstra 测试点", 21),
                knowledgeNode(dataStructureSkillPrimary, "DS", InformaticsKnowledgeNodeType.KNOWLEDGE_POINT,
                        "栈操作测试点", "数据结构 / 栈操作测试点", 20),
                knowledgeNode(dataStructurePoint, "DS", InformaticsKnowledgeNodeType.KNOWLEDGE_POINT,
                        "队列操作测试点", "数据结构 / 队列操作测试点", 21)
        ));

        String algorithmSkill = "SK_DQ3_ALGORITHM_NAVIGATION_FIXTURE";
        String algorithmMistake = "MP_DQ3_ALGORITHM_NAVIGATION_FIXTURE";
        String algorithmImprovement = "IP_DQ3_ALGORITHM_NAVIGATION_FIXTURE";
        skillUnitRepository.saveAndFlush(AiStandardSkillUnit.builder()
                .code(algorithmSkill)
                .category("图算法")
                .name("检查最短路算法前提")
                .description("根据边权和距离状态选择并验证最短路算法。")
                .learningGoal("先检查算法前提，再手算松弛轨迹。")
                .primaryKnowledgeNodeCode(algorithmSkillPrimary)
                .knowledgeNodeCodes(algorithmSkillPrimary + "\n" + algorithmPoint)
                .masteryLevel("MEDIUM")
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v3")
                .build());
        mistakePointRepository.saveAndFlush(AiStandardMistakePoint.builder()
                .code(algorithmMistake)
                .category("图算法")
                .name("Dijkstra 未过滤陈旧状态")
                .description("弹出历史距离后仍继续松弛出边。")
                .skillUnitCode(algorithmSkill)
                .mistakeType("STALE_STATE")
                .misconception("认为堆中同一节点只会出现一次。")
                .symptom("重复扩展并可能使用过期距离。")
                .repairStrategy("弹出后比较队列距离和当前 dist。")
                .severity("HIGH")
                .primaryKnowledgeNodeCode(algorithmPoint)
                .knowledgeNodeCodes(algorithmSkillPrimary + "\n" + algorithmPoint)
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v3")
                .build());
        improvementPointRepository.saveAndFlush(AiStandardImprovementPoint.builder()
                .code(algorithmImprovement)
                .category("提升点/最短路")
                .name("手算距离松弛轨迹")
                .description("逐步记录弹出状态、候选距离和松弛结果。")
                .skillUnitCode(algorithmSkill)
                .primaryKnowledgeNodeCode(algorithmPoint)
                .knowledgeNodeCodes(algorithmSkillPrimary + "\n" + algorithmPoint)
                .relatedMistakeCodes(algorithmMistake)
                .improvementGoal("建立最短路状态检查合同。")
                .practiceStrategy("对五点图填写完整松弛轨迹表。")
                .studentBenefit("能定位初始化、松弛或陈旧状态错误。")
                .teacherExplanation("要求学生解释每一次未松弛的原因。")
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v3")
                .build());

        String dataStructureSkill = "SK_DQ3_DATA_STRUCTURE_NAVIGATION_FIXTURE";
        String dataStructureMistake = "MP_DQ3_DATA_STRUCTURE_NAVIGATION_FIXTURE";
        String dataStructureImprovement = "IP_DQ3_DATA_STRUCTURE_NAVIGATION_FIXTURE";
        skillUnitRepository.saveAndFlush(AiStandardSkillUnit.builder()
                .code(dataStructureSkill)
                .category("数据结构")
                .name("按操作语义选择线性结构")
                .description("区分栈与队列的加入和取出顺序。")
                .learningGoal("用操作轨迹验证容器选择。")
                .primaryKnowledgeNodeCode(dataStructureSkillPrimary)
                .knowledgeNodeCodes(dataStructureSkillPrimary + "\n" + dataStructurePoint)
                .masteryLevel("MEDIUM")
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v3")
                .build());
        mistakePointRepository.saveAndFlush(AiStandardMistakePoint.builder()
                .code(dataStructureMistake)
                .category("数据结构")
                .name("队列错误使用后进先出")
                .description("从队尾取出元素，破坏先进先出顺序。")
                .skillUnitCode(dataStructureSkill)
                .mistakeType("OPERATION_CONTRACT")
                .misconception("只关注容器能存元素，没有检查取出顺序。")
                .symptom("BFS 扩展顺序变成深度优先。")
                .repairStrategy("记录每次入队和出队后的完整队列。")
                .severity("HIGH")
                .primaryKnowledgeNodeCode(dataStructurePoint)
                .knowledgeNodeCodes(dataStructureSkillPrimary + "\n" + dataStructurePoint)
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v3")
                .build());
        improvementPointRepository.saveAndFlush(AiStandardImprovementPoint.builder()
                .code(dataStructureImprovement)
                .category("提升点/数据结构")
                .name("按操作语义回放队列")
                .description("逐步记录入队、出队和下一次取出元素。")
                .skillUnitCode(dataStructureSkill)
                .primaryKnowledgeNodeCode(dataStructurePoint)
                .knowledgeNodeCodes(dataStructureSkillPrimary + "\n" + dataStructurePoint)
                .relatedMistakeCodes(dataStructureMistake)
                .improvementGoal("让容器操作和题目时序保持一致。")
                .practiceStrategy("回放八次操作并预测完整出队序列。")
                .studentBenefit("能区分栈、队列和优先队列。")
                .teacherExplanation("隐藏容器名，只给操作要求让学生选择结构。")
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v3")
                .build());

        AiStandardLibraryDiagnosticLayerResponse algorithmLayer = service.expandDiagnosticLayer(algorithmPoint);
        assertThat(algorithmLayer.getSkillUnits())
                .filteredOn(skill -> algorithmSkill.equals(skill.getCode()))
                .singleElement()
                .satisfies(skill -> {
                    assertThat(skill.getMistakePoints())
                            .extracting(AiStandardLibraryDiagnosticLayerResponse.MistakePoint::getCode)
                            .containsExactly(algorithmMistake);
                    assertThat(skill.getImprovementPoints())
                            .singleElement()
                            .satisfies(improvement -> {
                                assertThat(improvement.getCode()).isEqualTo(algorithmImprovement);
                                assertThat(improvement.getRelatedMistakeCodes())
                                        .containsExactly(algorithmMistake);
                            });
                });

        AiStandardLibraryDiagnosticLayerResponse dataStructureLayer =
                service.expandDiagnosticLayer(dataStructurePoint);
        assertThat(dataStructureLayer.getSkillUnits())
                .filteredOn(skill -> dataStructureSkill.equals(skill.getCode()))
                .singleElement()
                .satisfies(skill -> {
                    assertThat(skill.getMistakePoints())
                            .extracting(AiStandardLibraryDiagnosticLayerResponse.MistakePoint::getCode)
                            .containsExactly(dataStructureMistake);
                    assertThat(skill.getImprovementPoints())
                            .singleElement()
                            .satisfies(improvement -> {
                                assertThat(improvement.getCode()).isEqualTo(dataStructureImprovement);
                                assertThat(improvement.getRelatedMistakeCodes())
                                        .containsExactly(dataStructureMistake);
                            });
                });
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void resolvesParentSkillWhenMistakeUsesMorePrecisePrimaryKnowledgePoint() {
        String skillPrimaryCode = "BASIC.IO.MULTI_CASE.显式_T_组循环";
        String precisePointCode = "BASIC.IO.MULTI_CASE.周期等待参数";
        String skillCode = "SK_MULTI_CASE_CROSS_NODE_FIXTURE";
        String mistakeCode = "MP_MULTI_CASE_CROSS_NODE_FIXTURE";
        String improvementCode = "IP_MULTI_CASE_CROSS_NODE_FIXTURE";
        knowledgeRepository.saveAndFlush(knowledgeNode(
                precisePointCode,
                "BASIC.IO.MULTI_CASE",
                InformaticsKnowledgeNodeType.KNOWLEDGE_POINT,
                "周期等待参数",
                "基础语法 / 输入输出 / 多组数据 / 周期等待参数",
                2));
        skillUnitRepository.saveAndFlush(AiStandardSkillUnit.builder()
                .code(skillCode)
                .category("输入输出")
                .name("跨知识点能力归属")
                .description("能力点以主知识点组织，并允许错因挂到更精确的相关知识点。")
                .learningGoal("在精确知识点下仍能找到所属能力点和错因。")
                .primaryKnowledgeNodeCode(skillPrimaryCode)
                .knowledgeNodeCodes(skillPrimaryCode + "\n" + precisePointCode)
                .masteryLevel("MEDIUM")
                .applicableLanguages("CPP17")
                .enabled(true)
                .libraryVersion("test-fixture")
                .build());
        mistakePointRepository.saveAndFlush(AiStandardMistakePoint.builder()
                .code(mistakeCode)
                .category("输入输出")
                .name("精确知识点下的参数误用")
                .description("错因使用更精确的主知识点，但仍属于上级能力点。")
                .skillUnitCode(skillCode)
                .mistakeType("PARAMETER_MISUSE")
                .misconception("把两个不同语义的参数混为一谈。")
                .symptom("边界样例的计算结果出现固定偏差。")
                .repairStrategy("分别列出参数来源、单位和进入公式的位置。")
                .severity("HIGH")
                .primaryKnowledgeNodeCode(precisePointCode)
                .knowledgeNodeCodes(precisePointCode)
                .applicableLanguages("CPP17")
                .enabled(true)
                .libraryVersion("test-fixture")
                .build());
        improvementPointRepository.saveAndFlush(AiStandardImprovementPoint.builder()
                .code(improvementCode)
                .category("自测")
                .name("参数语义对照表")
                .description("用最小样例对照每个参数的来源、单位和作用。")
                .skillUnitCode(skillCode)
                .primaryKnowledgeNodeCode(precisePointCode)
                .knowledgeNodeCodes(precisePointCode)
                .improvementGoal("建立参数进入公式前的语义核对习惯。")
                .practiceStrategy("先写参数表，再手推一个边界样例。")
                .studentBenefit("能更早发现重复计入或参数串位。")
                .enabled(true)
                .libraryVersion("test-fixture")
                .build());

        AiStandardLibraryDiagnosticLayerResponse layer = service.expandDiagnosticLayer(precisePointCode);

        assertThat(layer.getKnowledgePoint().isHasDiagnosticLayer()).isTrue();
        assertThat(layer.getSkillUnits()).singleElement()
                .satisfies(skill -> {
                    assertThat(skill.getCode()).isEqualTo(skillCode);
                    assertThat(skill.getPrimaryKnowledgeNodeCode()).isEqualTo(skillPrimaryCode);
                    assertThat(skill.getMistakePoints())
                            .extracting(AiStandardLibraryDiagnosticLayerResponse.MistakePoint::getCode)
                            .containsExactly(mistakeCode);
                    assertThat(skill.getImprovementPoints())
                            .extracting(AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint::getCode)
                            .containsExactly(improvementCode);
                });
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void h2FixtureExpandsFromRootToNormalizedAnchorLayer() {
        String chapterCode = "BASIC.AI_QUALITY_FIXTURE";
        String pointCode = "BASIC.AI_QUALITY_FIXTURE.LAZY_RANGE";
        String skillCode = "SK_FIXTURE_LAZY_RANGE";
        String mistakeCode = "MP_FIXTURE_LAZY_OVERWRITE";
        String improvementCode = "IP_FIXTURE_LAZY_TRACE";
        knowledgeRepository.saveAllAndFlush(List.of(
                knowledgeNode(chapterCode, "BASIC", InformaticsKnowledgeNodeType.CHAPTER,
                        "AI 质量测试章节", "基础语法 / AI 质量测试章节", true),
                knowledgeNode(pointCode, chapterCode, InformaticsKnowledgeNodeType.KNOWLEDGE_POINT,
                        "区间 lazy 累积测试点", "基础语法 / AI 质量测试章节 / 区间 lazy 累积测试点", false)
        ));
        skillUnitRepository.saveAndFlush(AiStandardSkillUnit.builder()
                .code(skillCode)
                .category("线段树")
                .name("区间 lazy 累积观察")
                .description("能观察连续区间加后的 lazy 和 max 变化。")
                .learningGoal("用两次区间更新手推 lazy 累积。")
                .primaryKnowledgeNodeCode(pointCode)
                .knowledgeNodeCodes(pointCode)
                .masteryLevel("MEDIUM")
                .applicableLanguages("CPP17")
                .enabled(true)
                .libraryVersion("test-fixture")
                .build());
        mistakePointRepository.saveAndFlush(AiStandardMistakePoint.builder()
                .code(mistakeCode)
                .category("线段树")
                .name("lazy 覆盖代替累积")
                .description("连续两次区间加后 lazy 没有累积。")
                .skillUnitCode(skillCode)
                .mistakeType("STATE_UPDATE")
                .misconception("把区间增量当作覆盖值保存。")
                .symptom("第二次更新后答案偏小。")
                .repairStrategy("先手推两次区间加后的节点标记。")
                .severity("HIGH")
                .primaryKnowledgeNodeCode(pointCode)
                .knowledgeNodeCodes(pointCode)
                .applicableLanguages("CPP17")
                .enabled(true)
                .libraryVersion("test-fixture")
                .build());
        improvementPointRepository.saveAndFlush(AiStandardImprovementPoint.builder()
                .code(improvementCode)
                .category("自测")
                .name("连续区间更新 trace")
                .description("连续两次区间加后检查 lazy 和 max。")
                .skillUnitCode(skillCode)
                .primaryKnowledgeNodeCode(pointCode)
                .knowledgeNodeCodes(pointCode)
                .improvementGoal("把 lazy 累积过程写成可复盘表格。")
                .practiceStrategy("先手推两次区间加，再对照节点标记。")
                .studentBenefit("能更早发现 lazy 覆盖与累积混淆。")
                .enabled(true)
                .libraryVersion("test-fixture")
                .build());

        assertThat(service.expandKnowledgeNode("BASIC", 0, 100).getChildren())
                .extracting(AiStandardLibraryNavigationNodeResponse::getCode)
                .contains(chapterCode);
        assertThat(service.expandKnowledgeNode(chapterCode).getChildren())
                .extracting(AiStandardLibraryNavigationNodeResponse::getCode)
                .containsExactly(pointCode);
        AiStandardLibraryDiagnosticLayerResponse layer = service.expandDiagnosticLayer(pointCode);

        assertThat(layer.getSkillUnits()).singleElement()
                .satisfies(skill -> {
                    assertThat(skill.getCode()).isEqualTo(skillCode);
                    assertThat(skill.getMistakePoints())
                            .extracting(AiStandardLibraryDiagnosticLayerResponse.MistakePoint::getCode)
                            .containsExactly(mistakeCode);
                    assertThat(skill.getImprovementPoints())
                            .extracting(AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint::getCode)
                            .containsExactly(improvementCode);
                });
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void returnsBatchFourClosuresForBasicMathEngineeringAndContestPoints() {
        List<String> domains = List.of("BASIC", "MATH", "ENG", "CONTEST");
        knowledgeRepository.saveAllAndFlush(domains.stream()
                .map(domain -> knowledgeNode(
                        domain + ".DQ4.POINT",
                        domain,
                        InformaticsKnowledgeNodeType.KNOWLEDGE_POINT,
                        domain + " 第四批测试点",
                        domain + " / 第四批测试点",
                        90))
                .toList());

        for (String domain : domains) {
            String pointCode = domain + ".DQ4.POINT";
            String skillCode = "SK_DQ4_" + domain + "_FIXTURE";
            String mistakeCode = "MP_DQ4_" + domain + "_FIXTURE";
            String improvementCode = "IP_DQ4_" + domain + "_FIXTURE";

            skillUnitRepository.saveAndFlush(AiStandardSkillUnit.builder()
                    .code(skillCode)
                    .category("第四批导航测试")
                    .name(domain + " 第四批能力")
                    .description("验证第四批知识点可以读取规范诊断层。")
                    .learningGoal("读取同一能力下的易错点和提升点。")
                    .primaryKnowledgeNodeCode(pointCode)
                    .knowledgeNodeCodes(pointCode)
                    .masteryLevel("MEDIUM")
                    .applicableLanguages("PYTHON\nCPP17")
                    .enabled(true)
                    .libraryVersion("informatics-discipline-quality-v4")
                    .build());
            mistakePointRepository.saveAndFlush(AiStandardMistakePoint.builder()
                    .code(mistakeCode)
                    .category("第四批导航测试")
                    .name(domain + " 第四批细颗粒错误")
                    .description("描述可观察的第四批错误行为。")
                    .skillUnitCode(skillCode)
                    .mistakeType("CONTRACT")
                    .misconception("没有核对知识点合同。")
                    .symptom("边界样例出现第一处状态偏差。")
                    .repairStrategy("填写状态表并复测相邻边界。")
                    .severity("HIGH")
                    .primaryKnowledgeNodeCode(pointCode)
                    .knowledgeNodeCodes(pointCode)
                    .applicableLanguages("PYTHON\nCPP17")
                    .enabled(true)
                    .libraryVersion("informatics-discipline-quality-v4")
                    .build());
            improvementPointRepository.saveAndFlush(AiStandardImprovementPoint.builder()
                    .code(improvementCode)
                    .category("第四批导航测试")
                    .name(domain + " 第四批训练路径")
                    .description("把细颗粒错误转成可执行练习。")
                    .skillUnitCode(skillCode)
                    .primaryKnowledgeNodeCode(pointCode)
                    .knowledgeNodeCodes(pointCode)
                    .relatedMistakeCodes(mistakeCode)
                    .improvementGoal("建立可验证的知识点合同。")
                    .practiceStrategy("填写最小样例状态表并复测边界。")
                    .studentBenefit("能定位第一处偏差。")
                    .teacherExplanation("检查学生能否解释每一步状态变化。")
                    .applicableLanguages("PYTHON\nCPP17")
                    .enabled(true)
                    .libraryVersion("informatics-discipline-quality-v4")
                    .build());

            AiStandardLibraryDiagnosticLayerResponse layer = service.expandDiagnosticLayer(pointCode);
            assertThat(layer.getSkillUnits())
                    .filteredOn(skill -> skillCode.equals(skill.getCode()))
                    .singleElement()
                    .satisfies(skill -> {
                        assertThat(skill.getMistakePoints())
                                .extracting(AiStandardLibraryDiagnosticLayerResponse.MistakePoint::getCode)
                                .containsExactly(mistakeCode);
                        assertThat(skill.getImprovementPoints())
                                .extracting(AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint::getCode)
                                .containsExactly(improvementCode);
                    });
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void returnsBatchSixClosedThemePacksAcrossAllSixDomains() {
        List<String> domains = List.of("BASIC", "DS", "ALGO", "MATH", "ENG", "CONTEST");
        knowledgeRepository.saveAllAndFlush(domains.stream()
                .map(domain -> knowledgeNode(
                        domain + ".DQ6.POINT",
                        domain,
                        InformaticsKnowledgeNodeType.KNOWLEDGE_POINT,
                        domain + " 第六批测试点",
                        domain + " / 第六批测试点",
                        95))
                .toList());

        for (String domain : domains) {
            String pointCode = domain + ".DQ6.POINT";
            String skillCode = "SK_DQ6_" + domain + "_FIXTURE";
            String improvementCode = "IP_DQ6_" + domain + "_FIXTURE";
            String pairCode = "PAIR_DQ6_" + domain + "_FIXTURE";
            List<String> mistakeCodes = List.of(
                    "MP_DQ6_" + domain + "_SEMANTIC_FIXTURE",
                    "MP_DQ6_" + domain + "_STATE_FIXTURE",
                    "MP_DQ6_" + domain + "_VERIFY_FIXTURE");

            skillUnitRepository.saveAndFlush(AiStandardSkillUnit.builder()
                    .code(skillCode)
                    .category("第六批导航测试")
                    .name(domain + " 第六批能力")
                    .description("验证主能力、三类错因、改进路径和迁移场景能够成套读取。")
                    .learningGoal("能从语义、状态和验证三个角度完成复盘。")
                    .primaryKnowledgeNodeCode(pointCode)
                    .knowledgeNodeCodes(pointCode)
                    .masteryLevel("MEDIUM")
                    .applicableLanguages("PYTHON\nCPP17")
                    .enabled(true)
                    .libraryVersion("informatics-discipline-quality-v6")
                    .build());

            mistakePointRepository.saveAllAndFlush(List.of(
                    batchSixMistake(mistakeCodes.get(0), skillCode, pointCode, "SEMANTIC", "误读问题条件"),
                    batchSixMistake(mistakeCodes.get(1), skillCode, pointCode, "STATE", "状态更新顺序错位"),
                    batchSixMistake(mistakeCodes.get(2), skillCode, pointCode, "VERIFY", "没有覆盖边界反例")));

            improvementPointRepository.saveAndFlush(AiStandardImprovementPoint.builder()
                    .code(improvementCode)
                    .category("第六批导航测试")
                    .name(domain + " 第六批闭环练习")
                    .description("用一次可观察练习串起语义、状态和验证三类错因。")
                    .skillUnitCode(skillCode)
                    .primaryKnowledgeNodeCode(pointCode)
                    .knowledgeNodeCodes(pointCode)
                    .relatedMistakeCodes(String.join("\n", mistakeCodes))
                    .improvementGoal("建立从条件到状态再到反例的完整检查链。")
                    .practiceStrategy("先写最小样例状态表，再增加边界反例并定位第一处偏差。")
                    .studentBenefit("能把模糊的做错了转换成可修复的具体证据。")
                    .teacherExplanation("检查学生是否能说清三类错因的触发条件和验证方法。")
                    .applicableLanguages("PYTHON\nCPP17")
                    .enabled(true)
                    .libraryVersion("informatics-discipline-quality-v6")
                    .build());

            applicationScenarioRepository.saveAllAndFlush(List.of(
                    applicationScenario(
                            "SC_DQ6_" + domain + "_CLASSROOM_FIXTURE", pairCode, "CLASSROOM",
                            "GUIDED_PRACTICE", "课堂：" + domain + " 状态表复盘", skillCode,
                            pointCode, String.join("\n", mistakeCodes), improvementCode, 10),
                    applicationScenario(
                            "SC_DQ6_" + domain + "_CONTEST_FIXTURE", pairCode, "CONTEST",
                            "PROBLEM_READING", "竞赛：" + domain + " 边界反例核对", skillCode,
                            pointCode, String.join("\n", mistakeCodes), improvementCode, 20)));

            AiStandardLibraryDiagnosticLayerResponse layer = service.expandDiagnosticLayer(pointCode);
            assertThat(layer.getSkillUnits())
                    .filteredOn(skill -> skillCode.equals(skill.getCode()))
                    .singleElement()
                    .satisfies(skill -> {
                        assertThat(skill.getMistakePoints())
                                .extracting(AiStandardLibraryDiagnosticLayerResponse.MistakePoint::getCode)
                                .containsExactlyElementsOf(mistakeCodes);
                        assertThat(skill.getImprovementPoints())
                                .extracting(AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint::getCode)
                                .containsExactly(improvementCode);
                        assertThat(skill.getApplicationScenarios())
                                .extracting(AiStandardLibraryDiagnosticLayerResponse.ApplicationScenario::getContextType)
                                .containsExactly("CLASSROOM", "CONTEST");
                        assertThat(skill.getApplicationScenarios())
                                .allSatisfy(scenario -> assertThat(scenario.getTransferPairCode()).isEqualTo(pairCode));
                    });
        }
    }

    @Test
    void refusesDiagnosticLayerForNonKnowledgePoint() {
        assertThatThrownBy(() -> service.expandDiagnosticLayer("BASIC.IO.MULTI_CASE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("诊断层只能展开到知识点");
    }

    private InformaticsKnowledgeNode knowledgeNode(String code,
                                                   String parentCode,
                                                   InformaticsKnowledgeNodeType type,
                                                   String name,
                                                   String path,
                                                   boolean hasChildren) {
        return InformaticsKnowledgeNode.builder()
                .code(code)
                .parentCode(parentCode)
                .type(type)
                .name(name)
                .description(name)
                .path(path)
                .sortOrder(hasChildren ? -20 : -19)
                .enabled(true)
                .libraryVersion("test-fixture")
                .build();
    }

    private AiStandardApplicationScenario applicationScenario(
            String code,
            String contextType,
            String learningPhase,
            String title,
            String skillCode,
            String knowledgePointCode,
            String mistakeCode,
            String improvementCode,
            int sortOrder) {
        return applicationScenario(code, "PAIR_MULTI_CASE_FIXTURE", contextType, learningPhase, title,
                skillCode, knowledgePointCode, mistakeCode, improvementCode, sortOrder);
    }

    private AiStandardApplicationScenario applicationScenario(
            String code,
            String pairCode,
            String contextType,
            String learningPhase,
            String title,
            String skillCode,
            String knowledgePointCode,
            String mistakeCode,
            String improvementCode,
            int sortOrder) {
        return AiStandardApplicationScenario.builder()
                .code(code)
                .transferPairCode(pairCode)
                .contextType(contextType)
                .learningPhase(learningPhase)
                .title(title)
                .knowledgePointCode(knowledgePointCode)
                .skillUnitCode(skillCode)
                .linkedMistakeCodes(mistakeCode)
                .linkedImprovementCodes(improvementCode)
                .taskContext("读取两组结构不同但字段合同相同的输入，检查循环是否完整消费每一组。")
                .studentTask("画出读取游标并逐项标注每次循环消费的字段，再运行两组输入。")
                .observableEvidence("两组数据都产生对应输出，第二组读取起点与第一组结束位置连续且无状态串组。")
                .commonFailure("只处理第一组，或第二组继续使用第一组的临时状态。")
                .teacherMove("让学生指出第二组第一个 token 在代码中的读取位置并手推状态重置。")
                .studentCheck("把第二组改成最小边界后，循环次数和输出条数是否仍一致。")
                .constraintProfile("课堂使用两组最小输入；竞赛使用 T 组并包含每组边界与状态重置。")
                .successCriteria("输出条数等于 T，每组读取字段数符合题面且临时状态按组重置。")
                .transferNote("从课堂读取轨迹迁移到竞赛多测试用例，只增加组数，不改变每组输入合同。")
                .difficultyLevel("FOUNDATION")
                .applicableLanguages("PYTHON\nCPP17")
                .sourceFramework(contextType.equals("CLASSROOM")
                        ? "MOE_HIGH_SCHOOL_IT_2020"
                        : "CCF_NOI_2025")
                .sourceReference("https://example.test/official-standard")
                .reviewStatus("INFERRED_REVIEWED")
                .sortOrder(sortOrder)
                .enabled(true)
                .libraryVersion("test-fixture")
                .build();
    }

    private AiStandardMistakePoint batchSixMistake(String code,
                                                   String skillCode,
                                                   String pointCode,
                                                   String mistakeType,
                                                   String name) {
        return AiStandardMistakePoint.builder()
                .code(code)
                .category("第六批导航测试")
                .name(name)
                .description("记录可从提交轨迹中观察到的第一处偏差。")
                .skillUnitCode(skillCode)
                .mistakeType(mistakeType)
                .misconception("没有显式写出本环节应保持的条件。")
                .symptom("在最小反例中出现第一处可重现的状态偏差。")
                .repairStrategy("回到触发条件和状态表，修正后重跑原反例与相邻边界。")
                .severity("HIGH")
                .primaryKnowledgeNodeCode(pointCode)
                .knowledgeNodeCodes(pointCode)
                .applicableLanguages("PYTHON\nCPP17")
                .enabled(true)
                .libraryVersion("informatics-discipline-quality-v6")
                .build();
    }

    private InformaticsKnowledgeNode knowledgeNode(String code,
                                                   String parentCode,
                                                   InformaticsKnowledgeNodeType type,
                                                   String name,
                                                   String path,
                                                   int sortOrder) {
        return InformaticsKnowledgeNode.builder()
                .code(code)
                .parentCode(parentCode)
                .type(type)
                .name(name)
                .description(name)
                .path(path)
                .sortOrder(sortOrder)
                .enabled(true)
                .libraryVersion("test-fixture")
                .build();
    }
}
