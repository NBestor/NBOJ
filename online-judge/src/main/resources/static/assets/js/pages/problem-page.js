(function () {
    const API_BASE = "";
    const ui = window.CodeJudgeUI;
    const problemId = new URLSearchParams(window.location.search).get("id");

    const templates = {
        71: `# Python 模板
a, b = map(int, input().split())
print(a + b)
`,
        62: `// Java 模板
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int a = sc.nextInt();
        int b = sc.nextInt();
        System.out.println(a + b);
    }
}
`,
        54: `// C++ 模板
#include <iostream>
using namespace std;

int main() {
    int a, b;
    cin >> a >> b;
    cout << a + b << endl;
    return 0;
}
`,
        63: `// JavaScript 模板
const readline = require("readline");
const rl = readline.createInterface({ input: process.stdin, output: process.stdout });

rl.on("line", (line) => {
    const [a, b] = line.split(" ").map(Number);
    console.log(a + b);
    rl.close();
});
`,
        50: `// C 模板
#include <stdio.h>

int main() {
    int a, b;
    scanf("%d %d", &a, &b);
    printf("%d\\n", a + b);
    return 0;
}
`
    };

    let currentProblem = null;
    let historySummaries = [];
    let latestSubmission = null;
    let latestComparison = null;
    let latestGrowthReport = null;
    let activeSampleIndex = 0;
    let activeModalKind = null;
    let activeModalSubmissionId = null;
    let activeProblemTab = "statement";
    let expandedPane = null;
    let codeAssist = null;

    function logProblemFeature(level, feature, message, detail) {
        const method = typeof console[level] === "function" ? console[level] : console.log;
        const prefix = `[problem-page:${feature}] ${message}`;
        if (detail === undefined) {
            method(prefix);
            return;
        }
        method(prefix, detail);
    }

    const submissionDetailCache = new Map();
    const pendingDetailLoads = new Map();
    const analysisPollers = new Map();
    const persistDraft = ui.debounce(saveDraft, 240);
    const persistCodeInsightLayout = ui.debounce(syncCodeInsightLayer, 16);

    document.addEventListener("DOMContentLoaded", () => {
        if (!problemId) {
            window.location.href = "/";
            return;
        }

        bindControls();
        activateProblemTab("statement");
        applyPaneExpanded(null);
        initializeEditor();
        initializeCodeInsightLayer();
        loadPage();
    });

    function bindControls() {
        document.getElementById("language-select").addEventListener("change", handleLanguageChange);
        document.getElementById("code-editor").addEventListener("input", () => {
            persistDraft();
            persistCodeInsightLayout();
            document.getElementById("draft-status").textContent = "正在自动保存草稿...";
        });

        document.getElementById("code-editor").addEventListener("scroll", syncCodeInsightLayer);
        document.getElementById("reset-template-btn").addEventListener("click", resetTemplate);
        document.getElementById("clear-code-btn").addEventListener("click", clearCode);
        document.getElementById("rerun-analysis-btn").addEventListener("click", rerunLatestAnalysis);
        document.getElementById("clear-insights-btn").addEventListener("click", clearCodeLineInsights);
        document.getElementById("submit-btn").addEventListener("click", submitCode);
        document.getElementById("delete-problem-btn").addEventListener("click", deleteProblem);
        document.getElementById("toggle-left-expand-btn").addEventListener("click", () => togglePaneExpanded("left"));
        document.getElementById("toggle-right-expand-btn").addEventListener("click", () => togglePaneExpanded("right"));
        document.getElementById("refresh-history-btn").addEventListener("click", () => loadHistorySummary({ keepAlert: true }));
        document.getElementById("run-compare-btn").addEventListener("click", () => loadComparison({ autoOpen: true }));
        document.getElementById("generate-report-btn").addEventListener("click", generateGrowthReport);
        document.querySelectorAll("[data-problem-tab]").forEach(button => {
            button.addEventListener("click", () => activateProblemTab(button.dataset.problemTab));
        });

        document.getElementById("sample-tab-bar").addEventListener("click", event => {
            const button = event.target.closest("[data-sample-index]");
            if (!button || !currentProblem) {
                return;
            }

            activeSampleIndex = Number(button.dataset.sampleIndex);
            renderSampleCases(currentProblem.sampleTestCases || []);
        });

        document.getElementById("sample-testcases").addEventListener("click", async event => {
            const button = event.target.closest("[data-copy-kind]");
            if (!button || !currentProblem) {
                return;
            }

            const sample = (currentProblem.sampleTestCases || [])[activeSampleIndex];
            if (!sample) {
                return;
            }

            try {
                const text = button.dataset.copyKind === "input" ? sample.input : sample.expectedOutput;
                await ui.copyText(text || "");
                showPageAlert("已复制到剪贴板。", "success");
            } catch (error) {
                showPageAlert(error.message, "error");
            }
        });

        document.getElementById("history-list").addEventListener("click", async event => {
            const historyCard = event.target.closest("[data-open-history]");
            if (historyCard && !event.target.closest("[data-compare-role], [data-open-analysis], [data-quick-compare], [data-trigger-analysis]")) {
                event.preventDefault();
                await openHistorySubmissionModal(Number(historyCard.dataset.openHistory));
                return;
            }

            const compareButton = event.target.closest("[data-compare-role]");
            if (compareButton) {
                event.preventDefault();
                event.stopPropagation();
                const targetSelect = compareButton.dataset.compareRole === "left" ? "compare-left" : "compare-right";
                document.getElementById(targetSelect).value = compareButton.dataset.submissionId;
                return;
            }

            const analysisButton = event.target.closest("[data-open-analysis]");
            if (analysisButton) {
                event.preventDefault();
                event.stopPropagation();
                await openSubmissionAnalysisModal(Number(analysisButton.dataset.openAnalysis));
                return;
            }

            const quickCompareButton = event.target.closest("[data-quick-compare]");
            if (quickCompareButton) {
                event.preventDefault();
                event.stopPropagation();
                const leftId = Number(quickCompareButton.dataset.quickCompare);
                const rightId = Number(quickCompareButton.dataset.compareTarget);
                selectComparisonPair(leftId, rightId);
                await loadComparison({ autoOpen: true });
                return;
            }

            const triggerAnalysisButton = event.target.closest("[data-trigger-analysis]");
            if (triggerAnalysisButton) {
                event.preventDefault();
                event.stopPropagation();
                await triggerSubmissionAnalysis(Number(triggerAnalysisButton.dataset.triggerAnalysis), { autoOpen: true });
            }
        });

        document.getElementById("analysis-host").addEventListener("click", async event => {
            const jumpButton = event.target.closest("[data-jump-line]");
            if (jumpButton) {
                jumpToCodeLine(Number(jumpButton.dataset.jumpLine));
                return;
            }

            const triggerButton = event.target.closest("[data-trigger-analysis]");
            if (triggerButton) {
                await triggerSubmissionAnalysis(Number(triggerButton.dataset.triggerAnalysis));
                return;
            }

            const button = event.target.closest("[data-open-analysis]");
            if (!button) {
                return;
            }

            await openSubmissionAnalysisModal(Number(button.dataset.openAnalysis));
        });

        document.getElementById("comparison-host").addEventListener("click", event => {
            const button = event.target.closest("[data-open-comparison]");
            if (!button || !latestComparison) {
                return;
            }

            openComparisonModal();
        });

        document.getElementById("growth-report-host").addEventListener("click", event => {
            const button = event.target.closest("[data-open-growth-report]");
            if (!button || !latestGrowthReport) {
                return;
            }

            openGrowthReportModal();
        });

        document.getElementById("insight-modal-body").addEventListener("click", async event => {
            const jumpButton = event.target.closest("[data-jump-line]");
            if (jumpButton) {
                jumpToCodeLine(Number(jumpButton.dataset.jumpLine));
                closeInsightModal();
                return;
            }

            const analysisButton = event.target.closest("[data-open-analysis]");
            if (analysisButton) {
                await openSubmissionAnalysisModal(Number(analysisButton.dataset.openAnalysis));
                return;
            }

            const quickCompareButton = event.target.closest("[data-quick-compare]");
            if (quickCompareButton) {
                selectComparisonPair(
                    Number(quickCompareButton.dataset.quickCompare),
                    Number(quickCompareButton.dataset.compareTarget)
                );
                await loadComparison({ autoOpen: true });
                return;
            }

            const triggerButton = event.target.closest("[data-trigger-analysis]");
            if (triggerButton) {
                await triggerSubmissionAnalysis(Number(triggerButton.dataset.triggerAnalysis), { autoOpen: true });
            }
        });

        document.getElementById("code-editor-insights").addEventListener("click", event => {
            const jumpButton = event.target.closest("[data-jump-line]");
            if (jumpButton) {
                jumpToCodeLine(Number(jumpButton.dataset.jumpLine));
                return;
            }

            const toggle = event.target.closest("[data-line-tag-toggle]");
            if (!toggle) {
                return;
            }

            const tag = toggle.closest(".code-line-tag");
            if (!tag) {
                return;
            }

            const collapsed = tag.classList.toggle("is-collapsed");
            toggle.textContent = collapsed ? "展开" : "收起";
        });

        document.getElementById("insight-modal-close").addEventListener("click", closeInsightModal);
        document.querySelectorAll("[data-modal-close]").forEach(node => {
            node.addEventListener("click", closeInsightModal);
        });

        document.addEventListener("keydown", event => {
            if (event.key === "Escape" && !document.getElementById("insight-modal").hidden) {
                closeInsightModal();
            }
        });
    }

    function activateProblemTab(tabKey) {
        activeProblemTab = tabKey || "statement";

        document.querySelectorAll("[data-problem-tab]").forEach(button => {
            const active = button.dataset.problemTab === activeProblemTab;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-selected", active ? "true" : "false");
        });

        document.querySelectorAll("[data-problem-panel]").forEach(panel => {
            const active = panel.dataset.problemPanel === activeProblemTab;
            panel.classList.toggle("is-active", active);
            panel.hidden = !active;
        });

        const statementActionGroup = document.getElementById("statement-action-group");
        if (statementActionGroup) {
            statementActionGroup.hidden = activeProblemTab !== "statement";
        }
    }

    function togglePaneExpanded(target) {
        applyPaneExpanded(expandedPane === target ? null : target);
    }

    function applyIdeExpanded(expanded) {
        applyPaneExpanded(expanded ? "right" : null);
    }

    function applyPaneExpanded(target) {
        expandedPane = target === "left" || target === "right" ? target : null;
        document.body.classList.toggle("problem-pane-expanded", Boolean(expandedPane));
        document.body.classList.toggle("problem-pane-expanded-left", expandedPane === "left");
        document.body.classList.toggle("problem-pane-expanded-right", expandedPane === "right");

        document.querySelectorAll(".section-expand-btn").forEach(button => {
            const isActive = button.dataset.expandTarget === expandedPane;
            button.classList.toggle("is-collapsed", isActive);
            button.setAttribute("aria-pressed", isActive ? "true" : "false");
            const label = button.querySelector(".section-expand-btn__label");
            if (label) {
                label.textContent = isActive ? "收起" : "扩展";
            }
        });
    }

    function initializeCodeInsightLayer() {
        if (!window.CodeJudgeCodeAssist) {
            logProblemFeature("warn", "code-assist", "CodeJudgeCodeAssist is unavailable.");
            console.warn("CodeJudgeCodeAssist is unavailable.");
            return;
        }

        codeAssist = window.CodeJudgeCodeAssist.create({
            ui,
            editorSelector: "#code-editor",
            gutterSelector: "#code-editor-gutter",
            backdropSelector: "#code-editor-backdrop",
            insightsSelector: "#code-editor-insights",
            onJump(lineNumber, options = {}) {
                if (!options.silent) {
                    showPageAlert(`Jumped to line ${lineNumber}.`, "success");
                }
            }
        });
        logProblemFeature("info", "code-assist", "Initializing code insight layer.");
        codeAssist.initialize();
    }

    function syncCodeInsightLayer() {
        if (codeAssist) {
            codeAssist.sync();
        }
    }

    function renderCodeInsightDecorationsLegacy(lineIssues) {
        const editor = document.getElementById("code-editor");
        const backdrop = document.getElementById("code-editor-backdrop");
        const gutter = document.getElementById("code-editor-gutter");
        const insights = document.getElementById("code-editor-insights");
        if (!editor || !gutter || !backdrop || !insights) {
            return;
        }

        const lines = editor.value.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
        const safeIssues = Array.isArray(lineIssues) ? lineIssues : [];
        const issueByLine = new Map(safeIssues.map(item => [Number(item.lineNumber), item]));
        gutter.innerHTML = lines.map((_, index) => `
            <div class="code-editor-gutter-line ${issueByLine.has(index + 1) ? "is-highlight" : ""}">${index + 1}</div>
        `).join("");
        backdrop.innerHTML = lines.map((_, index) => `
            <div class="code-editor-overlay-line ${issueByLine.has(index + 1) ? "is-highlight" : ""}"></div>
        `).join("");

        insights.innerHTML = lines.map((_, index) => {
            const issue = issueByLine.get(index + 1);
            if (!issue) {
                return "<div class=\"code-editor-insight-row\"></div>";
            }

            return `
                <div class="code-editor-insight-row">
                    <div class="code-line-tag code-line-tag--severity">
                        <div class="code-line-tag__head">
                            <strong>行 ${issue.lineNumber}</strong>
                            <button type="button" class="code-line-tag__toggle" data-line-tag-toggle>收起</button>
                        </div>
                        <div class="code-line-tag__body">
                            <div><span>错误：</span>${ui.escapeHtml(issue.error || "-")}</div>
                            <div><span>建议：</span>${ui.escapeHtml(issue.suggestion || "-")}</div>
                        </div>
                    </div>
                </div>
            `;
        }).join("");

        backdrop.scrollTop = editor.scrollTop;
        insights.scrollTop = editor.scrollTop;
    }

    function applyCodeLineInsights(lineIssues) {
        if (codeAssist) {
            codeAssist.setLineIssues(lineIssues);
        }
    }

    function clearCodeLineInsights() {
        if (codeAssist) {
            codeAssist.clear();
        }
    }

    function jumpToCodeLine(lineNumber, options = {}) {
        if (codeAssist) {
            codeAssist.jumpToLine(lineNumber, options);
        }
    }

    async function rerunLatestAnalysis() {
        if (!latestSubmission || !latestSubmission.id) {
            showPageAlert("还没有可纠错的提交。", "error");
            return;
        }
        await triggerSubmissionAnalysis(latestSubmission.id, { autoOpen: false });
    }

    function initializeEditor() {
        restoreDraftOrTemplate(Number(document.getElementById("language-select").value));
    }

    async function loadPage() {
        try {
            await loadProblem();
            await loadHistorySummary();
        } catch (error) {
            logProblemFeature("error", "page", "Failed to load problem page.", error);
            showPageAlert(error.message, "error");
        }
    }

    async function loadProblem() {
        logProblemFeature("info", "problem", "Loading problem detail.", { problemId });
        const response = await fetch(`${API_BASE}/api/problems/${problemId}`);
        const result = await ui.readJson(response);
        if (!response.ok) {
            throw new Error(result.error || "题目不存在或已被删除");
        }

        currentProblem = result;
        document.getElementById("edit-problem-link").href = `/problem-create.html?id=${currentProblem.id}`;
        document.title = `${currentProblem.title} - NBOJ`;
        renderProblem(currentProblem);
        logProblemFeature("info", "problem", "Problem detail loaded.", {
            title: currentProblem.title,
            sampleCount: (currentProblem.sampleTestCases || []).length
        });
    }

    async function loadHistorySummary(options = {}) {
        const response = await fetch(`${API_BASE}/api/submissions/problem/${problemId}/history-summary`);
        const result = await ui.readJson(response);
        if (!response.ok) {
            throw new Error(result.error || "提交历史加载失败");
        }

        historySummaries = Array.isArray(result) ? result : [];
        document.getElementById("submission-count").textContent = historySummaries.length;

        const focusId = options.focusId || getOpenHistorySubmissionId() || (historySummaries[0] ? historySummaries[0].id : null);
        renderHistory(focusId);
        populateComparisonSelectors();

        if (!historySummaries.length) {
            latestSubmission = null;
            latestComparison = null;
            renderLatestSubmission(null);
            renderAnalysisPanel(null);
            updateAnalysisSourcePill(null);
            clearCodeLineInsights();
            return;
        }

        const latest = await ensureSubmissionDetail(historySummaries[0].id, { syncLatest: true });
        if (latest && !latest.analysis) {
            watchAnalysis(latest.id, { silent: true });
        }

        if (options.keepAlert) {
            showPageAlert("提交历史已刷新。", "success");
        }
    }

    async function ensureSubmissionDetail(submissionId, options = {}) {
        if (submissionDetailCache.has(submissionId)) {
            const cached = submissionDetailCache.get(submissionId);
            renderSubmissionDetail(submissionId, cached);
            if (options.syncLatest) {
                syncLatestSubmission(cached);
            }
            if (!cached.analysis) {
                watchAnalysis(submissionId, { silent: true });
            }
            return cached;
        }

        if (pendingDetailLoads.has(submissionId)) {
            const pending = pendingDetailLoads.get(submissionId);
            const result = await pending;
            if (options.syncLatest) {
                syncLatestSubmission(result);
            }
            return result;
        }

        const task = fetchSubmissionDetail(submissionId);
        pendingDetailLoads.set(submissionId, task);

        try {
            const result = await task;
            submissionDetailCache.set(submissionId, result);
            renderSubmissionDetail(submissionId, result);
            if (options.syncLatest) {
                syncLatestSubmission(result);
            }
            if (!result.analysis) {
                watchAnalysis(submissionId, { silent: true });
            }
            return result;
        } finally {
            pendingDetailLoads.delete(submissionId);
        }
    }

    async function fetchSubmissionDetail(submissionId) {
        const response = await fetch(`${API_BASE}/api/submissions/${submissionId}`);
        const result = await ui.readJson(response);
        if (!response.ok) {
            throw new Error(result.error || "提交详情加载失败");
        }
        return result;
    }

    function renderProblem(problem) {
        const descriptionHost = document.getElementById("problem-description");
        descriptionHost.className = "statement-body";
        document.getElementById("problem-title").textContent = problem.title;
        document.getElementById("problem-summary").textContent = ui.extractSummary(problem.description);
        document.getElementById("difficulty-badge").textContent = ui.formatDifficulty(problem.difficulty);
        document.getElementById("difficulty-badge").className = `difficulty-pill ${(problem.difficulty || "easy").toLowerCase()}`;
        document.getElementById("time-limit-pill").textContent = `时限 ${problem.timeLimit} ms`;
        document.getElementById("memory-limit-pill").textContent = `内存 ${Math.round(problem.memoryLimit / 1024)} MB`;
        document.getElementById("sample-count").textContent = (problem.sampleTestCases || []).length;
        descriptionHost.innerHTML = ui.renderMarkdown(problem.description);
        ui.typesetMath(descriptionHost);
        window.requestAnimationFrame(() => ui.typesetMath(descriptionHost));
        logProblemFeature("info", "renderer", "Rendered problem statement.", {
            title: problem.title,
            descriptionLength: String(problem.description || "").length
        });
        renderSampleCases(problem.sampleTestCases || []);
    }

    function renderSampleCases(testCases) {
        const tabBar = document.getElementById("sample-tab-bar");
        const host = document.getElementById("sample-testcases");

        if (!testCases.length) {
            tabBar.innerHTML = "";
            host.innerHTML = `
                <div class="empty-card">
                    <h3>这道题还没有公开样例</h3>
                    <p>出题人可能只配置了隐藏测试点。</p>
                </div>
            `;
            return;
        }

        activeSampleIndex = Math.min(activeSampleIndex, testCases.length - 1);
        tabBar.innerHTML = testCases.map((item, index) => `
            <button type="button" class="tab-chip ${index === activeSampleIndex ? "active" : ""}" data-sample-index="${index}">
                样例 ${index + 1}
            </button>
        `).join("");

        const sample = testCases[activeSampleIndex];
        host.innerHTML = `
            <div class="sample-card">
                <div class="action-row" style="justify-content:space-between;margin-bottom:1rem;">
                    <div>
                        <h4>样例 ${activeSampleIndex + 1}</h4>
                        <p class="helper">支持直接复制输入与输出。</p>
                    </div>
                    <div class="toolbar-cluster">
                        <button type="button" class="btn btn-secondary" data-copy-kind="input">复制输入</button>
                        <button type="button" class="btn btn-secondary" data-copy-kind="output">复制输出</button>
                    </div>
                </div>
                <div class="testcase-grid">
                    <div class="sample-card">
                        <h4>输入</h4>
                        <pre>${ui.escapeHtml(sample.input || "")}</pre>
                    </div>
                    <div class="sample-card">
                        <h4>输出</h4>
                        <pre>${ui.escapeHtml(sample.expectedOutput || "")}</pre>
                    </div>
                </div>
            </div>
        `;
    }

    function renderHistoryLegacy(focusId) {
        const host = document.getElementById("history-list");
        if (!historySummaries.length) {
            host.innerHTML = `
                <div class="empty-card">
                    <h3>还没有提交记录</h3>
                    <p>完成一次提交后，这里会按时间倒序显示摘要与详情入口。</p>
                </div>
            `;
            return;
        }

        const latestId = historySummaries[0] ? historySummaries[0].id : null;
        host.innerHTML = historySummaries.map((submission, index) => {
            const canQuickCompare = latestId && latestId !== submission.id;
            return `
                <details class="history-card" data-submission-id="${submission.id}" ${submission.id === focusId || (!focusId && index === 0) ? "open" : ""}>
                    <summary class="history-card__summary">
                        <div>
                            <div class="history-card__title">
                                <span class="verdict-pill ${ui.getVerdictClass(submission.verdict)}">${ui.formatVerdict(submission.verdict)}</span>
                                <strong>提交 #${submission.id}</strong>
                            </div>
                            <div class="helper" style="margin-top:0.45rem;">${ui.escapeHtml(ui.formatDateTime(submission.submittedAt))} | ${ui.escapeHtml(submission.languageName || "-")}</div>
                            <p class="helper" style="margin-top:0.45rem;">${ui.escapeHtml(historySummaryText(submission))}</p>
                        </div>
                        <div class="toolbar-cluster">
                            ${canQuickCompare ? `<button type="button" class="btn btn-secondary" data-quick-compare="${submission.id}" data-compare-target="${latestId}">对比最新</button>` : ""}
                            <button type="button" class="btn btn-secondary" data-compare-role="left" data-submission-id="${submission.id}">设为基线</button>
                            <button type="button" class="btn btn-secondary" data-compare-role="right" data-submission-id="${submission.id}">设为目标</button>
                        </div>
                    </summary>
                    <div class="history-card__body" id="history-detail-${submission.id}">
                        ${renderHistoryDetailPlaceholder(submission)}
                    </div>
                </details>
            `;
        }).join("");

        host.querySelectorAll("details[data-submission-id]").forEach(details => {
            details.addEventListener("toggle", () => {
                if (details.open) {
                    ensureSubmissionDetail(Number(details.dataset.submissionId));
                }
            });
        });

        const initiallyOpen = host.querySelector("details[open]");
        if (initiallyOpen) {
            ensureSubmissionDetail(Number(initiallyOpen.dataset.submissionId));
        }
    }

    function renderHistoryDetailPlaceholder(summary) {
        return `
            <div class="history-detail-grid">
                <div class="analysis-mini-card">
                    <span>AI 状态</span>
                    <strong>${ui.escapeHtml(summary.analysisHeadline || "分析生成中")}</strong>
                </div>
                <div class="analysis-mini-card">
                    <span>耗时 / 内存</span>
                    <strong>${ui.escapeHtml(formatPerf(summary))}</strong>
                </div>
            </div>
            <div class="skeleton" style="height:180px;"></div>
        `;
    }

    function renderSubmissionDetail(submissionId, submission) {
        const host = document.getElementById(`history-detail-${submissionId}`);
        if (!host) {
            return;
        }

        const latestId = latestSubmission ? latestSubmission.id : (historySummaries[0] ? historySummaries[0].id : null);
        const canQuickCompare = latestId && latestId !== submission.id;

        host.innerHTML = `
            <div class="history-detail-grid">
                <div class="analysis-mini-card">
                    <span>AI 状态</span>
                    <strong>${ui.escapeHtml(submission.analysis ? (submission.analysis.headline || "已生成 AI 报告") : "分析生成中")}</strong>
                </div>
                <div class="analysis-mini-card">
                    <span>耗时 / 内存</span>
                    <strong>${ui.escapeHtml(formatPerf(submission))}</strong>
                </div>
            </div>
            <div class="history-inline-grid">
                <div class="sample-card">
                    <div class="section-top" style="margin-bottom:0.75rem;">
                        <div>
                            <h4>AI 反馈</h4>
                            <p class="helper">摘要留在卡片内，完整内容进入悬浮窗查看。</p>
                        </div>
                        <div class="toolbar-cluster">
                            ${submission.analysis ? `<button type="button" class="btn btn-primary" data-open-analysis="${submission.id}">打开 AI 悬浮窗</button>` : ""}
                            ${canQuickCompare ? `<button type="button" class="btn btn-ghost" data-quick-compare="${submission.id}" data-compare-target="${latestId}">与最新提交对比</button>` : ""}
                        </div>
                    </div>
                    ${renderInlineAnalysis(submission)}
                </div>
                <div class="sample-card">
                    <h4>测试点结果</h4>
                    <div class="history-testcase-list">${renderHistoryTestCases(submission)}</div>
                </div>
            </div>
            <div class="history-code">
                <div class="section-top" style="margin-bottom:0.75rem;">
                    <div>
                        <h3 style="font-size:1rem;">提交代码</h3>
                        <p class="helper">这里展示本次提交的完整源码。</p>
                    </div>
                </div>
                <pre>${ui.escapeHtml(submission.sourceCode || "")}</pre>
            </div>
        `;
    }

    function renderInlineAnalysis(submission) {
        if (!submission.analysis) {
            return `
                <div class="analysis-pending-card">
                    <strong>评测已完成</strong>
                    <p class="helper">AI 反馈仍在后台生成，稍后会自动更新到这里。</p>
                </div>
            `;
        }

        return `
            <div class="insight-teaser">
                <div class="insight-teaser__head">
                    <strong>${ui.escapeHtml(submission.analysis.headline || "AI 已完成诊断")}</strong>
                    <span class="helper">${ui.escapeHtml(submission.analysis.sourceType || "RULE_BASED_V1")}</span>
                </div>
                <p>${ui.escapeHtml(submission.analysis.summary || "已生成完整 AI 报告，可点击查看详情。")}</p>
                ${renderAnalysisPreviewLists(submission.analysis)}
            </div>
        `;
    }

    function renderHistoryTestCases(submission) {
        if (!submission.testCaseResults || !submission.testCaseResults.length) {
            return "<div class=\"helper\">没有测试点明细。</div>";
        }

        return submission.testCaseResults.map(testCase => `
            <div class="history-testcase-item">
                <div>
                    <strong>测试点 #${testCase.testCaseNumber}${testCase.hidden ? "（隐藏）" : ""}</strong>
                    <div class="helper" style="margin-top:0.3rem;">${renderTestCasePerf(testCase)}</div>
                </div>
                <span class="helper">${testCase.passed ? "通过" : "失败"}</span>
            </div>
        `).join("");
    }

    function renderLatestSubmissionLegacy(submission) {
        const panel = document.getElementById("results-panel");
        const verdictBadge = document.getElementById("verdict-badge");
        const resultsStats = document.getElementById("results-stats");
        const resultsContent = document.getElementById("results-content");

        if (!submission) {
            panel.classList.remove("is-visible");
            document.getElementById("result-status").textContent = "未提交";
            verdictBadge.className = "verdict-pill pending";
            verdictBadge.textContent = "-";
            resultsStats.textContent = "暂无评测结果。";
            resultsContent.innerHTML = `
                <div class="empty-card">
                    <h3>还没有提交</h3>
                    <p>完成一次提交后，这里会显示最新评测摘要和测试点结果。</p>
                </div>
            `;
            return;
        }

        panel.classList.add("is-visible");
        document.getElementById("result-status").textContent = ui.formatVerdict(submission.verdict);
        verdictBadge.className = `verdict-pill ${ui.getVerdictClass(submission.verdict)}`;
        verdictBadge.textContent = ui.formatVerdict(submission.verdict);
        resultsStats.textContent = `提交于 ${ui.formatDateTime(submission.submittedAt)} | ${submission.languageName || "-"} | ${formatPerf(submission)}`;

        if (!submission.testCaseResults || !submission.testCaseResults.length) {
            resultsContent.innerHTML = `
                <div class="empty-card">
                    <h3>暂无测试点详情</h3>
                    <p>当前提交还没有回传测试点明细。</p>
                </div>
            `;
            return;
        }

        resultsContent.innerHTML = `
            <div class="result-list">
                ${submission.testCaseResults.map(testCase => `
                    <div class="result-item">
                        <div class="result-badge ${testCase.passed ? "passed" : "failed"}">${testCase.passed ? "OK" : "NO"}</div>
                        <div>
                            <strong>测试点 #${testCase.testCaseNumber}${testCase.hidden ? "（隐藏）" : ""}</strong>
                            <div class="helper" style="margin-top:0.35rem;">${renderTestCasePerf(testCase)}</div>
                            ${!testCase.passed && (testCase.actualOutput || testCase.expectedOutput) ? `
                                <div class="helper" style="margin-top:0.5rem;">输出不一致，展开历史详情可查看完整代码与更多上下文。</div>
                            ` : ""}
                        </div>
                    </div>
                `).join("")}
            </div>
        `;
    }

    function renderAnalysisPanelLegacy(submission) {
        const host = document.getElementById("analysis-host");
        if (!submission || !submission.id) {
            host.innerHTML = `
                <div class="empty-card">
                    <h3>还没有 AI 报告</h3>
                    <p>完成一次提交后，这里会显示最新分析摘要与查看入口。</p>
                </div>
            `;
            return;
        }

        if (!submission.analysis) {
            host.innerHTML = `
                <div class="analysis-pending-card">
                    <strong>AI 报告生成中</strong>
                    <p class="helper">评测结果会先显示，AI 建议会在后台生成完成后单独更新。</p>
                </div>
            `;
            return;
        }

        host.innerHTML = `
            <div class="insight-teaser insight-teaser--sidebar">
                <div class="insight-teaser__head">
                    <strong>${ui.escapeHtml(submission.analysis.headline || "AI 已完成诊断")}</strong>
                    <span class="helper">${ui.escapeHtml(submission.analysis.sourceType || "RULE_BASED_V1")}</span>
                </div>
                <p>${ui.escapeHtml(submission.analysis.summary || "已生成完整报告。")}</p>
                ${renderAnalysisPreviewLists(submission.analysis)}
                <div class="hero-actions" style="margin-top:1rem;">
                    <button type="button" class="btn btn-primary" data-open-analysis="${submission.id}">打开 AI 悬浮窗</button>
                </div>
            </div>
        `;
    }

    function renderAnalysisPreviewListsLegacy(analysis) {
        const blocks = [];

        if (analysis.rootCauses && analysis.rootCauses.length) {
            blocks.push(renderPreviewList("核心问题", analysis.rootCauses, 2));
        }

        if (analysis.fixDirections && analysis.fixDirections.length) {
            blocks.push(renderPreviewList("修改方向", analysis.fixDirections, 2));
        }

        if (analysis.lineIssues && analysis.lineIssues.length) {
            blocks.push(renderPreviewList(
                "定位行",
                analysis.lineIssues.slice(0, 3).map(item => `行 ${item.lineNumber}: ${item.error}`),
                3
            ));
        }

        return blocks.join("");
    }

    function renderPreviewList(title, items, limit) {
        const safeItems = Array.isArray(items) ? items.slice(0, limit) : [];
        if (!safeItems.length) {
            return "";
        }

        return `
            <div class="analysis-list-block">
                <h4>${ui.escapeHtml(title)}</h4>
                <ul>${safeItems.map(item => `<li>${ui.escapeHtml(item)}</li>`).join("")}</ul>
            </div>
        `;
    }

    function populateComparisonSelectors() {
        const leftSelect = document.getElementById("compare-left");
        const rightSelect = document.getElementById("compare-right");
        const previousLeft = leftSelect.value;
        const previousRight = rightSelect.value;

        if (historySummaries.length < 2) {
            leftSelect.innerHTML = "";
            rightSelect.innerHTML = "";
            latestComparison = null;
            document.getElementById("comparison-host").innerHTML = `
                <div class="empty-card">
                    <h3>至少需要两次提交</h3>
                    <p>有两次及以上提交后，这里才会显示差异对比。</p>
                </div>
            `;
            return;
        }

        const options = historySummaries.map(submission => `
            <option value="${submission.id}">#${submission.id} | ${ui.formatVerdict(submission.verdict)} | ${ui.formatDateTime(submission.submittedAt)}</option>
        `).join("");

        leftSelect.innerHTML = options;
        rightSelect.innerHTML = options;

        const validIds = new Set(historySummaries.map(item => String(item.id)));
        leftSelect.value = validIds.has(previousLeft) ? previousLeft : String(historySummaries[1].id);
        rightSelect.value = validIds.has(previousRight) ? previousRight : String(historySummaries[0].id);

        if (!latestComparison) {
            document.getElementById("comparison-host").innerHTML = `
                <div class="empty-card">
                    <h3>等待生成对比</h3>
                    <p>选中两次提交后，点击“生成对比”查看差异。</p>
                </div>
            `;
        }
    }

    function renderComparison(comparison) {
        latestComparison = comparison;
        const causeChanges = Array.isArray(comparison.causeChanges) ? comparison.causeChanges : [];
        const diffStats = comparison.diffStats || { addedLines: 0, removedLines: 0, unchangedLines: 0 };

        document.getElementById("comparison-host").innerHTML = `
            <div class="compare-summary-card compare-spotlight">
                <div class="compare-spotlight__head">
                    <div>
                        <h3>${ui.escapeHtml(comparison.progressSummary || "已生成提交对比")}</h3>
                        <p class="helper">完整 diff 和错因迁移会放到悬浮窗查看，主页面只保留摘要。</p>
                    </div>
                    <button type="button" class="btn btn-primary" data-open-comparison="true">打开完整对比</button>
                </div>
                <div class="summary-grid" style="margin-top:1rem;">
                    <div class="summary-card"><span>新增代码</span><strong>${diffStats.addedLines || 0}</strong></div>
                    <div class="summary-card"><span>删除代码</span><strong>${diffStats.removedLines || 0}</strong></div>
                    <div class="summary-card"><span>保留代码</span><strong>${diffStats.unchangedLines || 0}</strong></div>
                    <div class="summary-card"><span>结果变化</span><strong>${ui.escapeHtml((comparison.baseline && comparison.baseline.verdict) || "-")} → ${ui.escapeHtml((comparison.target && comparison.target.verdict) || "-")}</strong></div>
                </div>
                <div class="history-inline-grid" style="margin-top:1rem;">
                    <div class="analysis-mini-card">
                        <span>基线提交</span>
                        <strong>#${ui.escapeHtml(comparison.baseline && comparison.baseline.submissionId)}</strong>
                        <div class="helper" style="margin-top:0.3rem;">${ui.escapeHtml(composeSnapshotMeta(comparison.baseline))}</div>
                    </div>
                    <div class="analysis-mini-card">
                        <span>目标提交</span>
                        <strong>#${ui.escapeHtml(comparison.target && comparison.target.submissionId)}</strong>
                        <div class="helper" style="margin-top:0.3rem;">${ui.escapeHtml(composeSnapshotMeta(comparison.target))}</div>
                    </div>
                </div>
                ${causeChanges.length ? `
                    <div class="analysis-list-block" style="margin-top:1rem;">
                        <h4>变化摘要</h4>
                        <ul>${causeChanges.slice(0, 3).map(item => `<li>${ui.escapeHtml(item)}</li>`).join("")}</ul>
                    </div>
                ` : ""}
            </div>
        `;
    }

    function renderGrowthReport(report) {
        latestGrowthReport = report;

        document.getElementById("growth-report-summary").innerHTML = `
            <div class="summary-card"><span>提交次数</span><strong>${report.submissionCount || 0}</strong></div>
            <div class="summary-card"><span>通过次数</span><strong>${report.acceptedCount || 0}</strong></div>
            <div class="summary-card"><span>生成时间</span><strong>${ui.escapeHtml(ui.formatDateTime(report.generatedAt))}</strong></div>
            <div class="summary-card"><span>里程碑数</span><strong>${Array.isArray(report.milestones) ? report.milestones.length : 0}</strong></div>
        `;

        document.getElementById("growth-report-host").innerHTML = `
            <div class="compare-summary-card">
                <div class="compare-spotlight__head">
                    <div>
                        <h3>${ui.escapeHtml(report.problemTitle || "成长报告已生成")}</h3>
                        <p class="helper">完整 Markdown 报告已收进悬浮窗，主页面保留里程碑预览。</p>
                    </div>
                    <button type="button" class="btn btn-primary" data-open-growth-report="true">打开完整报告</button>
                </div>
                ${renderMilestonePreview(report.milestones || [])}
            </div>
        `;
    }

    function renderMilestonePreview(milestones) {
        if (!milestones.length) {
            return `
                <div class="analysis-pending-card" style="margin-top:1rem;">
                    <strong>暂无里程碑</strong>
                    <p class="helper">当前报告未整理出可展示的关键节点。</p>
                </div>
            `;
        }

        return `
            <div class="timeline-list" style="margin-top:1rem;">
                ${milestones.slice(0, 4).map(item => `
                    <div class="timeline-item">
                        <strong>#${ui.escapeHtml(item.submissionId)} · ${ui.escapeHtml(item.verdict || "-")}</strong>
                        <div class="helper">${ui.escapeHtml(ui.formatDateTime(item.submittedAt))}</div>
                        <p>${ui.escapeHtml(item.summary || "已记录该次提交的关键变化。")}</p>
                    </div>
                `).join("")}
            </div>
        `;
    }

    function syncLatestSubmission(submission) {
        latestSubmission = submission;
        renderLatestSubmission(submission);
        renderAnalysisPanel(submission);
        updateAnalysisSourcePill(submission);
        applyCodeLineInsights(submission && submission.analysis ? submission.analysis.lineIssues : []);
    }

    async function loadComparison(options = {}) {
        const leftId = document.getElementById("compare-left").value;
        const rightId = document.getElementById("compare-right").value;
        const host = document.getElementById("comparison-host");

        if (!leftId || !rightId || leftId === rightId) {
            host.innerHTML = `
                <div class="empty-card">
                    <h3>请选择两次不同的提交</h3>
                    <p>基线和目标提交不能相同。</p>
                </div>
            `;
            return;
        }

        host.innerHTML = "<div class=\"skeleton\" style=\"height:180px;\"></div>";

        try {
            const response = await fetch(`${API_BASE}/api/submissions/compare?leftId=${leftId}&rightId=${rightId}`);
            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || "提交对比失败");
            }

            renderComparison(result);
            if (options.autoOpen) {
                openComparisonModal();
            }
        } catch (error) {
            host.innerHTML = `<div class="error-output">${ui.escapeHtml(error.message)}</div>`;
        }
    }

    async function submitCode() {
        const submitButton = document.getElementById("submit-btn");
        const payload = {
            problemId: Number(problemId),
            languageId: Number(document.getElementById("language-select").value),
            sourceCode: document.getElementById("code-editor").value
        };
        logProblemFeature("info", "submission", "Submitting code for judge.", {
            problemId: payload.problemId,
            languageId: payload.languageId,
            sourceLength: payload.sourceCode.length
        });

        submitButton.disabled = true;
        submitButton.textContent = "评测中...";
        document.getElementById("results-panel").classList.add("is-visible");
        document.getElementById("verdict-badge").className = "verdict-pill pending";
        document.getElementById("verdict-badge").textContent = "评测中";
        document.getElementById("results-stats").textContent = "正在提交到评测队列，请稍候...";
        document.getElementById("results-content").innerHTML = "<div class=\"skeleton\" style=\"height:160px;\"></div>";
        renderAnalysisPanel({ id: -1, analysis: null, analysisStatus: "WAITING_FOR_JUDGE" });
        clearCodeLineInsights();
        updateAnalysisSourcePill(null);

        try {
            const response = await fetch(`${API_BASE}/api/submissions`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || "提交失败");
            }

            const displaySubmission = {
                ...result,
                analysis: null
            };

            submissionDetailCache.set(displaySubmission.id, displaySubmission);
            mergeSummaryFromDetail(displaySubmission);
            syncLatestSubmission(displaySubmission);
            renderHistory(displaySubmission.id);
            populateComparisonSelectors();
            invalidateGrowthReportState();
            watchAnalysis(displaySubmission.id, { silent: false, initialDelayMs: 1800 });
            logProblemFeature("info", "submission", "Submission accepted by backend queue.", { submissionId: displaySubmission.id });
            showPageAlert("提交成功，评测结果已更新。AI 分析会在生成完成后单独显示。", "success");
        } catch (error) {
            logProblemFeature("error", "submission", "Submission failed.", error);
            showPageAlert(error.message, "error");
            renderLatestSubmission(latestSubmission);
            renderAnalysisPanel(latestSubmission);
            updateAnalysisSourcePill(latestSubmission);
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = "提交评测";
        }
    }

    async function generateGrowthReport() {
        const button = document.getElementById("generate-report-btn");
        button.disabled = true;
        button.textContent = "生成中...";
        document.getElementById("growth-report-host").innerHTML = "<div class=\"skeleton\" style=\"height:200px;\"></div>";

        try {
            const response = await fetch(`${API_BASE}/api/problems/${problemId}/growth-report`);
            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || "成长报告生成失败");
            }

            renderGrowthReport(result);
            enableExportButtons();
            openGrowthReportModal();
        } catch (error) {
            document.getElementById("growth-report-host").innerHTML = `<div class="error-output">${ui.escapeHtml(error.message)}</div>`;
        } finally {
            button.disabled = false;
            button.textContent = "生成成长报告";
        }
    }

    async function deleteProblem() {
        if (!currentProblem) {
            return;
        }

        const confirmed = window.confirm(`确认删除“${currentProblem.title}”？该题的测试点、提交记录和 AI 分析都会一并删除。`);
        if (!confirmed) {
            return;
        }

        const button = document.getElementById("delete-problem-btn");
        button.disabled = true;
        button.textContent = "删除中...";

        try {
            const response = await fetch(`${API_BASE}/api/problems/${currentProblem.id}`, { method: "DELETE" });
            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || "删除题目失败");
            }
            window.location.href = "/";
        } catch (error) {
            button.disabled = false;
            button.textContent = "删除题目";
            showPageAlert(error.message, "error");
        }
    }

    function handleLanguageChange(event) {
        const languageId = Number(event.target.value);
        document.getElementById("language-label").textContent = event.target.selectedOptions[0].textContent;
        restoreDraftOrTemplate(languageId);
    }

    function resetTemplate() {
        const languageId = Number(document.getElementById("language-select").value);
        document.getElementById("code-editor").value = templates[languageId] || "";
        saveDraft();
        syncCodeInsightLayer();
        document.getElementById("draft-status").textContent = "已恢复默认模板，并同步更新本地草稿。";
    }

    function clearCode() {
        document.getElementById("code-editor").value = "";
        saveDraft();
        syncCodeInsightLayer();
        document.getElementById("draft-status").textContent = "代码已清空，并同步更新本地草稿。";
    }

    async function openSubmissionAnalysisModal(submissionId) {
        if (!submissionId) {
            return;
        }

        activeModalKind = "analysis";
        activeModalSubmissionId = submissionId;
        openInsightModal({
            eyebrow: "AI Insight",
            title: `提交 #${submissionId} 的 AI 报告`,
            meta: "<span class=\"helper\">正在加载详情...</span>",
            body: "<div class=\"skeleton\" style=\"height:260px;\"></div>"
        });

        try {
            const submission = await ensureSubmissionDetail(submissionId);
            if (!submission.analysis) {
                openInsightModal({
                    eyebrow: "AI Insight",
                    title: `提交 #${submissionId} 的 AI 报告`,
                    meta: buildModalMeta([
                        ui.formatDateTime(submission.submittedAt),
                        submission.languageName || "-",
                        ui.formatVerdict(submission.verdict)
                    ]),
                    body: `
                        <div class="analysis-pending-card">
                            <strong>AI 报告仍在生成</strong>
                            <p class="helper">评测已完成，建议稍后再打开查看。</p>
                        </div>
                    `
                });
                return;
            }

            openInsightModal({
                eyebrow: "AI Insight",
                title: submission.analysis.headline || "AI 诊断报告",
                meta: buildModalMeta([
                    `提交 #${submission.id}`,
                    ui.formatDateTime(submission.submittedAt),
                    submission.languageName || "-",
                    ui.formatVerdict(submission.verdict),
                    submission.analysis.sourceType || "RULE_BASED_V1"
                ]),
                body: buildAnalysisModalContent(submission)
            });
        } catch (error) {
            openInsightModal({
                eyebrow: "AI Insight",
                title: `提交 #${submissionId} 的 AI 报告`,
                meta: "",
                body: `<div class="error-output">${ui.escapeHtml(error.message)}</div>`
            });
        }
    }

    function openComparisonModal() {
        if (!latestComparison) {
            return;
        }

        activeModalKind = "comparison";
        activeModalSubmissionId = null;
        openInsightModal({
            eyebrow: "Compare",
            title: latestComparison.progressSummary || "提交对比",
            meta: buildModalMeta([
                currentProblem ? currentProblem.title : latestComparison.problemTitle,
                `基线 #${latestComparison.baseline && latestComparison.baseline.submissionId}`,
                `目标 #${latestComparison.target && latestComparison.target.submissionId}`
            ]),
            body: buildComparisonModalContent(latestComparison)
        });
    }

    function openGrowthReportModal() {
        if (!latestGrowthReport) {
            return;
        }

        activeModalKind = "growth";
        activeModalSubmissionId = null;
        openInsightModal({
            eyebrow: "Growth",
            title: latestGrowthReport.problemTitle || "成长报告",
            meta: buildModalMeta([
                `${latestGrowthReport.submissionCount || 0} 次提交`,
                `${latestGrowthReport.acceptedCount || 0} 次通过`,
                `生成于 ${ui.formatDateTime(latestGrowthReport.generatedAt)}`
            ]),
            body: buildGrowthReportModalContent(latestGrowthReport)
        });
    }

    function openInsightModal({ eyebrow, title, meta, body }) {
        const modal = document.getElementById("insight-modal");
        const bodyHost = document.getElementById("insight-modal-body");
        document.getElementById("insight-modal-eyebrow").textContent = eyebrow || "详情";
        document.getElementById("insight-modal-title").textContent = title || "详情";
        document.getElementById("insight-modal-meta").innerHTML = meta || "";
        bodyHost.innerHTML = body || "";
        ui.typesetMath(bodyHost);
        modal.hidden = false;
        document.body.classList.add("modal-open");
    }

    function closeInsightModal() {
        const modal = document.getElementById("insight-modal");
        modal.hidden = true;
        document.body.classList.remove("modal-open");
        activeModalKind = null;
        activeModalSubmissionId = null;
    }

    function buildAnalysisModalContentLegacy(submission) {
        const analysis = submission.analysis;
        const sections = [
            `
                <div class="modal-stack">
                    <div class="summary-grid">
                        <div class="summary-card"><span>评测结果</span><strong>${ui.escapeHtml(ui.formatVerdict(submission.verdict))}</strong></div>
                        <div class="summary-card"><span>耗时 / 内存</span><strong>${ui.escapeHtml(formatPerf(submission))}</strong></div>
                        <div class="summary-card"><span>来源</span><strong>${ui.escapeHtml(analysis.sourceType || "RULE_BASED_V1")}</strong></div>
                        <div class="summary-card"><span>提交编号</span><strong>#${ui.escapeHtml(submission.id)}</strong></div>
                    </div>
                    <div class="compare-summary-card">
                        <h3>总览</h3>
                        <p>${ui.escapeHtml(analysis.summary || "AI 已生成完整报告。")}</p>
                    </div>
                </div>
            `
        ];

        if (analysis.rootCauses && analysis.rootCauses.length) {
            sections.push(renderModalListSection("问题定位", analysis.rootCauses));
        }

        if (analysis.fixDirections && analysis.fixDirections.length) {
            sections.push(renderModalListSection("修改建议", analysis.fixDirections));
        }

        if (analysis.lineIssues && analysis.lineIssues.length) {
            sections.push(renderModalListSection(
                "逐行纠错",
                analysis.lineIssues.map(item => `行号：${item.lineNumber}｜错误：${item.error}｜建议：${item.suggestion}`)
            ));
        }

        if (analysis.firstFailedCase) {
            const failedCase = analysis.firstFailedCase;
            sections.push(`
                <div class="modal-section">
                    <h3>首个失败测试点</h3>
                    <div class="testcase-grid">
                        <div class="sample-card">
                            <span class="helper">输入</span>
                            <pre>${ui.escapeHtml(failedCase.input || "")}</pre>
                        </div>
                        <div class="sample-card">
                            <span class="helper">实际输出 / 标准输出</span>
                            <pre>实际输出：
${ui.escapeHtml(failedCase.actualOutput || "")}

标准输出：
${ui.escapeHtml(failedCase.expectedOutput || "")}</pre>
                        </div>
                    </div>
                </div>
            `);
        }

        sections.push(`
            <div class="modal-section">
                <h3>完整报告</h3>
                <div class="statement-body">${ui.renderMarkdown(analysis.reportMarkdown || "暂无 AI 报告。")}</div>
            </div>
        `);

        return sections.join("");
    }

    function buildComparisonModalContent(comparison) {
        const diffStats = comparison.diffStats || { addedLines: 0, removedLines: 0, unchangedLines: 0 };
        const diffLines = Array.isArray(comparison.diffLines) ? comparison.diffLines : [];
        const changes = Array.isArray(comparison.causeChanges) ? comparison.causeChanges : [];

        return `
            <div class="modal-stack">
                <div class="summary-grid">
                    <div class="summary-card"><span>新增代码</span><strong>${diffStats.addedLines || 0}</strong></div>
                    <div class="summary-card"><span>删除代码</span><strong>${diffStats.removedLines || 0}</strong></div>
                    <div class="summary-card"><span>保留代码</span><strong>${diffStats.unchangedLines || 0}</strong></div>
                    <div class="summary-card"><span>diff 行数</span><strong>${diffLines.length}</strong></div>
                </div>

                <div class="history-inline-grid">
                    <div class="compare-summary-card">
                        <h3>基线提交 #${ui.escapeHtml(comparison.baseline && comparison.baseline.submissionId)}</h3>
                        <p class="helper">${ui.escapeHtml(composeSnapshotMeta(comparison.baseline))}</p>
                        <p style="margin-top:0.75rem;">${ui.escapeHtml((comparison.baseline && comparison.baseline.analysisSummary) || "暂无 AI 摘要。")}</p>
                    </div>
                    <div class="compare-summary-card">
                        <h3>目标提交 #${ui.escapeHtml(comparison.target && comparison.target.submissionId)}</h3>
                        <p class="helper">${ui.escapeHtml(composeSnapshotMeta(comparison.target))}</p>
                        <p style="margin-top:0.75rem;">${ui.escapeHtml((comparison.target && comparison.target.analysisSummary) || "暂无 AI 摘要。")}</p>
                    </div>
                </div>

                ${changes.length ? renderModalListSection("变化摘要", changes) : ""}

                <div class="modal-section">
                    <h3>代码 diff</h3>
                    <div class="diff-list">
                        ${diffLines.map(line => `
                            <div class="diff-line diff-line--${ui.escapeHtml(line.type || "same")}">
                                <span class="diff-line__no">${line.leftLineNumber || ""}</span>
                                <span class="diff-line__no">${line.rightLineNumber || ""}</span>
                                <code>${ui.escapeHtml(line.content || "")}</code>
                            </div>
                        `).join("")}
                    </div>
                </div>
            </div>
        `;
    }

    function buildGrowthReportModalContent(report) {
        return `
            <div class="modal-stack">
                <div class="summary-grid">
                    <div class="summary-card"><span>提交次数</span><strong>${report.submissionCount || 0}</strong></div>
                    <div class="summary-card"><span>通过次数</span><strong>${report.acceptedCount || 0}</strong></div>
                    <div class="summary-card"><span>里程碑数</span><strong>${Array.isArray(report.milestones) ? report.milestones.length : 0}</strong></div>
                    <div class="summary-card"><span>生成时间</span><strong>${ui.escapeHtml(ui.formatDateTime(report.generatedAt))}</strong></div>
                </div>
                ${report.milestones && report.milestones.length ? `
                    <div class="modal-section">
                        <h3>提交里程碑</h3>
                        <div class="timeline-list">
                            ${report.milestones.map(item => `
                                <div class="timeline-item">
                                    <strong>提交 #${ui.escapeHtml(item.submissionId)} · ${ui.escapeHtml(item.verdict || "-")}</strong>
                                    <div class="helper">${ui.escapeHtml(ui.formatDateTime(item.submittedAt))}</div>
                                    <p>${ui.escapeHtml(item.summary || "该次提交产生了新的学习节点。")}</p>
                                </div>
                            `).join("")}
                        </div>
                    </div>
                ` : ""}
                <div class="modal-section">
                    <h3>完整复盘</h3>
                    <div class="statement-body">${ui.renderMarkdown(report.markdown || "暂无成长报告内容。")}</div>
                </div>
            </div>
        `;
    }

    function renderModalListSection(title, items) {
        return `
            <div class="modal-section">
                <h3>${ui.escapeHtml(title)}</h3>
                <div class="compare-summary-card">
                    <ul>${items.map(item => `<li>${ui.escapeHtml(item)}</li>`).join("")}</ul>
                </div>
            </div>
        `;
    }

    function buildModalMeta(parts) {
        const safeParts = (Array.isArray(parts) ? parts : []).filter(Boolean);
        return safeParts.map(item => `<span>${ui.escapeHtml(item)}</span>`).join("");
    }

    function composeSnapshotMeta(snapshot) {
        if (!snapshot) {
            return "-";
        }

        return [
            snapshot.languageName || "-",
            snapshot.verdict || "-",
            ui.formatDateTime(snapshot.submittedAt)
        ].filter(Boolean).join(" | ");
    }

    function selectComparisonPair(leftId, rightId) {
        document.getElementById("compare-left").value = String(leftId);
        document.getElementById("compare-right").value = String(rightId);
    }

    function renderTestCasePerf(testCase) {
        const parts = [];
        if (testCase.executionTime !== null && testCase.executionTime !== undefined) {
            parts.push(`${Number(testCase.executionTime).toFixed(3)}s`);
        }
        if (testCase.memoryUsed !== null && testCase.memoryUsed !== undefined) {
            parts.push(`${(Number(testCase.memoryUsed) / 1024).toFixed(2)}MB`);
        }
        return parts.length ? parts.join(" / ") : "未返回性能数据";
    }

    function mergeSummaryFromDetail(submission) {
        const summary = {
            id: submission.id,
            problemId: submission.problemId,
            problemTitle: submission.problemTitle,
            languageId: submission.languageId,
            languageName: submission.languageName,
            verdict: submission.verdict,
            executionTime: submission.executionTime,
            memoryUsed: submission.memoryUsed,
            submittedAt: submission.submittedAt,
            passedTestCases: Array.isArray(submission.testCaseResults) ? submission.testCaseResults.filter(item => item.passed).length : 0,
            totalTestCases: Array.isArray(submission.testCaseResults) ? submission.testCaseResults.length : 0,
            analysisStatus: submission.analysisStatus || (submission.analysis ? "READY" : "PROCESSING"),
            analysisSourceType: submission.analysis ? submission.analysis.sourceType : null,
            analysisHeadline: submission.analysis ? submission.analysis.headline : null,
            analysisSummary: submission.analysis ? submission.analysis.summary : null
        };

        historySummaries = historySummaries.filter(item => item.id !== submission.id);
        historySummaries.unshift(summary);
        document.getElementById("submission-count").textContent = historySummaries.length;
    }

    function enableExportButtons() {
        const markdownButton = document.getElementById("export-markdown-btn");
        const pdfButton = document.getElementById("export-pdf-btn");

        markdownButton.href = `${API_BASE}/api/problems/${problemId}/growth-report/export?format=markdown`;
        pdfButton.href = `${API_BASE}/api/problems/${problemId}/growth-report/export?format=pdf`;
        markdownButton.classList.remove("is-disabled");
        pdfButton.classList.remove("is-disabled");
        markdownButton.removeAttribute("aria-disabled");
        pdfButton.removeAttribute("aria-disabled");
    }

    function invalidateGrowthReportState() {
        latestGrowthReport = null;
        document.getElementById("growth-report-summary").innerHTML = "";
        document.getElementById("growth-report-host").innerHTML = `
            <div class="empty-card">
                <h3>成长报告需要重新生成</h3>
                <p>检测到新的提交，建议重新生成成长报告以同步最新进展。</p>
            </div>
        `;
        document.getElementById("export-markdown-btn").classList.add("is-disabled");
        document.getElementById("export-pdf-btn").classList.add("is-disabled");
        document.getElementById("export-markdown-btn").setAttribute("aria-disabled", "true");
        document.getElementById("export-pdf-btn").setAttribute("aria-disabled", "true");
        document.getElementById("export-markdown-btn").removeAttribute("href");
        document.getElementById("export-pdf-btn").removeAttribute("href");
    }

    function renderHistory() {
        const host = document.getElementById("history-list");
        if (!historySummaries.length) {
            host.innerHTML = `
                <div class="empty-card">
                    <h3>还没有提交记录</h3>
                    <p>完成一次提交后，这里会按时间倒序展示测试摘要。</p>
                </div>
            `;
            return;
        }

        host.innerHTML = historySummaries.map(summary => `
            <button type="button" class="history-card history-card--compact" data-open-history="${summary.id}">
                <div class="history-card__summary">
                    <div>
                        <div class="history-card__title">
                            <span class="verdict-pill ${ui.getVerdictClass(summary.verdict)}">${ui.formatVerdict(summary.verdict)}</span>
                            <strong>提交 #${summary.id}</strong>
                        </div>
                        <div class="helper" style="margin-top:0.45rem;">${ui.escapeHtml(ui.formatDateTime(summary.submittedAt))}</div>
                    </div>
                    <div class="history-card__metrics">
                        <span class="history-rate">${ui.escapeHtml(formatPassRate(summary))}</span>
                        <span class="history-ai-state ${getAnalysisStateClass(summary.analysisStatus)}">${ui.escapeHtml(formatAnalysisState(summary.analysisStatus))}</span>
                    </div>
                </div>
            </button>
        `).join("");
    }

    async function openHistorySubmissionModal(submissionId) {
        if (!submissionId) {
            return;
        }

        activeModalKind = "history";
        activeModalSubmissionId = submissionId;
        openInsightModal({
            eyebrow: "History",
            title: `提交 #${submissionId} 详情`,
            meta: "<span class=\"helper\">正在加载提交详情...</span>",
            body: "<div class=\"skeleton\" style=\"height:280px;\"></div>"
        });

        try {
            const submission = await ensureSubmissionDetail(submissionId, { syncLatest: false });
            if (activeModalKind !== "history" || activeModalSubmissionId !== submissionId) {
                return;
            }

            openInsightModal({
                eyebrow: "History",
                title: `提交 #${submission.id} 详情`,
                meta: buildModalMeta([
                    ui.formatDateTime(submission.submittedAt),
                    submission.languageName || "-",
                    ui.formatVerdict(submission.verdict),
                    formatPassRate(submission),
                    formatAnalysisState(submission.analysisStatus)
                ]),
                body: buildHistoryModalContent(submission)
            });
        } catch (error) {
            openInsightModal({
                eyebrow: "History",
                title: `提交 #${submissionId} 详情`,
                meta: "",
                body: `<div class="error-output">${ui.escapeHtml(error.message)}</div>`
            });
        }
    }

    function buildHistoryModalContent(submission) {
        const latestId = latestSubmission ? latestSubmission.id : (historySummaries[0] ? historySummaries[0].id : null);
        const canQuickCompare = latestId && latestId !== submission.id;
        return `
            <div class="modal-stack">
                <div class="summary-grid">
                    <div class="summary-card"><span>评测状态</span><strong>${ui.escapeHtml(ui.formatVerdict(submission.verdict))}</strong></div>
                    <div class="summary-card"><span>测试点通过率</span><strong>${ui.escapeHtml(formatPassRate(submission))}</strong></div>
                    <div class="summary-card"><span>性能</span><strong>${ui.escapeHtml(formatPerf(submission))}</strong></div>
                    <div class="summary-card"><span>AI 状态</span><strong>${ui.escapeHtml(formatAnalysisState(submission.analysisStatus))}</strong></div>
                </div>
                <div class="history-inline-grid">
                    <div class="sample-card">
                        <div class="section-top" style="margin-bottom:0.75rem;">
                            <div>
                                <h4>AI 评价</h4>
                                <p class="helper">测试结果先返回，AI 分析独立后台生成。</p>
                            </div>
                            <div class="toolbar-cluster">
                                ${submission.analysis ? `<button type="button" class="btn btn-primary" data-open-analysis="${submission.id}">打开 AI 报告</button>` : `<button type="button" class="btn btn-secondary" data-trigger-analysis="${submission.id}">重新触发 AI</button>`}
                                ${canQuickCompare ? `<button type="button" class="btn btn-ghost" data-quick-compare="${submission.id}" data-compare-target="${latestId}">与最新提交对比</button>` : ""}
                            </div>
                        </div>
                        ${renderInlineAnalysis(submission)}
                    </div>
                    <div class="sample-card">
                        <h4>测试点明细</h4>
                        <div class="history-testcase-list">${renderHistoryTestCases(submission)}</div>
                    </div>
                </div>
                <div class="history-code">
                    <div class="section-top" style="margin-bottom:0.75rem;">
                        <div>
                            <h3 style="font-size:1rem;">完整代码</h3>
                            <p class="helper">这里展示本次提交的完整源代码。</p>
                        </div>
                    </div>
                    <pre>${ui.escapeHtml(submission.sourceCode || "")}</pre>
                </div>
            </div>
        `;
    }

    async function triggerSubmissionAnalysis(submissionId, options = {}) {
        if (!submissionId) {
            return;
        }

        logProblemFeature("info", "analysis", "Triggering submission analysis.", { submissionId, autoOpen: Boolean(options.autoOpen) });
        try {
            const response = await fetch(`${API_BASE}/api/submissions/${submissionId}/analysis`, {
                method: "POST"
            });
            const result = await ui.readJson(response);
            if (!response.ok && response.status !== 202) {
                throw new Error(result.error || "重新触发 AI 分析失败");
            }

            historySummaries = historySummaries.map(summary => summary.id === submissionId
                ? {
                    ...summary,
                    analysisStatus: "PROCESSING"
                }
                : summary);

            const cached = submissionDetailCache.get(submissionId);
            if (cached) {
                cached.analysis = null;
                cached.analysisStatus = "PROCESSING";
                submissionDetailCache.set(submissionId, cached);
            }

            if (latestSubmission && latestSubmission.id === submissionId) {
                latestSubmission = {
                    ...latestSubmission,
                    analysis: null,
                    analysisStatus: "PROCESSING"
                };
                syncLatestSubmission(latestSubmission);
            } else {
                renderHistory();
            }

            clearCodeLineInsights();

            watchAnalysis(submissionId, { silent: false });
            logProblemFeature("info", "analysis", "Submission analysis queued.", { submissionId });
            if (options.autoOpen) {
                await openHistorySubmissionModal(submissionId);
            }
            showPageAlert("AI 分析已重新加入后台队列。", "success");
        } catch (error) {
            logProblemFeature("error", "analysis", "Failed to trigger submission analysis.", error);
            showPageAlert(error.message, "error");
        }
    }

    function renderLatestSubmission(submission) {
        const panel = document.getElementById("results-panel");
        const verdictBadge = document.getElementById("verdict-badge");
        const resultsStats = document.getElementById("results-stats");
        const resultsContent = document.getElementById("results-content");

        if (!submission) {
            panel.classList.remove("is-visible");
            document.getElementById("result-status").textContent = "未提交";
            verdictBadge.className = "verdict-pill pending";
            verdictBadge.textContent = "-";
            resultsStats.textContent = "暂无评测结果。";
            resultsContent.innerHTML = `
                <div class="empty-card">
                    <h3>还没有提交</h3>
                    <p>完成一次提交后，这里会显示最新评测摘要和测试点结果。</p>
                </div>
            `;
            return;
        }

        panel.classList.add("is-visible");

        if (submission.analysisStatus === "WAITING_FOR_JUDGE") {
            document.getElementById("result-status").textContent = "测试中";
            verdictBadge.className = "verdict-pill pending";
            verdictBadge.textContent = "测试中";
            resultsStats.textContent = "正在执行测试点，AI 分析尚未开始。";
            resultsContent.innerHTML = "<div class=\"skeleton\" style=\"height:160px;\"></div>";
            return;
        }

        document.getElementById("result-status").textContent = ui.formatVerdict(submission.verdict);
        verdictBadge.className = `verdict-pill ${ui.getVerdictClass(submission.verdict)}`;
        verdictBadge.textContent = ui.formatVerdict(submission.verdict);
        resultsStats.textContent = `提交于 ${ui.formatDateTime(submission.submittedAt)} | ${submission.languageName || "-"} | ${formatPerf(submission)} | ${formatAnalysisState(submission.analysisStatus)}`;

        if (!submission.testCaseResults || !submission.testCaseResults.length) {
            resultsContent.innerHTML = `
                <div class="empty-card">
                    <h3>暂无测试点详情</h3>
                    <p>当前提交还没有回传测试点明细。</p>
                </div>
            `;
            return;
        }

        resultsContent.innerHTML = `
            <div class="result-list">
                ${submission.testCaseResults.map(testCase => `
                    <div class="result-item">
                        <div class="result-badge ${testCase.passed ? "passed" : "failed"}">${testCase.passed ? "OK" : "NO"}</div>
                        <div>
                            <strong>测试点 #${testCase.testCaseNumber}${testCase.hidden ? "（隐藏）" : ""}</strong>
                            <div class="helper" style="margin-top:0.35rem;">${renderTestCasePerf(testCase)}</div>
                        </div>
                    </div>
                `).join("")}
            </div>
        `;
    }

    function renderAnalysisPanel(submission) {
        const host = document.getElementById("analysis-host");
        if (!submission || !submission.id) {
            host.innerHTML = `
                <div class="empty-card">
                    <h3>还没有 AI 报告</h3>
                    <p>完成一次提交后，这里会显示最新分析摘要与查看入口。</p>
                </div>
            `;
            return;
        }

        if (submission.analysisStatus === "WAITING_FOR_JUDGE") {
            host.innerHTML = `
                <div class="analysis-pending-card">
                    <strong>测试中</strong>
                    <p class="helper">测试尚未完成，AI 分析不会阻塞当前评测。</p>
                </div>
            `;
            return;
        }

        if (!submission.analysis) {
            host.innerHTML = `
                <div class="analysis-pending-card">
                    <strong>AI 分析中</strong>
                    <p class="helper">评测结果已返回，AI 建议正在后台独立生成。</p>
                    <div class="hero-actions" style="margin-top:1rem;">
                        <button type="button" class="btn btn-secondary" data-trigger-analysis="${submission.id}">重新触发 AI</button>
                    </div>
                </div>
            `;
            return;
        }

        host.innerHTML = `
            <div class="insight-teaser insight-teaser--sidebar">
                <div class="insight-teaser__head">
                    <strong>${ui.escapeHtml(submission.analysis.headline || "AI 已完成诊断")}</strong>
                    <span class="helper">${ui.escapeHtml(submission.analysis.sourceType || "RULE_BASED_V1")}</span>
                </div>
                <p>${ui.escapeHtml(submission.analysis.summary || "已生成完整报告。")}</p>
                ${renderAnalysisPreviewLists(submission.analysis)}
                <div class="hero-actions" style="margin-top:1rem;">
                    <button type="button" class="btn btn-primary" data-open-analysis="${submission.id}">打开 AI 报告</button>
                </div>
            </div>
        `;
    }

    function restoreDraftOrTemplate(languageId) {
        const draft = localStorage.getItem(getDraftKey(languageId));
        document.getElementById("code-editor").value = draft !== null ? draft : (templates[languageId] || "");
        syncCodeInsightLayer();
        document.getElementById("draft-status").textContent = draft !== null ? "已恢复本地草稿。" : "已载入默认模板。";
    }

    function saveDraft() {
        const languageId = Number(document.getElementById("language-select").value);
        localStorage.setItem(getDraftKey(languageId), document.getElementById("code-editor").value);
        document.getElementById("draft-status").textContent = `草稿已于 ${new Date().toLocaleTimeString("zh-CN", { hour12: false })} 自动保存。`;
    }

    function getDraftKey(languageId) {
        return `oj:draft:${problemId}:${languageId}`;
    }

    function showPageAlert(message, type) {
        document.getElementById("page-alert").innerHTML = `<div class="alert-strip ${type}">${ui.escapeHtml(message)}</div>`;
    }

    function formatPerf(submission) {
        const timeText = submission && submission.executionTime !== null && submission.executionTime !== undefined
            ? `${Number(submission.executionTime).toFixed(3)}s`
            : "-";
        const memoryText = submission && submission.memoryUsed !== null && submission.memoryUsed !== undefined
            ? `${(Number(submission.memoryUsed) / 1024).toFixed(2)}MB`
            : "-";
        return `${timeText} / ${memoryText}`;
    }

    function historySummaryText(summary) {
        return summary.analysisSummary || "评测结果已返回，AI 分析可能仍在生成中。";
    }

    function formatPassRate(summary) {
        const fallbackResults = Array.isArray(summary && summary.testCaseResults) ? summary.testCaseResults : [];
        const passed = summary && summary.passedTestCases !== undefined
            ? Number(summary.passedTestCases || 0)
            : fallbackResults.filter(item => item.passed).length;
        const total = summary && summary.totalTestCases !== undefined
            ? Number(summary.totalTestCases || 0)
            : fallbackResults.length;
        return total > 0 ? `${passed}/${total} 通过` : "0/0 通过";
    }

    function formatAnalysisState(status) {
        return status === "READY" ? "AI 已完成" : "AI 分析中";
    }

    function getAnalysisStateClass(status) {
        return status === "READY" ? "ready" : "processing";
    }

    function updateAnalysisSourcePillLegacy(submission) {
        document.getElementById("analysis-source-pill").textContent = submission && submission.analysis
            ? `分析来源 ${submission.analysis.sourceType || "RULE_BASED_V1"}`
            : "AI 分析待生成";
    }

    function updateAnalysisSourcePill(submission) {
        document.getElementById("analysis-source-pill").textContent = submission && submission.analysis
            ? `分析来源 ${submission.analysis.sourceType || "RULE_BASED_V1"}`
            : (submission && submission.analysisStatus === "PROCESSING" ? "AI 分析中" : "AI 分析待生成");
    }

    function jumpToCodeLineLegacyFinal(lineNumber, options = {}) {
        const editor = document.getElementById("code-editor");
        const targetLine = Number(lineNumber);
        if (!editor || !Number.isInteger(targetLine) || targetLine < 1) {
            return;
        }

        const lines = editor.value.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
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
        syncCodeInsightLayer();

        if (!options.silent) {
            showPageAlert(`已定位到第 ${safeLine} 行。`, "success");
        }
    }

    function renderCodeInsightDecorationsLegacyFinal(lineIssues) {
        const editor = document.getElementById("code-editor");
        const gutter = document.getElementById("code-editor-gutter");
        const backdrop = document.getElementById("code-editor-backdrop");
        const insights = document.getElementById("code-editor-insights");
        if (!editor || !gutter || !backdrop || !insights) {
            return;
        }

        const lines = editor.value.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
        const safeIssues = Array.isArray(lineIssues) ? lineIssues : [];
        const issueByLine = new Map(safeIssues.map(item => [Number(item.lineNumber), item]));

        gutter.innerHTML = lines.map((_, index) => `
            <div class="code-editor-gutter-line ${issueByLine.has(index + 1) ? "is-highlight" : ""}">${index + 1}</div>
        `).join("");

        backdrop.innerHTML = lines.map((_, index) => `
            <div class="code-editor-overlay-line ${issueByLine.has(index + 1) ? "is-highlight" : ""}"></div>
        `).join("");

        insights.innerHTML = lines.map((_, index) => {
            const issue = issueByLine.get(index + 1);
            if (!issue) {
                return "<div class=\"code-editor-insight-row\"></div>";
            }

            return `
                <div class="code-editor-insight-row">
                    <div class="code-line-tag code-line-tag--severity">
                        <div class="code-line-tag__head">
                            <strong>行 ${issue.lineNumber}</strong>
                            <div class="toolbar-cluster" style="gap:0.35rem;">
                                <button type="button" class="code-line-tag__jump" data-jump-line="${issue.lineNumber}">定位</button>
                                <button type="button" class="code-line-tag__toggle" data-line-tag-toggle>收起</button>
                            </div>
                        </div>
                        <div class="code-line-tag__body">
                            <div><span>错误：</span>${ui.escapeHtml(issue.error || "-")}</div>
                            <div><span>建议：</span>${ui.escapeHtml(issue.suggestion || "-")}</div>
                        </div>
                    </div>
                </div>
            `;
        }).join("");

        gutter.scrollTop = editor.scrollTop;
        backdrop.scrollTop = editor.scrollTop;
        insights.scrollTop = editor.scrollTop;
    }

    function renderLineIssuePreviewListLegacy(issues, limit) {
        const safeIssues = Array.isArray(issues) ? issues.slice(0, limit) : [];
        if (!safeIssues.length) {
            return "";
        }

        return `
            <div class="analysis-list-block">
                <h4>定位行</h4>
                <div class="analysis-line-issue-list">
                    ${safeIssues.map(item => `
                        <button type="button" class="analysis-line-issue-item" data-jump-line="${item.lineNumber}">
                            <strong>行 ${item.lineNumber}</strong>
                            <span>${ui.escapeHtml(item.error || "-")}</span>
                        </button>
                    `).join("")}
                </div>
            </div>
        `;
    }

    function renderLineIssuePreviewList(issues, limit) {
        return window.CodeJudgeCodeAssist
            ? window.CodeJudgeCodeAssist.renderPreviewList(issues, { limit, ui })
            : "";
    }

    function renderAnalysisPreviewListsLegacyFinal(analysis) {
        const blocks = [];

        if (analysis.rootCauses && analysis.rootCauses.length) {
            blocks.push(renderPreviewList("核心问题", analysis.rootCauses, 2));
        }

        if (analysis.fixDirections && analysis.fixDirections.length) {
            blocks.push(renderPreviewList("修改方向", analysis.fixDirections, 2));
        }

        if (analysis.lineIssues && analysis.lineIssues.length) {
            blocks.push(renderLineIssuePreviewList(analysis.lineIssues, 3));
        }

        return blocks.join("");
    }

    function renderLineIssueModalSectionLegacy(issues) {
        const safeIssues = Array.isArray(issues) ? issues : [];
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
                            <span>错误：${ui.escapeHtml(item.error || "-")}</span>
                            <span>建议：${ui.escapeHtml(item.suggestion || "-")}</span>
                        </button>
                    `).join("")}
                </div>
            </div>
        `;
    }

    function renderLineIssueModalSection(issues) {
        return window.CodeJudgeCodeAssist
            ? window.CodeJudgeCodeAssist.renderModalSection(issues, { ui })
            : "";
    }

    function buildAnalysisModalContentLegacyFinal(submission) {
        const analysis = submission.analysis;
        const sections = [
            `
                <div class="modal-stack">
                    <div class="summary-grid">
                        <div class="summary-card"><span>评测结果</span><strong>${ui.escapeHtml(ui.formatVerdict(submission.verdict))}</strong></div>
                        <div class="summary-card"><span>耗时 / 内存</span><strong>${ui.escapeHtml(formatPerf(submission))}</strong></div>
                        <div class="summary-card"><span>来源</span><strong>${ui.escapeHtml(analysis.sourceType || "RULE_BASED_V1")}</strong></div>
                        <div class="summary-card"><span>提交编号</span><strong>#${ui.escapeHtml(submission.id)}</strong></div>
                    </div>
                    <div class="compare-summary-card">
                        <h3>总览</h3>
                        <p>${ui.escapeHtml(analysis.summary || "AI 已生成完整报告。")}</p>
                    </div>
                </div>
            `
        ];

        if (analysis.rootCauses && analysis.rootCauses.length) {
            sections.push(renderModalListSection("问题定位", analysis.rootCauses));
        }

        if (analysis.fixDirections && analysis.fixDirections.length) {
            sections.push(renderModalListSection("修改建议", analysis.fixDirections));
        }

        if (analysis.lineIssues && analysis.lineIssues.length) {
            sections.push(renderLineIssueModalSection(analysis.lineIssues));
        }

        if (analysis.firstFailedCase) {
            const failedCase = analysis.firstFailedCase;
            sections.push(`
                <div class="modal-section">
                    <h3>首个失败测试点</h3>
                    <div class="testcase-grid">
                        <div class="sample-card">
                            <span class="helper">输入</span>
                            <pre>${ui.escapeHtml(failedCase.input || "")}</pre>
                        </div>
                        <div class="sample-card">
                            <span class="helper">实际输出 / 标准输出</span>
                            <pre>实际输出：${ui.escapeHtml(failedCase.actualOutput || "")}

标准输出：${ui.escapeHtml(failedCase.expectedOutput || "")}</pre>
                        </div>
                    </div>
                </div>
            `);
        }

        sections.push(`
            <div class="modal-section">
                <h3>完整报告</h3>
                <div class="statement-body">${ui.renderMarkdown(analysis.reportMarkdown || "暂无 AI 报告。")}</div>
            </div>
        `);

        return sections.join("");
    }

    function renderAnalysisPreviewLists(analysis) {
        const blocks = [];

        if (analysis.rootCauses && analysis.rootCauses.length) {
            blocks.push(renderPreviewList("核心问题", analysis.rootCauses, 2));
        }

        if (analysis.fixDirections && analysis.fixDirections.length) {
            blocks.push(renderPreviewList("修改方向", analysis.fixDirections, 2));
        }

        if (analysis.lineIssues && analysis.lineIssues.length) {
            const lineIssueBlock = renderLineIssuePreviewList(analysis.lineIssues, 3);
            if (lineIssueBlock) {
                blocks.push(lineIssueBlock);
            }
        }

        return blocks.join("");
    }

    function buildAnalysisModalContent(submission) {
        const analysis = submission.analysis;
        const sections = [
            `
                <div class="modal-stack">
                    <div class="summary-grid">
                        <div class="summary-card"><span>评测结果</span><strong>${ui.escapeHtml(ui.formatVerdict(submission.verdict))}</strong></div>
                        <div class="summary-card"><span>耗时 / 内存</span><strong>${ui.escapeHtml(formatPerf(submission))}</strong></div>
                        <div class="summary-card"><span>来源</span><strong>${ui.escapeHtml(analysis.sourceType || "RULE_BASED_V1")}</strong></div>
                        <div class="summary-card"><span>提交编号</span><strong>#${ui.escapeHtml(submission.id)}</strong></div>
                    </div>
                    <div class="compare-summary-card">
                        <h3>总览</h3>
                        <p>${ui.escapeHtml(analysis.summary || "AI 已生成完整报告。")}</p>
                    </div>
                </div>
            `
        ];

        if (analysis.rootCauses && analysis.rootCauses.length) {
            sections.push(renderModalListSection("问题定位", analysis.rootCauses));
        }

        if (analysis.fixDirections && analysis.fixDirections.length) {
            sections.push(renderModalListSection("修改建议", analysis.fixDirections));
        }

        if (analysis.lineIssues && analysis.lineIssues.length) {
            const lineIssueSection = renderLineIssueModalSection(analysis.lineIssues);
            if (lineIssueSection) {
                sections.push(lineIssueSection);
            }
        }

        if (analysis.firstFailedCase) {
            const failedCase = analysis.firstFailedCase;
            sections.push(`
                <div class="modal-section">
                    <h3>首个失败测试点</h3>
                    <div class="testcase-grid">
                        <div class="sample-card">
                            <span class="helper">输入</span>
                            <pre>${ui.escapeHtml(failedCase.input || "")}</pre>
                        </div>
                        <div class="sample-card">
                            <span class="helper">实际输出 / 标准输出</span>
                            <pre>Actual output:
${ui.escapeHtml(failedCase.actualOutput || "")}

Expected output:
${ui.escapeHtml(failedCase.expectedOutput || "")}</pre>
                        </div>
                    </div>
                </div>
            `);
        }

        sections.push(`
            <div class="modal-section">
                <h3>完整报告</h3>
                <div class="statement-body">${ui.renderMarkdown(analysis.reportMarkdown || "暂无 AI 报告。")}</div>
            </div>
        `);

        return sections.join("");
    }

    function watchAnalysis(submissionId, options = {}) {
        if (!submissionId || analysisPollers.has(submissionId)) {
            return;
        }

        const state = {
            attempts: 0,
            maxAttempts: 40,
            intervalMs: 2000,
            silent: Boolean(options.silent),
            initialDelayMs: Math.max(0, Number(options.initialDelayMs || 0))
        };

        analysisPollers.set(submissionId, state);
        logProblemFeature("info", "analysis", "Started analysis watcher.", {
            submissionId,
            intervalMs: state.intervalMs,
            maxAttempts: state.maxAttempts,
            initialDelayMs: state.initialDelayMs
        });
        if (state.initialDelayMs > 0) {
            window.setTimeout(() => {
                if (!analysisPollers.has(submissionId)) {
                    return;
                }
                pollAnalysis(submissionId, state);
            }, state.initialDelayMs);
            return;
        }

        pollAnalysis(submissionId, state);
    }

    async function pollAnalysis(submissionId, state) {
        try {
            const response = await fetch(`${API_BASE}/api/submissions/${submissionId}/analysis`);
            const result = await ui.readJson(response);

            if (response.ok && result.analysis) {
                applySubmissionAnalysis(submissionId, result.analysis);
                analysisPollers.delete(submissionId);
                logProblemFeature("info", "analysis", "Analysis poll completed.", { submissionId, attempts: state.attempts + 1 });
                if (!state.silent && latestSubmission && latestSubmission.id === submissionId) {
                    showPageAlert("AI 分析已更新。", "success");
                }
                return;
            }

            if (response.status !== 202) {
                throw new Error(result.error || "AI 分析加载失败");
            }
        } catch (error) {
            logProblemFeature("warn", "analysis", "Analysis poll attempt failed.", {
                submissionId,
                attempt: state.attempts + 1,
                error: error && error.message ? error.message : String(error)
            });
            if (!state.silent && latestSubmission && latestSubmission.id === submissionId) {
                console.warn(error);
            }
        }

        state.attempts += 1;
        if (state.attempts >= state.maxAttempts) {
            analysisPollers.delete(submissionId);
            logProblemFeature("warn", "analysis", "Stopped analysis watcher after max attempts.", { submissionId, maxAttempts: state.maxAttempts });
            return;
        }

        window.setTimeout(() => pollAnalysis(submissionId, state), state.intervalMs);
    }

    function applySubmissionAnalysis(submissionId, analysis) {
        logProblemFeature("info", "analysis", "Applying completed analysis payload.", {
            submissionId,
            lineIssueCount: Array.isArray(analysis && analysis.lineIssues) ? analysis.lineIssues.length : 0,
            sourceType: analysis && analysis.sourceType
        });
        const cached = submissionDetailCache.get(submissionId);
        if (cached) {
            cached.analysis = analysis;
            submissionDetailCache.set(submissionId, cached);
        }

        historySummaries = historySummaries.map(summary => summary.id === submissionId
            ? {
                ...summary,
                analysisStatus: "READY",
                analysisSourceType: analysis.sourceType,
                analysisHeadline: analysis.headline,
                analysisSummary: analysis.summary
            }
            : summary);

        const focusId = getOpenHistorySubmissionId() || submissionId;
        renderHistory(focusId);
        populateComparisonSelectors();

        if (latestSubmission && latestSubmission.id === submissionId) {
            latestSubmission = {
                ...(cached || latestSubmission),
                analysisStatus: "READY",
                analysis
            };
            submissionDetailCache.set(submissionId, latestSubmission);
            syncLatestSubmission(latestSubmission);
            const firstLineIssue = Array.isArray(analysis && analysis.lineIssues) ? analysis.lineIssues[0] : null;
            if (firstLineIssue && Number.isInteger(Number(firstLineIssue.lineNumber))) {
                window.requestAnimationFrame(() => {
                    jumpToCodeLine(Number(firstLineIssue.lineNumber), { silent: true });
                });
            }
        }

        if (activeModalKind === "analysis" && activeModalSubmissionId === submissionId) {
            openSubmissionAnalysisModal(submissionId);
        }
    }

    function getOpenHistorySubmissionId() {
        return activeModalKind === "history" ? activeModalSubmissionId : null;
    }
})();
