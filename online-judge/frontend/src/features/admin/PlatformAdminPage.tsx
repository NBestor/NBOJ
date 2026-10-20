import { FormEvent, useEffect, useState } from "react";
import { Building2, KeyRound, LogOut, RefreshCw, ShieldCheck } from "lucide-react";
import { api } from "../../shared/api/client";
import type { CreatedSchool, ProblemManage, SchoolSummary } from "../../shared/api/types";
import { useTranslation } from "../../shared/i18n";
import { Button } from "../../shared/ui/Button";
import { EmptyState } from "../../shared/ui/EmptyState";
import { Field, TextInput } from "../../shared/ui/Field";
import { Panel } from "../../shared/ui/Panel";
import { StatusPill } from "../../shared/ui/StatusPill";

export default function PlatformAdminPage() {
  const { t } = useTranslation();
  const [schools, setSchools] = useState<SchoolSummary[]>([]);
  const [reviews, setReviews] = useState<ProblemManage[]>([]);
  const [form, setForm] = useState({ schoolName: "", adminUsername: "", adminDisplayName: "", monthlyAiUnits: "0" });
  const [quotaDrafts, setQuotaDrafts] = useState<Record<string, string>>({});
  const [replacementDrafts, setReplacementDrafts] = useState<Record<string, { username: string; displayName: string }>>({});
  const [secret, setSecret] = useState<CreatedSchool | { temporaryPassword?: string | null } | null>(null);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  async function load() {
    setBusy(true);
    try { const [schoolRows, reviewRows] = await Promise.all([api.platformSchools(), api.platformProblemReviews()]); setSchools(schoolRows); setReviews(reviewRows); }
    catch (error) { setNotice(message(error, t("portals.loadFailed"))); }
    finally { setBusy(false); }
  }
  useEffect(() => { void load(); }, []);

  async function create(event: FormEvent) {
    event.preventDefault(); setBusy(true); setNotice(""); setSecret(null);
    try {
      const created = await api.createSchool({ ...form, monthlyAiUnits: Number(form.monthlyAiUnits) });
      setSecret(created); setForm({ schoolName: "", adminUsername: "", adminDisplayName: "", monthlyAiUnits: "0" });
      setNotice(t("portals.platform.created")); await load();
    } catch (error) { setNotice(message(error, t("portals.actionFailed"))); }
    finally { setBusy(false); }
  }
  async function run(action: () => Promise<unknown>) { setBusy(true); setNotice(""); try { await action(); await load(); } catch (error) { setNotice(message(error, t("portals.actionFailed"))); } finally { setBusy(false); } }
  async function resetPassword(id: string) { setBusy(true); setSecret(null); try { setSecret(await api.resetSchoolAdminPassword(id)); } catch (error) { setNotice(message(error, t("portals.actionFailed"))); } finally { setBusy(false); } }
  async function replaceAdmin(id: string) {
    const draft = replacementDrafts[id];
    if (!draft?.username.trim() || !draft?.displayName.trim()) return;
    setBusy(true); setSecret(null); setNotice("");
    try { setSecret(await api.replaceSchoolAdmin(id, draft)); setReplacementDrafts({ ...replacementDrafts, [id]: { username: "", displayName: "" } }); await load(); }
    catch (error) { setNotice(message(error, t("portals.actionFailed"))); }
    finally { setBusy(false); }
  }

  return <AdminLayout title={t("portals.platform.workspaceTitle")} description={t("portals.platform.workspaceDescription")} onRefresh={load} busy={busy}>
    {notice && <div className="alert alert--success">{notice}</div>}
    {secret && <div className="alert alert--warning"><strong>{t("portals.oneTimeSecret")}</strong>
      {secret.temporaryPassword && <p>{t("portals.temporaryPassword")}: <code>{secret.temporaryPassword}</code></p>}
      {"schoolRegistrationCode" in secret && secret.schoolRegistrationCode && <p>{t("portals.schoolCode")}: <code>{secret.schoolRegistrationCode}</code></p>}
    </div>}
    <div className="management-step-list">
      <Panel title={t("portals.platform.createSchool")} eyebrow={t("portals.platform.schoolGovernance")}>
        <form className="form-grid" onSubmit={create}>
          <Field label={t("portals.fields.schoolName")}><TextInput value={form.schoolName} onChange={e => setForm({ ...form, schoolName: e.target.value })} /></Field>
          <Field label={t("portals.fields.adminUsername")}><TextInput value={form.adminUsername} onChange={e => setForm({ ...form, adminUsername: e.target.value })} /></Field>
          <Field label={t("portals.fields.adminDisplayName")}><TextInput value={form.adminDisplayName} onChange={e => setForm({ ...form, adminDisplayName: e.target.value })} /></Field>
          <Field label={t("portals.fields.monthlyQuota")}><TextInput type="number" min="0" value={form.monthlyAiUnits} onChange={e => setForm({ ...form, monthlyAiUnits: e.target.value })} /></Field>
          <Button type="submit" variant="primary" icon={<Building2 size={17} />} disabled={busy}>{t("portals.actions.create")}</Button>
        </form>
      </Panel>
      <Panel title={t("portals.platform.schools")} eyebrow={t("portals.platform.schoolSummary")}>
        {schools.length ? schools.map(school => <article className="management-step" key={school.id}><div className="management-step__body">
          <div className="management-step__head"><h3>{school.name}</h3><p>{t("portals.quotaLine").replace("{{total}}", String(school.monthlyAiUnits)).replace("{{allocated}}", String(school.allocatedAiUnits)).replace("{{used}}", String(school.usedAiUnits))}</p></div>
          <div className="actions"><StatusPill tone={school.status === "ACTIVE" ? "success" : "warning"}>{school.status}</StatusPill>
            <Button type="button" variant="secondary" icon={<KeyRound size={16} />} onClick={() => void resetPassword(school.id)} disabled={busy}>{t("portals.actions.resetPassword")}</Button>
            <Button type="button" variant="secondary" onClick={() => void run(() => school.status === "ACTIVE" ? api.suspendSchool(school.id) : api.restoreSchool(school.id))} disabled={busy}>{t(school.status === "ACTIVE" ? "portals.actions.suspend" : "portals.actions.restore")}</Button>
          </div>
          <div className="form-grid"><Field label={t("portals.fields.monthlyQuota")}><TextInput type="number" min="0" value={quotaDrafts[school.id] ?? String(school.monthlyAiUnits)} onChange={e => setQuotaDrafts({ ...quotaDrafts, [school.id]: e.target.value })} /></Field>
            <Button type="button" variant="secondary" onClick={() => void run(() => api.setSchoolQuota(school.id, Number(quotaDrafts[school.id] ?? school.monthlyAiUnits)))} disabled={busy}>{t("portals.actions.saveQuota")}</Button></div>
          <div className="form-grid"><Field label={t("portals.fields.replacementUsername")}><TextInput value={replacementDrafts[school.id]?.username ?? ""} onChange={e => setReplacementDrafts({ ...replacementDrafts, [school.id]: { username: e.target.value, displayName: replacementDrafts[school.id]?.displayName ?? "" } })} /></Field>
            <Field label={t("portals.fields.replacementDisplayName")}><TextInput value={replacementDrafts[school.id]?.displayName ?? ""} onChange={e => setReplacementDrafts({ ...replacementDrafts, [school.id]: { username: replacementDrafts[school.id]?.username ?? "", displayName: e.target.value } })} /></Field>
            <Button type="button" variant="secondary" onClick={() => void replaceAdmin(school.id)} disabled={busy}>{t("portals.actions.replaceAdmin")}</Button></div>
        </div></article>) : <EmptyState title={t("portals.platform.noSchools")} />}
      </Panel>
      <Panel title={t("portals.platform.problemReviews")} eyebrow={t("portals.platform.contentGovernance")}>
        {reviews.length ? reviews.map(problem => <article className="management-step" key={problem.id}><div className="management-step__body">
          <div className="management-step__head"><h3>{problem.title}</h3><p>v{problem.versionNo} · {problem.description}</p></div>
          <div className="actions"><Button type="button" variant="primary" icon={<ShieldCheck size={16} />} onClick={() => void run(() => api.approvePlatformProblemReview(problem.id))}>{t("portals.actions.approve")}</Button>
            <Button type="button" variant="secondary" onClick={() => void run(async () => { const shared = await api.approvePlatformProblemReview(problem.id); await api.publishPlatformProblemPublic(shared.id); })}>{t("portals.actions.publishPublic")}</Button>
            <Button type="button" variant="secondary" onClick={() => void run(() => api.rejectPlatformProblemReview(problem.id, ""))}>{t("portals.actions.reject")}</Button></div>
        </div></article>) : <EmptyState title={t("portals.platform.noReviews")} />}
      </Panel>
    </div>
  </AdminLayout>;
}

export function AdminLayout({ title, description, onRefresh, busy, children }: { title: string; description: string; onRefresh: () => Promise<void>; busy: boolean; children: React.ReactNode }) {
  const { t } = useTranslation();
  return <div className="teacher-page teacher-workflow admin-workspace"><section className="teacher-workflow-header"><div><p className="eyebrow"><ShieldCheck size={16} /> {t("portals.secureWorkspace")}</p><h1>{title}</h1><p>{description}</p></div><div className="actions"><Button type="button" variant="secondary" icon={<RefreshCw size={16} />} onClick={() => void onRefresh()} disabled={busy}>{t("portals.actions.refresh")}</Button><Button type="button" variant="ghost" icon={<LogOut size={16} />} onClick={() => void api.accountLogout().finally(() => window.location.reload())}>{t("common.logout")}</Button></div></section>{children}</div>;
}
function message(error: unknown, fallback: string) { return error instanceof Error ? error.message : fallback; }
