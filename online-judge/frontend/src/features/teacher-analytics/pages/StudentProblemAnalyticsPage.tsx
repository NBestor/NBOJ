import { type FormEvent, type KeyboardEvent, useEffect, useMemo, useState } from "react";
import { Code2, RefreshCw, Save, TestTube2 } from "lucide-react";
import { useParams } from "react-router-dom";
import { ApiError, api } from "../../../shared/api/client";
import type {
  Assignment,
  AssignmentOverview,
  ClassGroup,
  DiagnosisTag,
  LearningProof,
  SubmissionHistorySummary,
  TeacherSubmissionAnalysisVersion,
  TeacherSubmissionEvidence
} from "../../../shared/api/types";
import { formatDateTime } from "../../../shared/format";
import { useTranslation } from "../../../shared/i18n";
import { Button } from "../../../shared/ui/Button";
import { EmptyState } from "../../../shared/ui/EmptyState";
import { GrowthTimeline, growthStateKey } from "../../growth/SingleProblemGrowthDashboard";
import { LearningProofPanel } from "../../growth/LearningProofPanel";
import { AnalyticsBreadcrumbs } from "../components/AnalyticsBreadcrumbs";
import { AnalyticsPageBar } from "../components/AnalyticsPageBar";

type CorrectionDraft = {
  issueTag: string;
  fineIssueTag: string;
  note: string;
};

type StudentWorkspace = "growth" | "evidence" | "analysis";
const STUDENT_WORKSPACES: StudentWorkspace[] = ["growth", "evidence", "analysis"];

