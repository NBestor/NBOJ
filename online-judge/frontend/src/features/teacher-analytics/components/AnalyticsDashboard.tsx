import { useMemo, useState } from "react";
import { ArrowRight, CheckCircle2, CircleDot, Repeat2 } from "lucide-react";
import { Link } from "react-router-dom";
import type { AssignmentOverview, SubmissionGrowthSummary, TeacherProblemLearningProof } from "../../../shared/api/types";
import type { AnalyticsSnapshot, InsightBucket } from "../model";
import { AnalyticsSubmissionProgress } from "./AnalyticsSubmissionProgress";

type Props = {
  snapshot: AnalyticsSnapshot;
  t: (key: string, params?: Record<string, string | number>) => string;
  learningProof?: TeacherProblemLearningProof | null;
};

type ProblemStudent = NonNullable<NonNullable<AssignmentOverview["problemSummaries"]>[number]["students"]>[number];
type StudentGroup = "affected" | "repeated" | "resolved";
type LearningStage = "failed" | "repaired" | "explained" | "verified";

export function AnalyticsDashboard({ snapshot, t, learningProof }: Props) {
  return (
    <div className="teacher-analytics-dashboard teacher-analytics-dashboard--focused">
      {snapshot.scope.type === "class" ? <ClassAssignments snapshot={snapshot} t={t} /> : null}
      {snapshot.scope.type === "assignment" ? <AssignmentProblems snapshot={snapshot} t={t} /> : null}
      {snapshot.scope.type === "problem" ? <ProblemEvidence snapshot={snapshot} t={t} learningProof={learningProof} /> : null}
    </div>
  );
}

function ClassAssignments({ snapshot, t }: Props) {
  return (
    <section className="teacher-analytics-focus-panel">
      <SectionTitle title={t("teacherAnalytics.focus.assignmentList")} count={snapshot.assignmentRows.length} />
      <div className="teacher-analytics-focus-list">
        {snapshot.assignmentRows.map(row => (
          <Link className="teacher-analytics-focus-row" to={row.href} key={row.id}>
            <div>
              <strong>{row.title}</strong>
              <small>{row.status} · {t("teacherAnalytics.focus.problemCount", { count: row.problemCount })}</small>
            </div>
            <AnalyticsSubmissionProgress
              label={t("teacherAnalytics.focus.submitted")}
              submitted={row.submittedStudentCount}
              total={row.participantCount}
            />
            <div className="teacher-analytics-row-metrics teacher-analytics-row-metrics--assignment">
              <Metric label={t("teacherAnalytics.focus.completedRequired")} value={row.completedRequiredStudentCount} />
            </div>
            <ArrowRight size={18} aria-hidden="true" />
          </Link>
        ))}
      </div>
    </section>
  );
}

function AssignmentProblems({ snapshot, t }: Props) {
  return (
    <section className="teacher-analytics-focus-panel">
      <SectionTitle title={t("teacherAnalytics.focus.problemList")} count={snapshot.problemRows.length} />
      <div className="teacher-analytics-focus-list">
        {snapshot.problemRows.map(row => (
          <Link className="teacher-analytics-focus-row teacher-analytics-focus-row--problem" to={row.href} key={row.id}>
            <div>
              <strong>{row.title}</strong>
            </div>
            <AnalyticsSubmissionProgress
              label={t("teacherAnalytics.focus.submitted")}
              submitted={row.submittedStudentCount}
              total={row.participantCount}
            />
            <div className="teacher-analytics-row-metrics teacher-analytics-row-metrics--problem">
              <Metric label={t("teacherAnalytics.focus.firstPass")} value={row.firstPassStudentCount} />
              <Metric label={t("teacherAnalytics.focus.eventualPass")} value={row.passedStudentCount} />
              <Metric label={t("teacherAnalytics.focus.unsubmitted")} value={row.unsubmittedStudentCount} />
            </div>
            <ArrowRight size={18} aria-hidden="true" />
          </Link>
        ))}
      </div>
    </section>
  );
}

