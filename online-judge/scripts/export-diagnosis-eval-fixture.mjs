import { readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const defaultMustNotMention = ["完整代码", "参考答案", "隐藏测试点"];
const __dirname = dirname(fileURLToPath(import.meta.url));
const defaultOutput = resolve(__dirname, "../src/test/resources/diagnosis-eval-fixtures/teacher-corrections.generated.json");

function usage() {
  return [
    "Usage:",
    "  node scripts/export-diagnosis-eval-fixture.mjs --input candidates.json [--output teacher-corrections.generated.json]",
    "",
    "Input should be the JSON response from:",
    "  GET /api/teacher/assignments/{assignmentId}/diagnosis-eval-candidates"
  ].join("\n");
}

function parseArgs(argv) {
  const args = { output: defaultOutput };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--input") {
      args.input = argv[++index];
    } else if (arg === "--output") {
      args.output = argv[++index];
    } else if (arg === "--help" || arg === "-h") {
      args.help = true;
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return args;
}

function asList(...values) {
  return values
    .flatMap(value => Array.isArray(value) ? value : [value])
    .filter(value => typeof value === "string" && value.trim())
    .map(value => value.trim());
}

function fixtureName(candidate) {
  const correctionId = candidate.correctionId ?? "unknown";
  const fineTag = candidate.correctedFineGrainedTag || candidate.correctedIssueTag || "teacher-correction";
  return `teacher-correction-${correctionId}-${fineTag.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`;
}

function toFixture(candidate) {
  const correctedIssue = candidate.correctedIssueTag || candidate.originalIssueTag || "UNKNOWN";
  const correctedFine = candidate.correctedFineGrainedTag || candidate.originalFineGrainedTag || correctedIssue;
  const originalIssueTags = asList(candidate.originalIssueTag);
  const originalFineTags = asList(candidate.originalFineGrainedTag);
  return {
    name: fixtureName(candidate),
    source: "teacher-correction-export",
    correctionId: candidate.correctionId ?? null,
    submissionId: candidate.submissionId ?? null,
    problem: {
      id: candidate.problemId ?? null,
      title: candidate.problemTitle || `Problem ${candidate.problemId ?? ""}`.trim(),
      description: candidate.problemDescription || "",
      difficulty: candidate.problemDifficulty || "EASY",
      timeLimit: candidate.problemTimeLimit ?? 1000,
      memoryLimit: candidate.problemMemoryLimit ?? 65536
    },
    submission: {
      languageName: candidate.languageName || "Unknown",
      verdict: candidate.verdict || "WRONG_ANSWER",
      sourceCode: candidate.sourceCode || candidate.sourceCodePreview || ""
    },
    analysis: {
      scenario: candidate.scenario || candidate.verdict || "UNKNOWN",
      originalIssueTags,
      originalFineGrainedTags: originalFineTags,
      analysisHeadline: candidate.analysisHeadline || ""
    },
    teacherCorrection: {
      correctedIssueTag: correctedIssue,
      correctedFineGrainedTag: correctedFine,
      teacherNote: candidate.teacherNote || ""
    },
    expectedIssueTags: [correctedIssue],
    expectedFineTags: [correctedFine],
    mustMention: asList(candidate.teacherNote).slice(0, 2),
    mustNotMention: defaultMustNotMention
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log(usage());
    return;
  }
  if (!args.input) {
    throw new Error(`Missing --input.\n${usage()}`);
  }
  const inputPath = resolve(args.input);
  const outputPath = resolve(args.output);
  const payload = JSON.parse((await readFile(inputPath, "utf8")).replace(/^\uFEFF/, ""));
  const candidates = Array.isArray(payload) ? payload : payload.candidates;
  if (!Array.isArray(candidates)) {
    throw new Error("Input JSON must be an array or an object with a candidates array.");
  }
  const fixtures = candidates
    .filter(candidate => candidate && candidate.evalCandidate !== false)
    .map(toFixture);
  await writeFile(outputPath, `${JSON.stringify(fixtures, null, 2)}\n`, "utf8");
  console.log(`[diagnosis-fixture-export] wrote ${fixtures.length} fixtures to ${outputPath}`);
}

main().catch(error => {
  console.error(`[diagnosis-fixture-export] ${error.message}`);
  process.exit(1);
});
