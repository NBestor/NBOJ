import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const outputPath = path.join(root, "src", "test", "resources", "diagnosis-eval-fixtures", "student-hint-cases.json");

const parentByFineTag = {
  OFF_BY_ONE: "LOOP_BOUNDARY",
  EMPTY_INPUT: "BOUNDARY_CONDITION",
  MAX_BOUNDARY: "BOUNDARY_CONDITION",
  DUPLICATE_CASE: "BOUNDARY_CONDITION",
  OUTPUT_FORMAT_DETAIL: "IO_FORMAT",
  INPUT_PARSING: "IO_FORMAT",
  INITIAL_STATE: "VARIABLE_INITIALIZATION",
  STATE_RESET: "VARIABLE_INITIALIZATION",
  OVER_SIMULATION: "TIME_COMPLEXITY",
  BRUTE_FORCE_LIMIT: "TIME_COMPLEXITY",
  GREEDY_ASSUMPTION: "ALGORITHM_STRATEGY",
  DP_STATE_DESIGN: "ALGORITHM_STRATEGY",
  SAMPLE_OVERFIT: "SAMPLE_ONLY",
  PARTIAL_FIX_REGRESSION: "NEEDS_MORE_EVIDENCE"
};

const teachingActionByTag = {
  OFF_BY_ONE: "TRACE_VARIABLES",
  INPUT_PARSING: "COMPARE_INPUT_SPEC",
  OUTPUT_FORMAT_DETAIL: "COMPARE_OUTPUT",
  BRUTE_FORCE_LIMIT: "COUNT_COMPLEXITY",
  OVER_SIMULATION: "COUNT_COMPLEXITY",
  MAX_BOUNDARY: "ASK_MIN_CASE",
  DUPLICATE_CASE: "ASK_MIN_CASE",
  EMPTY_INPUT: "ASK_MIN_CASE",
  INITIAL_STATE: "TRACE_STATE",
  STATE_RESET: "TRACE_STATE",
  DP_STATE_DESIGN: "DEFINE_STATE",
  GREEDY_ASSUMPTION: "CHECK_INVARIANT",
  SAMPLE_OVERFIT: "BUILD_COUNTEREXAMPLE",
  PARTIAL_FIX_REGRESSION: "COMPARE_SUBMISSIONS",
  RUNTIME_STABILITY: "CHECK_RUNTIME_GUARDS",
  SPACE_COMPLEXITY: "COMPARE_STRUCTURES",
  SYNTAX_ERROR: "FIX_FIRST_COMPILER_ERROR"
};

const abilityByTag = {
  OFF_BY_ONE: "循环边界追踪",
  INPUT_PARSING: "输入格式阅读",
  OUTPUT_FORMAT_DETAIL: "输出格式核对",
  BRUTE_FORCE_LIMIT: "复杂度估算",
  OVER_SIMULATION: "抽象规律识别",
  MAX_BOUNDARY: "最大规模意识",
  DUPLICATE_CASE: "重复元素建模",
  EMPTY_INPUT: "极小样例意识",
  INITIAL_STATE: "初始状态设定",
  STATE_RESET: "多组数据状态管理",
  DP_STATE_DESIGN: "动态规划状态定义",
  GREEDY_ASSUMPTION: "贪心正确性检验",
  SAMPLE_OVERFIT: "样例外泛化",
  PARTIAL_FIX_REGRESSION: "提交差异复盘",
  RUNTIME_STABILITY: "运行稳定性",
  SPACE_COMPLEXITY: "空间复杂度",
  SYNTAX_ERROR: "语法与编译定位"
};

const mustNotMention = ["完整代码", "参考答案", "隐藏测试点"];