function ProblemEvidence({ snapshot, t, learningProof }: Props) {
  const problem = snapshot.overview?.problemSummaries?.find(item => item.problemId === snapshot.scope.problemId);
  const issues = useMemo(
    () => [...(snapshot.insightBuckets.mistakePoint || [])]
      .sort((left, right) => (right.affectedStudentCount || 0) - (left.affectedStudentCount || 0) || (right.repeatedStudentCount || 0) - (left.repeatedStudentCount || 0)),
    [snapshot.insightBuckets.mistakePoint]
  );
  const [selectedIssueId, setSelectedIssueId] = useState<string | null>(null);
  const [studentGroup, setStudentGroup] = useState<StudentGroup>("affected");
  const [learningStage, setLearningStage] = useState<LearningStage | null>(null);
  const selectedIssue = selectedIssueId ? issues.find(item => item.id === selectedIssueId) || null : null;
  const studentIds = learningStage
    ? studentIdsForLearningStage(learningProof, learningStage)
    : selectedIssue ? idsForGroup(selectedIssue, studentGroup) : [];
  const rosterStudents = problem?.students || [];
  const proofByStudent = new Map((learningProof?.students || []).map(item => [item.studentProfileId, item]));
  const students = rosterStudents
    .filter(student => !selectedIssue && !learningStage || studentIds.includes(student.studentProfileId))
    .sort((left, right) => studentAttentionOrder(left) - studentAttentionOrder(right));
  const rosterCount = problem?.classStudentCount || rosterStudents.length;
  const submittedCount = problem?.submittedStudentCount || rosterStudents.filter(student => student.attemptCount > 0).length;
  const unsubmittedCount = Math.max(0, rosterCount - submittedCount);
  const hasFilter = Boolean(selectedIssue || learningStage);
  const submittedStudents = students.filter(student => student.attemptCount > 0);
  const unsubmittedStudents = students.filter(student => student.attemptCount <= 0);

  function selectIssueGroup(id: string, group: StudentGroup) {
    if (selectedIssueId === id && studentGroup === group) {
      setSelectedIssueId(null);
    } else {
      setSelectedIssueId(id);
      setStudentGroup(group);
    }
    setLearningStage(null);
  }

  function selectStage(stage: LearningStage) {
    setLearningStage(current => current === stage ? null : stage);
    setSelectedIssueId(null);
  }

  function clearFilters() {
    setLearningStage(null);
    setSelectedIssueId(null);
  }

  return (
    <>
      {learningProof ? (
        <section className="teacher-learning-flow" aria-label={t("learningProof.teacher.aria")}>
          <button
            type="button"
            className={!selectedIssue && !learningStage ? "teacher-learning-flow__all is-active" : "teacher-learning-flow__all"}
            aria-pressed={!selectedIssue && !learningStage}
            onClick={clearFilters}
          >
            <span>{t("learningProof.teacher.allStudents")}</span>
            <strong>{rosterCount}</strong>
          </button>
          <nav className="teacher-learning-stagebar">
            <LearningStageButton label={t("learningProof.teacher.failed")} value={learningProof.failedStudentCount} active={learningStage === "failed"} onClick={() => selectStage("failed")} />
            <LearningStageButton label={t("learningProof.teacher.repaired")} value={learningProof.repairedStudentCount} active={learningStage === "repaired"} onClick={() => selectStage("repaired")} />
            <LearningStageButton label={t("learningProof.teacher.explained")} value={learningProof.explainedStudentCount} active={learningStage === "explained"} onClick={() => selectStage("explained")} />
            <LearningStageButton label={t("learningProof.teacher.verified")} value={learningProof.independentVerifiedStudentCount} active={learningStage === "verified"} onClick={() => selectStage("verified")} />
          </nav>
        </section>
      ) : null}
      <div className={issues.length
        ? "teacher-problem-evidence-layout"
        : "teacher-problem-evidence-layout teacher-problem-evidence-layout--students-only"}>
      {issues.length ? <aside className="teacher-analytics-focus-panel teacher-problem-issue-sidebar">
        <SectionTitle title={t("teacherAnalytics.focus.issueOverview")} count={issues.length} />
        <div className="teacher-issue-overview-list">
            {issues.map(issue => (
              <article className={selectedIssue?.id === issue.id ? "teacher-issue-overview is-selected" : "teacher-issue-overview"} key={issue.id}>
                <button
                  type="button"
                  className="teacher-issue-overview__title"
                  onClick={() => selectIssueGroup(issue.id, "affected")}
                  aria-expanded={selectedIssue?.id === issue.id}
                >
                  <span>{issue.label}</span>
                </button>
                <div className="teacher-issue-counts" aria-label={t("teacherAnalytics.focus.issueCountsAria")}>
                  <IssueCount
                    icon={CircleDot}
                    label={t("teacherAnalytics.focus.affected")}
                    value={issue.affectedStudentCount || 0}
                    active={selectedIssue?.id === issue.id && studentGroup === "affected"}
                    onClick={() => selectIssueGroup(issue.id, "affected")}
                  />
                  <IssueCount
                    icon={Repeat2}
                    label={t("teacherAnalytics.focus.repeated")}
                    value={issue.repeatedStudentCount || 0}
                    active={selectedIssue?.id === issue.id && studentGroup === "repeated"}
                    onClick={() => selectIssueGroup(issue.id, "repeated")}
                  />
                  <IssueCount
                    icon={CheckCircle2}
                    label={t("teacherAnalytics.focus.resolved")}
                    value={issue.resolvedStudentCount || 0}
                    active={selectedIssue?.id === issue.id && studentGroup === "resolved"}
                    onClick={() => selectIssueGroup(issue.id, "resolved")}
                  />
                </div>
              </article>
            ))}
          </div>
      </aside> : null}

      <section className="teacher-analytics-focus-panel teacher-problem-students">
        <SectionTitle
          title={selectedIssue
            ? t("teacherAnalytics.focus.filteredStudents", { issue: selectedIssue.label })
            : learningStage ? t(`learningProof.teacher.${learningStage}Students`) : t("teacherAnalytics.focus.studentList")}
          count={hasFilter ? students.length : undefined}
          meta={selectedIssue
            ? t(`teacherAnalytics.focus.${studentGroup}`)
            : t("teacherAnalytics.focus.rosterMeta", { submitted: submittedCount, unsubmitted: unsubmittedCount })}
        />
        <div className="teacher-student-growth-list">
          {students.length ? hasFilter ? students.map(student => (
              <StudentGrowthRow student={student} proof={proofByStudent.get(student.studentProfileId)} snapshot={snapshot} t={t} key={student.studentProfileId} />
            )) : (
              <>
                {submittedStudents.length ? <StudentListGroupLabel label={t("teacherAnalytics.focus.submittedGroup")} count={submittedStudents.length} /> : null}
                {submittedStudents.map(student => (
                  <StudentGrowthRow student={student} proof={proofByStudent.get(student.studentProfileId)} snapshot={snapshot} t={t} key={student.studentProfileId} />
                ))}
                {unsubmittedStudents.length ? <StudentListGroupLabel label={t("teacherAnalytics.focus.unsubmittedGroup")} count={unsubmittedStudents.length} /> : null}
                {unsubmittedStudents.map(student => (
                  <StudentGrowthRow student={student} proof={proofByStudent.get(student.studentProfileId)} snapshot={snapshot} t={t} key={student.studentProfileId} />
                ))}
              </>
            ) : (
            <div className="teacher-analytics-empty-copy">
              <span>{rosterCount ? t("teacherAnalytics.empty.noMatchingStudents") : t("teacherAnalytics.empty.noRosterStudents")}</span>
              {hasFilter ? <button type="button" onClick={clearFilters}>{t("teacherAnalytics.actions.showRoster")}</button> : null}
            </div>
          )}
        </div>
      </section>
      </div>
    </>
  );
}

