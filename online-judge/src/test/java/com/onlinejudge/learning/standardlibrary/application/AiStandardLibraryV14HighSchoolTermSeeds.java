package com.onlinejudge.learning.standardlibrary.application;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;

import java.util.List;

final class AiStandardLibraryV14HighSchoolTermSeeds {

    private static final List<String> LANGUAGES = List.of("PYTHON", "CPP17");

    private AiStandardLibraryV14HighSchoolTermSeeds() {
    }

    static void addTo(List<AiStandardLibrarySeed> seeds) {
        addSortPass(seeds);
        addArenaExtreme(seeds);
        addRunLengthEncoding(seeds);
        addMajorityVote(seeds);
        addStateMarking(seeds);
        addLinkedList(seeds);
        addIntervalScheduling(seeds);
        addCountingSort(seeds);
    }

    private static void addSortPass(List<AiStandardLibrarySeed> seeds) {
        addTheme(seeds,
                "SK_V14_SORT_PASS_INVARIANT",
                "能力点/V14高中排序过程",
                "识别冒泡排序、选择排序和计数排序的趟次不变量",
                "能用高中排序术语说明每一趟结束后哪个位置已经确定、比较范围如何缩短、统计数组如何还原有序序列。",
                "先写出本趟结束后确定的位置，再判断下一趟还需要比较或统计哪些元素。",
                List.of("ALGO.SORT.BASIC.冒泡排序", "ALGO.SORT.BASIC.选择排序", "ALGO.SORT.BASIC.计数排序"),
                List.of("BASIC.ARRAY.TRAVERSE.全量遍历", "BASIC.ARRAY.INDEX.长度与最后下标"),
                "HIGH",
                List.of(
                        m("MP_V14_BUBBLE_PASS_BOUNDARY_INCLUDES_SORTED_SUFFIX",
                                "冒泡排序下一趟仍比较已就位后缀",
                                "每一趟冒泡结束后最大或最小元素已经到位，但内层循环仍扫到已排序后缀，轻则多做比较，重则把后续交换边界写错。",
                                "只记得相邻交换，没有把第 k 趟后末尾 k 个位置已经确定这个不变量写出来。",
                                "BOUNDARY",
                                List.of("ALGO.SORT.BASIC.冒泡排序", "BASIC.LOOP.BOUNDARY.最后一次迭代"),
                                List.of("BASIC.ARRAY.INDEX.长度与最后下标"),
                                "HIGH"),
                        m("MP_V14_SELECTION_SORT_SWAPS_INSIDE_SCAN",
                                "选择排序在扫描最值过程中提前交换",
                                "选择排序应先扫完整个未排序区间找到最值下标，再与当前位置交换；代码在扫描过程中边找边换，导致候选最值位置被破坏。",
                                "把冒泡的相邻交换习惯带到选择排序，没有区分“记录最值下标”和“最终交换一次”。",
                                "STATE",
                                List.of("ALGO.SORT.BASIC.选择排序", "BASIC.ARRAY.UPDATE.交换元素"),
                                List.of("BASIC.ARRAY.TRAVERSE.全量遍历"),
                                "HIGH")),
                "IP_V14_SORT_TRACE_TABLE",
                "用趟次表复核排序不变量",
                "适用于学生能背出冒泡或选择排序代码，但解释不清每一趟后哪个位置已经确定。",
                "修复后让学生列出趟号、比较范围、已确定位置和本趟交换次数。",
                "教师可用趟次表判断错误来自边界、交换时机还是排序不变量缺失。",
                List.of("MP_V14_BUBBLE_PASS_BOUNDARY_INCLUDES_SORTED_SUFFIX",
                        "MP_V14_SELECTION_SORT_SWAPS_INSIDE_SCAN"));
    }