function fixture({
  group,
  variant,
  title,
  description,
  difficulty = "EASY",
  timeLimit = 1000,
  memoryLimit = 65536,
  languageName = "Python 3",
  verdict,
  sourceCode,
  fineTag,
  issueTag,
  scenario,
  headline,
  teacherNote,
  mustMention,
  caseResults = [],
  compileOutput = "",
  errorMessage = "",
  history = null,
  misconception,
  expectedStudentMove
}) {
  const resolvedIssueTag = issueTag ?? parentByFineTag[fineTag] ?? fineTag;
  const teachingAction = teachingActionByTag[fineTag] ?? teachingActionByTag[resolvedIssueTag];
  const acceptableTeachingActions = [...new Set([
    teachingAction,
    ...(verdict === "RUNTIME_ERROR" && fineTag === "OFF_BY_ONE" ? ["CHECK_RUNTIME_GUARDS"] : []),
    ...(fineTag === "MAX_BOUNDARY" ? ["COUNT_COMPLEXITY"] : []),
    ...(fineTag === "EMPTY_INPUT" ? ["CHECK_RUNTIME_GUARDS"] : [])
  ])];
  const id = cases.length + 1;
  const submissionId = 30000 + id;
  return {
    name: `${group}-${String(variant).padStart(2, "0")}`,
    source: "synthetic-student-hint-v1",
    caseId: id,
    problem: {
      id: 70000 + id,
      title,
      description,
      difficulty,
      timeLimit,
      memoryLimit,
      knowledgePoints: [abilityByTag[fineTag] ?? abilityByTag[resolvedIssueTag] ?? "问题定位"],
      algorithmStrategies: strategyFor(resolvedIssueTag, fineTag),
      commonMistakes: [teacherNote],
      boundaryTypes: boundaryTypesFor(fineTag)
    },
    submission: {
      languageName,
      verdict,
      sourceCode: sourceCode.trimEnd() + "\n",
      compileOutput,
      errorMessage
    },
    caseResults,
    baseline: {
      scenario,
      originalIssueTags: [resolvedIssueTag],
      originalFineGrainedTags: fineTag && fineTag !== resolvedIssueTag ? [fineTag] : [],
      analysisHeadline: headline,
      studentHint: firstHintFor(fineTag, resolvedIssueTag)
    },
    expected: {
      issueTags: [resolvedIssueTag],
      fineTags: fineTag && fineTag !== resolvedIssueTag ? [fineTag] : [],
      teachingAction,
      acceptableTeachingActions,
      hintLevel: "L2",
      mustMention,
      mustNotMention,
      requiredEvidenceRefs: evidenceRefsFor(verdict, fineTag, caseResults)
    },
    quality: {
      bugPattern: group,
      misconception,
      expectedStudentMove
    },
    history
  };
}

function strategyFor(issueTag, fineTag) {
  if (issueTag === "TIME_COMPLEXITY") return ["复杂度优化", "减少重复计算"];
  if (issueTag === "ALGORITHM_STRATEGY") return ["证明策略正确性", "构造反例"];
  if (issueTag === "VARIABLE_INITIALIZATION") return ["状态维护", "手推变量变化"];
  if (issueTag === "IO_FORMAT") return ["逐字核对题面格式"];
  if (fineTag === "PARTIAL_FIX_REGRESSION") return ["对比提交差异"];
  return ["最小样例调试"];
}

function boundaryTypesFor(fineTag) {
  if (["EMPTY_INPUT", "OFF_BY_ONE", "MAX_BOUNDARY"].includes(fineTag)) return ["最小规模", "最大规模", "端点"];
  if (fineTag === "DUPLICATE_CASE") return ["重复元素"];
  if (fineTag === "SAMPLE_OVERFIT") return ["样例外输入"];
  return [];
}

function firstHintFor(fineTag, issueTag) {
  if (fineTag === "INPUT_PARSING") return "先重新圈出题面的输入结构，再对照代码每一次读取。";
  if (fineTag === "OUTPUT_FORMAT_DETAIL") return "先逐字比较实际输出和期望输出，尤其是空格与换行。";
  if (issueTag === "TIME_COMPLEXITY") return "先估算最大输入下核心操作会执行多少次。";
  if (issueTag === "ALGORITHM_STRATEGY") return "先构造一个能挑战当前策略的最小反例。";
  if (issueTag === "VARIABLE_INITIALIZATION") return "先手推变量初值和每轮循环后的状态变化。";
  if (fineTag === "PARTIAL_FIX_REGRESSION") return "先对比最近两次提交，找出失败现象变化的位置。";
  return "先构造一个最小样例，手推关键变量变化。";
}

function evidenceRefsFor(verdict, fineTag, caseResults) {
  const refs = [];
  if (verdict === "WRONG_ANSWER") refs.push("verdict:wrong_answer");
  if (verdict === "TIME_LIMIT_EXCEEDED") refs.push("verdict:tle");
  if (verdict === "MEMORY_LIMIT_EXCEEDED") refs.push("verdict:mle");
  if (verdict === "RUNTIME_ERROR") refs.push("verdict:runtime_error");
  if (verdict === "COMPILATION_ERROR") refs.push("verdict:compilation_error");
  if (caseResults.some((item) => item.hidden === true && item.passed === false)) refs.push("judge:hidden_failure");
  if (fineTag === "OUTPUT_FORMAT_DETAIL") refs.push("judge:whitespace_mismatch");
  if (["OFF_BY_ONE", "BRUTE_FORCE_LIMIT", "OVER_SIMULATION"].includes(fineTag)) refs.push("code:loop_present");
  return [...new Set(refs)];
}