function StudentGrowthRow({ student, proof, snapshot, t }: { student: ProblemStudent; proof?: TeacherProblemLearningProof["students"][number]; snapshot: AnalyticsSnapshot; t: Props["t"] }) {
  const href = `/teacher/classes/${snapshot.scope.classId}/assignments/${snapshot.scope.assignmentId}/problems/${snapshot.scope.problemId}/students/${student.studentProfileId}`;
  const growth = student.latestGrowthSummary;
  const submitted = student.attemptCount > 0 && Boolean(student.latestSubmissionId);
  const issueSignals = (growth?.issueSignals || [])
    .filter(item => ["PERSISTED", "NEW", "RECURRED"].includes(String(item.changeStatus || "").toUpperCase()))
    .slice(0, 2);
  const stateLabel = submitted
    ? proof ? learningProofLabel(proof, t) : growthStateLabel(growth, t)
    : t("teacherAnalytics.focus.notSubmitted");
  const stateDetail = submitted
    ? issueSignals.length
      ? issueSignals.map(item => item.title).filter(Boolean).join(" · ")
      : proof?.repaired
        ? t("teacherAnalytics.focus.repairedAfterFailure")
        : student.latestVerdict === "ACCEPTED"
          ? t("teacherAnalytics.focus.accepted")
          : t("teacherAnalytics.focus.noCurrentIssue")
    : "";
  const tone = !submitted
    ? "idle"
    : student.latestVerdict === "ACCEPTED"
      ? "success"
      : student.needsAttention ? "warning" : "active";
  const content = (
    <>
      <div className="teacher-student-growth-row__identity">
        <strong>{student.displayName}</strong>
        <small>{student.studentNo || t("teacherAnalytics.defaultLabels.studentWithId", { id: student.studentProfileId })}</small>
      </div>
      <div className={`teacher-student-growth-row__state is-${tone}`}>
        <span><i aria-hidden="true" />{stateLabel}</span>
        {stateDetail ? <small>{stateDetail}</small> : null}
      </div>
      {submitted ? <>
        <Metric label={t("teacherAnalytics.focus.rawSubmissions")} value={student.attemptCount} />
        <Metric label={t("teacherAnalytics.focus.effectiveEdits")} value={student.effectiveAttemptCount || 0} />
      </> : null}
      {submitted ? <ArrowRight size={18} aria-hidden="true" /> : null}
    </>
  );
  return submitted
    ? <Link className="teacher-student-growth-row is-clickable" to={href}>{content}</Link>
    : <div className="teacher-student-growth-row is-unsubmitted">{content}</div>;
}

