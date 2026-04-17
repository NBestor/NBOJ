(function () {
    function logCodeAssist(level, message, detail) {
        const method = typeof console[level] === "function" ? console[level] : console.log;
        if (detail === undefined) {
            method(`[code-assist] ${message}`);
            return;
        }
        method(`[code-assist] ${message}`, detail);
    }

    function toEditorLines(value) {
        return String(value || "")
            .replace(/\r\n/g, "\n")
            .replace(/\r/g, "\n")
            .split("\n");
    }

    function normalizeDisplayIssues(lineIssues) {
        if (!Array.isArray(lineIssues)) {
            return [];
        }

        return lineIssues
            .map(item => ({
                lineNumber: Number(item && item.lineNumber),
                error: String((item && item.error) || "").trim(),
                suggestion: String((item && item.suggestion) || "").trim()
            }))
            .filter(item => Number.isInteger(item.lineNumber) && item.lineNumber > 0 && item.error && item.suggestion)
            .sort((left, right) => left.lineNumber - right.lineNumber);
    }

    function escapeWithUi(ui, text) {
        return ui && typeof ui.escapeHtml === "function"
            ? ui.escapeHtml(text)
            : String(text || "");
    }

    function renderLineIssueTag(ui, issue) {
        return `
            <div class="code-line-tag code-line-tag--severity">
                <div class="code-line-tag__head">
                    <strong>行 ${issue.lineNumber}</strong>
                    <div class="toolbar-cluster" style="gap:0.35rem;">
                        <button type="button" class="code-line-tag__jump" data-jump-line="${issue.lineNumber}">定位</button>
                        <button type="button" class="code-line-tag__toggle" data-line-tag-toggle>收起</button>
                    </div>
                </div>
                <div class="code-line-tag__body">
                    <div><span>错误：</span>${escapeWithUi(ui, issue.error)}</div>
                    <div><span>建议：</span>${escapeWithUi(ui, issue.suggestion)}</div>
                </div>
            </div>
        `;
    }

    function renderLineIssuePreviewList(issues, options = {}) {
        const ui = options.ui;
        const limit = Math.max(1, Number(options.limit || 3));
        const safeIssues = normalizeDisplayIssues(issues).slice(0, limit);
        if (!safeIssues.length) {
            return "";
        }

        return `
            <div class="analysis-list-block">
                <h4>逐行定位</h4>
                <div class="analysis-line-issue-list">
                    ${safeIssues.map(item => `
                        <button type="button" class="analysis-line-issue-item" data-jump-line="${item.lineNumber}">
                            <strong>行 ${item.lineNumber}</strong>
                            <span>${escapeWithUi(ui, item.error)}</span>
                        </button>
                    `).join("")}
                </div>
            </div>
        `;
    }

    function renderLineIssueModalSection(issues, options = {}) {
        const ui = options.ui;
        const safeIssues = normalizeDisplayIssues(issues);
        if (!safeIssues.length) {
            return "";
        }

        return `
            <div class="modal-section">
                <h3>逐行纠错</h3>
                <div class="analysis-line-issue-list">
                    ${safeIssues.map(item => `
                        <button type="button" class="analysis-line-issue-item" data-jump-line="${item.lineNumber}">
                            <strong>行号：${item.lineNumber}</strong>
                            <span>错误：${escapeWithUi(ui, item.error)}</span>
                            <span>建议：${escapeWithUi(ui, item.suggestion)}</span>
                        </button>
                    `).join("")}
                </div>
            </div>
        `;
    }

    function create(options = {}) {
        const ui = options.ui;
        const editor = document.querySelector(options.editorSelector || "#code-editor");
        const gutter = document.querySelector(options.gutterSelector || "#code-editor-gutter");
        const backdrop = document.querySelector(options.backdropSelector || "#code-editor-backdrop");
        const insights = document.querySelector(options.insightsSelector || "#code-editor-insights");
        const onJump = typeof options.onJump === "function" ? options.onJump : null;

        const state = {
            activeLineIssues: []
        };

        function getEditorLines() {
            return toEditorLines(editor ? editor.value : "");
        }

        function normalizeLineIssues(lineIssues) {
            const maxLineNumber = getEditorLines().length;
            const dedupeKeys = new Set();

            return normalizeDisplayIssues(lineIssues)
                .filter(item => item.lineNumber <= maxLineNumber)
                .filter(item => {
                    const dedupeKey = `${item.lineNumber}|${item.error}|${item.suggestion}`;
                    if (dedupeKeys.has(dedupeKey)) {
                        return false;
                    }
                    dedupeKeys.add(dedupeKey);
                    return true;
                });
        }

        function buildIssueBuckets(lineIssues) {
            const buckets = new Map();
            lineIssues.forEach(issue => {
                if (!buckets.has(issue.lineNumber)) {
                    buckets.set(issue.lineNumber, []);
                }
                buckets.get(issue.lineNumber).push(issue);
            });
            return buckets;
        }

        function renderHighlightLayer(lines, issuesByLine) {
            if (!gutter || !backdrop) {
                return;
            }

            gutter.innerHTML = lines.map((_, index) => `
                <div class="code-editor-gutter-line ${issuesByLine.has(index + 1) ? "is-highlight" : ""}">${index + 1}</div>
            `).join("");

            backdrop.innerHTML = lines.map((_, index) => `
                <div class="code-editor-overlay-line ${issuesByLine.has(index + 1) ? "is-highlight" : ""}"></div>
            `).join("");

            gutter.scrollTop = editor ? editor.scrollTop : 0;
            backdrop.scrollTop = editor ? editor.scrollTop : 0;
        }

        function renderInsightPanel() {
            if (!insights) {
                return;
            }

            if (!state.activeLineIssues.length) {
                insights.innerHTML = "";
                insights.hidden = true;
                return;
            }

            insights.hidden = false;
            insights.innerHTML = state.activeLineIssues.map(issue => renderLineIssueTag(ui, issue)).join("");
        }

        function renderDecorations() {
            if (!editor || !gutter || !backdrop) {
                logCodeAssist("warn", "Skipped decoration render because editor hosts were missing.");
                return;
            }

            const lines = getEditorLines();
            const issuesByLine = buildIssueBuckets(state.activeLineIssues);
            renderHighlightLayer(lines, issuesByLine);
            renderInsightPanel();
        }

        function initialize() {
            window.addEventListener("resize", renderDecorations);
            logCodeAssist("info", "Initialized code insight layer.");
            renderDecorations();
        }

        function sync() {
            renderDecorations();
        }

        function setLineIssues(lineIssues) {
            state.activeLineIssues = normalizeLineIssues(lineIssues);
            logCodeAssist("info", `Applied ${state.activeLineIssues.length} line issues.`);
            renderDecorations();
            return state.activeLineIssues.slice();
        }

        function clear() {
            state.activeLineIssues = [];
            logCodeAssist("info", "Cleared active line issues.");
            renderDecorations();
        }

        function jumpToLine(lineNumber, jumpOptions = {}) {
            const targetLine = Number(lineNumber);
            if (!editor || !Number.isInteger(targetLine) || targetLine < 1) {
                logCodeAssist("warn", "Ignored invalid jump target.", { lineNumber });
                return;
            }

            const lines = getEditorLines();
            const safeLine = Math.min(targetLine, lines.length);
            let start = 0;
            for (let index = 0; index < safeLine - 1; index++) {
                start += lines[index].length + 1;
            }

            const end = start + (lines[safeLine - 1] ? lines[safeLine - 1].length : 0);
            editor.focus();
            editor.setSelectionRange(start, end);

            const lineHeight = parseFloat(window.getComputedStyle(editor).lineHeight) || 26;
            editor.scrollTop = Math.max(0, (safeLine - 2) * lineHeight);
            renderDecorations();
            logCodeAssist("info", `Jumped to line ${safeLine}.`);

            if (onJump) {
                onJump(safeLine, jumpOptions);
            }
        }

        return {
            initialize,
            sync,
            setLineIssues,
            clear,
            jumpToLine
        };
    }

    window.CodeJudgeCodeAssist = {
        create,
        renderPreviewList: renderLineIssuePreviewList,
        renderModalSection: renderLineIssueModalSection
    };
})();