function visibleFail(testCaseNumber, inputSnapshot, actualOutput, expectedOutput) {
  return {
    testCaseNumber,
    passed: false,
    hidden: false,
    inputSnapshot,
    actualOutput,
    expectedOutput,
    executionTime: 0.01,
    memoryUsed: 1024
  };
}

function hiddenFail(testCaseNumber) {
  return {
    testCaseNumber,
    passed: false,
    hidden: true,
    inputSnapshot: "",
    actualOutput: "",
    expectedOutput: "",
    executionTime: 0.03,
    memoryUsed: 2048
  };
}

function passedCase(testCaseNumber) {
  return {
    testCaseNumber,
    passed: true,
    hidden: false,
    inputSnapshot: "sample",
    actualOutput: "ok",
    expectedOutput: "ok",
    executionTime: 0.01,
    memoryUsed: 1024
  };
}

const cases = [];

function add(item) {
  cases.push(fixture(item));
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "off-by-one-sum",
    variant: i,
    title: `连续整数求和 ${i}`,
    description: `输入正整数 n，输出从 1 到 n 的整数和。n 最大为 ${1000 * i}。`,
    verdict: "WRONG_ANSWER",
    sourceCode: `
n = int(input())
total = 0
for x in range(1, n):
    total += x
print(total)
`,
    fineTag: "OFF_BY_ONE",
    scenario: "WA",
    headline: "循环终点少处理一次",
    teacherNote: "range 的右端没有包含 n，最后一个数没有被加入。",
    mustMention: ["循环终点", "最后一次"],
    caseResults: [visibleFail(1, "3", "3", "6")],
    misconception: "把 range 右端当成会被包含的值。",
    expectedStudentMove: "列出 n=3 时 x 的取值，确认最后一次循环是否出现。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "off-by-one-index",
    variant: i,
    title: `相邻差值统计 ${i}`,
    description: "输入 n 和 n 个整数，输出相邻两项差的绝对值之和。",
    verdict: "RUNTIME_ERROR",
    sourceCode: `
n = int(input())
a = list(map(int, input().split()))
ans = 0
for i in range(n):
    ans += abs(a[i + 1] - a[i])
print(ans)
`,
    fineTag: "OFF_BY_ONE",
    scenario: "RE",
    headline: "数组下标越过末尾",
    teacherNote: "循环跑到最后一个下标时仍访问 i + 1。",
    mustMention: ["下标", "最后一个元素"],
    caseResults: [visibleFail(1, "3\n1 4 9", "IndexError", "8")],
    errorMessage: "IndexError: list index out of range",
    misconception: "认为每个位置都有下一个元素。",
    expectedStudentMove: "检查 i 取最后一个值时 a[i + 1] 是否存在。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "input-query-count",
    variant: i,
    title: `区间查询读取 ${i}`,
    description: "第一行输入 n 和 q，第二行输入 n 个整数，接下来 q 行每行输入 l r，输出每个区间的元素和。",
    difficulty: "MEDIUM",
    verdict: "WRONG_ANSWER",
    sourceCode: `
n = int(input())
a = list(map(int, input().split()))
l, r = map(int, input().split())
print(sum(a[l - 1:r]))
`,
    fineTag: "INPUT_PARSING",
    scenario: "WA",
    headline: "没有按题面读取 q 次查询",
    teacherNote: "代码把第一行当成只有 n，也只处理了一组查询。",
    mustMention: ["输入结构", "多组查询"],
    caseResults: [visibleFail(1, "5 2\n1 2 3 4 5\n1 3\n2 5", "ValueError", "6\n14")],
    misconception: "把样例中的一次查询误认为题目只有一次查询。",
    expectedStudentMove: "画出每一行输入对应的变量，再确认 q 行循环是否存在。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "input-multiple-tests",
    variant: i,
    title: `多组成绩统计 ${i}`,
    description: "第一行输入 T，之后每组输入 n 和 n 个成绩，输出每组的平均分向下取整。",
    verdict: "WRONG_ANSWER",
    sourceCode: `
n = int(input())
scores = list(map(int, input().split()))
print(sum(scores) // n)
`,
    fineTag: "INPUT_PARSING",
    scenario: "WA",
    headline: "遗漏 T 组数据结构",
    teacherNote: "题面第一行是组数 T，代码直接把它当成 n。",
    mustMention: ["T", "多组数据"],
    caseResults: [visibleFail(1, "2\n3\n80 90 100\n2\n60 70", "1", "90\n65")],
    misconception: "看到第一行数字就默认是 n。",
    expectedStudentMove: "按题面把输入分层，先读 T，再在循环中读每组 n 和数据。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "output-format",
    variant: i,
    title: `原样输出格式 ${i}`,
    description: "输入一个整数，输出该整数本身，输出末尾不要带多余空格。",
    verdict: "WRONG_ANSWER",
    sourceCode: `
x = int(input())
print(x, end=" ")
`,
    fineTag: "OUTPUT_FORMAT_DETAIL",
    scenario: "WA",
    headline: "输出末尾多了空格",
    teacherNote: "实际输出和期望输出只差空白字符。",
    mustMention: ["空格", "实际输出"],
    caseResults: [visibleFail(1, "42", "42 ", "42")],
    misconception: "觉得末尾空格不会影响评测。",
    expectedStudentMove: "把实际输出和期望输出按字符对齐比较。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "brute-count",
    variant: i,
    title: `出现次数统计 ${i}`,
    description: "输入 n 和 n 个整数，输出每个不同整数的出现次数。n 最大为 200000。",
    difficulty: "MEDIUM",
    verdict: "TIME_LIMIT_EXCEEDED",
    sourceCode: `
n = int(input())
a = list(map(int, input().split()))
seen = []
for x in a:
    if x not in seen:
        seen.append(x)
        print(x, a.count(x))
`,
    fineTag: "BRUTE_FORCE_LIMIT",
    scenario: "TLE",
    headline: "循环里重复扫描整段数组",
    teacherNote: "每次 count 都会重新扫数组，最大规模下接近平方级。",
    mustMention: ["复杂度", "重复扫描"],
    caseResults: [hiddenFail(2)],
    misconception: "只看到了 count 写法短，没有估算它在循环中的总成本。",
    expectedStudentMove: "估算 n 次 count 每次扫描 n 个元素时的总操作数。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "nested-loop-limit",
    variant: i,
    title: `两数差统计 ${i}`,
    description: "输入 n 和 n 个整数，统计有多少对 i < j 满足 a[j] - a[i] 等于 k。n 最大为 200000。",
    difficulty: "MEDIUM",
    verdict: "TIME_LIMIT_EXCEEDED",
    sourceCode: `
n, k = map(int, input().split())
a = list(map(int, input().split()))
ans = 0
for i in range(n):
    for j in range(i + 1, n):
        if a[j] - a[i] == k:
            ans += 1
print(ans)
`,
    fineTag: "BRUTE_FORCE_LIMIT",
    scenario: "TLE",
    headline: "双重循环无法承受最大规模",
    teacherNote: "n 最大到 200000 时枚举所有二元组会超时。",
    mustMention: ["双重循环", "最大规模"],
    caseResults: [hiddenFail(3)],
    misconception: "认为样例能过就说明枚举可行。",
    expectedStudentMove: "把 n 代入双重循环的执行次数，判断是否超过时限。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "over-simulation",
    variant: i,
    title: `重复加一模拟 ${i}`,
    description: "给定 a 和 b，输出从 a 变到 b 至少需要多少次加一操作。0 <= a <= b <= 10^12。",
    difficulty: "EASY",
    verdict: "TIME_LIMIT_EXCEEDED",
    sourceCode: `
a, b = map(int, input().split())
steps = 0
while a < b:
    a += 1
    steps += 1
print(steps)
`,
    fineTag: "OVER_SIMULATION",
    scenario: "TLE",
    headline: "逐步模拟没有利用数学关系",
    teacherNote: "范围到 10^12 时逐次加一不可行。",
    mustMention: ["逐步模拟", "输入范围"],
    caseResults: [hiddenFail(4)],
    misconception: "把过程按字面模拟，没有先看数据范围。",
    expectedStudentMove: "比较循环次数和 b-a 的最大可能值。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "max-boundary",
    variant: i,
    title: `平方和最大规模 ${i}`,
    description: "输入 n，输出 1^2 + 2^2 + ... + n^2。n 最大为 10^9，答案对 1000000007 取模。",
    difficulty: "MEDIUM",
    verdict: "TIME_LIMIT_EXCEEDED",
    sourceCode: `
n = int(input())
mod = 1000000007
ans = 0
for x in range(1, n + 1):
    ans = (ans + x * x) % mod
print(ans)
`,
    fineTag: "MAX_BOUNDARY",
    scenario: "TLE",
    headline: "最大 n 下线性循环不可行",
    teacherNote: "代码在小 n 正确，但最大规模需要先估算循环次数。",
    mustMention: ["最大规模", "循环次数"],
    caseResults: [passedCase(1), hiddenFail(5)],
    misconception: "只验证了小样例，忽略 n 的上界。",
    expectedStudentMove: "把 n=10^9 代入循环次数，判断是否需要公式或优化。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "duplicate-case",
    variant: i,
    title: `保留重复票数 ${i}`,
    description: "输入 n 和 n 张投票编号，输出所有编号的总票数，重复编号必须重复计数。",
    verdict: "WRONG_ANSWER",
    sourceCode: `
n = int(input())
votes = list(map(int, input().split()))
print(len(set(votes)))
`,
    fineTag: "DUPLICATE_CASE",
    scenario: "WA",
    headline: "去重破坏了重复计数",
    teacherNote: "题目要求统计总票数，set 会丢掉重复出现。",
    mustMention: ["重复元素", "set"],
    caseResults: [visibleFail(1, "5\n3 3 4 4 4", "2", "5")],
    misconception: "把不同编号数量和总票数混在一起。",
    expectedStudentMove: "用包含重复值的最小样例检查 set 前后信息是否丢失。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "initial-state",
    variant: i,
    title: `最小值查询 ${i}`,
    description: "输入 n 和 n 个正整数，输出其中的最小值。所有数可能大于 100。",
    verdict: "WRONG_ANSWER",
    sourceCode: `
n = int(input())
a = list(map(int, input().split()))
mn = 0
for x in a:
    if x < mn:
        mn = x
print(mn)
`,
    fineTag: "INITIAL_STATE",
    scenario: "WA",
    headline: "最小值初始值不符合数据范围",
    teacherNote: "当所有输入都是正数时，mn 初始为 0 会一直不变。",
    mustMention: ["初始值", "数据范围"],
    caseResults: [visibleFail(1, "3\n5 7 9", "0", "5")],
    misconception: "默认用 0 初始化最小值，没有结合数据范围。",
    expectedStudentMove: "手推所有数都大于 0 时 mn 是否会被更新。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "state-reset",
    variant: i,
    title: `多组最长连续一 ${i}`,
    description: "第一行输入 T，每组输入一个 01 字符串，输出每组最长连续 1 的长度。",
    verdict: "WRONG_ANSWER",
    sourceCode: `
T = int(input())
best = 0
cur = 0
for _ in range(T):
    s = input().strip()
    for ch in s:
        if ch == "1":
            cur += 1
            best = max(best, cur)
        else:
            cur = 0
    print(best)
`,
    fineTag: "STATE_RESET",
    scenario: "WA",
    headline: "多组数据之间状态没有清空",
    teacherNote: "best 和 cur 放在 T 循环外，上一组结果会影响下一组。",
    mustMention: ["状态重置", "多组数据"],
    caseResults: [visibleFail(1, "2\n111\n01", "3\n3", "3\n1")],
    misconception: "认为变量会随着新一组输入自动重新开始。",
    expectedStudentMove: "标出哪些变量属于单组内部状态，确认它们初始化的位置。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "dp-state-design",
    variant: i,
    title: `不相邻选择 ${i}`,
    description: "给定 n 个数，选择若干个数使得任意两个被选位置不相邻，输出最大和。",
    difficulty: "MEDIUM",
    verdict: "WRONG_ANSWER",
    sourceCode: `
n = int(input())
a = list(map(int, input().split()))
dp = [0] * n
dp[0] = a[0]
for i in range(1, n):
    dp[i] = max(dp[i - 1], a[i])
print(dp[n - 1])
`,
    fineTag: "DP_STATE_DESIGN",
    scenario: "WA",
    headline: "状态没有表达选择当前后的限制",
    teacherNote: "dp[i] 只比较前一项和当前值，没有包含 i-2 的可转移信息。",
    mustMention: ["状态定义", "不相邻"],
    caseResults: [visibleFail(1, "4\n2 7 9 3", "9", "11")],
    misconception: "把局部最大值当成了满足约束的全局状态。",
    expectedStudentMove: "用一句话定义 dp[i] 的含义，再检查它是否包含不相邻约束。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "state-transition",
    variant: i,
    title: `爬楼梯计数 ${i}`,
    description: "一次可以爬 1 级或 2 级，输入 n，输出爬到第 n 级的方案数。",
    verdict: "WRONG_ANSWER",
    sourceCode: `
n = int(input())
dp = [0] * (n + 1)
dp[0] = 1
for i in range(1, n + 1):
    dp[i] = dp[i - 1]
print(dp[n])
`,
    fineTag: "DP_STATE_DESIGN",
    scenario: "WA",
    headline: "状态转移漏掉从 i-2 来的方案",
    teacherNote: "题目允许走 2 级，转移只用了 i-1。",
    mustMention: ["状态转移", "两种来源"],
    caseResults: [visibleFail(1, "4", "1", "5")],
    misconception: "只按一种操作写转移，没有回到题目允许的动作集合。",
    expectedStudentMove: "列出到达第 i 级的最后一步可能来自哪些位置。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "greedy-assumption",
    variant: i,
    title: `硬币数量最少 ${i}`,
    description: "给定若干硬币面值和目标金额，输出组成目标金额需要的最少硬币数。面值不保证满足贪心性质。",
    difficulty: "MEDIUM",
    verdict: "WRONG_ANSWER",
    sourceCode: `
n, target = map(int, input().split())
coins = sorted(map(int, input().split()), reverse=True)
cnt = 0
for coin in coins:
    cnt += target // coin
    target %= coin
print(cnt if target == 0 else -1)
`,
    fineTag: "GREEDY_ASSUMPTION",
    scenario: "WA",
    headline: "贪心选择缺少正确性保证",
    teacherNote: "面值任意时，每次选最大硬币不一定得到最少数量。",
    mustMention: ["贪心", "反例"],
    caseResults: [visibleFail(1, "3 6\n1 3 4", "3", "2")],
    misconception: "把常见硬币系统里的经验当成了任意面值的证明。",
    expectedStudentMove: "构造一个最大面值先选反而更差的最小反例。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "sample-overfit",
    variant: i,
    title: `判断偶数 ${i}`,
    description: "输入一个整数 x，如果 x 是偶数输出 YES，否则输出 NO。",
    verdict: "WRONG_ANSWER",
    sourceCode: `
x = int(input())
if x == 2 or x == 4:
    print("YES")
else:
    print("NO")
`,
    fineTag: "SAMPLE_OVERFIT",
    scenario: "WA",
    headline: "代码只覆盖了样例中的偶数",
    teacherNote: "判断条件写死了样例值，没有表达偶数的一般规律。",
    mustMention: ["样例", "泛化"],
    caseResults: [passedCase(1), hiddenFail(6)],
    misconception: "把样例值当成了题目的完整范围。",
    expectedStudentMove: "换一个样例外偶数手推判断条件是否仍然成立。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "empty-input",
    variant: i,
    title: `空序列最大值 ${i}`,
    description: "输入 n 和 n 个整数；当 n=0 时输出 EMPTY，否则输出最大值。",
    verdict: "RUNTIME_ERROR",
    sourceCode: `
n = int(input())
a = list(map(int, input().split()))
print(max(a))
`,
    fineTag: "EMPTY_INPUT",
    scenario: "RE",
    headline: "没有处理 n=0 的极小输入",
    teacherNote: "空列表上调用 max 会触发运行错误。",
    mustMention: ["n=0", "空列表"],
    caseResults: [visibleFail(1, "0\n", "ValueError", "EMPTY")],
    errorMessage: "ValueError: max() arg is an empty sequence",
    misconception: "默认数组里至少有一个元素。",
    expectedStudentMove: "先单独手推 n=0 时每个变量是什么。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "runtime-stability",
    variant: i,
    title: `平均速度 ${i}`,
    description: "输入距离 d 和时间 t，若 t 为 0 输出 INVALID，否则输出平均速度。",
    verdict: "RUNTIME_ERROR",
    sourceCode: `
d, t = map(int, input().split())
print(d // t)
`,
    issueTag: "RUNTIME_STABILITY",
    scenario: "RE",
    headline: "除数为 0 时运行失败",
    teacherNote: "代码没有在除法前处理 t=0。",
    mustMention: ["除零", "运行错误"],
    caseResults: [visibleFail(1, "10 0", "ZeroDivisionError", "INVALID")],
    errorMessage: "ZeroDivisionError: integer division or modulo by zero",
    misconception: "只考虑正常时间，没有检查非法或极端输入。",
    expectedStudentMove: "找出所有可能触发运行错误的表达式，并给出保护条件。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "space-complexity",
    variant: i,
    title: `巨大矩阵边界 ${i}`,
    description: "输入 n，输出 n 行 n 列棋盘的对角线元素个数。n 最大为 200000。",
    difficulty: "MEDIUM",
    verdict: "MEMORY_LIMIT_EXCEEDED",
    sourceCode: `
n = int(input())
grid = [[0] * n for _ in range(n)]
print(n)
`,
    issueTag: "SPACE_COMPLEXITY",
    scenario: "MLE",
    headline: "构造了不必要的巨大二维数组",
    teacherNote: "题目只需要数量，不需要保存 n*n 的矩阵。",
    mustMention: ["内存", "二维数组"],
    caseResults: [hiddenFail(7)],
    misconception: "把概念上的棋盘直接存成完整矩阵。",
    expectedStudentMove: "估算 n*n 个元素需要的内存，再判断是否真的需要保存。"
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "partial-fix-regression",
    variant: i,
    title: `修复后的区间输出 ${i}`,
    description: "输入 n 和数组，输出所有前缀和，每个前缀和一行。",
    verdict: "WRONG_ANSWER",
    sourceCode: `
n = int(input())
a = list(map(int, input().split()))
s = 0
for x in a:
    s += x
print(s)
`,
    fineTag: "PARTIAL_FIX_REGRESSION",
    scenario: "WA",
    headline: "局部修复后输出粒度发生回退",
    teacherNote: "上次可能修了累加逻辑，但现在只输出总和，没有保留每个前缀的输出。",
    mustMention: ["对比提交", "输出粒度"],
    caseResults: [visibleFail(1, "3\n1 2 3", "6", "1\n3\n6")],
    history: {
      previousVerdict: "RUNTIME_ERROR",
      transitionSignal: "评测阶段从 RUNTIME_ERROR 变化为 WRONG_ANSWER",
      recentIssueTags: ["RUNTIME_STABILITY"],
      recentFineGrainedTags: []
    },
    misconception: "修好一个局部问题后，没有重新核对题目完整输出要求。",
    expectedStudentMove: "对比最近两次提交，确认哪处修改改变了输出次数。"
  });
}