    private static void addArenaExtreme(List<AiStandardLibrarySeed> seeds) {
        addTheme(seeds,
                "SK_V14_ARENA_EXTREME_SCAN",
                "能力点/V14擂台法求最值",
                "用擂台法维护当前最大值、最小值和对应位置",
                "能在数组遍历中持续维护当前最优值、最优下标和并列规则，并用高中常用的擂台法解释最值更新过程。",
                "先确定擂主变量保存值还是下标，再写清楚遇到更优或并列元素时是否换擂主。",
                List.of("BASIC.ARRAY.TRAVERSE.擂台法求最值", "CONTEST.READING.OUTPUT.输出最值"),
                List.of("BASIC.ARRAY.INDEX.下标映射"),
                "MEDIUM",
                List.of(
                        m("MP_V14_ARENA_INITIALIZES_WITH_ZERO_OUTSIDE_VALUE_RANGE",
                                "擂台法用 0 初始化导致负数或小值全错",
                                "题目数据可能全为负数或小于 0，但最大值擂主初始化为 0，导致真实元素从未成为答案。",
                                "把 0 当成天然起点，没有从第一个元素或题目值域中选择合法初始擂主。",
                                "VALUE_RANGE",
                                List.of("BASIC.ARRAY.TRAVERSE.擂台法求最值", "CONTEST.READING.CONSTRAINT.值域范围"),
                                List.of("ENG.DEBUG.BOUNDARY.极端值"),
                                "HIGH"),
                        m("MP_V14_ARENA_FORGETS_INDEX_WHEN_VALUE_UPDATES",
                                "更新最值时忘记同步最优位置",
                                "代码只更新最大值或最小值变量，没有同步保存对应下标，最后输出位置、编号或对象时仍是旧擂主。",
                                "把最值和位置当成两个独立结果，没有意识到它们必须在同一次换擂主时同步更新。",
                                "STATE",
                                List.of("BASIC.ARRAY.TRAVERSE.擂台法求最值", "BASIC.ARRAY.INDEX.下标映射"),
                                List.of("BASIC.TYPE.VARIABLE.赋值覆盖"),
                                "HIGH")),
                "IP_V14_ARENA_EXTREME_CASE_SET",
                "用首项、负数和并列样例检查擂台法",
                "适用于学生会遍历数组，但最值初始值、位置同步或并列规则经常出错。",
                "修复后让学生固定测试全负数、只有一个元素、多个并列最值和答案在最后一个位置。",
                "教师可用样例组判断错误来自初始化、换擂主条件还是位置同步。",
                List.of("MP_V14_ARENA_INITIALIZES_WITH_ZERO_OUTSIDE_VALUE_RANGE",
                        "MP_V14_ARENA_FORGETS_INDEX_WHEN_VALUE_UPDATES"));
    }

    private static void addRunLengthEncoding(List<AiStandardLibrarySeed> seeds) {
        addTheme(seeds,
                "SK_V14_RUN_LENGTH_ENCODING",
                "能力点/V14游程编码",
                "维护连续相同字符的当前段、长度和输出时机",
                "能用游程编码或运行长度编码描述一段连续相同字符，并在字符变化或字符串结束时输出上一段。",
                "遍历字符串时先判断当前字符是否延续本段；一旦变化，先结算旧段，再开启新段。",
                List.of("BASIC.STRING.BUILD.游程编码", "BASIC.STRING.CHAR.字符遍历"),
                List.of("BASIC.LOOP.BOUNDARY.最后一次迭代"),
                "MEDIUM",
                List.of(
                        m("MP_V14_RLE_MISSES_LAST_RUN_FLUSH",
                                "游程编码漏输出最后一段",
                                "游程编码中只有遇到字符变化才输出上一段，循环结束后没有额外结算当前段，导致最后一段连续字符丢失。",
                                "把变化事件当成唯一输出时机，没有处理字符串结束也是一段结束。",
                                "BOUNDARY",
                                List.of("BASIC.STRING.BUILD.游程编码", "BASIC.LOOP.BOUNDARY.最后一次迭代"),
                                List.of("BASIC.STRING.CHAR.字符遍历"),
                                "HIGH"),
                        m("MP_V14_RLE_COUNTER_RESETS_BEFORE_OUTPUT",
                                "游程计数在输出前被提前重置",
                                "字符变化时先把计数改成 1，再输出上一段，导致上一段长度被新段初始值覆盖。",
                                "没有把旧段的字符和长度作为一组状态同时结算。",
                                "STATE",
                                List.of("BASIC.STRING.BUILD.游程编码", "ALGO.SIM.STATE.状态同步"),
                                List.of("BASIC.TYPE.VARIABLE.赋值覆盖"),
                                "HIGH")),
                "IP_V14_RLE_SEGMENT_TABLE",
                "用分段表检查游程编码",
                "适用于学生能遍历字符串，但段切换、计数重置或最后一段输出经常错。",
                "修复后让学生把字符串拆成字符段、起止位置、长度和输出内容四列。",
                "教师可用分段表判断错误来自段结束时机、计数同步还是输出格式。",
                List.of("MP_V14_RLE_MISSES_LAST_RUN_FLUSH", "MP_V14_RLE_COUNTER_RESETS_BEFORE_OUTPUT"));
    }

