import { useEffect, useMemo, useState } from "react";
import { ArrowRight, Plus, UsersRound } from "lucide-react";
import { Link } from "react-router-dom";
import { ApiError, api } from "../../shared/api/client";
import type { Assignment, AssignmentOverview, RecommendationActionEvidenceSignal } from "../../shared/api/types";
import { displayText, issueLabel, looksCorruptText } from "../../shared/format";
import { useTranslation } from "../../shared/i18n";
import { ButtonLink } from "../../shared/ui/Button";
import { EmptyState } from "../../shared/ui/EmptyState";
import { StatusPill } from "../../shared/ui/StatusPill";

type Alert = { type: "success" | "error"; message: string };
type TeacherHomeAssignment = Assignment & { title: string; className: string };
type Translator = (key: string, params?: Record<string, string | number>) => string;
type TeacherHomeErrorCopy = { serviceUnavailable: string; notFound: string; separator: string };

function cleanAssignmentTitle(value: string | null | undefined, fallback: string, pilotTitle: string) {
  const title = displayText(value, fallback);
  return title.includes("试点任务") ? pilotTitle : title;
}

function teacherErrorMessage(error: unknown, fallback: string, copy: TeacherHomeErrorCopy) {
  const base = fallback.replace(/[。.!！?？\s]+$/, "");
  if (error instanceof ApiError) {
    if (error.status >= 500) {
      return copy.serviceUnavailable;
    }
    if (error.status === 404) {
      return copy.notFound;
    }
    return `${base}${copy.separator}${error.message}`;
  }
  const detail = error instanceof Error ? error.message : "";
  return detail ? `${base}${copy.separator}${detail}` : fallback;
}

function attentionCount(overview?: AssignmentOverview | null) {
  return overview?.students.filter(student => student.needsAttention).length || 0;
}

function recentSubmissionDelta(overview?: AssignmentOverview | null) {
  const trend = overview?.progressTrend || [];
  if (trend.length >= 2) {
    const latest = trend[trend.length - 1];
    const previous = trend[trend.length - 2];
    return Math.max(0, latest.submissionCount - previous.submissionCount);
  }
  return trend[0]?.submissionCount ?? 0;
}

function overviewTargets(assignments: Assignment[]) {
  const active = assignments.filter(assignment => assignment.status === "ACTIVE");
  return active.length ? active : assignments.filter(assignment => assignment.status !== "DRAFT");
}

