import assert from "node:assert/strict";
import test from "node:test";
import { chromium } from "playwright";

const baseUrl = process.env.STUDENT_PROBLEM_BASE_URL || "http://127.0.0.1:8081";
const viewportWidth = Number(process.env.STUDENT_PROBLEM_VIEWPORT_WIDTH || 1600);
const viewportHeight = Number(process.env.STUDENT_PROBLEM_VIEWPORT_HEIGHT || 1000);
const student = { id: 41, displayName: "NothingK", classGroupId: 3, className: "温中信息技术试点班" };
const assignment = {
  id: 7,
  title: "try",
  classGroupId: 3,
  className: "温中信息技术试点班",
  hintPolicy: "L2",
  status: "ACTIVE",
  tasks: [
    { problemId: 101, title: "两数求和", difficulty: "EASY", orderIndex: 1, required: true },
    { problemId: 102, title: "回文判断", difficulty: "EASY", orderIndex: 2, required: true },
    { problemId: 103, title: "FizzBuzz", difficulty: "EASY", orderIndex: 3, required: true },
    { problemId: 104, title: "阶乘计算", difficulty: "MEDIUM", orderIndex: 4, required: true }
  ]
};
const problem = {
  id: 101,
  title: "两数求和",
  description: "## 题目描述\n给定两个整数 `a` 和 `b`，输出它们的和。\n\n## 输入格式\n一行两个整数。\n\n## 输出格式\n输出一个整数。",
  difficulty: "EASY",
  timeLimit: 1000,
  memoryLimit: 131072,
  starterCode: "a, b = map(int, input().split())\nprint(a + b)",
  sampleTestCases: [{ input: "3 5", expectedOutput: "8" }]
};
const trajectory = {
  assignment,
  student,
  completedTasks: 0,
  totalTasks: 4,
  totalAttempts: 0,
  repeatedIssueCount: 0,
  recentIssueDistribution: [],
  abilitySummary: [],
  tasks: assignment.tasks.map(task => ({ ...task, attemptCount: 0, passed: false, submissions: [] }))
};

async function readWorkspaceChrome(page, headerSelector, railSelector) {
  return page.evaluate(({ headerSelector, railSelector }) => {
    const header = document.querySelector(headerSelector);
    const rail = document.querySelector(railSelector);
    const railItem = rail?.querySelector("a");
    if (!(header instanceof HTMLElement) || !(rail instanceof HTMLElement) || !(railItem instanceof HTMLElement)) {
      throw new Error(`Missing workspace chrome: ${headerSelector}, ${railSelector}`);
    }
    const headerRect = header.getBoundingClientRect();
    const railRect = rail.getBoundingClientRect();
    const railItemRect = railItem.getBoundingClientRect();
    const headerStyle = getComputedStyle(header);
    const railStyle = getComputedStyle(rail);
    return {
      header: {
        height: headerRect.height,
        paddingLeft: headerStyle.paddingLeft,
        paddingRight: headerStyle.paddingRight
      },
      rail: {
        width: railRect.width,
        paddingTop: railStyle.paddingTop,
        paddingRight: railStyle.paddingRight,
        paddingBottom: railStyle.paddingBottom,
        paddingLeft: railStyle.paddingLeft,
        gap: railStyle.gap
      },
      railItem: {
        width: railItemRect.width,
        height: railItemRect.height
      }
    };
  }, { headerSelector, railSelector });
}

