import { useEffect, useMemo, useState } from "react";
import { CheckCircle2, Clock3, Info, LockKeyhole } from "lucide-react";
import { Navigate, useParams } from "react-router-dom";
import { api } from "../../shared/api/client";
import type { StudentAssignmentLeaderboard } from "../../shared/api/types";
import { EmptyState } from "../../shared/ui/EmptyState";
import {
  assignmentTaskState,
  formatRelativeTime,
  StudentAssignmentShell,
  useStudentAssignmentWorkspace
} from "./StudentAssignmentWorkspace";

type VisibleRankRow = StudentAssignmentLeaderboard["rows"][number] | null;

function visibleRankingRows(data: StudentAssignmentLeaderboard | null): VisibleRankRow[] {
  if (!data || data.rows.length <= 9) return data?.rows || [];
  const currentIndex = data.rows.findIndex(item => item.currentStudent);
  if (currentIndex >= 0 && currentIndex < 9) return data.rows.slice(0, 9);
  const indexes = new Set([0, 1, 2, 3, 4]);
  for (let index = Math.max(0, currentIndex - 1); index <= Math.min(data.rows.length - 1, currentIndex + 1); index++) {
    indexes.add(index);
  }
  const sorted = [...indexes].sort((left, right) => left - right);
  const result: VisibleRankRow[] = [];
  sorted.forEach((index, position) => {
    if (position > 0 && index - sorted[position - 1] > 1) result.push(null);
    result.push(data.rows[index]);
  });
  return result;
}

export default function StudentAssignmentRankingPage() {
  const { assignmentId } = useParams();
  const numericAssignmentId = Number(assignmentId);
  const workspace = useStudentAssignmentWorkspace(numericAssignmentId);
  const [leaderboard, setLeaderboard] = useState<StudentAssignmentLeaderboard | null>(null);
  const [rankingLoading, setRankingLoading] = useState(true);
  const [rankingFailed, setRankingFailed] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    if (!workspace.student || !Number.isFinite(numericAssignmentId)) {
      setRankingLoading(false);
      return () => {
        ignore = true;
      };
    }
    setRankingLoading(true);
    api.studentAssignmentLeaderboard(numericAssignmentId)
      .then(result => {
        if (!ignore) setLeaderboard(result);
      })
      .catch(error => {
        if (!ignore) setRankingFailed(error instanceof Error ? error.message : "班内排名加载失败");
      })
      .finally(() => {
        if (!ignore) setRankingLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [numericAssignmentId, workspace.student]);

  const rows = useMemo(() => visibleRankingRows(leaderboard), [leaderboard]);
  if (!workspace.student) return <Navigate to="/student/login" replace />;
  if (workspace.loading || rankingLoading) return <EmptyState title="正在读取班内排名" live />;
  if (!workspace.assignment) return <EmptyState title={workspace.failed || "作业不存在"} />;

  return (
    <StudentAssignmentShell assignment={workspace.assignment} student={workspace.student} nextTask={workspace.nextTask} activeTab="ranking">
      {rankingFailed || !leaderboard ? (
        <EmptyState title={rankingFailed || "暂无排名数据"} />
      ) : (
        <>
          <div className="student-ranking-layout">
            <section className="student-ranking-table" aria-label="班内完成度排名">
              <div className="student-ranking-table__header" aria-hidden="true">
                <span>排名</span><span>学生</span><span>已通过</span><span>完成进度</span><span>提交次数</span><span>最近进度</span>
              </div>
              {rows.map((row, index) => row ? (
                <div className={`student-ranking-row${row.currentStudent ? " is-current" : ""}`} key={`${row.rank}-${row.displayName}-${index}`}>
                  <strong>{row.rank}</strong>
                  <span>{row.displayName}</span>
                  <span><b>{row.completedTasks}</b> / {row.totalTasks}</span>
                  <span className="student-ranking-progress"><progress value={row.completedTasks} max={Math.max(row.totalTasks, 1)} /><small>{Math.round((row.completedTasks / Math.max(row.totalTasks, 1)) * 100)}%</small></span>
                  <span>{row.attemptCount}</span>
                  <span>{row.completedTasks === row.totalTasks ? "全部通过" : row.lastSubmittedAt ? formatRelativeTime(row.lastSubmittedAt) : "尚未提交"}</span>
                </div>
              ) : <div className="student-ranking-gap" aria-hidden="true" key={`gap-${index}`}>...</div>)}
              <p><LockKeyhole size={14} aria-hidden="true" />仅显示必要排名信息，同学姓名已脱敏。</p>
            </section>

            <aside className="student-ranking-context">
              <section>
                <h2><Info size={18} aria-hidden="true" />排名说明</h2>
                <strong>按已通过题数排名，同完成度并列</strong>
                <p>排名用于了解作业完成进度，不按运行时间或提交速度区分先后。</p>
              </section>
              <section>
                <h2>我的题目进度</h2>
                <div className="student-ranking-task-list">
                  {workspace.assignment.tasks.map((task, index) => {
                    const state = assignmentTaskState(task, workspace.trajectory);
                    return (
                      <div key={task.problemId}>
                        <b>{index + 1}</b><strong>{task.title}</strong>
                        <span className={state?.passed ? "is-passed" : "is-pending"}>{state?.passed ? <CheckCircle2 size={16} aria-hidden="true" /> : <Clock3 size={16} aria-hidden="true" />}{state?.passed ? "已通过" : "未通过"}</span>
                      </div>
                    );
                  })}
                </div>
              </section>
            </aside>
          </div>
        </>
      )}
    </StudentAssignmentShell>
  );
}
