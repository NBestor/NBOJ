import type { AnalyticsMetric } from "../model";

type Props = {
  metrics: AnalyticsMetric[];
  labelFor: (labelKey: string) => string;
};

export function AnalyticsSummaryCards({ metrics, labelFor }: Props) {
  return (
    <section className="teacher-analytics-summary" aria-label={labelFor("teacherAnalytics.sections.metrics")}>
      {metrics.map(metric => (
        <div className="teacher-analytics-metric" key={metric.key}>
          <span>{labelFor(metric.labelKey)}</span>
          <strong>{metric.value}</strong>
        </div>
      ))}
    </section>
  );
}
