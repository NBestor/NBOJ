import { ReactNode, useEffect, useMemo, useState } from "react";
import {
  BookOpen,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  GitMerge,
  ListChecks,
  Plus,
  Power,
  PowerOff,
  RefreshCw,
  Save,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  UploadCloud,
  X,
  XCircle
} from "lucide-react";
import { api } from "../../shared/api/client";
import type {
  AiStandardLibraryGrowthCandidate,
  AiStandardLibraryGrowthCandidatePayload,
  AiStandardLibraryGrowthGovernanceSummary,
  AiStandardLibraryGrowthPathStat,
  AiStandardLibraryItem,
  AiStandardLibraryItemPayload,
  AiStandardLibraryLayer,
  ClassGroup,
  InformaticsKnowledgeNode,
  ImportCommit,
  ImportPreview,
  ProblemManage,
  Readiness,
  StudentProfile,
  TeacherAiUsage
} from "../../shared/api/types";
import { displayText } from "../../shared/format";
import { useTranslation } from "../../shared/i18n";
import { Button } from "../../shared/ui/Button";
import { EmptyState } from "../../shared/ui/EmptyState";
import { Field, Select, TextArea, TextInput } from "../../shared/ui/Field";
import { StatusPill } from "../../shared/ui/StatusPill";
import TaskEditorPage from "../task-editor/TaskEditorPage";

type Alert = { type: "success" | "error"; message: string };
type ImportKind = "class" | "problem";
type ClassWorkspaceTab = "roster" | "import" | "settings";
type LibraryEnabledFilter = "" | "true" | "false";
type StandardLibraryView = "library" | "review";
type GrowthCandidateAction = "approve" | "merge" | "reject" | "ignore";
type GrowthCandidateStatusFilter = "" | "pending" | "PROPOSED" | "NEEDS_REVIEW" | "BLOCKED" | "MERGED_SIMILAR" | "TEACHER_APPROVED" | "MERGED" | "REJECTED" | "IGNORED";
type LibraryFilters = {
  query: string;
  layer: AiStandardLibraryLayer | "";
  category: string;
  enabled: LibraryEnabledFilter;
};
type LibraryDraft = {
  id?: number;
  layer: AiStandardLibraryLayer;
  code: string;
  category: string;
  name: string;
  description: string;
  studentExplanation: string;
  teacherExplanation: string;
  skillUnitCode: string;
  mistakeType: string;
  commonMisconception: string;
  evidenceSignals: string;
  commonCodePatterns: string;
  judgeSignals: string;
  requiredEvidence: string;
  whenToUse: string;
  studentBenefit: string;
  hintL1: string;
  hintL2: string;
  hintL3: string;
  abilityPoint: string;
  severity: string;
  applicableLanguages: string;
  relatedItems: string;
  knowledgeNodeCodes: string;
  prerequisiteKnowledgeCodes: string;
  teachingAction: string;
  enabled: boolean;
  libraryVersion: string;
};

const DEFAULT_LIBRARY_FILTERS: LibraryFilters = { query: "", layer: "", category: "", enabled: "" };

type TeacherManagementToolsProps = {
  section?: ManagementSection;
};
type ManagementSection = "classes" | "problems" | "ai-library" | "system";
type ProblemSortMode = "id" | "difficulty" | "timeLimit";

const PROBLEM_PAGE_SIZE = 10;
const PROBLEM_DIFFICULTY_ORDER: Record<string, number> = { EASY: 1, MEDIUM: 2, HARD: 3 };

const MANAGEMENT_SECTION_META = {
  classes: {
    title: "teacherManagement.sections.classes.title"
  },
  problems: {
    title: "teacherManagement.sections.problems.title"
  },
  "ai-library": {
    title: "teacherManagement.sections.aiLibrary.title"
  },
  system: {
    title: "teacherManagement.sections.system.title"
  }
} satisfies Record<ManagementSection, { title: string }>;

export default function TeacherManagementPage({ section = "classes" }: { section?: ManagementSection }) {
  const { t } = useTranslation();
  const meta = MANAGEMENT_SECTION_META[section];
  return (
    <div className={`teacher-page teacher-workflow teacher-manage-page teacher-manage-page--${section}`}>
      <section className="teacher-workflow-header teacher-workflow-header--simple teacher-manage-header">
        <div>
          <h1>{t(meta.title)}</h1>
        </div>
      </section>
      <TeacherManagementTools section={section} />
    </div>
  );
}

export function TeacherManagementTools({ section = "classes" }: TeacherManagementToolsProps) {
  const { t } = useTranslation();
  const [classes, setClasses] = useState<ClassGroup[]>([]);
  const [roster, setRoster] = useState<StudentProfile[]>([]);
  const [problems, setProblems] = useState<ProblemManage[]>([]);
  const [classForm, setClassForm] = useState({ name: "", grade: "", teacherName: "" });
  const [targetClassGroupId, setTargetClassGroupId] = useState("");
  const [classImport, setClassImport] = useState({ format: "csv", content: "" });
  const [problemImport, setProblemImport] = useState({ format: "markdown", content: "" });
  const [classImportResult, setClassImportResult] = useState<ImportPreview | ImportCommit | null>(null);
  const [problemImportResult, setProblemImportResult] = useState<ImportPreview | ImportCommit | null>(null);
  const [classFileName, setClassFileName] = useState("");
  const [problemFileName, setProblemFileName] = useState("");
  const [alert, setAlert] = useState<Alert | null>(null);
  const [busy, setBusy] = useState(true);
  const [readiness, setReadiness] = useState<Readiness | null>(null);
  const [usage, setUsage] = useState<TeacherAiUsage | null>(null);
  const [aiSmokeBusy, setAiSmokeBusy] = useState(false);
  const [libraryItems, setLibraryItems] = useState<AiStandardLibraryItem[]>([]);
  const [growthCandidates, setGrowthCandidates] = useState<AiStandardLibraryGrowthCandidate[]>([]);
  const [growthGovernanceSummary, setGrowthGovernanceSummary] = useState<AiStandardLibraryGrowthGovernanceSummary | null>(null);
  const [knowledgeTree, setKnowledgeTree] = useState<InformaticsKnowledgeNode[]>([]);
  const [libraryFilters, setLibraryFilters] = useState<LibraryFilters>(DEFAULT_LIBRARY_FILTERS);
  const [selectedLibraryId, setSelectedLibraryId] = useState<number | null>(null);
  const [libraryDraft, setLibraryDraft] = useState<LibraryDraft>(() => emptyLibraryDraft());
  const [libraryBusy, setLibraryBusy] = useState(false);
  const [selectedProblemId, setSelectedProblemId] = useState<number | null>(null);

  useEffect(() => {
    if (section === "classes") {
      void loadClasses();
    }
    if (section === "problems") {
      void loadProblems();
    }
    if (section === "system") {
      void loadReadiness();
      void loadUsage();
    }
    if (section === "ai-library") {
      void loadKnowledgeTree();
      void loadStandardLibraryGrowth();
    }
  }, [section]);

  useEffect(() => {
    if (section !== "classes" || !targetClassGroupId) {
      setRoster([]);
      return;
    }
    void loadRoster(Number(targetClassGroupId));
  }, [section, targetClassGroupId]);

  useEffect(() => {
    if (section !== "ai-library") {
      return;
    }
    const handle = window.setTimeout(() => {
      void loadStandardLibrary(libraryFilters);
    }, 220);
    return () => window.clearTimeout(handle);
  }, [section, libraryFilters.query, libraryFilters.layer, libraryFilters.category, libraryFilters.enabled]);

  const cleanClasses = useMemo(
    () =>
      classes.map(item => ({
        ...item,
        name: displayText(item.name, `班级 #${item.id}`),
        grade: displayText(item.grade, ""),
        teacherName: displayText(item.teacherName, "")
      })),
    [classes]
  );

  async function loadClasses() {
    setBusy(true);
    try {
      const classResult = await api.classes();
      setClasses(classResult);
      if (!targetClassGroupId && classResult[0]) {
        setTargetClassGroupId(String(classResult[0].id));
      }
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "班级数据读取失败。" });
    } finally {
      setBusy(false);
    }
  }

  async function loadProblems() {
    setBusy(true);
    try {
      const problemResult = await api.teacherProblems();
      setProblems(problemResult);
      if (!selectedProblemId && problemResult[0]) {
        setSelectedProblemId(problemResult[0].id);
      }
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "题库数据读取失败。" });
    } finally {
      setBusy(false);
    }
  }

  async function loadReadiness() {
    try {
      setReadiness(await api.readiness());
    } catch {
      setReadiness(null);
    }
  }

  async function loadRoster(classGroupId: number) {
    try {
      setRoster(await api.classRoster(classGroupId));
    } catch {
      setRoster([]);
    }
  }

  async function loadUsage() {
    try {
      setUsage(await api.teacherUsage());
    } catch {
      setUsage(null);
    }
  }

  async function loadKnowledgeTree() {
    try {
      setKnowledgeTree(await api.informaticsKnowledgeTree());
    } catch {
      setKnowledgeTree([]);
    }
  }

  async function loadStandardLibrary(filters = libraryFilters, preferredId = selectedLibraryId) {
    setLibraryBusy(true);
    try {
      const items = await api.aiStandardLibraryItems({
        layer: filters.layer,
        category: filters.category,
        enabled: filters.enabled === "" ? "" : filters.enabled === "true",
        query: filters.query
      });
      setLibraryItems(items);
      const selected = items.find(item => item.id === preferredId) || items[0];
      if (selected) {
        setSelectedLibraryId(selected.id);
        setLibraryDraft(itemToDraft(selected));
      } else {
        setSelectedLibraryId(null);
        setLibraryDraft(emptyLibraryDraft());
      }
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "AI 标准库读取失败。" });
    } finally {
      setLibraryBusy(false);
    }
  }

  async function loadStandardLibraryGrowth() {
    setLibraryBusy(true);
    try {
      const [candidates, summary] = await Promise.all([
        api.aiStandardLibraryGrowthCandidates(),
        api.aiStandardLibraryGrowthGovernanceSummary()
      ]);
      setGrowthCandidates(candidates);
      setGrowthGovernanceSummary(summary);
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "标准库成长候选读取失败。" });
    } finally {
      setLibraryBusy(false);
    }
  }

  function selectLibraryItem(item: AiStandardLibraryItem) {
    setSelectedLibraryId(item.id);
    setLibraryDraft(itemToDraft(item));
  }

  function createLibraryDraft() {
    setSelectedLibraryId(null);
    setLibraryDraft(emptyLibraryDraft());
  }

  async function saveLibraryItem(draftOverride = libraryDraft) {
    if (!draftOverride.code.trim() || !draftOverride.category.trim() || !draftOverride.name.trim()) {
      setAlert({ type: "error", message: "请填写条目 ID、分类和名称。" });
      return;
    }
    setLibraryBusy(true);
    try {
      const payload = draftToPayload(draftOverride);
      const saved = draftOverride.id
        ? await api.updateAiStandardLibraryItem(draftOverride.id, payload)
        : await api.createAiStandardLibraryItem(payload);
      setAlert({ type: "success", message: "AI 标准库已保存。" });
      await loadStandardLibrary(libraryFilters, saved.id);
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "AI 标准库保存失败。" });
    } finally {
      setLibraryBusy(false);
    }
  }

  async function toggleLibraryItem(item: AiStandardLibraryItem) {
    setLibraryBusy(true);
    try {
      const updated = item.enabled
        ? await api.disableAiStandardLibraryItem(item.id)
        : await api.enableAiStandardLibraryItem(item.id);
      setAlert({ type: "success", message: updated.enabled ? "条目已启用。" : "条目已停用。" });
      await loadStandardLibrary(libraryFilters, updated.id);
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "条目状态更新失败。" });
    } finally {
      setLibraryBusy(false);
    }
  }

  async function updateGrowthCandidate(id: number, payload: AiStandardLibraryGrowthCandidatePayload) {
    setLibraryBusy(true);
    try {
      await api.updateAiStandardLibraryGrowthCandidate(id, payload);
      setAlert({ type: "success", message: "成长候选已保存。" });
      await loadStandardLibraryGrowth();
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "成长候选保存失败。" });
    } finally {
      setLibraryBusy(false);
    }
  }

  async function reviewGrowthCandidate(id: number, action: GrowthCandidateAction, payload: AiStandardLibraryGrowthCandidatePayload) {
    setLibraryBusy(true);
    try {
      if (action === "approve") {
        await api.approveAiStandardLibraryGrowthCandidate(id, payload);
      } else if (action === "merge") {
        await api.mergeAiStandardLibraryGrowthCandidate(id, payload);
      } else if (action === "reject") {
        await api.rejectAiStandardLibraryGrowthCandidate(id, payload);
      } else {
        await api.ignoreAiStandardLibraryGrowthCandidate(id, payload);
      }
      setAlert({ type: "success", message: "候选审核状态已更新。" });
      await Promise.all([loadStandardLibraryGrowth(), loadStandardLibrary(libraryFilters, selectedLibraryId)]);
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "候选审核失败。" });
    } finally {
      setLibraryBusy(false);
    }
  }

  async function runAiSmoke() {
    setAiSmokeBusy(true);
    try {
      const result = await api.aiSmoke();
      setAlert({ type: result.status === "READY" ? "success" : "error", message: result.message || "AI smoke 已完成。" });
      await loadReadiness();
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "AI smoke 失败。" });
    } finally {
      setAiSmokeBusy(false);
    }
  }

  async function createClass() {
    if (!classForm.name.trim()) {
      setAlert({ type: "error", message: "请填写班级名称。" });
      return;
    }
    setBusy(true);
    try {
      const created = await api.createClass({
        name: classForm.name.trim(),
        grade: classForm.grade.trim(),
        teacherName: classForm.teacherName.trim()
      });
      setClassForm({ name: "", grade: "", teacherName: "" });
      setAlert({ type: "success", message: created.joinCode
        ? t("multiTeacher.classCode.created", { code: created.joinCode })
        : t("multiTeacher.classCode.createdWithoutCode") });
      await loadClasses();
      setClasses(current => current.map(item => item.id === created.id ? { ...item, joinCode: created.joinCode } : item));
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "班级创建失败。" });
    } finally {
      setBusy(false);
    }
  }

  async function rotateClassCode(classGroupId: number) {
    setBusy(true);
    try {
      const updated = await api.rotateClassJoinCode(classGroupId);
      setClasses(current => current.map(item => item.id === classGroupId ? { ...item, joinCode: updated.joinCode } : item));
      setAlert({ type: "success", message: t("multiTeacher.classCode.rotated", { code: updated.joinCode || "" }) });
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("multiTeacher.classCode.rotateFailed") });
    } finally {
      setBusy(false);
    }
  }

  async function updateRosterStatus(student: StudentProfile) {
    if (!student.classGroupId) return;
    setBusy(true);
    try {
      await api.updateRosterStatus(student.classGroupId, student.id, student.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
      await Promise.all([loadRoster(student.classGroupId), loadClasses()]);
      setAlert({ type: "success", message: t("multiTeacher.roster.statusSaved") });
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("multiTeacher.roster.statusFailed") });
    } finally {
      setBusy(false);
    }
  }

  async function submitProblemReview(problemId: number) {
    setBusy(true);
    try {
      const created = await api.submitProblemReview(problemId);
      setAlert({ type: "success", message: t("multiTeacher.problem.reviewSubmitted", { version: created.versionNo }) });
      await loadProblems();
      setSelectedProblemId(created.id);
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("multiTeacher.problem.actionFailed") });
    } finally {
      setBusy(false);
    }
  }

  async function reviseProblem(problemId: number) {
    setBusy(true);
    try {
      const created = await api.reviseProblem(problemId);
      await loadProblems();
      setSelectedProblemId(created.id);
      setAlert({ type: "success", message: t("multiTeacher.problem.revisionCreated", { version: created.versionNo }) });
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : t("multiTeacher.problem.actionFailed") });
    } finally {
      setBusy(false);
    }
  }

  function importPayload(kind: "class" | "problem") {
    const data = kind === "class" ? classImport : problemImport;
    return {
      format: data.format,
      content: data.content,
      classGroupId: targetClassGroupId ? Number(targetClassGroupId) : null,
      className: classForm.name || undefined
    };
  }

  async function runImport(kind: "class" | "problem", mode: "preview" | "commit") {
    const data = kind === "class" ? classImport : problemImport;
    if (!data.content.trim()) {
      setAlert({ type: "error", message: "请先粘贴或选择导入内容。" });
      return;
    }
    setBusy(true);
    try {
      const payload = importPayload(kind);
      const result =
        kind === "class"
          ? mode === "preview"
            ? await api.classImportPreview(payload)
            : await api.classImportCommit(payload)
          : mode === "preview"
            ? await api.problemImportPreview(payload)
            : await api.problemImportCommit(payload);
      if (kind === "class") {
        setClassImportResult(result);
      } else {
        setProblemImportResult(result);
      }
      setAlert({ type: "success", message: mode === "preview" ? "预览已生成。" : "导入已完成。" });
      if (mode === "commit") {
        if (kind === "class") {
          await loadClasses();
          if (targetClassGroupId) await loadRoster(Number(targetClassGroupId));
        } else {
          await loadProblems();
        }
      }
    } catch (error) {
      setAlert({ type: "error", message: error instanceof Error ? error.message : "导入失败。" });
    } finally {
      setBusy(false);
    }
  }

  async function readImportFile(kind: "class" | "problem", file: File | null) {
    if (!file) {
      return;
    }
    const extension = file.name.split(".").pop()?.toLowerCase() || "";
    const format = extension === "xlsx" ? "xlsx" : extension === "json" ? "json" : extension === "md" || extension === "markdown" ? "markdown" : "csv";
    const content = extension === "xlsx" ? await readFileAsDataUrl(file) : await file.text();
    if (kind === "class") {
      setClassFileName(file.name);
      setClassImport({ format: format === "xlsx" ? "xlsx" : "csv", content });
    } else {
      setProblemFileName(file.name);
      setProblemImport({ format, content });
    }
  }

  const classStatusPill = <StatusPill tone="neutral">{t("teacherManagement.classManage.classCount", { count: cleanClasses.length })}</StatusPill>;
  const problemStatusPill = <StatusPill tone="neutral">{t("teacherManagement.problemManage.problemCount", { count: problems.length })}</StatusPill>;

  return (
    <div className="teacher-management-embed teacher-management-embed--routed">
      {alert && <div className={`alert alert--${alert.type === "success" ? "success" : "error"}`}>{alert.message}</div>}

      <section className="management-console">
        <main className="management-workspace">
          {section === "classes" ? (
            <ClassManageSection
              classes={cleanClasses}
              roster={roster}
              selectedClassGroupId={targetClassGroupId}
              classForm={classForm}
              classImport={classImport}
              classFileName={classFileName}
              classImportResult={classImportResult}
              busy={busy}
              dataSummary={classStatusPill}
              onSelectClass={setTargetClassGroupId}
              onClassFormChange={setClassForm}
              onClassImportChange={setClassImport}
              onCreateClass={() => void createClass()}
              onPickFile={readImportFile}
              onRunImport={mode => void runImport("class", mode)}
              onRotateClassCode={classGroupId => void rotateClassCode(classGroupId)}
              onUpdateRosterStatus={student => void updateRosterStatus(student)}
            />
          ) : null}

          {section === "problems" ? (
            <ProblemManageSection
              problems={problems}
              selectedProblemId={selectedProblemId}
              problemImport={problemImport}
              problemFileName={problemFileName}
              problemImportResult={problemImportResult}
              busy={busy}
              dataSummary={problemStatusPill}
              onSelectProblem={setSelectedProblemId}
              onProblemImportChange={setProblemImport}
              onPickFile={readImportFile}
              onRunImport={mode => void runImport("problem", mode)}
              onProblemSaved={problem => {
                setSelectedProblemId(problem.id);
                void loadProblems();
              }}
              onSubmitReview={problemId => void submitProblemReview(problemId)}
              onRevise={problemId => void reviseProblem(problemId)}
            />
          ) : null}

          {section === "system" ? (
            <ReadinessPanel
              readiness={readiness}
              usage={usage}
              busy={aiSmokeBusy}
              onRefresh={loadReadiness}
              onAiSmoke={runAiSmoke}
            />
          ) : null}

          {section === "ai-library" ? (
            <StandardLibraryManager
              items={libraryItems}
              growthCandidates={growthCandidates}
              governanceSummary={growthGovernanceSummary}
              knowledgeTree={knowledgeTree}
              filters={libraryFilters}
              draft={libraryDraft}
              busy={libraryBusy}
              selectedId={selectedLibraryId}
              onFiltersChange={setLibraryFilters}
              onReload={filters => void loadStandardLibrary(filters || libraryFilters)}
              onNew={createLibraryDraft}
              onSelect={selectLibraryItem}
              onDraftChange={setLibraryDraft}
              onSave={draft => void saveLibraryItem(draft)}
              onToggle={item => void toggleLibraryItem(item)}
              onReloadGrowth={() => void loadStandardLibraryGrowth()}
              onUpdateGrowthCandidate={(id, payload) => void updateGrowthCandidate(id, payload)}
              onReviewGrowthCandidate={(id, action, payload) => void reviewGrowthCandidate(id, action, payload)}
            />
          ) : null}
        </main>
      </section>
    </div>
  );
}

