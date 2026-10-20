import { mkdir } from "node:fs/promises";
import { chromium } from "playwright";

const baseUrl = process.env.TEACHER_GROWTH_BASE_URL || "http://127.0.0.1:5176/code";
const screenshotDir = process.env.TEACHER_GROWTH_SCREENSHOT_DIR || "/tmp/teacher-growth-browser";

const assignment = {
  id: 7,
  title: "循环边界练习",
  classGroupId: 1,
  className: "高一 1 班",
  hintPolicy: "L2",
  status: "ACTIVE",
  tasks: [{ problemId: 21, title: "数组边界", difficulty: "MEDIUM", orderIndex: 0, required: true }]
};

const growth = (submissionId, state, comparisonSubmissionId, persisted, added, recovered) => ({
  submissionId,
  growthState: state,
  ruleVersion: "single-problem-growth-v1",
  effectiveAttempt: true,
  comparable: true,
  comparisonSubmissionId,
  passedTestCases: submissionId === 302 ? 7 : 3,
  totalTestCases: 8,
  previousPassedTestCases: comparisonSubmissionId ? 3 : null,
  previousTotalTestCases: comparisonSubmissionId ? 8 : null,
  passedTestCaseDelta: comparisonSubmissionId ? 4 : null,
  persistedCount: persisted,
  newCount: added,
  recurringCount: 0,
  notObservedCount: recovered,
  recoveredCount: 0,
  uncomparableCount: 0,
  improvementCount: 0,
  unresolvedCount: persisted + added,
  priorityIssueTitle: added ? "新出现的输入问题" : "循环边界",
  priorityIssueStatus: added ? "NEW" : "PERSISTED",
  dataCompletenessStatus: "COMPLETE",
  issueSignals: [
    ...(persisted ? [{ normalizedPointKey: "boundary", title: "循环边界", displayCategory: "REPAIR", changeStatus: "PERSISTED", knowledgePath: ["基础", "循环", "边界"] }] : []),
    ...(added ? [{ normalizedPointKey: "input", title: "新出现的输入问题", displayCategory: "REPAIR", changeStatus: "NEW", knowledgePath: ["基础", "输入输出"] }] : []),
    ...(recovered ? [{ normalizedPointKey: "index", title: "数组下标", displayCategory: "REPAIR", changeStatus: "NOT_OBSERVED", knowledgePath: ["基础", "数组"] }] : [])
  ]
});

const recentHistory = [
  {
    id: 302,
    problemId: 21,
    assignmentId: 7,
    studentProfileId: 11,
    problemTitle: "数组边界",
    verdict: "WRONG_ANSWER",
    submittedAt: "2026-08-11T10:20:00",
    passedTestCases: 7,
    totalTestCases: 8,
    feedbackStatus: "READY",
    growthSummary: growth(302, "MIXED_PROGRESS", 301, 1, 1, 1)
  },
  {
    id: 301,
    problemId: 21,
    assignmentId: 7,
    studentProfileId: 11,
    problemTitle: "数组边界",
    verdict: "WRONG_ANSWER",
    submittedAt: "2026-08-11T10:00:00",
    passedTestCases: 3,
    totalTestCases: 8,
    feedbackStatus: "READY",
    growthSummary: growth(301, "FIRST_RECORD", null, 0, 1, 0)
  }
];

const history = [
  ...recentHistory,
  ...Array.from({ length: 12 }, (_, index) => ({
    ...recentHistory[1],
    id: 300 - index,
    submittedAt: `2026-08-${String(10 - Math.floor(index / 4)).padStart(2, "0")}T${String(9 - (index % 4)).padStart(2, "0")}:00:00`,
    growthSummary: growth(300 - index, index % 3 === 0 ? "STAGNANT" : "IMPROVED", 299 - index, index % 3 === 0 ? 1 : 0, 0, index % 3 === 0 ? 0 : 1)
  }))
];

