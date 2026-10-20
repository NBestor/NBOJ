(function () {
    const API_BASE = "";
    const CACHE_KEY = "oj:problem-catalog:v1";
    const SNAPSHOT_KEY = "oj:problem-catalog:snapshot:v1";
    const ui = window.CodeJudgeUI;
    const state = {
        problems: [],
        search: "",
        difficulty: "ALL",
        sort: "recent",
        showingCache: false
    };

    document.addEventListener("DOMContentLoaded", () => {
        bindControls();
        restoreSnapshot();
        hydrateCatalogFromCache();
        loadCatalog();
    });

    function bindControls() {
        const applySearch = ui.debounce(value => {
            state.search = ui.normalizeKeyword(value);
            render();
        }, 120);

        document.getElementById("problem-search").addEventListener("input", event => {
            applySearch(event.target.value);
        });

        document.getElementById("problem-sort").addEventListener("change", event => {
            state.sort = event.target.value;
            render();
        });

        document.getElementById("difficulty-filter").addEventListener("click", event => {
            const button = event.target.closest("[data-filter]");
            if (!button) {
                return;
            }

            state.difficulty = button.dataset.filter;
            document.querySelectorAll("#difficulty-filter .chip").forEach(chip => {
                chip.classList.toggle("active", chip === button);
            });
            render();
        });
    }

    function hydrateCatalogFromCache() {
        const cached = ui.readCache(CACHE_KEY, 5 * 60 * 1000);
        if (!cached || !Array.isArray(cached.value) || !cached.value.length) {
            return;
        }

        applyCatalog(cached.value, { showingCache: true });
        document.getElementById("problem-summary").textContent = "已优先展示本地缓存，正在同步最新学习任务...";
    }

    function restoreSnapshot() {
        const cached = ui.readCache(SNAPSHOT_KEY, 5 * 60 * 1000);
        if (!cached || !cached.value || cached.expired) {
            return;
        }

        const snapshot = cached.value;
        syncStateFromSnapshot(snapshot);
        if (window.__OJ_HOME_SNAPSHOT_APPLIED__ === true) {
            return;
        }

        applySnapshot(snapshot);
    }

    async function loadCatalog() {
        try {
            const response = await fetch(`${API_BASE}/api/problems/catalog`);
            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || "无法获取学习任务数据");
            }

            ui.writeCache(CACHE_KEY, result);
            applyCatalog(result, { showingCache: false });
        } catch (error) {
            console.error("加载学习任务失败:", error);
            if (!state.problems.length) {
                document.getElementById("problem-list").innerHTML = `
                    <div class="empty-card">
                        <h3>学习任务加载失败</h3>
                        <p>${ui.escapeHtml(error.message)}</p>
                    </div>
                `;
            }

            document.getElementById("problem-summary").textContent = state.problems.length
                ? "已展示缓存内容，最新数据同步失败。"
                : "当前无法展示学习任务数据。";
        }
    }

    function applyCatalog(items, options) {
        state.showingCache = Boolean(options && options.showingCache);
        state.problems = (Array.isArray(items) ? items : []).map(problem => ({
            ...problem,
            summary: problem.summary || "",
            searchText: ui.normalizeKeyword(`${problem.title || ""} ${problem.summary || ""}`)
        }));
        render();
    }

    function render() {
        const filtered = getFilteredProblems();
        ui.schedule(() => {
            updateHero(filtered);
            updateStats(filtered);
            renderProblems(filtered);
            saveSnapshot();
        });
    }

    function getFilteredProblems() {
        return state.problems
            .filter(problem => {
                const matchesDifficulty = state.difficulty === "ALL" || problem.difficulty === state.difficulty;
                const matchesSearch = !state.search || problem.searchText.includes(state.search);
                return matchesDifficulty && matchesSearch;
            })
            .sort((left, right) => sortProblems(left, right, state.sort));
    }

    function renderProblems(problems) {
        const container = document.getElementById("problem-list");

        if (!problems.length) {
            container.innerHTML = `
                <div class="empty-card">
                    <h3>没有匹配的学习任务</h3>
                    <p>可以换一个关键词，或者调整筛选条件后继续查看。</p>
                </div>
            `;
            document.getElementById("problem-summary").textContent = "当前筛选条件下没有结果。";
            return;
        }

        container.innerHTML = problems.map(problem => `
            <a href="/problem.html?id=${problem.id}" class="problem-item">
                <div class="item-main">
                    <div class="item-mark" aria-hidden="true">&lt;/&gt;</div>
                    <div class="item-copy">
                        <div class="meta-row">
                            <span class="source-pill">#${problem.id}</span>
                            <span class="difficulty-pill ${String(problem.difficulty || "").toLowerCase()}">${ui.formatDifficulty(problem.difficulty)}</span>
                        </div>
                        <div class="item-title">${ui.escapeHtml(problem.title || "未命名学习任务")}</div>
                        <div class="item-note">${ui.escapeHtml(problem.summary || "查看学习任务详情")}</div>
                        <div class="meta-row">
                            <span class="chip">发布时间：${ui.formatDate(problem.createdAt)}</span>
                            <span class="chip">时限：${problem.timeLimit} ms</span>
                            <span class="chip">空间：${Math.round(Number(problem.memoryLimit || 0) / 1024)} MB</span>
                        </div>
                    </div>
                </div>
                <div class="item-side">
                    <span class="chip">${ui.formatDifficulty(problem.difficulty)}</span>
                    <span class="btn btn-ghost">进入任务</span>
                </div>
            </a>
        `).join("");

        document.getElementById("problem-summary").textContent = state.showingCache
            ? `共 ${state.problems.length} 个学习任务，当前显示 ${problems.length} 个。正在后台刷新最新数据...`
            : `共 ${state.problems.length} 个学习任务，当前显示 ${problems.length} 个。`;
    }

    function applySnapshot(snapshot) {
        if (!snapshot || typeof snapshot !== "object") {
            return;
        }

        setText("problem-summary", snapshot.summaryText);
        setText("hero-problem-count", snapshot.heroProblemCount);
        setText("hero-easy-rate", snapshot.heroEasyRate);
        setText("hero-time-median", snapshot.heroTimeMedian);
        setText("hero-latest-time", snapshot.heroLatestTime);
        setText("stat-total", snapshot.statTotal);
        setText("stat-easy", snapshot.statEasy);
        setText("stat-medium", snapshot.statMedium);
        setText("stat-hard", snapshot.statHard);

        const problemList = document.getElementById("problem-list");
        if (problemList && typeof snapshot.problemListHtml === "string" && snapshot.problemListHtml.trim()) {
            problemList.innerHTML = snapshot.problemListHtml;
        }
    }

    function syncStateFromSnapshot(snapshot) {
        if (!snapshot || typeof snapshot !== "object") {
            return;
        }

        const searchField = document.getElementById("problem-search");
        if (searchField && typeof snapshot.search === "string") {
            searchField.value = snapshot.search;
            state.search = ui.normalizeKeyword(snapshot.search);
        }

        const sortField = document.getElementById("problem-sort");
        if (sortField && typeof snapshot.sort === "string") {
            sortField.value = snapshot.sort;
            state.sort = snapshot.sort;
        }

        if (typeof snapshot.difficulty === "string") {
            state.difficulty = snapshot.difficulty;
            document.querySelectorAll("#difficulty-filter .chip").forEach(chip => {
                chip.classList.toggle("active", chip.dataset.filter === snapshot.difficulty);
            });
        }
    }

    function saveSnapshot() {
        const problemList = document.getElementById("problem-list");
        if (!problemList) {
            return;
        }

        ui.writeCache(SNAPSHOT_KEY, {
            search: document.getElementById("problem-search").value || "",
            difficulty: state.difficulty,
            sort: state.sort,
            summaryText: document.getElementById("problem-summary").textContent || "",
            problemListHtml: problemList.innerHTML,
            heroProblemCount: getOptionalText("hero-problem-count"),
            heroEasyRate: getOptionalText("hero-easy-rate"),
            heroTimeMedian: getOptionalText("hero-time-median"),
            heroLatestTime: getOptionalText("hero-latest-time"),
            statTotal: getOptionalText("stat-total"),
            statEasy: getOptionalText("stat-easy"),
            statMedium: getOptionalText("stat-medium"),
            statHard: getOptionalText("stat-hard")
        });
    }

    function setText(id, value) {
        if (typeof value !== "string") {
            return;
        }

        const element = document.getElementById(id);
        if (element) {
            element.textContent = value;
        }
    }

    function updateStats(problems) {
        setOptionalText("stat-total", problems.length);
        setOptionalText("stat-easy", problems.filter(problem => problem.difficulty === "EASY").length);
        setOptionalText("stat-medium", problems.filter(problem => problem.difficulty === "MEDIUM").length);
        setOptionalText("stat-hard", problems.filter(problem => problem.difficulty === "HARD").length);
    }

    function updateHero(problems) {
        setOptionalText("hero-problem-count", `${problems.length} 个`);
        setOptionalText("hero-easy-rate", problems.length
            ? `${((problems.filter(problem => problem.difficulty === "EASY").length / problems.length) * 100).toFixed(0)}%`
            : "0%");
        setOptionalText("hero-time-median", problems.length
            ? `${getMedian(problems.map(problem => Number(problem.timeLimit || 0)))} ms`
            : "-");
        setOptionalText("hero-latest-time", problems.length
            ? ui.formatDate([...problems].sort((left, right) => new Date(right.createdAt || 0) - new Date(left.createdAt || 0))[0].createdAt)
            : "暂无");
    }

    function sortProblems(left, right, sortKey) {
        if (sortKey === "id") {
            return left.id - right.id;
        }

        if (sortKey === "difficulty") {
            return difficultyWeight(left.difficulty) - difficultyWeight(right.difficulty) || left.id - right.id;
        }

        if (sortKey === "timeLimit") {
            return Number(left.timeLimit || 0) - Number(right.timeLimit || 0) || left.id - right.id;
        }

        return new Date(right.createdAt || 0) - new Date(left.createdAt || 0) || right.id - left.id;
    }

    function setOptionalText(id, value) {
        const element = document.getElementById(id);
        if (element) {
            element.textContent = String(value);
        }
    }

    function getOptionalText(id) {
        const element = document.getElementById(id);
        return element ? element.textContent || "" : "";
    }

    function difficultyWeight(difficulty) {
        const weights = { EASY: 1, MEDIUM: 2, HARD: 3 };
        return weights[difficulty] || 99;
    }

    function getMedian(values) {
        const sorted = [...values].sort((left, right) => left - right);
        const mid = Math.floor(sorted.length / 2);
        return sorted.length % 2 ? sorted[mid] : Math.round((sorted[mid - 1] + sorted[mid]) / 2);
    }
})();
