import { FormEvent, useEffect, useMemo, useState } from "react";
import { ArrowLeft, ArrowRight, ChartNoAxesCombined, PenLine, Presentation, UserRound } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { ApiError, api } from "../../shared/api/client";
import type { AssignmentOverview, DiagnosisTag } from "../../shared/api/types";
import { confidenceLabel, displayText, formatDateTime, hintPolicyLabel, issueLabel, percent, verdictLabel } from "../../shared/format";
import { useTranslation } from "../../shared/i18n";
import type { SubmissionHistorySummary } from "../../shared/api/types";
import { SingleProblemGrowthDashboard } from "../growth/SingleProblemGrowthDashboard";
import { Button, ButtonLink } from "../../shared/ui/Button";
import { EmptyState } from "../../shared/ui/EmptyState";
import { Field, Select, TextArea } from "../../shared/ui/Field";
import { StatusPill, VerdictPill } from "../../shared/ui/StatusPill";

type Alert = { type: "success" | "error"; message: string };
type ProblemSummary = NonNullable<AssignmentOverview["problemSummaries"]>[number];
type ProblemStudent = NonNullable<ProblemSummary["students"]>[number];
type OverviewStudent = AssignmentOverview["students"][number];
type TrendPoint = NonNullable<AssignmentOverview["progressTrend"]>[number];
type CorrectionDraft = {
  submissionId: number;
  correctedIssueTag: string;
  correctedFineGrainedTag: string;
  teacherNote: string;
};
type Translate = (key: string, params?: Record<string, string | number>) => string;

function cleanAssignmentTitle(value: string | null | undefined, t: Translate) {
  const title = displayText(value, t("assignmentDetail.fallbackTitle"));
  return title.includes("试点任务") || title === "课堂编程作业" ? t("assignmentDetail.pilotTitle") : title;
}

function teacherErrorMessage(error: unknown, fallback: string) {
  const base = fallback.replace(/[。.!！?？\s]+$/, "");
  if (error instanceof ApiError) {
    if (error.status === 404) {
      return `${base}，未找到作业。`;
    }
    if (error.status >= 500) {
      return `${base}，服务暂时不可用。`;
    }
    return `${base}，${error.message}`;
  }
  return error instanceof Error ? `${base}，${error.message}` : fallback;
}

function formatCountRate(value?: number | null) {
  return typeof value === "number" && Number.isFinite(value) ? percent(value) : "-";
}

function formatNumber(value?: number | null, digits = 1) {
  return typeof value === "number" && Number.isFinite(value) ? value.toFixed(digits).replace(/\.0$/, "") : "-";
}

function statusTone(status?: string | null): "neutral" | "success" | "warning" | "danger" | "info" {
  if (status === "已掌握") {
    return "success";
  }
  if (status === "需讲评") {
    return "warning";
  }
  if (status === "推进中") {
    return "info";
  }
  return "neutral";
}

function difficultyTone(difficulty?: string | null): "neutral" | "success" | "warning" | "danger" | "info" {
  const key = (difficulty || "").toUpperCase();
  if (key === "EASY") {
    return "success";
  }
  if (key === "MEDIUM") {
    return "warning";
  }
  if (key === "HARD") {
    return "danger";
  }
  return "neutral";
}

function difficultyText(difficulty: string | null | undefined, t: Translate) {
  switch ((difficulty || "").toUpperCase()) {
    case "EASY":
      return t("assignmentDetail.difficulty.easy");
    case "MEDIUM":
      return t("assignmentDetail.difficulty.medium");
    case "HARD":
      return t("assignmentDetail.difficulty.hard");
    default:
      return difficulty || t("assignmentDetail.difficulty.unknown");
  }
}

function assignmentStatusText(status: string | null | undefined, t: Translate) {
  switch ((status || "").toUpperCase()) {
    case "ACTIVE":
      return t("assignmentDetail.status.active");
    case "DRAFT":
      return t("assignmentDetail.status.draft");
    case "CLOSED":
      return t("assignmentDetail.status.closed");
    default:
      return status || t("assignmentDetail.status.unset");
  }
}

function problemStatusText(status: string | null | undefined, t: Translate) {
  if (status === "已掌握") {
    return t("assignmentDetail.status.mastered");
  }
  if (status === "需讲评") {
    return t("assignmentDetail.status.needsLecture");
  }
  if (status === "推进中") {
    return t("assignmentDetail.status.progressing");
  }
  if (status === "待提交") {
    return t("assignmentDetail.status.pending");
  }
  return status || t("assignmentDetail.status.pending");
}

function latestTrend(points: TrendPoint[]) {
  return points[points.length - 1] || null;
}

function trendSummary(points: TrendPoint[], overview: AssignmentOverview) {
  const latest = latestTrend(points);
  return {
    submitted: latest?.submittedStudentCount ?? 0,
    passed: latest?.passedStudentCount ?? 0,
    attempts: latest?.submissionCount ?? overview.attemptCount
  };
}

