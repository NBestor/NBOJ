import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const outputPath = path.join(root, "src", "test", "resources", "diagnosis-eval-fixtures", "student-hint-negative-cases.json");

const mustNotMention = ["瀹屾暣浠ｇ爜", "鍙傝€冪瓟妗?", "闅愯棌娴嬭瘯鐐?"];
const cases = [];

function passedCase(testCaseNumber = 1) {
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

function negative({
  group,
  variant,
  title,
  description,
  verdict = "ACCEPTED",
  sourceCode,
  forbiddenIssueTags = [],
  forbiddenFineTags = [],
  requiredIssueTags = ["GENERALIZATION_CHECK"],
  requiredFineTags = [],
  requiredEvidenceRefs = ["verdict:accepted"],
  caseResults = [passedCase()],
  misconception,
  expectedStudentMove
}) {
  const id = cases.length + 1;
  return {
    name: `${group}-${String(variant).padStart(2, "0")}`,
    source: "synthetic-student-hint-negative-v1",
    caseId: id,
    problem: {
      id: 80000 + id,
      title,
      description,
      difficulty: "EASY",
      timeLimit: 1000,
      memoryLimit: 65536,
      knowledgePoints: ["negative-control"],
      algorithmStrategies: ["avoid-over-tagging"],
      commonMistakes: ["This fixture checks that similar code is not over-diagnosed."],
      boundaryTypes: []
    },
    submission: {
      languageName: "Python 3",
      verdict,
      sourceCode: sourceCode.trimEnd() + "\n",
      compileOutput: "",
      errorMessage: ""
    },
    caseResults,
    baseline: {
      scenario: verdict,
      originalIssueTags: [],
      originalFineGrainedTags: [],
      analysisHeadline: "negative control",
      studentHint: "Keep the diagnosis conservative and review why this code should not receive the forbidden label."
    },
    expected: {
      issueTags: requiredIssueTags,
      fineTags: requiredFineTags,
      teachingAction: "EXPLAIN_GENERALITY",
      acceptableTeachingActions: ["EXPLAIN_GENERALITY", "COLLECT_EVIDENCE"],
      hintLevel: "L2",
      mustMention: ["review"],
      mustNotMention,
      requiredEvidenceRefs,
      forbiddenIssueTags,
      forbiddenFineTags
    },
    quality: {
      bugPattern: group,
      misconception,
      expectedStudentMove
    },
    history: null
  };
}

function add(item) {
  cases.push(negative(item));
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "accepted-input-parsing",
    variant: i,
    title: `Accepted input parser ${i}`,
    description: "Read n and n integers, then output their sum.",
    sourceCode: `
n = int(input())
a = list(map(int, input().split()))
print(sum(a))
`,
    forbiddenIssueTags: ["IO_FORMAT"],
    forbiddenFineTags: ["INPUT_PARSING"],
    misconception: "The analyzer may confuse ordinary input parsing with an input-format bug.",
    expectedStudentMove: "Treat normal parsing code as background evidence only."
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "accepted-dp-previous",
    variant: i,
    title: `Accepted DP previous state ${i}`,
    description: "Count ways to climb n steps using one or two steps each time.",
    sourceCode: `
n = int(input())
dp = [0] * (n + 1)
dp[0] = 1
for i in range(1, n + 1):
    dp[i] += dp[i - 1]
    if i >= 2:
        dp[i] += dp[i - 2]
print(dp[n])
`,
    forbiddenIssueTags: ["LOOP_BOUNDARY"],
    forbiddenFineTags: ["OFF_BY_ONE"],
    misconception: "The analyzer may treat normal dp[i - 1] transitions as off-by-one.",
    expectedStudentMove: "Recognize previous-state references as normal DP evidence."
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "accepted-accumulator",
    variant: i,
    title: `Accepted accumulator ${i}`,
    description: "Read n and output the sum from 0 to n - 1.",
    sourceCode: `
n = int(input())
answer = 0
for i in range(n):
    answer += i
print(answer)
`,
    forbiddenIssueTags: ["VARIABLE_INITIALIZATION"],
    forbiddenFineTags: ["INITIAL_STATE"],
    misconception: "The analyzer may treat a normal zero accumulator as a bad initial state.",
    expectedStudentMove: "Only promote initial-state labels when the variable role makes zero suspicious."
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "accepted-hidden-pass",
    variant: i,
    title: `Accepted hidden pass ${i}`,
    description: "Accepted code with visible and hidden cases all passing.",
    sourceCode: `
x = int(input())
print("YES" if x % 2 == 0 else "NO")
`,
    caseResults: [passedCase(1), {...passedCase(2), hidden: true}],
    forbiddenIssueTags: ["SAMPLE_ONLY", "BOUNDARY_CONDITION"],
    forbiddenFineTags: ["SAMPLE_OVERFIT"],
    misconception: "The analyzer may confuse hidden-case presence with hidden-case failure.",
    expectedStudentMove: "Only failed hidden cases should support sample-overfit concerns."
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "tle-not-sample-overfit",
    variant: i,
    title: `TLE is not sample overfit ${i}`,
    description: "A slow but general simulation reaches the time limit on large input.",
    verdict: "TIME_LIMIT_EXCEEDED",
    sourceCode: `
n = int(input())
total = 0
for i in range(n):
    total += i
print(total)
`,
    requiredIssueTags: ["TIME_COMPLEXITY"],
    requiredFineTags: [],
    requiredEvidenceRefs: ["verdict:tle"],
    caseResults: [
      passedCase(1),
      {
        testCaseNumber: 2,
        passed: false,
        hidden: true,
        inputSnapshot: "",
        actualOutput: "",
        expectedOutput: "",
        executionTime: 2.5,
        memoryUsed: 1024
      }
    ],
    forbiddenIssueTags: ["SAMPLE_ONLY"],
    forbiddenFineTags: ["SAMPLE_OVERFIT", "BRUTE_FORCE_LIMIT"],
    misconception: "The analyzer may label every hidden failure as sample overfit, even for TLE.",
    expectedStudentMove: "Keep TLE diagnosis at complexity level unless code shape gives stronger fine evidence."
  });
}