    private static void addMajorityVote(List<AiStandardLibrarySeed> seeds) {
        addTheme(seeds,
                "SK_V14_MAJORITY_VOTE",
                "能力点/V14多数投票算法",
                "维护候选值和票数抵消关系",
                "能解释多数投票算法中候选值、票数和抵消过程的含义，并在需要时二次验证候选是否真的过半。",
                "票数为 0 时先设新候选；遇到相同值加票，遇到不同值抵消一票。",
                List.of("ALGO.SIM.STATE.多数投票算法", "BASIC.ARRAY.PREFIX.频次统计"),
                List.of("BASIC.LOOP.FOR.循环次数计算"),
                "HIGH",
                List.of(
                        m("MP_V14_MAJORITY_REPLACES_CANDIDATE_BEFORE_ZERO",
                                "票数未归零就提前更换候选值",
                                "多数投票过程中只要看到不同元素就把候选值改掉，破坏了候选与票数抵消的配对含义。",
                                "把候选值当成当前元素记录，没有理解票数大于 0 时候选仍代表尚未被抵消的一组元素。",
                                "STATE",
                                List.of("ALGO.SIM.STATE.多数投票算法", "ALGO.SIM.STATE.状态同步"),
                                List.of("BASIC.TYPE.VARIABLE.赋值覆盖"),
                                "HIGH"),
                        m("MP_V14_MAJORITY_SKIPS_FINAL_VERIFICATION",
                                "题目不保证多数存在时省略二次验证",
                                "代码用投票结果直接输出候选值，但题目可能不存在超过一半的元素，导致候选只是相对剩余而非合法答案。",
                                "误以为投票算法一定会找出多数，没有区分“候选生成”和“候选验证”两个阶段。",
                                "MODELING",
                                List.of("ALGO.SIM.STATE.多数投票算法", "BASIC.ARRAY.PREFIX.频次统计"),
                                List.of("CONTEST.READING.CONSTRAINT.隐藏特殊条件"),
                                "HIGH")),
                "IP_V14_MAJORITY_CANDIDATE_COUNT_TRACE",
                "用候选票数轨迹检查多数投票",
                "适用于学生知道多数投票名称，但候选更新和二次验证边界经常错。",
                "修复后让学生逐行写出当前元素、候选值、票数和是否需要二次统计。",
                "教师可用轨迹表判断错误来自候选替换、票数抵消还是存在性验证。",
                List.of("MP_V14_MAJORITY_REPLACES_CANDIDATE_BEFORE_ZERO",
                        "MP_V14_MAJORITY_SKIPS_FINAL_VERIFICATION"));
    }