function recentTrendDelta(points: TrendPoint[]) {
  if (points.length >= 2) {
    return Math.max(0, points[points.length - 1].submissionCount - points[points.length - 2].submissionCount);
  }
  return points[0]?.submissionCount ?? 0;
}

function trendPath(points: TrendPoint[], key: "submittedStudentCount" | "passedStudentCount" | "submissionCount") {
  if (!points.length) {
    return "";
  }
  const maxValue = Math.max(1, ...points.map(point => point[key] || 0));
  const maxIndex = Math.max(1, points.length - 1);
  return points
    .map((point, index) => {
      const x = (index / maxIndex) * 100;
      const y = 100 - ((point[key] || 0) / maxValue) * 88 - 6;
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(" ");
}

function visibleProblems(overview: AssignmentOverview) {
  return [...(overview.problemSummaries || [])].sort((left, right) => (left.orderIndex ?? 0) - (right.orderIndex ?? 0));
}

function studentPassRate(student: ProblemStudent) {
  return student.attemptCount ? Math.round((student.passedCount / student.attemptCount) * 100) : 0;
}

function assignmentPassRate(overview: AssignmentOverview) {
  return overview.attemptCount ? Math.round((overview.passedAttemptCount / overview.attemptCount) * 100) : 0;
}

function problemStudentIssue(student: ProblemStudent) {
  return student.latestFineGrainedIssue || student.latestIssueTag ? issueLabel(student.latestFineGrainedIssue || student.latestIssueTag) : "-";
}

function latestStudentSubmission(students: ProblemStudent[]) {
  return [...students]
    .map(student => student.latestSubmittedAt)
    .filter(Boolean)
    .sort((left, right) => Date.parse(right || "") - Date.parse(left || ""))[0];
}

function sortedProblemStudents(students: ProblemStudent[]) {
  return [...students].sort((left, right) => {
    const attention = Number(right.needsAttention) - Number(left.needsAttention);
    if (attention) {
      return attention;
    }
    const submitted = Date.parse(right.latestSubmittedAt || "") - Date.parse(left.latestSubmittedAt || "");
    if (submitted) {
      return submitted;
    }
    return displayText(left.displayName, "").localeCompare(displayText(right.displayName, ""), "zh-Hans-CN");
  });
}

function timeValue(value?: string | null) {
  const parsed = Date.parse(value || "");
  return Number.isFinite(parsed) ? parsed : 0;
}

function problemPriorityScore(problem: ProblemSummary) {
  const passPenalty = typeof problem.passRate === "number" ? Math.max(0, 1 - problem.passRate) * 100 : 0;
  const statusWeight = problem.statusLabel === "需讲评" ? 800 : problem.statusLabel === "推进中" ? 220 : 0;
  return statusWeight + problem.attentionStudentCount * 80 + passPenalty + problem.submissionCount * 0.5;
}

function priorityProblem(problems: ProblemSummary[]) {
  const visible = problems.filter(problem => problem.submissionCount > 0 || problem.attentionStudentCount > 0);
  return [...(visible.length ? visible : problems)].sort((left, right) => problemPriorityScore(right) - problemPriorityScore(left))[0] || null;
}

function priorityStudentTarget(overview: AssignmentOverview, problems: ProblemSummary[]) {
  const problemTitleById = new Map(problems.map(problem => [problem.problemId, problem.title]));
  return overview.students
    .filter(student => student.needsAttention)
    .map(student => {
      const evidence = student.attentionEvidence?.find(item => item.problemId) || student.attentionEvidence?.[0] || null;
      const submittedAt = evidence?.submittedAt || "";
      return {
        student,
        problemId: evidence?.problemId,
        problemTitle: evidence?.problemId ? problemTitleById.get(evidence.problemId) : null,
        submittedAt,
        score: (student.repeatedIssueCount || 0) * 12 + (evidence?.problemId ? 6 : 0) + (submittedAt ? 3 : 0)
      };
    })
    .sort((left, right) => {
      const score = right.score - left.score;
      if (score) {
        return score;
      }
      return timeValue(right.submittedAt) - timeValue(left.submittedAt);
    })[0] || null;
}

function priorityStudentCopy(student: OverviewStudent, t: Translate) {
  if (student.attentionReason) {
    return student.attentionReason;
  }
  if (student.repeatedFineGrainedTag || student.repeatedIssueTag) {
    return t("assignmentDetail.focus.repeatedIssue", { issue: issueLabel(student.repeatedFineGrainedTag || student.repeatedIssueTag) });
  }
  return student.latestProgressSignal || t("assignmentDetail.focus.defaultStudentReason");
}

export default function AssignmentDetailPage() {
  const { t } = useTranslation();
  const { assignmentId, problemId, studentProfileId } = useParams<{
    assignmentId: string;
    problemId?: string;
    studentProfileId?: string;
  }>();
  const id = Number(assignmentId);
  const currentProblemId = Number(problemId);
  const currentStudentId = Number(studentProfileId);
  const [overview, setOverview] = useState<AssignmentOverview | null>(null);
  const [diagnosisTags, setDiagnosisTags] = useState<DiagnosisTag[]>([]);
  const [studentProblemGrowth, setStudentProblemGrowth] = useState<SubmissionHistorySummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [alert, setAlert] = useState<Alert | null>(null);
  const [correctionDraft, setCorrectionDraft] = useState<CorrectionDraft | null>(null);

  const coarseDiagnosisTags = useMemo(() => diagnosisTags.filter(tag => !tag.fineGrained), [diagnosisTags]);
  const fineDiagnosisTags = useMemo(() => diagnosisTags.filter(tag => tag.fineGrained), [diagnosisTags]);

  useEffect(() => {
    if (!Number.isFinite(id)) {
      setLoading(false);
      return;
    }
    void loadAssignmentDetail(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, currentProblemId, currentStudentId]);

  async function loadAssignmentDetail(targetId: number) {
    setLoading(true);
    setAlert(null);
    try {
      const growthRequest = Number.isFinite(currentProblemId) && Number.isFinite(currentStudentId)
        ? api.teacherStudentProblemGrowth(targetId, currentProblemId, currentStudentId).catch(() => [] as SubmissionHistorySummary[])
        : Promise.resolve([] as SubmissionHistorySummary[]);
      const [overviewResult, tags, growth] = await Promise.all([
        api.assignmentOverview(targetId),
        api.diagnosisTags(),
        growthRequest
      ]);
      setOverview(overviewResult);
      setDiagnosisTags(tags);
      setStudentProblemGrowth(growth);
    } catch (error) {
      setOverview(null);
      setStudentProblemGrowth([]);
      setAlert({ type: "error", message: teacherErrorMessage(error, "作业详情加载失败。") });
    } finally {
      setLoading(false);
    }
  }

  async function saveCorrection(event: FormEvent) {
    event.preventDefault();
    if (!overview || !correctionDraft) {
      return;
    }
    if (!correctionDraft.correctedIssueTag) {
      setAlert({ type: "error", message: "请选择主要错因。" });
      return;
    }
    setSaving(true);
    try {
      await api.correctDiagnosis(overview.assignment.id, {
        submissionId: correctionDraft.submissionId,
        correctedIssueTag: correctionDraft.correctedIssueTag,
        correctedFineGrainedTag: correctionDraft.correctedFineGrainedTag || null,
        teacherNote: correctionDraft.teacherNote,
        evalCandidate: true,
        correctedBy: "teacher"
      });
      setAlert({ type: "success", message: "教师校正已保存。" });
      setCorrectionDraft(null);
      await loadAssignmentDetail(overview.assignment.id);
    } catch (error) {
      setAlert({ type: "error", message: teacherErrorMessage(error, "教师校正保存失败。") });
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <EmptyState title={t("assignmentDetail.loading")} live />;
  }

  if (!overview) {
    return (
      <div className="stack assignment-detail-page teacher-workflow">
        {alert && <div className="alert alert--error">{alert.message}</div>}
        <EmptyState title={t("assignmentDetail.notFound")} description={t("assignmentDetail.notFoundDescription")} />
        <ButtonLink to="/teacher" variant="secondary" icon={<ArrowLeft size={16} />}>
          {t("assignmentDetail.backToAssignmentCenter")}
        </ButtonLink>
      </div>
    );
  }

  const problems = visibleProblems(overview);
  const selectedProblem = problems.find(problem => problem.problemId === currentProblemId) || null;
  const selectedStudent = selectedProblem?.students?.find(student => student.studentProfileId === currentStudentId) || null;

  return (
    <div className="teacher-page teacher-workflow assignment-drill-page">
      {alert && <div className={`alert alert--${alert.type === "success" ? "success" : "error"}`}>{alert.message}</div>}

      {!selectedProblem ? <AssignmentOverviewView overview={overview} problems={problems} /> : null}
      {selectedProblem && !selectedStudent ? <ProblemOverviewView overview={overview} problem={selectedProblem} /> : null}
      {selectedProblem && selectedStudent ? (
        <StudentProblemView
          overview={overview}
          problem={selectedProblem}
          student={selectedStudent}
          correctionDraft={correctionDraft}
          setCorrectionDraft={setCorrectionDraft}
          coarseDiagnosisTags={coarseDiagnosisTags}
          fineDiagnosisTags={fineDiagnosisTags}
          saving={saving}
          saveCorrection={saveCorrection}
          growthHistory={studentProblemGrowth}
        />
      ) : null}
    </div>
  );
}

function AssignmentOverviewView({ overview, problems }: { overview: AssignmentOverview; problems: ProblemSummary[] }) {
  const trend = overview.progressTrend || [];
  const { t } = useTranslation();
  const currentTrend = trendSummary(trend, overview);
  const passRate = assignmentPassRate(overview);
  const recentSubmissions = recentTrendDelta(trend);
  const submittedText = t("assignmentDetail.metrics.submittedPeople", { count: currentTrend.submitted });
  const studentTarget = priorityStudentTarget(overview, problems);
  const lectureProblem = priorityProblem(problems);
  const lectureIssue = lectureProblem?.topIssues?.[0] || null;
  const studentTargetHref = studentTarget?.problemId
    ? `/teacher/assignment/${overview.assignment.id}/problems/${studentTarget.problemId}/students/${studentTarget.student.studentProfileId}`
    : `/teacher/assignment/${overview.assignment.id}`;
  const lectureHref = lectureProblem
    ? `/teacher/assignment/${overview.assignment.id}/problems/${lectureProblem.problemId}`
    : `/teacher/assignment/${overview.assignment.id}`;
  const startReviewHref = studentTarget ? studentTargetHref : lectureHref;
  const historicalStudentCount = overview.students.filter(student => student.currentRoster === false).length;
  return (
    <>
      <section className="teacher-drill-header teacher-drill-header--compact">
        <div className="teacher-drill-header__main">
          <Link to="/teacher" className="back-link">
            <ArrowLeft size={16} /> {t("assignmentDetail.backToAssignmentCenter")}
          </Link>
          <h1>{cleanAssignmentTitle(overview.assignment.title, t)}</h1>
          <div className="assignment-detail-meta">
            <span>{displayText(overview.assignment.className, t("assignmentDetail.defaultClass"))}</span>
            <span>{t("assignmentDetail.header.problemCount", { count: problems.length })}</span>
            <span>{assignmentStatusText(overview.assignment.status, t)}</span>
            <span>{t("assignmentDetail.header.submitted", { count: currentTrend.submitted })}</span>
            <span>{t("assignmentDetail.header.passRate", { rate: passRate })}</span>
          </div>
        </div>
        <div className="teacher-drill-header__actions">
          <ButtonLink to={startReviewHref} variant="primary" disabled={!studentTarget && !lectureProblem} icon={<ArrowRight size={16} />}>
            {t("assignmentDetail.header.startReview")}
          </ButtonLink>
          <ButtonLink to={lectureHref} variant="secondary" disabled={!lectureProblem} icon={<Presentation size={16} />}>
            {t("assignmentDetail.header.viewProblem")}
          </ButtonLink>
        </div>
      </section>

      {historicalStudentCount > 0 ? (
        <div className="alert alert--warning">
          {t("assignmentDetail.historicalRosterNotice", { count: historicalStudentCount })}
        </div>
      ) : null}

      <section className="teacher-drill-overview">
        <section className="teacher-focus-panel" aria-label={t("assignmentDetail.focus.aria")}>
          <div className="teacher-focus-panel__head">
            <p className="eyebrow">{t("assignmentDetail.focus.eyebrow")}</p>
            <h2>{t("assignmentDetail.focus.title")}</h2>
          </div>
          <div className="teacher-focus-list">
            <div className={`teacher-focus-row ${studentTarget ? "" : "is-muted"}`}>
              <span className="teacher-focus-row__icon">
                <UserRound size={17} />
              </span>
              <div>
                <span>{t("assignmentDetail.focus.studentLabel")}</span>
                <strong>
                  {studentTarget
                    ? displayText(studentTarget.student.displayName, t("assignmentDetail.unknownStudent", { id: studentTarget.student.studentProfileId }))
                    : t("assignmentDetail.focus.noStudentTitle")}
                </strong>
                <p>
                  {studentTarget
                    ? `${studentTarget.problemTitle ? `${studentTarget.problemTitle}: ` : ""}${priorityStudentCopy(studentTarget.student, t)}`
                    : t("assignmentDetail.focus.noStudentDescription")}
                </p>
              </div>
              {studentTarget ? (
                <ButtonLink to={studentTargetHref} variant="ghost" icon={<ArrowRight size={15} />}>
                  {t("assignmentDetail.focus.studentAction")}
                </ButtonLink>
              ) : (
                <StatusPill tone="success">{t("assignmentDetail.focus.stable")}</StatusPill>
              )}
            </div>

            <div className={`teacher-focus-row ${lectureProblem ? "" : "is-muted"}`}>
              <span className="teacher-focus-row__icon">
                <Presentation size={17} />
              </span>
              <div>
                <span>{t("assignmentDetail.focus.problemLabel")}</span>
                <strong>{lectureProblem ? lectureProblem.title : t("assignmentDetail.focus.noProblemTitle")}</strong>
                <p>
                  {lectureProblem
                    ? lectureIssue?.label
                      ? t("assignmentDetail.focus.problemWithIssue", { count: lectureProblem.attentionStudentCount || 0, issue: issueLabel(lectureIssue.label) })
                      : t("assignmentDetail.focus.problemWithoutIssue", { count: lectureProblem.attentionStudentCount || 0 })
                    : t("assignmentDetail.focus.noProblemDescription")}
                </p>
              </div>
              {lectureProblem ? (
                <ButtonLink to={lectureHref} variant="ghost" icon={<ArrowRight size={15} />}>
                  {t("assignmentDetail.header.viewProblem")}
                </ButtonLink>
              ) : (
                <StatusPill>{t("assignmentDetail.status.pending")}</StatusPill>
              )}
            </div>
          </div>
        </section>

        <div className="teacher-compact-metrics" aria-label={t("assignmentDetail.metrics.aria")}>
          <CompactMetric label={t("assignmentDetail.metrics.submittedStudents")} value={submittedText} />
          <CompactMetric label={t("assignmentDetail.metrics.passedStudents")} value={currentTrend.passed || "-"} />
          <CompactMetric label={t("assignmentDetail.metrics.totalSubmissions")} value={overview.attemptCount} />
          <CompactMetric label={t("assignmentDetail.metrics.needsAttention")} value={overview.strugglingStudentCount} />
          <CompactMetric label={t("assignmentDetail.metrics.recentSubmissions")} value={recentSubmissions || "-"} />
        </div>

        {trend.length >= 2 ? (
          <article className="teacher-trend-panel teacher-trend-panel--compact">
            <div>
              <p className="eyebrow">{t("assignmentDetail.trend.eyebrow")}</p>
              <h2>{t("assignmentDetail.trend.title")}</h2>
            </div>
            <div className="teacher-trend-chart" aria-label={t("assignmentDetail.trend.chartAria")}>
              <svg viewBox="0 0 100 100" preserveAspectRatio="none">
                <polyline className="trend-line trend-line--attempts" points={trendPath(trend, "submissionCount")} />
                <polyline className="trend-line trend-line--submitted" points={trendPath(trend, "submittedStudentCount")} />
                <polyline className="trend-line trend-line--passed" points={trendPath(trend, "passedStudentCount")} />
              </svg>
              <div className="teacher-trend-legend">
                <span>{t("assignmentDetail.trend.attempts")}</span>
                <span>{t("assignmentDetail.trend.submitted")}</span>
                <span>{t("assignmentDetail.trend.passed")}</span>
              </div>
            </div>
          </article>
        ) : (
          <p className="teacher-trend-note">
            {trend.length === 1
              ? t("assignmentDetail.trend.onePoint", {
                  attempts: currentTrend.attempts,
                  submitted: currentTrend.submitted,
                  passed: currentTrend.passed
                })
              : t("assignmentDetail.trend.empty")}
          </p>
        )}

        <section className="teacher-problem-table teacher-problem-table--compact" aria-label={t("assignmentDetail.problems.aria")}>
          <div className="teacher-section-head">
            <div>
              <p className="eyebrow">{t("assignmentDetail.problems.eyebrow")}</p>
              <h2>{t("assignmentDetail.problems.title")}</h2>
            </div>
          </div>
          <div className="teacher-table teacher-table--problems teacher-problem-progress-table">
            <div className="teacher-table-row teacher-table-row--head">
              <span>{t("assignmentDetail.problems.problem")}</span>
              <span>{t("assignmentDetail.problems.difficulty")}</span>
              <span>{t("assignmentDetail.problems.submitted")}</span>
              <span>{t("assignmentDetail.problems.passed")}</span>
              <span>{t("assignmentDetail.problems.attention")}</span>
              <span>{t("assignmentDetail.problems.issue")}</span>
              <span>{t("assignmentDetail.problems.action")}</span>
            </div>
            {problems.map(problem => (
              <Link
                className="teacher-table-row teacher-table-row--link"
                to={`/teacher/assignment/${overview.assignment.id}/problems/${problem.problemId}`}
                key={problem.problemId}
              >
                <span className="teacher-table-title">
                  <strong>{problem.title}</strong>
                  <small>{problemStatusText(problem.statusLabel, t)}</small>
                </span>
                <span data-label={t("assignmentDetail.problems.difficulty")}>
                  <StatusPill tone={difficultyTone(problem.difficulty)}>{difficultyText(problem.difficulty, t)}</StatusPill>
                </span>
                <span data-label={t("assignmentDetail.problems.submitted")}>{formatCountRate(problem.submissionRate)}</span>
                <span data-label={t("assignmentDetail.problems.passed")}>{formatCountRate(problem.passRate)}</span>
                <span data-label={t("assignmentDetail.problems.attention")}>{problem.attentionStudentCount || "-"}</span>
                <span data-label={t("assignmentDetail.problems.issue")}>{problem.topIssues?.[0]?.label ? issueLabel(problem.topIssues[0].label) : t("assignmentDetail.problems.noIssue")}</span>
                <span className="teacher-table-action">
                  <span>{t("assignmentDetail.problems.view")}</span>
                  <ArrowRight size={16} />
                </span>
              </Link>
            ))}
          </div>
        </section>
      </section>
    </>
  );
}

function ProblemOverviewView({ overview, problem }: { overview: AssignmentOverview; problem: ProblemSummary }) {
  const { t } = useTranslation();
  const students = sortedProblemStudents(problem.students || []);
  const topIssue = problem.topIssues?.[0] || null;
  const topAbility = problem.abilityWeaknesses?.[0] || null;
  const hasStudentRows = students.length > 0;
  const hasSubmissions = problem.submissionCount > 0;
  const hasUnlinkedSubmissions = hasSubmissions && !hasStudentRows;
  const latestSubmittedAt = latestStudentSubmission(students);
  const firstAttentionStudent = students.find(student => student.needsAttention) || null;
  const actionTitle = hasUnlinkedSubmissions ? "先补学生关联" : !hasSubmissions ? "等待提交" : problem.statusLabel === "需讲评" ? "先讲这题" : "继续观察";
  const actionCopy = hasUnlinkedSubmissions
    ? "这道题已有提交，但还没有匹配到学生名单；先检查名单或提交关联。"
    : !hasSubmissions
      ? "暂无提交证据。"
      : topIssue?.interventionSuggestion || topAbility?.abilityPoint || "等待更多学生提交后再形成讲评建议。";
  return (
    <>
      <section className="teacher-drill-header">
        <div>
          <Link to={`/teacher/assignment/${overview.assignment.id}`} className="back-link">
            <ArrowLeft size={16} /> 返回作业总览
          </Link>
          <h1>{problem.title}</h1>
          <div className="assignment-detail-meta">
            <span>{cleanAssignmentTitle(overview.assignment.title, t)}</span>
            <span>{problem.submittedStudentCount} 人提交</span>
            <span>{problem.submissionCount} 次提交</span>
            <span>{problem.statusLabel || "待提交"}</span>
          </div>
        </div>
      </section>

      <section className="teacher-problem-layout">
        <main className="teacher-problem-main">
          <div className="teacher-drill-strip">
            <Metric label="提交率" value={formatCountRate(problem.submissionRate)} />
            <Metric label="通过率" value={formatCountRate(problem.passRate)} />
            <Metric label="人均尝试" value={formatNumber(problem.averageAttempts)} />
            <Metric label="需关注" value={problem.attentionStudentCount || "-"} />
            <Metric label="最新提交" value={latestSubmittedAt ? formatDateTime(latestSubmittedAt) : hasSubmissions ? "有提交" : "-"} />
          </div>

          <section className="teacher-analysis-grid">
            <RankPanel title="高频错因" items={(problem.topIssues || []).map(item => ({ label: issueLabel(item.label), value: item.count, note: item.interventionSuggestion }))} />
            <RankPanel title="知识点薄弱" items={(problem.abilityWeaknesses || []).map(item => ({ label: item.abilityPoint, value: item.submissionCount, note: item.evidenceTags?.map(issueLabel).join("、") }))} />
            <RankPanel title="提示进度" items={(problem.hintLevelDistribution || []).map(item => ({ label: hintPolicyLabel(item.hintLevel), value: item.count }))} />
          </section>

          <section className="teacher-problem-table" aria-label="学生题目明细">
            <div className="teacher-section-head">
              <div>
                <p className="eyebrow">学生</p>
                <h2>这道题的学生情况</h2>
              </div>
            </div>
            <div className="teacher-table teacher-table--students">
              <div className="teacher-table-row teacher-table-row--head">
                <span>学生</span>
                <span>提交</span>
                <span>是否通过</span>
                <span>最新结果</span>
                <span>提示层级</span>
                <span>错因</span>
                <span>最近提交</span>
                <span></span>
              </div>
              {hasStudentRows ? (
                students.map(student => (
                  <Link
                    className="teacher-table-row teacher-table-row--link"
                    to={`/teacher/assignment/${overview.assignment.id}/problems/${problem.problemId}/students/${student.studentProfileId}`}
                    key={student.studentProfileId}
                  >
                    <span className="teacher-table-title">
                      <strong>{displayText(student.displayName, `学生 #${student.studentProfileId}`)}</strong>
                      {student.studentNo ? <small>{student.studentNo} 号</small> : null}
                    </span>
                    <span data-label="提交">{student.attemptCount} 次</span>
                    <span data-label="是否通过">{student.passedCount > 0 ? "通过" : "未通过"}</span>
                    <span data-label="最新结果"><VerdictPill verdict={student.latestVerdict} /></span>
                    <span data-label="提示层级">{student.latestHintLevel ? hintPolicyLabel(student.latestHintLevel) : "-"}</span>
                    <span data-label="错因">{problemStudentIssue(student)}</span>
                    <span data-label="最近提交">{student.latestSubmittedAt ? formatDateTime(student.latestSubmittedAt) : "-"}</span>
                    <span className="teacher-table-action">
                      {student.needsAttention ? <StatusPill tone="warning">关注</StatusPill> : <StatusPill tone="success">稳定</StatusPill>}
                      <ArrowRight size={16} />
                    </span>
                  </Link>
                ))
              ) : (
                <EmptyState
                  title={hasUnlinkedSubmissions ? "有提交记录，但未关联学生名单" : "暂无学生提交"}
                  description={
                    hasUnlinkedSubmissions
                      ? "有提交未匹配到学生名单，请先检查名单或提交关联。"
                      : "暂无学生提交。"
                  }
                />
              )}
            </div>
          </section>
        </main>

        <aside className="teacher-action-rail">
          <p className="eyebrow">教师动作</p>
          <h2>{actionTitle}</h2>
          <p>{actionCopy}</p>
          {firstAttentionStudent ? (
            <ButtonLink
              to={`/teacher/assignment/${overview.assignment.id}/problems/${problem.problemId}/students/${firstAttentionStudent.studentProfileId}`}
              variant="primary"
              icon={<UserRound size={16} />}
            >
              查看优先学生
            </ButtonLink>
          ) : null}
          <ButtonLink to={`/teacher/assignment/${overview.assignment.id}`} variant="ghost" icon={<ChartNoAxesCombined size={16} />}>
            看作业推进
          </ButtonLink>
        </aside>
      </section>
    </>
  );
}

function StudentProblemView({
  overview,
  problem,
  student,
  correctionDraft,
  setCorrectionDraft,
  coarseDiagnosisTags,
  fineDiagnosisTags,
  saving,
  saveCorrection,
  growthHistory
}: {
  overview: AssignmentOverview;
  problem: ProblemSummary;
  student: ProblemStudent;
  correctionDraft: CorrectionDraft | null;
  setCorrectionDraft: (value: CorrectionDraft | null) => void;
  coarseDiagnosisTags: DiagnosisTag[];
  fineDiagnosisTags: DiagnosisTag[];
  saving: boolean;
  saveCorrection: (event: FormEvent) => void;
  growthHistory: SubmissionHistorySummary[];
}) {
  const { t } = useTranslation();
  const currentIssue = student.latestFineGrainedIssue || student.latestIssueTag ? issueLabel(student.latestFineGrainedIssue || student.latestIssueTag) : "等待更多证据";
  const issueTrajectories = student.recentLearningState?.issueTrajectories || [];
  return (
    <>
      <section className="teacher-drill-header">
        <div>
          <Link to={`/teacher/assignment/${overview.assignment.id}/problems/${problem.problemId}`} className="back-link">
            <ArrowLeft size={16} /> 返回题目总览
          </Link>
          <h1>{displayText(student.displayName, t("common.studentSide"))}</h1>
          <div className="assignment-detail-meta">
            <span>{problem.title}</span>
            <span>{student.attemptCount} 次提交</span>
            <span>{studentPassRate(student)}% 通过</span>
            <span>{student.latestHintLevel ? hintPolicyLabel(student.latestHintLevel) : "提示待定"}</span>
          </div>
        </div>
      </section>

      <section className="teacher-student-problem-layout">
        <main className="teacher-student-evidence">
          {growthHistory.length ? (
            <SingleProblemGrowthDashboard
              history={growthHistory}
              selectedSubmissionId={student.latestSubmissionId}
              currentSummary={growthHistory.find(item => item.id === student.latestSubmissionId)?.growthSummary}
              mode="teacher"
            />
          ) : null}
          <article className="teacher-evidence-card teacher-evidence-card--decision">
            <span>当前卡点</span>
            <strong>{currentIssue}</strong>
            <p>{student.latestIssue || student.latestProgressSignal || "这名学生在这道题上还没有形成明确错因。"}</p>
            <div className="teacher-decision-facts">
              <Metric label="提交次数" value={student.attemptCount} />
              <Metric label="通过次数" value={student.passedCount} />
              <Metric label="最新结果" value={verdictLabel(student.latestVerdict)} />
              <Metric label="提示层级" value={student.latestHintLevel ? hintPolicyLabel(student.latestHintLevel) : "-"} />
              <Metric label="最近提交" value={student.latestSubmittedAt ? formatDateTime(student.latestSubmittedAt) : "-"} />
            </div>
          </article>

          <section className="teacher-issue-timeline" aria-label={t("teacherIssueTimeline.aria")}>
            <div className="teacher-section-head">
              <div>
                <p className="eyebrow">{t("teacherIssueTimeline.eyebrow")}</p>
                <h2>{t("teacherIssueTimeline.title")}</h2>
              </div>
              <span>{t("teacherIssueTimeline.total", { count: issueTrajectories.length })}</span>
            </div>
            {issueTrajectories.length ? (
              <div className="teacher-issue-timeline__list">
                {issueTrajectories.map(item => (
                  <article className={`is-${String(item.currentStatus || "observing").toLowerCase()}`} key={item.normalizedPointKey}>
                    <div>
                      <strong>{item.label || t("teacherIssueTimeline.unnamed")}</strong>
                      <span>{t(`teacherIssueTimeline.status.${teacherIssueStatusKey(item.currentStatus)}`)}</span>
                    </div>
                    <p>{t("teacherIssueTimeline.metrics", {
                      raw: item.rawOccurrenceCount,
                      effective: item.effectiveOccurrenceCount,
                      consecutive: item.consecutiveEffectiveCount,
                      problems: item.affectedProblemCount
                    })}</p>
                    <div className="teacher-issue-timeline__labels">
                      {(item.personalLabels || []).map(label => (
                        <span key={label}>{t(`teacherIssueTimeline.label.${teacherIssueLabelKey(label)}`)}</span>
                      ))}
                    </div>
                    <small>{t("teacherIssueTimeline.evidence", {
                      ids: (item.evidenceSubmissionIds || []).map(id => `#${id}`).join("、") || "-"
                    })}</small>
                  </article>
                ))}
              </div>
            ) : (
              <p className="teacher-issue-timeline__empty">{t("teacherIssueTimeline.empty")}</p>
            )}
          </section>

          <div className="teacher-evidence-grid">
            <article className="teacher-evidence-card">
              <span>AI 判断</span>
              <strong>{confidenceLabel(student.latestConfidence)}</strong>
              <p>{student.latestProgressSignal || student.latestHintAction || "AI 还没有形成稳定判断。"}</p>
            </article>

            <article className="teacher-evidence-card">
              <span>错因标签</span>
              <strong>{currentIssue}</strong>
              <p>{displayText(student.abilityPoint, "知识点待观察")}</p>
            </article>

            <article className="teacher-evidence-card">
              <span>评测证据</span>
              <strong>{verdictLabel(student.latestVerdict)}</strong>
              <p>{student.latestSubmittedAt ? `${formatDateTime(student.latestSubmittedAt)} 的最近一次提交。` : "还没有可展示的提交时间。"}</p>
            </article>
          </div>
        </main>

        <aside className="teacher-action-rail teacher-action-rail--primary">
          <p className="eyebrow">教师动作</p>
          <h2>{student.needsAttention ? "优先处理" : "保持观察"}</h2>
          <p>{student.latestHintAction || student.latestProgressSignal || "让学生先补一次可见提交，再判断是否需要讲解。"}</p>
          <article className="teacher-action-note">
            <strong>给学生的下一步问题</strong>
            <p>{student.latestHintAction || `先让学生解释：这次结果为什么是${verdictLabel(student.latestVerdict)}？`}</p>
          </article>
          <Button
            type="button"
            variant="secondary"
            icon={<PenLine size={15} />}
            disabled={!student.latestSubmissionId}
            onClick={() =>
              student.latestSubmissionId &&
              setCorrectionDraft({
                submissionId: student.latestSubmissionId,
                correctedIssueTag: student.latestIssueTag || "",
                correctedFineGrainedTag: student.latestFineGrainedIssue || "",
                teacherNote: ""
              })
            }
          >
            校正 AI 错因
          </Button>
          {!student.latestSubmissionId ? <small className="teacher-action-hint">暂无可校正提交，先等待学生完成一次提交。</small> : null}

          {correctionDraft ? (
            <form className="teacher-correction-panel assignment-correction-panel" onSubmit={saveCorrection}>
              <Field label="主要错因">
                <Select
                  value={correctionDraft.correctedIssueTag}
                  onChange={event => setCorrectionDraft({ ...correctionDraft, correctedIssueTag: event.target.value })}
                >
                  <option value="">请选择</option>
                  {coarseDiagnosisTags.map(tag => (
                    <option value={tag.id} key={tag.id}>
                      {tag.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="细分错因">
                <Select
                  value={correctionDraft.correctedFineGrainedTag}
                  onChange={event => setCorrectionDraft({ ...correctionDraft, correctedFineGrainedTag: event.target.value })}
                >
                  <option value="">不指定</option>
                  {fineDiagnosisTags.map(tag => (
                    <option value={tag.id} key={tag.id}>
                      {tag.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="校正理由">
                <TextArea
                  value={correctionDraft.teacherNote}
                  onChange={event => setCorrectionDraft({ ...correctionDraft, teacherNote: event.target.value })}
                  placeholder="例如：不是循环边界，而是输入读取理解错。"
                />
              </Field>
              <div className="actions">
                <Button type="submit" variant="primary" disabled={saving}>
                  保存
                </Button>
                <Button type="button" variant="ghost" onClick={() => setCorrectionDraft(null)}>
                  取消
                </Button>
              </div>
            </form>
          ) : null}
        </aside>
      </section>
    </>
  );
}

function teacherIssueStatusKey(status?: string | null) {
  switch (String(status || "").toUpperCase()) {
    case "UNRESOLVED":
      return "unresolved";
    case "RECOVERED":
      return "recovered";
    default:
      return "notObserved";
  }
}

function teacherIssueLabelKey(label?: string | null) {
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
    default:
      return "observing";
  }
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="teacher-metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function CompactMetric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="teacher-compact-metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function RankPanel({ title, items }: { title: string; items: Array<{ label?: string | null; value: number; note?: string | null }> }) {
  return (
    <article className="teacher-rank-panel">
      <p className="eyebrow">{title}</p>
      {items.length ? (
        <div className="teacher-rank-list">
          {items.slice(0, 5).map((item, index) => (
            <div className="teacher-rank-row" key={`${title}-${item.label}-${index}`}>
              <span>{displayText(item.label, "待定")}</span>
              <strong>{item.value}</strong>
              {item.note ? <small>{item.note}</small> : null}
            </div>
          ))}
        </div>
      ) : (
        <p>暂无集中信号。</p>
      )}
    </article>
  );
}
