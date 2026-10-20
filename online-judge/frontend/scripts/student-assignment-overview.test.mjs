import assert from "node:assert/strict";
import test from "node:test";
import { chromium } from "playwright";

const baseUrl = process.env.STUDENT_ASSIGNMENT_BASE_URL || "http://127.0.0.1:8081";
const student = { id: 41, displayName: "Ge Kevin", classGroupId: 3, className: "温中信息技术试点班" };
const assignment = {
  id: 7,
  title: "try",
  description: "这段说明应从精简页面移除。",
  classGroupId: 3,
  className: "温中信息技术试点班",
  hintPolicy: "L2",
  status: "ACTIVE",
  endsAt: null,
  tasks: [
    { problemId: 101, title: "两数求和", difficulty: "EASY", orderIndex: 1, required: true },
    { problemId: 102, title: "回文判断", difficulty: "EASY", orderIndex: 2, required: true },
    { problemId: 103, title: "FizzBuzz", difficulty: "EASY", orderIndex: 3, required: true },
    { problemId: 104, title: "阶乘计算", difficulty: "MEDIUM", orderIndex: 4, required: true }
  ]
};
const trajectory = {
  completedTasks: 1,
  totalTasks: 4,
  totalAttempts: 4,
  tasks: [
    { problemId: 101, title: "两数求和", difficulty: "EASY", attemptCount: 2, passed: true, submissions: [] },
    { problemId: 102, title: "回文判断", difficulty: "EASY", attemptCount: 2, passed: false, submissions: [] },
    { problemId: 103, title: "FizzBuzz", difficulty: "EASY", attemptCount: 0, passed: false, submissions: [] },
    { problemId: 104, title: "阶乘计算", difficulty: "MEDIUM", attemptCount: 0, passed: false, submissions: [] }
  ]
};
const problem = {
  id: 102,
  title: "回文判断",
  description: "判断给定字符串是否为回文。",
  difficulty: "EASY",
  timeLimit: 1000,
  memoryLimit: 131072,
  starterCode: "",
  sampleTestCases: [{ input: "level", expectedOutput: "true" }]
};

test("assignment overview follows the left-rail workspace concept", async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: {
      width: Number(process.env.STUDENT_ASSIGNMENT_VIEWPORT_WIDTH || 1600),
      height: Number(process.env.STUDENT_ASSIGNMENT_VIEWPORT_HEIGHT || 1000)
    }
  });
  await context.addInitScript(value => {
    window.sessionStorage.setItem("wzai:student", JSON.stringify(value));
    window.sessionStorage.setItem("wzai:student:7", JSON.stringify(value));
  }, student);
  await context.route("**/code/api/**", async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/code/, "");
    const body = path === "/api/student/profile/41/assignments"
      ? [assignment]
      : path === "/api/student/assignments/7/profile/41/trajectory"
        ? trajectory
        : path === "/api/problems/102"
          ? problem
          : path === "/api/submissions/problem/102/history-summary"
            ? []
            : {};
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });

  try {
    const page = await context.newPage();
    const browserErrors = [];
    page.on("console", message => {
      if (message.type() === "error") browserErrors.push(message.text());
    });
    page.on("pageerror", error => browserErrors.push(error.message));
    await page.goto(`${baseUrl}/code/student/assignments/7`, { waitUntil: "domcontentloaded" });
    await page.locator(".student-assignment-progress-row").first().waitFor({ state: "visible" });

    assert.equal(await page.locator(".student-assignment-insights-tabs").count(), 0);
    assert.equal(await page.locator(".student-assignment-side-nav a").count(), 4);
    assert.equal((await page.locator(".student-assignment-side-nav a.is-active").textContent())?.trim(), "概览");
    assert.equal(await page.locator(".student-assignment-insights-title > span").count(), 0);
    assert.equal(await page.locator(".student-assignment-insights-meta").count(), 2);
    assert.equal((await page.locator(".student-assignment-insights-meta").first().textContent())?.trim(), "温中信息技术试点班");
    assert.equal((await page.locator("body").textContent()).includes("未设置截止时间"), true);
    assert.equal(await page.locator(".student-assignment-summary-band").count(), 0);
    assert.equal(await page.locator(".student-assignment-overview-layout").count(), 1);
    assert.equal(await page.getByRole("heading", { name: "作业概览" }).count(), 1);
    assert.equal((await page.locator(".student-assignment-overview-summary").textContent()).includes("总提交 4"), true);
    assert.equal(await page.locator(".student-assignment-activity").count(), 1);
    assert.equal(await page.locator(".student-assignment-activity-item").count(), 3);
    assert.equal(await page.locator(".student-assignment-note-band").count(), 0);
    assert.equal(await page.getByRole("heading", { name: "题目进度" }).count(), 0);
    assert.equal(await page.locator(".student-assignment-progress-header > span").count(), 5);
    assert.equal(await page.locator(".student-assignment-progress-row").first().evaluate(element => element.tagName), "A");
    assert.equal(await page.locator(".student-assignment-latest-verdict").count(), 0);
    assert.equal((await page.locator(".student-assignment-attempts").first().textContent())?.trim(), "2");
    assert.deepEqual(browserErrors, []);
    if (process.env.STUDENT_ASSIGNMENT_OVERVIEW_SCREENSHOT) {
      await page.screenshot({ path: process.env.STUDENT_ASSIGNMENT_OVERVIEW_SCREENSHOT, fullPage: true });
    }
    await page.getByRole("link", { name: "题目", exact: true }).click();
    await page.waitForURL(url => url.pathname === "/code/student/assignments/7/problems/102", { timeout: 5000 });
    await page.locator(".panel--statement").waitFor({ state: "visible" });
    assert.equal(await page.locator("#code-workbench").count(), 1);
    assert.equal(await page.getByRole("button", { name: "提交代码" }).count(), 1);
    if (process.env.STUDENT_ASSIGNMENT_SCREENSHOT) {
      await page.screenshot({ path: process.env.STUDENT_ASSIGNMENT_SCREENSHOT, fullPage: true });
    }
  } finally {
    await context.close();
    await browser.close();
  }
});