export default function StudentProblemAnalyticsPage() {
  const { locale, t } = useTranslation();
  const { classId = "", assignmentId = "", problemId = "", studentProfileId = "" } = useParams();
  const classIdNumber = Number(classId);
  const assignmentIdNumber = Number(assignmentId);
  const problemIdNumber = Number(problemId);
  const studentIdNumber = Number(studentProfileId);
  const [classes, setClasses] = useState<ClassGroup[]>([]);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [overview, setOverview] = useState<AssignmentOverview | null>(null);
  const [history, setHistory] = useState<SubmissionHistorySummary[]>([]);
  const [learningProof, setLearningProof] = useState<LearningProof | null>(null);
  const [diagnosisTags, setDiagnosisTags] = useState<DiagnosisTag[]>([]);
  const [selectedSubmissionId, setSelectedSubmissionId] = useState<number | null>(null);
  const [workspace, setWorkspace] = useState<StudentWorkspace>("growth");
  const [visibleHistoryCount, setVisibleHistoryCount] = useState(10);
  const [evidence, setEvidence] = useState<TeacherSubmissionEvidence | null>(null);
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null);
  const [correction, setCorrection] = useState<CorrectionDraft>({ issueTag: "", fineIssueTag: "", note: "" });
  const [loading, setLoading] = useState(true);
  const [evidenceLoading, setEvidenceLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    void loadPage();
  }, [assignmentIdNumber, problemIdNumber, studentIdNumber]);

  useEffect(() => {
    if (selectedSubmissionId) void loadEvidence(selectedSubmissionId);
  }, [selectedSubmissionId]);

  async function loadPage() {
    setLoading(true);
    setMessage(null);
    try {
      const [classResult, assignmentResult, overviewResult, growthResult, tagsResult, learningProofResult] = await Promise.all([
        api.classes(),
        api.assignments(),
        api.assignmentOverview(assignmentIdNumber),
        api.teacherStudentProblemGrowth(assignmentIdNumber, problemIdNumber, studentIdNumber),
        api.diagnosisTags(),
        api.teacherProblemLearningProof(assignmentIdNumber, problemIdNumber).catch(() => null)
      ]);
      setClasses(classResult);
      setAssignments(assignmentResult);
      setOverview(overviewResult);
      setHistory(growthResult);
      setVisibleHistoryCount(10);
      setDiagnosisTags(tagsResult);
      setLearningProof(learningProofResult?.students.find(item => item.studentProfileId === studentIdNumber)?.proof || null);
      const latest = [...growthResult].sort((left, right) => Date.parse(right.submittedAt || "") - Date.parse(left.submittedAt || "") || right.id - left.id)[0];
      setSelectedSubmissionId(current => current && growthResult.some(item => item.id === current) ? current : latest?.id || null);
    } catch (error) {
      setMessage({ tone: "error", text: errorMessage(error, t("teacherAnalytics.errors.load")) });
    } finally {
      setLoading(false);
    }
  }

  async function loadEvidence(submissionId: number) {
    setEvidenceLoading(true);
    try {
      const result = await api.teacherSubmissionEvidence(assignmentIdNumber, submissionId);
      setEvidence(result);
      const official = result.analysisVersions.find(item => item.officialVersion) || result.analysisVersions[0];
      setSelectedVersionId(official?.id || null);
    } catch (error) {
      setEvidence(null);
      setMessage({ tone: "error", text: errorMessage(error, t("teacherAnalytics.student.evidenceLoadFailed")) });
    } finally {
      setEvidenceLoading(false);
    }
  }

  async function regenerate() {
    if (!selectedSubmissionId) return;
    setSaving(true);
    try {
      await api.regenerateTeacherSubmissionAnalysis(assignmentIdNumber, selectedSubmissionId);
      setMessage({ tone: "success", text: t("teacherAnalytics.student.regenerationStarted") });
    } catch (error) {
      setMessage({ tone: "error", text: errorMessage(error, t("teacherAnalytics.student.regenerationFailed")) });
    } finally {
      setSaving(false);
    }
  }

  async function saveCorrection(event: FormEvent) {
    event.preventDefault();
    if (!selectedSubmissionId || !correction.issueTag) return;
    setSaving(true);
    try {
      await api.correctDiagnosis(assignmentIdNumber, {
        submissionId: selectedSubmissionId,
        feedbackRevisionId: selectedVersionId,
        correctedIssueTag: correction.issueTag,
        correctedFineGrainedTag: correction.fineIssueTag || null,
        teacherNote: correction.note,
        evalCandidate: true,
        correctedBy: "teacher"
      });
      setCorrection({ issueTag: "", fineIssueTag: "", note: "" });
      await loadEvidence(selectedSubmissionId);
      setMessage({ tone: "success", text: t("teacherAnalytics.student.correctionSaved") });
    } catch (error) {
      setMessage({ tone: "error", text: errorMessage(error, t("teacherAnalytics.student.correctionFailed")) });
    } finally {
      setSaving(false);
    }
  }

  const assignment = assignments.find(item => item.id === assignmentIdNumber) || overview?.assignment || null;
  const classGroup = classes.find(item => item.id === classIdNumber)
    || classes.find(item => item.id === assignment?.classGroupId)
    || null;
  const problem = overview?.problemSummaries?.find(item => item.problemId === problemIdNumber) || null;
  const student = problem?.students?.find(item => item.studentProfileId === studentIdNumber) || null;
  const selectedHistory = history.find(item => item.id === selectedSubmissionId) || null;
  const selectedVersion = evidence?.analysisVersions.find(item => item.id === selectedVersionId) || evidence?.analysisVersions[0] || null;
  const orderedHistory = useMemo(
    () => [...history].sort((left, right) => Date.parse(right.submittedAt || "") - Date.parse(left.submittedAt || "") || right.id - left.id),
    [history]
  );
  const visibleHistory = orderedHistory.slice(0, visibleHistoryCount);
  const comparisonHistory = selectedHistory?.growthSummary?.comparisonSubmissionId
    ? history.find(item => item.id === selectedHistory.growthSummary?.comparisonSubmissionId) || null
    : null;
  const coarseTags = diagnosisTags.filter(tag => !tag.fineGrained);
  const fineTags = diagnosisTags.filter(tag => tag.fineGrained);

  if (loading) return <EmptyState title={t("teacherAnalytics.student.loading")} live />;
  if (!overview || !assignment || !problem || !student) {
    return <EmptyState title={t("teacherAnalytics.student.notFound")} description={message?.text || t("teacherAnalytics.student.notFoundDescription")} />;
  }

  const resolvedClassId = classGroup?.id || classIdNumber;
  const problemHref = `/teacher/classes/${resolvedClassId}/assignments/${assignment.id}/problems/${problem.problemId}`;

  function handleWorkspaceKeyDown(event: KeyboardEvent<HTMLButtonElement>, current: StudentWorkspace) {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight" && event.key !== "Home" && event.key !== "End") return;
    event.preventDefault();
    const currentIndex = STUDENT_WORKSPACES.indexOf(current);
    const nextIndex = event.key === "Home"
      ? 0
      : event.key === "End"
        ? STUDENT_WORKSPACES.length - 1
        : (currentIndex + (event.key === "ArrowRight" ? 1 : -1) + STUDENT_WORKSPACES.length) % STUDENT_WORKSPACES.length;
    const nextWorkspace = STUDENT_WORKSPACES[nextIndex];
    setWorkspace(nextWorkspace);
    requestAnimationFrame(() => document.getElementById(`teacher-student-tab-${nextWorkspace}`)?.focus());
  }

  return (
    <div className="teacher-analytics-page teacher-student-trajectory-page">
      <AnalyticsBreadcrumbs items={[
        { label: t("teacherAnalytics.breadcrumb.classes"), to: "/teacher/classes" },
        { label: classGroup?.name || assignment.className || t("teacherAnalytics.scope.class"), to: `/teacher/classes/${resolvedClassId}` },
        { label: assignment.title, to: `/teacher/classes/${resolvedClassId}/assignments/${assignment.id}` },
        { label: problem.title, to: problemHref },
        { label: student.displayName }
      ]} />

      <AnalyticsPageBar
        title={student.displayName}
        t={t}
        metrics={[
          { key: "rawSubmissions", labelKey: "teacherAnalytics.focus.rawSubmissions", value: student.attemptCount },
          { key: "effectiveEdits", labelKey: "teacherAnalytics.focus.effectiveEdits", value: student.effectiveAttemptCount || 0 },
          { key: "eventualPass", labelKey: "teacherAnalytics.focus.eventualPass", value: student.passedCount > 0 ? t("teacherAnalytics.student.yes") : t("teacherAnalytics.student.no") },
          { key: "latestResult", labelKey: "teacherAnalytics.student.latestResult", value: teacherVerdictLabel(student.latestVerdict, t) }
        ]}
      />

      {message ? <div className={`alert alert--${message.tone}`}>{message.text}</div> : null}

      <div className="teacher-student-toolbar">
        <nav className="teacher-student-workspace-tabs" role="tablist" aria-label={t("teacherAnalytics.student.workspaceAria")}>
          {STUDENT_WORKSPACES.map(item => (
            <button
              type="button"
              role="tab"
              id={`teacher-student-tab-${item}`}
              aria-controls={`teacher-student-panel-${item}`}
              aria-selected={workspace === item}
              tabIndex={workspace === item ? 0 : -1}
              className={workspace === item ? "is-active" : ""}
              onClick={() => setWorkspace(item)}
              onKeyDown={event => handleWorkspaceKeyDown(event, item)}
              key={item}
            >
              {t(`teacherAnalytics.student.workspace.${item}`)}
            </button>
          ))}
        </nav>

        {selectedHistory ? (
          <section className="teacher-submission-context" aria-label={t("teacherAnalytics.student.submissionContextAria")}>
            <div>
              <span>{t("teacherAnalytics.student.currentSubmission")}</span>
              <strong>#{selectedHistory.id} · {teacherVerdictLabel(selectedHistory.verdict, t)}</strong>
              <time dateTime={selectedHistory.submittedAt || undefined}>{formatDateTime(selectedHistory.submittedAt, locale)}</time>
            </div>
            <label>
              <span>{t("teacherAnalytics.student.switchSubmission")}</span>
              <select value={selectedHistory.id} onChange={event => setSelectedSubmissionId(Number(event.target.value))}>
                {orderedHistory.map(item => (
                  <option value={item.id} key={item.id}>#{item.id} · {teacherVerdictLabel(item.verdict, t)} · {formatDateTime(item.submittedAt, locale)}</option>
                ))}
              </select>
            </label>
          </section>
        ) : null}
      </div>

      {workspace === "growth" ? (
        <div
          className="teacher-student-workspace teacher-student-workspace--growth"
          role="tabpanel"
          id="teacher-student-panel-growth"
          aria-labelledby="teacher-student-tab-growth"
        >
          {learningProof ? <LearningProofPanel proof={learningProof} /> : null}
          <section className="teacher-student-timeline-panel">
            <header className="teacher-evidence-section-title">
              <h2>{t("teacherAnalytics.student.fullTimeline")}</h2>
              <small>{t("teacherAnalytics.student.timelineCount", { visible: visibleHistory.length, total: orderedHistory.length })}</small>
            </header>
            <GrowthTimeline
              history={visibleHistory}
              selectedSubmissionId={selectedSubmissionId}
              onSelectSubmission={item => setSelectedSubmissionId(item.id)}
            />
            {visibleHistory.length < orderedHistory.length ? (
              <Button type="button" variant="secondary" onClick={() => setVisibleHistoryCount(count => count + 10)}>
                {t("teacherAnalytics.student.loadEarlier")}
              </Button>
            ) : null}
          </section>

          <TeacherSubmissionComparison selected={selectedHistory} comparison={comparisonHistory} t={t} />
        </div>
      ) : null}

      {workspace === "evidence" ? (
        <section
          className="teacher-submission-evidence-main"
          role="tabpanel"
          id="teacher-student-panel-evidence"
          aria-labelledby="teacher-student-tab-evidence"
        >
          <header className="teacher-evidence-section-title">
            <h2>{t("teacherAnalytics.student.submissionEvidence")}</h2>
          </header>

          {evidenceLoading ? <EmptyState title={t("teacherAnalytics.student.loadingEvidence")} live /> : null}
          {!evidenceLoading && evidence ? (
            <>
              <article className="teacher-code-evidence">
                <h3><Code2 size={17} />{t("teacherAnalytics.student.sourceCode")}</h3>
                <pre><code>{evidence.submission.sourceCode}</code></pre>
              </article>
              <article className="teacher-test-evidence">
                <h3><TestTube2 size={17} />{t("teacherAnalytics.student.testEvidence")}</h3>
                <div>
                  {(evidence.submission.testCaseResults || []).map(item => (
                    <span className={item.passed ? "is-passed" : "is-failed"} key={item.testCaseNumber}>
                      #{item.testCaseNumber} {item.passed ? t("teacherAnalytics.student.testPassed") : t("teacherAnalytics.student.testFailed")}
                    </span>
                  ))}
                </div>
              </article>
            </>
          ) : null}
        </section>
      ) : null}

      {workspace === "analysis" ? (
        <section
          className="teacher-analysis-version-panel"
          role="tabpanel"
          id="teacher-student-panel-analysis"
          aria-labelledby="teacher-student-tab-analysis"
        >
          <header className="teacher-evidence-section-title">
            <h2>{t("teacherAnalytics.student.analysisVersions")}</h2>
            <Button type="button" variant="secondary" icon={<RefreshCw size={15} />} disabled={saving || !selectedSubmissionId} onClick={regenerate}>
              {t("teacherAnalytics.student.regenerate")}
            </Button>
          </header>
          {evidenceLoading ? <EmptyState title={t("teacherAnalytics.student.loadingEvidence")} live /> : null}
          {evidence?.analysisVersions.length ? (
            <>
              <div className="teacher-analysis-version-tabs">
                {evidence.analysisVersions.map(version => (
                  <button type="button" className={version.id === selectedVersion?.id ? "is-active" : ""} onClick={() => setSelectedVersionId(version.id)} key={version.id}>
                    <strong>{t("teacherAnalytics.student.version", { version: version.versionNumber })}</strong>
                    <small>{version.officialVersion ? t("teacherAnalytics.student.official") : formatDateTime(version.generatedAt)}</small>
                  </button>
                ))}
              </div>
              {selectedVersion ? <AnalysisVersionDetail version={selectedVersion} t={t} /> : null}
            </>
          ) : <p>{t("teacherAnalytics.student.noSavedVersions")}</p>}

          <form className="teacher-version-correction" onSubmit={saveCorrection}>
            <h3>{t("teacherAnalytics.student.correctionForVersion")}</h3>
            <label>{t("teacherAnalytics.correction.issue")}
              <select value={correction.issueTag} onChange={event => setCorrection({ ...correction, issueTag: event.target.value })}>
                <option value="">{t("teacherAnalytics.student.selectIssue")}</option>
                {coarseTags.map(tag => <option value={tag.id} key={tag.id}>{tag.label}</option>)}
              </select>
            </label>
            <label>{t("teacherAnalytics.correction.fineIssue")}
              <select value={correction.fineIssueTag} onChange={event => setCorrection({ ...correction, fineIssueTag: event.target.value })}>
                <option value="">{t("teacherAnalytics.student.optional")}</option>
                {fineTags.map(tag => <option value={tag.id} key={tag.id}>{tag.label}</option>)}
              </select>
            </label>
            <label>{t("teacherAnalytics.correction.note")}
              <textarea value={correction.note} onChange={event => setCorrection({ ...correction, note: event.target.value })} />
            </label>
            <Button type="submit" variant="primary" icon={<Save size={15} />} disabled={saving || !correction.issueTag || !selectedVersionId}>
              {t("teacherAnalytics.correction.submit")}
            </Button>
          </form>

          {evidence?.corrections.length ? (
            <div className="teacher-version-corrections">
              <h3>{t("teacherAnalytics.student.savedCorrections")}</h3>
              {evidence.corrections.map(item => (
                <article key={item.id}>
                  <strong>{item.correctedFineGrainedTag || item.correctedIssueTag}</strong>
                  <small>{item.feedbackRevisionId ? t("teacherAnalytics.student.boundVersion", { id: item.feedbackRevisionId }) : t("teacherAnalytics.student.unboundLegacyCorrection")}</small>
                  {item.teacherNote ? <p>{item.teacherNote}</p> : null}
                </article>
              ))}
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

function TeacherSubmissionComparison({
  selected,
  comparison,
  t
}: {
  selected: SubmissionHistorySummary | null;
  comparison: SubmissionHistorySummary | null;
  t: (key: string, params?: Record<string, string | number>) => string;
}) {
  if (!selected) return null;
  const summary = selected.growthSummary;
  const currentPassed = summary?.passedTestCases ?? selected.passedTestCases ?? 0;
  const currentTotal = summary?.totalTestCases ?? selected.totalTestCases ?? 0;
  const previousPassed = summary?.previousPassedTestCases ?? comparison?.passedTestCases ?? 0;
  const previousTotal = summary?.previousTotalTestCases ?? comparison?.totalTestCases ?? 0;
  const signals = (summary?.issueSignals || []).filter(item => ["NEW", "PERSISTED", "RECURRED", "RECOVERED", "NOT_OBSERVED"].includes(String(item.changeStatus || "").toUpperCase()));
  const testChange = comparison || summary?.comparisonSubmissionId
    ? `${previousTotal ? `${previousPassed}/${previousTotal}` : "-"} → ${currentTotal ? `${currentPassed}/${currentTotal}` : "-"}`
    : currentTotal ? `${currentPassed}/${currentTotal}` : "-";

  return (
    <section className="teacher-submission-comparison" aria-labelledby="teacher-submission-comparison-title">
      <header>
        <div>
          <h2 id="teacher-submission-comparison-title">{t("teacherAnalytics.student.selectedChange")}</h2>
          <small>{summary?.comparisonSubmissionId
            ? t("teacherAnalytics.student.comparedSubmission", { id: summary.comparisonSubmissionId })
            : t("teacherAnalytics.student.firstRecordedSubmission")}</small>
        </div>
        {summary ? <strong>{t(`growthDashboard.state.${growthStateKey(summary.growthState)}`)}</strong> : null}
      </header>
      <div className="teacher-submission-comparison__facts">
        <span><small>{t("teacherAnalytics.student.testPointChange")}</small><strong>{testChange}</strong></span>
        <span><small>{t("teacherAnalytics.student.unresolvedIssues")}</small><strong>{summary?.unresolvedCount ?? "-"}</strong></span>
        <span><small>{t("teacherAnalytics.student.resolvedIssues")}</small><strong>{summary?.recoveredCount ?? 0}</strong></span>
      </div>
      {signals.length ? (
        <ul>
          {signals.map((item, index) => (
            <li key={`${item.normalizedPointKey || item.title || "issue"}-${index}`}>
              <strong>{item.title || t("growthDashboard.unnamedIssue")}</strong>
              <span>{t(`growthDashboard.issueStatus.${String(item.changeStatus || "uncomparable").toLowerCase()}`)}</span>
            </li>
          ))}
        </ul>
      ) : <p>{t("teacherAnalytics.student.noIssueChange")}</p>}
    </section>
  );
}

function AnalysisVersionDetail({ version, t }: { version: TeacherSubmissionAnalysisVersion; t: (key: string, params?: Record<string, string | number>) => string }) {
  const feedbackItems = [...(version.feedback?.repairItems || []), ...(version.feedback?.improvementItems || [])];
  return (
    <article className="teacher-analysis-version-detail">
      <div className="teacher-analysis-version-meta">
        <span>{version.status}</span>
        <span>{version.model || version.source || "-"}</span>
        <span>{version.promptVersion || version.schemaVersion || "-"}</span>
      </div>
      {version.analysis ? (
        <>
          <h3>{version.analysis.headline || t("teacherAnalytics.student.savedAnalysis")}</h3>
          <p>{version.analysis.summary}</p>
        </>
      ) : <p>{t("teacherAnalytics.student.legacyVersionNoAnalysis")}</p>}
      {feedbackItems.length ? (
        <div className="teacher-analysis-version-items">
          {feedbackItems.slice(0, 6).map((item, index) => (
            <div key={`${item.title || "item"}-${index}`}><strong>{item.title}</strong><p>{item.body}</p></div>
          ))}
        </div>
      ) : null}
    </article>
  );
}

function teacherVerdictLabel(
  verdict: string | null | undefined,
  t: (key: string, params?: Record<string, string | number>) => string
) {
  const key = String(verdict || "UNKNOWN").toUpperCase().replace(/[^A-Z0-9]+/g, "_").replace(/^_|_$/g, "").toLowerCase();
  const supported = ["accepted", "wrong_answer", "compilation_error", "runtime_error", "time_limit_exceeded", "memory_limit_exceeded", "internal_error", "pending", "unknown"];
  return t(`teacherAnalytics.student.verdict.${supported.includes(key) ? key : "unknown"}`);
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}