for (let i = 1; i <= 5; i += 1) {
  add({
    group: "accepted-dedupe-intended",
    variant: i,
    title: `Accepted dedupe intended ${i}`,
    description: "Read n ids and output the number of distinct ids.",
    sourceCode: `
n = int(input())
ids = list(map(int, input().split()))
print(len(set(ids)))
`,
    forbiddenIssueTags: ["BOUNDARY_CONDITION"],
    forbiddenFineTags: ["DUPLICATE_CASE"],
    misconception: "The analyzer may assume every set() use destroys required duplicates.",
    expectedStudentMove: "Use the problem statement before promoting dedupe to a duplicate-case bug."
  });
}

validate(cases);
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${JSON.stringify(cases, null, 2)}\n`, "utf8");
console.log(`Generated ${cases.length} negative student hint fixtures at ${path.relative(root, outputPath)}`);

function validate(items) {
  const errors = [];
  if (items.length !== 30) errors.push(`expected 30 cases, got ${items.length}`);
  const names = new Set();
  const distribution = new Map();
  for (const item of items) {
    if (names.has(item.name)) errors.push(`duplicate name ${item.name}`);
    names.add(item.name);
    if (!item.expected.forbiddenIssueTags.length && !item.expected.forbiddenFineTags.length) {
      errors.push(`missing forbidden tags ${item.name}`);
    }
    if (!item.submission.sourceCode || item.submission.sourceCode.split("\n").length < 3) {
      errors.push(`short source ${item.name}`);
    }
    const key = item.quality.bugPattern;
    distribution.set(key, (distribution.get(key) ?? 0) + 1);
  }
  if (distribution.size !== 6) errors.push(`expected 6 negative patterns, got ${distribution.size}`);
  for (const [key, count] of distribution.entries()) {
    if (count !== 5) errors.push(`negative pattern ${key} expected 5 cases, got ${count}`);
  }
  if (errors.length > 0) {
    throw new Error(`Negative fixture generation failed:\n${errors.join("\n")}`);
  }
}