function assignmentStatusText(status: string | null | undefined, t: Translator) {
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

function overviewPassRate(overview?: AssignmentOverview | null) {
  if (!overview?.attemptCount) {
    return "-";
  }
  return `${Math.round((overview.passedAttemptCount / overview.attemptCount) * 100)}%`;
}

function submittedStudentCount(overview?: AssignmentOverview | null) {
  const trend = overview?.progressTrend || [];
  return trend[trend.length - 1]?.submittedStudentCount ?? 0;
}

function topSharedIssue(overview?: AssignmentOverview | null) {
  return overview?.problemSummaries?.find(problem => problem.topIssues?.[0])?.topIssues?.[0]?.label || null;
}

function actionOutcomeLabel(outcome: string | null | undefined, t: Translator) {
  switch (outcome) {
    case "UNRESOLVED_SAME_FOCUS":
      return t("teacherHome.console.actionUnresolved");
    case "TEACHER_INTERVENTION_NEEDED":
      return t("teacherHome.console.actionHighRisk");
    case "NO_FOLLOWUP_SUBMISSION":
      return t("teacherHome.console.actionNoFollowup");
    default:
      return t("teacherHome.console.actionEvidencePending");
  }
}

export default function TeacherPage() {
  const { t } = useTranslation();
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [overviewByAssignment, setOverviewByAssignment] = useState<Record<number, AssignmentOverview | null>>({});
  const [recommendationInterventions, setRecommendationInterventions] = useState<RecommendationActionEvidenceSignal[]>([]);
  const [alert, setAlert] = useState<Alert | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void loadTeacherHome();
  }, []);

  const cleanAssignments = useMemo(
    () =>
      assignments
        .filter(item => !looksCorruptText(item.title))
        .map(item => ({
          ...item,
          title: cleanAssignmentTitle(item.title, t("teacherHome.assignmentFallbackWithId", { id: item.id }), t("teacherHome.pilotTitle")),
          className: displayText(item.className, t("teacherHome.unboundClass"))
        })),
    [assignments, t]
  );
  const activeAssignments = useMemo(() => {
    const active = cleanAssignments.filter(item => item.status === "ACTIVE");
    return active.length ? active : cleanAssignments;
  }, [cleanAssignments]);
  const teacherHomeSummary = useMemo(() => {
    const activeOverviews = activeAssignments.map(assignment => overviewByAssignment[assignment.id]).filter(Boolean) as AssignmentOverview[];
    const participantCount = activeOverviews.length ? Math.max(...activeOverviews.map(overview => overview.participantCount || 0)) : 0;
    const attentionStudentIds = new Set<number>();
    activeOverviews.forEach(overview => {
      overview.students.forEach(student => {
        if (student.needsAttention) {
          attentionStudentIds.add(student.studentProfileId);
        }
      });
    });
    const recentSubmissions = activeAssignments.reduce((sum, assignment) => sum + recentSubmissionDelta(overviewByAssignment[assignment.id]), 0);
    return {
      className: cleanAssignments[0]?.className || t("teacherHome.defaultClass"),
      assignmentCount: cleanAssignments.length,
      participantCount,
      attentionCount: attentionStudentIds.size,
      recentSubmissions
    };
  }, [activeAssignments, cleanAssignments, overviewByAssignment, t]);
  const summaryTiles = [
    { key: "assignments", label: t("teacherHome.metrics.assignments"), value: teacherHomeSummary.assignmentCount || "-" },
    { key: "students", label: t("teacherHome.metrics.students"), value: teacherHomeSummary.participantCount || "-" },
    {
      key: "attention",
      label: t("teacherHome.metrics.needsAttention"),
      value: teacherHomeSummary.attentionCount,
      tone: teacherHomeSummary.attentionCount ? "warning" : "success"
    },
    { key: "recent", label: t("teacherHome.metrics.recentSubmissions"), value: teacherHomeSummary.recentSubmissions || "-" }
  ];
  const priorityQueue = useMemo(() => {
    return activeAssignments
      .map(assignment => {
        const overview = overviewByAssignment[assignment.id];
        const count = attentionCount(overview);
        const issue = topSharedIssue(overview);
        const targetStudent = overview?.students.find(student => student.needsAttention);
        const evidence = targetStudent?.attentionEvidence?.find(item => item.problemId) || targetStudent?.attentionEvidence?.[0] || null;
        const href =
          targetStudent && evidence?.problemId
            ? `/teacher/assignment/${assignment.id}/problems/${evidence.problemId}/students/${targetStudent.studentProfileId}`
            : `/teacher/assignment/${assignment.id}`;
        return {
          assignment,
          overview,
          count,
          issue,
          href,
          recent: recentSubmissionDelta(overview)
        };
      })
      .filter(item => item.count > 0)
      .sort((left, right) => right.count - left.count || right.recent - left.recent)
      .slice(0, 5);
  }, [activeAssignments, overviewByAssignment]);
  const actionQueue = useMemo(() => {
    const assignmentById = new Map(cleanAssignments.map(assignment => [assignment.id, assignment]));
    return recommendationInterventions.slice(0, 5).map(signal => {
      const assignment = signal.assignmentId ? assignmentById.get(signal.assignmentId) : null;
      const href = signal.assignmentId && signal.problemId && signal.studentProfileId
        ? `/teacher/assignment/${signal.assignmentId}/problems/${signal.problemId}/students/${signal.studentProfileId}`
        : signal.assignmentId
          ? `/teacher/assignment/${signal.assignmentId}`
          : "/teacher/classes";
      return { signal, assignment, href };
    });
  }, [cleanAssignments, recommendationInterventions]);

  async function loadTeacherHome() {
    setLoading(true);
    setAlert(null);
    try {
      const [assignmentResult, interventionResult] = await Promise.all([
        api.assignments(),
        api.recommendationInterventions().catch(() => [])
      ]);
      setAssignments(assignmentResult);
      setRecommendationInterventions(interventionResult);
      if (!assignmentResult.length) {
        setOverviewByAssignment({});
        return;
      }
      const overviewEntries = await Promise.all(
        overviewTargets(assignmentResult).map(async assignment => {
          try {
            return [assignment.id, await api.assignmentOverview(assignment.id)] as const;
          } catch {
            return [assignment.id, null] as const;
          }
        })
      );
      setOverviewByAssignment(Object.fromEntries(overviewEntries));
    } catch (error) {
      setAlert({
        type: "error",
        message: teacherErrorMessage(error, t("teacherHome.errors.loadFailed"), {
          serviceUnavailable: t("teacherHome.errors.serviceUnavailable"),
          notFound: t("teacherHome.errors.notFound"),
          separator: t("teacherHome.errors.separator")
        })
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="teacher-page teacher-console-page teacher-workflow teacher-workflow-home">
      {alert && <div className={`alert alert--${alert.type === "success" ? "success" : "error"}`}>{alert.message}</div>}

      <section className="teacher-console-header" aria-label={t("teacherHome.console.title")} aria-busy={loading}>
        <div className="teacher-console-header__main">
          <span className="teacher-console-kicker">{t("common.teacherWorkbench")}</span>
          <h1>{t("teacherHome.console.title")}</h1>
          <p>{t("teacherHome.console.description")}</p>
        </div>
        <div className="teacher-console-header__actions">
          <ButtonLink to="/teacher/classes" variant="secondary" icon={<UsersRound size={16} />}>
            {t("teacherHome.classProgress")}
          </ButtonLink>
          <ButtonLink to="/teacher/assignment/new" variant="primary" icon={<Plus size={17} />}>
            {t("teacherHome.newAssignment")}
          </ButtonLink>
        </div>
      </section>

      <section className="teacher-console-status-strip" aria-label={t("teacherHome.classOverview")}>
        <div>
          <span>{t("teacherHome.classLabel")}</span>
          <strong>{teacherHomeSummary.className}</strong>
        </div>
        {summaryTiles.map(tile => (
          <div key={tile.key}>
            <span>{tile.label}</span>
            <strong>{tile.value}</strong>
          </div>
        ))}
      </section>

      <section className="teacher-console-grid teacher-console-grid--with-aside" aria-label={t("teacherHome.assignmentListAria")} aria-busy={loading}>
        <main className="teacher-console-panel">
          <div className="teacher-console-panel__head">
            <div>
              <span className="teacher-console-panel__subtle">{t("teacherHome.assignmentCount", { count: activeAssignments.length })}</span>
              <h2>{t("teacherHome.console.tableTitle")}</h2>
            </div>
            <span className="teacher-console-panel__subtle">{t("teacherHome.console.tableHint")}</span>
          </div>
          {loading && !cleanAssignments.length ? (
            <EmptyState title={t("teacherHome.loadingAssignments")} live />
          ) : cleanAssignments.length ? (
            <div className="teacher-console-table teacher-assignment-center-table">
              <div className="teacher-console-table__row teacher-console-table__head">
                <span>{t("teacherHome.assignmentListTitle")}</span>
                <span>{t("teacherHome.console.classColumn")}</span>
                <span>{t("teacherHome.metrics.problems")}</span>
                <span>{t("teacherHome.console.submitted")}</span>
                <span>{t("teacherHome.console.passRate")}</span>
                <span>{t("teacherHome.console.attention")}</span>
                <span>{t("teacherHome.console.action")}</span>
              </div>
              {activeAssignments.map(assignment => {
                const overview = overviewByAssignment[assignment.id];
                const taskCount = assignment.tasks?.length || 0;
                const count = attentionCount(overview);
                const status = assignmentStatusText(assignment.status, t);
                const issue = topSharedIssue(overview);
                return (
                  <Link className="teacher-console-table__row" to={`/teacher/assignment/${assignment.id}`} key={assignment.id}>
                    <span className="teacher-console-table__title">
                      <strong>{assignment.title}</strong>
                      <small>{issue ? issueLabel(issue) : t("teacherHome.console.noSharedIssue")}</small>
                    </span>
                    <span className="teacher-console-fact">
                      <span>{t("teacherHome.console.classColumn")}</span>
                      <strong>{assignment.className}</strong>
                    </span>
                    <span className="teacher-console-fact">
                      <span>{t("teacherHome.metrics.problems")}</span>
                      <strong>{taskCount}</strong>
                    </span>
                    <span className="teacher-console-fact">
                      <span>{t("teacherHome.console.submitted")}</span>
                      <strong>{overview ? submittedStudentCount(overview) : "-"}/{overview ? overview.participantCount || "-" : "-"}</strong>
                    </span>
                    <span className="teacher-console-fact">
                      <span>{t("teacherHome.console.passRate")}</span>
                      <strong>{overviewPassRate(overview)}</strong>
                    </span>
                    <span className="teacher-console-fact">
                      <span>{t("teacherHome.console.attention")}</span>
                      <strong>{count ? t("teacherHome.needsAttentionWithCount", { count }) : t("teacherHome.console.stable")}</strong>
                    </span>
                    <span className="teacher-console-action" aria-hidden="true">
                      <span>{status}</span>
                      <ArrowRight size={17} />
                    </span>
                  </Link>
                );
              })}
            </div>
          ) : (
            <EmptyState title={t("teacherHome.emptyAssignmentsTitle")} description={t("teacherHome.emptyAssignmentsDescription")} />
          )}
        </main>

        <aside className="teacher-console-panel">
          <div className="teacher-console-panel__head">
            <div>
              <span className="teacher-console-panel__subtle">{t("assignmentDetail.focus.eyebrow")}</span>
              <h2>{t("teacherHome.console.queueTitle")}</h2>
            </div>
          </div>
          <div className="teacher-console-panel__body teacher-console-queue">
            <p className="teacher-console-panel__subtle">{t("teacherHome.console.queueHint")}</p>
            {actionQueue.length ? (
              actionQueue.map(item => (
                <Link className="teacher-console-queue__item" to={item.href} key={item.signal.recommendationToken || item.href}>
                  <span>
                    <strong>{item.assignment?.title || t("teacherHome.console.unboundAction")}</strong>
                    <StatusPill tone="warning">{t("teacherHome.console.actionNeedsReview")}</StatusPill>
                  </span>
                  <p>
                    {t("teacherHome.console.studentWithId", { id: item.signal.studentProfileId || "-" })}
                    {" · "}
                    {actionOutcomeLabel(item.signal.outcome, t)}
                  </p>
                  <small>{t("teacherHome.console.matchBasis", { basis: item.signal.matchBasis || "NONE" })}</small>
                </Link>
              ))
            ) : priorityQueue.length ? (
              priorityQueue.map(item => (
                <Link className="teacher-console-queue__item" to={item.href} key={item.assignment.id}>
                  <span>
                    <strong>{item.assignment.title}</strong>
                    <StatusPill tone="warning">{t("teacherHome.needsAttentionWithCount", { count: item.count })}</StatusPill>
                  </span>
                  <p>{item.issue ? issueLabel(item.issue) : t("teacherHome.console.noSharedIssue")}</p>
                  <small>{item.recent ? `${t("teacherHome.console.recent")} ${item.recent}` : t("teacherHome.console.waiting")}</small>
                </Link>
              ))
            ) : (
              <EmptyState title={t("teacherHome.console.queueEmptyTitle")} description={t("teacherHome.console.queueEmptyDescription")} />
            )}
          </div>
        </aside>
      </section>
    </div>
  );
}
