import { useEffect, useState } from "react";
import { CheckCircle2, KeyRound, RotateCcw, XCircle } from "lucide-react";
import { api } from "../../shared/api/client";
import type { SchoolAdminOverview, SchoolTeachingAssignment, SchoolTeachingClass, SchoolTeachingStudent, SchoolTeachingSubmission, TeacherAccount } from "../../shared/api/types";
import { useTranslation } from "../../shared/i18n";
import { Button } from "../../shared/ui/Button";
import { EmptyState } from "../../shared/ui/EmptyState";
import { Field, TextInput } from "../../shared/ui/Field";
import { Panel } from "../../shared/ui/Panel";
import { StatusPill } from "../../shared/ui/StatusPill";
import { AdminLayout } from "./PlatformAdminPage";

export default function SchoolAdminPage() {
  const { t } = useTranslation();
  const [overview, setOverview] = useState<SchoolAdminOverview | null>(null);
  const [applications, setApplications] = useState<TeacherAccount[]>([]);
  const [classes, setClasses] = useState<SchoolTeachingClass[]>([]);
  const [quotaDrafts, setQuotaDrafts] = useState<Record<string, string>>({});
  const [teachingDetails, setTeachingDetails] = useState<{ selectedClass: SchoolTeachingClass; students: SchoolTeachingStudent[]; assignments: SchoolTeachingAssignment[] } | null>(null);
  const [submissionDetails, setSubmissionDetails] = useState<{ assignment: SchoolTeachingAssignment; submissions: SchoolTeachingSubmission[] } | null>(null);
  const [notice, setNotice] = useState("");
  const [secret, setSecret] = useState("");
  const [busy, setBusy] = useState(false);

  async function load() { setBusy(true); try { const [summary, pending, classRows] = await Promise.all([api.schoolAdminOverview(), api.schoolTeacherApplications(), api.schoolTeachingClasses()]); setOverview(summary); setApplications(pending); setClasses(classRows); } catch (error) { setNotice(message(error, t("portals.loadFailed"))); } finally { setBusy(false); } }
  useEffect(() => { void load(); }, []);
  async function run(action: () => Promise<unknown>) { setBusy(true); setNotice(""); try { await action(); await load(); } catch (error) { setNotice(message(error, t("portals.actionFailed"))); } finally { setBusy(false); } }
  async function reveal(action: () => Promise<{ temporaryPassword?: string; schoolRegistrationCode?: string }>) { setBusy(true); setSecret(""); try { const result = await action(); setSecret(result.temporaryPassword || result.schoolRegistrationCode || ""); } catch (error) { setNotice(message(error, t("portals.actionFailed"))); } finally { setBusy(false); } }
  async function inspectClass(row: SchoolTeachingClass) { setBusy(true); setNotice(""); try { const [students, assignments] = await Promise.all([api.schoolTeachingStudents(row.id), api.schoolTeachingAssignments(row.id)]); setTeachingDetails({ selectedClass: row, students, assignments }); setSubmissionDetails(null); } catch (error) { setNotice(message(error, t("portals.loadFailed"))); } finally { setBusy(false); } }
  async function inspectAssignment(row: SchoolTeachingAssignment) { setBusy(true); setNotice(""); try { setSubmissionDetails({ assignment: row, submissions: await api.schoolTeachingSubmissions(row.id) }); } catch (error) { setNotice(message(error, t("portals.loadFailed"))); } finally { setBusy(false); } }

  return <AdminLayout title={overview?.schoolName || t("portals.school.workspaceTitle")} description={t("portals.school.workspaceDescription")} onRefresh={load} busy={busy}>
    {notice && <div className="alert alert--success">{notice}</div>}
    {secret && <div className="alert alert--warning"><strong>{t("portals.oneTimeSecret")}</strong> <code>{secret}</code></div>}
    <div className="management-step-list">
      <Panel
        className="school-admin-quota-panel"
        title={t("portals.school.quotaPool")}
        eyebrow={t("portals.school.overview")}
        action={<Button type="button" variant="secondary" icon={<RotateCcw size={16} />} onClick={() => void reveal(api.rotateSchoolRegistrationCode)} disabled={busy}>{t("portals.actions.rotateCode")}</Button>}
      >
        {overview ? <dl className="school-admin-quota-grid" aria-label={t("portals.school.quotaPool")}>
          <div><dt>{t("portals.fields.totalQuota")}</dt><dd>{overview.quota.totalUnits}</dd></div>
          <div><dt>{t("portals.fields.allocatedQuota")}</dt><dd>{overview.quota.allocatedUnits}</dd></div>
          <div><dt>{t("portals.fields.usedQuota")}</dt><dd>{overview.quota.usedUnits}</dd></div>
          <div><dt>{t("portals.fields.availableQuota")}</dt><dd>{overview.quota.availableUnits}</dd></div>
        </dl> : <EmptyState title={t("portals.checking")} />}
      </Panel>
      <Panel title={t("portals.school.applications")} eyebrow={`${overview?.pendingApplications ?? 0} ${t("portals.school.pending")}`}>
        {applications.length ? applications.map(account => <article className="management-step school-admin-inline-row" key={account.id}><div className="management-step__body"><div className="management-step__head"><h3>{account.displayName} · {account.username}</h3></div><div className="actions"><Button type="button" variant="primary" icon={<CheckCircle2 size={16} />} onClick={() => void run(() => api.schoolApproveTeacher(account.id))}>{t("portals.actions.approve")}</Button><Button type="button" variant="secondary" icon={<XCircle size={16} />} onClick={() => void run(() => api.schoolRejectTeacher(account.id, ""))}>{t("portals.actions.reject")}</Button></div></div></article>) : <EmptyState title={t("portals.school.noApplications")} />}
      </Panel>
      <Panel title={t("portals.school.teachers")} eyebrow={t("portals.school.quotaAllocation")}>
        {overview?.teachers.length ? overview.teachers.map(teacher => {
          const usage = overview.teacherQuotas[teacher.id];
          const allocated = (usage?.baseUnits ?? 0) + (usage?.additionalUnits ?? 0);
          return <article className="management-step school-admin-teacher-card" key={teacher.id}><div className="management-step__body">
            <div className="school-admin-teacher-card__summary"><div className="management-step__head"><h3>{teacher.displayName} · {teacher.username}</h3><StatusPill tone={teacher.status === "ACTIVE" ? "success" : "warning"}>{teacher.status}</StatusPill></div><p>{t("portals.school.teacherUsage").replace("{{allocated}}", String(allocated)).replace("{{used}}", String(usage?.usedUnits ?? 0)).replace("{{reserved}}", String(usage?.reservedUnits ?? 0))}</p></div>
            <div className="actions school-admin-teacher-card__actions"><Button type="button" variant="secondary" icon={<KeyRound size={16} />} onClick={() => void reveal(() => api.schoolResetTeacherPassword(teacher.id))}>{t("portals.actions.resetPassword")}</Button><Button type="button" variant="secondary" onClick={() => void run(() => teacher.status === "ACTIVE" ? api.schoolSuspendTeacher(teacher.id) : api.schoolRestoreTeacher(teacher.id))}>{t(teacher.status === "ACTIVE" ? "portals.actions.suspend" : "portals.actions.restore")}</Button></div>
            <div className="school-admin-teacher-card__quota"><Field label={t("portals.fields.teacherQuota")}><TextInput type="number" min="0" value={quotaDrafts[teacher.id] ?? String(allocated)} onChange={e => setQuotaDrafts({ ...quotaDrafts, [teacher.id]: e.target.value })} /></Field><Button type="button" variant="secondary" onClick={() => void run(() => api.schoolSetTeacherQuota(teacher.id, Number(quotaDrafts[teacher.id] ?? allocated)))}>{t("portals.actions.allocate")}</Button></div>
          </div></article>;
        }) : <EmptyState title={t("portals.school.noTeachers")} />}
      </Panel>
      <Panel title={t("portals.school.teachingData")} eyebrow={t("portals.school.readOnly")}>
        {classes.length ? classes.map(row => <article className="management-step school-admin-inline-row" key={row.id}><div className="management-step__body"><div className="management-step__head"><h3>{row.name}</h3><p>{row.teacherName} · {row.studentCount} {t("portals.school.students")} · {row.assignmentCount} {t("portals.school.assignmentsCount")}</p></div><div className="actions"><StatusPill tone="neutral">{t("portals.school.readOnly")}</StatusPill><Button type="button" variant="secondary" onClick={() => void inspectClass(row)}>{t("portals.actions.inspect")}</Button></div></div></article>) : <EmptyState title={t("portals.school.noTeachingData")} />}
        {teachingDetails && <section className="admin-readonly-detail"><h3>{teachingDetails.selectedClass.name}</h3><h4>{t("portals.school.studentsTitle")}</h4>{teachingDetails.students.length ? <ul>{teachingDetails.students.map(student => <li key={student.id}>{student.studentNo || "—"} · {student.displayName} · {student.status}</li>)}</ul> : <p>{t("portals.school.noStudents")}</p>}<h4>{t("portals.school.assignmentsTitle")}</h4>{teachingDetails.assignments.length ? teachingDetails.assignments.map(assignment => <article className="management-step school-admin-inline-row" key={assignment.id}><div className="management-step__body"><div className="management-step__head"><h3>{assignment.title}</h3><p>{assignment.status} · {assignment.targetMode}</p></div><div className="actions"><Button type="button" variant="secondary" onClick={() => void inspectAssignment(assignment)}>{t("portals.actions.viewSubmissions")}</Button><Button type="button" variant="secondary" onClick={() => window.open(`/api/school-admin/teaching/assignments/${assignment.id}/export`, "_blank", "noopener,noreferrer")}>{t("portals.actions.exportCsv")}</Button></div></div></article>) : <p>{t("portals.school.noAssignments")}</p>}</section>}
        {submissionDetails && <section className="admin-readonly-detail"><h3>{submissionDetails.assignment.title} · {t("portals.school.submissionsTitle")}</h3>{submissionDetails.submissions.length ? submissionDetails.submissions.map(submission => <details key={submission.id}><summary>#{submission.id} · {submission.verdict} · {submission.languageName} · {submission.submittedAt}</summary><pre><code>{submission.sourceCode}</code></pre>{submission.errorMessage && <p>{submission.errorMessage}</p>}</details>) : <p>{t("portals.school.noSubmissions")}</p>}</section>}
      </Panel>
    </div>
  </AdminLayout>;
}
function message(error: unknown, fallback: string) { return error instanceof Error ? error.message : fallback; }
