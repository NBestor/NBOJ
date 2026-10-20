import { useMemo } from "react";
import {
  CircleCheckBig,
  FlaskConical,
  Info,
  Send,
  TriangleAlert,
  type LucideIcon
} from "lucide-react";
import {
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts";
import type {
  SubmissionGrowthIssueSignal,
  SubmissionGrowthSummary,
  SubmissionHistorySummary
} from "../../shared/api/types";
import { formatFullDateTime } from "../../shared/format";
import { useTranslation } from "../../shared/i18n";
import "./SingleProblemGrowthDashboard.css";

type DashboardMode = "student" | "teacher";

type Props = {
  history: SubmissionHistorySummary[];
  selectedSubmissionId?: number | null;
  currentSummary?: SubmissionGrowthSummary | null;
  mode?: DashboardMode;
  onSelectSubmission?: (item: SubmissionHistorySummary) => void;
};

const ISSUE_CATEGORIES = [
  { key: "persisted", color: "#2472d4" },
  { key: "new", color: "#e6a32f" },
  { key: "recurred", color: "#e66f61" },
  { key: "improved", color: "#20a994" },
  { key: "recovered", color: "#8ad3cc" }
] as const;

function normalizedStatus(value?: string | null) {
  return String(value || "UNCOMPARABLE").toUpperCase();
}

function pointKey(item: SubmissionGrowthIssueSignal) {
  return item.normalizedPointKey || item.title || "unclassified";
}

function issueTitle(item: SubmissionGrowthIssueSignal, fallback: string) {
  const path = item.knowledgePath || [];
  return item.title?.trim() || path[path.length - 1]?.trim() || fallback;
}

function currentSignals(summary?: SubmissionGrowthSummary | null) {
  return (summary?.issueSignals || []).filter(item => {
    if (String(item.displayCategory || "REPAIR").toUpperCase() === "IMPROVEMENT") return false;
    return ["NEW", "PERSISTED", "RECURRED"].includes(normalizedStatus(item.changeStatus));
  });
}

function rate(item: SubmissionHistorySummary) {
  const passed = item.growthSummary?.passedTestCases ?? item.passedTestCases ?? 0;
  const total = item.growthSummary?.totalTestCases ?? item.totalTestCases ?? 0;
  return total > 0 ? Math.round((passed / total) * 1000) / 10 : null;
}

function stateTone(state?: string | null) {
  switch (normalizedStatus(state)) {
    case "COMPLETED":
    case "CLEAR_PROGRESS":
      return "positive";
    case "MIXED_PROGRESS":
    case "ISSUE_SHIFTED":
      return "mixed";
    case "REGRESSED":
      return "negative";
    case "DUPLICATE_NO_CHANGE":
    case "UNCOMPARABLE":
      return "neutral";
    default:
      return "steady";
  }
}

export function growthStateKey(state?: string | null) {
  return normalizedStatus(state).toLowerCase();
}

export function SingleProblemGrowthDashboard({
  history,
  selectedSubmissionId,
  currentSummary,
  mode = "student",
  onSelectSubmission
}: Props) {
  const { t } = useTranslation();
  const ordered = useMemo(
    () => history
      .map(item => currentSummary && item.id === selectedSubmissionId
        ? { ...item, growthSummary: currentSummary }
        : item)
      .sort((left, right) => {
        const time = new Date(left.submittedAt || 0).getTime() - new Date(right.submittedAt || 0).getTime();
        return time || left.id - right.id;
      }),
    [currentSummary, history, selectedSubmissionId]
  );
  const selected = ordered.find(item => item.id === selectedSubmissionId) || ordered[ordered.length - 1] || null;
  const selectedSummary = selected?.growthSummary || currentSummary || null;
  const effective = ordered.filter(item => item.growthSummary?.effectiveAttempt && item.growthSummary.comparable);
  const trend = effective.map(item => ({
    id: item.id,
    label: `#${item.id}`,
    passRate: rate(item),
    verdict: item.verdict
  }));
  const shownHistory = ordered.slice(-6);
  const knowledge = useMemo(() => {
    const grouped = new Map<string, { key: string; title: string; count: number; path: string[] }>();
    ordered.forEach(item => {
      if (!item.growthSummary?.effectiveAttempt) return;
      currentSignals(item.growthSummary).forEach(signal => {
        const key = pointKey(signal);
        const existing = grouped.get(key) || {
          key,
          title: issueTitle(signal, t("growthDashboard.unnamedIssue")),
          count: 0,
          path: signal.knowledgePath || []
        };
        existing.count += 1;
        if ((signal.knowledgePath || []).length > existing.path.length) existing.path = signal.knowledgePath || [];
        grouped.set(key, existing);
      });
    });
    return [...grouped.values()].sort((left, right) => right.count - left.count || left.title.localeCompare(right.title)).slice(0, 5);
  }, [ordered, t]);
  const matrixRows = knowledge.map(point => ({
    ...point,
    cells: shownHistory.map(item => {
      const signal = (item.growthSummary?.issueSignals || []).find(candidate => pointKey(candidate) === point.key);
      return { submission: item, status: signal ? normalizedStatus(signal.changeStatus) : null };
    })
  }));
  const currentPassed = selectedSummary?.passedTestCases ?? selected?.passedTestCases ?? 0;
  const currentTotal = selectedSummary?.totalTestCases ?? selected?.totalTestCases ?? 0;
  const currentPassRate = currentTotal ? Math.round((currentPassed / currentTotal) * 1000) / 10 : null;
  const issueCounts = {
    persisted: selectedSummary?.persistedCount || 0,
    new: selectedSummary?.newCount || 0,
    recurred: selectedSummary?.recurringCount || 0,
    improved: selectedSummary?.notObservedCount || 0,
    recovered: selectedSummary?.recoveredCount || 0
  };
  const issueBreakdown = ISSUE_CATEGORIES.map(category => ({
    ...category,
    name: t(`growthDashboard.legend.${category.key}`),
    value: issueCounts[category.key]
  }));
  const issueTotal = issueBreakdown.reduce((total, item) => total + item.value, 0);
  const breadcrumb = knowledge[0]?.path?.length ? knowledge[0].path.slice(0, 3) : [];

  return (
    <section className={`growth-dashboard growth-dashboard--${mode}`} aria-labelledby="growth-dashboard-title">
      <header className="growth-dashboard__header">
        <div>
          {breadcrumb.length ? <p className="growth-dashboard__breadcrumb">{breadcrumb.join(" / ")}</p> : null}
          <div className="growth-dashboard__title-row">
            <h2 id="growth-dashboard-title">{t("growthDashboard.title")}</h2>
            {selected ? <span className="growth-dashboard__submission-chip">{t("growthDashboard.latestSubmission", { id: selected.id })}</span> : null}
            {selectedSummary?.comparisonSubmissionId ? (
              <span className="growth-dashboard__comparison-chip">{t("growthDashboard.comparedWith", { id: selectedSummary.comparisonSubmissionId })}</span>
            ) : null}
          </div>
        </div>
        {selectedSummary ? (
          <strong className={`growth-dashboard__state is-${stateTone(selectedSummary.growthState)}`}>
            {t(`growthDashboard.state.${growthStateKey(selectedSummary.growthState)}`)}
          </strong>
        ) : null}
      </header>

      <div className="growth-dashboard__kpis" aria-label={t("growthDashboard.metricsAria") }>
        <MetricFrame icon={Send} label={t("growthDashboard.metrics.submissions")} value={history.length} tone="brand" />
        <MetricFrame icon={CircleCheckBig} label={t("growthDashboard.metrics.effective")} value={effective.length} tone="success" />
        <MetricFrame icon={FlaskConical} label={t("growthDashboard.metrics.tests")} value={currentTotal ? `${currentPassed}/${currentTotal}` : "-"} tone="warning" />
        <MetricFrame icon={TriangleAlert} label={t("growthDashboard.metrics.unresolved")} value={selectedSummary?.unresolvedCount ?? "-"} tone="danger" />
      </div>

      <div className="growth-dashboard__analytics-grid">
        <article className="growth-dashboard__surface growth-dashboard__trend">
          <SurfaceHeader title={t("growthDashboard.trendTitle")} />
          <div className="growth-dashboard__trend-body">
            {trend.length >= 2 ? (
              <div className="growth-dashboard__chart" aria-label={t("growthDashboard.trendAria")}>
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={trend} margin={{ top: 12, right: 30, left: -8, bottom: 0 }}>
                    <CartesianGrid stroke="#e6eaf0" strokeDasharray="4 4" vertical={false} />
                    <XAxis dataKey="label" axisLine={false} tickLine={false} tick={{ fill: "#475467", fontSize: 12 }} />
                    <YAxis domain={[0, 100]} padding={{ top: 8, bottom: 8 }} ticks={[0, 25, 50, 75, 100]} axisLine={false} tickLine={false} tickFormatter={value => `${value}%`} tick={{ fill: "#667085", fontSize: 12 }} />
                    <Tooltip formatter={value => [`${value}%`, t("growthDashboard.passRate")]} />
                    <Line type="linear" dataKey="passRate" stroke="#1769d2" strokeWidth={3} dot={{ r: 5, fill: "#1769d2", strokeWidth: 2, stroke: "#ffffff" }} activeDot={{ r: 7 }} connectNulls={false} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <div className="growth-dashboard__slope" aria-label={t("growthDashboard.trendAria")}>
                {trend.length ? trend.map((point, index) => (
                  <div key={point.id}>
                    <span>{point.label}</span>
                    <strong>{point.passRate === null ? "-" : `${point.passRate}%`}</strong>
                    {index < trend.length - 1 ? <i aria-hidden="true" /> : null}
                  </div>
                )) : <p>{t("growthDashboard.noComparableAttempts")}</p>}
              </div>
            )}
            <div className="growth-dashboard__trend-insight">
              <Info size={15} aria-hidden="true" />
              <span>
                {currentPassRate === null
                  ? t("growthDashboard.noComparableAttempts")
                  : `${t("growthDashboard.passRate")} ${currentPassRate}%`}
              </span>
            </div>
          </div>
        </article>

        <article className="growth-dashboard__surface growth-dashboard__knowledge">
          <SurfaceHeader title={t("growthDashboard.knowledgeTitle")} />
          {knowledge.length ? (
            <ol className="growth-dashboard__knowledge-list" aria-label={t("growthDashboard.knowledgeAria")}>
              {knowledge.map((point, index) => (
                <li key={point.key}>
                  <span className="growth-dashboard__knowledge-rank">{index + 1}</span>
                  <span className="growth-dashboard__knowledge-name">{point.title}</span>
                  <strong aria-label={`${t("growthDashboard.effectiveOccurrences")} ${point.count}`}>{point.count}</strong>
                </li>
              ))}
            </ol>
          ) : <div className="growth-dashboard__empty">{t("growthDashboard.noKnowledgeData")}</div>}
        </article>

        <article className="growth-dashboard__surface growth-dashboard__evolution">
          <SurfaceHeader title={t("growthDashboard.evolutionTitle")} />
          {issueTotal > 0 ? (
            <div className="growth-dashboard__evolution-body">
              <div className="growth-dashboard__evolution-context">
                {selected ? <strong>{t("growthDashboard.latestSubmission", { id: selected.id })}</strong> : null}
                {selectedSummary?.comparisonSubmissionId ? (
                  <span>{t("growthDashboard.comparedWith", { id: selectedSummary.comparisonSubmissionId })}</span>
                ) : null}
              </div>
              <div className="growth-dashboard__donut-layout">
                <div className="growth-dashboard__donut" aria-label={t("growthDashboard.evolutionAria")}>
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={issueBreakdown}
                        dataKey="value"
                        nameKey="name"
                        cx="50%"
                        cy="50%"
                        innerRadius="62%"
                        outerRadius="84%"
                        paddingAngle={2}
                        stroke="none"
                      >
                        {issueBreakdown.map(item => <Cell key={item.key} fill={item.color} />)}
                      </Pie>
                      <Tooltip formatter={(value, name) => [value, name]} />
                    </PieChart>
                  </ResponsiveContainer>
                  <span className="growth-dashboard__donut-center">
                    <strong>{issueTotal}</strong>
                    <small>{t("growthDashboard.evolutionTitle")}</small>
                  </span>
                </div>
                <div className="growth-dashboard__donut-meta">
                  <div className="growth-dashboard__legend" aria-label={t("growthDashboard.legendAria")}>
                    {issueBreakdown.map(item => (
                      <span key={item.key} className={item.value ? undefined : "is-zero"}>
                        <i style={{ background: item.color }} />
                        {item.name}
                        <strong>{item.value}</strong>
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          ) : <div className="growth-dashboard__empty">{t("growthDashboard.noEvolutionData")}</div>}
        </article>
      </div>

      <article className="growth-dashboard__surface growth-dashboard__matrix">
        <SurfaceHeader title={t("growthDashboard.matrixTitle")} />
        {matrixRows.length ? (
          <div className="growth-dashboard__matrix-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">{t("growthDashboard.knowledgePoint")}</th>
                  {shownHistory.map(item => <th scope="col" key={item.id}>#{item.id}</th>)}
                </tr>
              </thead>
              <tbody>
                {matrixRows.map(row => (
                  <tr key={row.key}>
                    <th scope="row">{row.title}</th>
                    {row.cells.map(cell => (
                      <td key={cell.submission.id} className={cell.status ? `is-${cell.status.toLowerCase()}` : "is-empty"}>
                        <button type="button" disabled={!onSelectSubmission} onClick={() => onSelectSubmission?.(cell.submission)}>
                          {cell.status ? t(`growthDashboard.issueStatus.${cell.status.toLowerCase()}`) : "—"}
                        </button>
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <div className="growth-dashboard__empty">{t("growthDashboard.noMatrixData")}</div>}
      </article>
    </section>
  );
}

function MetricFrame({
  icon: Icon,
  label,
  tone,
  value
}: {
  icon: LucideIcon;
  label: string;
  tone: "brand" | "success" | "warning" | "danger";
  value: string | number;
}) {
  return (
    <div className={`growth-dashboard__metric is-${tone}`}>
      <span className="growth-dashboard__metric-icon" aria-hidden="true"><Icon size={22} /></span>
      <span className="growth-dashboard__metric-copy"><span>{label}</span><strong>{value}</strong></span>
    </div>
  );
}

function SurfaceHeader({ title }: { title: string }) {
  return <header className="growth-dashboard__surface-head"><h3>{title}</h3></header>;
}

export function GrowthTimeline({
  history,
  selectedSubmissionId,
  onSelectSubmission
}: {
  history: SubmissionHistorySummary[];
  selectedSubmissionId?: number | null;
  onSelectSubmission: (item: SubmissionHistorySummary) => void;
}) {
  const { locale, t } = useTranslation();
  return (
    <div className="growth-timeline" aria-label={t("growthTimeline.aria")}>
      {history.map(item => {
        const summary = item.growthSummary;
        const state = summary?.growthState || "UNCOMPARABLE";
        return (
          <button
            type="button"
            className={`growth-timeline__node is-${stateTone(state)}${selectedSubmissionId === item.id ? " is-active" : ""}`}
            onClick={() => onSelectSubmission(item)}
            key={item.id}
          >
            <i aria-hidden="true" />
            <span className="growth-timeline__main">
              <strong>#{item.id}</strong>
              <small>{t(`growthDashboard.state.${growthStateKey(state)}`)}</small>
            </span>
            <span className="growth-timeline__metrics">
              <b>{item.passedTestCases ?? summary?.passedTestCases ?? 0}/{item.totalTestCases ?? summary?.totalTestCases ?? 0}</b>
              {summary?.passedTestCaseDelta ? <small>{summary.passedTestCaseDelta > 0 ? "+" : ""}{summary.passedTestCaseDelta}</small> : null}
            </span>
            <span className="growth-timeline__issues">
              <small>{t("growthTimeline.issueDelta", {
                improved: (summary?.notObservedCount || 0) + (summary?.recoveredCount || 0),
                risk: (summary?.newCount || 0) + (summary?.recurringCount || 0)
              })}</small>
              {summary?.comparisonSubmissionId ? (
                <small>{t("growthDashboard.comparedWith", { id: summary.comparisonSubmissionId })}</small>
              ) : null}
              <time dateTime={item.submittedAt || undefined}>{formatFullDateTime(item.submittedAt, locale)}</time>
            </span>
          </button>
        );
      })}
    </div>
  );
}
