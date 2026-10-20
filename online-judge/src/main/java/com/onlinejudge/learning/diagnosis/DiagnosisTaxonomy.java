package com.onlinejudge.learning.diagnosis;

import com.onlinejudge.classroom.domain.Assignment;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DiagnosisTaxonomy {

    public static final String TAXONOMY_VERSION = "taxonomy-v1";

    private final Map<String, DiagnosisTag> tags = buildTags();

    public List<DiagnosisTag> allTags() {
        return List.copyOf(tags.values());
    }

    public DiagnosisTag get(String id) {
        return tags.get(normalize(id));
    }

    public String label(String id) {
        DiagnosisTag tag = get(id);
        return tag == null ? id : tag.getLabel();
    }

    public String teachingAction(String id) {
        DiagnosisTag tag = get(id);
        return tag == null || tag.getTeachingAction() == null ? "TRACE_VARIABLES" : tag.getTeachingAction();
    }

    public List<String> normalizeIssueTags(List<String> issueTags) {
        if (issueTags == null || issueTags.isEmpty()) {
            return List.of("NEEDS_MORE_EVIDENCE");
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : issueTags) {
            String id = normalize(tag);
            if (tags.containsKey(id) && !normalized.contains(id)) {
                normalized.add(id);
            }
        }
        if (normalized.isEmpty()) {
            normalized.add("NEEDS_MORE_EVIDENCE");
        }
        return normalized;
    }

    public List<String> normalizeFineGrainedTags(List<String> fineGrainedTags) {
        if (fineGrainedTags == null || fineGrainedTags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : fineGrainedTags) {
            String id = normalize(tag);
            DiagnosisTag diagnosisTag = tags.get(id);
            if (diagnosisTag != null && diagnosisTag.isFineGrained() && !normalized.contains(id)) {
                normalized.add(id);
            }
        }
        return normalized;
    }

    public Assignment.HintPolicy clampPolicy(Assignment.HintPolicy candidate, Assignment.HintPolicy fallback) {
        if (candidate == null) {
            return fallback == null ? Assignment.HintPolicy.L2 : fallback;
        }
        return candidate;
    }

    public boolean isBeyondPolicy(String text, Assignment.HintPolicy policy) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Assignment.HintPolicy effective = policy == null ? Assignment.HintPolicy.L2 : policy;
        if (effective == Assignment.HintPolicy.L4) {
            return false;
        }
        if (normalized.contains("完整代码") || normalized.contains("参考代码")
                || normalized.contains("直接改成") || normalized.contains("答案如下")
                || normalized.contains("int main") || normalized.contains("#include")
                || normalized.contains("def ") || normalized.contains("```")) {
            return true;
        }
        if (effective == Assignment.HintPolicy.L1) {
            return normalized.contains("第") && normalized.contains("行");
        }
        if (effective == Assignment.HintPolicy.L2) {
            return normalized.contains("改为") || normalized.contains("替换为");
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, DiagnosisTag> buildTags() {
        Map<String, DiagnosisTag> map = new LinkedHashMap<>();
        add(map, "SYNTAX_ERROR", "语法错误", "程序还没通过语法或编译检查。", "学生仍处在语法/环境修正阶段。", "语法与运行环境", Assignment.HintPolicy.L1);
        add(map, "IO_FORMAT", "输入输出格式", "读入或输出格式可能和题面不一致。", "适合提醒学生逐字核对输入输出规范。", "题意读取", Assignment.HintPolicy.L2);
        add(map, "BOUNDARY_CONDITION", "边界条件", "某些极小、极大、空值或特殊输入可能没有处理。", "适合用边界分类和手推样例干预。", "边界条件意识", Assignment.HintPolicy.L2);
        add(map, "CONDITION_BRANCH", "条件分支", "判断条件或分支顺序可能不完整。", "学生需要梳理条件覆盖关系。", "条件分支推理", Assignment.HintPolicy.L2);
        add(map, "LOOP_BOUNDARY", "循环边界", "循环起止位置可能少处理或多处理元素。", "可引导学生用最小样例跟踪循环变量。", "循环与边界", Assignment.HintPolicy.L3);
        add(map, "DATA_STRUCTURE_CHOICE", "数据结构选择", "当前容器或组织方式可能不适合题目规模。", "适合安排数据结构选择对比。", "数据结构", Assignment.HintPolicy.L2);
        add(map, "TIME_COMPLEXITY", "时间复杂度", "算法执行次数可能随输入规模增长过快。", "学生需要建立复杂度估算意识。", "算法复杂度", Assignment.HintPolicy.L2);
        add(map, "SPACE_COMPLEXITY", "空间复杂度", "保存的数据或中间状态可能过多。", "学生需要关注状态压缩和内存结构。", "空间管理", Assignment.HintPolicy.L2);
        add(map, "VARIABLE_INITIALIZATION", "变量初始化", "变量初值或状态重置可能有遗漏。", "适合检查循环前后和多组数据场景。", "状态维护", Assignment.HintPolicy.L2);
        add(map, "STATE_TRANSITION", "状态转移", "状态更新关系可能不完整或顺序错误。", "适合用于动态规划、模拟、递推类任务。", "状态建模", Assignment.HintPolicy.L3);
        add(map, "RECURSION_EXIT", "递归出口", "递归终止条件或回溯过程可能存在问题。", "适合引导学生画递归树。", "递归与搜索", Assignment.HintPolicy.L3);
        add(map, "CODE_READABILITY", "代码可读性", "代码表达可以更清楚，便于复盘。", "可作为通过后的优化建议。", "代码表达", Assignment.HintPolicy.L1);
        add(map, "CODE_QUALITY", "代码质量", "已通过后仍可优化结构、命名或复用。", "适合引导复盘和泛化。", "代码表达与复盘", Assignment.HintPolicy.L1);
        add(map, "SAMPLE_ONLY", "只通过样例", "代码可能只覆盖了样例，没有泛化到隐藏场景。", "适合要求学生自造反例。", "迁移泛化", Assignment.HintPolicy.L2);
        add(map, "GENERALIZATION_CHECK", "泛化检查", "通过后仍需要确认是否能解释边界和复杂度。", "适合让学生讲清思路。", "迁移泛化", Assignment.HintPolicy.L1);
        add(map, "ALGORITHM_STRATEGY", "算法策略", "当前思路可能需要更换或优化。", "适合做策略对比，而不是直接给答案。", "算法设计", Assignment.HintPolicy.L2);
        add(map, "RUNTIME_STABILITY", "运行稳定性", "代码运行中可能触发越界、空值、除零等错误。", "适合从异常日志和极端输入入手。", "程序稳定性", Assignment.HintPolicy.L2);
        add(map, "NEEDS_MORE_EVIDENCE", "证据不足", "当前信息不足，需要更多提交或样例判断。", "建议结合下一次提交继续观察。", "问题定位", Assignment.HintPolicy.L1);
        addFine(map, "OFF_BY_ONE", "差一位错误", "循环起点、终点或索引可能少算/多算一个位置。", "适合让学生列循环变量表，验证 1 个和 2 个元素的最小样例。", "循环与边界", Assignment.HintPolicy.L2);
        addFine(map, "EMPTY_INPUT", "极小输入", "空值、单元素或最小规模输入可能没有覆盖。", "适合要求学生自造最小输入并手推。", "边界条件意识", Assignment.HintPolicy.L2);
        addFine(map, "MAX_BOUNDARY", "最大规模边界", "最大输入规模下可能出现超时、溢出或内存压力。", "适合结合复杂度估算和最大规模压测。", "算法复杂度", Assignment.HintPolicy.L2);
        addFine(map, "DUPLICATE_CASE", "重复元素场景", "重复值或重复状态可能破坏当前判断。", "适合用重复元素构造反例。", "迁移泛化", Assignment.HintPolicy.L2);
        addFine(map, "OUTPUT_FORMAT_DETAIL", "输出格式细节", "换行、空格、大小写或多余字符可能和题面不一致。", "适合让学生逐字对比实际输出和期望输出。", "题意读取", Assignment.HintPolicy.L1);
        addFine(map, "INPUT_PARSING", "输入读取理解", "输入结构或多组数据读取方式可能理解错。", "适合重新圈出题面输入格式。", "题意读取", Assignment.HintPolicy.L2);
        addFine(map, "INITIAL_STATE", "初始状态", "变量初值或初始集合可能设置不符合题意。", "适合检查循环前状态和默认值。", "状态维护", Assignment.HintPolicy.L2);
        addFine(map, "STATE_RESET", "状态重置", "多轮循环或多组数据之间可能遗漏状态清空。", "适合检查每次处理前后变量是否重置。", "状态维护", Assignment.HintPolicy.L2);
        addFine(map, "OVER_SIMULATION", "过度模拟", "实现逐步模拟过多细节，可能导致复杂度过高。", "适合引导寻找规律或预处理。", "算法设计", Assignment.HintPolicy.L2);
        addFine(map, "BRUTE_FORCE_LIMIT", "暴力规模瓶颈", "暴力枚举在最大规模下可能无法通过。", "适合估算循环次数并比较更优策略。", "算法复杂度", Assignment.HintPolicy.L2);
        addFine(map, "GREEDY_ASSUMPTION", "贪心依据不足", "当前贪心选择可能缺少可证明依据。", "适合让学生找反例检验贪心规则。", "算法设计", Assignment.HintPolicy.L3);
        addFine(map, "DP_STATE_DESIGN", "状态定义不清", "动态规划或递推状态可能没有覆盖题目所需信息。", "适合先用自然语言定义状态含义。", "状态建模", Assignment.HintPolicy.L3);
        addFine(map, "IN_PLACE_STATE_PROGRESS", "原地状态推进", "原地修改过程中，当前位置的新值或新状态可能还没有继续处理到稳定。", "适合让学生跟踪一次交换或更新后，当前状态是否已经满足循环不变量。", "状态建模", Assignment.HintPolicy.L3);
        addFine(map, "SAMPLE_OVERFIT", "样例过拟合", "代码可能只覆盖样例或常规路径，没有泛化到隐藏场景。", "适合让学生构造不同于样例的最小反例。", "迁移泛化", Assignment.HintPolicy.L2);
        addFine(map, "RECURSION_BASE_CASE", "递归出口缺失", "递归遇到空节点、最小规模或终止状态时可能没有安全返回。", "适合让学生先手推递归函数收到空状态时第一步会做什么。", "递归与搜索", Assignment.HintPolicy.L2);
        addFine(map, "BINARY_SEARCH_BOUNDARY", "二分边界推进", "二分循环中的左右边界可能没有稳定缩小，导致死循环或漏解。", "适合让学生记录 left、right、mid 的变化，检查区间是否变短。", "循环与边界", Assignment.HintPolicy.L2);
        addFine(map, "SORT_KEY", "排序比较规则", "排序时使用的数据表示或比较规则可能和题意不一致。", "适合让学生对比字符串顺序和数值顺序等不同规则。", "数据表示与排序", Assignment.HintPolicy.L2);
        addFine(map, "INTEGER_DIVISION_PRECISION", "整数除法精度", "除法表达式可能先按整数计算，导致小数部分被截断。", "适合让学生手推表达式中每个值的类型和中间结果。", "数据类型与表达式", Assignment.HintPolicy.L2);
        addFine(map, "ARRAY_INDEX_OUT_OF_BOUNDS", "数组下标越界", "访问数组或容器时可能使用了不存在的位置。", "适合让学生核对合法下标范围和循环终点。", "数组与边界", Assignment.HintPolicy.L2);
        addFine(map, "UNINITIALIZED_VARIABLE", "变量未初始化", "变量在第一次使用前可能没有被赋予确定初值。", "适合让学生追踪变量从声明到第一次参与计算的路径。", "状态维护", Assignment.HintPolicy.L2);
        addFine(map, "PARTIAL_FIX_REGRESSION", "局部修复回退", "一次局部修改可能修好旧问题但引入新问题。", "适合对比两次提交差异和首个失败点变化。", "问题定位", Assignment.HintPolicy.L2);
        return map;
    }

    private void add(Map<String, DiagnosisTag> map,
                     String id,
                     String label,
                     String studentExplanation,
                     String teacherExplanation,
                     String abilityPoint,
                     Assignment.HintPolicy recommendedHintPolicy) {
        map.put(id, DiagnosisTag.builder()
                .id(id)
                .label(label)
                .studentExplanation(studentExplanation)
                .teacherExplanation(teacherExplanation)
                .abilityPoint(abilityPoint)
                .recommendedHintPolicy(recommendedHintPolicy)
                .fineGrained(false)
                .parentTag(null)
                .severity("MEDIUM")
                .teachingAction(resolveTeachingAction(id))
                .commonSignals(List.of())
                .examples(List.of())
                .build());
    }

    private void addFine(Map<String, DiagnosisTag> map,
                         String id,
                         String label,
                         String studentExplanation,
                         String teacherExplanation,
                         String abilityPoint,
                         Assignment.HintPolicy recommendedHintPolicy) {
        map.put(id, DiagnosisTag.builder()
                .id(id)
                .label(label)
                .studentExplanation(studentExplanation)
                .teacherExplanation(teacherExplanation)
                .abilityPoint(abilityPoint)
                .recommendedHintPolicy(recommendedHintPolicy)
                .fineGrained(true)
                .parentTag(resolveParentTag(id))
                .severity("MEDIUM")
                .teachingAction(resolveTeachingAction(id))
                .commonSignals(List.of())
                .examples(List.of())
                .build());
    }

    private String resolveParentTag(String id) {
        return switch (id) {
            case "OFF_BY_ONE", "EMPTY_INPUT", "MAX_BOUNDARY", "DUPLICATE_CASE" -> "BOUNDARY_CONDITION";
            case "OUTPUT_FORMAT_DETAIL", "INPUT_PARSING" -> "IO_FORMAT";
            case "INITIAL_STATE", "STATE_RESET" -> "VARIABLE_INITIALIZATION";
            case "OVER_SIMULATION", "BRUTE_FORCE_LIMIT" -> "TIME_COMPLEXITY";
            case "GREEDY_ASSUMPTION", "DP_STATE_DESIGN" -> "ALGORITHM_STRATEGY";
            case "IN_PLACE_STATE_PROGRESS" -> "STATE_TRANSITION";
            case "RECURSION_BASE_CASE" -> "RECURSION_EXIT";
            case "BINARY_SEARCH_BOUNDARY" -> "LOOP_BOUNDARY";
            case "SORT_KEY" -> "ALGORITHM_STRATEGY";
            case "INTEGER_DIVISION_PRECISION" -> "BOUNDARY_CONDITION";
            case "ARRAY_INDEX_OUT_OF_BOUNDS" -> "RUNTIME_STABILITY";
            case "UNINITIALIZED_VARIABLE" -> "VARIABLE_INITIALIZATION";
            case "SAMPLE_OVERFIT" -> "SAMPLE_ONLY";
            case "PARTIAL_FIX_REGRESSION" -> "NEEDS_MORE_EVIDENCE";
            default -> null;
        };
    }

    private String resolveTeachingAction(String id) {
        return switch (id) {
            case "SYNTAX_ERROR" -> "FIX_FIRST_COMPILER_ERROR";
            case "IO_FORMAT", "OUTPUT_FORMAT_DETAIL" -> "COMPARE_OUTPUT";
            case "BOUNDARY_CONDITION", "EMPTY_INPUT", "MAX_BOUNDARY", "DUPLICATE_CASE" -> "ASK_MIN_CASE";
            case "CONDITION_BRANCH" -> "CHECK_BRANCH_COVERAGE";
            case "LOOP_BOUNDARY", "OFF_BY_ONE", "ARRAY_INDEX_OUT_OF_BOUNDS" -> "TRACE_VARIABLES";
            case "DATA_STRUCTURE_CHOICE", "SPACE_COMPLEXITY" -> "COMPARE_STRUCTURES";
            case "TIME_COMPLEXITY", "BRUTE_FORCE_LIMIT", "OVER_SIMULATION" -> "COUNT_COMPLEXITY";
            case "VARIABLE_INITIALIZATION", "INITIAL_STATE", "STATE_RESET", "UNINITIALIZED_VARIABLE" -> "TRACE_STATE";
            case "STATE_TRANSITION", "DP_STATE_DESIGN", "IN_PLACE_STATE_PROGRESS" -> "DEFINE_STATE";
            case "RECURSION_EXIT", "RECURSION_BASE_CASE" -> "DRAW_RECURSION_TREE";
            case "CODE_READABILITY", "CODE_QUALITY", "GENERALIZATION_CHECK" -> "EXPLAIN_GENERALITY";
            case "SAMPLE_ONLY", "SAMPLE_OVERFIT" -> "BUILD_COUNTEREXAMPLE";
            case "ALGORITHM_STRATEGY", "GREEDY_ASSUMPTION", "SORT_KEY" -> "CHECK_INVARIANT";
            case "BINARY_SEARCH_BOUNDARY" -> "TRACE_VARIABLES";
            case "INTEGER_DIVISION_PRECISION" -> "TRACE_VARIABLES";
            case "RUNTIME_STABILITY" -> "CHECK_RUNTIME_GUARDS";
            case "INPUT_PARSING" -> "COMPARE_INPUT_SPEC";
            case "PARTIAL_FIX_REGRESSION" -> "COMPARE_SUBMISSIONS";
            case "NEEDS_MORE_EVIDENCE" -> "COLLECT_EVIDENCE";
            default -> "TRACE_VARIABLES";
        };
    }

    @Data
    @Builder
    public static class DiagnosisTag {
        private String id;
        private String label;
        private String studentExplanation;
        private String teacherExplanation;
        private String abilityPoint;
        private Assignment.HintPolicy recommendedHintPolicy;
        private boolean fineGrained;
        private String parentTag;
        private String severity;
        private String teachingAction;
        private List<String> commonSignals;
        private List<String> examples;
    }
}
