(function () {
    const API_BASE = "";
    const ui = window.CodeJudgeUI;

    const STARTER_TEMPLATE = `## 题目描述
请在这里描述题目要求、约束和注意事项。

## 输入格式
- 说明输入包含哪些内容
- 说明数据范围和约束

## 输出格式
说明程序需要输出的结果格式。

## 样例
**输入：**
\`\`\`
1 2
\`\`\`

**输出：**
\`\`\`
3
\`\`\`

## 提示
- 可以补充边界情况说明
- 需要时可以继续添加更多样例
`;

    const DEFAULT_TEST_CASES = [
        { input: "3 5", expectedOutput: "8", hidden: false, origin: "默认公开样例" },
        { input: "0 0", expectedOutput: "0", hidden: true, origin: "默认隐藏测试" }
    ];

    const FORMULA_EXAMPLES = {
        color: `下面是颜色示例：

$$
\\textcolor{#d95b2b}{x^2 + y^2 = z^2}
$$

$$
\\textcolor{#2b536e}{\\int_0^1 x^2\\,dx = \\frac{1}{3}}
$$`,
        style: `下面是字体和字号示例：

$$
\\style{color:#0b7285; font-family:serif; font-size:130%}{f(x)=\\sum_{i=1}^{n} i^2}
$$

$$
\\style{color:#8a2e1c; font-size:150%; font-weight:bold}{\\nabla \\cdot \\vec{F} = 0}
$$`,
        boxed: `下面是边框和混合样式示例：

$$
\\bbox[8px,border:2px solid #d95b2b,background:#fff4e8]{E = mc^2}
$$

$$
\\style{color:#2b536e; font-size:120%}{\\text{Complexity: }}\\bbox[6px,border:1px dashed #2b536e]{O(n \\log n)}
$$`
    };

    const LARGE_TESTCASE_PREVIEW_LIMIT = 4000;
    const problemId = new URLSearchParams(window.location.search).get("id");
    const isEditMode = Boolean(problemId);
    const draftKey = isEditMode
        ? `oj:edit-problem:${problemId}:draft:v2`
        : "oj:create-problem:draft:v6";

    const titleField = document.getElementById("title");
    const difficultyField = document.getElementById("difficulty");
    const timeLimitField = document.getElementById("timeLimit");
    const memoryLimitField = document.getElementById("memoryLimit");
    const descriptionField = document.getElementById("description-editor");
    const aiPromptDirectionField = document.getElementById("ai-prompt-direction");
    const previewHost = document.getElementById("rendered-preview");
    const descriptionPreviewBody = document.getElementById("description-preview-body");
    const formulaEditor = document.getElementById("formula-style-editor");
    const formulaPreviewHost = document.getElementById("formula-style-preview");
    const testcaseList = document.getElementById("testcase-list");
    const testcaseTemplate = document.getElementById("testcase-template");
    const compactTestCaseStore = new WeakMap();
    let syncingDescriptionScroll = false;

    let activeFormulaExample = "color";
    let baselineDraft = {
        title: "",
        description: STARTER_TEMPLATE,
        difficulty: "EASY",
        timeLimit: 1000,
        memoryLimit: 131072,
        aiPromptDirection: "",
        testCases: DEFAULT_TEST_CASES
    };

    const renderPreviewDeferred = ui.debounce(() => {
        renderPreview();
        syncChecklist();
        persistDraft();
    }, 140);
    const persistDraftDeferred = ui.debounce(persistDraft, 180);

    document.addEventListener("DOMContentLoaded", () => {
        bindControls();
        initializePage().catch(error => {
            showAlert(error.message, "error");
        });
    });

    async function initializePage() {
        syncPageMode();

        if (isEditMode) {
            await hydrateEditorProblem();
        } else {
            restoreDraftOrDefaults();
        }

        activateEditorTab("description");
        initializeFormulaLab();
        syncPreviewMeta();
        renderPreview();
        syncCounts();
        syncChecklist();
    }

    function bindControls() {
        document.getElementById("problem-form").addEventListener("submit", submitProblem);
        document.getElementById("use-template-btn").addEventListener("click", useStarterTemplate);
        document.getElementById("upload-markdown-btn").addEventListener("click", () => {
            document.getElementById("markdown-upload").click();
        });
        document.getElementById("markdown-upload").addEventListener("change", importMarkdownFile);
        bindDescriptionScrollSync();

        document.querySelectorAll("[data-editor-tab]").forEach(button => {
            button.addEventListener("click", () => activateEditorTab(button.dataset.editorTab));
        });

        document.getElementById("formula-example-list").addEventListener("click", event => {
            const button = event.target.closest("[data-formula-example]");
            if (!button) {
                return;
            }
            applyFormulaExample(button.dataset.formulaExample);
        });
        document.getElementById("copy-formula-example-btn").addEventListener("click", copyFormulaExample);
        document.getElementById("insert-formula-example-btn").addEventListener("click", insertFormulaExampleIntoDescription);
        formulaEditor.addEventListener("input", renderFormulaPreview);

        document.getElementById("add-sample-btn").addEventListener("click", () => addTestCase({ hidden: false }));
        document.getElementById("add-hidden-btn").addEventListener("click", () => addTestCase({ hidden: true }));
        document.getElementById("clear-testcases-btn").addEventListener("click", resetTestCases);
        document.getElementById("import-testcase-btn").addEventListener("click", importTestCaseFiles);
        document.getElementById("import-zip-btn").addEventListener("click", () => {
            document.getElementById("zip-testcase-upload").click();
        });
        document.getElementById("zip-testcase-upload").addEventListener("change", importZipArchive);

        descriptionField.addEventListener("input", () => {
            setPreviewState("同步中...");
            renderPreviewDeferred();
        });
        aiPromptDirectionField.addEventListener("input", persistDraftDeferred);

        [titleField, difficultyField, timeLimitField, memoryLimitField].forEach(field => {
            field.addEventListener("input", handleMetaChange);
            field.addEventListener("change", handleMetaChange);
        });

        testcaseList.addEventListener("click", handleTestCaseListClick);
        testcaseList.addEventListener("input", () => {
            syncCounts();
            persistDraftDeferred();
        });
        testcaseList.addEventListener("change", () => {
            syncCounts();
            persistDraftDeferred();
        });
    }

    async function hydrateEditorProblem() {
        const response = await fetch(`${API_BASE}/api/problems/${problemId}/manage`);
        const result = await ui.readJson(response);
        if (!response.ok) {
            throw new Error(result.error || "题目编辑数据加载失败");
        }

        baselineDraft = normalizeDraft(result, false);
        const cached = ui.readCache(draftKey);
        const draft = cached && cached.value ? normalizeDraft(cached.value, false) : baselineDraft;
        applyDraft(draft);
        setPreviewState(cached ? "已恢复草稿" : "已载入");
    }

    function restoreDraftOrDefaults() {
        const cached = ui.readCache(draftKey);
        const draft = cached && cached.value ? normalizeDraft(cached.value, true) : baselineDraft;
        applyDraft(draft);
        setPreviewState(cached ? "已恢复草稿" : "已同步");
    }

    function normalizeDraft(raw, fallBackToDefaults) {
        const fallbackTestCases = fallBackToDefaults ? DEFAULT_TEST_CASES : [];
        return {
            title: raw.title || "",
            description: raw.description || STARTER_TEMPLATE,
            difficulty: raw.difficulty || "EASY",
            timeLimit: Number(raw.timeLimit || 1000),
            memoryLimit: Number(raw.memoryLimit || 131072),
            aiPromptDirection: raw.aiPromptDirection || "",
            testCases: Array.isArray(raw.testCases) && raw.testCases.length
                ? raw.testCases.map(testCase => ({
                    input: testCase.input || "",
                    expectedOutput: testCase.expectedOutput || "",
                    hidden: Boolean(testCase.hidden),
                    origin: testCase.origin || "手动录入",
                    collapsedPreview: Boolean(testCase.collapsedPreview)
                }))
                : fallbackTestCases
        };
    }

    function applyDraft(draft) {
        titleField.value = draft.title || "";
        difficultyField.value = draft.difficulty || "EASY";
        timeLimitField.value = Number(draft.timeLimit || 1000);
        memoryLimitField.value = Number(draft.memoryLimit || 131072);
        descriptionField.value = draft.description || STARTER_TEMPLATE;
        aiPromptDirectionField.value = draft.aiPromptDirection || "";

        testcaseList.innerHTML = "";
        appendTestCases(draft.testCases && draft.testCases.length ? draft.testCases : DEFAULT_TEST_CASES);
    }

    function syncPageMode() {
        const heroTitle = document.getElementById("editor-hero-title");
        const heroCopy = document.getElementById("editor-hero-copy");
        const submitTitle = document.getElementById("submit-section-title");
        const submitCopy = document.getElementById("submit-section-copy");
        const submitButton = document.getElementById("create-problem-btn");

        if (!isEditMode) {
            document.title = "创建题目 - CodeJudge";
            return;
        }

        document.title = "编辑题目 - CodeJudge";
        heroTitle.textContent = "在原有题目的基础上继续修改题面、测试点和 AI 分析方向。";
        heroCopy.textContent = "编辑模式会先带出现有配置；顶部标签仍然只保留三个主分区，方便集中处理每一类内容。";
        submitTitle.textContent = "保存修改";
        submitCopy.textContent = "校验通过后会覆盖当前题目配置，并返回题目详情页。";
        submitButton.textContent = "保存修改";
    }

    function activateEditorTab(tabKey) {
        document.querySelectorAll("[data-editor-tab]").forEach(button => {
            const active = button.dataset.editorTab === tabKey;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-selected", active ? "true" : "false");
        });

        document.querySelectorAll("[data-editor-panel]").forEach(panel => {
            const active = panel.dataset.editorPanel === tabKey;
            panel.classList.toggle("is-active", active);
            panel.hidden = !active;
        });

        if (tabKey === "description") {
            syncDescriptionScrollPositions();
        }
    }

    function handleMetaChange() {
        syncPreviewMeta();
        syncChecklist();
        persistDraftDeferred();
    }

    function useStarterTemplate() {
        descriptionField.value = STARTER_TEMPLATE;
        renderPreview();
        syncChecklist();
        persistDraft();
        showAlert("已载入默认题面模板。", "success");
    }

    function initializeFormulaLab() {
        applyFormulaExample(activeFormulaExample);
    }

    function applyFormulaExample(exampleKey) {
        activeFormulaExample = Object.prototype.hasOwnProperty.call(FORMULA_EXAMPLES, exampleKey) ? exampleKey : "color";
        formulaEditor.value = FORMULA_EXAMPLES[activeFormulaExample];
        document.querySelectorAll("[data-formula-example]").forEach(button => {
            button.classList.toggle("active", button.dataset.formulaExample === activeFormulaExample);
        });
        renderFormulaPreview();
    }

    function renderPreview() {
        previewHost.innerHTML = ui.renderAlignedMarkdownPreview(descriptionField.value);
        syncDescriptionScrollPositions();
        setPreviewState("已同步");
    }

    function renderFormulaPreview() {
        formulaPreviewHost.innerHTML = ui.renderMarkdown(formulaEditor.value);
        document.getElementById("formula-preview-state").textContent = "已同步";
    }

    function syncPreviewMeta() {
        const difficulty = difficultyField.value || "EASY";
        document.getElementById("preview-problem-title").textContent = titleField.value.trim() || "未命名题目";
        document.getElementById("preview-difficulty").textContent = ui.formatDifficulty(difficulty);
        document.getElementById("preview-difficulty").className = `difficulty-pill ${difficulty.toLowerCase()}`;
        document.getElementById("preview-time").textContent = `时限 ${Number(timeLimitField.value || 0)} ms`;
        document.getElementById("preview-memory").textContent = `空间 ${Math.round(Number(memoryLimitField.value || 0) / 1024)} MB`;
    }

    function bindDescriptionScrollSync() {
        if (!descriptionField || !descriptionPreviewBody) {
            return;
        }

        descriptionField.addEventListener("scroll", () => {
            syncDescriptionScroll(descriptionField, descriptionPreviewBody);
        });

        descriptionPreviewBody.addEventListener("scroll", () => {
            syncDescriptionScroll(descriptionPreviewBody, descriptionField);
        });
    }

    function syncDescriptionScroll(source, target) {
        if (!source || !target || syncingDescriptionScroll) {
            return;
        }

        syncingDescriptionScroll = true;
        target.scrollTop = source.scrollTop;
        target.scrollLeft = source.scrollLeft;

        window.requestAnimationFrame(() => {
            syncingDescriptionScroll = false;
        });
    }

    function syncDescriptionScrollPositions() {
        if (!descriptionField || !descriptionPreviewBody) {
            return;
        }

        descriptionPreviewBody.scrollTop = descriptionField.scrollTop;
        descriptionPreviewBody.scrollLeft = descriptionField.scrollLeft;
    }

    function renderAlignedMarkdownPreview(markdown) {
        const normalized = String(markdown || "")
            .replace(/\r\n/g, "\n")
            .replace(/\r/g, "\n");
        const lines = normalized.split("\n");
        const rows = [];
        let inCodeBlock = false;
        let inMathBlock = false;

        if (!lines.length) {
            lines.push("");
        }

        lines.forEach(line => {
            const rawLine = String(line || "");
            const trimmed = rawLine.trim();

            if (/^```/.test(trimmed)) {
                inCodeBlock = !inCodeBlock;
                rows.push(renderAlignedPreviewRow("fence", `<span class="aligned-markdown__meta">${escapePreviewHtml(trimmed || "```")}</span>`));
                return;
            }

            if (trimmed === "$$") {
                inMathBlock = !inMathBlock;
                rows.push(renderAlignedPreviewRow("math-delimiter", `<span class="aligned-markdown__meta">$$</span>`));
                return;
            }

            if (!trimmed) {
                rows.push(renderAlignedPreviewRow("empty", "&nbsp;"));
                return;
            }

            if (inCodeBlock) {
                rows.push(renderAlignedPreviewRow("code", `<code>${escapePreviewHtml(rawLine)}</code>`));
                return;
            }

            if (inMathBlock) {
                rows.push(renderAlignedPreviewRow("math", `<code>${escapePreviewHtml(rawLine)}</code>`));
                return;
            }

            const headingMatch = trimmed.match(/^(#{1,3})\s+(.*)$/);
            if (headingMatch) {
                rows.push(renderAlignedPreviewRow(`heading-${headingMatch[1].length}`, applyPreviewInlineMarkdown(headingMatch[2])));
                return;
            }

            const orderedMatch = trimmed.match(/^(\d+)\.\s+(.*)$/);
            if (orderedMatch) {
                rows.push(
                    renderAlignedPreviewRow(
                        "ordered",
                        `<span class="aligned-markdown__marker">${escapePreviewHtml(`${orderedMatch[1]}.`)}</span><span class="aligned-markdown__text">${applyPreviewInlineMarkdown(orderedMatch[2])}</span>`
                    )
                );
                return;
            }

            const listMatch = trimmed.match(/^-\s+(.*)$/);
            if (listMatch) {
                rows.push(
                    renderAlignedPreviewRow(
                        "list",
                        `<span class="aligned-markdown__marker">•</span><span class="aligned-markdown__text">${applyPreviewInlineMarkdown(listMatch[1])}</span>`
                    )
                );
                return;
            }

            const quoteMatch = trimmed.match(/^>\s?(.*)$/);
            if (quoteMatch) {
                rows.push(
                    renderAlignedPreviewRow(
                        "quote",
                        `<span class="aligned-markdown__quote-bar"></span><span class="aligned-markdown__text">${applyPreviewInlineMarkdown(quoteMatch[1])}</span>`
                    )
                );
                return;
            }

            rows.push(renderAlignedPreviewRow("paragraph", `<span class="aligned-markdown__text">${applyPreviewInlineMarkdown(rawLine)}</span>`));
        });

        return rows.join("");
    }

    function renderAlignedPreviewRow(kind, content) {
        return `<div class="aligned-markdown__row aligned-markdown__row--${kind}"><div class="aligned-markdown__cell">${content}</div></div>`;
    }

    function applyPreviewInlineMarkdown(text) {
        return escapePreviewHtml(text)
            .replace(/`([^`]+)`/g, "<code>$1</code>")
            .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
            .replace(/\*([^*]+)\*/g, "<em>$1</em>");
    }

    function escapePreviewHtml(text) {
        if (text === null || text === undefined) {
            return "";
        }

        return String(text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function syncCounts() {
        const cards = getTestCaseCards();
        const visibleCount = cards.filter(card => !card.querySelector(".testcase-hidden").checked).length;
        const hiddenCount = cards.filter(card => card.querySelector(".testcase-hidden").checked).length;
        const totalCount = cards.length;

        document.getElementById("hero-visible-count").textContent = visibleCount;
        document.getElementById("hero-hidden-count").textContent = hiddenCount;
        document.getElementById("hero-total-count").textContent = totalCount;
        document.getElementById("summary-visible-count").textContent = visibleCount;
        document.getElementById("summary-hidden-count").textContent = hiddenCount;
        document.getElementById("summary-total-count").textContent = totalCount;
        syncChecklist();
    }

    function syncChecklist() {
        const cards = getTestCaseCards();
        const visibleCount = cards.filter(card => !card.querySelector(".testcase-hidden").checked).length;
        const readyMap = {
            title: titleField.value.trim().length > 0,
            description: descriptionField.value.trim().length > 20,
            visible: visibleCount > 0,
            tests: cards.length > 0
        };

        let readyCount = 0;
        Object.entries(readyMap).forEach(([key, ready]) => {
            const item = document.querySelector(`[data-check="${key}"]`);
            if (!item) {
                return;
            }

            item.classList.toggle("ready", ready);
            item.querySelector("strong").textContent = ready ? "已完成" : "待完成";
            if (ready) {
                readyCount += 1;
            }
        });

        document.getElementById("summary-ready-count").textContent = `${readyCount} / 4`;
    }

    function createTestCaseCard(data = {}) {
        const fragment = testcaseTemplate.content.cloneNode(true);
        const card = fragment.querySelector(".testcase-card");
        applyTestCaseCardData(card, data);
        return card;
    }

    function applyTestCaseCardData(card, data = {}) {
        const normalizedData = {
            input: data.input || "",
            expectedOutput: data.expectedOutput || "",
            hidden: Boolean(data.hidden),
            origin: data.origin || "手动录入",
            collapsedPreview: data.collapsedPreview
        };

        card.querySelector(".testcase-hidden").checked = normalizedData.hidden;
        card.querySelector(".testcase-origin").textContent = normalizedData.origin;

        if (shouldCollapseTestCasePreview(normalizedData)) {
            collapseTestCasePreview(card, normalizedData);
            return;
        }

        clearCollapsedTestCasePreview(card);
        card.querySelector(".testcase-input").value = normalizedData.input;
        card.querySelector(".testcase-output").value = normalizedData.expectedOutput;
    }

    function shouldCollapseTestCasePreview(testCase) {
        if (!isLargeTestCase(testCase)) {
            return false;
        }
        if (typeof testCase.collapsedPreview === "boolean") {
            return testCase.collapsedPreview;
        }
        return /导入|ZIP|import/i.test(String(testCase.origin || ""));
    }

    function isLargeTestCase(testCase) {
        return getTestCaseSize(testCase) > LARGE_TESTCASE_PREVIEW_LIMIT;
    }

    function getTestCaseSize(testCase) {
        return String(testCase.input || "").length + String(testCase.expectedOutput || "").length;
    }

    function collapseTestCasePreview(card, testCase) {
        const input = String(testCase.input || "");
        const expectedOutput = String(testCase.expectedOutput || "");
        const grid = card.querySelector(".testcase-grid");

        compactTestCaseStore.set(card, { input, expectedOutput });
        grid.hidden = true;
        card.querySelector(".testcase-input").value = "";
        card.querySelector(".testcase-output").value = "";

        let compactHost = card.querySelector(".compact-testcase-note");
        if (!compactHost) {
            compactHost = document.createElement("div");
            compactHost.className = "compact-testcase-note";
            card.querySelector(".toggle-line").insertAdjacentElement("afterend", compactHost);
        }

        compactHost.innerHTML = `
            <strong>该数据点内容较大，默认不展开显示</strong>
            <p>输入 ${input.length} 字符，输出 ${expectedOutput.length} 字符。提交时会保留原始内容，点击下方按钮后才会载入编辑区。</p>
            <button type="button" class="btn btn-ghost compact-testcase-toggle">展开查看</button>
        `;
    }

    function clearCollapsedTestCasePreview(card) {
        compactTestCaseStore.delete(card);
        card.querySelector(".testcase-grid").hidden = false;
        card.querySelector(".compact-testcase-note")?.remove();
    }

    function expandCollapsedTestCase(card) {
        const stored = compactTestCaseStore.get(card);
        if (!stored) {
            return;
        }

        clearCollapsedTestCasePreview(card);
        card.querySelector(".testcase-input").value = stored.input;
        card.querySelector(".testcase-output").value = stored.expectedOutput;
    }

    function appendTestCases(items) {
        const fragment = document.createDocumentFragment();
        items.forEach(item => fragment.appendChild(createTestCaseCard(item)));
        testcaseList.appendChild(fragment);
        refreshTestCaseCards();
    }

    function refreshTestCaseCards() {
        const cards = getTestCaseCards();
        cards.forEach((card, index) => {
            card.querySelector(".testcase-number").textContent = index + 1;
            card.querySelector(".remove-testcase-btn").disabled = cards.length === 1;
        });
        syncCounts();
    }

    function resetTestCases() {
        testcaseList.innerHTML = "";
        appendTestCases((baselineDraft.testCases && baselineDraft.testCases.length ? baselineDraft.testCases : DEFAULT_TEST_CASES).map(testCase => ({
            ...testCase
        })));
        persistDraft();
    }

    function addTestCase(data = {}) {
        appendTestCases([{
            input: data.input || "",
            expectedOutput: data.expectedOutput || "",
            hidden: Boolean(data.hidden),
            origin: data.origin || "手动录入",
            collapsedPreview: Boolean(data.collapsedPreview)
        }]);
        persistDraftDeferred();
    }

    function handleTestCaseListClick(event) {
        const compactToggleButton = event.target.closest(".compact-testcase-toggle");
        if (compactToggleButton) {
            const compactCard = compactToggleButton.closest(".testcase-card");
            if (compactCard) {
                expandCollapsedTestCase(compactCard);
                persistDraftDeferred();
            }
            return;
        }

        const removeButton = event.target.closest(".remove-testcase-btn");
        if (!removeButton) {
            return;
        }

        const card = removeButton.closest(".testcase-card");
        if (card) {
            card.remove();
            refreshTestCaseCards();
            persistDraftDeferred();
        }
    }

    async function importMarkdownFile(event) {
        const file = event.target.files[0];
        if (!file) {
            return;
        }

        try {
            descriptionField.value = stripBom(await file.text());
            renderPreview();
            syncChecklist();
            persistDraft();
            showAlert(`已从 ${file.name} 导入 Markdown。`, "success");
        } catch (error) {
            showAlert(`Markdown 导入失败：${error.message}`, "error");
        } finally {
            event.target.value = "";
        }
    }

    async function copyFormulaExample() {
        try {
            await ui.copyText(formulaEditor.value);
            showAlert("公式样式示例已复制到剪贴板。", "success");
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    function insertFormulaExampleIntoDescription() {
        const currentValue = descriptionField.value.trimEnd();
        descriptionField.value = currentValue
            ? `${currentValue}\n\n${formulaEditor.value}`
            : formulaEditor.value;
        renderPreview();
        syncChecklist();
        persistDraft();
        activateEditorTab("description");
        showAlert("公式样式示例已插入到题面编辑区。", "success");
    }

    async function importTestCaseFiles() {
        const inputFiles = [...document.getElementById("input-files").files];
        const answerFiles = [...document.getElementById("answer-files").files];
        if (!inputFiles.length || !answerFiles.length) {
            showAlert("请先选择输入文件和答案文件。", "error");
            return;
        }

        try {
            const inputMap = await buildFileContentMap(inputFiles);
            const answerMap = await buildFileContentMap(answerFiles);
            const matchedNames = Object.keys(inputMap)
                .filter(name => Object.prototype.hasOwnProperty.call(answerMap, name))
                .sort((left, right) => left.localeCompare(right, "zh-CN", { numeric: true }));

            if (!matchedNames.length) {
                showAlert("没有找到可以配对的 `.in` / `.ans` 文件。", "error");
                return;
            }

            appendImportedTestCases(matchedNames.map(name => ({
                input: inputMap[name],
                expectedOutput: answerMap[name],
                origin: `导入文件：${name}`
            })));

            persistDraft();
            showAlert(`已成功导入 ${matchedNames.length} 组测试点。`, "success");
        } catch (error) {
            showAlert(`测试点导入失败：${error.message}`, "error");
        }
    }

    async function importZipArchive(event) {
        const file = event.target.files[0];
        if (!file) {
            return;
        }

        if (!window.JSZip) {
            showAlert("当前页面未能加载 ZIP 解压组件，请刷新后重试。", "error");
            event.target.value = "";
            return;
        }

        try {
            const zip = await window.JSZip.loadAsync(file);
            const inputMap = {};
            const answerMap = {};

            await Promise.all(Object.values(zip.files).map(async entry => {
                if (entry.dir) {
                    return;
                }

                const normalizedName = entry.name.replace(/\\/g, "/");
                if (normalizedName.startsWith("__MACOSX/")) {
                    return;
                }

                const stem = normalizedName.replace(/\.[^.]+$/, "");
                if (/\.in$/i.test(normalizedName)) {
                    inputMap[stem] = stripBom(await entry.async("text"));
                    return;
                }

                if (/\.(ans|out)$/i.test(normalizedName)) {
                    answerMap[stem] = stripBom(await entry.async("text"));
                }
            }));

            const matchedNames = Object.keys(inputMap)
                .filter(name => Object.prototype.hasOwnProperty.call(answerMap, name))
                .sort((left, right) => left.localeCompare(right, "zh-CN", { numeric: true }));

            if (!matchedNames.length) {
                showAlert("ZIP 中没有找到可配对的 `.in` 与 `.ans` / `.out` 文件。", "error");
                return;
            }

            appendImportedTestCases(matchedNames.map(name => ({
                input: inputMap[name],
                expectedOutput: answerMap[name],
                origin: `ZIP 导入：${name}`
            })));

            persistDraft();
            showAlert(`已从 ${file.name} 解压并导入 ${matchedNames.length} 组测试点。`, "success");
        } catch (error) {
            showAlert(`ZIP 导入失败：${error.message}`, "error");
        } finally {
            event.target.value = "";
        }
    }

    function appendImportedTestCases(testCases) {
        const hidden = document.getElementById("uploaded-testcase-visibility").value === "hidden";
        appendTestCases(testCases.map(testCase => ({
            input: testCase.input,
            expectedOutput: testCase.expectedOutput,
            hidden,
            origin: testCase.origin,
            collapsedPreview: isLargeTestCase(testCase)
        })));
    }

    async function buildFileContentMap(files) {
        const entries = await Promise.all(files.map(async file => {
            const stem = file.name.replace(/\.[^.]+$/, "");
            return [stem, stripBom(await file.text())];
        }));
        return Object.fromEntries(entries);
    }

    async function submitProblem(event) {
        event.preventDefault();

        const payload = collectDraft();
        if (!payload.testCases.some(testCase => !testCase.hidden)) {
            showAlert("请至少保留一个公开样例测试点。", "error");
            activateEditorTab("testcases");
            return;
        }

        const button = document.getElementById("create-problem-btn");
        const idleText = isEditMode ? "保存修改" : "发布题目";
        button.disabled = true;
        button.textContent = isEditMode ? "保存中..." : "发布中...";

        try {
            const response = await fetch(
                isEditMode ? `${API_BASE}/api/problems/${problemId}` : `${API_BASE}/api/problems`,
                {
                    method: isEditMode ? "PUT" : "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                }
            );

            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || (isEditMode ? "题目更新失败" : "创建题目失败"));
            }

            clearDraft();
            showAlert(
                isEditMode
                    ? `题目“${result.title}”已更新，正在返回详情页。`
                    : `题目“${result.title}”创建成功，正在跳转。`,
                "success"
            );
            window.setTimeout(() => {
                window.location.href = `/problem.html?id=${result.id}`;
            }, 500);
        } catch (error) {
            showAlert(error.message, "error");
        } finally {
            button.disabled = false;
            button.textContent = idleText;
        }
    }

    function collectDraft() {
        return {
            title: titleField.value.trim(),
            description: descriptionField.value,
            difficulty: difficultyField.value,
            timeLimit: Number(timeLimitField.value || 1000),
            memoryLimit: Number(memoryLimitField.value || 131072),
            aiPromptDirection: aiPromptDirectionField.value.trim(),
            testCases: getTestCaseCards().map(card => {
                const testCase = readTestCaseCard(card);
                return {
                    input: testCase.input,
                    expectedOutput: testCase.expectedOutput,
                    hidden: testCase.hidden
                };
            })
        };
    }

    function persistDraft() {
        ui.writeCache(draftKey, {
            ...collectDraft(),
            testCases: getTestCaseCards().map(readTestCaseCard)
        });
    }

    function readTestCaseCard(card) {
        const compactData = compactTestCaseStore.get(card);
        return {
            input: compactData ? compactData.input : card.querySelector(".testcase-input").value,
            expectedOutput: compactData ? compactData.expectedOutput : card.querySelector(".testcase-output").value,
            hidden: card.querySelector(".testcase-hidden").checked,
            origin: card.querySelector(".testcase-origin").textContent,
            collapsedPreview: Boolean(compactData)
        };
    }

    function clearDraft() {
        try {
            window.sessionStorage.removeItem(draftKey);
        } catch (error) {
            // ignore cache cleanup failures
        }
    }

    function getTestCaseCards() {
        return [...testcaseList.querySelectorAll(".testcase-card")];
    }

    function stripBom(text) {
        return String(text || "").replace(/^\uFEFF/, "");
    }

    function setPreviewState(text) {
        document.getElementById("preview-render-state").textContent = text;
        document.getElementById("preview-render-state-mini").textContent = text;
    }

    function showAlert(message, type) {
        document.getElementById("form-alert").innerHTML = `<div class="alert-strip ${type}">${ui.escapeHtml(message)}</div>`;
    }
})();
