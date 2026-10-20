import { chromium } from "playwright";

const baseUrl = process.env.TEACHER_MANAGE_SMOKE_BASE_URL || "http://localhost:5173";

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function inspect(page, path) {
  await page.goto(`${baseUrl}${path}`, { waitUntil: "networkidle" });
  return page.evaluate(() => {
    const text = (selector) => document.querySelector(selector)?.textContent?.trim() || "";
    const count = (selector) => document.querySelectorAll(selector).length;
    const has = (selector) => Boolean(document.querySelector(selector));
    const rect = (selector) => {
      const element = document.querySelector(selector);
      if (!element) {
        return null;
      }
      const box = element.getBoundingClientRect();
      return {
        width: Math.round(box.width),
        height: Math.round(box.height)
      };
    };
    const heights = (selector) =>
      Array.from(document.querySelectorAll(selector)).map((element) => Math.round(element.getBoundingClientRect().height));
    const widths = (selector) =>
      Array.from(document.querySelectorAll(selector)).map((element) => Math.round(element.getBoundingClientRect().width));
    const maxHeight = (selector) => Math.max(0, ...heights(selector));
    const maxWidth = (selector) => Math.max(0, ...widths(selector));
    const doc = document.documentElement;
    return {
      url: window.location.href,
      h1: text("h1"),
      objectWorkbench: count(".management-object-workbench"),
      objectList: count(".management-object-list"),
      importDrawer: has(".management-import-drawer"),
      editorDrawer: has(".task-editor-page--embedded .editor-secondary-drawer"),
      classEmptyOption: Array.from(document.querySelectorAll("option")).some((option) => option.textContent?.trim() === "不指定班级"),
      aiEditorSectionCount: count(".standard-library-editor__section"),
      overflowX: doc.scrollWidth - doc.clientWidth,
      teacherHeader: rect(".teacher-manage-header"),
      shellNav: rect(".teacher-shell-nav"),
      workbenchBox: rect(".management-object-workbench"),
      objectListBox: rect(".management-object-list"),
      objectMainBox: rect(".management-object-main"),
      objectRowMaxHeight: maxHeight(".management-object-row"),
      objectRowMaxWidth: maxWidth(".management-object-row"),
      importSummaryMaxHeight: maxHeight(".management-import-drawer > summary"),
      importDrawerBodyMaxHeight: maxHeight(".management-import-drawer__body"),
      editorCommandBox: rect(".editor-command--embedded"),
      embeddedPanelMaxHeight: maxHeight(".task-editor-page--embedded .panel"),
      embeddedTextareaMaxHeight: maxHeight(".task-editor-page--embedded textarea.control"),
      aiEditorHead: rect(".standard-library-editor__head"),
      aiEditorSectionMaxHeight: maxHeight(".standard-library-editor__section"),
      aiTextareaMaxHeight: maxHeight(".standard-library-editor textarea.control"),
      buttonMaxHeight: maxHeight(".ui-button"),
      controlMaxHeight: maxHeight("input.control, select.control")
    };
  });
}

function assertMax(value, max, message) {
  assert(value <= max, `${message}: expected <= ${max}, got ${value}`);
}

const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 900 } });

  const legacyManagementRoute = await inspect(page, "/code/teacher/manage");
  assert(legacyManagementRoute.url.includes("/code/teacher/manage/classes"), "management root should redirect to class roster");
  assert(legacyManagementRoute.h1 === "班级名单", "management root should open class roster");
  assert(legacyManagementRoute.objectWorkbench === 1, "management root should render the class roster workbench");

  const classes = await inspect(page, "/code/teacher/manage/classes");
  assert(classes.objectWorkbench === 1, "classes page should render one object workbench");
  assert(classes.objectList === 1, "classes page should render class object list");
  assert(!classes.classEmptyOption, "classes import should not offer 不指定班级 as primary option");
  assertMax(classes.objectListBox.height, 340, "class object list should fit in the first screen");
  assertMax(classes.objectRowMaxHeight, 52, "class rows should stay dense");
  assertMax(classes.importSummaryMaxHeight, 32, "class create/import drawer summary should be compact");
  assertMax(classes.controlMaxHeight, 38, "class form controls should stay compact");

  const problems = await inspect(page, "/code/teacher/manage/problems");
  assert(problems.objectWorkbench === 1, "problems page should render one object workbench");
  assert(problems.objectList === 1, "problems page should render problem object list");
  assert(problems.importDrawer, "problems page should keep import in a compact drawer");
  assert(!problems.editorDrawer, "embedded task editor should not render its own catalog drawer");
  assertMax(problems.editorCommandBox.height, 72, "embedded problem editor command should not keep full page height");
  assertMax(problems.importSummaryMaxHeight, 32, "problem import drawer summary should be compact");
  assertMax(problems.embeddedTextareaMaxHeight, 150, "embedded problem editor textareas should be shorter than standalone editor");
  assertMax(problems.buttonMaxHeight, 38, "problem manager buttons should stay compact");

  const aiLibrary = await inspect(page, "/code/teacher/manage/ai-library");
  assert(aiLibrary.objectWorkbench === 1, "AI library page should render one object workbench");
  assert(aiLibrary.objectList === 1, "AI library page should render item object list");
  assert(aiLibrary.aiEditorSectionCount >= 2, "AI library editor should be grouped into compact sections");
  assertMax(aiLibrary.aiEditorHead.height, 52, "AI editor action head should stay thin and sticky");
  assertMax(aiLibrary.aiEditorSectionMaxHeight, 170, "AI editor sections should not become large dashboard cards");
  assertMax(aiLibrary.aiTextareaMaxHeight, 120, "AI editor long fields should stay compact by default");
  assertMax(aiLibrary.objectRowMaxHeight, 58, "AI list rows should stay scan-friendly");

  await page.setViewportSize({ width: 390, height: 844 });
  for (const path of ["/code/teacher/manage", "/code/teacher/manage/classes", "/code/teacher/manage/problems", "/code/teacher/manage/ai-library"]) {
    const result = await inspect(page, path);
    assert(result.overflowX === 0, `${path} should not have page-level horizontal overflow`);
    assertMax(result.teacherHeader.height, 154, `${path} mobile management header should not consume the first screen`);
    assertMax(result.shellNav.height, 116, `${path} mobile teacher shell nav should stay compact`);
    if (result.objectWorkbench) {
      assert(result.objectListBox.width >= 320, `${path} mobile object list should not be squeezed into a side column`);
      assert(result.objectMainBox.width >= 320, `${path} mobile object main should not be squeezed into a side column`);
    }
    if (result.editorCommandBox) {
      assertMax(result.editorCommandBox.height, 104, `${path} mobile embedded editor command should stay compact`);
    }
    if (result.aiEditorHead) {
      assertMax(result.aiEditorHead.height, 80, `${path} mobile AI editor head should stay compact`);
    }
  }

  console.log("teacher manage workbench smoke passed");
} finally {
  await browser.close();
}
