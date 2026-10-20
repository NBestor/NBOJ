import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { ArrowLeft, ArrowRight, Plus } from "lucide-react";
import { Link } from "react-router-dom";
import { api } from "../../shared/api/client";
import type { Assignment, AssignmentOverview } from "../../shared/api/types";
import { assignmentStatusLabel, displayText, issueLabel, looksCorruptText } from "../../shared/format";
import { useTranslation } from "../../shared/i18n";
import { ButtonLink } from "../../shared/ui/Button";
import { EmptyState } from "../../shared/ui/EmptyState";
import { StatusPill } from "../../shared/ui/StatusPill";
import { buildClassWeaknessInsights } from "./classWeaknessInsights";

type ProgressCellTone = "empty" | "idle" | "submitted" | "passed" | "attention";
type ClassAssignment = Assignment & { title: string; className: string };
type ProgressCell = {
  assignmentId: number;
  label: string;
  detail: string;
  tone: ProgressCellTone;
  href?: string;
};
type ProgressRow = {
  studentProfileId: number;
  displayName: string;
  studentNo?: string | null;
  attentionCount: number;
  cells: ProgressCell[];
};

function cleanAssignmentTitle(value?: string | null, fallback = "课堂作业") {
  const title = displayText(value, fallback);
  return title.includes("试点任务") ? "课堂编程作业" : title;
}

function recentSubmissionDelta(overview?: AssignmentOverview | null) {
  const trend = overview?.progressTrend || [];
  if (trend.length >= 2) {
    return Math.max(0, trend[trend.length - 1].submissionCount - trend[trend.length - 2].submissionCount);
  }
  return trend[0]?.submissionCount ?? 0;
}

function studentIssue(student: AssignmentOverview["students"][number]) {
  const evidence = student.attentionEvidence?.[0];
  const tag = evidence?.fineGrainedTag || student.latestFineGrainedIssue || evidence?.issueTag || student.latestIssueTag || student.repeatedFineGrainedTag || student.repeatedIssueTag;
  if (student.attentionReason) {
    return student.attentionReason;
  }
  if (evidence?.headline || evidence?.reason) {
    return evidence.headline || evidence.reason || "需要关注";
  }
  return tag ? issueLabel(tag) : student.latestProgressSignal || "暂无明确错因";
}

function makeCell(assignment: ClassAssignment, student: AssignmentOverview["students"][number]): ProgressCell {
  const evidence = student.attentionEvidence?.find(item => item.problemId) || student.attentionEvidence?.[0];
  const href = evidence?.problemId
    ? `/teacher/assignment/${assignment.id}/problems/${evidence.problemId}/students/${student.studentProfileId}`
    : `/teacher/assignment/${assignment.id}`;
  if (student.needsAttention) {
    return {
      assignmentId: assignment.id,
      label: "需关注",
      detail: studentIssue(student),
      tone: "attention",
      href
    };
  }
  if (student.passedCount > 0) {
    return {
      assignmentId: assignment.id,
      label: "通过",
      detail: `${student.passedCount}/${student.attemptCount || student.passedCount} 次`,
      tone: "passed",
      href
    };
  }
  if (student.attemptCount > 0) {
    return {
      assignmentId: assignment.id,
      label: "已提交",
      detail: `${student.attemptCount} 次，${studentIssue(student)}`,
      tone: "submitted",
      href
    };
  }
  return {
    assignmentId: assignment.id,
    label: "未开始",
    detail: "暂无提交",
    tone: "idle",
    href
  };
}

function emptyCell(assignmentId: number): ProgressCell {
  return { assignmentId, label: "-", detail: "缺少可定位数据", tone: "empty" };
}

function firstStudentHref(row: ProgressRow) {
  return row.cells.find(cell => cell.tone === "attention" && cell.href)?.href || row.cells.find(cell => cell.href)?.href || "/teacher/classes";
}

