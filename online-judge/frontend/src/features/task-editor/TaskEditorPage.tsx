import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useSearchParams } from "react-router-dom";
import { Bold, Code2, Eye, EyeOff, Italic, Link2, List, ListOrdered, Quote, RotateCcw, Save, Trash2 } from "lucide-react";
import { api } from "../../shared/api/client";
import type { Problem, ProblemCatalogItem, ProblemManage } from "../../shared/api/types";
import { difficultyLabel } from "../../shared/format";
import { useTranslation } from "../../shared/i18n";
import { Button } from "../../shared/ui/Button";
import { EmptyState } from "../../shared/ui/EmptyState";
import { Field, Select, TextArea, TextInput } from "../../shared/ui/Field";
import { Panel } from "../../shared/ui/Panel";
import { DifficultyPill, StatusPill } from "../../shared/ui/StatusPill";
import CodeEditor from "../problem/CodeEditor";
import { CPP17_LANGUAGE_ID, PYTHON3_LANGUAGE_ID } from "../problem/languages";

type TestCaseDraft = {
  input: string;
  expectedOutput: string;
  hidden: boolean;
};

const initialProblem = {
  id: "",
  title: "",
  description: "",
  difficulty: "EASY",
  timeLimit: 1000,
  memoryLimit: 131072,
  aiPromptDirection: "",
  starterCode: "",
  knowledgePointsText: "",
  algorithmStrategiesText: "",
  commonMistakesText: "",
  boundaryTypesText: "",
  testCases: [{ input: "", expectedOutput: "", hidden: false }] as TestCaseDraft[]
};

function createInitialProblem() {
  return {
    ...initialProblem,
    testCases: initialProblem.testCases.map(item => ({ ...item }))
  };
}

function adaptiveRows(value: string, minRows: number, maxRows: number, charsPerRow: number) {
  const lines = value ? value.split("\n") : [""];
  const estimatedRows = lines.reduce((total, line) => total + Math.max(1, Math.ceil(line.length / charsPerRow)), 0);
  return Math.min(maxRows, Math.max(minRows, estimatedRows));
}

function renderInlineMarkdown(value: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /`([^`]+)`|\*\*([^*\n]+)\*\*|\*([^*\n]+)\*|\[([^\]\n]+)\]\((https?:\/\/[^)\s]+|\/[^)\s]*)\)/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let tokenIndex = 0;

  while ((match = pattern.exec(value))) {
    if (match.index > lastIndex) {
      nodes.push(value.slice(lastIndex, match.index));
    }

    const key = `${keyPrefix}-${tokenIndex}`;
    if (match[1]) {
      nodes.push(<code key={key}>{match[1]}</code>);
    } else if (match[2]) {
      nodes.push(<strong key={key}>{match[2]}</strong>);
    } else if (match[3]) {
      nodes.push(<em key={key}>{match[3]}</em>);
    } else if (match[4] && match[5]) {
      nodes.push(
        <a href={match[5]} key={key} rel="noreferrer" target="_blank">
          {match[4]}
        </a>
      );
    }

    tokenIndex += 1;
    lastIndex = pattern.lastIndex;
  }

  if (lastIndex < value.length) {
    nodes.push(value.slice(lastIndex));
  }

  return nodes.length ? nodes : [value];
}

function renderMarkdownPreview(value: string, emptyText: string): ReactNode[] {
  if (!value.trim()) {
    return [
      <p className="editor-markdown-preview__empty" key="empty">
        {emptyText}
      </p>
    ];
  }

  const nodes: ReactNode[] = [];
  const codeLines: string[] = [];
  let inCode = false;

  function flushCode(key: string) {
    if (codeLines.length) {
      nodes.push(
        <pre key={key}>
          <code>{codeLines.join("\n")}</code>
        </pre>
      );
      codeLines.length = 0;
    }
  }

  value.split("\n").forEach((line, index) => {
    const trimmed = line.trim();
    if (trimmed.startsWith("```")) {
      if (inCode) {
        flushCode(`code-${index}`);
      }
      inCode = !inCode;
      return;
    }
    if (inCode) {
      codeLines.push(line);
      return;
    }
    if (!trimmed) {
      nodes.push(<span className="editor-markdown-preview__space" key={`space-${index}`} />);
      return;
    }
    if (trimmed.startsWith("### ")) {
      nodes.push(<h4 key={`h4-${index}`}>{renderInlineMarkdown(trimmed.slice(4), `h4-${index}`)}</h4>);
      return;
    }
    if (trimmed.startsWith("## ")) {
      nodes.push(<h3 key={`h3-${index}`}>{renderInlineMarkdown(trimmed.slice(3), `h3-${index}`)}</h3>);
      return;
    }
    if (trimmed.startsWith("# ")) {
      nodes.push(<h2 key={`h2-${index}`}>{renderInlineMarkdown(trimmed.slice(2), `h2-${index}`)}</h2>);
      return;
    }
    if (trimmed.startsWith(">")) {
      nodes.push(<blockquote key={`quote-${index}`}>{renderInlineMarkdown(trimmed.replace(/^>\s?/, ""), `quote-${index}`)}</blockquote>);
      return;
    }
    if (/^[-*]\s+/.test(trimmed)) {
      nodes.push(
        <p className="editor-markdown-preview__list" key={`list-${index}`}>
          <span aria-hidden="true">•</span>
          <span>{renderInlineMarkdown(trimmed.replace(/^[-*]\s+/, ""), `list-${index}`)}</span>
        </p>
      );
      return;
    }
    if (/^\d+\.\s+/.test(trimmed)) {
      nodes.push(<p key={`ordered-${index}`}>{renderInlineMarkdown(trimmed, `ordered-${index}`)}</p>);
      return;
    }
    nodes.push(<p key={`p-${index}`}>{renderInlineMarkdown(line, `p-${index}`)}</p>);
  });
  flushCode("code-tail");
  return nodes;
}