function StudentListGroupLabel({ label, count }: { label: string; count: number }) {
  return <div className="teacher-student-growth-list__group"><span>{label}</span><strong>{count}</strong></div>;
}

function SectionTitle({ title, count, meta }: { title: string; count?: number; meta?: string }) {
  return (
    <header className="teacher-analytics-focus-title">
      <div>
        <h2>{title}</h2>
        {meta ? <small>{meta}</small> : null}
      </div>
      {typeof count === "number" ? <span>{count}</span> : null}
    </header>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return <div className="teacher-analytics-inline-metric"><span>{label}</span><strong>{value}</strong></div>;
}

function studentAttentionOrder(student: ProblemStudent) {
  if (student.attemptCount <= 0) return 3;
  if (student.needsAttention || student.latestVerdict !== "ACCEPTED") return 0;
  return 1;
}

function IssueCount({ icon: Icon, label, value, active, onClick }: { icon: typeof CircleDot; label: string; value: number; active: boolean; onClick: () => void }) {
  return (
    <button type="button" className={active ? "is-active" : ""} onClick={onClick} disabled={value === 0}>
      <Icon size={16} aria-hidden="true" />
      <span>{label}</span>
      <strong>{value}</strong>
    </button>
  );
}

function LearningStageButton({ label, value, active, onClick }: { label: string; value: number; active: boolean; onClick: () => void }) {
  return (
    <button type="button" className={active ? "is-active" : ""} aria-pressed={active} onClick={onClick} disabled={value === 0}>
      <span>{label}</span><strong>{value}</strong>
    </button>
  );
}

function studentIdsForLearningStage(proof: TeacherProblemLearningProof | null | undefined, stage: LearningStage) {
  return (proof?.students || []).filter(item => {
    if (stage === "failed") return item.hadFailure;
    if (stage === "repaired") return item.repaired;
    if (stage === "explained") return item.explained;
    return item.independentVerified;
  }).map(item => item.studentProfileId);
}

function idsForGroup(issue: InsightBucket, group: StudentGroup) {
  if (group === "repeated") return issue.repeatedStudentIds || [];
  if (group === "resolved") return issue.resolvedStudentIds || [];
  return issue.affectedStudentIds || [];
}

function growthStateLabel(growth: SubmissionGrowthSummary | null | undefined, t: Props["t"]) {
  const key = String(growth?.growthState || "UNCOMPARABLE").toLowerCase();
  return t(`growthDashboard.state.${key}`);
}

function learningProofLabel(proof: TeacherProblemLearningProof["students"][number], t: Props["t"]) {
  if (proof.independentVerified) return t("learningProof.teacher.verified");
  if (proof.explained) return t("learningProof.teacher.explained");
  if (proof.repaired) return t("learningProof.teacher.repaired");
  if (proof.hadFailure) return t("learningProof.teacher.failed");
  return t("learningProof.repair.notObserved");
}
