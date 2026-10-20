import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, CheckCircle2, Search, X } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { ApiError, api } from "../../shared/api/client";
import type { AssignmentReadiness, AssignmentReadinessIssue, AssignmentTargetMode, ClassGroup, Difficulty, ProblemCatalogItem, StudentProfile } from "../../shared/api/types";
import { difficultyLabel, displayText } from "../../shared/format";
import { useTranslation } from "../../shared/i18n";
import { Button, ButtonLink } from "../../shared/ui/Button";
import { EmptyState } from "../../shared/ui/EmptyState";
import { Field, Select, TextArea, TextInput } from "../../shared/ui/Field";
import { DifficultyPill, StatusPill } from "../../shared/ui/StatusPill";

type Alert = { type: "success" | "error"; message: string };
type AssignmentForm = {
  title: string;
  description: string;
  classGroupId: string;
  hintPolicy: string;
  status: string;
  targetMode: AssignmentTargetMode;
  studentProfileIds: number[];
  problemIds: number[];
};
type DifficultyFilter = "ALL" | Difficulty;
type Translator = (key: string, params?: Record<string, string | number>) => string;

const EMPTY_ASSIGNMENT: AssignmentForm = {
  title: "",
  description: "",
  classGroupId: "",
  hintPolicy: "L2",
  status: "ACTIVE",
  targetMode: "CLASS",
  studentProfileIds: [],
  problemIds: []
};

const DIFFICULTY_FILTERS: Array<{ value: DifficultyFilter; label: string }> = [
  { value: "ALL", label: "全部难度" },
  { value: "EASY", label: "基础" },
  { value: "MEDIUM", label: "提高" },
  { value: "HARD", label: "挑战" }
];
const DEFAULT_CLASS_NAME = "默认班级";

function cleanAssignmentTitle(value?: string | null, fallback = "课堂作业") {
  const title = displayText(value, fallback);
  return title.includes("试点任务") ? "课堂编程作业" : title;
}

function teacherErrorMessage(error: unknown, fallback: string) {
  const base = fallback.replace(/[。.!！?？\s]+$/, "");
  if (error instanceof ApiError) {
    if (error.status >= 500) {
      return `${base}，服务暂时不可用。`;
    }
    if (error.status === 404) {
      return `${base}，未找到课堂资源。`;
    }
    return `${base}，${error.message}`;
  }
  const detail = error instanceof Error ? error.message : "";
  return detail ? `${base}，${detail}` : fallback;
}

function readinessIssueText(issue: AssignmentReadinessIssue, t: Translator) {
  const knownCodes = new Set([
    "PROBLEM_NOT_FOUND",
    "NO_TEST_CASES",
    "SEMANTIC_PROFILE_INCOMPLETE",
    "SEMANTIC_CODE_DUPLICATE",
    "STANDARD_LIBRARY_PATH_INVALID",
    "REVEAL_POLICY_MISMATCH",
    "LOW_INTENT_DIVERSITY",
    "STARTER_CODE_MISSING"
  ]);
  return knownCodes.has(issue.code)
    ? t(`assignmentReadiness.issues.${issue.code}`)
    : issue.message || t("assignmentReadiness.issues.UNKNOWN");
}

