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
  "student-hint-public-attempt-chain-cases.json",
);

const mustNotMention = ["complete code", "reference answer", "hidden test"];

const verdictMap = {
  Accepted: "ACCEPTED",
  "Wrong Answer": "WRONG_ANSWER",
  "Presentation Error": "WRONG_ANSWER",
  "Time Limit Exceeded": "TIME_LIMIT_EXCEEDED",
  "Memory Limit Exceeded": "MEMORY_LIMIT_EXCEEDED",
  "Runtime Error": "RUNTIME_ERROR",
  "Compilation Error": "COMPILATION_ERROR",
};

const scenarioByVerdict = {
  ACCEPTED: "AC",
  WRONG_ANSWER: "WA",
  TIME_LIMIT_EXCEEDED: "TLE",
  MEMORY_LIMIT_EXCEEDED: "MLE",
  RUNTIME_ERROR: "RE",
  COMPILATION_ERROR: "CE",
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

const selectedTransitions = [
  {
    currentSubmissionId: "d02e4ef3-e47b-4c1e-8df3-8180ab4b6615-69600866",
    expectedPhase: "FIXED_COMPILATION",
  },
  {
    currentSubmissionId: "17bb0d2d-dba5-4143-bc7c-72c1d086e7f8-20173854",
    expectedPhase: "REPEATED_STUCK",
    repeatedIssueCount: 24,
    needsTeacherAttention: true,
  },
  {
    currentSubmissionId: "47367c18-1e40-47b7-9ae8-d07f428c114d-55707130",
    expectedPhase: "RUNTIME_FIXED_CORRECTNESS_REMAINS",
  },
  {
    currentSubmissionId: "fa608b3b-ac68-4e92-ab87-cb30daf772b6-8349110",
    expectedPhase: "ACCEPTED_AFTER_FIX",
  },
  {
    currentSubmissionId: "20b127af-c579-4823-9eac-b095a4de9ff1-17910352",
    expectedPhase: "REGRESSION",
    needsTeacherAttention: true,
  },
  {
    currentSubmissionId: "ec34423d-f768-4202-b059-1587664928f6-90336212",
    expectedPhase: "FIXED_COMPILATION",
  },
  {
    currentSubmissionId: "d85a58df-3eb8-41c9-9487-ceb0dc1c4548-29096238",
    expectedPhase: "REGRESSION",
    needsTeacherAttention: true,
  },
  {
    currentSubmissionId: "07685e11-d38d-40d0-ae47-71a7d7aa6b93-90176407",
    expectedPhase: "ACCEPTED_AFTER_FIX",
  },
  {
    currentSubmissionId: "0e851897-e292-44fe-b25e-5a3119425e67-35544776",
    expectedPhase: "REGRESSION",
    needsTeacherAttention: true,
  },
  {
    currentSubmissionId: "5eb869e6-771f-4f1a-b0b6-3b40f0e98fa3-26436302",
    expectedPhase: "ACCEPTED_AFTER_FIX",
  },
  {
    currentSubmissionId: "5626a0b1-12ff-43ff-ba23-51a6156dbf8f-78098548",
    expectedPhase: "REPEATED_STUCK",
    repeatedIssueCount: 17,
    needsTeacherAttention: true,
  },
  {
    currentSubmissionId: "19e416aa-0428-45cd-8f6d-80fd9fc8b105-6353581",
    expectedPhase: "ACCEPTED_AFTER_FIX",
  },
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
  const submissions = readCsv(submissionPath);
  const groups = groupSubmissions(submissions);
  const submissionById = new Map(submissions.map((submission) => [submission.submission_id, submission]));
  const fixtures = selectedTransitions.map((selection, index) => {
    const current = submissionById.get(selection.currentSubmissionId);
    if (!current) {
      throw new Error(`Missing selected CodeStream submission: ${selection.currentSubmissionId}`);
    }
    const group = groups.get(groupKey(current));
    const previous = previousSubmission(group, current);
    if (!previous) {
      throw new Error(`Selected transition has no previous attempt: ${selection.currentSubmissionId}`);
    }
    const problem = problems.get(current.problem_id);
    if (!problem) {
      throw new Error(`Missing CodeStream problem for submission ${selection.currentSubmissionId}: ${current.problem_id}`);
    }
    return toFixture(index + 1, current, previous, problem, selection);
  });

  validate(fixtures);
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(fixtures, null, 2)}\n`, "utf8");
  console.log(`Generated ${fixtures.length} public attempt-chain fixtures at ${path.relative(root, outputPath)}`);
}

function toFixture(sequence, current, previous, problem, selection) {
  const currentVerdict = normalizeVerdict(current.final_verdict);
  const previousVerdict = normalizeVerdict(previous.final_verdict);
  const signal = classifyTransition(currentVerdict, previousVerdict, selection.expectedPhase);
  const trace = parseTrace(current.verdict_sequence_trace);
  const failedCase = firstFailedCase(trace, problem.evaluation_test_cases, currentVerdict);
  const publicId = shortId(current.submission_id);

  return {
    name: `codestream-chain-${String(sequence).padStart(2, "0")}-${selection.expectedPhase.toLowerCase()}-${publicId}`,
    source: "codestream-mendeley-cc-by-4.0-v1",
    caseId: 60000 + sequence,
    problem: {
      id: 60000 + Number(problem.problem_id.replace(/\D/g, "")),
      title: `CodeStream ${problem.problem_id}`,
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
      languageName: normalizeLanguage(current.programming_language),
      verdict: currentVerdict,
      sourceCode: current.source_code.trim(),
      compileOutput: currentVerdict === "COMPILATION_ERROR" ? "Compilation Error from CodeStream final_verdict" : null,
      errorMessage: currentVerdict === "RUNTIME_ERROR" ? "Runtime Error from CodeStream final_verdict" : null,
    },
    caseResults: buildCaseResults(trace, failedCase),
    baseline: {
      scenario: scenarioByVerdict[currentVerdict],
      originalIssueTags: [],
      originalFineGrainedTags: [],
      analysisHeadline: signal.headline,
      studentHint: signal.studentHint,
    },
    expected: {
      issueTags: issueByVerdict[currentVerdict],
      fineTags: signal.fineTags,
      teachingAction: teachingActionByVerdict[currentVerdict],
      acceptableTeachingActions: signal.acceptableTeachingActions,
      hintLevel: "L2",
      mustMention: signal.mustMention,
      mustNotMention,
      requiredEvidenceRefs: signal.requiredEvidenceRefs,
      forbiddenIssueTags: [],
      forbiddenFineTags: ["STATE_RESET"],
      trajectoryPhase: selection.expectedPhase,
      needsTeacherAttention: Boolean(selection.needsTeacherAttention),
    },
    quality: {
      bugPattern: `public-chain-${selection.expectedPhase.toLowerCase()}`,
      misconception: signal.misconception,
      expectedStudentMove: signal.expectedStudentMove,
    },
    history: {
      previousVerdict,
      transitionSignal: `Attempt ${previous.attempt_number} ${previousVerdict} -> attempt ${current.attempt_number} ${currentVerdict}`,
      recentIssueTags: issueByVerdict[previousVerdict] ?? ["NEEDS_MORE_EVIDENCE"],
      recentFineGrainedTags: [],
      repeatedIssueTag: repeatedTagFor(currentVerdict),
      repeatedFineGrainedTag: null,
      repeatedIssueCount: selection.repeatedIssueCount ?? sameVerdictRunLength(current, groupSubmissions.cached.get(groupKey(current))),
      repeatedFineGrainedIssueCount: null,
    },
  };
}

function classifyTransition(currentVerdict, previousVerdict, phase) {
  if (phase === "FIXED_COMPILATION") {
    return {
      headline: "Public attempt chain shows compilation was fixed, but the solution still needs correctness evidence.",
      studentHint: "Acknowledge that compilation has been fixed, then ask the student to test one minimal case for the new verdict.",
      mustMention: ["compile", "current verdict"],
      fineTags: [],
      requiredEvidenceRefs: ["history:fixed_compilation"],
      acceptableTeachingActions: ["ASK_MIN_CASE", "COMPARE_OUTPUT", "CHECK_RUNTIME_GUARDS"],
      knowledgePoints: ["debugging sequence"],
      algorithmStrategies: ["minimal verification"],
      commonMistakes: ["switching_from_syntax_to_correctness_too_late"],
      boundaryTypes: ["current_verdict"],
      misconception: "After fixing compilation, the student may assume the main problem is solved.",
      expectedStudentMove: "Separate the syntax fix from the remaining verdict and verify the smallest failing behavior.",
    };
  }
  if (phase === "REPEATED_STUCK") {
    return {
      headline: "Public attempt chain shows the same failure category repeated many times.",
      studentHint: "Stop broad edits and reduce the task to one failing example, one key variable, and one expected output comparison.",
      mustMention: ["same failure", "minimal case"],
      fineTags: [],
      requiredEvidenceRefs: ["history:repeated_stuck"],
      acceptableTeachingActions: ["ASK_MIN_CASE", "TRACE_VARIABLES", "BUILD_COUNTEREXAMPLE"],
      knowledgePoints: ["debugging sequence"],
      algorithmStrategies: ["trace one case"],
      commonMistakes: ["blind_repeated_edits"],
      boundaryTypes: ["repeated_failure"],
      misconception: "Repeated submissions are being used as the debugger instead of isolating the wrong assumption.",
      expectedStudentMove: "Use one small failing input and write the expected intermediate state before changing code again.",
    };
  }
  if (phase === "RUNTIME_FIXED_CORRECTNESS_REMAINS") {
    return {
      headline: "Public attempt chain shows runtime instability moved into a correctness failure.",
      studentHint: "Keep the runtime guard that stopped the crash, then compare the answer on a small case.",
      mustMention: ["runtime", "correctness"],
      fineTags: [],
      requiredEvidenceRefs: ["history:runtime_fixed_correctness_remains"],
      acceptableTeachingActions: ["ASK_MIN_CASE", "CHECK_RUNTIME_GUARDS"],
      knowledgePoints: ["runtime guards", "correctness debugging"],
      algorithmStrategies: ["guard then verify"],
      commonMistakes: ["treating_no_crash_as_correct"],
      boundaryTypes: ["runtime_to_correctness"],
      misconception: "No crash is mistaken for a correct algorithm.",
      expectedStudentMove: "Explain which guard fixed the crash and then test whether the computed answer matches the expected answer.",
    };
  }
  if (phase === "ACCEPTED_AFTER_FIX") {
    return {
      headline: "Public attempt chain shows a failing submission moved to accepted.",
      studentHint: "Now that it passes, review what changed and why the fix covers the boundary or invariant.",
      mustMention: ["accepted", "review"],
      fineTags: [],
      requiredEvidenceRefs: ["history:accepted_after_fix"],
      acceptableTeachingActions: ["EXPLAIN_GENERALITY"],
      knowledgePoints: ["generalization"],
      algorithmStrategies: ["solution explanation"],
      commonMistakes: ["not_reflecting_after_accept"],
      boundaryTypes: ["generalization_check"],
      misconception: "Passing is treated as the end rather than a chance to consolidate the reasoning.",
      expectedStudentMove: "State the key fix, the complexity, and one boundary case that now works.",
    };
  }
  if (phase === "REGRESSION") {
    return {
      headline: "Public attempt chain shows the latest edit regressed the verdict.",
      studentHint: "Compare only the latest diff first and identify which edit changed the previously better behavior.",
      mustMention: ["diff", "regression"],
      fineTags: ["PARTIAL_FIX_REGRESSION"],
      requiredEvidenceRefs: ["history:verdict_regression"],
      acceptableTeachingActions: ["TRACE_VARIABLES", "ASK_MIN_CASE"],
      knowledgePoints: ["debugging sequence"],
      algorithmStrategies: ["diff review"],
      commonMistakes: ["partial_fix_regression"],
      boundaryTypes: ["verdict_transition"],
      misconception: "The student may keep adding fixes without noticing that a recent edit made the state worse.",
      expectedStudentMove: "Compare the two latest submissions on one input and isolate the changed assumption.",
    };
  }
  throw new Error(`Unsupported expected phase: ${phase} (${previousVerdict} -> ${currentVerdict})`);
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
  if (verdict === "Accepted") return null;
  return verdict;
}

function groupSubmissions(submissions) {
  const groups = new Map();
  for (const submission of submissions) {
    const key = groupKey(submission);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(submission);
  }
  for (const group of groups.values()) {
    group.sort((left, right) => Number(left.attempt_number) - Number(right.attempt_number));
  }
  groupSubmissions.cached = groups;
  return groups;
}

function previousSubmission(group, current) {
  const index = group.findIndex((submission) => submission.submission_id === current.submission_id);
  return index > 0 ? group[index - 1] : null;
}

function sameVerdictRunLength(current, group) {
  if (!group) return 1;
  const currentIndex = group.findIndex((submission) => submission.submission_id === current.submission_id);
  if (currentIndex < 0) return 1;
  const currentVerdict = normalizeVerdict(current.final_verdict);
  let count = 0;
  for (let index = currentIndex; index >= 0; index -= 1) {
    if (normalizeVerdict(group[index].final_verdict) !== currentVerdict) break;
    count += 1;
  }
  return count;
}

function repeatedTagFor(verdict) {
  if (verdict === "COMPILATION_ERROR") return "SYNTAX_ERROR";
  if (verdict === "RUNTIME_ERROR") return "RUNTIME_STABILITY";
  if (verdict === "TIME_LIMIT_EXCEEDED") return "TIME_COMPLEXITY";
  if (verdict === "MEMORY_LIMIT_EXCEEDED") return "SPACE_COMPLEXITY";
  if (verdict === "WRONG_ANSWER") return "BOUNDARY_CONDITION";
  return null;
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
  const normalized = verdictMap[verdict] ?? verdict;
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

function groupKey(submission) {
  return `${submission.user_id}|${submission.problem_id}`;
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
  const phaseCounts = new Map();
  if (items.length !== selectedTransitions.length) {
    errors.push(`expected ${selectedTransitions.length} cases, got ${items.length}`);
  }
  for (const item of items) {
    if (names.has(item.name)) errors.push(`duplicate name ${item.name}`);
    names.add(item.name);
    if (item.source !== "codestream-mendeley-cc-by-4.0-v1") errors.push(`bad source ${item.name}`);
    if (!item.problem.description || item.problem.description.length < 80) errors.push(`short problem ${item.name}`);
    if (!item.submission.sourceCode || item.submission.sourceCode.length < 80) errors.push(`short code ${item.name}`);
    if (!item.expected.issueTags.length) errors.push(`missing issue tags ${item.name}`);
    if (!item.expected.trajectoryPhase) errors.push(`missing trajectory phase ${item.name}`);
    if (!item.expected.requiredEvidenceRefs.length) errors.push(`missing required evidence ${item.name}`);
    if (!item.history || !item.history.previousVerdict) errors.push(`missing history ${item.name}`);
    if (/user_\d+/.test(JSON.stringify(item))) errors.push(`raw user id leaked ${item.name}`);
    phaseCounts.set(item.expected.trajectoryPhase, (phaseCounts.get(item.expected.trajectoryPhase) ?? 0) + 1);
  }
  for (const phase of [
    "FIXED_COMPILATION",
    "REPEATED_STUCK",
    "RUNTIME_FIXED_CORRECTNESS_REMAINS",
    "ACCEPTED_AFTER_FIX",
    "REGRESSION",
  ]) {
    if (!phaseCounts.has(phase)) errors.push(`missing trajectory phase ${phase}`);
  }
  if (errors.length > 0) {
    throw new Error(`Public attempt-chain fixture generation failed:\n${errors.join("\n")}`);
  }
}
