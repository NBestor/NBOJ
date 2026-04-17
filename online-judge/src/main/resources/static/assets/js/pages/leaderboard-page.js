(function () {
    const API_BASE = "";
    const CACHE_KEY = "oj:leaderboard:v1";
    const ui = window.CodeJudgeUI;
    const state = {
        entries: [],
        search: "",
        difficulty: "ALL",
        sort: "rank",
        showingCache: false
    };

    document.addEventListener("DOMContentLoaded", () => {
        bindControls();
        hydrateLeaderboardFromCache();
        loadLeaderboard();
    });

    function bindControls() {
        const applySearch = ui.debounce(value => {
            state.search = ui.normalizeKeyword(value);
            render();
        }, 120);

        document.getElementById("leaderboard-search").addEventListener("input", event => {
            applySearch(event.target.value);
        });

        document.getElementById("leaderboard-sort").addEventListener("change", event => {
            state.sort = event.target.value;
            render();
        });

        document.getElementById("leaderboard-filter").addEventListener("click", event => {
            const button = event.target.closest("[data-filter]");
            if (!button) {
                return;
            }

            state.difficulty = button.dataset.filter;
            document.querySelectorAll("#leaderboard-filter .chip").forEach(chip => {
                chip.classList.toggle("active", chip === button);
            });
            render();
        });
    }

    function hydrateLeaderboardFromCache() {
        const cached = ui.readCache(CACHE_KEY, 5 * 60 * 1000);
        if (!cached || !Array.isArray(cached.value) || !cached.value.length) {
            return;
        }

        applyEntries(cached.value, { showingCache: true });
        document.getElementById("leaderboard-summary").textContent = "已优先展示本地缓存，正在同步最新排行榜...";
    }

    async function loadLeaderboard() {
        try {
            const response = await fetch(`${API_BASE}/api/leaderboard/problems`);
            const result = await ui.readJson(response);
            if (!response.ok) {
                throw new Error(result.error || "无法获取排行榜数据");
            }

            ui.writeCache(CACHE_KEY, result);
            applyEntries(result, { showingCache: false });
        } catch (error) {
            console.error("加载排行榜失败:", error);
            if (!state.entries.length) {
                document.getElementById("leaderboard-list").innerHTML = `
                    <div class="empty-card">
                        <h3>排行榜加载失败</h3>
                        <p>${ui.escapeHtml(error.message)}</p>
                    </div>
                `;
            }

            document.getElementById("leaderboard-summary").textContent = state.entries.length
                ? "已展示缓存内容，最新排行榜同步失败。"
                : "当前无法展示排行榜数据。";
        }
    }

    function applyEntries(items, options) {
        state.showingCache = Boolean(options && options.showingCache);
        state.entries = (Array.isArray(items) ? items : []).map(entry => ({
            ...entry,
            searchText: ui.normalizeKeyword(entry.problemTitle || "")
        }));
        render();
    }

    function render() {
        const filtered = getFilteredEntries();
        ui.schedule(() => {
            updateHero(filtered);
            renderEntries(filtered);
        });
    }

    function getFilteredEntries() {
        return state.entries
            .filter(entry => {
                const matchesDifficulty = state.difficulty === "ALL" || entry.difficulty === state.difficulty;
                const matchesSearch = !state.search || entry.searchText.includes(state.search);
                return matchesDifficulty && matchesSearch;
            })
            .sort((left, right) => sortEntries(left, right, state.sort));
    }

    function renderEntries(entries) {
        const container = document.getElementById("leaderboard-list");

        if (!entries.length) {
            container.innerHTML = `
                <div class="empty-card">
                    <h3>没有匹配的榜单记录</h3>
                    <p>可以调整关键词、难度筛选或排序方式后继续查看。</p>
                </div>
            `;
            document.getElementById("leaderboard-summary").textContent = "当前筛选条件下没有结果。";
            return;
        }

        container.innerHTML = entries.map((entry, index) => `
            <article class="ranking-item ${index < 3 ? "top" : ""}">
                <div class="rank-mark">${String(index + 1).padStart(2, "0")}</div>
                <div>
                    <div class="item-title-row">
                        <div class="item-copy">
                            <a href="/problem.html?id=${entry.problemId}" class="item-title">#${entry.problemId} ${ui.escapeHtml(entry.problemTitle || "未命名题目")}</a>
                            <div class="item-note">最后提交：${ui.formatDateTime(entry.lastSubmittedAt)}</div>
                        </div>
                        <span class="difficulty-pill ${String(entry.difficulty || "").toLowerCase()}">${ui.formatDifficulty(entry.difficulty)}</span>
                    </div>
                    <div class="metric-grid">
                        <div class="metric-card">
                            <span>通过数</span>
                            <strong>${entry.acceptedSubmissions}</strong>
                        </div>
                        <div class="metric-card">
                            <span>提交数</span>
                            <strong>${entry.totalSubmissions}</strong>
                        </div>
                        <div class="metric-card">
                            <span>通过率</span>
                            <strong>${Number(entry.acceptanceRate || 0).toFixed(1)}%</strong>
                        </div>
                        <div class="metric-card">
                            <span>最快 AC</span>
                            <strong>${entry.bestAcceptedTime !== null && entry.bestAcceptedTime !== undefined ? `${Number(entry.bestAcceptedTime).toFixed(3)}s` : "-"}</strong>
                        </div>
                    </div>
                </div>
                <div class="item-side">
                    <span class="chip">原榜排名：${entry.rank}</span>
                    <a href="/problem.html?id=${entry.problemId}" class="btn btn-ghost">查看题目</a>
                </div>
            </article>
        `).join("");

        document.getElementById("leaderboard-summary").textContent = state.showingCache
            ? `共 ${state.entries.length} 道上榜题目，当前显示 ${entries.length} 道。正在后台刷新最新数据...`
            : `共 ${state.entries.length} 道上榜题目，当前显示 ${entries.length} 道。`;
    }

    function updateHero(entries) {
        const acceptedTotal = entries.reduce((sum, entry) => sum + Number(entry.acceptedSubmissions || 0), 0);
        const submissionTotal = entries.reduce((sum, entry) => sum + Number(entry.totalSubmissions || 0), 0);
        const acceptanceRate = submissionTotal === 0 ? 0 : (acceptedTotal * 100) / submissionTotal;

        document.getElementById("hero-ranked-count").textContent = `${entries.length} 道`;
        document.getElementById("hero-accepted-total").textContent = acceptedTotal;
        document.getElementById("hero-submission-total").textContent = submissionTotal;
        document.getElementById("hero-acceptance-rate").textContent = `${acceptanceRate.toFixed(1)}%`;
    }

    function sortEntries(left, right, sortKey) {
        if (sortKey === "accepted") {
            return Number(right.acceptedSubmissions || 0) - Number(left.acceptedSubmissions || 0) || left.rank - right.rank;
        }

        if (sortKey === "acceptance") {
            return Number(right.acceptanceRate || 0) - Number(left.acceptanceRate || 0) || left.rank - right.rank;
        }

        if (sortKey === "speed") {
            return normalizeBestTime(left.bestAcceptedTime) - normalizeBestTime(right.bestAcceptedTime) || left.rank - right.rank;
        }

        return left.rank - right.rank;
    }

    function normalizeBestTime(value) {
        return value === null || value === undefined ? Number.MAX_SAFE_INTEGER : Number(value);
    }
})();