const overview = {
  assignment,
  rosterStudentCount: 30,
  participantCount: 18,
  submittedStudentCount: 18,
  unsubmittedStudentCount: 12,
  completedRequiredStudentCount: 9,
  requiredProblemCount: 1,
  attemptCount: 42,
  passedAttemptCount: 9,
  strugglingStudentCount: 1,
  knowledgePathStats: [],
  topIssues: [],
  students: [],
  problemSummaries: [{
    problemId: 21,
    title: "数组边界",
    difficulty: "MEDIUM",
    orderIndex: 0,
    required: true,
    classStudentCount: 30,
    submittedStudentCount: 18,
    submissionCount: 42,
    effectiveAttemptCount: 27,
    passedStudentCount: 9,
    firstPassStudentCount: 4,
    passedAttemptCount: 9,
    medianEffectiveAttempts: 2,
    attentionStudentCount: 1,
    knowledgePathStats: [{
      id: "mistakePoint:FORMAL:基础/循环/边界",
      label: "循环边界",
      granularity: "mistakePoint",
      normalizedIssueId: "boundary",
      path: [{ label: "基础", kind: "chapter" }, { label: "循环", kind: "knowledgePoint" }, { label: "循环边界", kind: "mistakePoint" }],
      pathStatus: "FORMAL",
      libraryFit: "HIT",
      errorOccurrenceCount: 6,
      rawOccurrenceCount: 7,
      effectiveWeightedOccurrenceCount: 6,
      affectedStudentCount: 3,
      repeatedStudentCount: 1,
      recoveredStudentCount: 1,
      affectedProblemCount: 1,
      affectedStudentIds: [11, 12, 13],
      repeatedStudentIds: [11],
      resolvedStudentIds: [11],
      affectedProblemIds: [21],
      evidenceSubmissionIds: [301, 302]
    }],
    topIssues: [{ label: "循环边界", count: 6, affectedStudentCount: 3 }],
    students: [{
      studentProfileId: 11,
      displayName: "林同学",
      studentNo: "S011",
      attemptCount: 5,
      effectiveAttemptCount: 3,
      passedCount: 0,
      latestSubmissionId: 302,
      latestVerdict: "WRONG_ANSWER",
      latestSubmittedAt: "2026-08-11T10:20:00",
      latestIssueTag: "BOUNDARY",
      latestFineGrainedIssue: "LOOP_BOUNDARY",
      latestGrowthSummary: history[0].growthSummary,
      needsAttention: true
    }]
  }]
};

const evidence = {
  submission: {
    id: 302,
    problemId: 21,
    assignmentId: 7,
    studentProfileId: 11,
    problemTitle: "数组边界",
    languageId: 54,
    languageName: "C++17",
    sourceCode: "int main() {\n  int n; cin >> n;\n  for (int i = 0; i <= n; ++i) cout << i;\n}",
    verdict: "WRONG_ANSWER",
    submittedAt: "2026-08-11T10:20:00",
    growthSummary: history[0].growthSummary,
    testCaseResults: [
      { testCaseNumber: 1, passed: true, hidden: false },
      { testCaseNumber: 2, passed: false, hidden: true }
    ]
  },
  analysisVersions: [
    {
      id: 502,
      versionNumber: 2,
      status: "READY",
      source: "MODEL",
      officialVersion: true,
      model: "Qwen",
      promptVersion: "teacher-analysis-v2",
      generatedAt: "2026-08-11T10:21:00",
      analysis: { submissionId: 302, headline: "循环结束条件多执行了一次", summary: "i <= n 会访问到边界外位置。" },
      feedback: { status: "READY", source: "MODEL", repairItems: [{ title: "检查循环结束条件", body: "把循环允许访问的最后一个下标写清楚。" }], improvementItems: [] }
    },
    {
      id: 501,
      versionNumber: 1,
      status: "READY",
      source: "MODEL",
      officialVersion: false,
      generatedAt: "2026-08-11T10:20:30",
      feedback: { status: "READY", source: "MODEL", repairItems: [{ title: "旧版反馈", body: "先检查数组边界。" }], improvementItems: [] }
    }
  ],
  corrections: [{
    id: 601,
    assignmentId: 7,
    submissionId: 302,
    feedbackRevisionId: 502,
    correctedIssueTag: "BOUNDARY",
    correctedFineGrainedTag: "LOOP_BOUNDARY",
    teacherNote: "应强调循环上界。",
    evalCandidate: true,
    correctedBy: "teacher",
    correctedAt: "2026-08-11T10:25:00"
  }]
};

function responseFor(pathname, method) {
  if (pathname === "/api/teacher/auth/session") return { authenticated: true };
  if (pathname === "/api/teacher/classes") return [{ id: 1, name: "高一 1 班", grade: "高一", teacherName: "王老师" }];
  if (pathname === "/api/teacher/assignments") return [assignment];
  if (pathname === "/api/teacher/assignments/7/overview") return overview;
  if (pathname === "/api/teacher/diagnosis-tags") return [
    { id: "BOUNDARY", label: "边界问题", fineGrained: false },
    { id: "LOOP_BOUNDARY", label: "循环上界", fineGrained: true, parentTag: "BOUNDARY" }
  ];
  if (pathname === "/api/teacher/assignments/7/problems/21/students/11/growth") return history;
  if (pathname === "/api/teacher/assignments/7/submissions/302/evidence") return evidence;
  if (pathname === "/api/teacher/assignments/7/submissions/301/evidence") return { ...evidence, submission: { ...evidence.submission, id: 301 }, analysisVersions: [evidence.analysisVersions[1]], corrections: [] };
  if (pathname.endsWith("/analysis/regenerate") && method === "POST") return null;
  if (pathname === "/api/teacher/assignments/7/diagnosis-corrections" && method === "POST") return evidence.corrections[0];
  return undefined;
}