    private static void addStateMarking(List<AiStandardLibrarySeed> seeds) {
        addTheme(seeds,
                "SK_V14_STATE_MARKING",
                "能力点/V14状态标记法",
                "用状态标记变量表达阶段、方向和连续性",
                "能把高中题目里的状态码、连续区间、波峰检测或流程分段转化为明确的状态标记和状态转移条件。",
                "先列出每一种状态的含义，再写出哪些输入会让状态发生变化。",
                List.of("ALGO.SIM.STATE.状态标记法", "ENG.STYLE.INVARIANT.循环不变量"),
                List.of("BASIC.BRANCH.CASE.互斥条件"),
                "MEDIUM",
                List.of(
                        m("MP_V14_STATE_FLAG_NOT_RESET_AFTER_SEGMENT_END",
                                "一段结束后状态标记没有重置",
                                "状态标记法处理连续区间、状态码或分段统计时，一段结束后没有重置 flag，下一段被错误合并或漏计。",
                                "只关注进入状态的条件，没有写出退出状态或开启新段时标记如何恢复。",
                                "STATE",
                                List.of("ALGO.SIM.STATE.状态标记法", "ALGO.SIM.STATE.状态同步"),
                                List.of("BASIC.LOOP.CONTROL.标志变量"),
                                "HIGH"),
                        m("MP_V14_STATE_TRANSITION_ORDER_COUNTS_OLD_STATE",
                                "状态更新顺序导致统计旧状态",
                                "代码先统计当前状态再更新状态，或先更新后统计，和题目要求的状态生效时机相反。",
                                "没有区分当前元素属于旧状态的结束，还是新状态的开始。",
                                "STATE",
                                List.of("ALGO.SIM.STATE.状态标记法", "ALGO.SIM.PROCESS.状态更新先后"),
                                List.of("ENG.DEBUG.TRACE.循环状态"),
                                "HIGH")),
                "IP_V14_STATE_TRANSITION_TABLE",
                "用状态转移表检查状态标记法",
                "适用于学生会写 flag，但进入、保持、退出和统计时机容易混在一起。",
                "修复后让学生列出状态名、进入条件、保持条件、退出条件和统计动作。",
                "教师可用状态转移表判断错误来自标记未重置还是更新顺序不清。",
                List.of("MP_V14_STATE_FLAG_NOT_RESET_AFTER_SEGMENT_END",
                        "MP_V14_STATE_TRANSITION_ORDER_COUNTS_OLD_STATE"));
    }

    private static void addLinkedList(List<AiStandardLibrarySeed> seeds) {
        addTheme(seeds,
                "SK_V14_LINKED_LIST_POINTER_ORDER",
                "能力点/V14链表指针顺序",
                "维护链表前驱、后继、头尾节点和数组模拟链表",
                "能用高中链表术语说明删除、插入、合并或循环链表中 next、prev、head、tail 的更新顺序。",
                "改指针前先保存会被覆盖的后继或前驱，再按不丢链的顺序连接。",
                List.of("DS.LINEAR.LIST.前驱后继", "DS.LINEAR.LIST.插入删除",
                        "DS.LINEAR.LIST.指针_下标模拟", "DS.LINEAR.LIST.头尾节点", "DS.LINEAR.LIST.边界节点"),
                List.of("BASIC.ARRAY.INDEX.下标映射"),
                "HIGH",
                List.of(
                        m("MP_V14_LINKED_LIST_OVERWRITES_NEXT_BEFORE_SAVING",
                                "链表删除前覆盖 next 导致后继丢失",
                                "删除或合并节点时先改掉当前节点 next，却没有保存原后继，后续链条断开或跳过多个节点。",
                                "只看到要把当前节点接到新位置，没有意识到被覆盖的指针仍要继续使用。",
                                "STATE",
                                List.of("DS.LINEAR.LIST.插入删除", "DS.LINEAR.LIST.前驱后继"),
                                List.of("BASIC.ARRAY.UPDATE.读旧写新分离"),
                                "HIGH"),
                        m("MP_V14_LINKED_LIST_HEAD_TAIL_NOT_UPDATED",
                                "链表操作后头尾节点没有同步更新",
                                "删除头节点、插入到尾部或循环链表断开时，主体指针改对了，但 head、tail 或循环入口仍指向旧节点。",
                                "把头尾节点当成普通节点处理，没有单独维护边界节点含义。",
                                "BOUNDARY",
                                List.of("DS.LINEAR.LIST.头尾节点", "DS.LINEAR.LIST.边界节点"),
                                List.of("BASIC.BRANCH.GUARD.特殊情况前置"),
                                "HIGH")),
                "IP_V14_LINKED_POINTER_TRACE",
                "用前驱后继表检查链表操作",
                "适用于学生能看懂链表题意，但指针覆盖、头尾边界或数组模拟链表经常出错。",
                "修复后让学生为每一步写出当前节点、前驱、后继、head 和 tail。",
                "教师可用指针轨迹判断错误来自覆盖顺序、边界节点还是数组下标模拟。",
                List.of("MP_V14_LINKED_LIST_OVERWRITES_NEXT_BEFORE_SAVING",
                        "MP_V14_LINKED_LIST_HEAD_TAIL_NOT_UPDATED"));
    }