validate(cases);
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${JSON.stringify(cases, null, 2)}\n`, "utf8");
console.log(`Generated ${cases.length} student hint eval fixtures at ${path.relative(root, outputPath)}`);

function validate(items) {
  const errors = [];
  if (items.length !== 100) errors.push(`expected 100 cases, got ${items.length}`);
  const names = new Set();
  const distribution = new Map();
  for (const item of items) {
    if (names.has(item.name)) errors.push(`duplicate name ${item.name}`);
    names.add(item.name);
    if (!item.problem.description || item.problem.description.length < 20) errors.push(`short description ${item.name}`);
    if (!item.submission.sourceCode || item.submission.sourceCode.split("\n").length < 3) errors.push(`short source ${item.name}`);
    if (!item.expected.issueTags.length) errors.push(`missing issue tag ${item.name}`);
    if (!item.expected.teachingAction) errors.push(`missing teaching action ${item.name}`);
    if (!item.expected.mustMention.length) errors.push(`missing mustMention ${item.name}`);
    for (const phrase of mustNotMention) {
      if (!item.expected.mustNotMention.includes(phrase)) errors.push(`missing forbidden phrase ${phrase} in ${item.name}`);
    }
    const key = item.quality.bugPattern;
    distribution.set(key, (distribution.get(key) ?? 0) + 1);
  }
  if (distribution.size < 20) errors.push(`expected at least 20 bug patterns, got ${distribution.size}`);
  for (const [key, count] of distribution.entries()) {
    if (count > 5) errors.push(`bug pattern ${key} has too many cases: ${count}`);
  }
  if (errors.length > 0) {
    throw new Error(`Fixture generation failed:\n${errors.join("\n")}`);
  }
}
