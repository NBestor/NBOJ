import { useState, type FormEvent } from "react";
import { Check, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import type { LearningProof } from "../../shared/api/types";
import { useTranslation } from "../../shared/i18n";
import "./LearningProofPanel.css";

type Props = {
  proof: LearningProof;
  editable?: boolean;
  busy?: boolean;
  onCreateReflection?: () => Promise<void> | void;
  onAnswerReflection?: (answer: string) => Promise<void> | void;
};

export function LearningProofPanel({
  proof,
  editable = false,
  busy = false,
  onCreateReflection,
  onAnswerReflection
}: Props) {
  const { t } = useTranslation();
  const [answer, setAnswer] = useState(proof.explanation.answer || "");
  const repairDone = proof.repair.status === "REPAIRED";
  const explanationDone = ["PROVIDED", "CHECKABLE"].includes(proof.explanation.status);
  const independentDone = proof.independentUse.status === "VERIFIED";
  const canAnswer = editable && proof.explanation.status === "WAITING";
  const canCreate = editable && proof.explanation.status === "TO_EXPLAIN";
  const targetHref = proof.assignmentId && proof.independentUse.targetProblemId
    ? `/student/assignments/${proof.assignmentId}/problems/${proof.independentUse.targetProblemId}?studentProfileId=${proof.studentProfileId || ""}&sourceSubmissionId=${proof.independentUse.sourceSubmissionId || ""}`
    : null;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (answer.trim() && onAnswerReflection) {
      await onAnswerReflection(answer.trim());
    }
  }

  return (
    <section className="learning-proof" aria-label={t("learningProof.title")}>
      <header className="learning-proof__stages">
        <Stage index="1" label={t("learningProof.stage.repair")} done={repairDone} active={!repairDone} />
        <i aria-hidden="true" />
        <Stage index="2" label={t("learningProof.stage.explain")} done={explanationDone} active={repairDone && !explanationDone} />
        <i aria-hidden="true" />
        <Stage index="3" label={t("learningProof.stage.verify")} done={independentDone} active={explanationDone && !independentDone} />
      </header>

      <div className="learning-proof__body">
        <div className="learning-proof__evidence">
          <strong>{t(`learningProof.repair.${repairStatusKey(proof.repair.status)}`)}</strong>
          {proof.repair.baselineSubmissionId && proof.repair.targetSubmissionId ? (
            <span>{t("learningProof.repair.submissions", {
              before: proof.repair.baselineSubmissionId,
              after: proof.repair.targetSubmissionId
            })}</span>
          ) : null}
          {proof.repair.passedTestCaseDelta && proof.repair.passedTestCaseDelta > 0 ? (
            <span>{t("learningProof.repair.tests", { count: proof.repair.passedTestCaseDelta })}</span>
          ) : null}
          {proof.repair.recoveredIssues.length ? <small>{proof.repair.recoveredIssues.join(" · ")}</small> : null}
        </div>

        <div className="learning-proof__reflection">
          {canCreate ? (
            <button type="button" className="learning-proof__primary" disabled={busy} onClick={() => void onCreateReflection?.()}>
              {t("learningProof.action.explain")}
            </button>
          ) : null}
          {canAnswer ? (
            <form onSubmit={submit}>
              <label htmlFor={`learning-proof-answer-${proof.latestSubmissionId}`}>{t("learningProof.explanation.question")}</label>
              <textarea
                id={`learning-proof-answer-${proof.latestSubmissionId}`}
                rows={3}
                maxLength={1200}
                value={answer}
                onChange={event => setAnswer(event.target.value)}
              />
              <button type="submit" className="learning-proof__primary" disabled={busy || !answer.trim()}>
                {t("learningProof.action.save")}
              </button>
            </form>
          ) : null}
          {explanationDone ? (
            <div className="learning-proof__saved-answer">
              <strong>{t(proof.explanation.checkable ? "learningProof.explanation.checkable" : "learningProof.explanation.provided")}</strong>
              <p>{proof.explanation.answer}</p>
              <small>{t(proof.explanation.checkable
                ? "learningProof.explanation.checkableHint"
                : "learningProof.explanation.providedHint")}</small>
            </div>
          ) : null}
          {!canCreate && !canAnswer && !explanationDone ? (
            <span className="learning-proof__pending">{t(`learningProof.explanation.${explanationStatusKey(proof.explanation.status)}`)}</span>
          ) : null}
        </div>

        <div className="learning-proof__target">
          <strong>{t(`learningProof.verify.${verifyStatusKey(proof.independentUse.status)}`)}</strong>
          {proof.independentUse.targetProblemTitle ? <span>{proof.independentUse.targetProblemTitle}</span> : null}
          {editable && targetHref && explanationDone && !independentDone ? (
            <Link to={targetHref}>{t("learningProof.action.verify")}<ChevronRight size={16} /></Link>
          ) : null}
          {independentDone && proof.independentUse.targetSubmissionId ? (
            <small>{t("learningProof.verify.submission", { id: proof.independentUse.targetSubmissionId })}</small>
          ) : null}
        </div>
      </div>
    </section>
  );
}

function Stage({ index, label, done, active }: { index: string; label: string; done: boolean; active: boolean }) {
  return (
    <span className={`${done ? "is-done" : ""}${active ? " is-active" : ""}`}>
      <b>{done ? <Check size={14} /> : index}</b>{label}
    </span>
  );
}

function repairStatusKey(status: string) {
  return status === "REPAIRED" ? "repaired" : status === "IN_PROGRESS" ? "inProgress" : "notObserved";
}

function explanationStatusKey(status: string) {
  return status === "NOT_READY" ? "notReady" : "waiting";
}

function verifyStatusKey(status: string) {
  if (status === "VERIFIED") return "verified";
  if (status === "TARGET_AVAILABLE") return "available";
  if (status === "NEEDS_SUPPORT") return "needsSupport";
  if (status === "NOT_AVAILABLE") return "notAvailable";
  return "notReady";
}
