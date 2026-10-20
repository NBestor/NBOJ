import { access, readFile, readdir } from "node:fs/promises";
import { constants } from "node:fs";
import { join, resolve } from "node:path";

const appDir = resolve(import.meta.dirname, process.env.VISUAL_SMOKE_APP_DIR || "../../src/main/resources/static/code");
const indexPath = join(appDir, "index.html");
const assetDir = join(appDir, "assets");
const checks = [];

function ok(name, passed, detail = "") {
  checks.push({ name, passed, detail });
}

async function exists(path) {
  try {
    await access(path, constants.R_OK);
    return true;
  } catch {
    return false;
  }
}

const indexExists = await exists(indexPath);
ok("built app index exists", indexExists, indexPath);

const indexHtml = indexExists ? await readFile(indexPath, "utf8") : "";
ok("viewport meta is present", /name="viewport"/.test(indexHtml));
ok("app root is present", /id="root"/.test(indexHtml));

const assetFiles = (await exists(assetDir)) ? await readdir(assetDir) : [];
const cssFiles = assetFiles.filter(file => file.endsWith(".css"));
const jsFiles = assetFiles.filter(file => file.endsWith(".js"));
ok("css asset exists", cssFiles.length > 0, cssFiles.join(", "));
ok("js asset exists", jsFiles.length > 0, jsFiles.slice(0, 5).join(", "));

const cssText = (await Promise.all(cssFiles.map(file => readFile(join(assetDir, file), "utf8")))).join("\n");
const jsText = (await Promise.all(jsFiles.map(file => readFile(join(assetDir, file), "utf8")))).join("\n");

[
  ".student-assignment-grid",
  ".teacher-shell-nav",
  ".teacher-analytics-summary",
  ".teacher-analytics-focus-panel",
  ".teacher-issue-counts",
  ".teacher-student-timeline-panel",
  ".problem-compact-details",
  ".editor-workbench",
  "@media(max-width:760px)",
  "[data-theme=dark]"
].forEach(selector => ok(`css contains ${selector}`, cssText.includes(selector)));

[
  "wzai:student-change",
  "wzai:theme",
  "教师",
  "登录查看课堂学习",
  "暂无课堂作业",
  "教学结果分析",
  "全班问题",
  "当前正式版本",
  "/api/teacher/assignments/",
  "class-review-feedback",
  "ENTERED_PROBLEM"
].forEach(text => ok(`bundle contains ${text}`, jsText.includes(text)));

[
  "长期能力画像",
  "下一步推荐",
  "AI 质量趋势",
  "推荐效果"
].forEach(text => ok(`bundle omits ${text}`, !jsText.includes(text)));

const failed = checks.filter(check => !check.passed);
if (failed.length) {
  console.error("[visual-smoke] failed checks:");
  failed.forEach(check => console.error(`- ${check.name}${check.detail ? ` (${check.detail})` : ""}`));
  process.exit(1);
}

console.log(`[visual-smoke] passed ${checks.length} checks against ${appDir}`);