function ClassManageSection({
  classes,
  roster,
  selectedClassGroupId,
  classForm,
  classImport,
  classFileName,
  classImportResult,
  busy,
  dataSummary,
  onSelectClass,
  onClassFormChange,
  onClassImportChange,
  onCreateClass,
  onRotateClassCode,
  onUpdateRosterStatus,
  onPickFile,
  onRunImport
}: {
  classes: ClassGroup[];
  roster: StudentProfile[];
  selectedClassGroupId: string;
  classForm: { name: string; grade: string; teacherName: string };
  classImport: { format: string; content: string };
  classFileName: string;
  classImportResult: ImportPreview | ImportCommit | null;
  busy: boolean;
  dataSummary?: ReactNode;
  onSelectClass: (id: string) => void;
  onClassFormChange: (form: { name: string; grade: string; teacherName: string }) => void;
  onClassImportChange: (value: { format: string; content: string }) => void;
  onCreateClass: () => void;
  onRotateClassCode: (classGroupId: number) => void;
  onUpdateRosterStatus: (student: StudentProfile) => void;
  onPickFile: (kind: ImportKind, file: File | null) => void | Promise<void>;
  onRunImport: (mode: "preview" | "commit") => void;
}) {
  const { t } = useTranslation();
  const selectedClass = classes.find(item => String(item.id) === selectedClassGroupId) || classes[0] || null;
  const [activeTab, setActiveTab] = useState<ClassWorkspaceTab>("roster");
  const activeStudentCount = roster.filter(student => student.status === "ACTIVE").length;

  useEffect(() => {
    setActiveTab("roster");
  }, [selectedClass?.id]);

  const selectTab = (tab: ClassWorkspaceTab) => setActiveTab(tab);

  return (
    <section className="management-object-workbench management-object-workbench--classes">
      <aside className="management-object-list" aria-label={t("teacherManagement.classManage.listAria")}>
        <div className="management-object-list__head">
          <strong>{t("teacherManagement.classManage.listTitle")}</strong>
          {dataSummary || <StatusPill tone="neutral">{classes.length} 个</StatusPill>}
        </div>
        {classes.length ? (
          classes.map(item => (
            <button
              type="button"
              className={`management-class-row ${String(item.id) === String(selectedClass?.id) ? "is-active" : ""}`}
              key={item.id}
              onClick={() => onSelectClass(String(item.id))}
            >
              <strong>{item.name}</strong>
              <small>{[item.grade, item.teacherName].filter(Boolean).join(" · ") || `班级 #${item.id}`}</small>
            </button>
          ))
        ) : (
          <EmptyState title={t("teacherManagement.classManage.emptyTitle")} description={t("teacherManagement.classManage.emptyDescription")} />
        )}
        <details className="management-import-drawer management-import-drawer--create">
          <summary>
            <Plus size={15} />
            {t("teacherManagement.classManage.create.title")}
          </summary>
          <div className="management-import-drawer__body">
            <Field label={t("teacherManagement.classManage.create.name")}>
              <TextInput value={classForm.name} onChange={event => onClassFormChange({ ...classForm, name: event.target.value })} />
            </Field>
            <Field label={t("teacherManagement.classManage.create.grade")}>
              <TextInput value={classForm.grade} onChange={event => onClassFormChange({ ...classForm, grade: event.target.value })} />
            </Field>
            <Field label={t("teacherManagement.classManage.create.teacher")}>
              <TextInput value={classForm.teacherName} onChange={event => onClassFormChange({ ...classForm, teacherName: event.target.value })} />
            </Field>
            <Button type="button" variant="primary" onClick={onCreateClass} disabled={busy}>
              {t("teacherManagement.classManage.create.submit")}
            </Button>
          </div>
        </details>
      </aside>

      <section className="management-object-main management-class-workspace">
        <div className="management-class-workspace__head">
          <div>
            <p className="eyebrow">{t("teacherManagement.classManage.import.eyebrow")}</p>
            <h2>{selectedClass?.name || t("teacherManagement.classManage.defaultClass")}</h2>
            <div className="management-class-summary" aria-label={t("teacherManagement.classManage.summaryAria")}>
              <StatusPill tone="info">{t("multiTeacher.classCode.studentCount", { count: activeStudentCount })}</StatusPill>
              <StatusPill tone="neutral">{t("multiTeacher.classCode.hidden")}</StatusPill>
            </div>
          </div>
          <Button
            className="management-class-primary-action"
            type="button"
            variant="primary"
            icon={<UploadCloud size={17} />}
            disabled={!selectedClass}
            onClick={() => selectTab("import")}
          >
            {t("teacherManagement.classManage.importAction")}
          </Button>
        </div>
        <div className="management-class-tabs" role="tablist" aria-label={t("teacherManagement.classManage.tabs.aria")}>
          {(["roster", "import", "settings"] as const).map(tab => (
            <button
              id={`class-workspace-tab-${tab}`}
              type="button"
              role="tab"
              data-class-tab={tab}
              aria-selected={activeTab === tab}
              aria-controls={`class-workspace-panel-${tab}`}
              className={`management-class-tab ${activeTab === tab ? "is-active" : ""}`}
              key={tab}
              onClick={() => selectTab(tab)}
            >
              {t(`teacherManagement.classManage.tabs.${tab}`)}
              {tab === "roster" ? <span>{roster.length}</span> : null}
            </button>
          ))}
        </div>

        {activeTab === "roster" ? (
          <section
            id="class-workspace-panel-roster"
            className="management-class-panel management-class-panel--roster"
            role="tabpanel"
            aria-labelledby="class-workspace-tab-roster"
          >
            <div className="management-class-panel__head">
              <div>
                <h3>{t("multiTeacher.roster.title")}</h3>
                <p>{t("multiTeacher.roster.description")}</p>
              </div>
              <StatusPill tone="neutral">{t("teacherManagement.classManage.rosterCount", { count: roster.length })}</StatusPill>
            </div>
            <div className="management-roster-columns" aria-hidden="true">
              <span>{t("teacherManagement.classManage.rosterColumns.student")}</span>
              <span>{t("teacherManagement.classManage.rosterColumns.number")}</span>
              <span>{t("teacherManagement.classManage.rosterColumns.status")}</span>
              <span>{t("teacherManagement.classManage.rosterColumns.action")}</span>
            </div>
            <div className="management-roster-list">
              {roster.length ? roster.map(student => (
                <article className="management-roster-row" key={student.id}>
                  <span className="management-roster-row__student">
                    <span className="management-roster-row__avatar" aria-hidden="true">{student.displayName.trim().slice(0, 1) || "—"}</span>
                    <strong>{student.displayName}</strong>
                  </span>
                  <small className="management-roster-row__number">{student.studentNo ? `#${student.studentNo}` : "—"}</small>
                  <StatusPill tone={student.status === "ACTIVE" ? "success" : student.status === "NEEDS_REVIEW" ? "warning" : "neutral"}>{t(`multiTeacher.roster.status.${(student.status || "needs_review").toLowerCase()}`)}</StatusPill>
                  <Button className="management-roster-row__action" type="button" variant="secondary" icon={student.status === "ACTIVE" ? <PowerOff size={15} /> : <Power size={15} />} disabled={busy || student.status === "NEEDS_REVIEW"} onClick={() => onUpdateRosterStatus(student)}>
                    {t(student.status === "ACTIVE" ? "multiTeacher.roster.deactivate" : "multiTeacher.roster.activate")}
                  </Button>
                </article>
              )) : <EmptyState title={t("multiTeacher.roster.empty")} description={t("multiTeacher.roster.emptyHint")} />}
            </div>
          </section>
        ) : null}

        {activeTab === "import" ? (
        <div
          id="class-workspace-panel-import"
          className="management-class-panel management-class-panel--import management-step-list"
          role="tabpanel"
          aria-labelledby="class-workspace-tab-import"
        >
          <section className="management-step">
            <span className="management-step__number">1</span>
            <div className="management-step__body">
              <div className="management-step__head">
                <h3>{t("teacherManagement.classManage.import.sourceTitle")}</h3>
                <p>{t("teacherManagement.classManage.import.sourceDescription")}</p>
              </div>
              <div className="management-import-grid">
                <FilePicker
                  accept=".csv,.txt,.xlsx"
                  fileName={classFileName}
                  kind="class"
                  label={t("teacherManagement.classManage.import.fileLabel")}
                  note={t("teacherManagement.classManage.import.fileNote")}
                  onPick={onPickFile}
                />
                <Field label={t("teacherManagement.classManage.import.targetLabel")}>
                  <Select value={selectedClass ? String(selectedClass.id) : ""} onChange={event => onSelectClass(event.target.value)}>
                    {classes.map(item => (
                      <option value={item.id} key={item.id}>
                        {item.name}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
            </div>
          </section>
          <section className="management-step">
            <span className="management-step__number">2</span>
            <div className="management-step__body">
              <div className="management-step__head">
                <h3>{t("teacherManagement.classManage.import.pasteTitle")}</h3>
                <p>{t("teacherManagement.classManage.import.pasteDescription")}</p>
              </div>
              <Field label={t("teacherManagement.classManage.import.pasteLabel")}>
                <TextArea
                  value={classImport.content}
                  onChange={event => onClassImportChange({ ...classImport, content: event.target.value })}
                  placeholder={t("teacherManagement.classManage.import.pastePlaceholder")}
                />
              </Field>
            </div>
          </section>
          <section className="management-step management-step--actions">
            <span className="management-step__number">3</span>
            <div className="management-step__body">
              <div className="management-step__head">
                <h3>{t("teacherManagement.classManage.import.confirmTitle")}</h3>
                <p>{t("teacherManagement.classManage.import.confirmDescription")}</p>
              </div>
              <div className="actions">
                <Button type="button" variant="secondary" onClick={() => onRunImport("preview")} disabled={busy} icon={<UploadCloud size={17} />}>
                  {t("teacherManagement.classManage.import.preview")}
                </Button>
                <Button type="button" variant="primary" onClick={() => onRunImport("commit")} disabled={busy}>
                  {t("teacherManagement.classManage.import.commit")}
                </Button>
              </div>
              <ImportResult result={classImportResult} />
            </div>
          </section>
        </div>
        ) : null}

        {activeTab === "settings" ? (
          <section
            id="class-workspace-panel-settings"
            className="management-class-panel management-class-panel--settings"
            role="tabpanel"
            aria-labelledby="class-workspace-tab-settings"
          >
            <div className="management-class-settings-card">
              <span className="management-step__number">#</span>
              <div className="management-step__body">
                <div className="management-step__head">
                  <h3>{t("multiTeacher.classCode.title")}</h3>
                  <p>{t("multiTeacher.classCode.description")}</p>
                </div>
                <div className="actions">
                  {selectedClass?.joinCode ? <StatusPill tone="success">{selectedClass.joinCode}</StatusPill> : <StatusPill tone="neutral">{t("multiTeacher.classCode.hidden")}</StatusPill>}
                  <StatusPill tone="info">{t("multiTeacher.classCode.studentCount", { count: activeStudentCount })}</StatusPill>
                  <Button type="button" variant="secondary" disabled={busy || !selectedClass} onClick={() => selectedClass && onRotateClassCode(selectedClass.id)}>
                    {t("multiTeacher.classCode.rotate")}
                  </Button>
                </div>
              </div>
            </div>
          </section>
        ) : null}
      </section>
    </section>
  );
}

function filterProblemCatalogItems(problems: ProblemManage[], query: string) {
  const normalizedQuery = query.trim().toLowerCase();
  if (!normalizedQuery) {
    return problems;
  }
  return problems.filter(item => {
    const haystack = `${item.title} ${item.description || ""} ${item.difficulty} ${item.scope} ${item.versionState}`.toLowerCase();
    return haystack.includes(normalizedQuery);
  });
}

function sortProblemCatalogItems(problems: ProblemManage[], mode: ProblemSortMode) {
  const items = [...problems];
  items.sort((first, second) => {
    if (mode === "difficulty") {
      const difficultyDelta =
        (PROBLEM_DIFFICULTY_ORDER[String(first.difficulty || "").toUpperCase()] || 0) -
        (PROBLEM_DIFFICULTY_ORDER[String(second.difficulty || "").toUpperCase()] || 0);
      return difficultyDelta || first.id - second.id;
    }
    if (mode === "timeLimit") {
      return (first.timeLimit || 0) - (second.timeLimit || 0) || first.id - second.id;
    }
    return first.id - second.id;
  });
  return items;
}

function problemDifficultyLabel(value: string | null | undefined, t: (key: string) => string) {
  switch ((value || "").toUpperCase()) {
    case "EASY":
      return t("teacherManagement.problemManage.difficulty.easy");
    case "MEDIUM":
      return t("teacherManagement.problemManage.difficulty.medium");
    case "HARD":
      return t("teacherManagement.problemManage.difficulty.hard");
    default:
      return value || "-";
  }
}

function createVisibleProblemPages(current: number, total: number): Array<number | "gap"> {
  if (total <= 5) {
    return Array.from({ length: total }, (_, index) => index + 1);
  }
  const pages = new Set([1, total, current - 1, current, current + 1]);
  const normalized = [...pages].filter(page => page >= 1 && page <= total).sort((first, second) => first - second);
  const result: Array<number | "gap"> = [];
  normalized.forEach(page => {
    const previous = result[result.length - 1];
    if (typeof previous === "number" && page - previous > 1) {
      result.push("gap");
    }
    result.push(page);
  });
  return result;
}

function renderReadOnlyProblemStatement(value: string, title: string, emptyText: string): ReactNode[] {
  if (!value.trim()) {
    return [<p className="management-problem-readonly__empty" key="empty">{emptyText}</p>];
  }

  const nodes: ReactNode[] = [];
  const codeLines: string[] = [];
  let inCode = false;
  let contentStarted = false;

  function flushCode(key: string) {
    if (!codeLines.length) {
      return;
    }
    nodes.push(
      <pre key={key}>
        <code>{codeLines.join("\n")}</code>
      </pre>
    );
    codeLines.length = 0;
  }

  value.split(/\r?\n/).forEach((line, index) => {
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
      return;
    }
    if (!contentStarted && trimmed.replace(/^#\s+/, "") === title.trim()) {
      contentStarted = true;
      return;
    }
    contentStarted = true;
    if (trimmed.startsWith("### ")) {
      nodes.push(<h4 key={`h4-${index}`}>{trimmed.slice(4)}</h4>);
      return;
    }
    if (trimmed.startsWith("## ")) {
      nodes.push(<h3 key={`h3-${index}`}>{trimmed.slice(3)}</h3>);
      return;
    }
    if (trimmed.startsWith("# ")) {
      nodes.push(<h2 key={`h2-${index}`}>{trimmed.slice(2)}</h2>);
      return;
    }
    if (/^[-*]\s+/.test(trimmed)) {
      nodes.push(
        <p className="management-problem-readonly__list-item" key={`list-${index}`}>
          <span aria-hidden="true">•</span>
          <span>{trimmed.replace(/^[-*]\s+/, "")}</span>
        </p>
      );
      return;
    }
    nodes.push(<p key={`p-${index}`}>{line}</p>);
  });
  flushCode("code-tail");
  return nodes.length ? nodes : [<p className="management-problem-readonly__empty" key="empty">{emptyText}</p>];
}

function ReadOnlyProblemDetail({ problem }: { problem: ProblemManage }) {
  const { t } = useTranslation();
  const samples = problem.sampleTestCases?.length
    ? problem.sampleTestCases
    : problem.testCases.filter(item => !item.hidden).map(item => ({ input: item.input, expectedOutput: item.expectedOutput }));
  const hiddenTestCount = problem.testCases.filter(item => item.hidden).length;
  const metadataGroups = [
    { label: t("teacherManagement.problemManage.readOnly.knowledgePoints"), values: problem.knowledgePoints || [] },
    { label: t("teacherManagement.problemManage.readOnly.algorithmStrategies"), values: problem.algorithmStrategies || [] },
    { label: t("teacherManagement.problemManage.readOnly.commonMistakes"), values: problem.commonMistakes || [] },
    { label: t("teacherManagement.problemManage.readOnly.boundaryTypes"), values: problem.boundaryTypes || [] }
  ].filter(group => group.values.length);

  return (
    <article className="management-problem-readonly">
      <div className="management-problem-readonly__notice" role="note">
        <BookOpen size={19} aria-hidden="true" />
        <div>
          <strong>{t("multiTeacher.problem.readOnlyTitle")}</strong>
          <span>{t("multiTeacher.problem.readOnlyDescription")}</span>
        </div>
      </div>

      <dl className="management-problem-readonly__facts" aria-label={t("teacherManagement.problemManage.readOnly.factsAria")}>
        <div>
          <dt>{t("teacherManagement.problemManage.readOnly.difficulty")}</dt>
          <dd>{problemDifficultyLabel(String(problem.difficulty), t)}</dd>
        </div>
        <div>
          <dt>{t("teacherManagement.problemManage.readOnly.timeLimit")}</dt>
          <dd>{problem.timeLimit} ms</dd>
        </div>
        <div>
          <dt>{t("teacherManagement.problemManage.readOnly.memoryLimit")}</dt>
          <dd>{Math.round(problem.memoryLimit / 1024)} MB</dd>
        </div>
        <div>
          <dt>{t("teacherManagement.problemManage.readOnly.publicSamples")}</dt>
          <dd>{samples.length}</dd>
        </div>
      </dl>

      <section className="management-problem-readonly__section">
        <header>
          <span aria-hidden="true">01</span>
          <div>
            <h3>{t("teacherManagement.problemManage.readOnly.statementTitle")}</h3>
            <p>{t("teacherManagement.problemManage.readOnly.statementDescription")}</p>
          </div>
        </header>
        <div className="management-problem-readonly__statement">
          {renderReadOnlyProblemStatement(problem.description, problem.title, t("teacherManagement.problemManage.readOnly.emptyStatement"))}
        </div>
      </section>

      <section className="management-problem-readonly__section">
        <header>
          <span aria-hidden="true">02</span>
          <div>
            <h3>{t("teacherManagement.problemManage.readOnly.samplesTitle")}</h3>
            <p>{t("teacherManagement.problemManage.readOnly.samplesDescription")}</p>
          </div>
        </header>
        {samples.length ? (
          <div className="management-problem-readonly__samples">
            {samples.map((sample, index) => (
              <article className="management-problem-readonly__sample" key={`${sample.input}-${sample.expectedOutput}-${index}`}>
                <strong>{t("teacherManagement.problemManage.readOnly.sampleNumber", { number: index + 1 })}</strong>
                <div>
                  <span>{t("teacherManagement.problemManage.readOnly.sampleInput")}</span>
                  <pre>{sample.input || t("teacherManagement.problemManage.readOnly.emptyValue")}</pre>
                </div>
                <div>
                  <span>{t("teacherManagement.problemManage.readOnly.sampleOutput")}</span>
                  <pre>{sample.expectedOutput || t("teacherManagement.problemManage.readOnly.emptyValue")}</pre>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <p className="management-problem-readonly__empty">{t("teacherManagement.problemManage.readOnly.noSamples")}</p>
        )}
        {hiddenTestCount ? (
          <p className="management-problem-readonly__hidden-note">
            {t("teacherManagement.problemManage.readOnly.hiddenTests", { count: hiddenTestCount })}
          </p>
        ) : null}
      </section>

      {metadataGroups.length || problem.aiPromptDirection || problem.starterCode ? (
        <section className="management-problem-readonly__section">
          <header>
            <span aria-hidden="true">03</span>
            <div>
              <h3>{t("teacherManagement.problemManage.readOnly.teachingTitle")}</h3>
              <p>{t("teacherManagement.problemManage.readOnly.teachingDescription")}</p>
            </div>
          </header>
          {metadataGroups.length ? (
            <div className="management-problem-readonly__metadata">
              {metadataGroups.map(group => (
                <div key={group.label}>
                  <strong>{group.label}</strong>
                  <span>
                    {group.values.map(value => <em key={value}>{value}</em>)}
                  </span>
                </div>
              ))}
            </div>
          ) : null}
          {problem.aiPromptDirection ? (
            <div className="management-problem-readonly__teaching-note">
              <strong>{t("teacherManagement.problemManage.readOnly.aiDirection")}</strong>
              <p>{problem.aiPromptDirection}</p>
            </div>
          ) : null}
          {problem.starterCode ? (
            <details className="management-problem-readonly__starter">
              <summary>{t("teacherManagement.problemManage.readOnly.starterCode")}</summary>
              <pre><code>{problem.starterCode}</code></pre>
            </details>
          ) : null}
        </section>
      ) : null}
    </article>
  );
}

function ProblemManageSection({
  problems,
  selectedProblemId,
  problemImport,
  problemFileName,
  problemImportResult,
  busy,
  dataSummary,
  onSelectProblem,
  onProblemImportChange,
  onPickFile,
  onRunImport,
  onProblemSaved,
  onSubmitReview,
  onRevise
}: {
  problems: ProblemManage[];
  selectedProblemId: number | null;
  problemImport: { format: string; content: string };
  problemFileName: string;
  problemImportResult: ImportPreview | ImportCommit | null;
  busy: boolean;
  dataSummary?: ReactNode;
  onSelectProblem: (id: number | null) => void;
  onProblemImportChange: (value: { format: string; content: string }) => void;
  onPickFile: (kind: ImportKind, file: File | null) => void | Promise<void>;
  onRunImport: (mode: "preview" | "commit") => void;
  onProblemSaved: (problem: import("../../shared/api/types").Problem) => void;
  onSubmitReview: (problemId: number) => void;
  onRevise: (problemId: number) => void;
}) {
  const { t } = useTranslation();
  const [problemQuery, setProblemQuery] = useState("");
  const [creatingProblem, setCreatingProblem] = useState(false);
  const [createDraftSignal, setCreateDraftSignal] = useState(0);
  const [problemSortMode, setProblemSortMode] = useState<ProblemSortMode>("id");
  const [problemPage, setProblemPage] = useState(1);
  const filteredProblems = useMemo(() => filterProblemCatalogItems(problems, problemQuery), [problemQuery, problems]);
  const sortedProblems = useMemo(() => sortProblemCatalogItems(filteredProblems, problemSortMode), [filteredProblems, problemSortMode]);
  const problemPageCount = Math.max(1, Math.ceil(sortedProblems.length / PROBLEM_PAGE_SIZE));
  const currentProblemPage = Math.min(problemPage, problemPageCount);
  const pagedProblems = useMemo(() => {
    const offset = (currentProblemPage - 1) * PROBLEM_PAGE_SIZE;
    return sortedProblems.slice(offset, offset + PROBLEM_PAGE_SIZE);
  }, [currentProblemPage, sortedProblems]);
  const problemPages = useMemo(() => createVisibleProblemPages(currentProblemPage, problemPageCount), [currentProblemPage, problemPageCount]);
  const firstProblemIndex = sortedProblems.length ? (currentProblemPage - 1) * PROBLEM_PAGE_SIZE + 1 : 0;
  const lastProblemIndex = Math.min(currentProblemPage * PROBLEM_PAGE_SIZE, sortedProblems.length);
  const problemPlaceholderCount = Math.max(0, PROBLEM_PAGE_SIZE - pagedProblems.length);
  const selectedProblem = creatingProblem ? null : sortedProblems.find(item => item.id === selectedProblemId) || sortedProblems[0] || null;
  const problemSortLabel = t(`teacherManagement.problemManage.sort.${problemSortMode}`);

  useEffect(() => {
    setProblemPage(page => Math.min(page, problemPageCount));
  }, [problemPageCount]);

  useEffect(() => {
    if (creatingProblem || !sortedProblems.length) {
      return;
    }
    if (selectedProblemId && sortedProblems.some(item => item.id === selectedProblemId)) {
      return;
    }
    onSelectProblem(sortedProblems[0].id);
  }, [creatingProblem, onSelectProblem, selectedProblemId, sortedProblems]);

  function startNewProblem() {
    setCreatingProblem(true);
    setCreateDraftSignal(value => value + 1);
    onSelectProblem(null);
  }

  function selectProblem(id: number) {
    setCreatingProblem(false);
    onSelectProblem(id);
  }

  function updateProblemQuery(value: string) {
    const nextItems = sortProblemCatalogItems(filterProblemCatalogItems(problems, value), problemSortMode);
    setProblemQuery(value);
    setProblemPage(1);
    if (!creatingProblem && nextItems[0]) {
      onSelectProblem(nextItems[0].id);
    } else if (!creatingProblem) {
      onSelectProblem(null);
    }
  }

  function cycleProblemSort() {
    const nextMode: ProblemSortMode = problemSortMode === "id" ? "difficulty" : problemSortMode === "difficulty" ? "timeLimit" : "id";
    const nextItems = sortProblemCatalogItems(filteredProblems, nextMode);
    setProblemSortMode(nextMode);
    setProblemPage(1);
    if (!creatingProblem && nextItems[0]) {
      onSelectProblem(nextItems[0].id);
    } else if (!creatingProblem) {
      onSelectProblem(null);
    }
  }

  function goToProblemPage(page: number) {
    const targetPage = Math.min(problemPageCount, Math.max(1, page));
    const firstItem = sortedProblems[(targetPage - 1) * PROBLEM_PAGE_SIZE];
    setProblemPage(targetPage);
    if (firstItem) {
      setCreatingProblem(false);
      onSelectProblem(firstItem.id);
    }
  }

  return (
    <section className="management-object-workbench management-object-workbench--problems">
      <aside className="management-object-list" aria-label={t("teacherManagement.problemManage.listAria")}>
        <div className="management-object-list__head">
          <span>
            <strong>{t("teacherManagement.problemManage.editorTitle")}</strong>
            <small>
              {t("teacherManagement.problemManage.visibleCount", { visible: filteredProblems.length, total: problems.length })}
            </small>
          </span>
          {dataSummary || <StatusPill tone="neutral">{t("teacherManagement.problemManage.problemCount", { count: problems.length })}</StatusPill>}
        </div>
        <div className="management-problem-tools">
          <div className="management-problem-tool-row">
            <Button type="button" variant={creatingProblem ? "primary" : "secondary"} onClick={startNewProblem} icon={<Plus size={16} />}>
              {t("teacherManagement.problemManage.newProblem")}
            </Button>
            <button
              type="button"
              className="management-problem-filter-button"
              onClick={cycleProblemSort}
              title={t("teacherManagement.problemManage.currentSort", { sort: problemSortLabel })}
              aria-label={t("teacherManagement.problemManage.switchSort", { sort: problemSortLabel })}
            >
              <SlidersHorizontal size={16} aria-hidden="true" />
              <span>{problemSortLabel}</span>
            </button>
          </div>
          <label className="management-problem-search">
            <Search size={15} aria-hidden="true" />
            <TextInput value={problemQuery} onChange={event => updateProblemQuery(event.target.value)} placeholder={t("teacherManagement.problemManage.searchPlaceholder")} />
          </label>
        </div>
        <div className="management-problem-list">
          {pagedProblems.length ? (
            <>
              {pagedProblems.map(item => (
                <button
                  type="button"
                  className={`management-object-row ${item.id === selectedProblem?.id ? "is-active" : ""}`}
                  key={item.id}
                  onClick={() => selectProblem(item.id)}
                >
                  <span className="management-object-row__number">{String(item.id).padStart(4, "0")}</span>
                  <span className="management-object-row__content">
                    <strong>{item.title}</strong>
                    <small>{displayText(item.description || "", t("teacherManagement.problemManage.noSummary"))}</small>
                  </span>
                  <span className="management-object-row__state">
                    <span>{t(`multiTeacher.problem.scope.${item.scope.toLowerCase()}`)}</span>
                    <strong>v{item.versionNo}</strong>
                  </span>
                </button>
              ))}
              {Array.from({ length: problemPlaceholderCount }, (_, index) => (
                <div className="management-object-row management-object-row--placeholder" aria-hidden="true" key={`placeholder-${currentProblemPage}-${index}`} />
              ))}
            </>
          ) : (
            <EmptyState title={problems.length ? t("teacherManagement.problemManage.emptyFilteredTitle") : t("teacherManagement.problemManage.emptyTitle")} description={t("teacherManagement.problemManage.emptyDescription")} />
          )}
        </div>
        {sortedProblems.length ? (
          <div className="management-problem-pagination" aria-label={t("teacherManagement.problemManage.paginationAria")}>
            <span>
              {firstProblemIndex}-{lastProblemIndex} / {sortedProblems.length}
            </span>
            <div>
              <button type="button" onClick={() => goToProblemPage(currentProblemPage - 1)} disabled={currentProblemPage === 1} aria-label={t("teacherManagement.problemManage.previousPage")}>
                <ChevronLeft size={15} />
              </button>
              {problemPages.map((page, index) =>
                page === "gap" ? (
                  <span className="management-problem-pagination__gap" key={`gap-${index}`}>
                    ...
                  </span>
                ) : (
                  <button
                    type="button"
                    className={page === currentProblemPage ? "is-active" : ""}
                    key={page}
                    onClick={() => goToProblemPage(page)}
                    aria-label={t("teacherManagement.problemManage.pageAria", { page })}
                    aria-current={page === currentProblemPage ? "page" : undefined}
                  >
                    {page}
                  </button>
                )
              )}
              <button
                type="button"
                onClick={() => goToProblemPage(currentProblemPage + 1)}
                disabled={currentProblemPage === problemPageCount}
                aria-label={t("teacherManagement.problemManage.nextPage")}
              >
                <ChevronRight size={15} />
              </button>
            </div>
          </div>
        ) : null}
        <details className="management-import-drawer">
          <summary>
            <UploadCloud size={15} />
            {t("teacherManagement.problemManage.import.title")}
          </summary>
          <div className="management-import-drawer__body">
            <FilePicker
              accept=".md,.markdown,.json,.csv,.txt,.xlsx"
              fileName={problemFileName}
              kind="problem"
              label={t("teacherManagement.problemManage.import.fileLabel")}
              note={t("teacherManagement.problemManage.import.fileNote")}
              onPick={onPickFile}
            />
            <Field label={t("teacherManagement.problemManage.import.pasteLabel")}>
              <TextArea
                value={problemImport.content}
                onChange={event => onProblemImportChange({ ...problemImport, content: event.target.value })}
                placeholder={t("teacherManagement.problemManage.import.pastePlaceholder")}
              />
            </Field>
            <div className="actions">
              <Button type="button" variant="secondary" onClick={() => onRunImport("preview")} disabled={busy} icon={<UploadCloud size={17} />}>
                {t("teacherManagement.problemManage.import.preview")}
              </Button>
              <Button type="button" variant="primary" onClick={() => onRunImport("commit")} disabled={busy}>
                {t("teacherManagement.problemManage.import.commit")}
              </Button>
            </div>
            <ImportResult result={problemImportResult} />
          </div>
        </details>
      </aside>

      <section className="management-object-main">
        {selectedProblem || creatingProblem ? (
          <>
            {selectedProblem ? (
              <div className="management-object-main__head">
                <div>
                  <p className="eyebrow">{t(`multiTeacher.problem.scope.${selectedProblem.scope.toLowerCase()}`)}</p>
                  <h2>{selectedProblem.title}</h2>
                </div>
                <div className="actions">
                  <StatusPill tone={selectedProblem.versionState === "PUBLISHED" ? "success" : selectedProblem.versionState === "DRAFT" ? "neutral" : "info"}>
                    {t(`multiTeacher.problem.state.${selectedProblem.versionState.toLowerCase()}`)} · v{selectedProblem.versionNo}
                  </StatusPill>
                  {selectedProblem.scope === "PRIVATE" && selectedProblem.versionState === "DRAFT" ? (
                    <Button type="button" variant="secondary" disabled={busy} onClick={() => onSubmitReview(selectedProblem.id)}>
                      {t("multiTeacher.problem.submitReview")}
                    </Button>
                  ) : null}
                  {selectedProblem.scope === "SHARED" && selectedProblem.versionState === "PUBLISHED" ? (
                    <Button type="button" variant="secondary" disabled={busy} onClick={() => onRevise(selectedProblem.id)}>
                      {t("multiTeacher.problem.revise")}
                    </Button>
                  ) : null}
                </div>
              </div>
            ) : null}
            {creatingProblem || selectedProblem?.versionState === "DRAFT" ? (
              <TaskEditorPage
                embedded
                selectedProblemId={selectedProblem?.id || null}
                createDraftSignal={createDraftSignal}
                showCatalogDrawer={false}
                onSaved={problem => {
                  setCreatingProblem(false);
                  onProblemSaved(problem);
                }}
              />
            ) : (
              selectedProblem ? <ReadOnlyProblemDetail problem={selectedProblem} /> : null
            )}
          </>
        ) : (
          <div className="management-object-empty">
            <EmptyState
              title={problems.length ? t("teacherManagement.problemManage.emptyFilteredTitle") : t("teacherManagement.problemManage.emptyTitle")}
              description={problems.length ? t("teacherManagement.problemManage.emptyEditorFilteredDescription") : t("teacherManagement.problemManage.emptyEditorDescription")}
            />
          </div>
        )}
      </section>
    </section>
  );
}

function ReadinessPanel({
  readiness,
  usage,
  busy,
  summary,
  onRefresh,
  onAiSmoke
}: {
  readiness: Readiness | null;
  usage: TeacherAiUsage | null;
  busy: boolean;
  summary?: ReactNode;
  onRefresh: () => void;
  onAiSmoke: () => void;
}) {
  const { t } = useTranslation();
  const status = readiness?.status || "UNKNOWN";
  const tone = status === "READY" ? "success" : status === "BLOCKED" ? "danger" : "warning";
  const blocking = readiness?.checks.filter(item => item.blocking && item.status !== "PASS") || [];
  const warnings = readiness?.checks.filter(item => item.status !== "PASS" && !(item.blocking && item.status !== "PASS")) || [];
  const visibleChecks = [...blocking, ...warnings].slice(0, 6);
  const compactChecks = visibleChecks.slice(0, 3);
  const statusDescription =
    status === "READY"
      ? t("teacherManagement.readiness.canStart")
      : status === "BLOCKED"
        ? t("teacherManagement.readiness.cannotStart")
        : t("teacherManagement.readiness.trialStart");
  return (
    <section className="management-readiness management-readiness--compact" aria-label={t("teacherManagement.readiness.aria")}>
      <div className="management-readiness__chips">
        <span><StatusPill tone={usage && usage.remainingUnits > 0 ? "success" : "warning"}>{usage ? usage.remainingUnits : "-"}</StatusPill>{t("multiTeacher.quota.remaining")}</span>
        <span>{t("multiTeacher.quota.used", { used: usage?.usedUnits || 0, total: usage ? usage.baseUnits + usage.additionalUnits : 0 })}</span>
        {usage ? <span>{t("multiTeacher.quota.resets", { time: new Date(usage.resetsAt).toLocaleString() })}</span> : null}
      </div>
      <div className="management-readiness__head">
        <div>
          <span>{t("teacherManagement.readiness.summaryLabel")}</span>
          <h2>{t("teacherManagement.readiness.title")}</h2>
          <p>
            {statusDescription}
            {readiness ? t("teacherManagement.readiness.issueSummary", { blocking: blocking.length, warnings: warnings.length }) : ""}
          </p>
        </div>
        <StatusPill tone={tone}>{readinessStatusText(status, t)}</StatusPill>
        {summary ? <span className="management-readiness__summary">{summary}</span> : null}
        <div className="management-readiness__actions">
          <Button type="button" variant="secondary" onClick={() => void onRefresh()}>
            {t("teacherManagement.readiness.refresh")}
          </Button>
          <Button type="button" variant="primary" onClick={() => void onAiSmoke()} disabled={busy}>
            {busy ? t("teacherManagement.readiness.running") : t("teacherManagement.readiness.runAi")}
          </Button>
        </div>
      </div>
      {readiness ? (
        visibleChecks.length ? (
          <>
            <div className="management-readiness__chips">
              {compactChecks.map(item => (
                <span key={item.id} title={`${item.message} ${item.action}`}>
                  <StatusPill tone={item.status === "FAIL" ? "danger" : item.status === "WARN" ? "warning" : "neutral"}>
                    {readinessCheckStatusText(item.status, t)}
                  </StatusPill>
                  {item.label}
                </span>
              ))}
              {visibleChecks.length > compactChecks.length ? <small>{t("teacherManagement.readiness.moreCompact", { count: visibleChecks.length - compactChecks.length })}</small> : null}
            </div>
            <details className="management-readiness__details">
              <summary>{t("teacherManagement.readiness.checkDetails")}</summary>
              <div className="management-readiness__checks">
                {visibleChecks.map(item => (
                  <article key={item.id} title={`${item.message} ${item.action}`}>
                    <strong>{item.label}</strong>
                    <StatusPill tone={item.status === "FAIL" ? "danger" : item.status === "WARN" ? "warning" : "neutral"}>
                      {readinessCheckStatusText(item.status, t)}
                    </StatusPill>
                    <p>{item.message}</p>
                  </article>
                ))}
              </div>
            </details>
          </>
        ) : (
          <p className="management-readiness__ok">{t("teacherManagement.readiness.allClear")}</p>
        )
      ) : (
        <p className="management-readiness__ok">{t("teacherManagement.readiness.loadingDescription")}</p>
      )}
    </section>
  );
}

function readinessStatusText(status: string, t: (key: string, params?: Record<string, string | number>) => string) {
  const key = status.toUpperCase();
  if (key === "READY") return t("teacherManagement.readiness.ready");
  if (key === "BLOCKED") return t("teacherManagement.readiness.blocked");
  if (key === "DEGRADED") return t("teacherManagement.readiness.degraded");
  return t("teacherManagement.readiness.unknown");
}

function readinessCheckStatusText(status: string, t: (key: string, params?: Record<string, string | number>) => string) {
  const key = status.toUpperCase();
  if (key === "FAIL") return t("teacherManagement.readiness.checkFail");
  if (key === "WARN") return t("teacherManagement.readiness.checkWarn");
  if (key === "PASS") return t("teacherManagement.readiness.checkPass");
  return status;
}

type LibraryCodeGroup = {
  code: string;
  label: string;
  path: string;
  orderPath: number[];
  known: boolean;
  items: AiStandardLibraryItem[];
};
type LibraryBranchGroup = {
  root: string;
  orderPath: number[];
  count: number;
  groups: LibraryCodeGroup[];
};

function StandardLibraryManager({
  items,
  growthCandidates,
  governanceSummary,
  knowledgeTree,
  filters,
  draft,
  busy,
  selectedId,
  onFiltersChange,
  onReload,
  onNew,
  onSelect,
  onDraftChange,
  onSave,
  onToggle,
  onReloadGrowth,
  onUpdateGrowthCandidate,
  onReviewGrowthCandidate
}: {
  items: AiStandardLibraryItem[];
  growthCandidates: AiStandardLibraryGrowthCandidate[];
  governanceSummary: AiStandardLibraryGrowthGovernanceSummary | null;
  knowledgeTree: InformaticsKnowledgeNode[];
  filters: LibraryFilters;
  draft: LibraryDraft;
  busy: boolean;
  selectedId: number | null;
  onFiltersChange: (filters: LibraryFilters) => void;
  onReload: (filters?: LibraryFilters) => void;
  onNew: () => void;
  onSelect: (item: AiStandardLibraryItem) => void;
  onDraftChange: (draft: LibraryDraft) => void;
  onSave: (draft: LibraryDraft) => void;
  onToggle: (item: AiStandardLibraryItem) => void;
  onReloadGrowth: () => void;
  onUpdateGrowthCandidate: (id: number, payload: AiStandardLibraryGrowthCandidatePayload) => void;
  onReviewGrowthCandidate: (id: number, action: GrowthCandidateAction, payload: AiStandardLibraryGrowthCandidatePayload) => void;
}) {
  const { t } = useTranslation();
  const [editorOpen, setEditorOpen] = useState(false);
  const [activeView, setActiveView] = useState<StandardLibraryView>("library");
  const visibleItems = items.filter(item => libraryItemMatchesFilters(item, filters));
  const skillCount = visibleItems.filter(item => item.layer === "SKILL_UNIT").length;
  const mistakeCount = visibleItems.filter(item => item.layer === "MISTAKE_POINT").length;
  const categoryOptions = Array.from(new Set(items.map(item => item.category).filter(Boolean))).sort((left, right) => left.localeCompare(right, "zh-Hans-CN"));
  const selectedItem = visibleItems.find(item => item.id === selectedId) || items.find(item => item.id === selectedId) || null;
  const knowledgeGroups = buildKnowledgePathGroups(visibleItems, knowledgeTree);
  const knowledgeBranches = buildKnowledgeBranches(knowledgeGroups);
  const unlinkedItems = visibleItems.filter(item => !knowledgeCodes(item).length);
  const hasActiveLibraryFilters = Boolean(filters.query.trim() || filters.layer || filters.category || filters.enabled);
  const layerOptions: Array<{ value: LibraryFilters["layer"]; label: string }> = [
    { value: "", label: "全部" },
    { value: "SKILL_UNIT", label: "能力点" },
    { value: "MISTAKE_POINT", label: "易错点" }
  ];
  const enabledOptions: Array<{ value: LibraryEnabledFilter; label: string }> = [
    { value: "", label: "全部" },
    { value: "true", label: "启用" },
    { value: "false", label: "停用" }
  ];
  const pendingGrowthCount = growthCandidates.filter(candidate => growthCandidatePending(candidate.status)).length;

  function viewTabs() {
    const tabs: Array<{ value: StandardLibraryView; label: string; icon: ReactNode; badge?: number }> = [
      { value: "library", label: t("teacherManagement.aiLibrary.tabs.library"), icon: <BookOpen size={15} />, badge: visibleItems.length },
      { value: "review", label: t("teacherManagement.aiLibrary.tabs.governance"), icon: <ShieldCheck size={15} />, badge: pendingGrowthCount }
    ];
    return (
      <div className="standard-library-view-tabs" role="tablist" aria-label="AI 标准库视图">
        {tabs.map(tab => (
          <button
            type="button"
            role="tab"
            className={`standard-library-view-tab ${activeView === tab.value ? "is-active" : ""}`}
            aria-selected={activeView === tab.value}
            key={tab.value}
            onClick={() => setActiveView(tab.value)}
          >
            {tab.icon}
            <span>{tab.label}</span>
            <StatusPill tone={tab.value === "review" && (tab.badge || 0) > 0 ? "warning" : "neutral"}>{tab.badge || 0}</StatusPill>
          </button>
        ))}
      </div>
    );
  }

  useEffect(() => {
    if (!editorOpen) {
      return undefined;
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setEditorOpen(false);
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [editorOpen]);

  function patchDraft(values: Partial<LibraryDraft>) {
    onDraftChange({ ...draft, ...values });
  }

  function updateFilters(values: Partial<LibraryFilters>) {
    onFiltersChange({ ...filters, ...values });
  }

  function openNewDraft() {
    onNew();
    setEditorOpen(true);
  }

  function selectTreeItem(item: AiStandardLibraryItem) {
    onSelect(item);
    setEditorOpen(false);
  }

  function openSelectedEditor() {
    if (selectedItem) {
      onSelect(selectedItem);
      setEditorOpen(true);
    }
  }

  function openChildDraft() {
    if (!selectedItem) {
      return;
    }
    onNew();
    onDraftChange({
      ...emptyLibraryDraft(),
      category: selectedItem.category,
      skillUnitCode: selectedItem.layer === "SKILL_UNIT" ? selectedItem.code : selectedItem.skillUnitCode || "",
      knowledgeNodeCodes: linesToText(selectedItem.knowledgeNodeCodes)
    });
    setEditorOpen(true);
  }

  function renderLibraryItem(item: AiStandardLibraryItem) {
    return (
      <button
        type="button"
        key={item.id}
        className={`management-object-row standard-library-list__item standard-library-tree__item ${item.id === selectedId ? "is-active" : ""}`}
        aria-current={item.id === selectedId ? "true" : undefined}
        onClick={() => selectTreeItem(item)}
      >
        <span className="standard-library-tree__item-main">
          <strong>{item.name}</strong>
          <small>{[item.code, item.category].filter(Boolean).join(" · ")}</small>
        </span>
        <span className="standard-library-tree__item-meta">
          <StatusPill tone={item.layer === "SKILL_UNIT" ? "info" : "warning"}>{layerLabel(item.layer)}</StatusPill>
          {item.enabled ? null : <StatusPill tone="neutral">停用</StatusPill>}
        </span>
      </button>
    );
  }

  function renderKnowledgeGroup(group: LibraryCodeGroup) {
    const groupOpen = hasActiveLibraryFilters || group.items.some(item => item.id === selectedId);
    return (
      <details className={`standard-library-tree__node ${group.known ? "" : "standard-library-tree__node--loose"}`} open={groupOpen} key={group.code}>
        <summary>
          <span>
            <strong>{group.label}</strong>
            <small>{group.path}</small>
          </span>
          <StatusPill tone={group.known ? "neutral" : "warning"}>{group.items.length} 条</StatusPill>
        </summary>
        <div className="standard-library-tree__children">{group.items.map(renderLibraryItem)}</div>
      </details>
    );
  }

  function renderKnowledgeBranch(branch: LibraryBranchGroup) {
    const branchOpen = hasActiveLibraryFilters || branch.groups.some(group => group.items.some(item => item.id === selectedId));
    return (
      <details className="standard-library-tree__branch" open={branchOpen} key={branch.root}>
        <summary className="standard-library-tree__branch-head">
          <span>
            <strong>{branch.root}</strong>
            <small>{branch.groups.length} 个知识点</small>
          </span>
          <StatusPill tone="neutral">{branch.count} 条</StatusPill>
        </summary>
        <div className="standard-library-tree__branch-body">{branch.groups.map(renderKnowledgeGroup)}</div>
      </details>
    );
  }

  if (activeView === "review") {
    return (
      <section className="management-object-workbench management-object-workbench--ai standard-library-manager">
        {viewTabs()}
        <StandardLibraryGrowthReview
          candidates={growthCandidates}
          summary={governanceSummary}
          busy={busy}
          onReload={onReloadGrowth}
          onUpdate={onUpdateGrowthCandidate}
          onReview={onReviewGrowthCandidate}
        />
      </section>
    );
  }

  return (
    <section className={`management-object-workbench management-object-workbench--ai standard-library-manager ${editorOpen ? "is-editor-open" : ""}`}>
      {viewTabs()}
      <div className={`standard-library-canvas ${selectedItem && !editorOpen ? "has-action-panel" : ""}`} aria-label="AI 标准库知识树">
        <div className="standard-library-canvas__bar">
          <div className="standard-library-canvas__summary" aria-label="当前筛选结果">
            <StatusPill tone="info">能力点 {skillCount}</StatusPill>
            <StatusPill tone="warning">易错点 {mistakeCount}</StatusPill>
            <StatusPill tone="neutral">当前 {visibleItems.length}</StatusPill>
          </div>
          <div className="actions">
            <button type="button" className="standard-library-icon-button" aria-label="刷新标准库" title="刷新标准库" onClick={() => onReload(filters)} disabled={busy}>
              <RefreshCw size={17} />
            </button>
            <Button type="button" variant="primary" icon={<Plus size={16} />} onClick={openNewDraft} disabled={busy}>
              新建
            </Button>
          </div>
        </div>
        <div className="standard-library-command" role="search" aria-label="筛选 AI 标准库">
          <label className="standard-library-search">
            <Search size={16} aria-hidden="true" />
            <TextInput
              aria-label="搜索条目 ID、名称或知识点"
              className="control standard-library-search__input"
              name="query"
              value={filters.query}
              onChange={event => updateFilters({ query: event.target.value })}
              onKeyDown={event => {
                if (event.key === "Enter") {
                  onReload({ ...filters, query: event.currentTarget.value });
                }
              }}
              placeholder="ID、名称、知识点"
            />
          </label>
          <div className="standard-library-segments" aria-label="类型筛选">
            {layerOptions.map(option => (
              <button
                type="button"
                className={`standard-library-chip ${filters.layer === option.value ? "is-active" : ""}`}
                aria-pressed={filters.layer === option.value}
                key={`layer-${option.value || "all"}`}
                onClick={() => updateFilters({ layer: option.value })}
              >
                {option.label}
              </button>
            ))}
          </div>
          <div className="standard-library-segments" aria-label="状态筛选">
            {enabledOptions.map(option => (
              <button
                type="button"
                className={`standard-library-chip ${filters.enabled === option.value ? "is-active" : ""}`}
                aria-pressed={filters.enabled === option.value}
                key={`enabled-${option.value || "all"}`}
                onClick={() => updateFilters({ enabled: option.value })}
              >
                {option.label}
              </button>
            ))}
          </div>
          <Select
            aria-label="分类筛选"
            className="control standard-library-command__select"
            name="category"
            value={filters.category}
            onChange={event => updateFilters({ category: event.target.value })}
          >
            <option value="">全部分类</option>
            {categoryOptions.map(category => (
              <option value={category} key={category}>
                {category}
              </option>
            ))}
          </Select>
        </div>
        {visibleItems.length ? (
          <div className="standard-library-tree standard-library-tree--canvas" role="tree" aria-label="按知识树管理 AI 标准库">
            {knowledgeBranches.map(renderKnowledgeBranch)}
            {unlinkedItems.length ? (
              <details className="standard-library-tree__branch standard-library-tree__branch--loose" open>
                <summary className="standard-library-tree__branch-head">
                  <span>
                    <strong>未关联知识点</strong>
                    <small>需要补充 knowledgeNodeCodes</small>
                  </span>
                  <StatusPill tone="warning">{unlinkedItems.length} 条</StatusPill>
                </summary>
                <div className="standard-library-tree__children standard-library-tree__children--loose">{unlinkedItems.map(renderLibraryItem)}</div>
              </details>
            ) : null}
          </div>
        ) : (
          <EmptyState title="暂无条目" description="换一个筛选条件，或新建一个标准库条目。" />
        )}
        {selectedItem && !editorOpen ? (
          <section className="standard-library-action-panel" aria-label="当前条目操作">
            <div className="standard-library-action-panel__main">
              <span>
                <StatusPill tone={selectedItem.layer === "SKILL_UNIT" ? "info" : "warning"}>{layerLabel(selectedItem.layer)}</StatusPill>
                <StatusPill tone={selectedItem.enabled ? "success" : "neutral"}>{selectedItem.enabled ? "启用" : "停用"}</StatusPill>
              </span>
              <strong>{selectedItem.name}</strong>
              <small>{[selectedItem.code, selectedItem.category].filter(Boolean).join(" · ")}</small>
            </div>
            <div className="standard-library-action-panel__actions">
              <Button type="button" variant="secondary" icon={<BookOpen size={16} />} onClick={openSelectedEditor} disabled={busy}>
                编辑
              </Button>
              {selectedItem.layer === "SKILL_UNIT" ? (
                <Button type="button" variant="secondary" icon={<Plus size={16} />} onClick={openChildDraft} disabled={busy}>
                  新增易错点
                </Button>
              ) : null}
              <Button
                type="button"
                variant={selectedItem.enabled ? "secondary" : "primary"}
                icon={selectedItem.enabled ? <PowerOff size={16} /> : <Power size={16} />}
                onClick={() => onToggle(selectedItem)}
                disabled={busy}
              >
                {selectedItem.enabled ? "停用" : "启用"}
              </Button>
            </div>
          </section>
        ) : null}
      </div>

      {editorOpen ? (
        <>
          <button type="button" className="standard-library-editor-backdrop" aria-label="关闭编辑面板" onClick={() => setEditorOpen(false)} />
          <aside className="standard-library-editor-drawer" role="dialog" aria-modal="true" aria-labelledby="standard-library-editor-title">
            <div className="standard-library-editor">
          <div className="standard-library-editor__head">
            <div>
              <p className="eyebrow">{draft.layer === "SKILL_UNIT" ? "能力点" : "易错点"}</p>
              <h3 id="standard-library-editor-title">{draft.id ? draft.name || "编辑条目" : "新建条目"}</h3>
              <p>{draft.id ? `${draft.code} · ${draft.category}` : "保存后进入当前学校标准库"}</p>
            </div>
            <div className="actions">
              {selectedItem ? (
                <Button
                  type="button"
                  variant={selectedItem.enabled ? "secondary" : "primary"}
                  icon={selectedItem.enabled ? <PowerOff size={17} /> : <Power size={17} />}
                  onClick={() => onToggle(selectedItem)}
                  disabled={busy}
                >
                  {selectedItem.enabled ? "停用" : "启用"}
                </Button>
              ) : null}
              <Button type="button" variant="primary" icon={<Save size={17} />} onClick={event => onSave(readDraftFromElement(event.currentTarget, draft))} disabled={busy}>
                保存
              </Button>
              <button type="button" className="standard-library-editor__close" aria-label="关闭编辑面板" title="关闭编辑面板" onClick={() => setEditorOpen(false)}>
                <X size={18} />
              </button>
            </div>
          </div>

          <section className="standard-library-editor__section">
            <h4>基础信息</h4>
            <div className="form-grid standard-library-editor__core">
              <Field label="类型">
                <Select name="layer" value={draft.layer} onChange={event => patchDraft({ layer: event.target.value as AiStandardLibraryLayer })}>
                  <option value="SKILL_UNIT">能力点</option>
                  <option value="MISTAKE_POINT">易错点</option>
                </Select>
              </Field>
              <Field label="条目 ID">
                <TextInput name="code" value={draft.code} onChange={event => patchDraft({ code: event.target.value })} placeholder="SK_LOOP_ENDPOINT_INCLUSION" />
              </Field>
              <Field label="分类">
                <TextInput name="category" value={draft.category} onChange={event => patchDraft({ category: event.target.value })} />
              </Field>
              <Field label="名称">
                <TextInput name="name" value={draft.name} onChange={event => patchDraft({ name: event.target.value })} />
              </Field>
            </div>
          </section>

          <section className="standard-library-editor__section">
            <h4>教学解释</h4>
            <div className="standard-library-editor__textarea-grid">
              <Field label={draft.layer === "SKILL_UNIT" ? "能力定义" : "易错点定义"}>
                <TextArea name="description" value={draft.description} onChange={event => patchDraft({ description: event.target.value })} />
              </Field>
              <Field label={draft.layer === "SKILL_UNIT" ? "学习目标" : "学生常见误解"}>
                <TextArea name="studentExplanation" value={draft.studentExplanation} onChange={event => patchDraft({ studentExplanation: event.target.value })} />
              </Field>
              <Field label="教师解释">
                <TextArea name="teacherExplanation" value={draft.teacherExplanation} onChange={event => patchDraft({ teacherExplanation: event.target.value })} />
              </Field>
            </div>
          </section>

          <details className="standard-library-editor__section" open>
            <summary>关联与规则</summary>
            <div className="form-grid standard-library-editor__meta">
              {draft.layer === "MISTAKE_POINT" ? (
                <>
                  <Field label="所属能力点">
                    <TextInput name="skillUnitCode" value={draft.skillUnitCode} onChange={event => patchDraft({ skillUnitCode: event.target.value })} placeholder="SK_..." />
                  </Field>
                  <Field label="易错类型">
                    <Select name="mistakeType" value={draft.mistakeType} onChange={event => patchDraft({ mistakeType: event.target.value })}>
                      <option value="">未指定</option>
                      <option value="CONCEPT">概念</option>
                      <option value="BOUNDARY">边界</option>
                      <option value="IO_FORMAT">输入输出</option>
                      <option value="STATE">状态</option>
                      <option value="TRANSITION">转移</option>
                      <option value="COMPLEXITY">复杂度</option>
                      <option value="MODELING">建模</option>
                      <option value="SYNTAX">语法</option>
                      <option value="RUNTIME">运行时</option>
                      <option value="DEBUGGING">调试习惯</option>
                    </Select>
                  </Field>
                  <Field label="常见误解">
                    <TextInput name="commonMisconception" value={draft.commonMisconception} onChange={event => patchDraft({ commonMisconception: event.target.value })} />
                  </Field>
                </>
              ) : null}
              <Field label="严重度">
                <Select name="severity" value={draft.severity} onChange={event => patchDraft({ severity: event.target.value })}>
                  <option value="">未指定</option>
                  <option value="LOW">LOW</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HIGH">HIGH</option>
                </Select>
              </Field>
              <Field label="版本">
                <TextInput name="libraryVersion" value={draft.libraryVersion} onChange={event => patchDraft({ libraryVersion: event.target.value })} />
              </Field>
            </div>
          </details>

          <details className="standard-library-editor__section">
            <summary>证据与建议</summary>
            <div className="standard-library-editor__textarea-grid standard-library-editor__signals">
              <Field label="关联知识点">
                <TextArea name="knowledgeNodeCodes" value={draft.knowledgeNodeCodes} onChange={event => patchDraft({ knowledgeNodeCodes: event.target.value })} placeholder="每行一个知识点 ID" />
              </Field>
              <Field label="适用语言">
                <TextArea name="applicableLanguages" value={draft.applicableLanguages} onChange={event => patchDraft({ applicableLanguages: event.target.value })} placeholder="PYTHON&#10;CPP17" />
              </Field>
              <Field label="前置知识">
                <TextArea name="prerequisiteKnowledgeCodes" value={draft.prerequisiteKnowledgeCodes} onChange={event => patchDraft({ prerequisiteKnowledgeCodes: event.target.value })} placeholder="每行一个知识点或能力点 ID" />
              </Field>
              <Field label="相关条目">
                <TextArea name="relatedItems" value={draft.relatedItems} onChange={event => patchDraft({ relatedItems: event.target.value })} placeholder="每行一个条目 ID" />
              </Field>
            </div>
          </details>
            </div>
          </aside>
        </>
      ) : null}
    </section>
  );
}

type GrowthCandidateDraft = {
  layer: AiStandardLibraryLayer;
  suggestedCode: string;
  suggestedName: string;
  suggestedPath: string;
  sourceProblemId: string;
  sourceSubmissionId: string;
  similarExistingItems: string;
  evidenceRefs: string;
  evidenceStatus: string;
  changeReason: string;
  confidence: string;
  teacherNote: string;
};

function StandardLibraryGrowthReview({
  candidates,
  summary,
  busy,
  onReload,
  onUpdate,
  onReview
}: {
  candidates: AiStandardLibraryGrowthCandidate[];
  summary: AiStandardLibraryGrowthGovernanceSummary | null;
  busy: boolean;
  onReload: () => void;
  onUpdate: (id: number, payload: AiStandardLibraryGrowthCandidatePayload) => void;
  onReview: (id: number, action: GrowthCandidateAction, payload: AiStandardLibraryGrowthCandidatePayload) => void;
}) {
  const { t } = useTranslation();
  const [selectedId, setSelectedId] = useState<number | null>(candidates[0]?.id || null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<GrowthCandidateStatusFilter>("");
  const [pendingOnly, setPendingOnly] = useState(true);
  const [focusedPath, setFocusedPath] = useState<string[]>([]);
  const visibleSummary = summary || emptyGrowthGovernanceSummary();
  const filteredCandidates = useMemo(
    () => candidates.filter(candidate => growthCandidateMatchesFilters(candidate, { query, statusFilter, pendingOnly, focusedPath })),
    [candidates, focusedPath, pendingOnly, query, statusFilter]
  );
  const selected = filteredCandidates.find(candidate => candidate.id === selectedId) || filteredCandidates[0] || null;
  const [draft, setDraft] = useState<GrowthCandidateDraft>(() => growthCandidateToDraft(selected));
  const pendingCount = candidates.filter(candidate => growthCandidatePending(candidate.status)).length;
  const activeFilterCount = [query.trim(), statusFilter, pendingOnly ? "pending" : "", focusedPath.length ? "path" : ""].filter(Boolean).length;
  const statusOptions: Array<{ value: GrowthCandidateStatusFilter; label: string }> = [
    { value: "", label: t("teacherManagement.aiLibrary.governance.filters.allStatus") },
    { value: "PROPOSED", label: t("teacherManagement.aiLibrary.governance.status.proposed") },
    { value: "NEEDS_REVIEW", label: t("teacherManagement.aiLibrary.governance.status.needsReview") },
    { value: "BLOCKED", label: t("teacherManagement.aiLibrary.governance.status.blocked") },
    { value: "MERGED_SIMILAR", label: t("teacherManagement.aiLibrary.governance.status.mergedSimilar") },
    { value: "TEACHER_APPROVED", label: t("teacherManagement.aiLibrary.governance.status.teacherApproved") },
    { value: "MERGED", label: t("teacherManagement.aiLibrary.governance.status.merged") },
    { value: "REJECTED", label: t("teacherManagement.aiLibrary.governance.status.rejected") },
    { value: "IGNORED", label: t("teacherManagement.aiLibrary.governance.status.ignored") }
  ];

  useEffect(() => {
    if (!filteredCandidates.length) {
      setSelectedId(null);
      return;
    }
    if (!selectedId || !filteredCandidates.some(candidate => candidate.id === selectedId)) {
      setSelectedId(filteredCandidates[0].id);
    }
  }, [filteredCandidates, selectedId]);

  useEffect(() => {
    setDraft(growthCandidateToDraft(selected));
  }, [selected?.id]);

  function patchDraft(values: Partial<GrowthCandidateDraft>) {
    setDraft(current => ({ ...current, ...values }));
  }

  function submit(action?: GrowthCandidateAction) {
    if (!selected) {
      return;
    }
    const payload = growthDraftToPayload(draft);
    if (action) {
      onReview(selected.id, action, payload);
    } else {
      onUpdate(selected.id, payload);
    }
  }

  function applyPathFilter(stat: AiStandardLibraryGrowthPathStat) {
    setFocusedPath(stat.path);
    setPendingOnly(true);
    setStatusFilter("");
    setQuery("");
  }

  function clearFilters() {
    setQuery("");
    setStatusFilter("");
    setPendingOnly(false);
    setFocusedPath([]);
  }

  return (
    <div className="standard-library-review">
      <div className="standard-library-review__head">
        <div>
          <p className="eyebrow">{t("teacherManagement.aiLibrary.governance.eyebrow")}</p>
          <h2>{t("teacherManagement.aiLibrary.governance.title")}</h2>
          <p>{t("teacherManagement.aiLibrary.governance.description")}</p>
        </div>
        <div className="actions">
          <StatusPill tone={pendingCount ? "warning" : "success"}>{t("teacherManagement.aiLibrary.governance.pendingCount", { count: pendingCount })}</StatusPill>
          <button type="button" className="standard-library-icon-button" aria-label={t("teacherManagement.aiLibrary.governance.refresh")} title={t("teacherManagement.aiLibrary.governance.refresh")} onClick={() => onReload()} disabled={busy}>
            <RefreshCw size={17} />
          </button>
        </div>
      </div>
      <section className="standard-library-governance-strip" aria-label={t("teacherManagement.aiLibrary.governance.summaryAria")}>
        <div className="standard-library-governance__stats">
          <GovernanceStat label={t("teacherManagement.aiLibrary.governance.metrics.total")} value={visibleSummary.totalCount} tone="neutral" />
          <GovernanceStat label={t("teacherManagement.aiLibrary.governance.metrics.pending")} value={visibleSummary.reviewPendingCount} tone={visibleSummary.reviewPendingCount ? "warning" : "success"} />
          <GovernanceStat label={t("teacherManagement.aiLibrary.governance.metrics.duplicates")} value={visibleSummary.duplicateAggregateCount} tone={visibleSummary.duplicateAggregateCount ? "warning" : "neutral"} />
          <GovernanceStat label={t("teacherManagement.aiLibrary.governance.metrics.merged")} value={visibleSummary.teacherApprovedCount + visibleSummary.mergedCount} tone="success" />
          <GovernanceStat label={t("teacherManagement.aiLibrary.governance.metrics.closed")} value={visibleSummary.rejectedCount + visibleSummary.ignoredCount} tone="neutral" />
        </div>
        <div className="standard-library-governance-focus">
          <section>
            <h3>{t("teacherManagement.aiLibrary.governance.highFrequencyPaths")}</h3>
            {visibleSummary.highFrequencyPaths.length ? (
              visibleSummary.highFrequencyPaths
                .slice(0, 4)
                .map((path, index) => (
                  <GovernancePathStat
                    stat={path}
                    active={sameGrowthPath(path.path, focusedPath)}
                    onSelect={applyPathFilter}
                    key={`freq-${index}-${path.path.join("/")}`}
                  />
                ))
            ) : (
              <p>{t("teacherManagement.aiLibrary.governance.noHighFrequencyPaths")}</p>
            )}
          </section>
          <section>
            <h3>{t("teacherManagement.aiLibrary.governance.weakPaths")}</h3>
            {visibleSummary.weakPaths.length ? (
              visibleSummary.weakPaths
                .slice(0, 4)
                .map((path, index) => (
                  <GovernancePathStat
                    stat={path}
                    active={sameGrowthPath(path.path, focusedPath)}
                    onSelect={applyPathFilter}
                    key={`weak-${index}-${path.path.join("/")}`}
                  />
                ))
            ) : (
              <p>{t("teacherManagement.aiLibrary.governance.noWeakPaths")}</p>
            )}
          </section>
        </div>
      </section>
      <div className="standard-library-review-filters" role="search" aria-label={t("teacherManagement.aiLibrary.governance.filters.aria")}>
        <label className="standard-library-search standard-library-review-search">
          <Search size={16} aria-hidden="true" />
          <TextInput
            aria-label={t("teacherManagement.aiLibrary.governance.filters.searchAria")}
            className="control standard-library-search__input"
            name="growthCandidateQuery"
            value={query}
            onChange={event => setQuery(event.target.value)}
            placeholder={t("teacherManagement.aiLibrary.governance.filters.searchPlaceholder")}
          />
        </label>
        <Select
          aria-label={t("teacherManagement.aiLibrary.governance.filters.statusAria")}
          className="control standard-library-review-filters__select"
          name="growthStatusFilter"
          value={statusFilter}
          onChange={event => setStatusFilter(event.target.value as GrowthCandidateStatusFilter)}
        >
          {statusOptions.map(option => (
            <option value={option.value} key={option.value || "all"}>
              {option.label}
            </option>
          ))}
        </Select>
        <button type="button" className={`standard-library-chip ${pendingOnly ? "is-active" : ""}`} aria-pressed={pendingOnly} onClick={() => setPendingOnly(value => !value)}>
          {t("teacherManagement.aiLibrary.governance.filters.pendingOnly")}
        </button>
        {focusedPath.length ? <StatusPill tone="info">{focusedPath.join(" / ")}</StatusPill> : null}
        {activeFilterCount ? (
          <button type="button" className="standard-library-filter-clear" onClick={clearFilters}>
            {t("teacherManagement.aiLibrary.governance.filters.clear")}
          </button>
        ) : null}
        <span className="standard-library-review-filters__count">
          <ListChecks size={15} />
          {filteredCandidates.length} / {candidates.length}
        </span>
      </div>
      {candidates.length ? (
        <div className="standard-library-review__grid">
          <div className="standard-library-review__list" aria-label="成长候选列表">
            {filteredCandidates.length ? filteredCandidates.map(candidate => (
              <button
                type="button"
                className={`standard-library-review-card ${candidate.id === selected?.id ? "is-active" : ""}`}
                key={candidate.id}
                onClick={() => setSelectedId(candidate.id)}
              >
                <span>
                  <strong>{candidate.suggestedName}</strong>
                  <small>{candidate.suggestedPath.join(" / ") || "未提供归属路径"}</small>
                  <small>{candidate.suggestedCode}</small>
                </span>
                <span>
                  <StatusPill tone={growthStatusTone(candidate.status)}>{growthStatusLabel(candidate.status)}</StatusPill>
                  <StatusPill tone="neutral">出现 {candidate.occurrenceCount || 1}</StatusPill>
                  <StatusPill tone={growthEvidenceTone(candidate.evidenceStatus)}>
                    {growthEvidenceLabel(candidate.evidenceStatus, t)}
                  </StatusPill>
                  {candidate.confidence == null ? null : <StatusPill tone="info">置信 {formatGrowthConfidence(candidate.confidence)}</StatusPill>}
                </span>
              </button>
            )) : (
              <EmptyState title={t("teacherManagement.aiLibrary.governance.emptyFilteredTitle")} description={t("teacherManagement.aiLibrary.governance.emptyFilteredDescription")} />
            )}
          </div>
          {selected ? (
            <section className="standard-library-review__detail" aria-label="候选详情">
              <div className="standard-library-review__detail-head">
                <div>
                  <p className="eyebrow">{layerLabel(selected.layer)}</p>
                  <h3>{selected.suggestedName}</h3>
                  <p>{selected.suggestedPath.join(" / ") || "未提供归属路径"}</p>
                </div>
                <StatusPill tone={growthStatusTone(selected.status)}>{growthStatusLabel(selected.status)}</StatusPill>
              </div>
              <div className="standard-library-review__meta">
                <StatusPill tone="neutral">题目 {selected.sourceProblemId || "-"}</StatusPill>
                <StatusPill tone="neutral">提交 {selected.sourceSubmissionId || "-"}</StatusPill>
                <StatusPill tone="neutral">出现 {selected.occurrenceCount || 1}</StatusPill>
                <StatusPill tone={growthEvidenceTone(selected.evidenceStatus)}>
                  {growthEvidenceLabel(selected.evidenceStatus, t)}
                </StatusPill>
                {selected.confidence == null ? null : <StatusPill tone="info">置信 {formatGrowthConfidence(selected.confidence)}</StatusPill>}
              </div>
              <div className="form-grid standard-library-editor__core">
                <Field label="类型">
                  <Select name="growthLayer" value={draft.layer} onChange={event => patchDraft({ layer: event.target.value as AiStandardLibraryLayer })}>
                    <option value="SKILL_UNIT">能力点</option>
                    <option value="MISTAKE_POINT">易错点</option>
                  </Select>
                </Field>
                <Field label="候选 ID">
                  <TextInput name="growthCode" value={draft.suggestedCode} onChange={event => patchDraft({ suggestedCode: event.target.value })} />
                </Field>
                <Field label="候选名称">
                  <TextInput name="growthName" value={draft.suggestedName} onChange={event => patchDraft({ suggestedName: event.target.value })} />
                </Field>
                <Field label="置信度">
                  <TextInput name="growthConfidence" value={draft.confidence} onChange={event => patchDraft({ confidence: event.target.value })} />
                </Field>
              </div>
              <div className="standard-library-editor__textarea-grid standard-library-review__textarea-grid">
                <Field label="归属路径">
                  <TextArea name="growthPath" value={draft.suggestedPath} onChange={event => patchDraft({ suggestedPath: event.target.value })} placeholder="每行一层路径" />
                </Field>
                <Field label="证据线索">
                  <TextArea name="growthEvidence" value={draft.evidenceRefs} onChange={event => patchDraft({ evidenceRefs: event.target.value })} placeholder="每行一个 evidenceRef" />
                </Field>
                <Field label="相似条目">
                  <TextArea name="growthSimilar" value={draft.similarExistingItems} onChange={event => patchDraft({ similarExistingItems: event.target.value })} placeholder="每行一个标准库条目 ID" />
                </Field>
                <Field label="审核说明">
                  <TextArea name="growthTeacherNote" value={draft.teacherNote} onChange={event => patchDraft({ teacherNote: event.target.value })} />
                </Field>
              </div>
              <Field label="变更理由">
                <TextArea name="growthReason" value={draft.changeReason} onChange={event => patchDraft({ changeReason: event.target.value })} />
              </Field>
              {selected.precheckMessage ? <p className="standard-library-review__note">{selected.precheckMessage}</p> : null}
              {selected.rollbackInfo ? <p className="standard-library-review__note">{selected.rollbackInfo}</p> : null}
              <div className="actions standard-library-review__actions">
                <Button type="button" variant="secondary" icon={<Save size={16} />} onClick={() => submit()} disabled={busy}>
                  保存修改
                </Button>
                <Button type="button" variant="primary" icon={<CheckCircle2 size={16} />} onClick={() => submit("approve")} disabled={busy}>
                  通过入库
                </Button>
                <Button type="button" variant="secondary" icon={<GitMerge size={16} />} onClick={() => submit("merge")} disabled={busy}>
                  合并入库
                </Button>
                <Button type="button" variant="secondary" icon={<XCircle size={16} />} onClick={() => submit("reject")} disabled={busy}>
                  拒绝
                </Button>
                <Button type="button" variant="secondary" icon={<X size={16} />} onClick={() => submit("ignore")} disabled={busy}>
                  忽略
                </Button>
              </div>
            </section>
          ) : null}
        </div>
      ) : (
        <EmptyState title={t("teacherManagement.aiLibrary.governance.emptyTitle")} description={t("teacherManagement.aiLibrary.governance.emptyDescription")} />
      )}
    </div>
  );
}

function GovernanceStat({ label, value, tone }: { label: string; value: number; tone: "neutral" | "warning" | "success" }) {
  return (
    <article>
      <span>{label}</span>
      <strong>{value}</strong>
      <StatusPill tone={tone}>{label}</StatusPill>
    </article>
  );
}

function GovernancePathStat({
  stat,
  active,
  onSelect
}: {
  stat: AiStandardLibraryGrowthPathStat;
  active?: boolean;
  onSelect?: (stat: AiStandardLibraryGrowthPathStat) => void;
}) {
  const content = (
    <>
      <strong>{stat.path.join(" / ") || "未提供路径"}</strong>
      <span>
        <StatusPill tone="neutral">候选 {stat.candidateCount}</StatusPill>
        <StatusPill tone={stat.pendingCount ? "warning" : "success"}>待审 {stat.pendingCount}</StatusPill>
        <StatusPill tone="neutral">出现 {stat.occurrenceCount}</StatusPill>
      </span>
      {stat.recommendedAction ? <p>{stat.recommendedAction}</p> : null}
    </>
  );
  if (onSelect) {
    return (
      <button type="button" className={`standard-library-governance-path ${active ? "is-active" : ""}`} onClick={() => onSelect(stat)}>
        {content}
      </button>
    );
  }
  return (
    <article className="standard-library-governance-path">
      {content}
    </article>
  );
}

function growthCandidateToDraft(candidate: AiStandardLibraryGrowthCandidate | null): GrowthCandidateDraft {
  return {
    layer: candidate?.layer || "MISTAKE_POINT",
    suggestedCode: candidate?.suggestedCode || "",
    suggestedName: candidate?.suggestedName || "",
    suggestedPath: linesToText(candidate?.suggestedPath),
    sourceProblemId: candidate?.sourceProblemId ? String(candidate.sourceProblemId) : "",
    sourceSubmissionId: candidate?.sourceSubmissionId ? String(candidate.sourceSubmissionId) : "",
    similarExistingItems: linesToText(candidate?.similarExistingItems),
    evidenceRefs: linesToText(candidate?.evidenceRefs),
    evidenceStatus: candidate?.evidenceStatus || "",
    changeReason: candidate?.changeReason || "",
    confidence: candidate?.confidence == null ? "" : String(candidate.confidence),
    teacherNote: candidate?.teacherNote || ""
  };
}

function growthDraftToPayload(draft: GrowthCandidateDraft): AiStandardLibraryGrowthCandidatePayload {
  const confidence = Number(draft.confidence);
  const evidenceRefs = textToLines(draft.evidenceRefs);
  return {
    layer: draft.layer,
    suggestedCode: draft.suggestedCode.trim(),
    suggestedName: draft.suggestedName.trim(),
    suggestedPath: textToLines(draft.suggestedPath),
    sourceProblemId: draft.sourceProblemId.trim() ? Number(draft.sourceProblemId) : null,
    sourceSubmissionId: draft.sourceSubmissionId.trim() ? Number(draft.sourceSubmissionId) : null,
    similarExistingItems: textToLines(draft.similarExistingItems),
    evidenceRefs,
    evidenceStatus: draft.evidenceStatus || (evidenceRefs.length ? "SUPPORTED" : "NO_DIRECT_CODE_EVIDENCE"),
    changeReason: draft.changeReason.trim(),
    confidence: Number.isFinite(confidence) ? confidence : null,
    teacherNote: draft.teacherNote.trim()
  };
}

function emptyGrowthGovernanceSummary(): AiStandardLibraryGrowthGovernanceSummary {
  return {
    totalCount: 0,
    reviewPendingCount: 0,
    proposedCount: 0,
    needsReviewCount: 0,
    blockedCount: 0,
    mergedSimilarCount: 0,
    teacherApprovedCount: 0,
    mergedCount: 0,
    rejectedCount: 0,
    ignoredCount: 0,
    duplicateAggregateCount: 0,
    statusStats: ["PROPOSED", "NEEDS_REVIEW", "BLOCKED", "MERGED_SIMILAR", "TEACHER_APPROVED", "MERGED", "REJECTED", "IGNORED"].map(status => ({
      status,
      count: 0
    })),
    highFrequencyPaths: [],
    weakPaths: []
  };
}

function growthCandidateMatchesFilters(
  candidate: AiStandardLibraryGrowthCandidate,
  filters: {
    query: string;
    statusFilter: GrowthCandidateStatusFilter;
    pendingOnly: boolean;
    focusedPath: string[];
  }
) {
  const status = (candidate.status || "").toUpperCase();
  if (filters.pendingOnly && !growthCandidatePending(status)) {
    return false;
  }
  if (filters.statusFilter && status !== filters.statusFilter) {
    return false;
  }
  if (filters.focusedPath.length && !growthCandidatePathText(candidate).toLowerCase().includes(filters.focusedPath.join(" / ").toLowerCase())) {
    return false;
  }
  const query = filters.query.trim().toLowerCase();
  if (!query) {
    return true;
  }
  return [
    candidate.suggestedName,
    candidate.suggestedCode,
    growthCandidatePathText(candidate),
    candidate.changeReason,
    candidate.precheckMessage,
    candidate.evidenceRefs?.join(" "),
    candidate.evidenceStatus,
    candidate.similarExistingItems?.join(" "),
    growthStatusLabel(candidate.status)
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase()
    .includes(query);
}

function growthCandidatePathText(candidate: AiStandardLibraryGrowthCandidate) {
  return (candidate.suggestedPath || []).join(" / ");
}

function sameGrowthPath(left: string[], right: string[]) {
  return left.join("\n") === right.join("\n");
}

function formatGrowthConfidence(value: number) {
  if (value <= 1) {
    return `${Math.round(value * 100)}%`;
  }
  return String(Math.round(value));
}

function growthCandidatePending(status?: string | null) {
  return ["PROPOSED", "NEEDS_REVIEW", "BLOCKED", "MERGED_SIMILAR"].includes((status || "").toUpperCase());
}

function growthStatusLabel(status?: string | null) {
  const key = (status || "").toUpperCase();
  const labels: Record<string, string> = {
    PROPOSED: "待处理",
    NEEDS_REVIEW: "待审核",
    BLOCKED: "需修正",
    MERGED_SIMILAR: "重复聚合",
    TEACHER_APPROVED: "教师批准",
    REJECTED: "已拒绝",
    MERGED: "已入库",
    IGNORED: "已忽略"
  };
  return labels[key] || key || "未知";
}

function growthStatusTone(status?: string | null): "neutral" | "success" | "warning" | "danger" | "info" {
  const key = (status || "").toUpperCase();
  if (key === "TEACHER_APPROVED" || key === "MERGED") {
    return "success";
  }
  if (key === "REJECTED") {
    return "danger";
  }
  if (key === "PROPOSED" || key === "NEEDS_REVIEW" || key === "BLOCKED" || key === "MERGED_SIMILAR") {
    return "warning";
  }
  if (key === "IGNORED") {
    return "neutral";
  }
  return "info";
}

function growthEvidenceLabel(status: string | null | undefined, t: (key: string) => string) {
  const key = (status || "").toUpperCase();
  const labels: Record<string, string> = {
    SUPPORTED: t("teacherManagement.aiLibrary.governance.evidence.supported"),
    NO_DIRECT_CODE_EVIDENCE: t("teacherManagement.aiLibrary.governance.evidence.noDirectCodeEvidence"),
    UNSUPPORTED: t("teacherManagement.aiLibrary.governance.evidence.unsupported")
  };
  return labels[key] || t("teacherManagement.aiLibrary.governance.evidence.unknown");
}

function growthEvidenceTone(status?: string | null): "neutral" | "success" | "warning" | "danger" | "info" {
  const key = (status || "").toUpperCase();
  if (key === "SUPPORTED") {
    return "success";
  }
  if (key === "UNSUPPORTED") {
    return "danger";
  }
  if (key === "NO_DIRECT_CODE_EVIDENCE") {
    return "neutral";
  }
  return "warning";
}

function buildKnowledgePathGroups(items: AiStandardLibraryItem[], knowledgeTree: InformaticsKnowledgeNode[]): LibraryCodeGroup[] {
  const index = buildKnowledgeIndex(knowledgeTree);
  const groups = new Map<string, AiStandardLibraryItem[]>();
  items.forEach(item => {
    knowledgeCodes(item).forEach(code => {
      groups.set(code, [...(groups.get(code) || []), item]);
    });
  });
  return Array.from(groups, ([code, groupItems]) => {
    const meta = index.get(code);
    return {
      code,
      label: meta?.name || code,
      path: meta?.path || "未在知识树中找到",
      orderPath: meta?.orderPath || [Number.MAX_SAFE_INTEGER],
      known: Boolean(meta),
      items: sortLibraryItems(groupItems)
    };
  }).sort((left, right) => compareOrderPath(left.orderPath, right.orderPath) || left.path.localeCompare(right.path, "zh-Hans-CN") || left.code.localeCompare(right.code));
}

function buildKnowledgeBranches(groups: LibraryCodeGroup[]): LibraryBranchGroup[] {
  const branches = new Map<string, LibraryBranchGroup>();
  groups.forEach(group => {
    const root = group.known ? group.path.split(" / ")[0] || group.label : "未在知识树中找到";
    const branch = branches.get(root) || { root, orderPath: group.orderPath, count: 0, groups: [] };
    if (compareOrderPath(group.orderPath, branch.orderPath) < 0) {
      branch.orderPath = group.orderPath;
    }
    branch.count += group.items.length;
    branch.groups.push(group);
    branches.set(root, branch);
  });
  return Array.from(branches.values()).sort((left, right) => compareOrderPath(left.orderPath, right.orderPath) || left.root.localeCompare(right.root, "zh-Hans-CN"));
}

function buildKnowledgeIndex(
  nodes: InformaticsKnowledgeNode[],
  parents: string[] = [],
  parentOrder: number[] = [],
  index = new Map<string, { name: string; path: string; orderPath: number[] }>()
) {
  nodes.forEach(node => {
    const path = [...parents, node.name];
    const orderPath = [...parentOrder, node.sortOrder ?? Number.MAX_SAFE_INTEGER];
    index.set(node.code, { name: node.name, path: path.join(" / "), orderPath });
    buildKnowledgeIndex(node.children || [], path, orderPath, index);
  });
  return index;
}

function knowledgeCodes(item: AiStandardLibraryItem) {
  return Array.from(new Set([
    item.primaryKnowledgeNodeCode,
    ...(item.relatedKnowledgeNodeCodes || []),
    ...(item.knowledgeNodeCodes || [])
  ].map(code => (code || "").trim()).filter(Boolean)));
}

function sortLibraryItems(items: AiStandardLibraryItem[]) {
  return [...items].sort((left, right) => layerRank(left.layer) - layerRank(right.layer) || left.category.localeCompare(right.category, "zh-Hans-CN") || left.code.localeCompare(right.code));
}

function compareOrderPath(left: number[], right: number[]) {
  const length = Math.max(left.length, right.length);
  for (let index = 0; index < length; index += 1) {
    const diff = (left[index] ?? Number.MAX_SAFE_INTEGER) - (right[index] ?? Number.MAX_SAFE_INTEGER);
    if (diff) {
      return diff;
    }
  }
  return 0;
}

function layerRank(layer: AiStandardLibraryLayer) {
  if (layer === "SKILL_UNIT") return 0;
  if (layer === "MISTAKE_POINT") return 1;
  return 2;
}

function FilePicker({
  accept,
  fileName,
  kind,
  label,
  note,
  onPick
}: {
  accept: string;
  fileName: string;
  kind: ImportKind;
  label: string;
  note: string;
  onPick: (kind: ImportKind, file: File | null) => void | Promise<void>;
}) {
  return (
    <label className="field management-file-picker">
      <span>{label}</span>
      <span className="management-file-picker__box">
        <UploadCloud size={18} />
        <strong>{fileName || "选择文件"}</strong>
        <small>{fileName ? "已读取" : note}</small>
      </span>
      <input type="file" accept={accept} onChange={event => void onPick(kind, event.target.files?.[0] || null)} />
    </label>
  );
}

function emptyLibraryDraft(): LibraryDraft {
  return {
    layer: "MISTAKE_POINT",
    code: "",
    category: "",
    name: "",
    description: "",
    studentExplanation: "",
    teacherExplanation: "",
    skillUnitCode: "",
    mistakeType: "",
    commonMisconception: "",
    evidenceSignals: "",
    commonCodePatterns: "",
    judgeSignals: "",
    requiredEvidence: "",
    whenToUse: "",
    studentBenefit: "",
    hintL1: "",
    hintL2: "",
    hintL3: "",
    abilityPoint: "",
    severity: "MEDIUM",
    applicableLanguages: "PYTHON\nCPP17",
    relatedItems: "",
    knowledgeNodeCodes: "",
    prerequisiteKnowledgeCodes: "",
    teachingAction: "",
    enabled: true,
    libraryVersion: "standard-library-v3-skill-mistake"
  };
}

function itemToDraft(item: AiStandardLibraryItem): LibraryDraft {
  return {
    id: item.id,
    layer: item.layer,
    code: item.code,
    category: item.category,
    name: item.name,
    description: item.description || "",
    studentExplanation: item.studentExplanation || "",
    teacherExplanation: item.teacherExplanation || "",
    skillUnitCode: item.skillUnitCode || "",
    mistakeType: item.mistakeType || "",
    commonMisconception: item.commonMisconception || "",
    evidenceSignals: linesToText(item.evidenceSignals),
    commonCodePatterns: linesToText(item.commonCodePatterns),
    judgeSignals: linesToText(item.judgeSignals),
    requiredEvidence: linesToText(item.requiredEvidence),
    whenToUse: item.whenToUse || "",
    studentBenefit: item.studentBenefit || "",
    hintL1: item.hintL1 || "",
    hintL2: item.hintL2 || "",
    hintL3: item.hintL3 || "",
    abilityPoint: item.abilityPoint || "",
    severity: item.severity || "",
    applicableLanguages: linesToText(item.applicableLanguages),
    relatedItems: linesToText(item.relatedItems),
    knowledgeNodeCodes: linesToText(item.knowledgeNodeCodes),
    prerequisiteKnowledgeCodes: linesToText(item.prerequisiteKnowledgeCodes),
    teachingAction: item.teachingAction || "",
    enabled: item.enabled,
    libraryVersion: item.libraryVersion || "standard-library-v3-skill-mistake"
  };
}

function draftToPayload(draft: LibraryDraft): AiStandardLibraryItemPayload {
  return {
    layer: draft.layer,
    code: draft.code.trim(),
    category: draft.category.trim(),
    name: draft.name.trim(),
    description: draft.description.trim(),
    studentExplanation: draft.studentExplanation.trim(),
    teacherExplanation: draft.teacherExplanation.trim(),
    skillUnitCode: draft.skillUnitCode.trim(),
    mistakeType: draft.mistakeType.trim(),
    commonMisconception: draft.commonMisconception.trim(),
    evidenceSignals: [],
    commonCodePatterns: [],
    judgeSignals: [],
    requiredEvidence: [],
    whenToUse: "",
    studentBenefit: "",
    hintL1: "",
    hintL2: "",
    hintL3: "",
    abilityPoint: draft.layer === "SKILL_UNIT" ? draft.name.trim() : draft.skillUnitCode.trim(),
    severity: draft.severity.trim(),
    applicableLanguages: textToLines(draft.applicableLanguages),
    relatedItems: textToLines(draft.relatedItems),
    knowledgeNodeCodes: textToLines(draft.knowledgeNodeCodes),
    prerequisiteKnowledgeCodes: textToLines(draft.prerequisiteKnowledgeCodes),
    teachingAction: "",
    enabled: draft.enabled,
    libraryVersion: draft.libraryVersion.trim()
  };
}

function linesToText(lines: string[] | undefined | null): string {
  return (lines || []).join("\n");
}

function textToLines(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean);
}

function layerLabel(layer: AiStandardLibraryLayer) {
  if (layer === "SKILL_UNIT") return "能力点";
  if (layer === "MISTAKE_POINT") return "易错点";
  if (layer === "BASIC_CAUSE") return "旧基础层";
  return "旧提高层";
}

function libraryItemMatchesFilters(item: AiStandardLibraryItem, filters: LibraryFilters) {
  if (filters.layer && item.layer !== filters.layer) {
    return false;
  }
  if (filters.category && item.category !== filters.category) {
    return false;
  }
  if (filters.enabled && item.enabled !== (filters.enabled === "true")) {
    return false;
  }
  const query = filters.query.trim().toLowerCase();
  if (!query) {
    return true;
  }
  const haystack = [
    item.code,
    item.name,
    item.category,
    item.description,
    item.studentExplanation,
    item.teacherExplanation,
    item.skillUnitCode,
    item.mistakeType,
    item.commonMisconception,
    ...item.evidenceSignals,
    ...item.commonCodePatterns,
    ...item.judgeSignals,
    ...item.requiredEvidence,
    item.abilityPoint,
    item.severity,
    ...item.applicableLanguages,
    ...item.relatedItems,
    ...item.knowledgeNodeCodes,
    ...item.prerequisiteKnowledgeCodes,
    item.teachingAction
  ]
    .filter(Boolean)
    .join("\n")
    .toLowerCase();
  return haystack.includes(query);
}

function readDraftFromElement(element: HTMLElement, fallback: LibraryDraft): LibraryDraft {
  const editor = element.closest(".standard-library-manager")?.querySelector(".standard-library-editor");
  const value = (name: keyof LibraryDraft) => {
    const field = editor?.querySelector<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(`[name="${String(name)}"]`);
    return field?.value ?? String(fallback[name] ?? "");
  };
  return {
    ...fallback,
    layer: value("layer") as AiStandardLibraryLayer,
    code: value("code"),
    category: value("category"),
    name: value("name"),
    description: value("description"),
    studentExplanation: value("studentExplanation"),
    teacherExplanation: value("teacherExplanation"),
    skillUnitCode: value("skillUnitCode"),
    mistakeType: value("mistakeType"),
    commonMisconception: value("commonMisconception"),
    evidenceSignals: value("evidenceSignals"),
    commonCodePatterns: value("commonCodePatterns"),
    judgeSignals: value("judgeSignals"),
    requiredEvidence: value("requiredEvidence"),
    whenToUse: value("whenToUse"),
    studentBenefit: value("studentBenefit"),
    hintL1: value("hintL1"),
    hintL2: value("hintL2"),
    hintL3: value("hintL3"),
    abilityPoint: value("abilityPoint"),
    severity: value("severity"),
    applicableLanguages: value("applicableLanguages"),
    relatedItems: value("relatedItems"),
    knowledgeNodeCodes: value("knowledgeNodeCodes"),
    prerequisiteKnowledgeCodes: value("prerequisiteKnowledgeCodes"),
    teachingAction: value("teachingAction"),
    libraryVersion: value("libraryVersion")
  };
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener("load", () => resolve(String(reader.result || "")));
    reader.addEventListener("error", () => reject(reader.error || new Error("文件读取失败")));
    reader.readAsDataURL(file);
  });
}

function ImportResult({ result }: { result: ImportPreview | ImportCommit | null }) {
  if (!result) {
    return null;
  }
  const isCommit = "createdCount" in result;
  return (
    <div className="management-import-result">
      <h3>{result.message || (isCommit ? "导入完成" : "预览完成")}</h3>
      {isCommit ? (
        <div className="status-box-row">
          <StatusPill tone="success">新增 {(result as ImportCommit).createdCount}</StatusPill>
          <StatusPill tone="info">更新 {(result as ImportCommit).updatedCount}</StatusPill>
          <StatusPill tone="neutral">跳过 {(result as ImportCommit).skippedCount}</StatusPill>
          <StatusPill tone={(result as ImportCommit).failedCount ? "danger" : "neutral"}>失败 {(result as ImportCommit).failedCount}</StatusPill>
        </div>
      ) : (
        <div className="status-box-row">
          <StatusPill tone="info">总行数 {(result as ImportPreview).totalRows}</StatusPill>
          <StatusPill tone="success">有效 {(result as ImportPreview).validRows}</StatusPill>
          <StatusPill tone={(result as ImportPreview).invalidRows ? "warning" : "neutral"}>无效 {(result as ImportPreview).invalidRows}</StatusPill>
          <StatusPill tone={(result as ImportPreview).duplicateRows ? "warning" : "neutral"}>重复 {(result as ImportPreview).duplicateRows}</StatusPill>
        </div>
      )}
      {!!result.issues?.length && (
        <ul>
          {result.issues.slice(0, 6).map(issue => (
            <li key={`${issue.rowNumber}-${issue.message}`}>
              第 {issue.rowNumber} 行：{issue.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
