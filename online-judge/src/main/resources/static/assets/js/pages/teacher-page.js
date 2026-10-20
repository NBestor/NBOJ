(function () {
    const ui = window.CodeJudgeUI;
    const state = {
        assignments: [],
        classes: [],
        problems: [],
        activeAssignmentId: null,
        classImportContent: "",
        problemImportContent: "",
        classPreview: null,
        problemPreview: null,
        executorStatus: null
    };

    document.addEventListener("DOMContentLoaded", () => {
        bindControls();
        refreshAll();
    });

    function bindControls() {
        document.getElementById("refresh-teacher-btn").addEventListener("click", refreshAll);
        document.getElementById("create-class-btn").addEventListener("click", createClassGroup);
        document.getElementById("assignment-form").addEventListener("submit", saveAssignment);
        document.getElementById("rotate-invite-btn").addEventListener("click", rotateActiveInvite);
        document.getElementById("class-import-preview-btn").addEventListener("click", previewClassImport);
        document.getElementById("class-import-commit-btn").addEventListener("click", commitClassImport);
        document.getElementById("problem-import-preview-btn").addEventListener("click", previewProblemImport);
        document.getElementById("problem-import-commit-btn").addEventListener("click", commitProblemImport);
        document.getElementById("class-import-file").addEventListener("change", event => readFile(event, "class"));
        document.getElementById("problem-import-file").addEventListener("change", event => readFile(event, "problem"));
    }

    async function refreshAll() {
        try {
            const [assignments, classes, problems, executorStatus] = await Promise.all([
                fetchJson("/api/teacher/assignments"),
                fetchJson("/api/teacher/classes"),
                fetchJson("/api/problems/catalog"),
                fetchJson("/api/system/executor-status")
            ]);
            state.assignments = Array.isArray(assignments) ? assignments : [];
            state.classes = Array.isArray(classes) ? classes : [];
            state.problems = Array.isArray(problems) ? problems : [];
            state.executorStatus = executorStatus || {};
            renderExecutorStatus();
            renderClasses();
            renderProblemOptions();
            renderAssignments();
            if (state.assignments.length) {
                const target = state.activeAssignmentId || state.assignments[0].id;
                loadOverview(target);
                populateAssignmentForm(state.assignments.find(item => item.id === target) || state.assignments[0]);
            }
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    async function fetchJson(url, options = {}) {
        const response = await fetch(url, options);
        const result = await ui.readJson(response);
        if (!response.ok) {
            throw new Error(result.error || "请求失败");
        }
        return result;
    }

    function renderExecutorStatus() {
        const status = state.executorStatus || {};
        const ready = status.cppAvailable && status.pythonAvailable;
        const pill = document.getElementById("executor-status-pill");
        pill.textContent = ready ? "Python / C++ 就绪" : "C++ 沙箱未就绪";
        pill.className = `source-pill ${ready ? "status-ok" : "status-warn"}`;
        document.getElementById("executor-status-detail").textContent = status.message || "正在检测执行环境";
    }

    function renderClasses() {
        const select = document.getElementById("assignment-class");
        select.innerHTML = `<option value="">不绑定班级</option>` + state.classes.map(item => `
            <option value="${item.id}">${ui.escapeHtml(item.name || "未命名班级")}</option>
        `).join("");
        document.getElementById("teacher-class-count").textContent = state.classes.length;
    }

    function renderProblemOptions() {
        const container = document.getElementById("assignment-problems");
        if (!state.problems.length) {
            container.innerHTML = `<p class="helper">暂无题目，请先在下方导入或到任务编辑器创建。</p>`;
            return;
        }
        container.innerHTML = state.problems.map(problem => `
            <label class="choice-row">
                <input type="checkbox" value="${problem.id}">
                <span>
                    <strong>${ui.escapeHtml(problem.title || "学习任务")}</strong>
                    <small>${formatDifficulty(problem.difficulty)} · ${problem.timeLimit || 1000}ms</small>
                </span>
            </label>
        `).join("");
    }

    function renderAssignments() {
        document.getElementById("teacher-assignment-count").textContent = state.assignments.length;
        const container = document.getElementById("assignment-list");
        if (!state.assignments.length) {
            container.innerHTML = `
                <div class="empty-card">
                    <h3>暂无作业</h3>
                    <p>请在右侧创建第一份学习作业。</p>
                </div>
            `;
            return;
        }

        container.innerHTML = state.assignments.map(assignment => `
            <button type="button" class="teacher-assignment-item ${assignment.id === state.activeAssignmentId ? "active" : ""}" data-assignment-id="${assignment.id}">
                <div>
                    <strong>${ui.escapeHtml(assignment.title || "学习作业")}</strong>
                    <span>${ui.escapeHtml(assignment.className || "未绑定班级")} · ${assignment.tasks ? assignment.tasks.length : 0} 个任务 · ${formatHintPolicy(assignment.hintPolicy)}</span>
                </div>
                <code>${ui.escapeHtml(assignment.inviteCode || "-")}</code>
            </button>
        `).join("");

        container.querySelectorAll("[data-assignment-id]").forEach(button => {
            button.addEventListener("click", () => {
                const id = Number(button.dataset.assignmentId);
                const assignment = state.assignments.find(item => item.id === id);
                populateAssignmentForm(assignment);
                loadOverview(id);
            });
        });
    }

    async function loadOverview(assignmentId) {
        state.activeAssignmentId = assignmentId;
        renderAssignments();
        try {
            const result = await fetchJson(`/api/teacher/assignments/${assignmentId}/overview`);
            renderOverview(result);
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    function populateAssignmentForm(assignment) {
        if (!assignment) {
            return;
        }
        document.getElementById("assignment-id").value = assignment.id || "";
        document.getElementById("assignment-title-input").value = assignment.title || "";
        document.getElementById("assignment-description-input").value = assignment.description || "";
        document.getElementById("assignment-class").value = assignment.classGroupId || "";
        document.getElementById("assignment-hint-policy").value = assignment.hintPolicy || "L2";
        document.getElementById("assignment-status").value = assignment.status || "ACTIVE";
        const selected = new Set((assignment.tasks || []).map(task => String(task.problemId)));
        document.querySelectorAll("#assignment-problems input[type='checkbox']").forEach(input => {
            input.checked = selected.has(input.value);
        });
        document.getElementById("active-invite-code").textContent = assignment.inviteCode || "-";
    }

    async function saveAssignment(event) {
        event.preventDefault();
        const id = document.getElementById("assignment-id").value;
        const problemIds = Array.from(document.querySelectorAll("#assignment-problems input[type='checkbox']:checked"))
            .map(input => Number(input.value));
        if (!problemIds.length) {
            showAlert("请至少选择一个学习任务。", "error");
            return;
        }
        const payload = {
            title: document.getElementById("assignment-title-input").value.trim(),
            description: document.getElementById("assignment-description-input").value.trim(),
            classGroupId: document.getElementById("assignment-class").value ? Number(document.getElementById("assignment-class").value) : null,
            hintPolicy: document.getElementById("assignment-hint-policy").value,
            status: document.getElementById("assignment-status").value,
            problemIds
        };
        try {
            const assignment = await fetchJson(id ? `/api/teacher/assignments/${id}` : "/api/teacher/assignments", {
                method: id ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            state.activeAssignmentId = assignment.id;
            showAlert("学习作业已保存。", "success");
            await refreshAll();
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    async function rotateActiveInvite() {
        const id = document.getElementById("assignment-id").value || state.activeAssignmentId;
        if (!id) {
            showAlert("请先选择或保存一个作业。", "error");
            return;
        }
        try {
            const assignment = await fetchJson(`/api/teacher/assignments/${id}/invite`, { method: "POST" });
            state.activeAssignmentId = assignment.id;
            document.getElementById("active-invite-code").textContent = assignment.inviteCode || "-";
            showAlert("邀请码已更新。", "success");
            await refreshAll();
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    async function createClassGroup() {
        const name = document.getElementById("new-class-name").value.trim();
        if (!name) {
            showAlert("请输入班级名称。", "error");
            return;
        }
        try {
            await fetchJson("/api/teacher/classes", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    name,
                    grade: document.getElementById("new-class-grade").value.trim(),
                    teacherName: document.getElementById("new-class-teacher").value.trim()
                })
            });
            document.getElementById("new-class-name").value = "";
            document.getElementById("new-class-grade").value = "";
            document.getElementById("new-class-teacher").value = "";
            showAlert("班级已创建。", "success");
            await refreshAll();
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    async function previewClassImport() {
        await runImport("class", "/api/teacher/classes/import-preview", renderClassPreview);
    }

    async function commitClassImport() {
        await runImport("class", "/api/teacher/classes/import-commit", result => {
            renderCommitResult("class-import-result", result);
            state.classPreview = null;
            refreshAll();
        });
    }

    async function previewProblemImport() {
        await runImport("problem", "/api/teacher/problems/import-preview", renderProblemPreview);
    }

    async function commitProblemImport() {
        await runImport("problem", "/api/teacher/problems/import-commit", result => {
            renderCommitResult("problem-import-result", result);
            state.problemPreview = null;
            refreshAll();
        });
    }

    async function runImport(kind, url, renderer) {
        const textAreaId = kind === "class" ? "class-import-text" : "problem-import-text";
        const formatId = kind === "class" ? "class-import-format" : "problem-import-format";
        const content = kind === "class"
            ? (state.classImportContent || document.getElementById(textAreaId).value)
            : (state.problemImportContent || document.getElementById(textAreaId).value);
        if (!content.trim()) {
            showAlert("请先粘贴内容或选择文件。", "error");
            return;
        }
        try {
            const result = await fetchJson(url, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    format: document.getElementById(formatId).value,
                    content,
                    classGroupId: document.getElementById("assignment-class").value ? Number(document.getElementById("assignment-class").value) : null,
                    className: document.getElementById("new-class-name").value.trim()
                })
            });
            renderer(result);
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    function readFile(event, kind) {
        const file = event.target.files && event.target.files[0];
        if (!file) {
            return;
        }
        const reader = new FileReader();
        reader.onload = () => {
            const result = String(reader.result || "");
            if (kind === "class") {
                state.classImportContent = result;
                document.getElementById("class-import-text").value = file.name;
                document.getElementById("class-import-format").value = inferFormat(file.name);
            } else {
                state.problemImportContent = result;
                document.getElementById("problem-import-text").value = file.name;
                document.getElementById("problem-import-format").value = inferFormat(file.name);
            }
        };
        reader.readAsDataURL(file);
    }

    function renderClassPreview(result) {
        state.classPreview = result;
        const container = document.getElementById("class-import-result");
        container.innerHTML = `
            <div class="import-result-card">
                <strong>${ui.escapeHtml(result.message || "解析完成")}</strong>
                <p>${result.validRows || 0} 行有效 · ${result.invalidRows || 0} 行错误 · ${result.duplicateRows || 0} 行重复</p>
                ${renderIssues(result.issues || [])}
                ${renderStudentRows((result.students || []).slice(0, 8))}
            </div>
        `;
    }

    function renderProblemPreview(result) {
        state.problemPreview = result;
        const container = document.getElementById("problem-import-result");
        container.innerHTML = `
            <div class="import-result-card">
                <strong>${ui.escapeHtml(result.message || "解析完成")}</strong>
                <p>${result.validRows || 0} 题有效 · ${result.invalidRows || 0} 题错误 · ${result.duplicateRows || 0} 题重复</p>
                ${renderIssues(result.issues || [])}
                ${renderProblemRows((result.problems || []).slice(0, 6))}
            </div>
        `;
    }

    function renderCommitResult(containerId, result) {
        document.getElementById(containerId).innerHTML = `
            <div class="import-result-card">
                <strong>${ui.escapeHtml(result.message || "导入完成")}</strong>
                <p>新增 ${result.createdCount || 0} · 跳过 ${result.skippedCount || 0} · 失败 ${result.failedCount || 0}</p>
                ${renderIssues(result.issues || [])}
            </div>
        `;
    }

    function renderIssues(issues) {
        if (!issues.length) {
            return "";
        }
        return `<ul class="import-issue-list">${issues.slice(0, 6).map(issue => `
            <li>${issue.rowNumber || "-"} 行 · ${ui.escapeHtml(issue.message || "")}</li>
        `).join("")}</ul>`;
    }

    function renderStudentRows(rows) {
        if (!rows.length) {
            return "";
        }
        return `<div class="preview-table">${rows.map(row => `
            <span>${row.rowNumber}</span>
            <strong>${ui.escapeHtml(row.displayName || "-")}</strong>
            <em>${ui.escapeHtml(row.className || "-")} · ${ui.escapeHtml(row.studentNo || "无学号")}</em>
        `).join("")}</div>`;
    }

    function renderProblemRows(rows) {
        if (!rows.length) {
            return "";
        }
        return `<div class="preview-table">${rows.map(row => `
            <span>${row.rowNumber}</span>
            <strong>${ui.escapeHtml(row.title || "-")}</strong>
            <em>${ui.escapeHtml(formatDifficulty(row.difficulty))} · ${row.visibleTestCaseCount || 0} 个可见测试点</em>
        `).join("")}</div>`;
    }

    function renderOverview(overview) {
        const assignment = overview.assignment || {};
        document.getElementById("overview-title").textContent = assignment.title || "班级诊断概览";
        document.getElementById("overview-subtitle").textContent = `${assignment.className || "试点班级"} · 邀请码 ${assignment.inviteCode || "-"}`;
        document.getElementById("overview-participants").textContent = overview.participantCount || 0;
        document.getElementById("overview-attempts").textContent = overview.attemptCount || 0;
        document.getElementById("overview-passed").textContent = overview.passedAttemptCount || 0;
        document.getElementById("overview-attention").textContent = overview.strugglingStudentCount || 0;
        document.getElementById("teacher-participant-count").textContent = overview.participantCount || 0;
        document.getElementById("teacher-attempt-count").textContent = overview.attemptCount || 0;
        document.getElementById("teacher-attention-count").textContent = overview.strugglingStudentCount || 0;
        renderIssueList(overview.topIssues || []);
        renderStudents(overview.students || []);
    }

    function renderIssueList(issues) {
        const container = document.getElementById("issue-list");
        if (!issues.length) {
            container.innerHTML = `<p class="helper">暂无足够诊断数据。学生提交后这里会出现高频问题。</p>`;
            return;
        }
        container.innerHTML = issues.map(issue => `
            <div class="issue-row">
                <span>${ui.escapeHtml(formatIssueTag(issue.label || "未分类问题"))}</span>
                <strong>${issue.count || 0}</strong>
            </div>
        `).join("");
    }

    function renderStudents(students) {
        const container = document.getElementById("student-progress-list");
        if (!students.length) {
            container.innerHTML = `<p class="helper">暂无学生提交。学生通过邀请码进入并提交后，这里会自动更新。</p>`;
            return;
        }
        container.innerHTML = students.map(student => `
            <article class="student-progress-card ${student.needsAttention ? "needs-attention" : ""}">
                <div>
                    <strong>${ui.escapeHtml(student.displayName || "未知学生")}</strong>
                    <span>${ui.escapeHtml(student.studentNo || "未填学号")} · ${student.attemptCount || 0} 次尝试 · ${student.passedCount || 0} 次通过</span>
                </div>
                <p>${ui.escapeHtml(student.latestProgressSignal || "等待更多提交")}</p>
                ${student.repeatedIssueTag ? `<em>${ui.escapeHtml(formatIssueTag(student.repeatedIssueTag))} 连续 ${student.repeatedIssueCount || 0} 次</em>` : ""}
                <small>${ui.escapeHtml(student.latestIssue || "暂无诊断")}</small>
            </article>
        `).join("");
    }

    function inferFormat(fileName) {
        const lower = String(fileName || "").toLowerCase();
        if (lower.endsWith(".xlsx")) return "xlsx";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "markdown";
        return "csv";
    }

    function showAlert(message, type) {
        document.getElementById("teacher-alert").innerHTML = `<div class="alert ${type}">${ui.escapeHtml(message)}</div>`;
    }

    function formatDifficulty(value) {
        const map = { EASY: "基础", MEDIUM: "提高", HARD: "挑战" };
        return map[String(value || "").toUpperCase()] || value || "基础";
    }

    function formatHintPolicy(value) {
        const map = { L1: "只给问题类型", L2: "给定位方向", L3: "给局部解释", L4: "允许参考改法" };
        return map[value] || "给定位方向";
    }

    function formatIssueTag(tag) {
        const labels = {
            SYNTAX_ERROR: "语法错误",
            IO_FORMAT: "输入输出格式",
            BOUNDARY_CONDITION: "边界条件",
            CONDITION_BRANCH: "条件分支",
            LOOP_BOUNDARY: "循环边界",
            DATA_STRUCTURE_CHOICE: "数据结构选择",
            TIME_COMPLEXITY: "时间复杂度",
            SPACE_COMPLEXITY: "空间复杂度",
            VARIABLE_INITIALIZATION: "变量初始化",
            STATE_TRANSITION: "状态转移",
            RECURSION_EXIT: "递归出口",
            CODE_READABILITY: "代码可读性",
            CODE_QUALITY: "代码质量",
            SAMPLE_ONLY: "只通过样例",
            GENERALIZATION_CHECK: "泛化检查",
            ALGORITHM_STRATEGY: "算法策略",
            RUNTIME_STABILITY: "运行稳定性",
            NEEDS_MORE_EVIDENCE: "证据不足"
        };
        return labels[String(tag || "").toUpperCase()] || tag;
    }
})();
