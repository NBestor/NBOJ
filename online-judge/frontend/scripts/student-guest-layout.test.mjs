import assert from "node:assert/strict";
import test from "node:test";
import { chromium } from "playwright";

const baseUrl = process.env.STUDENT_LAYOUT_BASE_URL || "http://127.0.0.1:8081";

test("guest public practice and classroom login are stacked on wide screens", async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });

  try {
    const page = await context.newPage();
    await page.goto(`${baseUrl}/code/student`, { waitUntil: "domcontentloaded" });
    await page.evaluate(() => {
      Object.keys(window.sessionStorage)
        .filter(key => key.startsWith("wzai:student"))
        .forEach(key => window.sessionStorage.removeItem(key));
      window.dispatchEvent(new Event("wzai:student-change"));
    });

    const practice = page.locator(".student-guest-practice");
    const classroom = page.locator(".student-guest-login-card");
    await practice.waitFor({ state: "visible" });
    await classroom.waitFor({ state: "visible" });

    const [practiceBox, classroomBox] = await Promise.all([
      practice.boundingBox(),
      classroom.boundingBox()
    ]);

    assert.ok(practiceBox, "public practice section must have a layout box");
    assert.ok(classroomBox, "classroom login section must have a layout box");
    assert.ok(
      classroomBox.y >= practiceBox.y + practiceBox.height,
      `classroom section must be below public practice: ${JSON.stringify({ practiceBox, classroomBox })}`
    );
    assert.ok(
      Math.abs(classroomBox.x - practiceBox.x) <= 2,
      `stacked sections must share the same left edge: ${JSON.stringify({ practiceBox, classroomBox })}`
    );
  } finally {
    await context.close();
    await browser.close();
  }
});