    private static void addIntervalScheduling(List<AiStandardLibrarySeed> seeds) {
        addTheme(seeds,
                "SK_V14_INTERVAL_SCHEDULING",
                "能力点/V14区间调度与合并",
                "维护区间排序依据、不相交选择和边界重叠规则",
                "能用高中区间调度与合并术语说明按右端点排序、不相交选择、区间合并和端点是否重叠的判断。",
                "先判断目标是选最多不相交区间还是合并覆盖区间，再决定排序关键字和边界条件。",
                List.of("ALGO.GREEDY.INTERVAL.按右端点排序", "ALGO.GREEDY.INTERVAL.不相交选择",
                        "ALGO.GREEDY.INTERVAL.区间合并", "ALGO.GREEDY.INTERVAL.边界重叠"),
                List.of("ALGO.SORT.BASIC.自定义比较"),
                "HIGH",
                List.of(
                        m("MP_V14_INTERVAL_SCHEDULE_SORTS_BY_START_INSTEAD_OF_END",
                                "区间调度按起点排序导致选择不最优",
                                "区间调度求最多不相交区间时，代码没有按右端点排序，而是按开始时间或长度排序，导致早结束的区间被错过，后续可选空间变小。",
                                "把区间题都当成从左到右处理，没有理解不相交选择需要优先保留最早结束的区间。",
                                "MODELING",
                                List.of("ALGO.GREEDY.INTERVAL.按右端点排序", "ALGO.GREEDY.INTERVAL.不相交选择"),
                                List.of("ALGO.GREEDY.CHOICE.排序依据"),
                                "HIGH"),
                        m("MP_V14_INTERVAL_TOUCHING_ENDPOINT_RULE_REVERSED",
                                "区间端点相接是否冲突判断反了",
                                "题目规定 [l,r] 与 [r,next] 是否可以同时选择，但代码把小于写成小于等于或相反，导致边界相接区间被误删或误选。",
                                "只比较了区间大小，没有把闭区间、开区间或时间段端点归属写清楚。",
                                "BOUNDARY",
                                List.of("ALGO.GREEDY.INTERVAL.边界重叠", "BASIC.EXPR.COMPARE.边界比较"),
                                List.of("BASIC.LOOP.BOUNDARY.左闭右闭"),
                                "HIGH")),
                "IP_V14_INTERVAL_ENDPOINT_CASES",
                "用端点样例检查区间调度",
                "适用于学生知道区间贪心，但排序依据和端点相接规则容易错。",
                "修复后让学生构造完全不重叠、刚好相接、部分重叠和包含关系四类区间。",
                "教师可用端点样例判断错误来自排序目标、边界比较还是合并逻辑。",
                List.of("MP_V14_INTERVAL_SCHEDULE_SORTS_BY_START_INSTEAD_OF_END",
                        "MP_V14_INTERVAL_TOUCHING_ENDPOINT_RULE_REVERSED"));
    }