export default function AssignmentCreatePage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [classes, setClasses] = useState<ClassGroup[]>([]);
  const [problems, setProblems] = useState<ProblemCatalogItem[]>([]);
  const [students, setStudents] = useState<StudentProfile[]>([]);
  const [form, setForm] = useState<AssignmentForm>(EMPTY_ASSIGNMENT);
  const [query, setQuery] = useState("");
  const [difficulty, setDifficulty] = useState<DifficultyFilter>("ALL");
  const [alert, setAlert] = useState<Alert | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [readiness, setReadiness] = useState<AssignmentReadiness | null>(null);
  const [readinessLoading, setReadinessLoading] = useState(false);

  useEffect(() => {
    void loadCreateContext();
  }, []);

  useEffect(() => {
    const classGroupId = Number(form.classGroupId);
    if (!classGroupId) {
      setStudents([]);
      return;
    }
    api.classRoster(classGroupId)
      .then(result => setStudents(result.filter(student => student.status === "ACTIVE")))
      .catch(() => setStudents([]));
  }, [form.classGroupId]);

  useEffect(() => {
    if (!form.problemIds.length) {
      setReadiness(null);
      setReadinessLoading(false);
      return;
    }
    let ignore = false;
    setReadinessLoading(true);
    api.assignmentReadiness(form.problemIds)
      .then(result => {
        if (!ignore) setReadiness(result);
      })
      .catch(() => {
        if (!ignore) setReadiness(null);
      })
      .finally(() => {
        if (!ignore) setReadinessLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [form.problemIds]);

  const selectedProblems = useMemo(
    () => form.problemIds.map(id => problems.find(problem => problem.id === id)).filter(Boolean) as ProblemCatalogItem[],
    [form.problemIds, problems]
  );

  const filteredProblems = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return problems.filter(problem => {
      const matchesDifficulty = difficulty === "ALL" || String(problem.difficulty).toUpperCase() === difficulty;
      const matchesQuery =
        !normalizedQuery ||
        problem.title.toLowerCase().includes(normalizedQuery) ||
        displayText(problem.summary, "").toLowerCase().includes(normalizedQuery);
      return matchesDifficulty && matchesQuery;
    });
  }, [difficulty, problems, query]);

  async function loadCreateContext() {
    setLoading(true);
    setAlert(null);
    try {
      const [classResult, teacherProblems] = await Promise.all([ensureDefaultClass(), api.teacherProblems()]);
      const problemResult: ProblemCatalogItem[] = teacherProblems.map(problem => ({
        id: problem.id,
        title: problem.title,
        summary: problem.description,
        difficulty: problem.difficulty,
        timeLimit: problem.timeLimit,
        memoryLimit: problem.memoryLimit,
        createdAt: problem.createdAt
      }));
      setClasses(classResult);
      setProblems(problemResult);
      setForm(current => ({
        ...current,
        classGroupId: current.classGroupId || (classResult[0] ? String(classResult[0].id) : "")
      }));
    } catch (error) {
      setAlert({ type: "error", message: teacherErrorMessage(error, "新建作业资源读取失败。") });
    } finally {
      setLoading(false);
    }
  }

  async function ensureDefaultClass() {
    const classResult = await api.classes();
    if (classResult.length) {
      return classResult;
    }
    const created = await api.createClass({ name: DEFAULT_CLASS_NAME });
    return [created];
  }

  function toggleProblem(problemId: number) {
    setForm(current => ({
      ...current,
      problemIds: current.problemIds.includes(problemId)
        ? current.problemIds.filter(id => id !== problemId)
        : [...current.problemIds, problemId]
    }));
  }

  async function saveAssignment() {
    if (!form.title.trim()) {
      setAlert({ type: "error", message: "请填写作业名称。" });
      return;
    }
    const classGroupId = form.classGroupId || (classes[0] ? String(classes[0].id) : "");
    if (!classGroupId) {
      setAlert({ type: "error", message: "默认班级还没准备好，请刷新后重试。" });
      return;
    }
    if (!form.problemIds.length) {
      setAlert({ type: "error", message: "请至少选择一道题目。" });
      return;
    }
    setSaving(true);
    try {
      if (form.status === "ACTIVE") {
        const latestReadiness = await api.assignmentReadiness(form.problemIds);
        setReadiness(latestReadiness);
        if (!latestReadiness.publishable) {
          setAlert({ type: "error", message: t("assignmentReadiness.publishBlocked") });
          return;
        }
      }
      const payload = {
        title: form.title.trim(),
        description: form.description.trim(),
        classGroupId: Number(classGroupId),
        targetMode: form.targetMode,
        studentProfileIds: form.targetMode === "STUDENTS" ? form.studentProfileIds : [],
        hintPolicy: form.hintPolicy,
        status: form.status,
        problemIds: form.problemIds
      };
      const saved = await api.createAssignment(payload);
      setAlert({ type: "success", message: `${cleanAssignmentTitle(saved.title)} 已保存。` });
      navigate(`/teacher/classes/${classGroupId}/assignments/${saved.id}`);
    } catch (error) {
      setAlert({ type: "error", message: teacherErrorMessage(error, "作业保存失败。") });
    } finally {
      setSaving(false);
    }
  }

  const selectedClass = classes.find(item => String(item.id) === form.classGroupId) || classes[0];
  const publishIssues = [
    !form.title.trim() ? "填写作业名称" : null,
    !selectedProblems.length ? "至少选择 1 道题" : null,
    !selectedClass ? "默认班级未就绪" : null,
    form.targetMode === "STUDENTS" && !form.studentProfileIds.length ? t("assignmentTarget.validation") : null,
    form.status === "ACTIVE" && readiness && !readiness.publishable
      ? t("assignmentReadiness.blockerSummary", { count: readiness.blockerCount })
      : null
  ].filter(Boolean);
  const canPublish = publishIssues.length === 0 && !saving;

  function resetForm() {
    setForm({ ...EMPTY_ASSIGNMENT, classGroupId: classes[0] ? String(classes[0].id) : "" });
    setQuery("");
    setDifficulty("ALL");
  }

  return (
    <div className="teacher-page teacher-workflow assignment-builder-page">
      {alert && <div className={`alert alert--${alert.type === "success" ? "success" : "error"}`}>{alert.message}</div>}

      <section className="teacher-workflow-header">
        <div>
          <ButtonLink to="/teacher/classes" variant="ghost" icon={<ArrowLeft size={17} />}>
            {t("teacherAnalytics.actions.backToAnalytics")}
          </ButtonLink>
          <h1>布置课堂练习</h1>
        </div>
        <Button
          type="button"
          variant="primary"
          onClick={() => void saveAssignment()}
          disabled={!canPublish}
          title={publishIssues.join("、") || undefined}
          icon={<CheckCircle2 size={17} />}
        >
          {saving ? "保存中" : "发布作业"}
        </Button>
      </section>

      {loading ? (
        <EmptyState title="正在准备新建作业" live />
      ) : (
        <div className="assignment-builder assignment-builder--minimal">
          <section className="assignment-builder-section" aria-label="基本信息">
            <div className="assignment-builder-section__head">
              <div>
                <p className="eyebrow">基本信息</p>
                <h2>作业</h2>
              </div>
            </div>
            <div className="form-grid">
              <Field label="作业名称">
                <TextInput
                  value={form.title}
                  onChange={event => setForm({ ...form, title: event.target.value })}
                  placeholder="例如：循环边界练习"
                />
              </Field>
              <Field label={t("assignmentTarget.classLabel")}>
                <Select value={form.classGroupId} onChange={event => setForm({ ...form, classGroupId: event.target.value, studentProfileIds: [] })}>
                  {classes.map(item => <option value={item.id} key={item.id}>{item.name}</option>)}
                </Select>
              </Field>
              <Field label="发布状态">
                <Select value={form.status} onChange={event => setForm({ ...form, status: event.target.value })}>
                  <option value="ACTIVE">进行中</option>
                  <option value="DRAFT">草稿</option>
                  <option value="CLOSED">已结束</option>
                </Select>
              </Field>
              <Field label={t("assignmentTarget.label")}>
                <Select
                  value={form.targetMode}
                  onChange={event => setForm({ ...form, targetMode: event.target.value as AssignmentTargetMode, studentProfileIds: [] })}
                >
                  <option value="CLASS">{t("assignmentTarget.class")}</option>
                  <option value="STUDENTS">{t("assignmentTarget.students")}</option>
                </Select>
              </Field>
            </div>
            <Field label="作业说明">
              <TextArea
                value={form.description}
                onChange={event => setForm({ ...form, description: event.target.value })}
                placeholder="写给学生看的简短说明。"
              />
            </Field>
            {form.targetMode === "STUDENTS" && (
              <Field label={t("assignmentTarget.selectStudents")}>
                <div className="assignment-problem-list">
                  {students.length ? students.map(student => (
                    <label className={`assignment-problem-row ${form.studentProfileIds.includes(student.id) ? "is-selected" : ""}`} key={student.id}>
                      <input
                        type="checkbox"
                        checked={form.studentProfileIds.includes(student.id)}
                        onChange={() => setForm(current => ({
                          ...current,
                          studentProfileIds: current.studentProfileIds.includes(student.id)
                            ? current.studentProfileIds.filter(id => id !== student.id)
                            : [...current.studentProfileIds, student.id]
                        }))}
                      />
                      <span className="assignment-problem-row__info"><strong>{student.displayName}</strong><small>{student.studentNo}</small></span>
                    </label>
                  )) : <EmptyState title={t("assignmentTarget.emptyRoster")} description={t("assignmentTarget.emptyRosterHint")} />}
                </div>
              </Field>
            )}
          </section>

          <section className="assignment-builder-section" aria-label="选择题目">
            <div className="assignment-builder-section__head">
              <div>
                <p className="eyebrow">选择题目</p>
                <h2>题库</h2>
              </div>
            </div>
            <div className="assignment-builder-picker">
              <div className="assignment-problem-bank">
                <div className="assignment-problem-bank__tools">
                  <label className="assignment-search">
                    <Search size={16} />
                    <input value={query} onChange={event => setQuery(event.target.value)} placeholder="搜索题目" />
                  </label>
                  <Select value={difficulty} onChange={event => setDifficulty(event.target.value as DifficultyFilter)} aria-label="按难度筛选">
                    {DIFFICULTY_FILTERS.map(filter => (
                      <option value={filter.value} key={filter.value}>
                        {filter.label}
                      </option>
                    ))}
                  </Select>
                </div>
                <div className="assignment-problem-list">
                  {filteredProblems.length ? (
                    filteredProblems.map(problem => {
                      const checked = form.problemIds.includes(problem.id);
                      return (
                        <label className={`assignment-problem-row ${checked ? "is-selected" : ""}`} key={problem.id}>
                          <input type="checkbox" checked={checked} onChange={() => toggleProblem(problem.id)} />
                          <span className="assignment-problem-row__info">
                            <span>
                              <strong>{problem.title}</strong>
                              <small>{displayText(problem.summary, "暂无题目说明")}</small>
                            </span>
                            <DifficultyPill difficulty={problem.difficulty} />
                          </span>
                        </label>
                      );
                    })
                  ) : (
                    <EmptyState title="没有匹配的题目" description="换一个关键词或难度筛选。" />
                  )}
                </div>
              </div>

              <aside className="assignment-selected-panel" aria-label="已选题目">
                <div>
                  <p className="eyebrow">已选题目</p>
                  <strong>{selectedProblems.length} 题</strong>
                </div>
                {selectedProblems.length ? (
                  <div className="assignment-selected-list">
                    {selectedProblems.map(problem => (
                      <button type="button" key={problem.id} onClick={() => toggleProblem(problem.id)}>
                        <span>
                          <strong>{problem.title}</strong>
                          <small>{difficultyLabel(problem.difficulty)}</small>
                        </span>
                        <X size={15} />
                      </button>
                    ))}
                  </div>
                ) : (
                  <p>从左侧题库勾选题目，老师发布前可以在这里复查。</p>
                )}
              </aside>
            </div>
          </section>

          <section className="assignment-builder-section" aria-label="确认发布">
            <div className="assignment-builder-section__head">
              <div>
                <p className="eyebrow">确认发布</p>
                <h2>发布</h2>
              </div>
            </div>
            <div className="assignment-publish-review">
              <div>
                <span>作业名称</span>
                <strong>{form.title.trim() || "未填写"}</strong>
              </div>
              <div>
                <span>班级</span>
                <strong>{displayText(selectedClass?.name, "未选择")}</strong>
              </div>
              <div>
                <span>题目</span>
                <strong>{selectedProblems.length} 题</strong>
              </div>
              <div>
                <span>{t("assignmentTarget.label")}</span>
                <strong>{form.targetMode === "CLASS" ? t("assignmentTarget.class") : t("assignmentTarget.studentCount", { count: form.studentProfileIds.length })}</strong>
              </div>
              <div>
                <span>状态</span>
                <StatusPill tone={form.status === "ACTIVE" ? "success" : form.status === "DRAFT" ? "neutral" : "info"}>
                  {form.status === "ACTIVE" ? "进行中" : form.status === "DRAFT" ? "草稿" : "已结束"}
                </StatusPill>
              </div>
            </div>
            <p className={`assignment-publish-hint ${publishIssues.length ? "is-warning" : "is-ready"}`}>
              {publishIssues.length ? `还差：${publishIssues.join("、")}` : "信息完整，可以发布给学生。"}
            </p>
            <div className="assignment-readiness" aria-live="polite" aria-busy={readinessLoading}>
              <strong>
                {readinessLoading
                  ? t("assignmentReadiness.checking")
                  : readiness
                    ? readiness.publishable
                      ? t("assignmentReadiness.ready")
                      : t("assignmentReadiness.notReady")
                    : t("assignmentReadiness.empty")}
              </strong>
              {readiness?.problems.map(problem => {
                const issues: AssignmentReadinessIssue[] = [...problem.blockers, ...problem.warnings];
                return issues.length ? (
                  <div className="assignment-readiness__problem" key={problem.problemId}>
                    <span>{problem.problemTitle}</span>
                    <ul>
                      {issues.map((issue, index) => (
                        <li key={`${issue.code}-${index}`}>
                          <StatusPill tone={issue.severity === "BLOCKER" ? "warning" : "neutral"}>
                            {t(issue.severity === "BLOCKER" ? "assignmentReadiness.blocker" : "assignmentReadiness.warning")}
                          </StatusPill>
                          {readinessIssueText(issue, t)}
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null;
              })}
            </div>
            <div className="actions assignment-builder-actions">
              <Button
                type="button"
                variant="primary"
                onClick={() => void saveAssignment()}
                disabled={!canPublish}
                title={publishIssues.join("、") || undefined}
                icon={<CheckCircle2 size={17} />}
              >
                {saving ? "保存中" : "发布作业"}
              </Button>
              <Button type="button" variant="ghost" onClick={resetForm} disabled={saving}>
                清空
              </Button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
