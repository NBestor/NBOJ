import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..", "..");
const datasetDir = path.join(root, "online-judge", "tmp", "public-datasets", "codestream");
const outputPath = path.join(
  root,
  "online-judge",
  "src",
  "test",
  "resources",
  "diagnosis-eval-fixtures",
  "student-hint-public-replay-cases.json",
);

const mustNotMention = ["完整代码", "参考答案", "隐藏测试点"];

const verdictMap = {
  Accepted: "ACCEPTED",
  "Wrong Answer": "WRONG_ANSWER",
  "Presentation Error": "WRONG_ANSWER",
  "Time Limit Exceeded": "TIME_LIMIT_EXCEEDED",
  "Memory Limit Exceeded": "MEMORY_LIMIT_EXCEEDED",
  "Runtime Error": "RUNTIME_ERROR",
  "Compilation Error": "COMPILATION_ERROR",
};

const issueByVerdict = {
  ACCEPTED: ["GENERALIZATION_CHECK"],
  WRONG_ANSWER: ["BOUNDARY_CONDITION"],
  TIME_LIMIT_EXCEEDED: ["TIME_COMPLEXITY"],
  MEMORY_LIMIT_EXCEEDED: ["SPACE_COMPLEXITY"],
  RUNTIME_ERROR: ["RUNTIME_STABILITY"],
  COMPILATION_ERROR: ["SYNTAX_ERROR"],
};

const teachingActionByVerdict = {
  ACCEPTED: "EXPLAIN_GENERALITY",
  WRONG_ANSWER: "ASK_MIN_CASE",
  TIME_LIMIT_EXCEEDED: "COUNT_COMPLEXITY",
  MEMORY_LIMIT_EXCEEDED: "COMPARE_STRUCTURES",
  RUNTIME_ERROR: "CHECK_RUNTIME_GUARDS",
  COMPILATION_ERROR: "FIX_FIRST_COMPILER_ERROR",
};

const scenarioByVerdict = {
  ACCEPTED: "AC",
  WRONG_ANSWER: "WA",
  TIME_LIMIT_EXCEEDED: "TLE",
  MEMORY_LIMIT_EXCEEDED: "MLE",
  RUNTIME_ERROR: "RE",
  COMPILATION_ERROR: "CE",
};

const selectedSubmissionIds = [
  "a60846ea-acdf-4932-9f7c-5454d20f9fa9-72120069",
  "02e90273-36ad-430f-9e2c-0f35ce8e8dff-35013842",
  "84215789-797d-4ae5-bec9-070d369dc1ae-81079708",
  "165b675e-7074-40eb-ba97-a7f71a932b79-79508711",
  "d0b7f6e5-7c86-4d8c-8b24-6712be6f5fea-66004265",
  "92900725-3723-4952-8e23-a84392e1d5a4-47984207",
  "8e48de22-5097-4742-be71-07cc4c56d488-93172439",
  "689a9956-dece-4fb3-a326-32585c46e739-38362834",
  "5b7e6d91-7c6c-4b29-9f90-72ecd4d8a59c-56025156",
  "a9dc580c-2911-451a-aa33-42079878676b-98252215",
  "8a702e87-a4ed-4a91-9c1d-3559005e1ceb-87865122",
  "446dbbcd-3ca8-4ed2-b302-7bf0c77509cb-88649231",
  "35c0d252-e77d-4c64-864b-eaa0aa7b5d61-15311316",
  "eeb8f10d-e3d9-4b1e-9149-87ececf9f265-28078363",
  "25c76b94-96f9-42e0-ae5d-d71b3c376bb4-56047553",
  "4a95e189-a55c-49c6-abe8-8c933e89ff68-90685297",
  "f447c677-27f6-410b-9986-d243722e536a-82807137",
  "6f8403ef-6b7f-4914-94df-ceb5117be187-57872775",
  "085c67d8-9ddd-40ed-981b-16c2317433da-1199089",
  "a0ba2b18-155c-4d43-88da-5727f92202a8-81068494",
  "a8f1bf65-d20b-445d-86c7-ec9f1e6c4f96-45119285",
  "73002dd4-8a22-4217-9a24-5de903ae9bdb-18917528",
  "e0b40ecb-497a-4bb5-8bcf-a8797aa1da86-68651737",
  "18f5ded1-6cd4-4b64-80cc-0bed74b0256d-72030145",
  "41177f82-41b7-4e99-8864-10e809ed3ba6-11305032",
];