function inferStarterLanguage(source: string) {
  return /#include|using namespace|int\s+main\s*\(/.test(source) ? CPP17_LANGUAGE_ID : PYTHON3_LANGUAGE_ID;
}

type TaskEditorPageProps = {
  embedded?: boolean;
  selectedProblemId?: number | null;
  showCatalogDrawer?: boolean;
  createDraftSignal?: number;
  onSaved?: (problem: Problem) => void;
};

export default function TaskEditorPage({
  embedded = false,
  selectedProblemId,
  showCatalogDrawer = true,
  createDraftSignal = 0,
  onSaved
}: TaskEditorPageProps) {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [catalog, setCatalog] = useState<ProblemCatalogItem[]>([]);
  const [form, setForm] = useState(createInitialProblem);
  const [alert, setAlert] = useState<{ type: "success" | "error"; message: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [statementPreview, setStatementPreview] = useState(false);
  const [starterLanguageId, setStarterLanguageId] = useState(PYTHON3_LANGUAGE_ID);
  const statementRef = useRef<HTMLTextAreaElement | null>(null);
  const statementPreviewRef = useRef<HTMLDivElement | null>(null);
  const initialStarterCodeRef = useRef(initialProblem.starterCode);
  const loadProblemRequestRef = useRef(0);

  const visibleCount = useMemo(() => form.testCases.filter(item => !item.hidden).length, [form.testCases]);
  const hiddenCount = useMemo(() => form.testCases.filter(item => item.hidden).length, [form.testCases]);
  const statementPreviewBlocks = useMemo(
    () => renderMarkdownPreview(form.description, t("taskEditor.statement.emptyPreview")),
    [form.description, t]
  );
  const qualityItems = useMemo(
    () => [
      { label: t("taskEditor.quality.title"), ready: Boolean(form.title.trim()), note: form.title.trim() ? t("taskEditor.quality.filled") : t("taskEditor.quality.empty") },
      { label: t("taskEditor.quality.statement"), ready: form.description.trim().length >= 20, note: form.description.trim().length >= 20 ? t("taskEditor.quality.filled") : t("taskEditor.quality.incomplete") },
      { label: t("taskEditor.quality.publicTests"), ready: visibleCount > 0, note: visibleCount > 0 ? t("taskEditor.quality.visibleCount", { count: visibleCount }) : t("taskEditor.quality.needPublic") },
      { label: t("taskEditor.quality.hiddenTests"), ready: hiddenCount > 0, note: hiddenCount > 0 ? t("taskEditor.quality.hiddenCount", { count: hiddenCount }) : t("taskEditor.quality.notSet") },
      {
        label: t("taskEditor.quality.knowledge"),
        ready: Boolean(form.knowledgePointsText.trim() || form.commonMistakesText.trim() || form.boundaryTypesText.trim()),
        note: form.knowledgePointsText.trim() ? t("taskEditor.quality.filled") : t("taskEditor.quality.empty")
      }
    ],
    [form.boundaryTypesText, form.commonMistakesText, form.description, form.knowledgePointsText, form.title, hiddenCount, t, visibleCount]
  );
  const readyQualityCount = qualityItems.filter(item => item.ready).length;
  const publishState = readyQualityCount >= qualityItems.length ? t("taskEditor.status.checked") : readyQualityCount >= 4 ? t("taskEditor.status.basic") : t("taskEditor.status.incomplete");
  const readinessTone = readyQualityCount >= qualityItems.length ? "success" : readyQualityCount >= 4 ? "info" : "warning";

  useEffect(() => {
    if (showCatalogDrawer) {
      void loadCatalog();
    }
    const id = searchParams.get("id");
    const initialId = selectedProblemId || (id ? Number(id) : null);
    if (initialId) {
      void loadProblem(initialId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (selectedProblemId) {
      void loadProblem(selectedProblemId);
    }
  }, [selectedProblemId]);

  useEffect(() => {
    if (!statementPreview) {
      return;
    }
    window.requestAnimationFrame(() => {
      statementPreviewRef.current?.scrollTo({ top: 0 });
    });
  }, [statementPreview]);

  useEffect(() => {
    if (createDraftSignal > 0 && selectedProblemId == null) {
      loadProblemRequestRef.current += 1;
      const draft = createInitialProblem();
      initialStarterCodeRef.current = draft.starterCode;
      setStarterLanguageId(PYTHON3_LANGUAGE_ID);
      setForm(draft);
      setAlert(null);
    }
  }, [createDraftSignal, selectedProblemId]);

  async function loadCatalog() {
    try {
      setCatalog(await api.problemCatalog());
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("taskEditor.errors.catalog") });
    }
  }

  async function loadProblem(id: number) {
    const requestId = loadProblemRequestRef.current + 1;
    loadProblemRequestRef.current = requestId;
    try {
      const problem = await api.problemManage(id);
      if (requestId !== loadProblemRequestRef.current) {
        return;
      }
      populate(problem);
    } catch (error) {
      if (requestId !== loadProblemRequestRef.current) {
        return;
      }
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("taskEditor.errors.load") });
    }
  }

  function populate(problem: ProblemManage) {
    const starterCode = problem.starterCode || "";
    initialStarterCodeRef.current = starterCode;
    setStarterLanguageId(inferStarterLanguage(starterCode));
    setForm({
      id: String(problem.id),
      title: problem.title || "",
      description: problem.description || "",
      difficulty: String(problem.difficulty || "EASY"),
      timeLimit: problem.timeLimit || 1000,
      memoryLimit: problem.memoryLimit || 131072,
      aiPromptDirection: problem.aiPromptDirection || "",
      starterCode,
      knowledgePointsText: joinList(problem.knowledgePoints),
      algorithmStrategiesText: joinList(problem.algorithmStrategies),
      commonMistakesText: joinList(problem.commonMistakes),
      boundaryTypesText: joinList(problem.boundaryTypes),
      testCases: problem.testCases?.length
        ? problem.testCases.map(item => ({
            input: item.input || "",
            expectedOutput: item.expectedOutput || "",
            hidden: Boolean(item.hidden)
          }))
        : [{ input: "", expectedOutput: "", hidden: false }]
    });
  }

  function updateTestCase(index: number, patch: Partial<TestCaseDraft>) {
    setForm(current => ({
      ...current,
      testCases: current.testCases.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item))
    }));
  }

  function addTestCase(hidden = false) {
    setForm(current => ({
      ...current,
      testCases: [...current.testCases, { input: "", expectedOutput: "", hidden }]
    }));
  }

  function removeTestCase(index: number) {
    setForm(current => ({
      ...current,
      testCases: current.testCases.length > 1 ? current.testCases.filter((_, itemIndex) => itemIndex !== index) : current.testCases
    }));
  }

  function insertStatementInline(prefix: string, suffix = prefix, fallback = t("taskEditor.statement.fallbackText")) {
    const textarea = statementRef.current;
    const value = form.description;
    const start = textarea?.selectionStart ?? value.length;
    const end = textarea?.selectionEnd ?? value.length;
    const selected = value.slice(start, end) || fallback;
    const nextValue = `${value.slice(0, start)}${prefix}${selected}${suffix}${value.slice(end)}`;
    const nextStart = start + prefix.length;
    const nextEnd = nextStart + selected.length;
    setForm({ ...form, description: nextValue });
    window.requestAnimationFrame(() => {
      statementRef.current?.focus();
      statementRef.current?.setSelectionRange(nextStart, nextEnd);
    });
  }

  function insertStatementBlock(marker: string, fallback = t("taskEditor.statement.fallbackText")) {
    const textarea = statementRef.current;
    const value = form.description;
    const start = textarea?.selectionStart ?? value.length;
    const end = textarea?.selectionEnd ?? value.length;
    const selected = value.slice(start, end) || fallback;
    const needsLeadBreak = start > 0 && value[start - 1] !== "\n";
    const block = selected
      .split("\n")
      .map(line => `${marker}${line || fallback}`)
      .join("\n");
    const nextValue = `${value.slice(0, start)}${needsLeadBreak ? "\n" : ""}${block}${value.slice(end)}`;
    const offset = needsLeadBreak ? 1 : 0;
    const nextStart = start + offset + marker.length;
    const nextEnd = nextStart + selected.length;
    setForm({ ...form, description: nextValue });
    window.requestAnimationFrame(() => {
      statementRef.current?.focus();
      statementRef.current?.setSelectionRange(nextStart, nextEnd);
    });
  }

  function resetStarterCode() {
    setForm(current => ({ ...current, starterCode: initialStarterCodeRef.current }));
  }

  async function save() {
    if (!form.title.trim() || !form.description.trim()) {
      setAlert({ type: "error", message: t("taskEditor.errors.required") });
      return;
    }
    if (visibleCount < 1) {
      setAlert({ type: "error", message: t("taskEditor.errors.needPublic") });
      return;
    }
    setBusy(true);
    const payload = {
      title: form.title.trim(),
      description: form.description.trim(),
      difficulty: form.difficulty,
      timeLimit: Number(form.timeLimit),
      memoryLimit: Number(form.memoryLimit),
      aiPromptDirection: form.aiPromptDirection.trim(),
      starterCode: form.starterCode.trim(),
      knowledgePoints: splitList(form.knowledgePointsText),
      algorithmStrategies: splitList(form.algorithmStrategiesText),
      commonMistakes: splitList(form.commonMistakesText),
      boundaryTypes: splitList(form.boundaryTypesText),
      testCases: form.testCases.map(item => ({
        input: item.input,
        expectedOutput: item.expectedOutput,
        hidden: item.hidden
      }))
    };
    try {
      const result = form.id ? await api.updateProblem(Number(form.id), payload) : await api.createProblem(payload);
      setAlert({ type: "success", message: t("taskEditor.success.saved") });
      initialStarterCodeRef.current = form.starterCode;
      setForm(current => ({ ...current, id: String(result.id) }));
      if (showCatalogDrawer) {
        await loadCatalog();
      }
      onSaved?.(result);
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("taskEditor.errors.save") });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={`stack task-editor-page ${embedded ? "task-editor-page--embedded" : ""}`}>
      {!embedded ? (
        <section className="editor-command">
          <div>
            <h1>{form.title || t("taskEditor.newProblem")}</h1>
          </div>
          <div className="editor-command__actions">
            <span className={`meta-badge ${visibleCount ? "meta-badge--success" : "meta-badge--danger"}`}>{t("taskEditor.badges.publicTests", { count: visibleCount })}</span>
            <span className={`meta-badge ${readyQualityCount >= 4 ? "meta-badge--success" : "meta-badge--warning"}`}>{t("taskEditor.badges.checks", { count: readyQualityCount })}</span>
            <Button type="button" variant="primary" onClick={() => void save()} disabled={busy} icon={<Save size={18} />}>
              {t("taskEditor.actions.saveProblem")}
            </Button>
          </div>
        </section>
      ) : (
        <section className="editor-command editor-command--embedded">
          <div>
            <p className="eyebrow">{t("taskEditor.eyebrow")}</p>
            <h1>{form.title || t("taskEditor.newProblem")}</h1>
          </div>
          <div className="editor-command__actions">
            <span className={`meta-badge ${visibleCount ? "meta-badge--success" : "meta-badge--danger"}`}>{t("taskEditor.badges.publicCompact", { count: visibleCount })}</span>
            <span className={`meta-badge ${readyQualityCount >= 4 ? "meta-badge--success" : "meta-badge--warning"}`}>{readyQualityCount}/5</span>
            <Button type="button" variant="primary" onClick={() => void save()} disabled={busy} icon={<Save size={18} />}>
              {t("taskEditor.actions.save")}
            </Button>
          </div>
        </section>
      )}

      {alert && <div className={`alert alert--${alert.type === "success" ? "success" : "error"}`}>{alert.message}</div>}

      <section className="editor-workbench editor-workbench--single">
        <div className="editor-primary-stack">
          <Panel className="editor-problem-panel" title={t("taskEditor.sections.problemInfo")} action={<StatusPill tone={readinessTone}>{publishState}</StatusPill>}>
            <div className="stack">
              <div className="form-grid">
                <Field label={t("taskEditor.fields.title")}>
                  <TextInput value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} placeholder={t("taskEditor.placeholders.title")} />
                </Field>
                <Field label={t("taskEditor.fields.difficulty")}>
                  <Select value={form.difficulty} onChange={event => setForm({ ...form, difficulty: event.target.value })}>
                    <option value="EASY">{t("taskEditor.difficulty.easy")}</option>
                    <option value="MEDIUM">{t("taskEditor.difficulty.medium")}</option>
                    <option value="HARD">{t("taskEditor.difficulty.hard")}</option>
                  </Select>
                </Field>
                <Field label={t("taskEditor.fields.timeLimit")}>
                  <TextInput type="number" value={form.timeLimit} onChange={event => setForm({ ...form, timeLimit: Number(event.target.value) })} />
                </Field>
              </div>
              <div className="field editor-field">
                <span>{t("taskEditor.fields.statement")}</span>
                <div className="editor-markdown-shell">
                  <div className="editor-inline-toolbar" aria-label={t("taskEditor.statement.toolbarAria")}>
                    <div className="editor-inline-toolbar__group">
                      <button type="button" className="editor-tool-button" onClick={() => insertStatementInline("**", "**", t("taskEditor.statement.boldFallback"))} aria-label={t("taskEditor.statement.bold")} title={t("taskEditor.statement.bold")}>
                        <Bold size={15} />
                      </button>
                      <button type="button" className="editor-tool-button" onClick={() => insertStatementInline("*", "*", t("taskEditor.statement.italicFallback"))} aria-label={t("taskEditor.statement.italic")} title={t("taskEditor.statement.italic")}>
                        <Italic size={15} />
                      </button>
                      <button type="button" className="editor-tool-button" onClick={() => insertStatementInline("`", "`", "code")} aria-label={t("taskEditor.statement.inlineCode")} title={t("taskEditor.statement.inlineCode")}>
                        <Code2 size={15} />
                      </button>
                      <button type="button" className="editor-tool-button" onClick={() => insertStatementBlock("> ", t("taskEditor.statement.quoteFallback"))} aria-label={t("taskEditor.statement.quote")} title={t("taskEditor.statement.quote")}>
                        <Quote size={15} />
                      </button>
                      <button type="button" className="editor-tool-button" onClick={() => insertStatementBlock("- ", t("taskEditor.statement.listFallback"))} aria-label={t("taskEditor.statement.unorderedList")} title={t("taskEditor.statement.unorderedList")}>
                        <List size={15} />
                      </button>
                      <button type="button" className="editor-tool-button" onClick={() => insertStatementBlock("1. ", t("taskEditor.statement.listFallback"))} aria-label={t("taskEditor.statement.orderedList")} title={t("taskEditor.statement.orderedList")}>
                        <ListOrdered size={15} />
                      </button>
                      <button type="button" className="editor-tool-button" onClick={() => insertStatementInline("[", "](https://)", t("taskEditor.statement.linkFallback"))} aria-label={t("taskEditor.statement.link")} title={t("taskEditor.statement.link")}>
                        <Link2 size={15} />
                      </button>
                    </div>
                    <button
                      type="button"
                      className={`editor-tool-button editor-tool-button--text ${statementPreview ? "is-active" : ""}`}
                      onClick={() => setStatementPreview(value => !value)}
                      aria-pressed={statementPreview}
                    >
                      {statementPreview ? <EyeOff size={15} /> : <Eye size={15} />}
                      <span>{statementPreview ? t("taskEditor.statement.edit") : t("taskEditor.statement.preview")}</span>
                    </button>
                  </div>
                  {statementPreview ? (
                    <div className="editor-markdown-preview" ref={statementPreviewRef}>
                      {statementPreviewBlocks}
                    </div>
                  ) : (
                    <TextArea
                      ref={statementRef}
                      className="editor-statement-textarea"
                      value={form.description}
                      onChange={event => setForm({ ...form, description: event.target.value })}
                      rows={adaptiveRows(form.description, 7, 7, 88)}
                    />
                  )}
                  <div className="editor-field-foot">
                    <span>Markdown</span>
                    <strong>{form.description.length.toLocaleString()} / 20000</strong>
                  </div>
                </div>
              </div>
              <div className="field editor-field">
                <span>{t("taskEditor.fields.starterCode")}</span>
                <div className="editor-starter-code-shell">
                  <div className="editor-inline-toolbar editor-inline-toolbar--code" aria-label={t("taskEditor.code.toolbarAria")}>
                    <Select value={starterLanguageId} onChange={event => setStarterLanguageId(Number(event.target.value))} aria-label={t("taskEditor.code.languageAria")}>
                      <option value={PYTHON3_LANGUAGE_ID}>Python 3</option>
                      <option value={CPP17_LANGUAGE_ID}>C++17</option>
                    </Select>
                    <button type="button" className="editor-tool-button editor-tool-button--text" onClick={resetStarterCode}>
                      <RotateCcw size={15} />
                      <span>{t("taskEditor.actions.reset")}</span>
                    </button>
                  </div>
                  <CodeEditor
                    className="editor-code-editor"
                    languageId={starterLanguageId}
                    sourceCode={form.starterCode}
                    onChange={value => setForm(current => ({ ...current, starterCode: value }))}
                    minHeight="112px"
                  />
                </div>
              </div>
            </div>
          </Panel>

          <Panel
            className="editor-tests-panel"
            title={t("taskEditor.sections.tests")}
            action={
              <div className="actions">
                <StatusPill tone={visibleCount ? "success" : "danger"}>{t("taskEditor.badges.publicCompact", { count: visibleCount })}</StatusPill>
                <StatusPill tone={hiddenCount ? "warning" : "neutral"}>{t("taskEditor.badges.hiddenCompact", { count: hiddenCount })}</StatusPill>
                <Button type="button" variant="secondary" onClick={() => addTestCase(false)}>
                  {t("taskEditor.actions.addPublic")}
                </Button>
                <Button type="button" variant="ghost" onClick={() => addTestCase(true)}>
                  {t("taskEditor.actions.addHidden")}
                </Button>
              </div>
            }
          >
            {embedded ? (
              <div className="editor-test-table-wrap">
                <table className="editor-test-table">
                  <thead>
                    <tr>
                      <th>{t("taskEditor.tests.index")}</th>
                      <th>{t("taskEditor.tests.status")}</th>
                      <th>{t("taskEditor.tests.input")}</th>
                      <th>{t("taskEditor.tests.expected")}</th>
                      <th>{t("taskEditor.tests.score")}</th>
                      <th>{t("taskEditor.tests.hidden")}</th>
                      <th>{t("taskEditor.tests.actions")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {form.testCases.map((item, index) => (
                      <tr key={index}>
                        <td className="editor-test-table__index">{String(index + 1).padStart(2, "0")}</td>
                        <td>
                          <StatusPill tone={item.hidden ? "warning" : "success"}>{item.hidden ? t("taskEditor.tests.hiddenShort") : t("taskEditor.tests.publicShort")}</StatusPill>
                        </td>
                        <td>
                          <TextArea
                            aria-label={t("taskEditor.tests.inputAria", { index: index + 1 })}
                            className="editor-test-textarea"
                            value={item.input}
                            onChange={event => updateTestCase(index, { input: event.target.value })}
                            rows={adaptiveRows(item.input, 2, 4, 36)}
                          />
                        </td>
                        <td>
                          <TextArea
                            aria-label={t("taskEditor.tests.expectedAria", { index: index + 1 })}
                            className="editor-test-textarea"
                            value={item.expectedOutput}
                            onChange={event => updateTestCase(index, { expectedOutput: event.target.value })}
                            rows={adaptiveRows(item.expectedOutput, 2, 4, 36)}
                          />
                        </td>
                        <td className="editor-test-table__score">100</td>
                        <td>
                          <label className="editor-switch">
                            <input type="checkbox" checked={item.hidden} onChange={event => updateTestCase(index, { hidden: event.target.checked })} aria-label={t("taskEditor.tests.hiddenAria", { index: index + 1 })} />
                            <span aria-hidden="true" />
                          </label>
                        </td>
                        <td>
                          <button type="button" className="editor-tool-button editor-tool-button--danger" onClick={() => removeTestCase(index)} aria-label={t("taskEditor.tests.deleteAria", { index: index + 1 })}>
                            <Trash2 size={15} />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="stack">
                {form.testCases.map((item, index) => (
                  <div className="list-row editor-test-row" key={index}>
                    <div className="actions">
                      <StatusPill tone={item.hidden ? "warning" : "success"}>{item.hidden ? t("taskEditor.tests.hiddenTest") : t("taskEditor.tests.publicTest")}</StatusPill>
                      <label className="actions editor-test-toggle">
                        <input type="checkbox" checked={item.hidden} onChange={event => updateTestCase(index, { hidden: event.target.checked })} />
                        {t("taskEditor.tests.hidden")}
                      </label>
                      <Button type="button" variant="danger" onClick={() => removeTestCase(index)}>
                        {t("taskEditor.actions.delete")}
                      </Button>
                    </div>
                    <div className="two-column">
                      <Field label={t("taskEditor.tests.input")}>
                        <TextArea
                          className="editor-test-textarea"
                          value={item.input}
                          onChange={event => updateTestCase(index, { input: event.target.value })}
                          rows={adaptiveRows(item.input, 2, 6, 48)}
                        />
                      </Field>
                      <Field label={t("taskEditor.tests.expected")}>
                        <TextArea
                          className="editor-test-textarea"
                          value={item.expectedOutput}
                          onChange={event => updateTestCase(index, { expectedOutput: event.target.value })}
                          rows={adaptiveRows(item.expectedOutput, 2, 6, 48)}
                        />
                      </Field>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Panel>

          <details className="editor-compact-details editor-compact-details--teaching">
            <summary>
              <span>{t("taskEditor.sections.teaching")}</span>
              <StatusPill tone={qualityItems[4].ready ? "success" : "neutral"}>{qualityItems[4].ready ? t("taskEditor.quality.filled") : t("taskEditor.quality.optional")}</StatusPill>
            </summary>
            <div className="editor-compact-details__body">
              <div className="form-grid">
                <Field label={t("taskEditor.fields.memoryLimit")}>
                  <TextInput type="number" value={form.memoryLimit} onChange={event => setForm({ ...form, memoryLimit: Number(event.target.value) })} />
                </Field>
                <Field label={t("taskEditor.fields.feedbackScope")}>
                  <TextInput
                    value={form.aiPromptDirection}
                    onChange={event => setForm({ ...form, aiPromptDirection: event.target.value })}
                    placeholder={t("taskEditor.placeholders.feedbackScope")}
                  />
                </Field>
              </div>
              <div className="knowledge-grid">
                <Field label={t("taskEditor.fields.knowledge")}>
                  <TextArea value={form.knowledgePointsText} onChange={event => setForm({ ...form, knowledgePointsText: event.target.value })} rows={3} />
                </Field>
                <Field label={t("taskEditor.fields.strategy")}>
                  <TextArea value={form.algorithmStrategiesText} onChange={event => setForm({ ...form, algorithmStrategiesText: event.target.value })} rows={3} />
                </Field>
                <Field label={t("taskEditor.fields.mistakes")}>
                  <TextArea value={form.commonMistakesText} onChange={event => setForm({ ...form, commonMistakesText: event.target.value })} rows={3} />
                </Field>
                <Field label={t("taskEditor.fields.boundaries")}>
                  <TextArea value={form.boundaryTypesText} onChange={event => setForm({ ...form, boundaryTypesText: event.target.value })} rows={3} />
                </Field>
              </div>
            </div>
          </details>
        </div>

      </section>

      {showCatalogDrawer ? (
      <section className="editor-secondary-drawer">
        <details className="editor-compact-details editor-compact-details--side">
          <summary>
            <span>{t("taskEditor.sections.saveChecks")}</span>
            <StatusPill tone={readinessTone}>{readyQualityCount}/5</StatusPill>
          </summary>
          <div className="editor-quality-list">
            {qualityItems.map(item => (
              <div className={item.ready ? "is-ready" : ""} key={item.label}>
                <span>{item.ready ? "✓" : "!"}</span>
                <div>
                  <strong>{item.label}</strong>
                  <small>{item.note}</small>
                </div>
              </div>
            ))}
          </div>
        </details>

        <details className="editor-compact-details editor-compact-details--side">
          <summary>
            <span>{t("taskEditor.sections.problemList")}</span>
            <StatusPill tone="info">{t("taskEditor.badges.problemCount", { count: catalog.length })}</StatusPill>
          </summary>
          <div className="stack editor-problem-list">
            {catalog.length ? (
              catalog.map(item => (
                <button type="button" className="list-row" key={item.id} onClick={() => void loadProblem(item.id)} style={{ textAlign: "left" }}>
                  <DifficultyPill difficulty={item.difficulty} />
                  <h3>{item.title}</h3>
                  <p>{item.summary || `${difficultyLabel(item.difficulty)} · ${item.timeLimit} ms · ${Math.round(item.memoryLimit / 1024)} MB`}</p>
                </button>
              ))
            ) : (
              <EmptyState title={t("taskEditor.empty.noProblems")} />
            )}
          </div>
        </details>
      </section>
      ) : null}

    </div>
  );
}

function splitList(value: string): string[] {
  return value
    .split(/[\n,，、;；]/)
    .map(item => item.trim())
    .filter(Boolean)
    .filter((item, index, all) => all.indexOf(item) === index)
    .slice(0, 12);
}

function joinList(value?: string[] | null): string {
  return value?.length ? value.join("\n") : "";
}
