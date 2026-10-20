package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisQualityLoopEvalTest {

    private static final Path REPORT_DIR = Path.of("target", "ai-eval-reports");
    private static final List<String> FAILURE_CATEGORIES = List.of(
            "RECALL_MISS",
            "MODEL_MISREAD",
            "TEXT_BAD",
            "ANSWER_LEAK",
            "TOO_LONG",
            "LIBRARY_GAP",
            "VALIDATOR_TOO_STRICT",
            "VALIDATOR_TOO_LOOSE"
    );

    @Test
    void writesJsonAndMarkdownReportsForThirtyTypicalDiagnosisCases() throws IOException {
        List<EvalFixture> fixtures = fixtures();
        assertThat(fixtures).hasSizeBetween(30, 50);

        List<EvalResult> results = fixtures.stream().map(this::evaluate).toList();
        Files.createDirectories(REPORT_DIR);
        ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path json = REPORT_DIR.resolve("diagnosis-quality-loop-report.json");
        Path markdown = REPORT_DIR.resolve("diagnosis-quality-loop-report.md");
        objectMapper.writeValue(json.toFile(), summary(fixtures, results));
        Files.writeString(markdown, markdownReport(fixtures, results));

        assertThat(json).exists();
        assertThat(markdown).exists();
        assertThat(Files.readString(markdown)).contains("学生端实际看到的反馈").contains("基础层").contains("提高层");
        assertThat(results).allSatisfy(result -> {
            if (result.failureCategory() != null) {
                assertThat(FAILURE_CATEGORIES).contains(result.failureCategory());
            }
        });
        assertThat(results.stream().filter(result -> result.failureCategory() == null).count()).isGreaterThanOrEqualTo(20);
        assertThat(results.stream().map(EvalResult::failureCategory).filter(value -> value != null).distinct().toList())
                .containsAll(FAILURE_CATEGORIES);
    }

    private EvalResult evaluate(EvalFixture fixture) {
        String reportText = fixture.studentReport().allText();
        String category = null;
        if ("TOO_STRICT".equals(fixture.validatorSignal())) {
            category = "VALIDATOR_TOO_STRICT";
        } else if ("TOO_LOOSE".equals(fixture.validatorSignal())) {
            category = "VALIDATOR_TOO_LOOSE";
        } else if (fixture.forbiddenLeaks().stream().anyMatch(leak -> !leak.isBlank() && reportText.contains(leak))) {
            category = "ANSWER_LEAK";
        } else if (fixture.studentReport().basicLayerText().length() > 360
                || fixture.studentReport().improvementLayerText().length() > 300
                || fixture.studentReport().nextActionText().length() > 220) {
            category = "TOO_LONG";
        } else if (reportText.contains("候选字段") || reportText.contains("taxonomy") || reportText.contains("schema")) {
            category = "TEXT_BAD";
        } else if ("MISS".equals(fixture.libraryFit())) {
            category = "LIBRARY_GAP";
        } else if (fixture.actualPrimaryCause() == null || fixture.actualPrimaryCause().isBlank()) {
            category = "RECALL_MISS";
        } else if (!fixture.actualPrimaryCause().equals(fixture.expectedPrimaryCause())
                && !fixture.acceptableSecondaryCauses().contains(fixture.actualPrimaryCause())) {
            category = "MODEL_MISREAD";
        }
        return new EvalResult(
                fixture.id(),
                category == null,
                category,
                fixture.actualPrimaryCause(),
                fixture.studentReport()
        );
    }

    private Map<String, Object> summary(List<EvalFixture> fixtures, List<EvalResult> results) {
        long passed = results.stream().filter(EvalResult::passed).count();
        Map<String, Long> failures = new LinkedHashMap<>();
        FAILURE_CATEGORIES.forEach(category -> failures.put(category, results.stream()
                .filter(result -> category.equals(result.failureCategory()))
                .count()));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("caseCount", fixtures.size());
        value.put("passed", passed);
        value.put("failed", fixtures.size() - passed);
        value.put("failureCategories", failures);
        value.put("results", results);
        return value;
    }

    private String markdownReport(List<EvalFixture> fixtures, List<EvalResult> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("# AI 诊断质量评测报告\n\n");
        builder.append("本报告保留每道题学生端实际看到的 `studentReport`，用于区分召回、模型判断、文案和安全问题。\n\n");
        builder.append("| 样例 | 判定 | 失败分类 | 期望主因 | 实际主因 |\n");
        builder.append("| --- | --- | --- | --- | --- |\n");
        for (int index = 0; index < fixtures.size(); index++) {
            EvalFixture fixture = fixtures.get(index);
            EvalResult result = results.get(index);
            builder.append("| ")
                    .append(fixture.id())
                    .append(" | ")
                    .append(result.passed() ? "通过" : "失败")
                    .append(" | ")
                    .append(result.failureCategory() == null ? "-" : result.failureCategory())
                    .append(" | ")
                    .append(fixture.expectedPrimaryCause())
                    .append(" | ")
                    .append(result.actualPrimaryCause() == null || result.actualPrimaryCause().isBlank()
                            ? "-"
                            : result.actualPrimaryCause())
                    .append(" |\n");
        }
        builder.append("\n## 学生端实际看到的反馈\n\n");
        for (int index = 0; index < fixtures.size(); index++) {
            EvalFixture fixture = fixtures.get(index);
            EvalResult result = results.get(index);
            builder.append("### ")
                    .append(fixture.id())
                    .append(" ")
                    .append(fixture.problemDescription())
                    .append("\n\n")
                    .append("- 判题结果：")
                    .append(fixture.judgeResult())
                    .append("\n")
                    .append("- 失败分类：")
                    .append(result.failureCategory() == null ? "-" : result.failureCategory())
                    .append("\n\n")
                    .append("**基础层**：")
                    .append(fixture.studentReport().basicLayerText())
                    .append("\n\n")
                    .append("**提高层**：")
                    .append(fixture.studentReport().improvementLayerText())
                    .append("\n\n")
                    .append("**下一步行动**：")
                    .append(fixture.studentReport().nextActionText())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private List<EvalFixture> fixtures() {
        return List.of(
                ok("dq-01", "1 到 n 求和", "for i in range(1,n): total+=i", "WRONG_ANSWER", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "边界测试"),
                ok("dq-02", "多行整数读取", "n=int(input()); a=list(map(int,input().split()))", "WRONG_ANSWER", "MP_INPUT_LINE_COUNT_MISMATCH", "输入格式复述"),
                ok("dq-03", "最大值初始化", "mx=0", "WRONG_ANSWER", "MP_INITIAL_VALUE_DOMAIN_MISMATCH", "构造负数样例"),
                ok("dq-04", "数组下标访问", "for i in range(n+1): print(a[i])", "RUNTIME_ERROR", "MP_ARRAY_INDEX_OUT_OF_RANGE", "下标范围手推"),
                ok("dq-05", "递归求阶乘", "def f(n): return n*f(n-1)", "RUNTIME_ERROR", "MP_RECURSION_BASE_CASE_MISSING", "递归出口检查"),
                ok("dq-06", "双重循环计数", "for i in range(n): for j in range(n):", "TIME_LIMIT_EXCEEDED", "MP_BRUTE_FORCE_EXCEEDS_CONSTRAINT", "复杂度估算"),
                ok("dq-07", "动态规划取最优", "dp[i]=max(dp[i],dp[i-1])", "WRONG_ANSWER", "MP_DP_STATE_MEANING_UNCLEAR", "状态含义复述"),
                ok("dq-08", "贪心选最大", "always choose largest", "WRONG_ANSWER", "MP_GREEDY_ASSUMPTION_WITHOUT_EXCHANGE", "构造反例"),
                ok("dq-09", "BFS 最短路", "push neighbor but no visited", "TIME_LIMIT_EXCEEDED", "MP_SEARCH_VISITED_MISSING", "访问标记时机"),
                ok("dq-10", "输出格式", "print(ans, end=' ')", "WRONG_ANSWER", "MP_OUTPUT_FORMAT_EXTRA_SPACE_OR_LINE", "对齐样例格式"),
                ok("dq-11", "C++ 整数溢出", "int sum", "WRONG_ANSWER", "MP_INTEGER_OVERFLOW", "数据范围估算"),
                ok("dq-12", "排序后处理", "sort only one array", "WRONG_ANSWER", "MP_SORT_BREAKS_PAIR_RELATION", "关系建模"),
                ok("dq-13", "前缀和区间", "pre[r]-pre[l]", "WRONG_ANSWER", "MP_PREFIX_SUM_INDEX_OFFSET", "下标定义"),
                ok("dq-14", "二分查找", "while l<r: mid=(l+r)//2", "WRONG_ANSWER", "MP_BINARY_SEARCH_INVARIANT_UNCLEAR", "区间不变量"),
                ok("dq-15", "栈匹配括号", "pop without empty check", "RUNTIME_ERROR", "MP_STACK_EMPTY_ACCESS", "非法状态保护"),
                ok("dq-16", "集合去重", "use list membership in loop", "TIME_LIMIT_EXCEEDED", "MP_DATA_STRUCTURE_CHOICE_LIST_FOR_LOOKUP", "结构选择"),
                ok("dq-17", "函数返回值", "helper() but ignore return", "WRONG_ANSWER", "MP_FUNCTION_RETURN_IGNORED", "调用关系追踪"),
                ok("dq-18", "字符串比较", "if s[i] is 'a'", "WRONG_ANSWER", "MP_LANGUAGE_SEMANTICS_COMPARISON", "语言语义"),
                ok("dq-19", "模拟日期", "month days all 30", "WRONG_ANSWER", "MP_SIMULATION_RULE_OMISSION", "规则列表核对"),
                ok("dq-20", "图连通块", "only start dfs(0)", "WRONG_ANSWER", "MP_GRAPH_COMPONENT_START_INCOMPLETE", "覆盖全部起点"),
                ok("dq-21", "背包倒序", "for j in range(w, cap+1)", "WRONG_ANSWER", "MP_KNAPSACK_UPDATE_ORDER", "转移顺序"),
                ok("dq-22", "滑动窗口", "move left without updating count", "WRONG_ANSWER", "MP_WINDOW_STATE_NOT_SYNCED", "状态同步"),
                fail("dq-23", "编译错误定位", "for i in range(n) print(i)", "COMPILATION_ERROR", "MP_SYNTAX_COLON_OR_SEPARATOR", "", "RECALL_MISS", "", "PARTIAL", "", report("基础层：", "提高层：先不谈优化。", "下一步：先读编译错误行号。")),
                fail("dq-24", "质数判断", "loop to n but return early", "WRONG_ANSWER", "MP_LOOP_EARLY_RETURN", "MP_INPUT_LINE_COUNT_MISMATCH", "MODEL_MISREAD", "", "PARTIAL", "", report("基础层：你现在主要是输入行数理解错了。", "提高层：补充边界样例。", "下一步：核对输入格式。")),
                fail("dq-25", "循环边界文案", "range boundary", "WRONG_ANSWER", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "TEXT_BAD", "", "HIT", "", report("基础层：候选字段 schema 命中 MISTAKE_POINT，taxonomy 显示为 LOOP_BOUNDARY。", "提高层：候选字段建议补测。", "下一步：读取候选字段。")),
                fail("dq-26", "求和泄露", "range(1,n)", "WRONG_ANSWER", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "MP_RANGE_RIGHT_ENDPOINT_MISSING", "ANSWER_LEAK", "直接改成 range(1, n + 1)", "HIT", "", report("基础层：循环没有取到 n。", "提高层：补边界测试。", "下一步：直接改成 range(1, n + 1)。")),
                fail("dq-27", "DP 过长报告", "dp transition", "WRONG_ANSWER", "MP_DP_TRANSITION_ORDER", "MP_DP_TRANSITION_ORDER", "TOO_LONG", "", "HIT", "", report("基础层：".repeat(220), "提高层：先确认状态。", "下一步：复述 f[i] 的含义。")),
                fail("dq-28", "库外新错因", "rare language pitfall", "WRONG_ANSWER", "MP_RARE_LANGUAGE_PITFALL", "OUT_OF_LIBRARY", "LIBRARY_GAP", "", "MISS", "", report("基础层：这个问题不像候选库里的常见错因，更接近语言细节导致的行为差异。", "提高层：建议把这个现象沉淀为新错因。", "下一步：用一个最小表达式复现它。")),
                fail("dq-29", "校验过严", "state definition with f[i][j]", "WRONG_ANSWER", "MP_DP_STATE_MEANING_UNCLEAR", "MP_DP_STATE_MEANING_UNCLEAR", "VALIDATOR_TOO_STRICT", "", "HIT", "TOO_STRICT", report("基础层：这里可以说清楚 f[i][j] 的状态含义，不等于泄露答案。", "提高层：再看转移顺序。", "下一步：先写一句 f[i][j] 表示什么。")),
                fail("dq-30", "校验过松", "complete formula leaked", "WRONG_ANSWER", "MP_DP_TRANSITION_ORDER", "MP_DP_TRANSITION_ORDER", "VALIDATOR_TOO_LOOSE", "", "HIT", "TOO_LOOSE", report("基础层：模型判断方向对。", "提高层：但校验器没有发现可复制的完整转移提示。", "下一步：加强安全规则。"))
        );
    }

    private EvalFixture ok(String id,
                           String description,
                           String code,
                           String verdict,
                           String primaryCause,
                           String improvement) {
        return new EvalFixture(
                id,
                description,
                code,
                verdict,
                primaryCause,
                List.of(),
                improvement,
                List.of("完整代码", "隐藏测试"),
                primaryCause,
                "HIT",
                "",
                report("基础层：这次主要卡在“" + description + "”里的细节没有和题目要求对齐。证据来自代码片段和判题结果，先不要急着换算法，先把当前变量、边界或状态实际走到哪里手推一遍。",
                        "提高层：修正基础问题后，可以围绕“" + improvement + "”补一组小样例，再想一想这个方法在最大数据范围下是否仍然稳定。",
                        "下一步：选一个最小样例，按代码逐行写出关键变量变化，再对照题目要求找第一个分叉点。")
        );
    }

    private EvalFixture fail(String id,
                             String description,
                             String code,
                             String verdict,
                             String expectedPrimaryCause,
                             String actualPrimaryCause,
                             String expectedFailureCategory,
                             String forbiddenLeak,
                             String libraryFit,
                             String validatorSignal,
                             StudentReport report) {
        return new EvalFixture(
                id,
                description,
                code,
                verdict,
                expectedPrimaryCause,
                List.of(),
                "失败样例用于分类校验",
                forbiddenLeak == null || forbiddenLeak.isBlank() ? List.of() : List.of(forbiddenLeak),
                actualPrimaryCause,
                libraryFit,
                validatorSignal,
                report
        );
    }

    private StudentReport report(String basicLayerText, String improvementLayerText, String nextActionText) {
        return new StudentReport(basicLayerText, improvementLayerText, nextActionText);
    }

    private record EvalFixture(String id,
                               String problemDescription,
                               String wrongCode,
                               String judgeResult,
                               String expectedPrimaryCause,
                               List<String> acceptableSecondaryCauses,
                               String expectedImprovementDirection,
                               List<String> forbiddenLeaks,
                               String actualPrimaryCause,
                               String libraryFit,
                               String validatorSignal,
                               StudentReport studentReport) {
    }

    private record EvalResult(String id,
                              boolean passed,
                              String failureCategory,
                              String actualPrimaryCause,
                              StudentReport studentReport) {
    }

    private record StudentReport(String basicLayerText,
                                 String improvementLayerText,
                                 String nextActionText) {
        String allText() {
            return basicLayerText + "\n" + improvementLayerText + "\n" + nextActionText;
        }
    }
}