main();

function main() {
  const problemPath = path.join(datasetDir, "Problem Data.csv");
  const submissionPath = path.join(datasetDir, "Submission Data.csv");
  if (!fs.existsSync(problemPath) || !fs.existsSync(submissionPath)) {
    throw new Error(
      `Missing CodeStream csv files under ${path.relative(root, datasetDir)}. ` +
        "Download the public dataset files before running this generator.",
    );
  }

  const problems = new Map(readCsv(problemPath).map((problem) => [problem.problem_id, problem]));
  const submissions = new Map(readCsv(submissionPath).map((submission) => [submission.submission_id, submission]));
  const fixtures = selectedSubmissionIds.map((submissionId, index) => {
    const submission = submissions.get(submissionId);
    if (!submission) {
      throw new Error(`Missing selected CodeStream submission: ${submissionId}`);
    }
    const problem = problems.get(submission.problem_id);
    if (!problem) {
      throw new Error(`Missing CodeStream problem for submission ${submissionId}: ${submission.problem_id}`);
    }
    return toFixture(index + 1, submission, problem);
  });

  validate(fixtures);
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(fixtures, null, 2)}\n`, "utf8");
  console.log(`Generated ${fixtures.length} public replay fixtures at ${path.relative(root, outputPath)}`);
}

function toFixture(sequence, submission, problem) {
  const normalizedVerdict = normalizeVerdict(submission.final_verdict);
  const trace = parseTrace(submission.verdict_sequence_trace);
  const failedCase = firstFailedCase(trace, problem.evaluation_test_cases, normalizedVerdict);
  const signal = classifySignal(submission, problem, normalizedVerdict);
  const issueTags = issueByVerdict[normalizedVerdict];
  const fineTags = signal.fineTag ? [signal.fineTag] : [];
  const problemTitle = `CodeStream ${problem.problem_id}`;
  const publicId = shortId(submission.submission_id);

  return {
    name: `codestream-${String(sequence).padStart(2, "0")}-${signal.slug}-${publicId}`,
    source: "codestream-mendeley-cc-by-4.0-v1",
    caseId: 50000 + sequence,
    problem: {
      id: 50000 + Number(problem.problem_id.replace(/\D/g, "")),
      title: problemTitle,
      description: truncate(problem.problem_description, 1600),
      difficulty: difficultyFor(problem.problem_description),
      timeLimit: 2,
      memoryLimit: 256,
      knowledgePoints: signal.knowledgePoints,
      algorithmStrategies: signal.algorithmStrategies,
      commonMistakes: signal.commonMistakes,
      boundaryTypes: signal.boundaryTypes,
    },
    submission: {
      languageName: normalizeLanguage(submission.programming_language),
      verdict: normalizedVerdict,
      sourceCode: submission.source_code.trim(),
      compileOutput: normalizedVerdict === "COMPILATION_ERROR" ? "Compilation Error from CodeStream final_verdict" : null,
      errorMessage: normalizedVerdict === "RUNTIME_ERROR" ? "Runtime Error from CodeStream final_verdict" : null,
    },
    caseResults: buildCaseResults(trace, failedCase),
    baseline: {
      scenario: scenarioByVerdict[normalizedVerdict],
      originalIssueTags: [],
      originalFineGrainedTags: [],
      analysisHeadline: signal.headline,
      studentHint: signal.studentHint,
    },
    expected: {
      issueTags,
      fineTags,
      teachingAction: teachingActionByVerdict[normalizedVerdict],
      acceptableTeachingActions: signal.acceptableTeachingActions,
      hintLevel: "L2",
      mustMention: signal.mustMention,
      mustNotMention,
      requiredEvidenceRefs: requiredEvidenceRefs(normalizedVerdict),
      forbiddenIssueTags: [],
      forbiddenFineTags: ["STATE_RESET"],
    },
    quality: {
      bugPattern: signal.slug,
      misconception: signal.misconception,
      expectedStudentMove: signal.expectedStudentMove,
    },
    history: buildHistory(submission, normalizedVerdict),
  };
}

function classifySignal(submission, problem, verdict) {
  const problemText = problem.problem_description.toLowerCase();
  const source = submission.source_code.toLowerCase();
  if (verdict === "COMPILATION_ERROR") {
    return {
      slug: "public-compile-error",
      fineTag: null,
      headline: "公开提交显示编译阶段失败",
      studentHint: "先定位第一条编译错误，把语言、类名、方法名和变量声明修到能编译，再回到算法逻辑。",
      mustMention: ["编译", "第一条错误"],
      knowledgePoints: ["syntax"],
      algorithmStrategies: [],
      commonMistakes: ["syntax_or_language_mismatch"],
      boundaryTypes: [],
      acceptableTeachingActions: ["FIX_FIRST_COMPILER_ERROR"],
      misconception: "还没有把程序放到可编译状态，就开始判断算法对错。",
      expectedStudentMove: "先只处理编译器报告的第一处错误，重新提交后再观察 verdict 是否变化。",
    };
  }
  if (verdict === "RUNTIME_ERROR") {
    return {
      slug: "public-runtime-error",
      fineTag: null,
      headline: "公开提交显示运行时失败",
      studentHint: "先找最可能越界、空结构或非法访问的位置，用最小输入手推这些变量的范围。",
      mustMention: ["运行", "越界"],
      knowledgePoints: ["runtime guards"],
      algorithmStrategies: [],
      commonMistakes: ["missing_runtime_guard"],
      boundaryTypes: ["empty_or_index_boundary"],
      acceptableTeachingActions: ["CHECK_RUNTIME_GUARDS", "ASK_MIN_CASE"],
      misconception: "默认输入或下标总是处在正常范围内，没有给极端情况留保护。",
      expectedStudentMove: "列出数组下标、栈顶读取、除法或递归出口这些危险点，并构造最小触发样例。",
    };
  }
  if (verdict === "TIME_LIMIT_EXCEEDED") {
    const bruteForce = source.includes("sort(") || source.includes("for(") || source.includes("for (") || source.includes("while");
    return {
      slug: bruteForce ? "public-time-complexity-loop" : "public-time-complexity",
      fineTag: bruteForce ? "BRUTE_FORCE_LIMIT" : null,
      headline: "公开提交显示最大规模下超时",
      studentHint: "先把题目最大 n、m 代入核心循环次数，判断当前做法是不是在重复扫描或重复排序。",
      mustMention: ["复杂度", "最大规模"],
      knowledgePoints: ["complexity"],
      algorithmStrategies: ["complexity estimation"],
      commonMistakes: ["brute_force_on_large_input"],
      boundaryTypes: ["max_input_size"],
      acceptableTeachingActions: ["COUNT_COMPLEXITY"],
      misconception: "样例或前几个测试点能过，就误以为最大规模下同样能跑完。",
      expectedStudentMove: "写出核心操作的次数估算，并标记哪一层循环或哪次排序随输入规模增长。",
    };
  }
  if (verdict === "MEMORY_LIMIT_EXCEEDED") {
    return {
      slug: "public-space-complexity",
      fineTag: null,
      headline: "公开提交显示内存超限",
      studentHint: "先估算当前数组、矩阵或图结构会占多少空间，再判断是否真的需要保存所有状态。",
      mustMention: ["内存", "空间"],
      knowledgePoints: ["space complexity"],
      algorithmStrategies: ["storage estimation"],
      commonMistakes: ["oversized_allocation"],
      boundaryTypes: ["max_input_size"],
      acceptableTeachingActions: ["COMPARE_STRUCTURES"],
      misconception: "把题目中的概念对象直接完整存下来，没有先估算空间成本。",
      expectedStudentMove: "按最大 n、m 估算主要容器的元素数量和内存，再寻找可压缩或按需处理的状态。",
    };
  }
  if (verdict === "ACCEPTED") {
    return {
      slug: "public-accepted-generalization",
      fineTag: null,
      headline: "公开提交已通过，适合进入复盘",
      studentHint: "这次提交已经通过，下一步用自己的话解释为什么它能覆盖边界和最大规模。",
      mustMention: ["通过", "复盘"],
      knowledgePoints: ["generalization"],
      algorithmStrategies: ["solution explanation"],
      commonMistakes: [],
      boundaryTypes: ["generalization_check"],
      acceptableTeachingActions: ["EXPLAIN_GENERALITY"],
      misconception: "通过后只看结果，不复盘代码为什么对以及适用边界。",
      expectedStudentMove: "写出算法不变量、复杂度和一个边界样例，确认不是偶然通过。",
    };
  }
  if (problemText.includes("print") || problemText.includes("output") || submission.final_verdict === "Presentation Error") {
    return {
      slug: "public-output-format-or-boundary",
      fineTag: "OUTPUT_FORMAT_DETAIL",
      headline: "公开提交显示输出或细节与评测不一致",
      studentHint: "先逐字符核对题目的输出要求、换行和空格，再看失败测试前后的实际输出差异。",
      mustMention: ["输出", "格式"],
      knowledgePoints: ["output format"],
      algorithmStrategies: [],
      commonMistakes: ["format_detail_mismatch"],
      boundaryTypes: ["format_boundary"],
      acceptableTeachingActions: ["COMPARE_OUTPUT", "ASK_MIN_CASE"],
      misconception: "认为空格、换行或输出顺序这类细节不会影响评测。",
      expectedStudentMove: "用一个最小样例手动写出期望输出，并和程序输出逐字符对齐。",
    };
  }
  return {
    slug: "public-wrong-answer-boundary",
    fineTag: "SAMPLE_OVERFIT",
    headline: "公开提交显示部分测试点答案错误",
    studentHint: "先不要急着改整段代码，挑一个最小反例手推关键变量，看哪一步开始偏离题意。",
    mustMention: ["反例", "手推"],
    knowledgePoints: ["correctness debugging"],
    algorithmStrategies: ["counterexample"],
    commonMistakes: ["hidden_case_generalization"],
    boundaryTypes: ["hidden_case"],
    acceptableTeachingActions: ["ASK_MIN_CASE", "BUILD_COUNTEREXAMPLE"],
    misconception: "能通过部分测试点后，忽略了隐藏边界或题意细节。",
    expectedStudentMove: "根据题目约束构造一个样例外的小输入，逐步比较人工结果和程序状态。",
  };
}

function buildCaseResults(trace, failedCase) {
  if (trace.length === 0) {
    return [];
  }
  return trace.slice(0, 8).map((verdict, index) => {
    const passed = verdict === "Accepted";
    const isFirstFailed = !passed && index === trace.findIndex((item) => item !== "Accepted");
    return {
      testCaseNumber: index + 1,
      passed,
      hidden: index > 0,
      inputSnapshot: isFirstFailed ? failedCase.input : null,
      actualOutput: isFirstFailed ? displayActual(verdict) : null,
      expectedOutput: isFirstFailed ? failedCase.output : null,
      executionTime: verdict === "Time Limit Exceeded" ? 2.5 : 0.05,
      memoryUsed: verdict === "Memory Limit Exceeded" ? 300000 : 1024,
    };
  });
}

function firstFailedCase(trace, evaluationTestCases, verdict) {
  const fallback = { input: null, output: null };
  if (verdict === "ACCEPTED" || verdict === "COMPILATION_ERROR") {
    return fallback;
  }
  try {
    const cases = JSON.parse(evaluationTestCases);
    const index = Math.max(0, trace.findIndex((item) => item !== "Accepted"));
    return cases[index] ?? cases[0] ?? fallback;
  } catch {
    return fallback;
  }
}

function displayActual(verdict) {
  if (verdict === "Accepted") {
    return null;
  }
  if (verdict === "Time Limit Exceeded") {
    return "Time Limit Exceeded";
  }
  if (verdict === "Memory Limit Exceeded") {
    return "Memory Limit Exceeded";
  }
  if (verdict === "Runtime Error") {
    return "Runtime Error";
  }
  if (verdict === "Compilation Error") {
    return "Compilation Error";
  }
  return verdict;
}

function buildHistory(submission, verdict) {
  const attempt = Number.parseInt(submission.attempt_number, 10);
  if (!Number.isFinite(attempt) || attempt <= 1 || verdict === "ACCEPTED") {
    return null;
  }
  return {
    previousVerdict: "UNKNOWN",
    transitionSignal: `CodeStream attempt ${attempt} for the same anonymized user-problem pair remains ${verdict}`,
    recentIssueTags: ["NEEDS_MORE_EVIDENCE"],
    recentFineGrainedTags: [],
  };
}

function requiredEvidenceRefs(verdict) {
  if (verdict === "COMPILATION_ERROR") return ["verdict:compilation_error"];
  if (verdict === "RUNTIME_ERROR") return ["verdict:runtime_error"];
  if (verdict === "TIME_LIMIT_EXCEEDED") return ["verdict:tle"];
  if (verdict === "MEMORY_LIMIT_EXCEEDED") return ["verdict:mle"];
  if (verdict === "ACCEPTED") return ["verdict:accepted"];
  return ["verdict:wrong_answer"];
}

function normalizeVerdict(verdict) {
  const normalized = verdictMap[verdict];
  if (!normalized) {
    throw new Error(`Unsupported CodeStream verdict: ${verdict}`);
  }
  return normalized;
}

function normalizeLanguage(language) {
  if (language === "c++") return "C++";
  if (language === "c") return "C";
  if (language === "java") return "Java";
  return language;
}

function difficultyFor(description) {
  const text = description.toLowerCase();
  if (text.includes("100000") || text.includes("graph") || text.includes("dynamic") || text.includes("shortest")) {
    return "MEDIUM";
  }
  return "EASY";
}

function parseTrace(value) {
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function shortId(value) {
  return value.split("-").at(-1)?.slice(0, 8) ?? value.slice(0, 8);
}

function truncate(value, limit) {
  if (value.length <= limit) return value;
  return `${value.slice(0, limit - 3)}...`;
}

function readCsv(filePath) {
  const text = fs.readFileSync(filePath, "utf8");
  const rows = [];
  let row = [];
  let cell = "";
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (quoted) {
      if (char === "\"") {
        if (text[index + 1] === "\"") {
          cell += "\"";
          index += 1;
        } else {
          quoted = false;
        }
      } else {
        cell += char;
      }
      continue;
    }
    if (char === "\"") {
      quoted = true;
    } else if (char === ",") {
      row.push(cell);
      cell = "";
    } else if (char === "\n") {
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else if (char !== "\r") {
      cell += char;
    }
  }
  if (cell.length > 0 || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  const [headers, ...dataRows] = rows;
  return dataRows
    .filter((dataRow) => dataRow.some((value) => value !== ""))
    .map((dataRow) => Object.fromEntries(headers.map((header, index) => [header, dataRow[index] ?? ""])));
}

function validate(items) {
  const errors = [];
  const names = new Set();
  const counts = new Map();
  if (items.length !== selectedSubmissionIds.length) {
    errors.push(`expected ${selectedSubmissionIds.length} cases, got ${items.length}`);
  }
  for (const item of items) {
    if (names.has(item.name)) errors.push(`duplicate name ${item.name}`);
    names.add(item.name);
    if (item.source !== "codestream-mendeley-cc-by-4.0-v1") errors.push(`bad source ${item.name}`);
    if (!item.problem.description || item.problem.description.length < 80) errors.push(`short problem ${item.name}`);
    if (!item.submission.sourceCode || item.submission.sourceCode.length < 80) errors.push(`short code ${item.name}`);
    if (!item.expected.issueTags.length) errors.push(`missing issue tags ${item.name}`);
    if (!item.expected.teachingAction) errors.push(`missing teaching action ${item.name}`);
    if (!item.expected.mustMention.length) errors.push(`missing mustMention ${item.name}`);
    for (const phrase of mustNotMention) {
      if (!item.expected.mustNotMention.includes(phrase)) errors.push(`missing forbidden phrase ${phrase} in ${item.name}`);
    }
    if (/user_\d+/.test(JSON.stringify(item))) errors.push(`raw user id leaked ${item.name}`);
    counts.set(item.submission.verdict, (counts.get(item.submission.verdict) ?? 0) + 1);
  }
  for (const verdict of ["ACCEPTED", "WRONG_ANSWER", "TIME_LIMIT_EXCEEDED", "RUNTIME_ERROR", "COMPILATION_ERROR"]) {
    if (!counts.has(verdict)) errors.push(`missing verdict bucket ${verdict}`);
  }
  if (!counts.has("MEMORY_LIMIT_EXCEEDED")) errors.push("missing verdict bucket MEMORY_LIMIT_EXCEEDED");
  if (errors.length > 0) {
    throw new Error(`Public replay fixture generation failed:\n${errors.join("\n")}`);
  }
}