test("problem workbench has persistent navigation, resizable split panels, and collapsible code", async () => {
  const codeRunRequests = [];
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: {
      width: viewportWidth,
      height: viewportHeight
    }
  });
  await context.addInitScript(value => {
    window.localStorage.setItem("wzai:theme", "light");
    window.sessionStorage.setItem("wzai:student", JSON.stringify(value));
    window.sessionStorage.setItem("wzai:student:7", JSON.stringify(value));
  }, student);
  await context.route("**/code/api/**", async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/code/, "");
    if (path === "/api/code-runs" && route.request().method() === "POST") {
      codeRunRequests.push(route.request().postDataJSON());
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          status: "SUCCESS",
          stdout: "8\n",
          stderr: "",
          exitCode: 0,
          executionTimeMs: 12,
          stdoutTruncated: false,
          stderrTruncated: false
        })
      });
      return;
    }
    const body = path === "/api/problems/101"
      ? problem
      : path === "/api/student/profile/41/assignments"
        ? [assignment]
        : path === "/api/student/assignments/7/profile/41/trajectory"
          ? trajectory
          : path === "/api/submissions/problem/101/history-summary"
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
    await page.goto(`${baseUrl}/code/student/assignments/7/problems/101?studentProfileId=41`, { waitUntil: "domcontentloaded" });
    await page.locator(".problem-main-split").waitFor({ state: "visible", timeout: 10000 });
    await page.locator(".problem-page > .student-assignment-insights-header").waitFor({ state: "visible", timeout: 10000 });

    assert.equal(await page.locator(".problem-page > .student-assignment-insights-header").count(), 1);
    assert.equal(await page.locator(".problem-assignment-header").count(), 0);
    assert.equal((await page.locator(".student-assignment-insights-header h1").textContent())?.trim(), "try");
    assert.equal(await page.locator(".student-assignment-insights-header .student-assignment-insights-meta").count(), 2);
    assert.equal((await page.locator(".student-assignment-insights-header .student-assignment-profile strong").textContent())?.trim(), "NothingK");
    assert.equal(await page.locator(".problem-back-link").count(), 0);
    const pageFrame = await page.locator(".problem-page").boundingBox();
    const appHeaderFrame = await page.locator(".app-header").boundingBox();
    const mainShellFrame = await page.locator(".main-shell").boundingBox();
    assert.ok(pageFrame);
    assert.ok(appHeaderFrame);
    assert.ok(pageFrame.x <= 1, JSON.stringify(pageFrame));
    assert.ok(pageFrame.width >= viewportWidth - 2, JSON.stringify(pageFrame));
    assert.ok(Math.abs(pageFrame.y - (appHeaderFrame.y + appHeaderFrame.height)) <= 1, JSON.stringify({ pageFrame, appHeaderFrame, mainShellFrame }));
    assert.equal(await page.locator(".student-assignment-side-nav[data-student-assignment-navigation] a").count(), 4);
    assert.equal(await page.locator(".problem-workbench-rail").count(), 0);
    assert.equal(await page.locator(".problem-workbench-shell.student-assignment-workspace").count(), 1);
    assert.equal(await page.locator(".problem-workbench-shell > .student-assignment-side-nav").count(), 1);
    const problemChrome = await readWorkspaceChrome(page, ".student-assignment-insights-header", ".student-assignment-side-nav");
    const overviewPage = await context.newPage();
    await overviewPage.goto(`${baseUrl}/code/student/assignments/7?studentProfileId=41`, { waitUntil: "domcontentloaded" });
    await overviewPage.locator(".student-assignment-overview-layout").waitFor({ state: "visible", timeout: 10000 });
    const overviewChrome = await readWorkspaceChrome(
      overviewPage,
      ".student-assignment-insights-header",
      ".student-assignment-side-nav"
    );
    assert.deepEqual(problemChrome, overviewChrome);
    await overviewPage.close();
    assert.equal((await page.locator(".student-assignment-side-nav a.is-active").textContent())?.trim(), "题目");
    assert.equal(await page.locator(".problem-main-split > .panel--statement").count(), 1);
    assert.equal(await page.locator(".problem-main-split > .panel--editor").count(), 1);
    if (process.env.STUDENT_PROBLEM_SCREENSHOT) {
      await page.screenshot({ path: process.env.STUDENT_PROBLEM_SCREENSHOT, fullPage: true });
    }

    const separator = page.getByRole("separator", { name: "调整题目和代码宽度" });
    if (viewportWidth > 980) {
      const statementBefore = await page.locator(".panel--statement").boundingBox();
      const editorBefore = await page.locator(".panel--editor").boundingBox();
      const splitBefore = await page.locator(".problem-main-split").evaluate(element => ({
        style: element.getAttribute("style"),
        columns: getComputedStyle(element).gridTemplateColumns,
        width: element.getBoundingClientRect().width
      }));
      const separatorBox = await separator.boundingBox();
      assert.ok(statementBefore && editorBefore && separatorBox);
      await page.mouse.move(separatorBox.x + separatorBox.width / 2, separatorBox.y + 100);
      await page.mouse.down();
      await page.mouse.move(separatorBox.x + 140, separatorBox.y + 100, { steps: 6 });
      await page.mouse.up();
      const statementAfter = await page.locator(".panel--statement").boundingBox();
      const editorAfter = await page.locator(".panel--editor").boundingBox();
      assert.ok(statementAfter && editorAfter);
      assert.ok(statementAfter.width > statementBefore.width + 60, JSON.stringify({ splitBefore, statementBefore, statementAfter }));
      assert.ok(editorAfter.width < editorBefore.width - 60, JSON.stringify({ editorBefore, editorAfter }));
    } else {
      assert.equal(await separator.isVisible(), false);
    }

    await page.getByRole("button", { name: "收起代码" }).click();
    assert.equal(await page.locator(".problem-main-split > .panel--editor").count(), 0);
    if (process.env.STUDENT_PROBLEM_COLLAPSED_SCREENSHOT) {
      await page.screenshot({ path: process.env.STUDENT_PROBLEM_COLLAPSED_SCREENSHOT, fullPage: true });
    }
    await page.getByRole("button", { name: "展开代码" }).click();
    await page.locator(".problem-main-split > .panel--editor").waitFor({ state: "visible" });
    assert.equal(await page.getByRole("button", { name: "提交代码" }).count(), 1);
    const codeRunToggle = page.getByRole("button", { name: "展开测试运行" });
    assert.equal(await codeRunToggle.getAttribute("aria-expanded", { timeout: 5000 }), "false");
    assert.equal(await page.getByText("快速验证", { exact: true }).count(), 0);
    assert.equal(await page.getByText("自定义输入运行", { exact: true }).count(), 0);
    assert.equal(await page.getByText("用一组输入运行当前代码，不计入正式提交和学习记录。", { exact: true }).count(), 0);
    assert.equal(await page.getByText("临时运行", { exact: true }).count(), 0);
    assert.equal(await page.getByText("内容会作为程序的标准输入 stdin。", { exact: true }).count(), 0);
    if (process.env.STUDENT_PROBLEM_CODE_RUN_COLLAPSED_SCREENSHOT) {
      await page.locator(".code-run-panel").screenshot({ path: process.env.STUDENT_PROBLEM_CODE_RUN_COLLAPSED_SCREENSHOT });
    }
    await codeRunToggle.click();
    assert.equal(
      await page.getByRole("button", { name: "收起测试运行" }).getAttribute("aria-expanded"),
      "true"
    );
    const customInput = page.getByRole("textbox", { name: /自定义输入/ });
    await customInput.waitFor({ state: "visible" });
    assert.equal(await customInput.inputValue(), "");
    await page.getByRole("button", { name: "载入首个样例" }).click();
    assert.equal(await customInput.inputValue(), "3 5");
    if (process.env.STUDENT_PROBLEM_CODE_RUN_EXPANDED_SCREENSHOT) {
      await page.locator(".code-run-panel").screenshot({ path: process.env.STUDENT_PROBLEM_CODE_RUN_EXPANDED_SCREENSHOT });
    }
    await page.getByRole("button", { name: "运行代码" }).click();
    await page.locator(".code-run-result").waitFor({ state: "visible", timeout: 5000 });
    assert.equal((await page.locator(".code-run-stdout").textContent())?.trim(), "8");
    assert.equal(codeRunRequests.length, 1);
    assert.deepEqual(codeRunRequests[0], {
      problemId: 101,
      assignmentId: 7,
      languageId: 71,
      sourceCode: problem.starterCode,
      stdin: "3 5"
    });
    assert.equal(await page.locator(".problem-result-modal").count(), 0);
    await customInput.fill("2 4");
    assert.match((await page.locator(".code-run-result").textContent()) || "", /内容已变化/);
    assert.deepEqual(browserErrors, []);

  } finally {
    await context.close();
    await browser.close();
  }
});
