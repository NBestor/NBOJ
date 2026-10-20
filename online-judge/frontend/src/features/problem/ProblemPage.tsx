import { lazy, Suspense, useEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, ArrowRight, BarChart3, ClipboardList, FileCheck2, GripVertical, LayoutGrid, Lightbulb, PanelRightClose, PanelRightOpen, Play, RefreshCw, RotateCcw, X } from "lucide-react";
import { api } from "../../shared/api/client";
import type {
  Assignment,
  AssignmentTask,
  AiDiagnosisProgress,
  CoachPrompt,
  LearningProof,
  Problem,
  ProblemCatalogItem,
  StudentAiFeedback,
  StudentAiFeedbackItem,
  StudentTrajectory,
  SubmissionHistorySummary,
  SubmissionResult
} from "../../shared/api/types";
import { difficultyLabel, verdictLabel } from "../../shared/format";
import { useTranslation } from "../../shared/i18n";
import { clearDraft, loadDraft, loadStudent, saveDraft, saveLastPublicProblem } from "../../shared/storage";
import { Button, ButtonLink } from "../../shared/ui/Button";
import { EmptyState } from "../../shared/ui/EmptyState";
import { Field, Select, TextArea } from "../../shared/ui/Field";
import { Panel } from "../../shared/ui/Panel";
import { DifficultyPill, StatusPill, VerdictPill } from "../../shared/ui/StatusPill";
import { StudentAssignmentHeader, StudentAssignmentNavigation } from "../student/StudentAssignmentWorkspace";
import { CONTEST_LANGUAGES, DEFAULT_CONTEST_LANGUAGE_ID, contestLanguageById } from "./languages";
import { GrowthTimeline, SingleProblemGrowthDashboard } from "../growth/SingleProblemGrowthDashboard";
import { LearningProofPanel } from "../growth/LearningProofPanel";
import { FeedbackRepairWorkbench } from "./FeedbackRepairWorkbench";
import CodeRunPanel from "./CodeRunPanel";

const CodeEditor = lazy(() => import("./CodeEditor"));

type WorkbenchTask = {
  problemId: number;
  title: string;
  difficulty?: string | null;
  orderIndex?: number;
};

type FeedbackPollState = "idle" | "checking" | "slow" | "background" | "refreshing" | "stalled";
type ResultView = "repair" | "growth";

const FEEDBACK_SLOW_AFTER_MS = 30_000;
const FEEDBACK_BACKGROUND_AFTER_MS = 95_000;
const FEEDBACK_STALLED_AFTER_MS = 15 * 60_000;

function renderInline(text: string) {
  const parts = text.split(/(`[^`]+`|\*\*[^*]+\*\*)/g).filter(Boolean);
  return parts.map((part, index) => {
    if (part.startsWith("`") && part.endsWith("`")) {
      return <code key={index}>{part.slice(1, -1)}</code>;
    }
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={index}>{part.slice(2, -2)}</strong>;
    }
    return <span key={index}>{part}</span>;
  });
}

function renderMarkdownLike(text: string) {
  const lines = text.split(/\r?\n/);
  const nodes: React.ReactNode[] = [];
  let codeBuffer: string[] = [];
  let inCode = false;

  lines.forEach((line, index) => {
    if (line.trim().startsWith("```")) {
      if (inCode) {
        nodes.push(
          <pre className="code-block" key={`code-${index}`}>
            {codeBuffer.join("\n")}
          </pre>
        );
        codeBuffer = [];
        inCode = false;
      } else {
        inCode = true;
      }
      return;
    }
    if (inCode) {
      codeBuffer.push(line);
      return;
    }
    if (line.startsWith("### ")) {
      nodes.push(<h3 key={index}>{renderInline(line.slice(4))}</h3>);
      return;
    }
    if (line.startsWith("## ")) {
      nodes.push(<h2 key={index}>{renderInline(line.slice(3))}</h2>);
      return;
    }
    if (line.startsWith("# ")) {
      nodes.push(<h1 key={index}>{renderInline(line.slice(2))}</h1>);
      return;
    }
    if (!line.trim()) {
      return;
    }
    nodes.push(<p key={index}>{renderInline(line)}</p>);
  });

  if (codeBuffer.length) {
    nodes.push(
      <pre className="code-block" key="code-tail">
        {codeBuffer.join("\n")}
      </pre>
    );
  }
  return nodes;
}

function visibleAssignmentTitle(assignment?: Assignment | null) {
  if (!assignment) {
    return "课堂作业";
  }
  return assignment.title.includes("试点任务") ? "课堂编程作业" : assignment.title;
}

function isDiagnosticOutput(value?: string | null) {
  const text = (value || "").trim();
  if (!text) {
    return false;
  }
  return (
    text.length > 180 ||
    /(Traceback \(most recent call last\)|ValueError|TypeError|IndexError|RuntimeError|Exception|Segmentation fault|AddressSanitizer|NullPointerException|NumberFormatException|line \d+, in|error:)/i.test(text)
  );
}

function outputPreview(value?: string | null) {
  const text = (value || "").trim();
  if (!text) {
    return "(空输出)";
  }
  if (isDiagnosticOutput(text)) {
    return "程序运行时异常，展开原始错误查看细节。";
  }
  return text;
}

function taskFromAssignment(task: AssignmentTask): WorkbenchTask {
  return {
    problemId: task.problemId,
    title: task.title,
    difficulty: task.difficulty,
    orderIndex: task.orderIndex
  };
}

function taskFromCatalog(problem: ProblemCatalogItem): WorkbenchTask {
  return {
    problemId: problem.id,
    title: problem.title,
    difficulty: problem.difficulty,
    orderIndex: problem.id
  };
}

