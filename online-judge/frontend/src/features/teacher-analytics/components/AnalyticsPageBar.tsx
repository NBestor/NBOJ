import type { ReactNode } from "react";
import type { AnalyticsMetric } from "../model";
import { AnalyticsSummaryCards } from "./AnalyticsSummaryCards";

type Props = {
  title: string;
  metrics?: AnalyticsMetric[];
  action?: ReactNode;
  t: (key: string, params?: Record<string, string | number>) => string;
};

export function AnalyticsPageBar({ title, metrics = [], action, t }: Props) {
  return (
    <header className="teacher-analytics-pagebar">
      <div className="teacher-analytics-pagebar__object">
        <h1>{title}</h1>
        {metrics.length ? <AnalyticsSummaryCards metrics={metrics} labelFor={t} /> : null}
      </div>
      {action ? <div className="teacher-analytics-pagebar__action">{action}</div> : null}
    </header>
  );
}
