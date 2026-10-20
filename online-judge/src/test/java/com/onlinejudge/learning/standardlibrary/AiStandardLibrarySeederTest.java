package com.onlinejudge.learning.standardlibrary;

import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeed;
import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeedCatalog;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibrarySeeder;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibrarySeed;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibrarySeedCatalog;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardLibraryItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-standard-library-seeder;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "TEACHER_PASSWORD=test-teacher-password",
        "TEACHER_SESSION_SECRET=test-teacher-session-secret-1234567890",
        "STUDENT_TOKEN_SECRET=test-student-token-secret-1234567890",
        "AI_ENABLED=false",
        "app.content-migration.enabled=true"
})
class AiStandardLibrarySeederTest {

    private static final Path GENERATED_FALLBACK_BACKUP = Path.of(
            "backups/standard-library/generated-fallback-archive-2026-07-06/generated-fallback-seeds.json");
    private static final String LEGACY_FALLBACK_SKILL_CODE = "SK_BASIC_IO_STDIN_DDADF4B0";
    private static final String LEGACY_FALLBACK_MISTAKE_CODE = "MP_BASIC_IO_STDIN_DDADF4B0";
    private static final String LEGACY_FULL_COVERAGE_BASIC_CAUSE_CODE = "KB_BASIC_BRANCH_IF_4D9D4F62";

    @Autowired
    AiStandardLibraryItemRepository repository;

    @Autowired
    AiStandardLibrarySeeder seeder;

