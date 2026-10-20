import { useEffect, useMemo, useState, type ReactNode } from "react";
import { ArrowLeft, BarChart3, CalendarDays, ChevronDown, ClipboardList, FileCheck2, LayoutGrid } from "lucide-react";
import { Link } from "react-router-dom";
import { api } from "../../shared/api/client";
import type { Assignment, AssignmentTask, StudentProfile, StudentTrajectory } from "../../shared/api/types";
import { loadStudent, saveStudent } from "../../shared/storage";

export type StudentAssignmentTab = "assignment" | "tasks" | "ranking" | "submissions";

export function pickNextAssignmentTask(assignment: Assignment | null, trajectory: StudentTrajectory | null) {
  if (!assignment?.tasks.length) {
    return null;
  }
  const pending = trajectory?.tasks.find(task => !task.passed);
  if (pending) {
    return assignment.tasks.find(task => task.problemId === pending.problemId) || assignment.tasks[0];
  }
  return assignment.tasks[0];
}

export function useStudentAssignmentWorkspace(assignmentId: number) {
  const [student] = useState<StudentProfile | null>(() => loadStudent(assignmentId));
  const [assignment, setAssignment] = useState<Assignment | null>(null);
  const [trajectory, setTrajectory] = useState<StudentTrajectory | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    if (!student || !Number.isFinite(assignmentId)) {
      setLoading(false);
      return () => {
        ignore = true;
      };
    }
    setLoading(true);
    setFailed(null);
    Promise.all([
      api.studentAssignments(student.id),
      api.studentTrajectory(assignmentId, student.id)
    ])
      .then(([assignments, trajectoryResult]) => {
        if (ignore) return;
        const matched = assignments.find(item => item.id === assignmentId) || null;
        setAssignment(matched);
        setTrajectory(trajectoryResult);
        if (matched) saveStudent(matched.id, student);
      })
      .catch(error => {
        if (!ignore) setFailed(error instanceof Error ? error.message : "作业数据加载失败");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [assignmentId, student]);

  const nextTask = useMemo(() => pickNextAssignmentTask(assignment, trajectory), [assignment, trajectory]);
  return { student, assignment, trajectory, nextTask, loading, failed };
}

export function latestTaskSubmission(task?: StudentTrajectory["tasks"][number] | null) {
  return task?.submissions?.[0] || null;
}

export function assignmentTaskState(task: AssignmentTask, trajectory: StudentTrajectory | null) {
  return trajectory?.tasks.find(item => item.problemId === task.problemId) || null;
}

function formatAssignmentDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}

export function formatRelativeTime(value?: string | null) {
  if (!value) return "暂无";
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return "暂无";
  const minutes = Math.max(0, Math.round((Date.now() - timestamp) / 60000));
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  return `${Math.round(hours / 24)} 天前`;
}

interface StudentAssignmentShellProps {
  assignment: Assignment;
  student: StudentProfile;
  nextTask: AssignmentTask | null;
  activeTab: StudentAssignmentTab;
  children: ReactNode;
}

interface StudentAssignmentHeaderProps {
  assignment: Assignment;
  student: StudentProfile;
  className?: string;
}

interface StudentAssignmentNavigationProps {
  assignmentId: number;
  taskPath: string;
  activeTab: StudentAssignmentTab;
}

export function StudentAssignmentHeader({ assignment, student, className = "" }: StudentAssignmentHeaderProps) {
  const studentInitials = student.displayName.trim().slice(0, 2).toUpperCase() || "ST";
  return (
    <header className={`student-assignment-insights-header${className ? ` ${className}` : ""}`}>
      <Link className="student-assignment-back" to="/student" aria-label="返回学生端">
        <ArrowLeft size={21} aria-hidden="true" />
      </Link>
      <div className="student-assignment-insights-title">
        <h1>{assignment.title}</h1>
      </div>
      <div className="student-assignment-insights-context">
        <span className="student-assignment-insights-meta">{assignment.className || student.className || "当前班级"}</span>
        <span className="student-assignment-insights-meta"><CalendarDays size={16} aria-hidden="true" />{assignment.endsAt ? formatAssignmentDate(assignment.endsAt) : "未设置截止时间"}</span>
      </div>
      <div className="student-assignment-profile" aria-label={`当前学生 ${student.displayName}`}>
        <span>{studentInitials}</span>
        <strong>{student.displayName}</strong>
        <ChevronDown size={16} aria-hidden="true" />
      </div>
    </header>
  );
}

export function StudentAssignmentNavigation({ assignmentId, taskPath, activeTab }: StudentAssignmentNavigationProps) {
  const basePath = `/student/assignments/${assignmentId}`;
  const navItems = [
    { key: "assignment", label: "概览", to: basePath, icon: LayoutGrid },
    { key: "tasks", label: "题目", to: taskPath, icon: ClipboardList },
    { key: "submissions", label: "提交", to: `${basePath}/submissions`, icon: FileCheck2 },
    { key: "ranking", label: "排名", to: `${basePath}/ranking`, icon: BarChart3 }
  ] as const;

  return (
    <nav className="student-assignment-side-nav" aria-label="作业页面导航" data-student-assignment-navigation>
      {navItems.map(item => {
        const Icon = item.icon;
        const isActive = activeTab === item.key;
        return (
          <Link className={isActive ? "is-active" : ""} aria-current={isActive ? "page" : undefined} to={item.to} key={item.key}>
            <Icon size={22} aria-hidden="true" />
            <span>{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}

export function StudentAssignmentShell({ assignment, student, nextTask, activeTab, children }: StudentAssignmentShellProps) {
  const basePath = `/student/assignments/${assignment.id}`;
  const nextTaskPath = nextTask
    ? `${basePath}/problems/${nextTask.problemId}?studentProfileId=${student.id}`
    : basePath;
  return (
    <div className="student-assignment-insights-page">
      <StudentAssignmentHeader assignment={assignment} student={student} />
      <div className="student-assignment-workspace">
        <StudentAssignmentNavigation assignmentId={assignment.id} taskPath={nextTaskPath} activeTab={activeTab} />
        <main className="student-assignment-workspace-content">{children}</main>
      </div>
    </div>
  );
}
