import assert from "node:assert/strict";
import test from "node:test";
import { chromium } from "playwright";

const baseUrl = process.env.STUDENT_HOME_BASE_URL || "http://127.0.0.1:8081";
const student = { id: 41, displayName: "Ge Kevin", classGroupId: 3, className: "温中信息技术试点班" };
const assignments = [
  {
    id: 7,
    title: "课堂编程作业",
    description: "这段作业说明不应显示在精简首页。",
    classGroupId: 3,
    className: "温中信息技术试点班",
    hintPolicy: "L2",
    status: "ACTIVE",
    endsAt: "2026-07-20T18:00:00",
    tasks: [
      { problemId: 101, title: "两数求和", difficulty: "EASY", orderIndex: 1, required: true },
      { problemId: 102, title: "回文判断", difficulty: "EASY", orderIndex: 2, required: true }
    ]
  },
  {
    id: 8,
    title: "循环练习",
    description: "另一段不应显示的作业说明。",
    classGroupId: 3,
    className: "温中信息技术试点班",
    hintPolicy: "L2",
    status: "ACTIVE",
    tasks: [{ problemId: 103, title: "阶乘计算", difficulty: "MEDIUM", orderIndex: 1, required: true }]
  }
];
const publicProblems = [
  { id: 101, title: "两数求和", difficulty: "EASY", timeLimit: 1000, memoryLimit: 65536 },
  { id: 102, title: "回文判断", difficulty: "EASY", timeLimit: 1000, memoryLimit: 65536 },
  { id: 103, title: "阶乘计算", difficulty: "MEDIUM", timeLimit: 1000, memoryLimit: 65536 }
];
const recommendations = {
  student,
  summary: "先解决边界判断，再做迁移练习。",
  recommendations: [
    {
      type: "REDO",
      title: "重做回文判断的边界样例",
      reason: "最近一次提交在空串边界仍未通过。",
      actionLabel: "去修复",
      assignmentId: 7,
      problemId: 102,
      focusAbility: "边界条件分析",
      focusTags: ["字符串", "空输入"],
      recommendationToken: "rec-primary-41",
      learningHypothesis: "当前更需要验证边界判断，而不是更换算法。",
      expectedCompletionSignal: "补充空串与单字符样例，并在后续提交中通过。",
      fallbackAction: "先手写三个最小边界样例。",
      priority: 1
    },
    {
      type: "NEXT_PROBLEM",
      title: "完成一道同类迁移题",
      reason: "用于确认问题不是只在原题中偶然解决。",
      actionLabel: "稍后练习",
      problemId: 103,
      recommendationToken: "rec-secondary-41",
      priority: 2
    }
  ]
};

async function withSignedInPage(run, viewport = { width: 1600, height: 1000 }) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport });
  await context.addInitScript(value => {
    window.sessionStorage.setItem("wzai:student", JSON.stringify(value));
  }, student);
  await context.route("**/code/api/**", async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/code/, "");
    let body = {};
    if (path === "/api/problems/catalog") body = publicProblems;
    if (path === "/api/student/profile/41/assignments") body = assignments;
    if (path === "/api/student/assignments/7/profile/41/trajectory") body = { completedTasks: 1, totalTasks: 2 };
    if (path === "/api/student/assignments/8/profile/41/trajectory") body = { completedTasks: 0, totalTasks: 1 };
    if (path === "/api/student/profile/41/ability-profile") body = {};
    if (path === "/api/student/profile/41/recommendations") body = recommendations;
    if (path === "/api/student/profile/41/recommendation-clicks") {
      await route.fulfill({ status: 204, body: "" });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });

  try {
    const page = await context.newPage();
    await page.goto(`${baseUrl}/code/student`, { waitUntil: "domcontentloaded" });
    await page.locator(".student-home-sections").waitFor({ state: "visible" });
    await run(page);
  } finally {
    await context.close();
    await browser.close();
  }
}

test("signed-in home separates continue, classroom, and self-practice areas", async () => {
  await withSignedInPage(async page => {
    const sections = page.locator(".student-home-sections > section");
    assert.equal(await sections.count(), 3);
    assert.equal(await sections.nth(0).getAttribute("data-home-zone"), "continue");
    assert.equal(await sections.nth(1).getAttribute("data-home-zone"), "classroom");
    assert.equal(await sections.nth(2).getAttribute("data-home-zone"), "practice");
    assert.equal(await page.locator(".student-self-practice-row").count(), 1);
    assert.equal(await page.locator(".student-public-task-row__difficulty").count(), 0);
    assert.equal(await page.locator(".student-guest-practice").count(), 0);
  });
});

