(function () {
    const ui = window.CodeJudgeUI;
    const state = {
        assignment: null,
        student: null,
        inviteCode: "",
        trajectory: null
    };

    document.addEventListener("DOMContentLoaded", () => {
        bindControls();
        const codeFromUrl = new URLSearchParams(window.location.search).get("code");
        if (codeFromUrl) {
            document.getElementById("invite-code").value = codeFromUrl;
            resolveInvite(codeFromUrl);
        }
    });

    function bindControls() {
        document.getElementById("invite-form").addEventListener("submit", event => {
            event.preventDefault();
            const code = document.getElementById("invite-code").value.trim();
            resolveInvite(code);
        });

        document.getElementById("identity-form").addEventListener("submit", event => {
            event.preventDefault();
            bindIdentity();
        });
        document.getElementById("identity-form").addEventListener("keydown", event => {
            if (event.key === "Enter") {
                event.preventDefault();
                bindIdentity();
            }
        });
        const identityButton = document.getElementById("confirm-identity-btn");
        if (identityButton) {
            identityButton.addEventListener("click", event => {
                event.preventDefault();
                bindIdentity();
            });
        }
    }

    async function resolveInvite(code) {
        if (!code) {
            showAlert("请输入老师提供的邀请码。", "error");
            return;
        }

        try {
            const response = await fetch("/api/invites/resolve", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ code })
            });
            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || "邀请码无效");
            }

            state.assignment = result;
            state.inviteCode = code.toUpperCase();
            renderAssignment(result);
            showAlert("已进入学习任务，请先确认身份。", "success");
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    async function bindIdentity() {
        if (!state.assignment) {
            showAlert("请先输入邀请码进入任务。", "error");
            return;
        }

        const payload = {
            assignmentId: state.assignment.id,
            classGroupId: state.assignment.classGroupId,
            className: document.getElementById("student-class-name").value.trim(),
            displayName: document.getElementById("student-name").value.trim(),
            studentNo: document.getElementById("student-no").value.trim()
        };

        if (!payload.displayName) {
            showAlert("请填写姓名。", "error");
            return;
        }

        try {
            const response = await fetch("/api/student/identity", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || "身份确认失败");
            }

            state.student = result;
            window.sessionStorage.setItem("wzai:student", JSON.stringify(result));
            window.sessionStorage.setItem(`wzai:student:${state.assignment.id}`, JSON.stringify(result));
            document.getElementById("identity-status").textContent = `${result.displayName}，身份已确认`;
            renderTasks();
            loadTrajectory();
            showAlert("身份已确认，可以开始学习任务。", "success");
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    function renderAssignment(assignment) {
        document.getElementById("assignment-section").hidden = false;
        document.getElementById("assignment-title").textContent = assignment.title || "学习任务";
        document.getElementById("assignment-description").textContent = assignment.description || "完成老师布置的任务，并根据智能反馈逐步修改。";
        document.getElementById("assignment-invite-pill").textContent = `邀请码 ${state.inviteCode || assignment.inviteCode || ""}`;
        if (assignment.className) {
            document.getElementById("student-class-name").value = assignment.className;
        }
        restoreStudentIdentity(assignment);
        renderTasks();
    }

    function restoreStudentIdentity(assignment) {
        const storageKey = `wzai:student:${assignment.id}`;
        const raw = window.sessionStorage.getItem(storageKey) || window.sessionStorage.getItem("wzai:student");
        if (!raw) {
            return;
        }
        try {
            const student = JSON.parse(raw);
            if (!student || !student.id) {
                return;
            }
            state.student = student;
            document.getElementById("student-name").value = student.displayName || "";
            document.getElementById("student-no").value = student.studentNo || "";
            document.getElementById("identity-status").textContent = `${student.displayName || "学生"}，身份已确认`;
            loadTrajectory();
        } catch (error) {
            window.sessionStorage.removeItem(storageKey);
        }
    }

    function renderTasks() {
        const container = document.getElementById("assignment-task-list");
        const tasks = state.assignment && Array.isArray(state.assignment.tasks) ? state.assignment.tasks : [];
        if (!tasks.length) {
            container.innerHTML = `
                <div class="empty-card">
                    <h3>暂无任务</h3>
                    <p>该邀请码暂未绑定学习任务，请联系老师。</p>
                </div>
            `;
            return;
        }

        const identityQuery = state.student
            ? `&assignmentId=${state.assignment.id}&studentProfileId=${state.student.id}`
            : `&assignmentId=${state.assignment.id}`;
        container.innerHTML = tasks.map((task, index) => `
            <article class="learning-task-card">
                <div>
                    <span class="source-pill">任务 ${index + 1}</span>
                    <h3>${ui.escapeHtml(task.title || "学习任务")}</h3>
                    <p>${formatDifficulty(task.difficulty)} · Python / C++ · 分层提示 ${ui.escapeHtml(state.assignment.hintPolicy || "L2")}</p>
                </div>
                <a class="btn btn-primary ${state.student ? "" : "is-disabled"}"
                   href="${state.student ? `/problem.html?id=${task.problemId}${identityQuery}` : "#"}"
                   aria-disabled="${state.student ? "false" : "true"}">开始练习</a>
            </article>
        `).join("");
    }

    function formatDifficulty(difficulty) {
        return ui.formatDifficulty ? ui.formatDifficulty(difficulty) : (difficulty || "-");
    }

    async function loadTrajectory() {
        if (!state.assignment || !state.student) {
            return;
        }
        try {
            const response = await fetch(`/api/student/assignments/${state.assignment.id}/profile/${state.student.id}/trajectory`);
            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || "无法加载学习轨迹");
            }
            state.trajectory = result;
            renderTrajectory(result);
        } catch (error) {
            showAlert(error.message, "error");
        }
    }

    function renderTrajectory(trajectory) {
        document.getElementById("trajectory-section").hidden = false;
        document.getElementById("trajectory-stage").textContent = trajectory.stageTransition || "继续观察";
        document.getElementById("trajectory-completion").textContent = `${trajectory.completedTasks || 0}/${trajectory.totalTasks || 0}`;
        document.getElementById("trajectory-attempts").textContent = trajectory.totalAttempts || 0;
        document.getElementById("trajectory-repeated").textContent = trajectory.repeatedIssueTag
            ? `${formatIssueTag(trajectory.repeatedIssueTag)} ${trajectory.repeatedIssueCount || 0} 次`
            : "暂无";
        document.getElementById("trajectory-next").textContent = trajectory.nextStep || "完成一次提交后查看建议";
        renderTrajectoryIssues(trajectory.recentIssueDistribution || []);
        renderTrajectoryTasks(trajectory.tasks || []);
    }

    function renderTrajectoryIssues(issues) {
        const container = document.getElementById("trajectory-issues");
        if (!issues.length) {
            container.innerHTML = `<p class="helper">完成提交后，这里会显示最近 10 次提交的错因分布。</p>`;
            return;
        }
        container.innerHTML = issues.map(issue => `
            <div class="issue-row">
                <span>${ui.escapeHtml(formatIssueTag(issue.label || ""))}</span>
                <strong>${issue.count || 0}</strong>
            </div>
        `).join("");
    }

    function renderTrajectoryTasks(tasks) {
        const container = document.getElementById("trajectory-tasks");
        if (!tasks.length) {
            container.innerHTML = `<p class="helper">暂无任务轨迹。</p>`;
            return;
        }
        container.innerHTML = tasks.map(task => `
            <article class="student-progress-card ${task.passed ? "" : "needs-attention"}">
                <div>
                    <strong>${ui.escapeHtml(task.title || "学习任务")}</strong>
                    <span>${task.attemptCount || 0} 次尝试 · ${ui.escapeHtml(formatVerdict(task.latestVerdict))}</span>
                </div>
                <p>${ui.escapeHtml(task.latestProgressSignal || "等待提交")}</p>
                ${task.latestHint ? `<small>${ui.escapeHtml(task.latestHint)}</small>` : ""}
            </article>
        `).join("");
    }

    function formatVerdict(value) {
        const map = {
            ACCEPTED: "已通过",
            WRONG_ANSWER: "答案不一致",
            TIME_LIMIT_EXCEEDED: "超时",
            MEMORY_LIMIT_EXCEEDED: "超内存",
            RUNTIME_ERROR: "运行错误",
            COMPILATION_ERROR: "语法/编译错误",
            INTERNAL_ERROR: "系统环境问题",
            PENDING: "评测中"
        };
        return map[String(value || "").toUpperCase()] || value || "暂无提交";
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
        return labels[String(tag || "").toUpperCase()] || tag || "暂无";
    }

    function showAlert(message, type) {
        document.getElementById("student-alert").innerHTML = `<div class="alert ${type}">${ui.escapeHtml(message)}</div>`;
    }
})();
