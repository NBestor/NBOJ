import { FormEvent, useEffect, useState } from "react";
import { ArrowLeft, LogIn } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { api } from "../../shared/api/client";
import { useTranslation } from "../../shared/i18n";
import { clearActiveStudent, saveActiveStudent } from "../../shared/storage";
import { Button, ButtonLink } from "../../shared/ui/Button";
import { Field, TextInput } from "../../shared/ui/Field";
import { Panel } from "../../shared/ui/Panel";

export default function StudentLoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [classCode, setClassCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [studentNo, setStudentNo] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api.studentSession().then(student => {
      saveActiveStudent(student);
      navigate("/student", { replace: true });
    }).catch(() => clearActiveStudent());
  }, [navigate]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!classCode.trim() || !displayName.trim() || !studentNo.trim()) {
      setError(t("studentLogin.validation"));
      return;
    }
    setBusy(true);
    setError("");
    try {
      const student = await api.loginStudent({ classCode: classCode.trim().toUpperCase(), displayName: displayName.trim(), studentNo: studentNo.trim() });
      saveActiveStudent(student);
      navigate("/student", { replace: true });
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : t("studentLogin.failed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack student-page student-login-page">
      <section className="student-home-command">
        <div><p className="eyebrow">{t("studentLogin.eyebrow")}</p><h1>{t("studentLogin.title")}</h1></div>
        <ButtonLink to="/student" variant="ghost" icon={<ArrowLeft size={17} />}>
          {t("studentLogin.back")}
        </ButtonLink>
      </section>
      {error && <div className="alert alert--error">{error}</div>}
      <Panel className="student-login-panel" title={t("studentLogin.panelTitle")} eyebrow={t("studentLogin.panelEyebrow")}>
        <form className="student-login-form" onSubmit={event => void submit(event)}>
          <div className="form-grid">
            <Field label={t("studentLogin.classCode")}>
              <TextInput value={classCode} onChange={event => setClassCode(event.target.value)} placeholder={t("studentLogin.classCodePlaceholder")} autoComplete="off" />
            </Field>
            <Field label={t("studentLogin.displayName")}>
              <TextInput value={displayName} onChange={event => setDisplayName(event.target.value)} placeholder={t("studentLogin.displayNamePlaceholder")} autoComplete="name" />
            </Field>
            <Field label={t("studentLogin.studentNo")}>
              <TextInput value={studentNo} onChange={event => setStudentNo(event.target.value)} placeholder={t("studentLogin.studentNoPlaceholder")} autoComplete="off" />
            </Field>
          </div>
          <p>{t("studentLogin.rosterHint")}</p>
          <Button type="submit" variant="primary" icon={<LogIn size={17} />} disabled={busy}>
            {busy ? t("studentLogin.loggingIn") : t("studentLogin.action")}
          </Button>
        </form>
      </Panel>
    </div>
  );
}