async function installApiMock(page) {
  await page.route("**/*", async route => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/code/api/") && !url.pathname.startsWith("/api/")) return route.continue();
    const pathname = url.pathname.replace(/^\/code/, "");
    const payload = responseFor(pathname, route.request().method());
    if (payload === undefined) {
      return route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ message: `No mock for ${pathname}` }) });
    }
    return route.fulfill({ status: payload === null ? 204 : 200, contentType: "application/json", body: payload === null ? "" : JSON.stringify(payload) });
  });
}

await mkdir(screenshotDir, { recursive: true });
const browser = await chromium.launch({ headless: true });
try {
  const desktop = await browser.newContext({ viewport: { width: 1440, height: 1100 }, colorScheme: "light" });
  const page = await desktop.newPage();
  page.on("pageerror", error => console.error("browser page error:", error));
  page.on("console", message => {
    if (message.type() === "error") console.error("browser console error:", message.text());
  });
  await installApiMock(page);
  await page.goto(`${baseUrl}/teacher/classes/1/assignments/7/problems/21`, { waitUntil: "networkidle" });
  try {
    await page.getByRole("heading", { name: "全班问题" }).waitFor();
  } catch (error) {
    await page.screenshot({ path: `${screenshotDir}/desktop-failure.png`, fullPage: true });
    console.error("desktop body:", (await page.locator("body").innerText()).slice(0, 4000));
    throw error;
  }
  await page.getByRole("button", { name: /遇到过/ }).first().waitFor();
  await page.getByRole("button", { name: /反复出现/ }).first().click();
  await page.screenshot({ path: `${screenshotDir}/problem-workspace.png`, fullPage: true });
  await page.getByText("林同学", { exact: true }).click();
  await page.getByRole("heading", { name: "全部提交时间线" }).waitFor();
  await page.getByRole("button", { name: "继续显示更早提交" }).click();
  const firstTimelineNode = page.locator(".growth-timeline__node").first();
  await firstTimelineNode.focus();
  if (!(await firstTimelineNode.evaluate(element => element === document.activeElement))) {
    throw new Error("timeline node is not keyboard focusable");
  }
  await page.getByRole("tab", { name: "提交证据" }).click();
  await page.getByRole("heading", { name: "提交证据" }).waitFor();
  await page.getByText("原始代码", { exact: true }).waitFor();
  await page.getByRole("tab", { name: "AI 分析" }).click();
  await page.getByText("当前正式版本", { exact: true }).waitFor();
  await page.getByText("循环结束条件多执行了一次", { exact: true }).waitFor();
  await page.screenshot({ path: `${screenshotDir}/desktop.png`, fullPage: true });
  await desktop.close();

  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: "dark" });
  await mobile.addInitScript(() => {
    localStorage.setItem("wzai:theme", "dark");
    localStorage.setItem("wzai:locale", "en");
  });
  const mobilePage = await mobile.newPage();
  await installApiMock(mobilePage);
  await mobilePage.goto(`${baseUrl}/teacher/classes/1/assignments/7/problems/21/students/11`, { waitUntil: "networkidle" });
  await mobilePage.getByRole("tab", { name: "Submission evidence" }).click();
  await mobilePage.getByRole("heading", { name: "Submission evidence" }).waitFor();
  await mobilePage.getByRole("tab", { name: "AI analysis" }).click();
  await mobilePage.getByText("Current official version", { exact: true }).waitFor();
  const mobileText = await mobilePage.locator("body").innerText();
  if (mobileText.includes("答案需修正")) throw new Error("English teacher UI still contains the Chinese verdict label");
  const overflow = await mobilePage.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
  if (overflow) throw new Error("mobile page has horizontal overflow");
  await mobilePage.screenshot({ path: `${screenshotDir}/mobile-dark-en.png`, fullPage: true });
  await mobile.close();
} finally {
  await browser.close();
}

console.log(`teacher growth browser acceptance passed; screenshots: ${screenshotDir}`);
