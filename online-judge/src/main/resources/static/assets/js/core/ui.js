(function () {
    const EMPTY_SUMMARY = "查看题目说明、样例和评测限制。";
    const THEME_KEY = "oj:theme";
    const NAV_CURRENT_ROUTE_KEY = "oj:nav:current-route";
    const NAV_PREVIOUS_ROUTE_KEY = "oj:nav:previous-route";
    const MATHJAX_CDN = "/assets/vendor/mathjax/tex-mml-svg.js?v=20260425d";
    const MATHJAX_LOAD_TIMEOUT_MS = 1500;
    const MATHJAX_TEX_EXTENSIONS = ["color", "html", "bbox", "textmacros"];
    const MATHJAX_TEX_LOADS = MATHJAX_TEX_EXTENSIONS.map(name => `[tex]/${name}`);
    let mathJaxPromise = null;
    let mathTypesetScheduled = false;

    function escapeHtml(text) {
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

    function renderInlineMath(math) {
        const content = String(math || "").trim();
        return `<span class="math-inline" data-math-source="${escapeHtml(content)}"><code>${escapeHtml(content)}</code></span>`;
    }

    function applyInlineMarkdown(text) {
        const codeTokens = [];
        const mathTokens = [];
        const source = String(text || "");

        const withCodePlaceholders = source.replace(/`([^`]+)`/g, (_, code) => {
            const token = `@@INLINE_CODE_${codeTokens.length}@@`;
            codeTokens.push(`<code>${escapeHtml(code)}</code>`);
            return token;
        });

        const withMathPlaceholders = withCodePlaceholders.replace(/(^|[^\\$])\$(?!\$)([^$\n]+?)\$(?!\$)/g, (_, prefix, math) => {
            const token = `@@INLINE_MATH_${mathTokens.length}@@`;
            mathTokens.push(renderInlineMath(math));
            return `${prefix}${token}`;
        });

        return escapeHtml(withMathPlaceholders)
            .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
            .replace(/\*([^*]+)\*/g, "<em>$1</em>")
            .replace(/@@INLINE_MATH_(\d+)@@/g, (_, index) => mathTokens[Number(index)] || "")
            .replace(/@@INLINE_CODE_(\d+)@@/g, (_, index) => codeTokens[Number(index)] || "");
    }

    function mergeUnique(base = [], extra = []) {
        return [...new Set([...(Array.isArray(base) ? base : []), ...(Array.isArray(extra) ? extra : [])])];
    }

    function renderMathBlock(math) {
        const content = String(math || "").trim();
        return `<div class="math-block" data-math-source="${escapeHtml(content)}"><code>${escapeHtml(content)}</code></div>`;
    }

    function getPendingMathElements(root) {
        if (!root) {
            return [];
        }

        if (root.matches && root.matches(".math-block:not([data-math-ready='true']), .math-inline:not([data-math-ready='true'])")) {
            return [root];
        }

        if (!root.querySelectorAll) {
            return [];
        }

        return [...root.querySelectorAll(".math-block:not([data-math-ready='true']), .math-inline:not([data-math-ready='true'])")];
    }

    function ensureMathJaxLoaded() {
        if (window.MathJax && typeof window.MathJax.typesetPromise === "function") {
            return Promise.resolve(window.MathJax);
        }

        if (mathJaxPromise) {
            return mathJaxPromise;
        }

        mathJaxPromise = new Promise((resolve, reject) => {
            let settled = false;
            const settle = callback => value => {
                if (settled) {
                    return;
                }
                settled = true;
                callback(value);
            };
            const resolveOnce = settle(resolve);
            const rejectOnce = settle(reject);
            const timeout = window.setTimeout(() => {
                rejectOnce(new Error("MathJax load timed out."));
            }, MATHJAX_LOAD_TIMEOUT_MS);
            const existingScript = document.querySelector(`script[data-mathjax-loader="true"]`);
            if (existingScript) {
                existingScript.addEventListener("load", () => {
                    window.clearTimeout(timeout);
                    resolveOnce(window.MathJax);
                }, { once: true });
                existingScript.addEventListener("error", error => {
                    window.clearTimeout(timeout);
                    rejectOnce(error);
                }, { once: true });
                return;
            }

            const existingConfig = window.MathJax || {};
            window.MathJax = {
                ...existingConfig,
                loader: {
                    ...(existingConfig.loader || {}),
                    load: mergeUnique(existingConfig.loader && existingConfig.loader.load, MATHJAX_TEX_LOADS)
                },
                tex: {
                    ...(existingConfig.tex || {}),
                    inlineMath: [["$", "$"], ["\\(", "\\)"]],
                    displayMath: [["$$", "$$"], ["\\[", "\\]"]],
                    packages: {
                        "[+]": mergeUnique(
                            existingConfig.tex && existingConfig.tex.packages && existingConfig.tex.packages["[+]"],
                            MATHJAX_TEX_EXTENSIONS
                        )
                    },
                    textmacros: {
                        ...((existingConfig.tex && existingConfig.tex.textmacros) || {}),
                        packages: {
                            "[+]": mergeUnique(
                                existingConfig.tex
                                && existingConfig.tex.textmacros
                                && existingConfig.tex.textmacros.packages
                                && existingConfig.tex.textmacros.packages["[+]"],
                                ["color", "html", "bbox"]
                            )
                        }
                    }
                },
                options: {
                    ...(existingConfig.options || {}),
                    skipHtmlTags: ["script", "noscript", "style", "textarea", "pre", "code"]
                }
            };

            const script = document.createElement("script");
            script.src = MATHJAX_CDN;
            script.async = true;
            script.dataset.mathjaxLoader = "true";
            script.addEventListener("load", () => {
                window.clearTimeout(timeout);
                resolveOnce(window.MathJax);
            }, { once: true });
            script.addEventListener("error", error => {
                window.clearTimeout(timeout);
                rejectOnce(error);
            }, { once: true });
            document.head.appendChild(script);
        }).catch(error => {
            mathJaxPromise = null;
            throw error;
        });

        return mathJaxPromise;
    }

    function restoreMathFallback(element) {
        const source = element.dataset.mathSource || "";
        element.innerHTML = `<code>${escapeHtml(source)}</code>`;
        delete element.dataset.mathReady;
    }

    function typesetMath(root = document.body) {
        const pendingMathElements = getPendingMathElements(root);
        if (!pendingMathElements.length) {
            return Promise.resolve();
        }

        return ensureMathJaxLoaded()
            .then(MathJax => {
                pendingMathElements.forEach(element => {
                    const source = escapeHtml(element.dataset.mathSource || "");
                    element.dataset.mathReady = "pending";
                    element.innerHTML = element.classList.contains("math-inline")
                        ? `\\(${source}\\)`
                        : `\\[${source}\\]`;
                });

                return MathJax.typesetPromise(pendingMathElements).then(() => {
                    pendingMathElements.forEach(element => {
                        element.dataset.mathReady = "true";
                    });
                });
            })
            .catch(() => {
                pendingMathElements.forEach(restoreMathFallback);
            });
    }

    function scheduleMathTypeset(root = document.body) {
        if (mathTypesetScheduled) {
            return;
        }

        mathTypesetScheduled = true;
        schedule(() => {
            mathTypesetScheduled = false;
            typesetMath(root);
        });
    }

    function bindMathObserver() {
        if (!document.body || typeof MutationObserver !== "function") {
            return;
        }

        const observer = new MutationObserver(() => {
            if (getPendingMathElements(document.body).length) {
                scheduleMathTypeset(document.body);
            }
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true
        });

        if (getPendingMathElements(document.body).length) {
            scheduleMathTypeset(document.body);
        }
    }

    function renderMarkdown(markdown) {
        const normalized = String(markdown || "")
            .replace(/\r\n/g, "\n")
            .replace(/\r/g, "\n");
        const codeBlocks = [];
        const mathBlocks = [];

        const codePlaceholderMarkdown = normalized.replace(/```([\s\S]*?)```/g, (_, code) => {
            const token = `@@CODEBLOCK_${codeBlocks.length}@@`;
            codeBlocks.push(`<pre><code>${escapeHtml(code.trim())}</code></pre>`);
            return token;
        });

        const placeholderMarkdown = codePlaceholderMarkdown.replace(/(^|\n)\$\$([\s\S]*?)\$\$(?=\n|$)/g, (_, prefix, math) => {
            const token = `@@MATHBLOCK_${mathBlocks.length}@@`;
            mathBlocks.push(renderMathBlock(math));
            return `${prefix}${token}`;
        });

        const lines = placeholderMarkdown.split("\n");
        const parts = [];
        let paragraph = [];
        let listItems = [];
        let listType = null;
        let quoteLines = [];

        function flushParagraph() {
            if (!paragraph.length) {
                return;
            }
            parts.push(`<p>${paragraph.join("<br>")}</p>`);
            paragraph = [];
        }

        function flushList() {
            if (!listItems.length) {
                return;
            }
            const tag = listType === "ol" ? "ol" : "ul";
            parts.push(`<${tag}>${listItems.map(item => `<li>${item}</li>`).join("")}</${tag}>`);
            listItems = [];
            listType = null;
        }

        function flushQuote() {
            if (!quoteLines.length) {
                return;
            }
            parts.push(`<blockquote>${quoteLines.join("<br>")}</blockquote>`);
            quoteLines = [];
        }

        function flushAll() {
            flushParagraph();
            flushList();
            flushQuote();
        }

        lines.forEach(line => {
            const trimmed = line.trim();
            if (!trimmed) {
                flushAll();
                return;
            }

            if (trimmed.startsWith("@@CODEBLOCK_") || trimmed.startsWith("@@MATHBLOCK_")) {
                flushAll();
                parts.push(trimmed);
                return;
            }

            const headingMatch = trimmed.match(/^(#{1,3})\s+(.*)$/);
            if (headingMatch) {
                flushAll();
                const level = headingMatch[1].length;
                parts.push(`<h${level}>${applyInlineMarkdown(headingMatch[2])}</h${level}>`);
                return;
            }

            const orderedListMatch = trimmed.match(/^(\d+)\.\s+(.*)$/);
            if (orderedListMatch) {
                flushParagraph();
                flushQuote();
                if (listType && listType !== "ol") {
                    flushList();
                }
                listType = "ol";
                listItems.push(applyInlineMarkdown(orderedListMatch[2]));
                return;
            }

            const listMatch = trimmed.match(/^[-*]\s+(.*)$/);
            if (listMatch) {
                flushParagraph();
                flushQuote();
                if (listType && listType !== "ul") {
                    flushList();
                }
                listType = "ul";
                listItems.push(applyInlineMarkdown(listMatch[1]));
                return;
            }

            const quoteMatch = trimmed.match(/^> (.*)$/);
            if (quoteMatch) {
                flushParagraph();
                flushList();
                quoteLines.push(applyInlineMarkdown(quoteMatch[1]));
                return;
            }

            flushList();
            flushQuote();
            paragraph.push(applyInlineMarkdown(trimmed));
        });

        flushAll();

        let html = parts.join("");
        codeBlocks.forEach((block, index) => {
            html = html.replace(`@@CODEBLOCK_${index}@@`, block);
        });
        mathBlocks.forEach((block, index) => {
            html = html.replace(`@@MATHBLOCK_${index}@@`, block);
        });

        return html || `<p>${applyInlineMarkdown("暂无内容")}</p>`;
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
                rows.push(renderAlignedPreviewRow("fence", `<span class="aligned-markdown__meta">${escapeHtml(trimmed || "```")}</span>`));
                return;
            }

            if (trimmed === "$$") {
                inMathBlock = !inMathBlock;
                rows.push(renderAlignedPreviewRow("math-delimiter", "<span class=\"aligned-markdown__meta\">$$</span>"));
                return;
            }

            if (!trimmed) {
                rows.push(renderAlignedPreviewRow("empty", "&nbsp;"));
                return;
            }

            if (inCodeBlock) {
                rows.push(renderAlignedPreviewRow("code", `<code>${escapeHtml(rawLine)}</code>`));
                return;
            }

            if (inMathBlock) {
                rows.push(renderAlignedPreviewRow("math", `<code>${escapeHtml(rawLine)}</code>`));
                return;
            }

            const headingMatch = trimmed.match(/^(#{1,3})\s+(.*)$/);
            if (headingMatch) {
                rows.push(renderAlignedPreviewRow(`heading-${headingMatch[1].length}`, applyInlineMarkdown(headingMatch[2])));
                return;
            }

            const orderedMatch = trimmed.match(/^(\d+)\.\s+(.*)$/);
            if (orderedMatch) {
                rows.push(renderAlignedPreviewRow(
                    "ordered",
                    `<span class="aligned-markdown__marker">${escapeHtml(`${orderedMatch[1]}.`)}</span><span class="aligned-markdown__text">${applyInlineMarkdown(orderedMatch[2])}</span>`
                ));
                return;
            }

            const listMatch = trimmed.match(/^-\s+(.*)$/);
            if (listMatch) {
                rows.push(renderAlignedPreviewRow(
                    "list",
                    `<span class="aligned-markdown__marker">•</span><span class="aligned-markdown__text">${applyInlineMarkdown(listMatch[1])}</span>`
                ));
                return;
            }

            const quoteMatch = trimmed.match(/^>\s?(.*)$/);
            if (quoteMatch) {
                rows.push(renderAlignedPreviewRow(
                    "quote",
                    `<span class="aligned-markdown__quote-bar"></span><span class="aligned-markdown__text">${applyInlineMarkdown(quoteMatch[1])}</span>`
                ));
                return;
            }

            rows.push(renderAlignedPreviewRow("paragraph", `<span class="aligned-markdown__text">${applyInlineMarkdown(rawLine)}</span>`));
        });

        return rows.join("");
    }

    function renderAlignedPreviewRow(kind, content) {
        return `<div class="aligned-markdown__row aligned-markdown__row--${kind}"><div class="aligned-markdown__cell">${content}</div></div>`;
    }

    function formatVerdict(verdict) {
        const mapping = {
            ACCEPTED: "通过",
            WRONG_ANSWER: "答案错误",
            TIME_LIMIT_EXCEEDED: "超时",
            MEMORY_LIMIT_EXCEEDED: "超内存",
            RUNTIME_ERROR: "运行错误",
            COMPILATION_ERROR: "编译错误",
            PENDING: "评测中",
            INTERNAL_ERROR: "系统错误"
        };
        return mapping[verdict] || verdict || "-";
    }

    function getVerdictClass(verdict) {
        const mapping = {
            ACCEPTED: "accepted",
            WRONG_ANSWER: "wrong-answer",
            TIME_LIMIT_EXCEEDED: "tle",
            MEMORY_LIMIT_EXCEEDED: "mle",
            RUNTIME_ERROR: "runtime-error",
            COMPILATION_ERROR: "compilation-error",
            PENDING: "pending",
            INTERNAL_ERROR: "compilation-error"
        };
        return mapping[verdict] || "pending";
    }

    function formatDifficulty(difficulty) {
        const mapping = {
            EASY: "简单",
            MEDIUM: "中等",
            HARD: "困难"
        };
        return mapping[difficulty] || difficulty || "-";
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return String(value);
        }

        return new Intl.DateTimeFormat("zh-CN", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false
        }).format(date).replace(/\//g, "-");
    }

    function formatDate(value) {
        if (!value) {
            return "暂无";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return String(value);
        }

        return new Intl.DateTimeFormat("zh-CN", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit"
        }).format(date).replace(/\//g, "-");
    }

    function extractSummary(markdown) {
        const summary = String(markdown || "")
            .replace(/```[\s\S]*?```/g, " ")
            .replace(/\$\$[\s\S]*?\$\$/g, " ")
            .replace(/[#>*`_\-\n]/g, " ")
            .replace(/\s+/g, " ")
            .trim();
        if (!summary) {
            return EMPTY_SUMMARY;
        }
        return summary.length > 120 ? `${summary.slice(0, 120)}...` : summary;
    }

    function normalizeKeyword(text) {
        return String(text || "").trim().toLowerCase();
    }

    async function readJson(response) {
        try {
            return await response.json();
        } catch (error) {
            return {};
        }
    }

    async function copyText(text) {
        if (!navigator.clipboard) {
            throw new Error("当前浏览器不支持剪贴板操作");
        }
        await navigator.clipboard.writeText(text || "");
    }

    function debounce(fn, wait) {
        let timer = null;
        return function debounced(...args) {
            window.clearTimeout(timer);
            timer = window.setTimeout(() => fn.apply(this, args), wait);
        };
    }

    function schedule(task) {
        if (typeof window.requestAnimationFrame === "function") {
            window.requestAnimationFrame(task);
            return;
        }
        window.setTimeout(task, 16);
    }

    function getSessionStorage() {
        try {
            return window.sessionStorage;
        } catch (error) {
            return null;
        }
    }

    function writeCache(key, value) {
        const storage = getSessionStorage();
        if (!storage) {
            return;
        }

        try {
            storage.setItem(key, JSON.stringify({
                savedAt: Date.now(),
                value
            }));
        } catch (error) {
            // ignore cache write failures
        }
    }

    function readCache(key, maxAgeMs) {
        const storage = getSessionStorage();
        if (!storage) {
            return null;
        }

        try {
            const raw = storage.getItem(key);
            if (!raw) {
                return null;
            }

            const parsed = JSON.parse(raw);
            if (!parsed || typeof parsed !== "object") {
                return null;
            }

            const savedAt = Number(parsed.savedAt || 0);
            const expired = !savedAt || (typeof maxAgeMs === "number" && Date.now() - savedAt > maxAgeMs);
            return {
                value: parsed.value,
                savedAt,
                expired
            };
        } catch (error) {
            return null;
        }
    }

    function getCurrentRoute() {
        return `${window.location.pathname || "/"}${window.location.search || ""}`;
    }

    function isHomeRoute(route) {
        return typeof route === "string" && (route === "/" || route.startsWith("/?"));
    }

    function trackNavigationRoute() {
        const storage = getSessionStorage();
        if (!storage) {
            return;
        }

        try {
            const currentRoute = getCurrentRoute();
            const previousCurrentRoute = storage.getItem(NAV_CURRENT_ROUTE_KEY);
            if (previousCurrentRoute && previousCurrentRoute !== currentRoute) {
                storage.setItem(NAV_PREVIOUS_ROUTE_KEY, previousCurrentRoute);
            }
            storage.setItem(NAV_CURRENT_ROUTE_KEY, currentRoute);
        } catch (error) {
            // ignore navigation tracking failures
        }
    }

    function shouldUseHistoryBackForHome() {
        if (isHomeRoute(getCurrentRoute()) || window.history.length <= 1) {
            return false;
        }

        const storage = getSessionStorage();
        if (!storage) {
            return false;
        }

        try {
            return isHomeRoute(storage.getItem(NAV_PREVIOUS_ROUTE_KEY));
        } catch (error) {
            return false;
        }
    }

    function isModifiedNavigation(event) {
        return event.defaultPrevented
            || event.button !== 0
            || event.metaKey
            || event.ctrlKey
            || event.shiftKey
            || event.altKey;
    }

    function bindSmartHomeLinks() {
        document.querySelectorAll('a[href="/"], a[href^="/?"]').forEach(link => {
            if (link.dataset.smartHomeBound === "true") {
                return;
            }

            link.dataset.smartHomeBound = "true";
            link.addEventListener("click", event => {
                if (isModifiedNavigation(event) || !shouldUseHistoryBackForHome()) {
                    return;
                }

                event.preventDefault();
                window.history.back();
            });
        });
    }

    function getLocalStorage() {
        try {
            return window.localStorage;
        } catch (error) {
            return null;
        }
    }

    function readTheme() {
        const storage = getLocalStorage();
        if (!storage) {
            return "light";
        }

        const theme = storage.getItem(THEME_KEY);
        return theme === "dark" ? "dark" : "light";
    }

    function applyTheme(theme) {
        const normalized = theme === "dark" ? "dark" : "light";
        document.body.dataset.theme = normalized;
        document.documentElement.style.colorScheme = normalized === "dark" ? "dark" : "light";
        syncThemeButtons(normalized);
        return normalized;
    }

    function writeTheme(theme) {
        const storage = getLocalStorage();
        if (!storage) {
            return;
        }

        try {
            storage.setItem(THEME_KEY, theme);
        } catch (error) {
            // ignore theme persistence failures
        }
    }

    function toggleTheme() {
        const nextTheme = document.body.dataset.theme === "dark" ? "light" : "dark";
        writeTheme(nextTheme);
        return applyTheme(nextTheme);
    }

    function syncThemeButtons(theme) {
        document.querySelectorAll("[data-theme-toggle]").forEach(button => {
            button.textContent = theme === "dark" ? "暖光日间" : "深棕夜间";
            button.setAttribute("aria-pressed", theme === "dark" ? "true" : "false");
        });
    }

    function bindThemeToggles() {
        document.querySelectorAll("[data-theme-toggle]").forEach(button => {
            if (button.dataset.boundThemeToggle === "true") {
                return;
            }

            button.dataset.boundThemeToggle = "true";
            button.addEventListener("click", () => {
                toggleTheme();
            });
        });
        syncThemeButtons(document.body.dataset.theme || readTheme());
    }

    applyTheme(readTheme());
    trackNavigationRoute();
    window.addEventListener("pageshow", () => {
        trackNavigationRoute();
    });
    document.addEventListener("DOMContentLoaded", () => {
        bindThemeToggles();
        bindSmartHomeLinks();
        bindMathObserver();
    });

    window.CodeJudgeUI = {
        applyTheme,
        copyText,
        debounce,
        escapeHtml,
        extractSummary,
        formatDate,
        formatDateTime,
        formatDifficulty,
        formatVerdict,
        getVerdictClass,
        normalizeKeyword,
        readCache,
        readJson,
        renderAlignedMarkdownPreview,
        renderMarkdown,
        schedule,
        typesetMath,
        toggleTheme,
        writeCache
    };
})();