    private static void addCountingSort(List<AiStandardLibrarySeed> seeds) {
        addTheme(seeds,
                "SK_V14_COUNTING_SORT_RANGE",
                "能力点/V14计数排序",
                "维护计数数组下标、值域偏移和还原顺序",
                "能用计数排序术语说明值如何映射到计数数组下标，以及如何按值域顺序还原输出。",
                "先从题面确定最小值、最大值和值域偏移，再决定计数数组长度和输出顺序。",
                List.of("ALGO.SORT.BASIC.计数排序", "BASIC.ARRAY.PREFIX.计数数组", "BASIC.ARRAY.PREFIX.频次统计"),
                List.of("BASIC.ARRAY.INDEX.下标映射"),
                "MEDIUM",
                List.of(
                        m("MP_V14_COUNTING_SORT_IGNORES_VALUE_OFFSET",
                                "计数排序忽略负数或非零最小值偏移",
                                "计数排序中数据值可能从负数或非零起点开始，但代码没有处理值域偏移，直接用值当作计数数组下标，导致越界或频次落到错误位置。",
                                "只在 0 到 max 的样例上测试，没有把值域最小值作为下标映射的一部分。",
                                "BOUNDARY",
                                List.of("ALGO.SORT.BASIC.计数排序", "BASIC.ARRAY.INDEX.下标映射"),
                                List.of("CONTEST.READING.CONSTRAINT.值域范围"),
                                "HIGH"),
                        m("MP_V14_COUNTING_SORT_OUTPUTS_BY_INPUT_ORDER",
                                "计数排序按输入顺序还原导致没有排序",
                                "统计频次后仍按原数组顺序输出每个值的次数，结果保留了原顺序而不是按值域从小到大还原。",
                                "只完成了计数，没有意识到计数排序的第二步是按值域顺序展开频次。",
                                "MODELING",
                                List.of("ALGO.SORT.BASIC.计数排序", "BASIC.ARRAY.PREFIX.频次统计"),
                                List.of("ALGO.SORT.BASIC.升序降序"),
                                "MEDIUM")),
                "IP_V14_COUNTING_SORT_RANGE_TABLE",
                "用值域映射表检查计数排序",
                "适用于学生会建计数数组，但值域偏移、数组长度或还原顺序经常错。",
                "修复后让学生列出原值、下标、频次和展开输出四列。",
                "教师可用值域映射表判断错误来自偏移、越界还是输出顺序。",
                List.of("MP_V14_COUNTING_SORT_IGNORES_VALUE_OFFSET",
                        "MP_V14_COUNTING_SORT_OUTPUTS_BY_INPUT_ORDER"));
    }

    private static MistakeSpec m(String code,
                                 String name,
                                 String description,
                                 String misconception,
                                 String mistakeType,
                                 List<String> knowledgeNodeCodes,
                                 List<String> prerequisites,
                                 String severity) {
        return new MistakeSpec(code, name, description, misconception, mistakeType,
                knowledgeNodeCodes, prerequisites, severity);
    }

    private static void addTheme(List<AiStandardLibrarySeed> seeds,
                                 String skillCode,
                                 String category,
                                 String skillName,
                                 String skillDescription,
                                 String learningGoal,
                                 List<String> skillKnowledgeNodes,
                                 List<String> skillPrerequisites,
                                 String severity,
                                 List<MistakeSpec> mistakes,
                                 String improvementCode,
                                 String improvementName,
                                 String improvementDescription,
                                 String studentBenefit,
                                 String teacherExplanation,
                                 List<String> relatedMistakeCodes) {
        AiStandardLibrarySeedCatalog.skill(seeds,
                skillCode,
                category,
                skillName,
                skillDescription,
                learningGoal,
                skillKnowledgeNodes,
                skillPrerequisites,
                severity,
                LANGUAGES);
        mistakes.forEach(mistake -> AiStandardLibrarySeedCatalog.mistake(seeds,
                mistake.code(),
                category.replace("能力点/", "易错点/"),
                mistake.name(),
                mistake.description(),
                mistake.misconception(),
                skillCode,
                mistake.mistakeType(),
                mistake.knowledgeNodeCodes(),
                mistake.prerequisites(),
                mistake.severity(),
                LANGUAGES));
        seeds.add(new AiStandardLibrarySeed(
                AiStandardLibraryLayer.IMPROVEMENT_POINT,
                improvementCode,
                category.replace("能力点/", "提升点/"),
                improvementName,
                improvementDescription,
                "",
                teacherExplanation,
                skillCode,
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                improvementDescription,
                studentBenefit,
                "",
                "",
                "",
                skillCode,
                "MEDIUM",
                LANGUAGES,
                relatedMistakeCodes,
                skillKnowledgeNodes,
                skillPrerequisites,
                "通过轨迹表、边界样例或值域映射表把高中术语下的错误转化为可迁移的解题习惯。",
                AiStandardLibrarySeedCatalog.VERSION
        ));
    }

    private record MistakeSpec(
            String code,
            String name,
            String description,
            String misconception,
            String mistakeType,
            List<String> knowledgeNodeCodes,
            List<String> prerequisites,
            String severity
    ) {
    }
}