    @Test
    void seedsSkillUnitsAndMistakePointsIdempotently() {
        long initialCount = repository.count();

        assertThat(initialCount).isGreaterThanOrEqualTo(460);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.SKILL_UNIT)
                .count()).isGreaterThanOrEqualTo(112);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .count()).isGreaterThanOrEqualTo(295);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.IMPROVEMENT_POINT)
                .count()).isGreaterThanOrEqualTo(50);
        assertThat(repository.findAll())
                .noneMatch(item -> AiStandardLibrarySeedCatalog.isGeneratedFallbackCode(item.getLayer(), item.getCode()));
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .allMatch(item -> item.getSkillUnitCode() != null && !item.getSkillUnitCode().isBlank()))
                .isTrue();
        assertThat(repository.findAll().stream()
                .filter(item -> item.getKnowledgeNodeCodes() != null
                        && item.getKnowledgeNodeCodes().contains("BASIC.LOOP.BOUNDARY.左闭右开"))
                .count()).isGreaterThanOrEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getKnowledgeNodeCodes() != null
                        && item.getKnowledgeNodeCodes().contains("ALGO.DP.STATE.状态含义"))
                .count()).isGreaterThanOrEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getKnowledgeNodeCodes() != null
                        && item.getKnowledgeNodeCodes().contains("CONTEST.READING.CONSTRAINT.数据范围"))
                .count()).isGreaterThanOrEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_LOOP_STRICT_INEQUALITY_WHEN_EQUAL_ALLOWED"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_DP_STATE_MISSING_DIMENSION"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_GRAPH_UNDIRECTED_EDGE_ADDED_ONCE"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_FUNCTION_OUTPUT_RETURN_MIXED"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_TOPO_EDGE_DIRECTION_REVERSED"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_BITMASK_INDEX_OFF_BY_ONE"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_SIM_UPDATE_ORDER_USES_STALE_STATE"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_STRING_FIND_NOT_FOUND_USED_AS_INDEX"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_ARRAY_IN_PLACE_UPDATE_OVERWRITES_SOURCE"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_READING_SAMPLE_SHAPE_OVERFITS_FORMAT"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_V6_DP_01_KNAPSACK_FORWARD_REUSES_ITEM"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_V6_DIJKSTRA_NEGATIVE_EDGE_USED"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_V6_IO_TREATED_T_AS_DATA_VALUE"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_V7_DP_COUNT_EMPTY_SCHEME_OMITTED"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_V7_BFS_MULTI_SOURCE_ENQUEUES_ONLY_ONE_START"))
                .count()).isEqualTo(1);
        assertThat(repository.findAll().stream()
                .filter(item -> item.getLayer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(item -> item.getCode().equals("MP_V7_MULTICASE_GRAPH_NOT_CLEARED_BETWEEN_CASES"))
                .count()).isEqualTo(1);

        seeder.run();

        assertThat(repository.count()).isEqualTo(initialCount);
    }

    @Test
    void generatedFallbackBackupIsStaticOnlyAndOldCodesRemainRecognizable() throws IOException {
        String backup = Files.readString(GENERATED_FALLBACK_BACKUP);

        assertThat(backup)
                .contains("\"count\": 1156")
                .contains("\"skillCount\": 578")
                .contains("\"mistakeCount\": 578")
                .contains(LEGACY_FALLBACK_SKILL_CODE)
                .contains(LEGACY_FALLBACK_MISTAKE_CODE);
        assertThat(AiStandardLibrarySeedCatalog.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("archivedGeneratedFallbackSeeds"));
        assertThat(AiStandardLibrarySeedCatalog.isGeneratedFallbackCode(
                AiStandardLibraryLayer.SKILL_UNIT, LEGACY_FALLBACK_SKILL_CODE)).isTrue();
        assertThat(AiStandardLibrarySeedCatalog.isGeneratedFallbackCode(
                AiStandardLibraryLayer.MISTAKE_POINT, LEGACY_FALLBACK_MISTAKE_CODE)).isTrue();
        assertThat(AiStandardLibrarySeedCatalog.isGeneratedFallbackCode(
                AiStandardLibraryLayer.BASIC_CAUSE, LEGACY_FULL_COVERAGE_BASIC_CAUSE_CODE)).isTrue();
        assertThat(repository.findByLayerAndCode(AiStandardLibraryLayer.SKILL_UNIT, LEGACY_FALLBACK_SKILL_CODE)).isEmpty();
        assertThat(repository.findByLayerAndCode(AiStandardLibraryLayer.MISTAKE_POINT, LEGACY_FALLBACK_MISTAKE_CODE)).isEmpty();
    }

    @Test
    @Transactional
    void disablesLegacyFullCoverageBasicCauseRowsOnStartup() {
        AiStandardLibraryItem legacy = repository.save(AiStandardLibraryItem.builder()
                .layer(AiStandardLibraryLayer.BASIC_CAUSE)
                .code(LEGACY_FULL_COVERAGE_BASIC_CAUSE_CODE)
                .category("知识点错因/程序设计基础")
                .name("单分支判断掌握偏差")
                .description("学生在「程序设计基础 / 条件分支 / if 条件 / 单分支判断」相关代码中出现概念理解、边界处理或应用迁移偏差。")
                .studentExplanation("先回到「单分支判断」这个知识点，确认题目里它对应的是输入、状态、循环、结构还是算法选择。")
                .teacherExplanation("该条目用于覆盖知识点「程序设计基础 / 条件分支 / if 条件 / 单分支判断」下的常见基础层错因，教师可根据课堂题型继续细化证据信号。")
                .abilityPoint("单分支判断")
                .knowledgeNodeCodes("BASIC.BRANCH.IF.单分支判断")
                .enabled(true)
                .libraryVersion("standard-library-db-v2-full-coverage")
                .build());

        seeder.run();

        assertThat(repository.findById(legacy.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void runtimeSourceDoesNotImportGeneratedFallbackBackup() throws IOException {
        try (var paths = Files.walk(Path.of("src/main"))) {
            List<Path> offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isUtf8TextSource)
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("backups/standard-library")
                                    || source.contains("generated-fallback-seeds.json");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }

    private boolean isUtf8TextSource(Path path) {
        String name = path == null || path.getFileName() == null
                ? ""
                : path.getFileName().toString().toLowerCase();
        return List.of(".java", ".json", ".yml", ".yaml", ".properties", ".xml",
                        ".html", ".css", ".js", ".ts", ".tsx", ".md", ".txt")
                .stream()
                .anyMatch(name::endsWith);
    }

    @Test
    void absorbedHandwrittenEntriesUseDomainSpecificEducationalLanguage() {
        AiStandardLibraryItem graphMistake = repository.findByLayerAndCode(AiStandardLibraryLayer.MISTAKE_POINT,
                "MP_GRAPH_UNDIRECTED_EDGE_ADDED_ONCE").orElseThrow();
        assertThat(graphMistake.getDescription()).contains("边");
        assertThat(graphMistake.getCommonMisconception()).contains("无向");
        assertThat(graphMistake.getSkillUnitCode()).isEqualTo("SK_GRAPH_EDGE_MODELING");
        assertThat(graphMistake.getPrimaryKnowledgeNodeCode()).isNotBlank();
    }

    @Test
    void seedsHaveValidKnowledgeAndSkillReferences() {
        Set<String> knowledgeCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());
        Set<String> skillCodes = AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .map(AiStandardLibrarySeed::code)
                .collect(Collectors.toSet());
        Set<String> allowedMistakeTypes = Set.of(
                "BOUNDARY",
                "CE",
                "COMPLEXITY",
                "CONCEPT",
                "DEBUGGING",
                "INITIALIZATION",
                "IO_FORMAT",
                "LOGIC",
                "MODELING",
                "INVARIANT",
                "ORDER",
                "RUNTIME",
                "STATE",
                "SYNTAX",
                "TRANSITION",
                "TYPE",
                "VALIDATION",
                "VALUE_RANGE");

        List<String> errors = new ArrayList<>();
        for (AiStandardLibrarySeed seed : AiStandardLibrarySeedCatalog.seeds()) {
            seed.knowledgeNodeCodes().stream()
                    .filter(code -> !knowledgeCodes.contains(code))
                    .forEach(code -> errors.add(seed.code() + " unknown knowledge node: " + code));
            seed.prerequisiteKnowledgeCodes().stream()
                    .filter(code -> !knowledgeCodes.contains(code))
                    .forEach(code -> errors.add(seed.code() + " unknown prerequisite node: " + code));
            if (seed.layer() == AiStandardLibraryLayer.SKILL_UNIT) {
                if (seed.description() == null || seed.description().isBlank()) {
                    errors.add(seed.code() + " blank skill definition");
                }
                if (seed.studentExplanation() == null || seed.studentExplanation().isBlank()) {
                    errors.add(seed.code() + " blank learning goal");
                }
                if (seed.knowledgeNodeCodes().isEmpty()) {
                    errors.add(seed.code() + " missing skill knowledge nodes");
                }
            }
            if (seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT) {
                if (!skillCodes.contains(seed.skillUnitCode())) {
                    errors.add(seed.code() + " unknown linked skill: " + seed.skillUnitCode());
                }
                if (!allowedMistakeTypes.contains(seed.mistakeType())) {
                    errors.add(seed.code() + " invalid mistake type: " + seed.mistakeType());
                }
                if (seed.commonMisconception() == null || seed.commonMisconception().isBlank()) {
                    errors.add(seed.code() + " blank misconception");
                }
                if (seed.knowledgeNodeCodes().isEmpty()) {
                    errors.add(seed.code() + " missing mistake knowledge nodes");
                }
            }
            if (seed.layer() == AiStandardLibraryLayer.IMPROVEMENT_POINT) {
                if (!skillCodes.contains(seed.skillUnitCode())) {
                    errors.add(seed.code() + " unknown linked improvement skill: " + seed.skillUnitCode());
                }
                if (seed.whenToUse() == null || seed.whenToUse().isBlank()) {
                    errors.add(seed.code() + " blank improvement usage");
                }
                if (seed.studentBenefit() == null || seed.studentBenefit().isBlank()) {
                    errors.add(seed.code() + " blank improvement benefit");
                }
                if (seed.teacherExplanation() == null || seed.teacherExplanation().isBlank()) {
                    errors.add(seed.code() + " blank improvement teacher explanation");
                }
                if (seed.knowledgeNodeCodes().isEmpty()) {
                    errors.add(seed.code() + " missing improvement knowledge nodes");
                }
            }
        }

        assertThat(errors)
                .as("standard library relation errors: " + errors.stream().limit(20).toList())
                .isEmpty();
    }

    @Test
    void seedContentQualityDoesNotRegressToGenericTemplates() {
        long activeSkillCount = AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .count();
        long genericSkillNameCount = AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.SKILL_UNIT)
                .filter(seed -> seed.name().startsWith("掌握"))
                .count();
        long genericMistakeNameCount = AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(seed -> seed.name().contains("理解或应用偏差"))
                .count();
        long strongHandwrittenSamples = AiStandardLibrarySeedCatalog.seeds().stream()
                .filter(seed -> seed.layer() == AiStandardLibraryLayer.MISTAKE_POINT)
                .filter(seed -> seed.code().startsWith("MP_FUNCTION_")
                        || seed.code().startsWith("MP_BACKTRACK_")
                        || seed.code().startsWith("MP_MATRIX_")
                        || seed.code().startsWith("MP_SORT_")
                        || seed.code().startsWith("MP_UNION_FIND_")
                        || seed.code().startsWith("MP_TOPO_")
                        || seed.code().startsWith("MP_BIT")
                        || seed.code().startsWith("MP_READING_")
                        || seed.code().startsWith("MP_SIM_")
                        || seed.code().startsWith("MP_STRING_")
                        || seed.code().startsWith("MP_ARRAY_")
                        || seed.code().startsWith("MP_V6_")
                        || seed.code().startsWith("MP_V7_")
                        || seed.code().startsWith("MP_V8_")
                        || seed.code().startsWith("MP_V9_")
                        || seed.code().startsWith("MP_V10_")
                        || seed.code().startsWith("MP_V11_")
                        || seed.code().startsWith("MP_V12_")
                        || seed.code().startsWith("MP_V13_"))
                .count();

        assertThat(activeSkillCount).isGreaterThanOrEqualTo(112);
        assertThat(AiStandardLibrarySeedCatalog.seeds())
                .noneMatch(AiStandardLibrarySeedCatalog::isGeneratedFallbackSeed);
        assertThat(genericSkillNameCount).isZero();
        assertThat(genericMistakeNameCount).isZero();
        assertThat(strongHandwrittenSamples).isGreaterThanOrEqualTo(190);
    }

    @Test
    void representativeV4SamplesArePreciseAndTeacherReadable() {
        Map<String, AiStandardLibrarySeed> seedsByCode = AiStandardLibrarySeedCatalog.seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode.get("MP_FUNCTION_OUTPUT_RETURN_MIXED").description())
                .contains("print").contains("返回值");
        assertThat(seedsByCode.get("MP_MATRIX_ROW_COL_SWAPPED").commonMisconception())
                .contains("x/y").contains("row/col");
        assertThat(seedsByCode.get("MP_UNION_FIND_PARENT_WITHOUT_FIND").description())
                .contains("parent").contains("find");
        assertThat(seedsByCode.get("MP_TOPO_CYCLE_NOT_CHECKED").description())
                .contains("处理点数").contains("总点数");
        assertThat(seedsByCode.get("MP_READING_OBJECTIVE_MISIDENTIFIED").description())
                .contains("最值").contains("路径");
        assertThat(seedsByCode.get("MP_SIM_MULTI_OBJECT_STATE_DESYNC").description())
                .contains("多个对象").contains("只更新其中一部分");
        assertThat(seedsByCode.get("MP_STRING_FIND_NOT_FOUND_USED_AS_INDEX").description())
                .contains("没有命中").contains("有效位置");
        assertThat(seedsByCode.get("MP_ARRAY_IN_PLACE_UPDATE_OVERWRITES_SOURCE").description())
                .contains("原数组旧值").contains("本轮新状态");
        assertThat(seedsByCode.get("MP_READING_SAMPLE_SHAPE_OVERFITS_FORMAT").description())
                .contains("样例").contains("隐藏数据");
        assertThat(seedsByCode.get("MP_READING_TIE_RULE_IGNORED").description())
                .contains("并列").contains("输出顺序");
        assertThat(seedsByCode.get("MP_V6_BINARY_LOWER_UPPER_BOUND_MIXED").description())
                .contains("大于等于").contains("大于");
        assertThat(seedsByCode.get("MP_V6_DIFF_RIGHT_PLUS_ONE_MISSING").description())
                .contains("r+1").contains("后续所有位置");
        assertThat(seedsByCode.get("MP_V6_FUNCTION_MISSING_RETURN_ON_BRANCH").description())
                .contains("部分条件").contains("其他合法分支");
        assertThat(seedsByCode.get("MP_V7_DP_COUNT_EMPTY_SCHEME_OMITTED").description())
                .contains("空方案").contains("初始化为 1");
        assertThat(seedsByCode.get("MP_V7_LONG_LONG_CAST_AFTER_MULTIPLICATION").description())
                .contains("int").contains("long long");
        assertThat(seedsByCode.get("MP_V7_MULTICASE_GRAPH_NOT_CLEARED_BETWEEN_CASES").description())
                .contains("邻接表").contains("旧边");
        assertThat(seedsByCode.get("MP_V8_GRAPH_MULTICASE_ADJACENCY_NOT_CLEARED").description())
                .contains("邻接表").contains("上一组");
        assertThat(seedsByCode.get("MP_V8_SUBMIT_OVERFLOW_CHECK_ONLY_ON_FINAL_ANSWER").description())
                .contains("long long").contains("中间表达式");
        assertThat(seedsByCode.get("IP_V8_ARRAY_UPDATE_INVARIANT_TESTING").studentBenefit())
                .contains("旧值").contains("累计量");
    }

    @Test
    void upgradedRepresentativeEntriesCarrySpecificTeachingSemantics() {
        Map<String, AiStandardLibrarySeed> seedsByCode = AiStandardLibrarySeedCatalog.seeds().stream()
                .collect(Collectors.toMap(AiStandardLibrarySeed::code, Function.identity(), (left, right) -> left));

        assertThat(seedsByCode.get("SK_LOOP_ENDPOINT_INCLUSION").studentExplanation())
                .contains("合法取值集合").contains("等于边界");
        assertThat(seedsByCode.get("MP_LOOP_STRICT_INEQUALITY_WHEN_EQUAL_ALLOWED").commonMisconception())
                .contains("等于边界").contains("代码路径");
        assertThat(seedsByCode.get("SK_IO_STRUCTURE_MAPPING").studentExplanation())
                .contains("输入层级").contains("输出次数");
        assertThat(seedsByCode.get("MP_IO_ONLY_READS_ONE_CASE").description())
                .contains("T").contains("后续组").contains("没有对应输出");
        assertThat(seedsByCode.get("SK_DP_STATE_MEANING").description())
                .contains("下标范围").contains("转移来源").contains("答案读取位置");
        assertThat(seedsByCode.get("MP_DP_STATE_MISSING_DIMENSION").commonMisconception())
                .contains("容量").contains("方向").contains("特殊操作");
        assertThat(seedsByCode.get("MP_DP_INIT_UNREACHABLE_AS_ZERO").commonMisconception())
                .contains("真实答案为 0").contains("尚不可达");
        assertThat(seedsByCode.get("SK_SEARCH_VISITED_STATE").studentExplanation())
                .contains("状态元组").contains("visited");
        assertThat(seedsByCode.get("MP_SEARCH_VISITED_DIMENSION_INCOMPLETE").description())
                .contains("钥匙").contains("剩余资源").contains("时间层级");
        assertThat(seedsByCode.get("SK_COMPLEXITY_CONSTRAINT_READING").description())
                .contains("O(n^2)").contains("O(qn)").contains("指数搜索");
        assertThat(seedsByCode.get("MP_COMPLEXITY_IGNORES_MAX_CONSTRAINTS").description())
                .contains("最大 n").contains("嵌套循环").contains("时间限制");
        assertThat(seedsByCode.get("SK_STRING_SEARCH_AND_BUILD").studentExplanation())
                .contains("找不到").contains("重叠出现").contains("末尾匹配");
        assertThat(seedsByCode.get("MP_STRING_FIND_NOT_FOUND_USED_AS_INDEX").description())
                .contains("失败分支").contains("切片").contains("下标访问");
        assertThat(seedsByCode.get("MP_STRING_OVERLAPPING_MATCH_SKIPPED").description())
                .contains("匹配起点").contains("重叠匹配");
        assertThat(seedsByCode.get("SK_ARRAY_TRAVERSAL_UPDATE_SAFETY").studentExplanation())
                .contains("read-from").contains("write-to");
        assertThat(seedsByCode.get("MP_ARRAY_IN_PLACE_UPDATE_OVERWRITES_SOURCE").commonMisconception())
                .contains("新状态").contains("同一轮");
        assertThat(seedsByCode.get("MP_ARRAY_PARALLEL_TRAVERSE_MISALIGNED").description())
                .contains("不同属性").contains("对象错配");
        assertThat(seedsByCode.get("SK_GRAPH_EDGE_MODELING").studentExplanation())
                .contains("点是什么").contains("边表示什么").contains("权值");
        assertThat(seedsByCode.get("MP_GRAPH_WEIGHT_MEANING_IGNORED").description())
                .contains("松弛条件").contains("比较函数");
        assertThat(seedsByCode.get("SK_DEBUG_MINIMAL_COUNTEREXAMPLE").description())
                .contains("关键变量表").contains("第一处偏差");
        assertThat(seedsByCode.get("MP_DEBUG_ONLY_RETESTS_SAMPLE").commonMisconception())
                .contains("正确性证明").contains("隐藏数据");
        assertThat(seedsByCode.get("SK_READING_OBJECTIVE_CONSTRAINT").description())
                .contains("并列规则").contains("算法选择依据");
        assertThat(seedsByCode.get("MP_READING_OBJECTIVE_MISIDENTIFIED").commonMisconception())
                .contains("题面动词").contains("方案数");

        assertThat(seedsByCode.get("MP_GRAPH_WEIGHT_MEANING_IGNORED").skillUnitCode())
                .isEqualTo("SK_GRAPH_EDGE_MODELING");
        assertThat(seedsByCode.get("MP_DP_STATE_MISSING_DIMENSION").knowledgeNodeCodes())
                .contains("ALGO.DP.STATE.维度选择");
    }

    @Test
    void generatedFallbackArchiveIsRemovedFromRuntimeSeeds() {
        assertThat(AiStandardLibrarySeedCatalog.seeds())
                .noneMatch(AiStandardLibrarySeedCatalog::isGeneratedFallbackSeed);
        assertThat(AiStandardLibrarySeedCatalog.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("archivedGeneratedFallbackSeeds"));
    }
}