export default function ClassOverviewPage() {
  const { t } = useTranslation();
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [overviewByAssignment, setOverviewByAssignment] = useState<Record<number, AssignmentOverview | null>>({});
  const [alert, setAlert] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void loadClassProgress();
  }, []);

  async function loadClassProgress() {
    setLoading(true);
    setAlert(null);
    try {
      const assignmentResult = await api.assignments();
      setAssignments(assignmentResult);
      const overviewEntries = await Promise.all(
        assignmentResult.map(async assignment => {
          try {
            return [assignment.id, await api.assignmentOverview(assignment.id)] as const;
          } catch {
            return [assignment.id, null] as const;
          }
        })
      );
      setOverviewByAssignment(Object.fromEntries(overviewEntries));
    } catch (error) {
      setAlert(error instanceof Error ? error.message : "班级进度加载失败。");
    } finally {
      setLoading(false);
    }
  }

  const cleanAssignments = useMemo(
    () =>
      assignments
        .filter(item => !looksCorruptText(item.title))
        .map(item => ({
          ...item,
          title: cleanAssignmentTitle(item.title, `课堂作业 #${item.id}`),
          className: displayText(item.className, "默认班级")
        })),
    [assignments]
  );
  const columns = useMemo(() => {
    const active = cleanAssignments.filter(item => item.status === "ACTIVE");
    return active.length ? active : cleanAssignments;
  }, [cleanAssignments]);
  const progressRows = useMemo(() => {
    const rowMap = new Map<number, Omit<ProgressRow, "cells"> & { cellMap: Map<number, ProgressCell> }>();
    columns.forEach(assignment => {
      const overview = overviewByAssignment[assignment.id];
      overview?.students.forEach(student => {
        const row =
          rowMap.get(student.studentProfileId) ||
          {
            studentProfileId: student.studentProfileId,
            displayName: displayText(student.displayName, `学生 #${student.studentProfileId}`),
            studentNo: student.studentNo,
            attentionCount: 0,
            cellMap: new Map<number, ProgressCell>()
          };
        const cell = makeCell(assignment, student);
        row.cellMap.set(assignment.id, cell);
        row.attentionCount += cell.tone === "attention" ? 1 : 0;
        rowMap.set(student.studentProfileId, row);
      });
    });
    return [...rowMap.values()]
      .map(row => ({
        studentProfileId: row.studentProfileId,
        displayName: row.displayName,
        studentNo: row.studentNo,
        attentionCount: row.attentionCount,
        cells: columns.map(assignment => row.cellMap.get(assignment.id) || emptyCell(assignment.id))
      }))
      .sort((left, right) => right.attentionCount - left.attentionCount || left.displayName.localeCompare(right.displayName, "zh-Hans-CN"));
  }, [columns, overviewByAssignment]);

  const summary = useMemo(() => {
    const overviews = columns.map(assignment => overviewByAssignment[assignment.id]).filter(Boolean) as AssignmentOverview[];
    const participantCount = overviews.length ? Math.max(...overviews.map(overview => overview.participantCount || 0)) : 0;
    const attentionStudentIds = new Set(progressRows.filter(row => row.attentionCount > 0).map(row => row.studentProfileId));
    const recentSubmissions = columns.reduce((sum, assignment) => sum + recentSubmissionDelta(overviewByAssignment[assignment.id]), 0);
    return {
      className: columns[0]?.className || "默认班级",
      assignmentCount: columns.length,
      participantCount,
      attentionCount: attentionStudentIds.size,
      recentSubmissions
    };
  }, [columns, overviewByAssignment, progressRows]);
  const classWeaknessInsights = useMemo(() => buildClassWeaknessInsights(columns, overviewByAssignment), [columns, overviewByAssignment]);
  const gridStyle: CSSProperties = {
    gridTemplateColumns: `minmax(190px, 1.05fr) repeat(${Math.max(columns.length, 1)}, minmax(132px, 0.72fr))`
  };

  return (
    <div className="stack class-overview-page class-progress-page">
      <section className="overview-command class-progress-command">
        <div className="overview-command__main">
          <Link to="/teacher" className="teacher-page-breadcrumb">
            <ArrowLeft size={15} /> {t("classOverview.breadcrumb")}
          </Link>
          <h1>{t("classOverview.title")}</h1>
        </div>
        <div className="teacher-home-actions">
          <ButtonLink to="/teacher" variant="secondary" icon={<ArrowLeft size={16} />}>
            {t("classOverview.backAssignments")}
          </ButtonLink>
          <ButtonLink to="/teacher/assignment/new" variant="primary" icon={<Plus size={16} />}>
            {t("classOverview.newAssignment")}
          </ButtonLink>
        </div>
      </section>

      {alert && <div className="alert alert--error">{alert}</div>}

      <section className="teacher-home-status-strip" aria-label="班级状态">
        <div>
          <span>默认班级</span>
          <strong>{summary.className}</strong>
        </div>
        <div>
          <span>进行中作业</span>
          <strong>{summary.assignmentCount || "-"}</strong>
        </div>
        <div>
          <span>总学生</span>
          <strong>{summary.participantCount || "-"}</strong>
        </div>
        <div>
          <span>需关注学生</span>
          <strong>{summary.attentionCount}</strong>
        </div>
        <div>
          <span>最近提交</span>
          <strong>{summary.recentSubmissions || "-"}</strong>
        </div>
      </section>

      {classWeaknessInsights.length ? (
        <section className="class-weakness-panel" aria-label="班级薄弱点">
          <div className="teacher-section-head teacher-section-head--compact">
            <div>
              <p className="eyebrow">细颗粒画像</p>
              <h2>班级薄弱点</h2>
            </div>
            <StatusPill tone="warning">{classWeaknessInsights.length} 个方向</StatusPill>
          </div>
          <div className="class-weakness-list">
            {classWeaknessInsights.map(item => (
              <article className={`class-weakness-card class-weakness-card--${item.kind}`} key={`${item.kind}-${item.label}`}>
                <div>
                  <span>{item.kind === "issue" ? "高频错因" : "能力薄弱"}</span>
                  <strong>{issueLabel(item.label)}</strong>
                </div>
                <p>{item.action || "建议选一题代表题做共同样例、边界和变量轨迹复盘。"}</p>
                <small>{item.count} 条证据 · {item.assignmentTitles.slice(0, 2).join("、")}</small>
              </article>
            ))}
          </div>
        </section>
      ) : null}

      <section className="class-progress-workbench" aria-label="班级进度矩阵">
        <div className="teacher-section-head teacher-section-head--compact">
          <div>
            <p className="eyebrow">学生 x 作业</p>
            <h2>进度矩阵</h2>
          </div>
          <StatusPill tone={summary.attentionCount ? "warning" : "success"}>{summary.attentionCount ? `${summary.attentionCount} 人关注` : "稳定"}</StatusPill>
        </div>

        {loading && !columns.length ? (
          <EmptyState title="正在读取班级进度" live />
        ) : !columns.length ? (
          <EmptyState title="暂无作业" description="新建作业后，这里会按学生和作业显示推进状态。" />
        ) : !progressRows.length ? (
          <EmptyState title="暂无可展示的学生进度" description="先查看作业或等待学生提交。" />
        ) : (
          <>
            <div className="class-progress-matrix class-progress-matrix--desktop">
              <div className="class-progress-grid class-progress-grid--head" style={gridStyle}>
                <span>学生</span>
                {columns.map(assignment => (
                  <Link to={`/teacher/assignment/${assignment.id}`} key={assignment.id}>
                    <strong>{assignment.title}</strong>
                    <small>{assignmentStatusLabel(assignment.status)}</small>
                  </Link>
                ))}
              </div>
              {progressRows.map(row => (
                <div className="class-progress-grid" style={gridStyle} key={row.studentProfileId}>
                  <Link className="class-progress-student" to={firstStudentHref(row)}>
                    <strong>{row.displayName}</strong>
                    <small>{row.studentNo ? `${row.studentNo} 号` : row.attentionCount ? `${row.attentionCount} 个关注` : "有提交记录"}</small>
                  </Link>
                  {row.cells.map(cell =>
                    cell.href ? (
                      <Link className={`class-progress-cell class-progress-cell--${cell.tone}`} to={cell.href} key={`${row.studentProfileId}-${cell.assignmentId}`}>
                        <strong>{cell.label}</strong>
                        <small>{cell.detail}</small>
                      </Link>
                    ) : (
                      <span className={`class-progress-cell class-progress-cell--${cell.tone}`} key={`${row.studentProfileId}-${cell.assignmentId}`}>
                        <strong>{cell.label}</strong>
                        <small>{cell.detail}</small>
                      </span>
                    )
                  )}
                </div>
              ))}
            </div>

            <div className="class-progress-mobile-list">
              {progressRows.map(row => (
                <article className="class-progress-mobile-card" key={`mobile-${row.studentProfileId}`}>
                  <Link className="class-progress-student" to={firstStudentHref(row)}>
                    <strong>{row.displayName}</strong>
                    <small>{row.studentNo ? `${row.studentNo} 号` : row.attentionCount ? `${row.attentionCount} 个关注` : "有提交记录"}</small>
                  </Link>
                  <div>
                    {row.cells.map((cell, index) => {
                      const assignment = columns[index];
                      const content = (
                        <>
                          <span>
                            <strong>{assignment.title}</strong>
                            <small>{cell.detail}</small>
                          </span>
                          <em>{cell.label}</em>
                        </>
                      );
                      return cell.href ? (
                        <Link className={`class-progress-mobile-row class-progress-cell--${cell.tone}`} to={cell.href} key={`${row.studentProfileId}-m-${cell.assignmentId}`}>
                          {content}
                          <ArrowRight size={15} />
                        </Link>
                      ) : (
                        <span className={`class-progress-mobile-row class-progress-cell--${cell.tone}`} key={`${row.studentProfileId}-m-${cell.assignmentId}`}>
                          {content}
                        </span>
                      );
                    })}
                  </div>
                </article>
              ))}
            </div>
          </>
        )}
      </section>

    </div>
  );
}