function normalizeNumber(value: string | null | undefined) {
  if (!value) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function initialSourceFor(problem: Problem | null, problemId: number, languageId: number) {
  const draft = loadDraft(problemId, languageId);
  const languageTemplate = contestLanguageById(languageId).template;
  if (problem?.starterCode?.trim()) {
    if (draft !== null && !isLegacyStarterDraft(draft, languageTemplate)) {
      return draft;
    }
    return problem.starterCode;
  }
  if (draft !== null) {
    return draft;
  }
  return languageTemplate;
}

function defaultSourceFor(problem: Problem | null, languageId: number) {
  return problem?.starterCode?.trim() ? problem.starterCode : contestLanguageById(languageId).template;
}

function isLegacyStarterDraft(draft: string, languageTemplate: string) {
  const normalized = draft.replace(/\r\n/g, "\n").replace(/\r/g, "\n").trim();
  return (
    normalized === languageTemplate.trim() ||
    normalized === "n = int(input())\n# 在这里计算 1 到 n 的和" ||
    isPreviousPublicSeedStarterDraft(normalized)
  );
}

function isPreviousPublicSeedStarterDraft(normalized: string) {
  return (
    (
      normalized.includes("def solve_tree(n, k, values, children):") &&
      normalized.includes("current = [NEG, values[node]]") &&
      normalized.includes("return dfs(0)[k]")
    ) ||
    (
      normalized.includes("def current_cost(left, right):") &&
      normalized.includes("average = sum(merged) // len(merged)") &&
      normalized.includes("remove_value(left, right, arr[i - k])")
    ) ||
    (
      normalized.includes("def shortest(n, k, graph):") &&
      normalized.includes("best_node = [INF] * (n + 1)") &&
      normalized.includes("visited = [False] * (n + 1)")
    ) ||
    (
      normalized.includes("def greedy_schedule(n, durations, edges):") &&
      normalized.includes("while available and len(running) < 2:") &&
      normalized.includes("heapq.heappush(available, (-durations[task], task))")
    )
  );
}

function terminalFeedbackStatus(status?: string | null) {
  return ["READY", "TIMEOUT", "FAILED", "SAFETY_REJECTED"].includes(String(status || "").toUpperCase());
}

function inFlightFeedbackStatus(status?: string | null) {
  return ["GENERATING", "NOT_REQUESTED"].includes(String(status || "").toUpperCase());
}

function normalizeFailureReason(reason?: string | null) {
  const text = String(reason || "").trim();
  if (!text) {
    return "";
  }
  return text
    .split(",")
    .map(item => item.trim())
    .filter(Boolean)[0] || text;
}

function feedbackFailureReason(feedback?: StudentAiFeedback | null) {
  return normalizeFailureReason(feedback?.failureReason || feedback?.safety?.blockedReasons?.find(Boolean));
}

function withLookupFailureReason(feedback: StudentAiFeedback | null, failureReason?: string | null) {
  if (!feedback || feedback.failureReason || !failureReason) {
    return feedback;
  }
  return { ...feedback, failureReason };
}

function modelFailureTitle(reason: string | null | undefined, t: (key: string) => string) {
  const normalized = normalizeFailureReason(reason).toUpperCase();
  switch (normalized) {
    case "TIMEOUT":
      return t("diagnosisFailure.takingLongerTitle");
    case "OUTPUT_TRUNCATED":
    case "INVALID_JSON":
    case "FULL_CHAIN_FEEDBACK_EMPTY":
      return t("diagnosisFailure.incompleteTitle");
    case "SAFETY_RISK":
    case "SAFETY_REJECTED":
      return t("diagnosisFailure.reviewTitle");
    default:
      return t("diagnosisFailure.unavailableTitle");
  }
}

function modelFailureMessage(reason: string | null | undefined, t: (key: string) => string) {
  const normalized = normalizeFailureReason(reason).toUpperCase();
  switch (normalized) {
    case "TIMEOUT":
      return t("diagnosisFailure.takingLongerMessage");
    case "OUTPUT_TRUNCATED":
    case "INVALID_JSON":
    case "FULL_CHAIN_FEEDBACK_EMPTY":
      return t("diagnosisFailure.incompleteMessage");
    case "SAFETY_RISK":
    case "SAFETY_REJECTED":
      return t("diagnosisFailure.reviewMessage");
    default:
      return t("diagnosisFailure.unavailableMessage");
  }
}

function feedbackPollingDelay(elapsedMs: number) {
  if (elapsedMs < 12_000) {
    return 1_200;
  }
  if (elapsedMs < FEEDBACK_SLOW_AFTER_MS) {
    return 2_200;
  }
  return 5_000;
}

function feedbackTextWeight(items: Array<{ body?: string | null; title?: string | null }>) {
  return items.reduce((total, item) => total + (item.title?.length || 0) + (item.body?.length || 0), 0);
}

function evidenceLineFromItem(item?: StudentAiFeedbackItem | null) {
  const snippetLine = item?.evidenceSnippets?.find(snippet => snippet.lineNumber)?.lineNumber;
  if (snippetLine) {
    return snippetLine;
  }
  const ref = item?.evidenceRefs?.find(value => /^code:line:\d+$/.test(value || ""));
  return ref ? Number(ref.split(":").pop()) : null;
}

function evidenceRefKindKey(ref: string) {
  if (ref.startsWith("judge:")) {
    return "feedbackMeta.evidenceKinds.judge";
  }
  if (ref.startsWith("problem:")) {
    return "feedbackMeta.evidenceKinds.problem";
  }
  if (ref.startsWith("verdict:")) {
    return "feedbackMeta.evidenceKinds.verdict";
  }
  if (ref.startsWith("source:")) {
    return "feedbackMeta.evidenceKinds.source";
  }
  return "feedbackMeta.evidenceKinds.analysis";
}

function splitFeedbackSentence(text: string, maxChars = 74) {
  const sentence = text.trim();
  if (sentence.length <= maxChars) {
    return sentence ? [sentence] : [];
  }
  const softParts = sentence.match(/[^，,、]+[，,、]?/g)?.map(part => part.trim()).filter(Boolean) || [sentence];
  const chunks: string[] = [];
  let current = "";
  softParts.forEach(part => {
    if (!current) {
      current = part;
      return;
    }
    if ((current + part).length <= maxChars) {
      current += part;
      return;
    }
    chunks.push(current);
    current = part;
  });
  if (current) {
    chunks.push(current);
  }
  return chunks.flatMap(chunk => {
    if (chunk.length <= maxChars + 10) {
      return [chunk];
    }
    const hardChunks: string[] = [];
    for (let start = 0; start < chunk.length; start += maxChars) {
      hardChunks.push(chunk.slice(start, start + maxChars));
    }
    return hardChunks;
  });
}

function feedbackTextLines(text?: string | null, maxLines = 4) {
  const cleaned = (text || "").replace(/\s+/g, " ").trim();
  if (!cleaned) {
    return [];
  }
  const sentenceParts = cleaned.match(/[^。！？；;]+[。！？；;]?/g)?.map(part => part.trim()).filter(Boolean) || [cleaned];
  const lines = sentenceParts.flatMap(part => splitFeedbackSentence(part));
  if (lines.length <= maxLines) {
    return lines;
  }
  return [...lines.slice(0, maxLines - 1), lines.slice(maxLines - 1).join("")];
}

function FeedbackTextBlock({ text, maxLines = 4 }: { text?: string | null; maxLines?: number }) {
  const lines = feedbackTextLines(text, maxLines);
  if (!lines.length) {
    return null;
  }
  return (
    <div className="student-feedback-text">
      {lines.map((line, index) => (
        <p key={`${line}-${index}`}>{line}</p>
      ))}
    </div>
  );
}

function FeedbackLifecycleBadges({ item }: { item?: StudentAiFeedbackItem | null }) {
  const { t } = useTranslation();
  const status = String(item?.changeStatus || "").toUpperCase();
  const labels = item?.personalLabels || [];
  if (!status && !labels.length) {
    return null;
  }
  return (
    <div className="student-feedback-lifecycle-badges">
      {status ? <span className={`is-${status.toLowerCase()}`}>{t(`issueLifecycle.status.${lifecycleStatusKey(status)}`)}</span> : null}
      {labels.map(label => (
        <span className="is-label" key={label}>{t(`issueLifecycle.label.${lifecycleLabelKey(label)}`)}</span>
      ))}
      {typeof item?.effectiveOccurrenceCount === "number" ? (
        <span className="is-count">{t("issueLifecycle.effectiveCount", { count: item.effectiveOccurrenceCount })}</span>
      ) : null}
      {typeof item?.consecutiveEffectiveCount === "number" && item.consecutiveEffectiveCount > 1 ? (
        <span className="is-count">{t("issueLifecycle.consecutiveCount", { count: item.consecutiveEffectiveCount })}</span>
      ) : null}
    </div>
  );
}

function lifecycleStatusKey(status?: string | null) {
  const normalized = String(status || "").toUpperCase();
  return ["NEW", "PERSISTED", "NOT_OBSERVED", "RECOVERED", "RECURRED", "UNCOMPARABLE"].includes(normalized)
    ? normalized.toLowerCase()
    : "uncomparable";
}

function lifecycleLabelKey(label?: string | null) {
  switch (String(label || "").toUpperCase()) {
    case "PERSISTENT_DIFFICULTY":
      return "persistentDifficulty";
    case "RECURRING_ERROR":
      return "recurringError";
    case "CROSS_PROBLEM_WEAKNESS":
      return "crossProblemWeakness";
    case "RECOVERED":
      return "recovered";
    case "SINGLE_OBSERVATION":
      return "singleObservation";
    case "OBSERVING":
      return "observing";
    default:
      return "evidenceInsufficient";
  }
}

function FeedbackEvidenceMeta({
  item,
  onJumpToLine,
  showEmptyEvidence = false
}: {
  item?: StudentAiFeedbackItem | null;
  onJumpToLine: (line: number) => void;
  showEmptyEvidence?: boolean;
}) {
  const { t } = useTranslation();
  const knowledgePath = item?.knowledgePath?.filter(Boolean) || [];
  const pathStatus = (item?.knowledgePathStatus || (knowledgePath.length ? "INFERRED" : "UNCLASSIFIED")).toUpperCase();
  const pathStatusKey = ["FORMAL", "PROVISIONAL", "INFERRED", "UNCLASSIFIED"].includes(pathStatus)
    ? pathStatus.toLowerCase()
    : "unclassified";
  const snippets = item?.evidenceSnippets?.filter(snippet => snippet.code && snippet.lineNumber) || [];
  const evidenceRefs = item?.evidenceRefs?.filter(Boolean) || [];
  const fallbackLine = evidenceLineFromItem(item);
  if (!knowledgePath.length && !snippets.length && !fallbackLine && !evidenceRefs.length && !showEmptyEvidence) {
    return null;
  }
  return (
    <div className={`student-feedback-meta-row student-feedback-meta-row--${pathStatusKey}`}>
      <div className="student-feedback-meta-segment student-feedback-meta-segment--knowledge" aria-label={t("feedbackMeta.knowledgePathAria")}>
        <span className="student-feedback-meta__label">{t("feedbackMeta.knowledgePath")}</span>
        {knowledgePath.length ? (
          <nav className="student-feedback-meta-path" aria-label={t("feedbackMeta.knowledgePathAria")}>
            {knowledgePath.map((segment, index) => (
              <span key={`${segment}-${index}`}>
                {index > 0 ? <i aria-hidden="true">›</i> : null}
                <b>{segment}</b>
              </span>
            ))}
          </nav>
        ) : (
          <span className="student-feedback-meta-empty">{t("feedbackMeta.noKnowledgePath")}</span>
        )}
        <span className="student-feedback-path-status">{t(`feedbackMeta.pathStatus.${pathStatusKey}`)}</span>
      </div>
      {snippets.length ? (
        <div className="student-feedback-meta-segment student-feedback-meta-segment--evidence" aria-label={t("feedbackMeta.codeEvidenceAria")}>
          <span className="student-feedback-meta__label">{t("feedbackMeta.codeEvidence")}</span>
          {snippets.map(snippet => (
            <button
              type="button"
              className="student-feedback-meta-action"
              key={snippet.evidenceRef || `${snippet.lineNumber}-${snippet.code}`}
              title={t("feedbackMeta.jumpToCode")}
              onClick={() => snippet.lineNumber && onJumpToLine(snippet.lineNumber)}
            >
              <span>{snippet.lineEnd && snippet.lineEnd !== snippet.lineNumber
                ? t("feedbackMeta.lineRange", { start: snippet.lineNumber || "", end: snippet.lineEnd })
                : t("feedbackMeta.line", { line: snippet.lineNumber || "" })}</span>
              <code>{snippet.code}</code>
            </button>
          ))}
        </div>
      ) : fallbackLine ? (
        <div className="student-feedback-meta-segment student-feedback-meta-segment--evidence" aria-label={t("feedbackMeta.codeEvidenceAria")}>
          <span className="student-feedback-meta__label">{t("feedbackMeta.codeEvidence")}</span>
          <button className="student-feedback-meta-action" type="button" title={t("feedbackMeta.jumpToCode")} onClick={() => onJumpToLine(fallbackLine)}>
            <span>{t("feedbackMeta.line", { line: fallbackLine })}</span>
            <code>{t("feedbackMeta.viewCode")}</code>
          </button>
        </div>
      ) : evidenceRefs.length ? (
        <div className="student-feedback-meta-segment student-feedback-meta-segment--evidence" aria-label={t("feedbackMeta.evidenceBasis")}>
          <span className="student-feedback-meta__label">{t("feedbackMeta.evidenceBasis")}</span>
          <em>{t("feedbackMeta.noJumpableLine", {
            evidence: Array.from(new Set(evidenceRefs.map(ref => t(evidenceRefKindKey(ref))))).join(t("feedbackMeta.listSeparator"))
          })}</em>
        </div>
      ) : (
        <div className="student-feedback-meta-segment student-feedback-meta-segment--evidence" aria-label={t("feedbackMeta.codeEvidenceAria")}>
          <span className="student-feedback-meta__label">{t("feedbackMeta.codeEvidence")}</span>
          <em>{t("feedbackMeta.noCodeEvidence")}</em>
        </div>
      )}
    </div>
  );
}

function FeedbackLoadingPanel({
  state,
  progress
}: {
  mode: "repair" | "growth";
  state: FeedbackPollState;
  progress?: AiDiagnosisProgress | null;
}) {
  const { t } = useTranslation();
  const steps = [
    t("diagnosisProgress.stages.understanding"),
    t("diagnosisProgress.stages.matching"),
    t("diagnosisProgress.stages.generating"),
    t("diagnosisProgress.stages.verifying")
  ];
  const isSlow = state === "slow";
  const isBackground = state === "background";
  const title = progress?.retrying
    ? t("diagnosisProgress.retrying")
    : state === "refreshing"
      ? t("diagnosisProgress.refreshing")
      : isBackground
        ? t("diagnosisProgress.background")
        : isSlow
          ? t("diagnosisProgress.stillWorking")
          : t("diagnosisProgress.processing");
  const note = isBackground
    ? t("diagnosisProgress.backgroundNote")
    : isSlow
      ? t("diagnosisProgress.slowNote")
      : null;
  const stageIndex = {
    UNDERSTANDING_EVIDENCE: 0,
    MATCHING_KNOWLEDGE_PATHS: 1,
    GENERATING_COMPLETE_FEEDBACK: 2,
    VERIFYING_EVIDENCE_AND_SAFETY: 3,
    COMPLETED: 3
  }[String(progress?.stage || "UNDERSTANDING_EVIDENCE").toUpperCase()] ?? 0;
  const completed = progress?.completedStages || 0;
  const total = progress?.totalStages || 0;

  return (
    <div className="student-feedback-loading" aria-live="polite">
      <div className="student-feedback-loading__head">
        <span className="student-feedback-loading__spinner" aria-hidden="true" />
        <strong>{title}</strong>
      </div>
      {note && <p className="student-feedback-loading__note">{note}</p>}
      {total > 0 && (
        <p className="student-feedback-loading__note">
          {t("diagnosisProgress.stageCount", { completed, total })}
        </p>
      )}
      <div className="student-feedback-loading__steps">
        {steps.map((step, index) => (
          <span className={`is-step-${index + 1}${index <= stageIndex ? " is-active" : ""}`} key={step}>
            {step}
          </span>
        ))}
      </div>
      <div className="student-feedback-loading__bar" aria-hidden="true">
        <span />
      </div>
    </div>
  );
}

export default function ProblemPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const params = useParams();
  const [searchParams] = useSearchParams();
  const problemId = Number(params.problemId);
  const routeAssignmentParam = params.assignmentId;
  const routeAssignmentId = routeAssignmentParam && routeAssignmentParam !== "public" ? normalizeNumber(routeAssignmentParam) : null;
  const queryAssignmentId = normalizeNumber(searchParams.get("assignmentId"));
  const assignmentId = queryAssignmentId ?? routeAssignmentId;
  const isPublicWorkbench = routeAssignmentParam === "public" || !assignmentId;
  const studentFromAssignment = assignmentId ? loadStudent(assignmentId) : null;
  const studentFromAny = loadStudent();
  const currentStudent = studentFromAssignment || studentFromAny;
  const studentProfileId = normalizeNumber(searchParams.get("studentProfileId")) ?? currentStudent?.id ?? null;
  const recommendationToken = searchParams.get("recommendationToken");
  const requestedSubmissionId = normalizeNumber(searchParams.get("submissionId"));
  const assignmentBasePath = assignmentId ? `/student/assignments/${assignmentId}` : "/student/assignments/public";
  const backTo = assignmentBasePath;
  const backLabel = isPublicWorkbench ? "返回题目列表" : "返回学生端";

  const [problem, setProblem] = useState<Problem | null>(null);
  const [languageId, setLanguageId] = useState(DEFAULT_CONTEST_LANGUAGE_ID);
  const [sourceCode, setSourceCode] = useState(() => contestLanguageById(DEFAULT_CONTEST_LANGUAGE_ID).template);
  const [evidenceFocus, setEvidenceFocus] = useState<{ line: number | null; nonce: number }>({ line: null, nonce: 0 });
  const [editorResetVersion, setEditorResetVersion] = useState(0);
  const [latest, setLatest] = useState<SubmissionResult | null>(null);
  const [history, setHistory] = useState<SubmissionHistorySummary[]>([]);
  const [trajectory, setTrajectory] = useState<StudentTrajectory | null>(null);
  const [assignmentTitle, setAssignmentTitle] = useState(isPublicWorkbench ? "公共题库" : "课堂作业");
  const [assignmentContext, setAssignmentContext] = useState<Assignment | null>(null);
  const [workbenchTasks, setWorkbenchTasks] = useState<WorkbenchTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [resultOpen, setResultOpen] = useState(false);
  const [resultView, setResultView] = useState<ResultView>("repair");
  const [coachPrompt, setCoachPrompt] = useState<CoachPrompt | null>(null);
  const [learningProof, setLearningProof] = useState<LearningProof | null>(null);
  const [learningProofBusy, setLearningProofBusy] = useState(false);
  const [coachAnswer, setCoachAnswer] = useState("");
  const [alert, setAlert] = useState<{ type: "success" | "error"; message: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [runBusy, setRunBusy] = useState(false);
  const [studentAiFeedback, setStudentAiFeedback] = useState<StudentAiFeedback | null>(null);
  const [diagnosisProgress, setDiagnosisProgress] = useState<AiDiagnosisProgress | null>(null);
  const [feedbackPollState, setFeedbackPollState] = useState<FeedbackPollState>("idle");
  const [coachBusy, setCoachBusy] = useState(false);
  const [coachReplyBusy, setCoachReplyBusy] = useState(false);
  const [statementShare, setStatementShare] = useState(50);
  const [editorCollapsed, setEditorCollapsed] = useState(false);
  const splitRef = useRef<HTMLDivElement | null>(null);
  const viewedFeedbackIdsRef = useRef<Set<number>>(new Set());
  const feedbackPollTokenRef = useRef(0);
  const feedbackTimerRef = useRef<number | null>(null);
  const resultOpenRef = useRef(false);

  useEffect(() => {
    resultOpenRef.current = resultOpen;
    if (resultOpen) {
      setResultView("repair");
    }
  }, [resultOpen]);

  useEffect(() => {
    if (!resultOpen) {
      return;
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setResultOpen(false);
      }
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [resultOpen]);

  useEffect(() => {
    if (!resultOpen || !latest) {
      return;
    }
    const root = document.documentElement;
    const previousRootOverflow = root.style.overflow;
    const previousOverflow = document.body.style.overflow;
    root.style.overflow = "hidden";
    document.body.style.overflow = "hidden";
    return () => {
      root.style.overflow = previousRootOverflow;
      document.body.style.overflow = previousOverflow;
    };
  }, [latest, resultOpen]);

  useEffect(() => {
    let ignore = false;
    if (!resultOpen || !latest?.id || resultView !== "growth") {
      return;
    }
    api.learningProof(latest.id)
      .then(result => {
        if (!ignore) setLearningProof(result);
      })
      .catch(() => {
        if (!ignore) setLearningProof(null);
      });
    return () => {
      ignore = true;
    };
  }, [latest?.id, resultOpen, resultView]);

  useEffect(() => {
    return () => {
      feedbackPollTokenRef.current += 1;
      clearFeedbackTimer();
    };
  }, []);

  useEffect(() => {
    if (isPublicWorkbench && Number.isFinite(problemId)) {
      saveLastPublicProblem(problemId);
    }
  }, [isPublicWorkbench, problemId]);

  useEffect(() => {
    let ignore = false;
    async function load() {
      try {
        setLatest(null);
        setResultOpen(false);
        setCoachPrompt(null);
        setLearningProof(null);
        setCoachAnswer("");
        setAlert(null);
        feedbackPollTokenRef.current += 1;
        clearFeedbackTimer();
        setFeedbackPollState("idle");
        setStudentAiFeedback(null);
        setDiagnosisProgress(null);
        const [problemResult, historyResult] = await Promise.all([api.problem(problemId), api.history(problemId, assignmentId)]);
        if (ignore) {
          return;
        }
        setProblem(problemResult);
        setHistory(historyResult);
        setSourceCode(initialSourceFor(problemResult, problemId, languageId));
        const selectedSubmissionId = requestedSubmissionId ?? historyResult[0]?.id ?? null;
        if (selectedSubmissionId) {
          const [submissionResult, feedbackLookup] = await Promise.all([
            api.submission(selectedSubmissionId),
            api.studentAiFeedback(selectedSubmissionId)
          ]);
          if (!ignore) {
            setLatest(submissionResult);
            const feedback = withLookupFailureReason(feedbackLookup.feedback || null, feedbackLookup.failureReason);
            setStudentAiFeedback(feedback);
            setDiagnosisProgress(feedbackLookup.diagnosisProgress || null);
            if (requestedSubmissionId) {
              setResultOpen(true);
            }
            if (inFlightFeedbackStatus(feedback?.status)) {
              const token = startFeedbackPollingState(true);
              pollStudentAiFeedback(selectedSubmissionId, token);
            }
          }
        }
      } catch (error) {
        if (!ignore) {
          setAlert({ type: "error", message: error instanceof Error ? error.message : "题目加载失败。" });
        }
      }
    }
    if (Number.isFinite(problemId)) {
      void load();
    }
    return () => {
      ignore = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [assignmentId, problemId, requestedSubmissionId]);

  useEffect(() => {
    setSourceCode(initialSourceFor(problem, problemId, languageId));
  }, [languageId, problem, problemId]);

  useEffect(() => {
    let ignore = false;
    setTasksLoading(true);

    if (isPublicWorkbench) {
      setAssignmentContext(null);
      api.problemCatalog()
        .then(result => {
          if (!ignore) {
            setAssignmentTitle("公共题库");
            setWorkbenchTasks(result.map(taskFromCatalog));
          }
        })
        .catch(() => {
          if (!ignore) {
            setWorkbenchTasks([]);
          }
        })
        .finally(() => {
          if (!ignore) {
            setTasksLoading(false);
          }
        });
      return () => {
        ignore = true;
      };
    }

    if (!assignmentId) {
      setWorkbenchTasks([]);
      setTasksLoading(false);
      return () => {
        ignore = true;
      };
    }

    const currentAssignmentId = assignmentId;

    async function loadAssignmentTasks() {
      try {
        let assignment: Assignment | null = null;
        if (studentProfileId) {
          const assignments = await api.studentAssignments(studentProfileId);
          assignment = assignments.find(item => item.id === currentAssignmentId) || null;
        }
        if (!ignore) {
          if (assignment) {
            setAssignmentContext(assignment);
            setAssignmentTitle(visibleAssignmentTitle(assignment));
            setWorkbenchTasks(assignment.tasks.map(taskFromAssignment));
          } else {
            setAssignmentContext(null);
            setAssignmentTitle("课堂作业");
            setWorkbenchTasks([]);
          }
        }
      } catch {
        if (!ignore) {
          setAssignmentContext(null);
          setWorkbenchTasks([]);
        }
      } finally {
        if (!ignore) {
          setTasksLoading(false);
        }
      }
    }

    void loadAssignmentTasks();
    return () => {
      ignore = true;
    };
  }, [assignmentId, isPublicWorkbench, studentProfileId]);

  useEffect(() => {
    if (!assignmentId || !studentProfileId) {
      setTrajectory(null);
      return;
    }
    void api.studentTrajectory(assignmentId, studentProfileId).then(setTrajectory).catch(() => undefined);
  }, [assignmentId, studentProfileId, latest]);

  useEffect(() => {
    if (!studentProfileId || !recommendationToken) {
      return;
    }
    void api.recordRecommendationEvent(studentProfileId, recommendationToken, "ENTERED_PROBLEM");
  }, [studentProfileId, recommendationToken]);

  useEffect(() => {
    setCoachPrompt(null);
    setCoachAnswer("");
    if (!latest?.id || studentAiFeedback?.status !== "READY" || studentAiFeedback.source !== "MODEL") {
      return;
    }
    void api.coachPrompt(latest.id).then(result => setCoachPrompt(result || null)).catch(() => undefined);
  }, [latest?.id, studentAiFeedback?.status, studentAiFeedback?.source]);

  useEffect(() => {
    if (!resultOpen || !latest?.id || studentAiFeedback?.status !== "READY" || studentAiFeedback.source !== "MODEL") {
      return;
    }
    if (viewedFeedbackIdsRef.current.has(latest.id)) {
      return;
    }
    viewedFeedbackIdsRef.current.add(latest.id);
    void api.recordStudentAiFeedbackView(latest.id).catch(() => undefined);
  }, [resultOpen, latest?.id, studentAiFeedback?.status, studentAiFeedback?.source]);

  const trajectoryTaskByProblemId = useMemo(() => {
    const tasks = new Map<number, StudentTrajectory["tasks"][number]>();
    trajectory?.tasks.forEach(task => tasks.set(task.problemId, task));
    return tasks;
  }, [trajectory]);

  const taskRows = useMemo(
    () =>
      workbenchTasks.map(task => {
        const state = trajectoryTaskByProblemId.get(task.problemId);
        return {
          ...task,
          attemptCount: state?.attemptCount || 0,
          passed: Boolean(state?.passed || state?.latestVerdict === "ACCEPTED"),
          latestVerdict: state?.latestVerdict || null,
          latestHint: state?.latestHint || state?.latestProgressSignal || null
        };
      }),
    [trajectoryTaskByProblemId, workbenchTasks]
  );

  const currentTaskIndex = taskRows.findIndex(task => task.problemId === problemId);
  const nextTask = currentTaskIndex >= 0 ? taskRows[currentTaskIndex + 1] || null : taskRows.find(task => task.problemId !== problemId) || null;

  function buildTaskLink(nextProblemId: number) {
    const studentParam = studentProfileId ? `?studentProfileId=${studentProfileId}` : "";
    if (assignmentId) {
      return `/student/assignments/${assignmentId}/problems/${nextProblemId}${studentParam}`;
    }
    return `/student/assignments/public/problems/${nextProblemId}${studentParam}`;
  }

  function updateStatementShare(clientX: number) {
    const split = splitRef.current;
    if (!split) return;
    const bounds = split.getBoundingClientRect();
    const nextShare = ((clientX - bounds.left) / bounds.width) * 100;
    setStatementShare(Math.min(68, Math.max(32, nextShare)));
  }

  function startPanelResize(event: ReactPointerEvent<HTMLDivElement>) {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    const handleMove = (moveEvent: PointerEvent) => updateStatementShare(moveEvent.clientX);
    const handleEnd = () => {
      window.removeEventListener("pointermove", handleMove);
      window.removeEventListener("pointerup", handleEnd);
    };
    window.addEventListener("pointermove", handleMove);
    window.addEventListener("pointerup", handleEnd, { once: true });
  }

  function resizeWithKeyboard(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    setStatementShare(current => Math.min(68, Math.max(32, current + (event.key === "ArrowRight" ? 4 : -4))));
  }

  function updateCode(value: string) {
    setSourceCode(value);
    if (Number.isFinite(problemId)) {
      saveDraft(problemId, languageId, value);
    }
  }

  function jumpToEvidenceLine(line: number) {
    setEvidenceFocus(current => ({ line, nonce: current.nonce + 1 }));
    setResultOpen(false);
    window.setTimeout(() => document.getElementById("code-workbench")?.scrollIntoView({ behavior: "smooth", block: "center" }), 40);
  }

  function resetCode() {
    setSourceCode(defaultSourceFor(problem, languageId));
    if (Number.isFinite(problemId)) {
      clearDraft(problemId, languageId);
    }
    setEditorResetVersion(version => version + 1);
  }

  function closeResult() {
    if (requestedSubmissionId && assignmentId) {
      navigate(`${assignmentBasePath}/submissions`);
      return;
    }
    setResultOpen(false);
  }

  async function submit() {
    if (!problem) {
      return;
    }
    if (!sourceCode.trim()) {
      setAlert({ type: "error", message: "请先写代码再提交。" });
      return;
    }
    setBusy(true);
    try {
      const result = await api.submit({
        problemId: problem.id,
        assignmentId,
        studentProfileId,
        recommendationToken,
        languageId,
        sourceCode
      });
      setLatest(result);
      setResultOpen(true);
      setCoachPrompt(null);
      setLearningProof(null);
      setStudentAiFeedback(null);
      setDiagnosisProgress(null);
      const feedbackToken = startFeedbackPollingState(Boolean(result.id));
      setHistory(await api.history(problem.id, assignmentId));
      setAlert(
        result.verdict === "INTERNAL_ERROR"
          ? { type: "error", message: result.errorMessage || "执行环境未就绪。" }
          : null
      );
      if (result.id) {
        void api
          .triggerStudentAiFeedback(result.id)
          .then(lookup => {
            setDiagnosisProgress(lookup.diagnosisProgress || null);
            return handleFeedbackLookup(withLookupFailureReason(lookup.feedback || null, lookup.failureReason), feedbackToken);
          })
          .catch(() => undefined);
        pollStudentAiFeedback(result.id, feedbackToken);
      }
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "提交失败。" });
    } finally {
      setBusy(false);
    }
  }

  function clearFeedbackTimer() {
    if (feedbackTimerRef.current !== null) {
      window.clearTimeout(feedbackTimerRef.current);
      feedbackTimerRef.current = null;
    }
  }

  function startFeedbackPollingState(active: boolean) {
    feedbackPollTokenRef.current += 1;
    clearFeedbackTimer();
    setFeedbackPollState(active ? "checking" : "idle");
    return feedbackPollTokenRef.current;
  }

  function handleFeedbackLookup(feedback: StudentAiFeedback | null, token: number) {
    if (token !== feedbackPollTokenRef.current) {
      return true;
    }
    if (feedback) {
      setStudentAiFeedback(feedback);
    }
    if (feedback && terminalFeedbackStatus(feedback.status)) {
      setFeedbackPollState("idle");
      if (feedback.status === "READY" && resultOpenRef.current) {
        setResultOpen(true);
      }
      return true;
    }
    return false;
  }

  function pollStudentAiFeedback(id: number, token: number, startedAt = Date.now()) {
    const elapsedMs = Date.now() - startedAt;
    const delay = elapsedMs <= 0 ? 350 : feedbackPollingDelay(elapsedMs);
    feedbackTimerRef.current = window.setTimeout(async () => {
      if (token !== feedbackPollTokenRef.current) {
        return;
      }
      try {
        const lookup = await api.studentAiFeedback(id);
        setDiagnosisProgress(lookup.diagnosisProgress || null);
        const feedback = withLookupFailureReason(lookup.feedback || null, lookup.failureReason);
        if (handleFeedbackLookup(feedback, token)) {
          return;
        }
      } catch {
        // AI 快反馈失败不阻塞学生继续修改代码。
      }
      if (token !== feedbackPollTokenRef.current) {
        return;
      }
      const nextElapsedMs = Date.now() - startedAt;
      if (nextElapsedMs >= FEEDBACK_STALLED_AFTER_MS) {
        setFeedbackPollState("stalled");
        return;
      }
      if (nextElapsedMs >= FEEDBACK_BACKGROUND_AFTER_MS) {
        setFeedbackPollState("background");
      } else {
        setFeedbackPollState(nextElapsedMs >= FEEDBACK_SLOW_AFTER_MS ? "slow" : "checking");
      }
      pollStudentAiFeedback(id, token, startedAt);
    }, delay);
  }

  async function refreshStudentAiFeedback() {
    if (!latest?.id) {
      return;
    }
    const shouldRetry =
      terminalFeedbackStatus(studentAiFeedback?.status) &&
      (studentAiFeedback?.status !== "READY" || String(studentAiFeedback?.source || "").toUpperCase() === "RULE_FALLBACK");
    const feedbackToken = startFeedbackPollingState(true);
    setFeedbackPollState("refreshing");
    try {
      const lookup = shouldRetry ? await api.triggerStudentAiFeedback(latest.id) : await api.studentAiFeedback(latest.id);
      setDiagnosisProgress(lookup.diagnosisProgress || null);
      const feedback = withLookupFailureReason(lookup.feedback || null, lookup.failureReason);
      if (handleFeedbackLookup(feedback, feedbackToken)) {
        return;
      }
      setFeedbackPollState(inFlightFeedbackStatus(feedback?.status) ? "checking" : "background");
      pollStudentAiFeedback(latest.id, feedbackToken);
    } catch {
      if (feedbackToken === feedbackPollTokenRef.current) {
        setFeedbackPollState("background");
      }
    }
  }

  async function openHistorySubmission(summary: SubmissionHistorySummary) {
    const token = startFeedbackPollingState(false);
    setCoachPrompt(null);
    setLearningProof(null);
    setCoachAnswer("");
    try {
      const [submissionResult, feedbackLookup] = await Promise.all([
        api.submission(summary.id),
        api.studentAiFeedback(summary.id)
      ]);
      if (token !== feedbackPollTokenRef.current) {
        return;
      }
      setLatest(submissionResult);
      const feedback = withLookupFailureReason(feedbackLookup.feedback || null, feedbackLookup.failureReason);
      setStudentAiFeedback(feedback);
      setDiagnosisProgress(feedbackLookup.diagnosisProgress || null);
      setResultOpen(true);
      if (inFlightFeedbackStatus(feedback?.status)) {
        const pollingToken = startFeedbackPollingState(true);
        pollStudentAiFeedback(summary.id, pollingToken);
      }
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("problemHistory.loadFailed") });
    }
  }

  async function createLearningReflection() {
    if (!latest?.id) return;
    setLearningProofBusy(true);
    try {
      setLearningProof(await api.createLearningReflection(latest.id));
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("learningProof.error") });
    } finally {
      setLearningProofBusy(false);
    }
  }

  async function answerLearningReflection(answer: string) {
    if (!latest?.id) return;
    setLearningProofBusy(true);
    try {
      setLearningProof(await api.answerLearningReflection(latest.id, answer));
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("learningProof.error") });
    } finally {
      setLearningProofBusy(false);
    }
  }

  async function generateCoachPrompt() {
    if (!latest?.id) {
      return;
    }
    if (studentAiFeedback?.status !== "READY" || studentAiFeedback.source !== "MODEL") {
      setAlert({ type: "error", message: "AI 反馈生成后才能追问。" });
      return;
    }
    setCoachBusy(true);
    try {
      setCoachPrompt(await api.generateCoachPrompt(latest.id));
      setCoachAnswer("");
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "追问生成失败。" });
    } finally {
      setCoachBusy(false);
    }
  }

  async function replyCoachPrompt() {
    if (!latest?.id || !coachPrompt) {
      return;
    }
    if (!coachAnswer.trim()) {
      setAlert({ type: "error", message: "先写一句你的判断或验证样例。" });
      return;
    }
    setCoachReplyBusy(true);
    try {
      setCoachPrompt(await api.replyCoachPrompt(latest.id, coachAnswer));
      setCoachAnswer("");
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "回答提交失败。" });
    } finally {
      setCoachReplyBusy(false);
    }
  }

  if (!problem) {
    return <EmptyState title="正在加载题目" live />;
  }

  const passed = latest?.testCaseResults?.filter(item => item.passed).length || 0;
  const total = latest?.testCaseResults?.length || 0;
  const firstFailedCase = latest?.testCaseResults?.find(item => !item.passed) || null;
  const feedbackStatus = String(studentAiFeedback?.status || "").toUpperCase();
  const feedbackSource = String(studentAiFeedback?.source || "").toUpperCase();
  const feedbackFailure = feedbackFailureReason(studentAiFeedback);
  const modelFeedbackReady = feedbackStatus === "READY" && feedbackSource === "MODEL";
  const repairViewItems = modelFeedbackReady
    ? [...(studentAiFeedback?.repairItems?.filter(item => item.body || item.title) || [])].sort((left, right) => {
        const order: Record<string, number> = { PERSISTED: 0, RECURRED: 1, NEW: 2, UNCOMPARABLE: 3 };
        return (order[String(left.changeStatus || "UNCOMPARABLE").toUpperCase()] ?? 4)
          - (order[String(right.changeStatus || "UNCOMPARABLE").toUpperCase()] ?? 4);
      })
    : [];
  const improvementViewItems = modelFeedbackReady ? studentAiFeedback?.improvementItems?.filter(item => item.body || item.title) || [] : [];
  const lifecycleChanges = studentAiFeedback?.issueChanges || [];
  const historicalLifecycleChanges = lifecycleChanges.filter(item => ["NOT_OBSERVED", "RECOVERED"].includes(String(item.changeStatus).toUpperCase()));
  const lifecycleSummary = studentAiFeedback?.issueChangeSummary;
  const currentGrowthSummary = studentAiFeedback?.growthSummary
    || latest?.growthSummary
    || history.find(item => item.id === latest?.id)?.growthSummary
    || null;
  const isFeedbackWaiting = Boolean(
    latest &&
      feedbackPollState !== "idle" &&
      feedbackPollState !== "stalled" &&
      (!studentAiFeedback || inFlightFeedbackStatus(feedbackStatus))
  );
  const isFeedbackBackground = Boolean(latest && feedbackPollState === "background" && (!studentAiFeedback || inFlightFeedbackStatus(feedbackStatus)));
  const feedbackFailed = Boolean(
    latest && (
      (
        studentAiFeedback &&
        (["TIMEOUT", "FAILED", "SAFETY_REJECTED"].includes(feedbackStatus) || (terminalFeedbackStatus(feedbackStatus) && feedbackSource === "RULE_FALLBACK"))
      ) ||
      feedbackPollState === "stalled"
    )
  );
  const canShowStudentReport = modelFeedbackReady || Boolean(feedbackFailed && studentAiFeedback?.studentReport);
  const studentReport = canShowStudentReport ? studentAiFeedback?.studentReport || null : null;
  const basicReportText = studentReport?.basicLayerText?.trim() || "";
  const improvementReportText = studentReport?.improvementLayerText?.trim() || "";
  const nextActionReportText = studentReport?.nextActionText?.trim() || "";
  const feedbackFallbackMessage = feedbackPollState === "stalled"
    ? t("diagnosisFailure.takingLongerMessage")
    : studentAiFeedback?.source === "RULE_FALLBACK"
    ? t("diagnosisFailure.unavailableMessage")
    : modelFailureMessage(feedbackFailure, t);
  const feedbackFailureTitle = feedbackPollState === "stalled"
    ? t("diagnosisFailure.takingLongerTitle")
    : modelFailureTitle(feedbackFailure, t);
  const testCaseSummary = total ? `${passed}/${total} 测试点` : "等待评测";
  const feedbackReady = Boolean(latest);
  const nextTaskLink = nextTask ? buildTaskLink(nextTask.problemId) : null;
  const passedLatest = latest?.verdict === "ACCEPTED";
  const lastResultText = modelFeedbackReady
    ? t("problemHistory.feedback.ready")
    : isFeedbackBackground
      ? t("diagnosisProgress.background")
      : isFeedbackWaiting
        ? diagnosisProgress?.retrying
          ? t("diagnosisProgress.status.retrying")
          : t("diagnosisProgress.status.processing")
        : feedbackFailed
          ? t("problemHistory.feedback.failed")
          : testCaseSummary;
  const selectedLanguage = contestLanguageById(languageId);
  const draftChanged = sourceCode !== defaultSourceFor(problem, languageId);
  const canSubmit = Boolean(sourceCode.trim()) && !busy && !runBusy;
  const codeLineCount = sourceCode ? sourceCode.split(/\r?\n/).length : 0;
  const repairCheckQuestion = canShowStudentReport ? nextActionReportText || (modelFeedbackReady ? studentAiFeedback?.nextQuestion || "" : "") : "";
  const showRepairSection =
    isFeedbackWaiting ||
    isFeedbackBackground ||
    feedbackFailed ||
    Boolean(basicReportText) ||
    repairViewItems.length > 0 ||
    Boolean(repairCheckQuestion) ||
    Boolean(coachPrompt);
  const showGrowthSection = isFeedbackWaiting || isFeedbackBackground || feedbackFailed || Boolean(improvementReportText) || improvementViewItems.length > 0;
  const showFeedbackRefreshAction = Boolean(
    latest && (isFeedbackBackground || feedbackFailed || feedbackPollState === "slow" || feedbackPollState === "refreshing")
  );
  const feedbackRefreshLabel = feedbackPollState === "refreshing" ? "刷新中" : feedbackFailed ? "重试 AI" : "刷新 AI";
  const rawJudgeOutputs = Array.from(
    new Set(
      [
        latest?.errorMessage,
        latest?.compileOutput,
        firstFailedCase && isDiagnosticOutput(firstFailedCase.actualOutput) ? firstFailedCase.actualOutput : null
      ]
        .map(item => (item || "").trim())
        .filter(Boolean)
    )
  );
  const hasRawJudgeOutput = rawJudgeOutputs.length > 0;
  const judgeTextWeight =
    total * 36 +
    rawJudgeOutputs.reduce((sum, item) => sum + item.length, 0) +
    (firstFailedCase && !firstFailedCase.hidden
      ? (firstFailedCase.expectedOutput?.length || 0) + (firstFailedCase.actualOutput?.length || 0) + 90
      : 0);
  const repairTextWeight = feedbackTextWeight(repairViewItems) + basicReportText.length + repairCheckQuestion.length + (coachPrompt?.question?.length || 0);
  const growthTextWeight = feedbackTextWeight(improvementViewItems) + improvementReportText.length;
  const resultLayoutMode = isFeedbackWaiting
    ? "waiting"
    : judgeTextWeight > Math.max(520, repairTextWeight + growthTextWeight)
      ? "judge-heavy"
      : repairTextWeight > Math.max(420, growthTextWeight * 1.35)
        ? "repair-heavy"
        : repairTextWeight + growthTextWeight > Math.max(520, judgeTextWeight * 1.18)
          ? "ai-heavy"
          : "balanced";
  const coachQuestionBlock = (
    <div className="coach-next-question">
      {modelFeedbackReady ? (
        coachPrompt ? (
          <>
            <strong>{coachPrompt.question}</strong>
            {coachPrompt.contextSummary && <p>{coachPrompt.contextSummary}</p>}
            {coachPrompt.rationale && <p>{coachPrompt.rationale}</p>}
            {coachPrompt.turns?.length ? (
              <div className="coach-turn-list">
                {coachPrompt.turns.map(turn => (
                  <div className="coach-turn" key={turn.id}>
                    <span>第 {turn.turnIndex || 1} 轮</span>
                    <strong>{turn.question}</strong>
                    {turn.studentAnswer && <p>我的回答：{turn.studentAnswer}</p>}
                    {turn.coachFeedback && <p>反馈：{turn.coachFeedback}</p>}
                  </div>
                ))}
              </div>
            ) : null}
            {!coachPrompt.studentAnswer && (
              <div className="coach-reply-box">
                <TextArea
                  value={coachAnswer}
                  onChange={event => setCoachAnswer(event.target.value)}
                  rows={3}
                  maxLength={1200}
                  placeholder="写下你的判断、最小样例、变量变化或复杂度估算。"
                />
                <Button type="button" variant="primary" onClick={() => void replyCoachPrompt()} disabled={coachReplyBusy}>
                  {coachReplyBusy ? "提交中" : "提交回答"}
                </Button>
              </div>
            )}
          </>
        ) : (
          null
        )
      ) : null}
      {modelFeedbackReady && (
        <Button
          type="button"
          variant="secondary"
          onClick={() => void generateCoachPrompt()}
          disabled={coachBusy}
          icon={<Lightbulb size={16} />}
        >
          {coachBusy ? "稍等" : coachPrompt ? "换一问" : "给我一问"}
        </Button>
      )}
    </div>
  );

  return (
    <div className={`stack problem-page problem-workbench${assignmentId ? " problem-workbench--assignment" : " problem-workbench--public"}`}>
      {alert && <div className={`alert alert--${alert.type === "success" ? "success" : "error"}`}>{alert.message}</div>}

      {assignmentId && assignmentContext && currentStudent ? (
        <StudentAssignmentHeader assignment={assignmentContext} student={currentStudent} />
      ) : null}

      <div className={`problem-workbench-shell${assignmentId ? " student-assignment-workspace" : ""}`}>
        {assignmentId && assignmentContext && currentStudent ? (
          <StudentAssignmentNavigation assignmentId={assignmentContext.id} taskPath={buildTaskLink(problemId)} activeTab="tasks" />
        ) : !isPublicWorkbench ? (
          <nav className="problem-workbench-rail" aria-label="作业页面导航">
            <Link to="/student">
              <LayoutGrid size={22} aria-hidden="true" /><span>概览</span>
            </Link>
            <Link className="is-active" aria-current="page" to={buildTaskLink(problemId)}>
              <ClipboardList size={22} aria-hidden="true" /><span>题目</span>
            </Link>
            <Link to={assignmentBasePath}>
              <FileCheck2 size={22} aria-hidden="true" /><span>提交</span>
            </Link>
            <Link to={assignmentBasePath}>
              <BarChart3 size={22} aria-hidden="true" /><span>排名</span>
            </Link>
          </nav>
        ) : null}

        <section className="problem-layout problem-layout--workbench">
        <aside className="problem-task-sidebar" aria-label="题目列表" aria-busy={tasksLoading}>
          {isPublicWorkbench ? (
            <Link to={backTo} className="problem-back-link">
              <ArrowLeft size={14} /> {backLabel}
            </Link>
          ) : null}
          <div className="problem-task-sidebar__head">
            <div>
              <strong>{assignmentTitle}</strong>
            </div>
            <StatusPill tone={trajectory ? "success" : "neutral"}>
              {trajectory ? `${trajectory.completedTasks}/${trajectory.totalTasks}` : tasksLoading ? "加载中" : `${taskRows.length} 题`}
            </StatusPill>
          </div>
          <div className="problem-task-list">
            {tasksLoading ? (
              <EmptyState title="加载中" />
            ) : !taskRows.length ? (
              <EmptyState title="暂无题目" description="返回列表重新选择题目。" />
            ) : (
              taskRows.map((task, index) => (
                <Link
                  className={`problem-task-item ${task.problemId === problemId ? "is-active" : ""} ${task.passed ? "is-passed" : ""}`}
                  to={buildTaskLink(task.problemId)}
                  key={task.problemId}
                  aria-current={task.problemId === problemId ? "page" : undefined}
                >
                  <span className="problem-task-item__index">{index + 1}</span>
                  <span className="problem-task-item__main">
                    <strong>{task.title}</strong>
                    <small>{difficultyLabel(task.difficulty)}</small>
                  </span>
                  {task.latestVerdict ? <VerdictPill verdict={task.latestVerdict} /> : task.passed ? <StatusPill tone="success">已过</StatusPill> : null}
                </Link>
              ))
            )}
          </div>
        </aside>

        <div
          className={`problem-main-split${editorCollapsed ? " is-editor-collapsed" : ""}`}
          ref={splitRef}
          style={{
            gridTemplateColumns: editorCollapsed
              ? "minmax(0, 1fr) 44px"
              : `minmax(320px, calc(${statementShare}% - 5px)) 10px minmax(360px, calc(${100 - statementShare}% - 5px))`
          }}
        >

        <Panel
          title={problem.title}
          className="panel--statement"
          action={<DifficultyPill difficulty={problem.difficulty} />}
          description={
            <span className="problem-statement-meta">
              <span>{assignmentTitle}</span>
              <span>{problem.timeLimit} ms</span>
              <span>{Math.round(problem.memoryLimit / 1024)} MB</span>
            </span>
          }
        >
          <div className="statement">{renderMarkdownLike(problem.description)}</div>
          <details className="problem-compact-details problem-sample-drawer" open={problem.sampleTestCases.length <= 1}>
            <summary>
              <span>公开样例</span>
              {problem.sampleTestCases.length ? <span className="meta-badge">{problem.sampleTestCases.length}</span> : null}
            </summary>
            <div className="problem-sample-drawer__body">
              {problem.sampleTestCases.length ? (
                problem.sampleTestCases.map((sample, index) => (
                  <div className="two-column" key={`${sample.input}-${index}`}>
                    <div>
                      <span className="code-label">输入 {index + 1}</span>
                      <pre className="code-block">{sample.input || "(空输入)"}</pre>
                    </div>
                    <div>
                      <span className="code-label">输出 {index + 1}</span>
                      <pre className="code-block">{sample.expectedOutput || "(空输出)"}</pre>
                    </div>
                  </div>
                ))
              ) : null}
            </div>
          </details>
          <a href="#code-workbench" className="problem-mobile-jump ui-button ui-button--primary">
            <Play size={16} />
            <span>去写代码</span>
          </a>
        </Panel>

        {editorCollapsed ? (
          <button
            type="button"
            className="problem-editor-expand"
            aria-label="展开代码"
            onClick={() => setEditorCollapsed(false)}
          >
            <PanelRightOpen size={19} aria-hidden="true" />
            <span>代码</span>
          </button>
        ) : (
          <>
            <div
              className="problem-panel-resizer"
              role="separator"
              aria-label="调整题目和代码宽度"
              aria-orientation="vertical"
              aria-valuemin={32}
              aria-valuemax={68}
              aria-valuenow={Math.round(statementShare)}
              tabIndex={0}
              onPointerDown={startPanelResize}
              onKeyDown={resizeWithKeyboard}
            >
              <GripVertical size={16} aria-hidden="true" />
            </div>

            <Panel
          title="代码"
          className="panel--editor"
          action={
            <div className="problem-editor-header-actions">
              <Field label="语言">
                <Select value={languageId} onChange={event => setLanguageId(Number(event.target.value))} disabled={busy || runBusy}>
                  {CONTEST_LANGUAGES.map(language => (
                    <option value={language.id} key={language.id}>
                      {language.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <button
                type="button"
                className="problem-editor-collapse"
                aria-label="收起代码"
                title="收起代码"
                onClick={() => setEditorCollapsed(true)}
              >
                <PanelRightClose size={18} aria-hidden="true" />
              </button>
            </div>
          }
        >
          <div className="stack" id="code-workbench">
            <div className="editor-shell">
              <div className="editor-toolbar" aria-label="编辑器状态">
                <span>{selectedLanguage.sourceFileName}</span>
                <strong>{draftChanged ? `已保存草稿 · ${codeLineCount} 行` : "初始代码"}</strong>
              </div>
              <Suspense fallback={<div className="editor-loading">正在准备代码编辑器</div>}>
                <CodeEditor
                  key={`${problem.id}-${languageId}-${editorResetVersion}`}
                  languageId={languageId}
                  sourceCode={sourceCode}
                  onChange={updateCode}
                  highlightLine={evidenceFocus.line}
                  highlightNonce={evidenceFocus.nonce}
                />
              </Suspense>
            </div>
            <CodeRunPanel
              problemId={problem.id}
              assignmentId={assignmentId}
              languageId={languageId}
              sourceCode={sourceCode}
              sampleInput={problem.sampleTestCases[0]?.input}
              disabled={busy}
              onRunningChange={setRunBusy}
            />
            {latest && (
              <button type="button" className="problem-last-result" onClick={() => setResultOpen(true)}>
                <span>
                  <VerdictPill verdict={latest.verdict} />
                  <span>{lastResultText}</span>
                </span>
                <strong>查看结果</strong>
              </button>
            )}
            <div className="actions">
              <Button
                type="button"
                variant="primary"
                onClick={() => void submit()}
                disabled={!canSubmit}
                title={!sourceCode.trim() ? "先写代码再提交" : undefined}
                icon={<Play size={18} />}
              >
                {busy ? "提交中" : "提交代码"}
              </Button>
              <Button
                type="button"
                variant="ghost"
                onClick={resetCode}
                disabled={!draftChanged || busy || runBusy}
                title={draftChanged ? undefined : "当前已经是初始代码"}
                icon={<RotateCcw size={18} />}
              >
                恢复初始代码
              </Button>
            </div>
          </div>
            </Panel>
          </>
        )}
        </div>
        </section>
      </div>

      {history.length > 0 && (
        <Panel title={t("problemHistory.title")} className="problem-history-panel">
          <GrowthTimeline
            history={history}
            selectedSubmissionId={latest?.id}
            onSelectSubmission={item => void openHistorySubmission(item)}
          />
        </Panel>
      )}

      {feedbackReady && resultOpen && latest && (
        <div className="problem-result-modal-backdrop" role="presentation" onClick={closeResult}>
          <section className="problem-result-modal" role="dialog" aria-modal="true" aria-labelledby="problem-result-title" onClick={event => event.stopPropagation()}>
            <div className="problem-result-modal__header">
              <div className="problem-result-modal__summary">
                {requestedSubmissionId && assignmentId && (
                  <Link className="problem-result-modal__back" to={`${assignmentBasePath}/submissions`}>
                    <ArrowLeft size={16} />
                    <span>{t("problemResultViews.returnToSubmissions")}</span>
                  </Link>
                )}
                <div className="problem-result-modal__title-row">
                  <h2 id="problem-result-title">{passedLatest ? verdictLabel(latest.verdict) : t("problemFeedbackWorkbench.title")}</h2>
                  <span>{t("problemFeedbackWorkbench.score", { score: total ? `${passed}/${total}` : "-" })}</span>
                </div>
              </div>
              <div className="problem-result-view-switch" role="tablist" aria-label={t("problemResultViews.aria")}>
                <button
                  type="button"
                  role="tab"
                  id="problem-result-tab-repair"
                  aria-controls="problem-result-panel-repair"
                  aria-selected={resultView === "repair"}
                  className={resultView === "repair" ? "is-active" : ""}
                  onClick={() => setResultView("repair")}
                >
                  <ClipboardList size={17} />
                  <span>{t("problemResultViews.repair")}</span>
                </button>
                <button
                  type="button"
                  role="tab"
                  id="problem-result-tab-growth"
                  aria-controls="problem-result-panel-growth"
                  aria-selected={resultView === "growth"}
                  className={resultView === "growth" ? "is-active" : ""}
                  onClick={() => setResultView("growth")}
                >
                  <BarChart3 size={17} />
                  <span>{t("problemResultViews.growth")}</span>
                </button>
              </div>
              <div className="problem-result-modal__status">
                <StatusPill tone={latest.verdict === "ACCEPTED" ? "success" : "warning"}>{testCaseSummary}</StatusPill>
                {(isFeedbackWaiting || isFeedbackBackground) && (
                  <StatusPill tone="neutral">
                    {diagnosisProgress?.retrying
                      ? t("diagnosisProgress.status.retrying")
                      : isFeedbackBackground
                        ? t("diagnosisProgress.background")
                        : t("diagnosisProgress.status.processing")}
                  </StatusPill>
                )}
                {feedbackFailed && <StatusPill tone="danger">{feedbackFailureTitle}</StatusPill>}
                <button type="button" aria-label="关闭结果" onClick={closeResult}>
                  <X size={18} />
                </button>
              </div>
            </div>

            <div className="problem-result-modal__body">
              {resultView === "repair" ? (
                <div
                  className="problem-result-view problem-result-view--repair"
                  role="tabpanel"
                  id="problem-result-panel-repair"
                  aria-labelledby="problem-result-tab-repair"
                >
                  {modelFeedbackReady ? (
                    <FeedbackRepairWorkbench
                      sourceCode={latest.sourceCode || sourceCode}
                      sourceFileName={selectedLanguage.sourceFileName}
                      languageName={latest.languageName || selectedLanguage.label}
                      repairItems={repairViewItems}
                      improvementItems={improvementViewItems}
                      summary={basicReportText}
                      verificationPrompt={repairCheckQuestion}
                      passed={passed}
                      total={total}
                      firstFailedCase={firstFailedCase}
                      onReturnToCode={() => setResultOpen(false)}
                    />
                  ) : (
                  <div className={`problem-result-modal__grid problem-result-modal__grid--${resultLayoutMode}`}>
                {lifecycleChanges.length ? (
                  <section className="problem-result-section problem-result-section--lifecycle">
                    <div className="problem-result-section__head">
                      <h3>{t("issueLifecycle.title")}</h3>
                      <strong>{t("issueLifecycle.completeCount", { count: repairViewItems.length + improvementViewItems.length })}</strong>
                    </div>
                    <div className="student-issue-change-summary">
                      <span>{t("issueLifecycle.summary.persisted", { count: lifecycleSummary?.persistedCount || 0 })}</span>
                      <span>{t("issueLifecycle.summary.new", { count: lifecycleSummary?.newCount || 0 })}</span>
                      <span>{t("issueLifecycle.summary.recurring", { count: lifecycleSummary?.recurringCount || 0 })}</span>
                      <span>{t("issueLifecycle.summary.recovered", { count: lifecycleSummary?.recoveredCount || 0 })}</span>
                      <span>{t("issueLifecycle.summary.improvements", { count: improvementViewItems.length })}</span>
                    </div>
                    {historicalLifecycleChanges.length ? (
                      <div className="student-issue-history-changes">
                        <strong>{t("issueLifecycle.previousChanges")}</strong>
                        {historicalLifecycleChanges.map(item => (
                          <article key={`${item.normalizedPointKey}-${item.changeStatus}`}>
                            <span>{t(`issueLifecycle.status.${lifecycleStatusKey(item.changeStatus)}`)}</span>
                            <p>{item.title || t("issueLifecycle.unnamedIssue")}</p>
                            <small>{t("issueLifecycle.historyEvidence", {
                              raw: item.rawOccurrenceCount,
                              effective: item.effectiveOccurrenceCount,
                              submission: item.previousSubmissionId || "-"
                            })}</small>
                          </article>
                        ))}
                      </div>
                    ) : null}
                  </section>
                ) : null}
                <section className="problem-result-section problem-result-section--tests">
                  <div className="problem-result-section__head">
                    <h3>评测</h3>
                    <strong>{total ? `${passed}/${total}` : "-"}</strong>
                  </div>
                  {latest.testCaseResults?.length ? (
                    <div className="testcase-compact-list">
                      {latest.testCaseResults.map(item => (
                        <div className={item.passed ? "is-passed" : ""} key={item.testCaseNumber}>
                          <span>{item.testCaseNumber}</span>
                          <strong>{item.hidden ? "隐藏" : "公开"}</strong>
                          <small>{item.executionTime ?? "-"} ms</small>
                          <em>{item.passed ? "过" : "错"}</em>
                        </div>
                      ))}
                    </div>
                  ) : null}
                  {firstFailedCase && !firstFailedCase.hidden ? (
                    <details className="problem-compact-details failed-case-card">
                      <summary>
                        <span>首个失败点</span>
                      </summary>
                      <div className="failed-case-card__body">
                        <div>
                          <strong>期望</strong>
                          <pre className="code-block">{outputPreview(firstFailedCase.expectedOutput)}</pre>
                        </div>
                        <div>
                          <strong>实际</strong>
                          <pre className="code-block">{outputPreview(firstFailedCase.actualOutput)}</pre>
                        </div>
                      </div>
                    </details>
                  ) : firstFailedCase?.hidden ? (
                    <div className="problem-test-note">隐藏点未过</div>
                  ) : null}
                  {hasRawJudgeOutput && (
                    <details className="problem-compact-details problem-raw-output-drawer">
                      <summary>
                      <span>原始错误</span>
                      </summary>
                      <div className="problem-raw-output-drawer__body">
                        {rawJudgeOutputs.map((item, index) => (
                          <pre className="code-block" key={`${item.slice(0, 24)}-${index}`}>
                            {item}
                          </pre>
                        ))}
                      </div>
                    </details>
                  )}
                  <details className="problem-compact-details problem-submitted-code-drawer">
                    <summary>
                      <span>{t("problemHistory.submittedCode")}</span>
                    </summary>
                    <pre className="code-block">{latest.sourceCode || ""}</pre>
                  </details>
                </section>

                {showRepairSection ? (
                  <section className="problem-result-section problem-result-section--repair">
                    <div className="problem-result-section__head">
                      <h3>修正建议</h3>
                    </div>
                    {isFeedbackWaiting || isFeedbackBackground ? (
                      <FeedbackLoadingPanel mode="repair" state={feedbackPollState} progress={diagnosisProgress} />
                    ) : basicReportText ? (
                      <article className="student-feedback-report student-feedback-report--basic">
                        <FeedbackTextBlock text={basicReportText} />
                        {repairViewItems.length ? (
                          <div className="student-feedback-secondary-list" aria-label="逐条检查">
                            <span>逐条检查</span>
                            {repairViewItems.map((item, index) => (
                              <article className={`student-feedback-item${index === 0 ? " student-feedback-item--primary" : ""}`} key={`${item.kind || "repair-extra"}-${index}`}>
                                {item.title && <strong>{item.title}</strong>}
                                <FeedbackLifecycleBadges item={item} />
                                <FeedbackTextBlock text={item.body} maxLines={2} />
                                <FeedbackEvidenceMeta item={item} onJumpToLine={jumpToEvidenceLine} showEmptyEvidence />
                              </article>
                            ))}
                          </div>
                        ) : null}
                      </article>
                    ) : feedbackFailed ? (
                      <div className="student-feedback-empty">
                        <strong>{feedbackFailureTitle}</strong>
                        <p>{feedbackFallbackMessage}</p>
                      </div>
                    ) : repairViewItems.length ? (
                      <div className="student-feedback-list">
                        {repairViewItems.map((item, index) => (
                          <article className={`student-feedback-item${index === 0 ? " student-feedback-item--primary" : ""}`} key={`${item.kind || "repair"}-${index}`}>
                            {item.title && <strong>{item.title}</strong>}
                            <FeedbackLifecycleBadges item={item} />
                            <FeedbackTextBlock text={item.body} maxLines={2} />
                            <FeedbackEvidenceMeta item={item} onJumpToLine={jumpToEvidenceLine} showEmptyEvidence />
                          </article>
                        ))}
                      </div>
                    ) : null}

                    {canShowStudentReport && repairCheckQuestion ? (
                      <section className="student-feedback-next" aria-label="下一步">
                        <p>{repairCheckQuestion}</p>
                      </section>
                    ) : null}

                    {modelFeedbackReady && coachPrompt ? (
                      <section className="problem-feedback-coach" aria-label="追问">
                        {coachQuestionBlock}
                      </section>
                    ) : null}
                  </section>
                ) : null}

                {showGrowthSection ? (
                  <section className="problem-result-section problem-result-section--growth">
                    <div className="problem-result-section__head">
                      <h3>提升建议</h3>
                    </div>
                    {isFeedbackWaiting || isFeedbackBackground ? (
                      <FeedbackLoadingPanel mode="growth" state={feedbackPollState} progress={diagnosisProgress} />
                    ) : improvementReportText ? (
                      <article className="student-feedback-report student-feedback-report--growth">
                        <FeedbackTextBlock text={improvementReportText} />
                        {improvementViewItems.length ? (
                          <div className="student-feedback-secondary-list" aria-label="逐条提升">
                            <span>逐条提升</span>
                            {improvementViewItems.map((item, index) => (
                              <article className="student-feedback-item student-feedback-item--growth" key={`${item.kind || "growth-extra"}-${index}`}>
                                {item.title && <strong>{item.title}</strong>}
                                <FeedbackLifecycleBadges item={item} />
                                <FeedbackTextBlock text={item.body} maxLines={2} />
                                <FeedbackEvidenceMeta item={item} onJumpToLine={jumpToEvidenceLine} showEmptyEvidence />
                              </article>
                            ))}
                          </div>
                        ) : null}
                      </article>
                    ) : feedbackFailed ? (
                      <div className="student-feedback-empty">
                        <strong>{feedbackFailureTitle}</strong>
                        <p>{feedbackFallbackMessage}</p>
                      </div>
                    ) : improvementViewItems.length ? (
                      <div className="student-feedback-list">
                        {improvementViewItems.map((item, index) => (
                          <article className="student-feedback-item student-feedback-item--growth" key={`${item.kind || "improvement"}-${index}`}>
                            {item.title && <strong>{item.title}</strong>}
                            <FeedbackLifecycleBadges item={item} />
                            <FeedbackTextBlock text={item.body} maxLines={2} />
                            <FeedbackEvidenceMeta item={item} onJumpToLine={jumpToEvidenceLine} showEmptyEvidence />
                          </article>
                        ))}
                      </div>
                    ) : null}
                  </section>
                ) : null}
                  </div>
                  )}
                </div>
              ) : (
                <div
                  className="problem-result-view problem-result-view--growth"
                  role="tabpanel"
                  id="problem-result-panel-growth"
                  aria-labelledby="problem-result-tab-growth"
                >
                  {learningProof ? (
                    <LearningProofPanel
                      proof={learningProof}
                      editable
                      busy={learningProofBusy}
                      onCreateReflection={createLearningReflection}
                      onAnswerReflection={answerLearningReflection}
                    />
                  ) : null}
                  <SingleProblemGrowthDashboard
                    history={history}
                    selectedSubmissionId={latest.id}
                    currentSummary={currentGrowthSummary}
                    onSelectSubmission={item => void openHistorySubmission(item)}
                  />
                </div>
              )}
            </div>

            {!(resultView === "repair" && modelFeedbackReady) && <div className="problem-result-modal__footer">
              {showFeedbackRefreshAction && (
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => void refreshStudentAiFeedback()}
                  disabled={feedbackPollState === "refreshing"}
                  icon={<RefreshCw size={16} />}
                >
                  {feedbackRefreshLabel}
                </Button>
              )}
              <Button type="button" variant="primary" onClick={() => setResultOpen(false)}>
                {passedLatest && nextTaskLink ? "留在本题" : "继续修改"}
              </Button>
              {nextTaskLink && (
                <ButtonLink to={nextTaskLink} variant={passedLatest ? "primary" : "secondary"} icon={<ArrowRight size={16} />}>
                  下一题
                </ButtonLink>
              )}
            </div>}
          </section>
        </div>
      )}
    </div>
  );
}
