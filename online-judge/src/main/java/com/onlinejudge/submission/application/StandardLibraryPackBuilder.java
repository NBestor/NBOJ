package com.onlinejudge.submission.application;

import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StandardLibraryPackBuilder {

    private final DiagnosisTaxonomy diagnosisTaxonomy;
    private final AiStandardLibraryService aiStandardLibraryService;

    public StandardLibraryPackBuilder(DiagnosisTaxonomy diagnosisTaxonomy) {
        this(diagnosisTaxonomy, null);
    }

    @Autowired
    public StandardLibraryPackBuilder(DiagnosisTaxonomy diagnosisTaxonomy,
                                      AiStandardLibraryService aiStandardLibraryService) {
        this.diagnosisTaxonomy = diagnosisTaxonomy;
        this.aiStandardLibraryService = aiStandardLibraryService;
    }

    public StandardLibraryPack build(ModelDiagnosisBrief brief) {
        Set<String> issueTagIds = new LinkedHashSet<>();
        Set<String> fineTagIds = new LinkedHashSet<>();

        if (brief != null && brief.getAllowedIssueTags() != null) {
            issueTagIds.addAll(brief.getAllowedIssueTags());
        }
        if (brief != null && brief.getAllowedFineGrainedTags() != null) {
            fineTagIds.addAll(brief.getAllowedFineGrainedTags());
        }

        issueTagIds.add("NEEDS_MORE_EVIDENCE");

        List<StandardLibraryPack.TagOption> issueTags = issueTagIds.stream()
                .map(diagnosisTaxonomy::get)
                .filter(tag -> tag != null && !tag.isFineGrained())
                .map(this::toTagOption)
                .toList();
        List<StandardLibraryPack.TagOption> fineTags = fineTagIds.stream()
                .map(diagnosisTaxonomy::get)
                .filter(tag -> tag != null && tag.isFineGrained())
                .map(this::toTagOption)
                .toList();

        LinkedHashSet<String> selectedIssueIds = new LinkedHashSet<>(issueTags.stream()
                .map(StandardLibraryPack.TagOption::getId)
                .toList());
        LinkedHashSet<String> selectedFineIds = new LinkedHashSet<>(fineTags.stream()
                .map(StandardLibraryPack.TagOption::getId)
                .toList());

        List<StandardLibraryPack.BasicCauseOption> basicCauses = selectBasicCauses(selectedIssueIds, selectedFineIds);
        List<StandardLibraryPack.ImprovementPointOption> improvementPoints = selectImprovementPoints(selectedIssueIds, selectedFineIds);

        LinkedHashSet<String> teachingActionIds = new LinkedHashSet<>();
        issueTags.stream()
                .map(StandardLibraryPack.TagOption::getTeachingAction)
                .filter(this::present)
                .forEach(teachingActionIds::add);
        fineTags.stream()
                .map(StandardLibraryPack.TagOption::getTeachingAction)
                .filter(this::present)
                .forEach(teachingActionIds::add);
        basicCauses.stream()
                .map(StandardLibraryPack.BasicCauseOption::getTeachingAction)
                .filter(this::present)
                .forEach(teachingActionIds::add);

        return StandardLibraryPack.builder()
                .schemaVersion(StandardLibraryPack.SCHEMA_VERSION)
                .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                .structureVersion(StandardLibraryPack.STRUCTURE_VERSION)
                .basicCauses(basicCauses)
                .improvementPoints(improvementPoints)
                .applicationScenarios(List.of())
                .issueTags(issueTags)
                .fineGrainedTags(fineTags)
                .improvementTags(buildImprovementTags(improvementPoints))
                .teachingActions(teachingActionIds.stream().map(this::toTeachingActionOption).toList())
                .build();
    }

    private List<StandardLibraryPack.BasicCauseOption> selectBasicCauses(Set<String> issueTagIds,
                                                                         Set<String> fineTagIds) {
        Map<String, StandardLibraryPack.BasicCauseOption> library = activeBasicCauseLibrary();
        LinkedHashMap<String, StandardLibraryPack.BasicCauseOption> selected = new LinkedHashMap<>();
        for (String issueTag : issueTagIds) {
            StandardLibraryPack.BasicCauseOption option = library.get(issueTag);
            if (option != null) {
                selected.put(option.getId(), option);
            }
        }
        for (String fineTag : fineTagIds) {
            StandardLibraryPack.BasicCauseOption option = library.get(fineTag);
            if (option != null) {
                selected.put(option.getId(), option);
            }
        }
        selected.putIfAbsent("NEEDS_MORE_EVIDENCE", library.get("NEEDS_MORE_EVIDENCE"));
        return selected.values().stream().filter(item -> item != null).toList();
    }

    private List<StandardLibraryPack.ImprovementPointOption> selectImprovementPoints(Set<String> issueTagIds,
                                                                                     Set<String> fineTagIds) {
        Map<String, StandardLibraryPack.ImprovementPointOption> library = activeImprovementPointLibrary();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (issueTagIds.contains("TIME_COMPLEXITY") || fineTagIds.contains("OVER_SIMULATION")
                || fineTagIds.contains("BRUTE_FORCE_LIMIT") || fineTagIds.contains("MAX_BOUNDARY")) {
            ids.add("COMPLEXITY");
            ids.add("ALGORITHM_MODELING");
        }
        if (issueTagIds.contains("DATA_STRUCTURE_CHOICE") || issueTagIds.contains("SPACE_COMPLEXITY")) {
            ids.add("DATA_STRUCTURE_FIT");
        }
        if (issueTagIds.contains("ALGORITHM_STRATEGY") || fineTagIds.contains("GREEDY_ASSUMPTION")) {
            ids.add("ALGORITHM_MODELING");
            ids.add("GREEDY_PROOF");
        }
        if (issueTagIds.contains("STATE_TRANSITION") || fineTagIds.contains("DP_STATE_DESIGN")) {
            ids.add("DP_STATE_DESIGN");
        }
        if (issueTagIds.contains("BOUNDARY_CONDITION") || issueTagIds.contains("LOOP_BOUNDARY")
                || fineTagIds.contains("OFF_BY_ONE") || fineTagIds.contains("EMPTY_INPUT")
                || fineTagIds.contains("DUPLICATE_CASE")) {
            ids.add("TESTING_HABIT");
            ids.add("BOUNDARY_AWARENESS");
        }
        if (issueTagIds.contains("IO_FORMAT") || fineTagIds.contains("INPUT_PARSING")
                || fineTagIds.contains("OUTPUT_FORMAT_DETAIL")) {
            ids.add("TESTING_HABIT");
            ids.add("CODE_ORGANIZATION");
        }
        if (issueTagIds.contains("SAMPLE_ONLY") || fineTagIds.contains("SAMPLE_OVERFIT")) {
            ids.add("TESTING_HABIT");
            ids.add("TRANSFER_REVIEW");
        }
        if (issueTagIds.contains("CODE_READABILITY") || issueTagIds.contains("CODE_QUALITY")) {
            ids.add("CODE_ORGANIZATION");
            ids.add("TRANSFER_REVIEW");
        }
        if (ids.isEmpty()) {
            ids.add("TESTING_HABIT");
            ids.add("TRANSFER_REVIEW");
        }
        return ids.stream().map(library::get).filter(item -> item != null).toList();
    }

    private Map<String, StandardLibraryPack.BasicCauseOption> activeBasicCauseLibrary() {
        Map<String, StandardLibraryPack.BasicCauseOption> fallback = basicCauseLibrary();
        if (aiStandardLibraryService == null) {
            return fallback;
        }
        List<StandardLibraryPack.BasicCauseOption> databaseItems = aiStandardLibraryService.enabledBasicCauses();
        if (databaseItems.isEmpty()) {
            return fallback;
        }
        LinkedHashMap<String, StandardLibraryPack.BasicCauseOption> map = new LinkedHashMap<>();
        databaseItems.forEach(item -> map.put(item.getId(), item));
        map.putIfAbsent("NEEDS_MORE_EVIDENCE", fallback.get("NEEDS_MORE_EVIDENCE"));
        return map;
    }

    private Map<String, StandardLibraryPack.ImprovementPointOption> activeImprovementPointLibrary() {
        Map<String, StandardLibraryPack.ImprovementPointOption> fallback = improvementPointLibrary();
        if (aiStandardLibraryService == null) {
            return fallback;
        }
        List<StandardLibraryPack.ImprovementPointOption> databaseItems = aiStandardLibraryService.enabledImprovementPoints();
        if (databaseItems.isEmpty()) {
            return fallback;
        }
        LinkedHashMap<String, StandardLibraryPack.ImprovementPointOption> map = new LinkedHashMap<>();
        databaseItems.forEach(item -> map.put(item.getId(), item));
        map.putIfAbsent("TESTING_HABIT", fallback.get("TESTING_HABIT"));
        map.putIfAbsent("TRANSFER_REVIEW", fallback.get("TRANSFER_REVIEW"));
        return map;
    }

    private Map<String, StandardLibraryPack.BasicCauseOption> basicCauseLibrary() {
        LinkedHashMap<String, StandardLibraryPack.BasicCauseOption> map = new LinkedHashMap<>();
        addBasic(map, basic("SYNTAX_ERROR", "语法与编译", "语法/编译错误",
                "程序还没有通过语言语法或编译器检查。",
                "当前先让程序能被解释器或编译器接受。",
                "学生处在基础可运行阶段，应优先处理第一条编译/语法错误。",
                List.of("compiler error", "syntax error", "missing symbol", "type mismatch"),
                List.of("括号不配对", "变量未声明", "缩进错误", "函数签名不匹配"),
                List.of("CE", "COMPILE_ERROR"),
                "先看第一条报错，不要从后面的连锁报错开始。",
                "圈出报错行附近用到的符号、括号和缩进。",
                "只修改一个最小位置后重新提交，观察第一条报错是否变化。",
                "语法与运行环境", "HIGH", List.of("PYTHON", "CPP17"), List.of(), "FIX_FIRST_COMPILER_ERROR"));
        addBasic(map, basic("RUNTIME_STABILITY", "运行时", "运行时稳定性",
                "代码运行中触发越界、空引用、除零或递归过深等异常。",
                "当前先定位程序在哪类输入下中断。",
                "学生需要学会从异常信息和极小输入倒推状态。",
                List.of("runtime error", "index out of range", "division by zero", "segmentation fault"),
                List.of("数组下标未检查", "空容器取值", "递归无出口", "除数来自输入"),
                List.of("RE", "RUNTIME_ERROR"),
                "先确认程序是算错了，还是运行中断了。",
                "用最小输入追踪每个关键变量进入危险操作前的值。",
                "检查访问数组、取模除法、递归调用前是否满足题意条件。",
                "程序稳定性", "HIGH", List.of("PYTHON", "CPP17"), List.of("EMPTY_INPUT"), "CHECK_RUNTIME_GUARDS"));
        addBasic(map, basic("IO_FORMAT", "输入输出", "输入输出格式",
                "读入结构或输出格式与题面要求不一致。",
                "当前先核对题面要求的输入/输出单位。",
                "适合让学生逐项比较题面格式、实际读取和实际输出。",
                List.of("wrong output shape", "missing output line", "extra output", "input format"),
                List.of("只读一组数据", "输出多余调试内容", "换行或空格不一致", "未读取查询次数"),
                List.of("WA", "VISIBLE_OUTPUT_MISMATCH"),
                "先不要改算法，先核对输入输出格式。",
                "数一数题面要求读几组、输出几行，和代码实际行为比较。",
                "用一个最小样例手动写出应读内容和应输出内容，再对照代码。",
                "题意读取", "HIGH", List.of("PYTHON", "CPP17"),
                List.of("INPUT_PARSING", "OUTPUT_FORMAT_DETAIL"), "COMPARE_INPUT_SPEC"));
        addBasic(map, basic("INPUT_PARSING", "输入输出", "输入读取理解",
                "题面输入结构、多组数据或多次查询的读取方式理解错。",
                "当前先看代码是否完整消费了题面给出的输入。",
                "常见于多组数据、q 次查询、矩阵/列表长度不匹配等问题。",
                List.of("candidate input parsing", "remaining input", "fewer output lines"),
                List.of("没有按查询次数处理", "循环次数和输入组数不一致", "嵌套数据只读一层"),
                List.of("WA", "VISIBLE_OUTPUT_MISMATCH"),
                "先关注读入次数，不急着改计算逻辑。",
                "数一数题面有几段输入，代码实际读取了几段。",
                "构造两组以上输入，观察输出次数是否和题面一致。",
                "题意读取", "HIGH", List.of("PYTHON", "CPP17"), List.of(), "COMPARE_INPUT_SPEC"));
        addBasic(map, basic("OUTPUT_FORMAT_DETAIL", "输入输出", "输出格式细节",
                "换行、空格、大小写、多余字符或调试输出影响判题。",
                "当前先逐字符比较实际输出与期望输出。",
                "适合在算法证据不足时优先排除格式问题。",
                List.of("whitespace mismatch", "case mismatch", "extra debug output"),
                List.of("print 调试变量", "末尾多空格", "大小写与题面不一致", "输出顺序不一致"),
                List.of("WA", "PRESENTATION_ERROR"),
                "先逐字符比较输出，不急着换算法。",
                "把期望输出和实际输出按行对齐，看多了什么或少了什么。",
                "删除调试输出后，用最小样例复核换行和空格。",
                "题意读取", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "COMPARE_OUTPUT"));
        addBasic(map, basic("VARIABLE_INITIALIZATION", "变量与状态", "变量初始化/状态重置",
                "变量初值或多轮处理之间的状态重置不符合题意。",
                "当前先检查变量在第一次使用前和每轮循环前的状态。",
                "适合处理累计变量、标志变量、多组数据残留等问题。",
                List.of("state not reset", "unexpected carry-over", "wrong initial state"),
                List.of("累加器未清零", "flag 沿用上一轮", "列表复用", "默认值不符合题意"),
                List.of("WA"),
                "先看变量第一次参与判断前是什么值。",
                "追踪一轮结束后，下一轮开始前哪些状态还保留着。",
                "用两组连续数据测试，观察第二组是否受到第一组影响。",
                "状态维护", "MEDIUM", List.of("PYTHON", "CPP17"), List.of("INITIAL_STATE", "STATE_RESET"), "TRACE_STATE"));
        addBasic(map, basic("INITIAL_STATE", "变量与状态", "初始状态",
                "初始变量、初始集合或初始答案没有覆盖题意的起点。",
                "当前先核对题目中“开始时”的状态。",
                "常见于最值初始化、DP 起点、空集/单元素起点。",
                List.of("initial state", "minimum case mismatch"),
                List.of("最值初始为 0", "DP 初值遗漏", "起点元素未计入"),
                List.of("WA"),
                "先找题目里最开始的状态。",
                "用最小输入手推初值应该是什么。",
                "检查循环开始前的变量是否已经表达了题意起点。",
                "状态维护", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "TRACE_STATE"));
        addBasic(map, basic("STATE_RESET", "变量与状态", "状态重置",
                "多组数据、多轮循环或重复尝试之间状态没有清空。",
                "当前先检查每轮开始前状态是否干净。",
                "适合处理多 case 累计、缓存残留、数组复用等问题。",
                List.of("state reset", "multi-case carry-over"),
                List.of("全局数组未重置", "容器未 clear", "计数器跨 case 累加"),
                List.of("WA"),
                "先看第二组数据是否被第一组影响。",
                "追踪每轮开始前所有关键状态。",
                "构造两组差异很大的输入，检查后一组结果是否异常。",
                "状态维护", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "TRACE_STATE"));
        addBasic(map, basic("CONDITION_BRANCH", "条件分支", "条件分支覆盖",
                "判断条件、分支顺序或互斥关系没有覆盖所有情况。",
                "当前先列出题目要求区分的情况。",
                "学生需要把自然语言条件转成完整的分支表。",
                List.of("branch condition", "missing branch", "wrong branch order"),
                List.of("if/else 顺序错误", "边界值落入错误分支", "条件有重叠或遗漏"),
                List.of("WA"),
                "先列题目中所有情况，不急着改条件。",
                "给每个分支写一个能进入它的最小例子。",
                "检查边界值是否进入了你预期的分支。",
                "条件分支推理", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "CHECK_BRANCH_COVERAGE"));
        addBasic(map, basic("LOOP_BOUNDARY", "循环与边界", "循环边界",
                "循环起点、终点、步长或退出条件少处理/多处理元素。",
                "当前先追踪循环变量经过了哪些值。",
                "常见于 range 终点、数组长度、二分边界和 while 退出条件。",
                List.of("loop boundary", "off by one", "missed element"),
                List.of("少处理第一个/最后一个元素", "while 条件过早结束", "二分区间不收缩"),
                List.of("WA", "TLE"),
                "先看循环到底跑了哪些下标。",
                "用 1 个和 2 个元素的样例列出循环变量表。",
                "核对每次循环后区间或下标是否更接近结束。",
                "循环与边界", "HIGH", List.of("PYTHON", "CPP17"), List.of("OFF_BY_ONE"), "TRACE_VARIABLES"));
        addBasic(map, basic("OFF_BY_ONE", "循环与边界", "差一位错误",
                "索引、计数或区间端点多算/少算一个位置。",
                "当前先用极小规模检查端点是否被处理。",
                "适合处理数组、字符串、排名、天数、闭区间/开区间。",
                List.of("off by one", "index one-based zero-based"),
                List.of("0/1 编号混用", "range 右端点误解", "长度和最后下标混淆"),
                List.of("WA", "RE"),
                "先怀疑端点，不要先怀疑整体算法。",
                "把编号体系写清楚：题面从 1 还是从 0 开始。",
                "用长度为 1、2 的输入手推每个下标是否被处理一次。",
                "循环与边界", "HIGH", List.of("PYTHON", "CPP17"), List.of(), "TRACE_VARIABLES"));
        addBasic(map, basic("BOUNDARY_CONDITION", "边界条件", "边界条件",
                "极小、极大、空值、重复值或特殊输入没有被正确处理。",
                "当前先构造最小/最大/特殊输入。",
                "学生需要形成可复用的边界检查清单。",
                List.of("boundary", "minimum input", "maximum input", "duplicate values"),
                List.of("空列表未处理", "单元素逻辑不同", "重复元素判断失效", "最大值溢出"),
                List.of("WA", "RE", "TLE"),
                "先找最小、最大和特殊重复情况。",
                "挑一个最小边界样例，手推代码每一步。",
                "把样例、最小值、最大值、重复值各测一次。",
                "边界条件意识", "MEDIUM", List.of("PYTHON", "CPP17"),
                List.of("EMPTY_INPUT", "MAX_BOUNDARY", "DUPLICATE_CASE"), "ASK_MIN_CASE"));
        addBasic(map, basic("EMPTY_INPUT", "边界条件", "极小输入",
                "空、单元素、最小规模输入没有被覆盖。",
                "当前先只用题目允许的最小输入测试。",
                "适合排查初始化、循环是否进入、数组访问等问题。",
                List.of("empty input", "single element", "minimum input"),
                List.of("默认至少两个元素", "空容器取值", "循环完全不进入"),
                List.of("WA", "RE"),
                "先用题目允许的最小输入。",
                "追踪每个函数在最小输入下收到什么、返回什么。",
                "检查没有进入循环时答案是否仍符合题意。",
                "边界条件意识", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "ASK_MIN_CASE"));
        addBasic(map, basic("MAX_BOUNDARY", "边界条件", "最大规模边界",
                "最大输入规模下出现超时、溢出或内存压力。",
                "当前先估算最大规模操作次数和数据范围。",
                "适合作为从样例正确走向规模正确的关键训练。",
                List.of("large constraints", "max boundary", "timeout", "overflow"),
                List.of("嵌套枚举全范围", "int 溢出", "保存过多中间状态"),
                List.of("TLE", "MLE", "WA"),
                "先看最大 n 可能有多大。",
                "估算核心循环在最大输入下会执行多少次。",
                "把变量最大值范围写出来，检查类型和空间是否承受得住。",
                "算法复杂度", "HIGH", List.of("PYTHON", "CPP17"), List.of(), "COUNT_COMPLEXITY"));
        addBasic(map, basic("DUPLICATE_CASE", "边界条件", "重复元素场景",
                "重复值、重复状态或相等边界破坏当前判断。",
                "当前先构造包含重复元素的最小反例。",
                "常见于排序、去重、计数、贪心和二分边界。",
                List.of("duplicate values", "equal boundary"),
                List.of("相等时分支遗漏", "set 去重破坏计数", "重复元素顺序影响结果"),
                List.of("WA"),
                "先测一组有重复值的输入。",
                "观察相等时走的是哪个分支。",
                "构造所有元素相同或只差一个元素的反例。",
                "迁移泛化", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "ASK_MIN_CASE"));
        addBasic(map, basic("DATA_STRUCTURE_CHOICE", "数据结构", "数据结构选择",
                "当前容器或组织方式不适合题目操作和规模。",
                "当前先列出题目需要支持的核心操作。",
                "适合引导学生比较查找、插入、删除、区间查询等成本。",
                List.of("data structure", "lookup cost", "memory layout"),
                List.of("频繁线性查找", "需要按序却用无序结构", "重复扫描列表"),
                List.of("TLE", "MLE"),
                "先列题目真正频繁的操作。",
                "估算当前容器完成这些操作的代价。",
                "比较是否需要支持快速查找、排序或区间信息。",
                "数据结构", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "COMPARE_STRUCTURES"));
        addBasic(map, basic("TIME_COMPLEXITY", "复杂度", "时间复杂度",
                "算法执行次数随输入规模增长过快。",
                "当前先估算最大输入下核心循环次数。",
                "这是信息竞赛中从会写到能过大数据的关键能力。",
                List.of("time limit", "large n", "nested loop", "operation count"),
                List.of("双重/三重循环枚举", "重复排序", "每次查询全量扫描"),
                List.of("TLE"),
                "先估算次数，不急着找技巧。",
                "把核心循环次数写成 n、m 或 q 的表达。",
                "代入最大数据范围，判断是否明显超过可接受数量级。",
                "算法复杂度", "HIGH", List.of("PYTHON", "CPP17"),
                List.of("OVER_SIMULATION", "BRUTE_FORCE_LIMIT"), "COUNT_COMPLEXITY"));
        addBasic(map, basic("OVER_SIMULATION", "复杂度", "过度模拟",
                "代码逐步模拟过多细节，导致规模上不可承受。",
                "当前先看是否每一步都必须真的执行。",
                "适合引导学生从模拟走向规律、预处理或状态压缩。",
                List.of("step simulation", "large range", "repeated process"),
                List.of("按时间一秒一秒模拟", "按区间每个点更新", "重复做相同计算"),
                List.of("TLE"),
                "先问：每一步都必须模拟吗？",
                "找出重复发生、结果可合并的部分。",
                "用小规模手推，观察是否存在可直接维护的状态或规律。",
                "算法设计", "HIGH", List.of("PYTHON", "CPP17"), List.of(), "COUNT_COMPLEXITY"));
        addBasic(map, basic("BRUTE_FORCE_LIMIT", "复杂度", "暴力规模瓶颈",
                "暴力枚举在最大规模下无法通过。",
                "当前先确认暴力枚举的对象和层数。",
                "适合训练学生从枚举范围、剪枝和等价状态入手优化。",
                List.of("brute force", "enumeration limit", "timeout"),
                List.of("枚举所有对/三元组", "每次重新扫描", "无剪枝搜索"),
                List.of("TLE"),
                "先数枚举了多少种可能。",
                "估算最坏情况下枚举总量。",
                "找一找哪些枚举结果可以提前排除或复用。",
                "算法复杂度", "HIGH", List.of("PYTHON", "CPP17"), List.of(), "COUNT_COMPLEXITY"));
        addBasic(map, basic("SPACE_COMPLEXITY", "复杂度", "空间复杂度",
                "保存的数据或中间状态过多，超过内存或不必要。",
                "当前先估算最大输入下会开多少空间。",
                "适合处理大数组、二维 DP、缓存全量结果等问题。",
                List.of("memory limit", "large array", "space complexity"),
                List.of("二维数组过大", "保存所有历史状态", "复制大容器"),
                List.of("MLE", "RE"),
                "先估算开了多少个元素。",
                "把数组维度和最大范围相乘。",
                "检查是否只需要上一层、局部窗口或统计量。",
                "空间管理", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "COMPARE_STRUCTURES"));
        addBasic(map, basic("ALGORITHM_STRATEGY", "算法策略", "算法策略",
                "当前思路没有匹配题目结构或规模。",
                "当前先判断题目属于哪类模型。",
                "适合在基础错误排除后，引导学生比较策略而非直接给解法。",
                List.of("algorithm strategy", "wrong model", "hidden failure"),
                List.of("样例能过但一般情况不成立", "局部规则代替全局目标", "没有利用单调性/结构"),
                List.of("WA", "TLE"),
                "先描述你现在的思路依赖什么假设。",
                "找一个不满足这个假设的小反例。",
                "比较题目目标更像排序、搜索、DP、贪心还是数学规律。",
                "算法设计", "MEDIUM", List.of("PYTHON", "CPP17"),
                List.of("GREEDY_ASSUMPTION", "DP_STATE_DESIGN"), "CHECK_INVARIANT"));
        addBasic(map, basic("GREEDY_ASSUMPTION", "算法策略", "贪心依据不足",
                "当前贪心选择缺少可验证依据或存在反例。",
                "当前先验证每一步局部选择是否不影响全局最优。",
                "适合训练交换论证、反例构造和不变量意识。",
                List.of("greedy", "local choice", "counterexample"),
                List.of("按当前最大/最小直接选", "排序后单向决策", "没有证明选择不可逆"),
                List.of("WA"),
                "先不要相信局部最优一定全局最优。",
                "构造一个局部最优看起来好但全局更差的例子。",
                "说明每次选择后，剩余问题为什么仍等价或更优。",
                "算法设计", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "CHECK_INVARIANT"));
        addBasic(map, basic("STATE_TRANSITION", "状态建模", "状态转移",
                "状态更新关系、顺序或覆盖信息不完整。",
                "当前先用自然语言定义每个状态代表什么。",
                "适合 DP、递推、模拟和原地更新类问题。",
                List.of("state transition", "recurrence", "update order"),
                List.of("状态含义不清", "更新顺序覆盖旧值", "漏掉一种来源"),
                List.of("WA"),
                "先写清楚状态代表什么。",
                "检查每次更新用了哪些旧状态。",
                "用 3 个以内元素手推状态表，看转移是否覆盖所有来源。",
                "状态建模", "HIGH", List.of("PYTHON", "CPP17"),
                List.of("DP_STATE_DESIGN", "IN_PLACE_STATE_PROGRESS"), "DEFINE_STATE"));
        addBasic(map, basic("DP_STATE_DESIGN", "状态建模", "DP 状态定义不清",
                "动态规划状态没有包含决策所需的信息。",
                "当前先定义状态的含义和维度。",
                "适合训练状态、转移、初值、答案位置四件套。",
                List.of("dp state", "missing dimension", "transition source"),
                List.of("状态只看位置却需要额外条件", "初值与状态含义不一致", "答案取错状态"),
                List.of("WA", "TLE"),
                "先别写转移，先定义状态。",
                "用一句话说明 dp[i] 或 dp[i][j] 表示什么。",
                "检查初值、转移和最终答案是否都符合这个定义。",
                "状态建模", "HIGH", List.of("PYTHON", "CPP17"), List.of(), "DEFINE_STATE"));
        addBasic(map, basic("IN_PLACE_STATE_PROGRESS", "状态建模", "原地状态推进",
                "原地修改后，新状态没有继续处理到稳定或破坏循环不变量。",
                "当前先追踪一次修改后当前位置的含义。",
                "常见于原地交换、数组归位、链表/指针推进。",
                List.of("in-place mutation", "loop invariant", "state progress"),
                List.of("交换后直接跳过当前位置", "指针移动过早", "新值未重新检查"),
                List.of("WA", "TLE"),
                "先看一次修改后当前位置变成了什么。",
                "追踪修改前后循环不变量是否还成立。",
                "检查新移动来的值是否已经被处理到稳定状态。",
                "状态建模", "HIGH", List.of("PYTHON", "CPP17"), List.of(), "DEFINE_STATE"));
        addBasic(map, basic("RECURSION_EXIT", "递归与搜索", "递归出口",
                "递归终止条件、回溯恢复或搜索边界存在问题。",
                "当前先画出最小输入下的递归树。",
                "适合处理 DFS、回溯、分治和递归模拟。",
                List.of("recursion", "base case", "backtracking"),
                List.of("出口条件漏掉", "递归参数不收缩", "回溯后状态未恢复"),
                List.of("RE", "TLE", "WA"),
                "先画最小输入的递归调用。",
                "检查每条路径是否越来越接近出口。",
                "追踪一次回溯后，全局/局部状态是否恢复。",
                "递归与搜索", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "DRAW_RECURSION_TREE"));
        addBasic(map, basic("SAMPLE_ONLY", "泛化", "只通过样例",
                "代码覆盖了样例路径，但没有泛化到更多情况。",
                "当前先构造一个不同于样例结构的小反例。",
                "适合处理硬编码、样例特判和隐藏测试失败。",
                List.of("sample only", "hidden failure", "public samples pass"),
                List.of("硬编码样例", "按样例形状写分支", "缺少一般性推理"),
                List.of("WA"),
                "先承认隐藏数据不可见，不猜隐藏用例。",
                "构造一个和样例结构不同的最小输入。",
                "说明你的代码为什么对这个新输入也应该成立。",
                "迁移泛化", "MEDIUM", List.of("PYTHON", "CPP17"), List.of("SAMPLE_OVERFIT"), "BUILD_COUNTEREXAMPLE"));
        addBasic(map, basic("SAMPLE_OVERFIT", "泛化", "样例过拟合",
                "代码对样例或常规路径适配过强，缺少一般性。",
                "当前先验证代码有没有依赖样例中的特殊数字或结构。",
                "适合让学生用自造反例检查思路是否真成立。",
                List.of("sample overfit", "hard-coded", "hidden tests"),
                List.of("样例数字特判", "固定输出", "只处理样例长度"),
                List.of("WA"),
                "先看代码是否依赖样例特征。",
                "换一个同类型但不同数字的小样例。",
                "用反例说明当前规则哪里无法泛化。",
                "迁移泛化", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "BUILD_COUNTEREXAMPLE"));
        addBasic(map, basic("CODE_READABILITY", "代码表达", "代码可读性",
                "代码表达影响排查和复盘，但不一定是当前失败主因。",
                "当前先区分计算逻辑、输入输出和调试代码。",
                "适合作为次要提醒或通过后的复盘建议。",
                List.of("readability", "debug branch", "unclear helper"),
                List.of("变量名难懂", "逻辑混在一起", "临时分支残留"),
                List.of("AC", "WA"),
                "先把主要逻辑和调试代码区分开。",
                "给关键变量换成能表达含义的名字。",
                "把输入、计算、输出分成清楚的几段复盘。",
                "代码表达", "LOW", List.of("PYTHON", "CPP17"), List.of(), "EXPLAIN_GENERALITY"));
        addBasic(map, basic("CODE_QUALITY", "代码表达", "代码质量",
                "通过后仍可优化结构、复用和可解释性。",
                "当前先关注是否便于下次复用和讲解。",
                "适合作为 AC 后提高层入口。",
                List.of("code quality", "accepted review", "refactor opportunity"),
                List.of("重复代码", "函数职责不清", "缺少复盘注释"),
                List.of("AC"),
                "先确认它已经解决当前题。",
                "找出最重复或最难解释的一段。",
                "把可复用的思路整理成一句话和一个函数边界。",
                "代码表达与复盘", "LOW", List.of("PYTHON", "CPP17"), List.of(), "EXPLAIN_GENERALITY"));
        addBasic(map, basic("GENERALIZATION_CHECK", "泛化", "泛化检查",
                "通过后仍需要确认复杂度、边界和思路可迁移。",
                "当前先让学生讲清为什么能覆盖一般情况。",
                "适合作为 AC 后复盘与迁移训练。",
                List.of("accepted", "generalization", "review"),
                List.of("只知道过了但说不清原因", "没有总结边界", "无法迁移到变式"),
                List.of("AC"),
                "先说清这题为什么能过。",
                "列一个这题最容易错的边界。",
                "把本题方法迁移到一个相似变式上口头验证。",
                "迁移泛化", "LOW", List.of("PYTHON", "CPP17"), List.of(), "EXPLAIN_GENERALITY"));
        addBasic(map, basic("PARTIAL_FIX_REGRESSION", "学习轨迹", "局部修复回退",
                "一次局部修改修好旧问题但引入新问题。",
                "当前先比较两次提交的首个失败点变化。",
                "适合结合学习轨迹判断学生是否在有效推进。",
                List.of("regression", "submission diff", "previous attempt"),
                List.of("只改一处但 verdict 变差", "新提交首个失败点前移", "旧样例重新失败"),
                List.of("WA", "RE", "CE"),
                "先比较这次和上次失败点有什么变化。",
                "找出新问题是修复副作用还是旧问题暴露。",
                "保留能通过的最小修改，再逐步验证每个新增改动。",
                "问题定位", "MEDIUM", List.of("PYTHON", "CPP17"), List.of(), "COMPARE_SUBMISSIONS"));
        addBasic(map, basic("NEEDS_MORE_EVIDENCE", "证据", "证据不足",
                "当前证据不足以可靠判断主因。",
                "当前先补一条可观察证据，而不是猜答案。",
                "适合隐藏测试不可见、信号冲突或代码片段不足的情况。",
                List.of("low confidence", "conflicting signals", "hidden data unavailable"),
                List.of("没有失败样例", "只看到隐藏测试失败", "多个信号互相冲突"),
                List.of("UNKNOWN", "WA"),
                "先不要猜隐藏数据。",
                "补一个最小可见样例或变量追踪结果。",
                "把当前判断建立在可复现的输入、输出或报错上。",
                "问题定位", "LOW", List.of("PYTHON", "CPP17"), List.of(), "COLLECT_EVIDENCE"));
        return map;
    }

    private Map<String, StandardLibraryPack.ImprovementPointOption> improvementPointLibrary() {
        LinkedHashMap<String, StandardLibraryPack.ImprovementPointOption> map = new LinkedHashMap<>();
        addImprovement(map, improvementPoint("COMPLEXITY", "复杂度", "复杂度估算",
                "训练学生从样例规模推进到最大规模判断。",
                "当题面约束较大、出现 TLE、嵌套循环或重复扫描时使用。",
                "帮助学生知道代码为什么小数据能跑、大数据不行。",
                "教师可要求学生写出核心操作次数和最大数据代入结果。",
                List.of("constraints", "loop count", "TLE", "MAX_BOUNDARY"),
                "先估算最核心循环执行多少次。",
                "把次数写成 n、m、q 的表达。",
                "代入最大范围，判断数量级是否可接受。",
                "算法复杂度", List.of("TIME_COMPLEXITY", "MAX_BOUNDARY", "BRUTE_FORCE_LIMIT", "OVER_SIMULATION")));
        addImprovement(map, improvementPoint("ALGORITHM_MODELING", "算法策略", "算法模型选择",
                "训练学生识别题目更像哪类算法模型。",
                "当当前思路和题目结构不匹配，或隐藏失败说明样例规律不够时使用。",
                "帮助学生从写代码转向识别题型结构。",
                "教师可让学生比较模拟、枚举、贪心、DP、搜索、数学规律的适用条件。",
                List.of("ALGORITHM_STRATEGY", "hidden failure", "large constraints"),
                "先描述当前思路依赖的假设。",
                "找一个能破坏这个假设的小例子。",
                "比较题目是否存在单调性、最优子结构或可合并状态。",
                "算法设计", List.of("ALGORITHM_STRATEGY", "SAMPLE_OVERFIT")));
        addImprovement(map, improvementPoint("DATA_STRUCTURE_FIT", "数据结构", "数据结构匹配",
                "训练学生按操作需求选择容器。",
                "当代码频繁查找、插入、删除、排序、区间查询或内存压力明显时使用。",
                "帮助学生理解数据结构不是模板，而是服务操作。",
                "教师可要求学生列出操作频率和每种容器的代价。",
                List.of("DATA_STRUCTURE_CHOICE", "SPACE_COMPLEXITY", "operation frequency"),
                "先列题目最频繁的操作。",
                "估算当前结构完成这些操作的代价。",
                "比较是否需要快速查找、有序维护、队列或区间信息。",
                "数据结构", List.of("DATA_STRUCTURE_CHOICE", "SPACE_COMPLEXITY")));
        addImprovement(map, improvementPoint("BINARY_SEARCH_MODEL", "算法技巧", "二分模型",
                "训练学生识别单调性、边界和答案检查函数。",
                "当题目存在有序区间、可行性单调或搜索答案空间时使用。",
                "帮助学生把二分从模板变成模型理解。",
                "教师可让学生说明单调条件、左右边界含义和检查函数含义。",
                List.of("monotonic", "ordered range", "boundary"),
                "先判断答案或位置是否有单调性。",
                "说明左边界和右边界分别代表什么。",
                "构造边界样例检查收缩后是否仍保留答案。",
                "算法设计", List.of("LOOP_BOUNDARY", "OFF_BY_ONE", "ALGORITHM_STRATEGY")));
        addImprovement(map, improvementPoint("DP_STATE_DESIGN", "动态规划", "DP 状态设计",
                "训练学生先定义状态，再写转移。",
                "当题目需要从历史状态递推，或当前 DP 状态缺失信息时使用。",
                "帮助学生建立状态、转移、初值、答案位置四件套。",
                "教师可要求学生用自然语言解释每个状态含义。",
                List.of("STATE_TRANSITION", "DP_STATE_DESIGN", "recurrence"),
                "先用一句话定义状态含义。",
                "写出状态需要保存哪些信息。",
                "检查初值、转移和最终答案是否都符合状态定义。",
                "状态建模", List.of("STATE_TRANSITION", "DP_STATE_DESIGN", "INITIAL_STATE")));
        addImprovement(map, improvementPoint("GREEDY_PROOF", "贪心", "贪心依据",
                "训练学生验证局部选择是否能推出全局最优。",
                "当代码依赖排序、最大/最小优先或局部选择时使用。",
                "帮助学生避免只凭直觉写贪心。",
                "教师可要求学生给出反例检验或交换论证雏形。",
                List.of("GREEDY_ASSUMPTION", "local choice", "counterexample"),
                "先说清每一步为什么这样选。",
                "构造一个局部看起来好但可能全局差的例子。",
                "尝试说明交换后不会更差，或找出反例。",
                "算法设计", List.of("GREEDY_ASSUMPTION", "ALGORITHM_STRATEGY")));
        addImprovement(map, improvementPoint("SEARCH_PRUNING", "搜索", "搜索剪枝",
                "训练学生控制搜索规模和无效分支。",
                "当 DFS/BFS/回溯出现超时或重复状态时使用。",
                "帮助学生从能搜到，推进到搜得动。",
                "教师可要求学生列出状态数量、重复状态和剪枝条件的安全性。",
                List.of("RECURSION_EXIT", "TLE", "repeated state"),
                "先估算搜索状态数量。",
                "找出哪些分支一定不可能产生答案。",
                "验证剪枝不会删掉可能正确的路径。",
                "递归与搜索", List.of("RECURSION_EXIT", "TIME_COMPLEXITY")));
        addImprovement(map, improvementPoint("MATH_PATTERN", "数学规律", "数学规律抽象",
                "训练学生从枚举结果中发现可证明规律。",
                "当题目存在周期、整除、组合计数、前缀性质或模拟重复时使用。",
                "帮助学生把观察变成可解释的规律。",
                "教师可让学生列小表、找不变量，再解释规律为什么成立。",
                List.of("OVER_SIMULATION", "large range", "pattern"),
                "先列出几个小规模结果。",
                "观察差值、周期、奇偶或整除关系。",
                "说明规律为什么对下一组数据也成立。",
                "数学抽象", List.of("OVER_SIMULATION", "ALGORITHM_STRATEGY")));
        addImprovement(map, improvementPoint("TESTING_HABIT", "自测", "自测与反例构造",
                "训练学生主动构造非样例测试。",
                "当出现边界、隐藏失败、样例过拟合、输入输出错误或修复回退时使用。",
                "帮助学生在提交前发现问题，而不是只依赖评测。",
                "教师可要求学生提交一组能暴露问题的自造样例。",
                List.of("boundary", "hidden failure", "sample overfit", "visible mismatch"),
                "先写一个不同于样例的小测试。",
                "覆盖最小值、最大值、重复值或多组输入。",
                "说明这个测试为什么能验证当前思路。",
                "测试与调试", List.of("BOUNDARY_CONDITION", "SAMPLE_ONLY", "IO_FORMAT", "PARTIAL_FIX_REGRESSION")));
        addImprovement(map, improvementPoint("CODE_ORGANIZATION", "代码表达", "代码组织",
                "训练学生把输入、计算、输出和调试代码分清楚。",
                "当代码难以排查、调试输出残留或函数职责混乱时使用。",
                "帮助学生减少低级错误，提高复盘效率。",
                "教师可要求学生指出每一段代码负责什么。",
                List.of("CODE_READABILITY", "DEBUG_CLEANUP", "IO_FORMAT"),
                "先把输入、计算、输出分成三段看。",
                "删除或隔离临时调试输出。",
                "给关键函数和变量写出一句职责说明。",
                "代码表达", List.of("CODE_READABILITY", "CODE_QUALITY", "OUTPUT_FORMAT_DETAIL")));
        addImprovement(map, improvementPoint("TRANSFER_REVIEW", "复盘迁移", "AC 后复盘迁移",
                "训练学生通过后总结可迁移能力。",
                "当代码 AC、接近 AC，或当前问题已定位后使用。",
                "帮助学生把一次通过沉淀成下一题可复用的方法。",
                "教师可要求学生复述题型、关键边界和复杂度理由。",
                List.of("AC", "GENERALIZATION_CHECK", "CODE_QUALITY"),
                "先说清这题真正考的是什么。",
                "总结一个最容易错的边界和一个复杂度理由。",
                "把本题方法迁移到一个相似题口头验证。",
                "迁移泛化", List.of("GENERALIZATION_CHECK", "CODE_QUALITY", "SAMPLE_ONLY")));
        addImprovement(map, improvementPoint("BOUNDARY_AWARENESS", "边界", "边界意识",
                "训练学生系统检查极小、极大、重复、特殊值。",
                "当出现边界条件、循环端点、状态初值或运行时问题时使用。",
                "帮助学生建立每题都能复用的边界清单。",
                "教师可要求学生列出本题至少三类边界输入。",
                List.of("BOUNDARY_CONDITION", "OFF_BY_ONE", "EMPTY_INPUT", "MAX_BOUNDARY"),
                "先列最小、最大和特殊值。",
                "每类边界至少构造一个输入。",
                "说明代码在这些边界下关键变量如何变化。",
                "边界条件意识", List.of("BOUNDARY_CONDITION", "LOOP_BOUNDARY", "VARIABLE_INITIALIZATION")));
        return map;
    }

    private void addBasic(Map<String, StandardLibraryPack.BasicCauseOption> map,
                          StandardLibraryPack.BasicCauseOption option) {
        map.put(option.getId(), option);
    }

    private StandardLibraryPack.BasicCauseOption basic(String id,
                                                       String category,
                                                       String name,
                                                       String description,
                                                       String studentExplanation,
                                                       String teacherExplanation,
                                                       List<String> evidenceSignals,
                                                       List<String> commonCodePatterns,
                                                       List<String> judgeSignals,
                                                       String hintL1,
                                                       String hintL2,
                                                       String hintL3,
                                                       String abilityPoint,
                                                       String severity,
                                                       List<String> applicableLanguages,
                                                       List<String> relatedFineTags,
                                                       String teachingAction) {
        return StandardLibraryPack.BasicCauseOption.builder()
                .id(id)
                .category(category)
                .name(name)
                .description(description)
                .studentExplanation(studentExplanation)
                .teacherExplanation(teacherExplanation)
                .evidenceSignals(evidenceSignals)
                .commonCodePatterns(commonCodePatterns)
                .judgeSignals(judgeSignals)
                .hintL1(hintL1)
                .hintL2(hintL2)
                .hintL3(hintL3)
                .abilityPoint(abilityPoint)
                .severity(severity)
                .applicableLanguages(applicableLanguages)
                .relatedFineTags(relatedFineTags)
                .teachingAction(teachingAction)
                .build();
    }

    private void addImprovement(Map<String, StandardLibraryPack.ImprovementPointOption> map,
                                StandardLibraryPack.ImprovementPointOption option) {
        map.put(option.getId(), option);
    }

    private StandardLibraryPack.ImprovementPointOption improvementPoint(String id,
                                                                        String category,
                                                                        String name,
                                                                        String description,
                                                                        String whenToUse,
                                                                        String studentBenefit,
                                                                        String teacherExplanation,
                                                                        List<String> requiredEvidence,
                                                                        String hintL1,
                                                                        String hintL2,
                                                                        String hintL3,
                                                                        String abilityPoint,
                                                                        List<String> relatedBasicCauses) {
        return StandardLibraryPack.ImprovementPointOption.builder()
                .id(id)
                .category(category)
                .name(name)
                .description(description)
                .whenToUse(whenToUse)
                .studentBenefit(studentBenefit)
                .teacherExplanation(teacherExplanation)
                .requiredEvidence(requiredEvidence)
                .hintL1(hintL1)
                .hintL2(hintL2)
                .hintL3(hintL3)
                .abilityPoint(abilityPoint)
                .relatedBasicCauses(relatedBasicCauses)
                .build();
    }

    private List<StandardLibraryPack.ImprovementTagOption> buildImprovementTags(
            List<StandardLibraryPack.ImprovementPointOption> improvementPoints) {
        if (improvementPoints == null || improvementPoints.isEmpty()) {
            return List.of();
        }
        return improvementPoints.stream()
                .map(point -> StandardLibraryPack.ImprovementTagOption.builder()
                        .id(point.getId())
                        .label(point.getName())
                        .whenToUse(point.getWhenToUse())
                        .studentBenefit(point.getStudentBenefit())
                        .build())
                .toList();
    }

    private StandardLibraryPack.TagOption toTagOption(DiagnosisTaxonomy.DiagnosisTag tag) {
        return StandardLibraryPack.TagOption.builder()
                .id(tag.getId())
                .label(tag.getLabel())
                .studentExplanation(tag.getStudentExplanation())
                .teacherExplanation(tag.getTeacherExplanation())
                .abilityPoint(tag.getAbilityPoint())
                .parentTag(tag.getParentTag())
                .teachingAction(tag.getTeachingAction())
                .build();
    }

    private StandardLibraryPack.TeachingActionOption toTeachingActionOption(String action) {
        return StandardLibraryPack.TeachingActionOption.builder()
                .id(action)
                .label(switch (action) {
                    case "FIX_FIRST_COMPILER_ERROR" -> "先修第一条编译错误";
                    case "COMPARE_OUTPUT" -> "对比输出";
                    case "COMPARE_INPUT_SPEC" -> "核对输入结构";
                    case "ASK_MIN_CASE" -> "构造最小样例";
                    case "CHECK_BRANCH_COVERAGE" -> "检查分支覆盖";
                    case "TRACE_VARIABLES" -> "追踪变量";
                    case "COMPARE_STRUCTURES" -> "比较数据结构";
                    case "COUNT_COMPLEXITY" -> "估算复杂度";
                    case "TRACE_STATE" -> "追踪状态";
                    case "DEFINE_STATE" -> "定义状态";
                    case "DRAW_RECURSION_TREE" -> "画递归树";
                    case "EXPLAIN_GENERALITY" -> "解释泛化";
                    case "BUILD_COUNTEREXAMPLE" -> "构造反例";
                    case "CHECK_INVARIANT" -> "检查不变量";
                    case "CHECK_RUNTIME_GUARDS" -> "检查运行保护";
                    case "COMPARE_SUBMISSIONS" -> "比较提交差异";
                    case "COLLECT_EVIDENCE" -> "补充证据";
                    default -> action;
                })
                .whenToUse(null)
                .studentTaskTemplate(null)
                .build();
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