test("next learning shows only the primary action, reason, and completion target", async () => {
  await withSignedInPage(async page => {
    const panel = page.locator(".student-next-learning");
    await panel.locator(".student-next-learning__primary").waitFor({ state: "visible" });
    assert.equal(await panel.locator(".student-next-learning__primary").count(), 1);
    assert.match(await panel.textContent(), /重做回文判断的边界样例/);
    assert.match(await panel.textContent(), /补充空串与单字符样例/);
    assert.doesNotMatch(await panel.textContent(), /当前更需要验证边界判断/);
    assert.doesNotMatch(await panel.textContent(), /先手写三个最小边界样例/);
    assert.doesNotMatch(await panel.textContent(), /完成一道同类迁移题/);
    assert.equal(await panel.locator(".student-next-learning__candidate").count(), 0);
    assert.equal(await panel.locator(".student-next-learning__tags").count(), 0);
  });
});

test("primary action preserves student identity and recommendation token", async () => {
  await withSignedInPage(async page => {
    const action = page.locator(".student-next-learning__cta");
    await action.waitFor({ state: "visible" });
    const href = await action.getAttribute("href");
    assert.ok(href);
    const url = new URL(href, baseUrl);
    assert.equal(url.pathname, "/code/student/assignments/7/problems/102");
    assert.equal(url.searchParams.get("studentProfileId"), "41");
    assert.equal(url.searchParams.get("recommendationToken"), "rec-primary-41");
  });
});

test("each classroom assignment opens directly without selection", async () => {
  await withSignedInPage(async page => {
    const firstAssignment = page.locator(".student-classroom-section .student-assignment-row").first();
    assert.equal(await firstAssignment.evaluate(element => element.tagName), "A");
    assert.equal(await page.locator('.student-assignment-row input[type="radio"]').count(), 0);
    await Promise.all([
      page.waitForURL(url => url.pathname === "/code/student/assignments/7"),
      firstAssignment.click()
    ]);
  });
});

test("signed-in home removes secondary summaries and assignment descriptions", async () => {
  await withSignedInPage(async page => {
    assert.equal(await page.locator(".student-home-command__message span").count(), 0);
    assert.equal(await page.locator(".student-assignment-board__head p").count(), 0);
    assert.equal(await page.locator(".student-assignment-table__header").count(), 0);
    assert.equal(await page.locator(".student-classroom-section .student-assignment-row__main small").count(), 0);
    assert.equal((await page.locator("body").textContent()).includes("这段作业说明不应显示"), false);
  });
});

test("signed-in home omits the recent review strip", async () => {
  await withSignedInPage(async page => {
    assert.equal(await page.locator(".student-review-strip").count(), 0);
  });
});

test("signed-in home omits the duplicated page command row", async () => {
  await withSignedInPage(async page => {
    assert.equal(await page.locator(".student-home-command--entry").count(), 0);
  });
});

test("classroom assignment rows distribute controls across the full width", async () => {
  await withSignedInPage(async page => {
    const row = page.locator(".student-classroom-section .student-assignment-row").first();
    const chevron = row.locator(".student-assignment-row__chevron");
    const progress = row.locator(".student-assignment-row__progress");
    const [rowBox, chevronBox, progressBox] = await Promise.all([
      row.boundingBox(),
      chevron.boundingBox(),
      progress.boundingBox()
    ]);
    assert.ok(rowBox && chevronBox && progressBox);
    const rightGap = rowBox.x + rowBox.width - (chevronBox.x + chevronBox.width);
    assert.ok(rightGap <= 32, `entry arrow must align near the right edge; gap=${rightGap}`);
    assert.ok(progressBox.x >= rowBox.x + rowBox.width * 0.6, `progress must occupy the right side of the row; ${JSON.stringify({ rowBox, progressBox })}`);
  });
});

test("mobile first viewport includes continue learning and classroom work", async () => {
  await withSignedInPage(async page => {
    const continueBox = await page.locator('.student-home-zone[data-home-zone="continue"]').boundingBox();
    const classroomHeadingBox = await page.locator("#student-assignment-heading").boundingBox();
    assert.ok(continueBox && classroomHeadingBox);
    assert.ok(continueBox.height <= 230, `continue area must stay compact; height=${continueBox.height}`);
    assert.ok(classroomHeadingBox.y < 844, `classroom heading must appear in initial viewport; y=${classroomHeadingBox.y}`);
  }, { width: 390, height: 844 });
});
