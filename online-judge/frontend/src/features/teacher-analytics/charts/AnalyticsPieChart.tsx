import type { InsightBucket } from "../model";

const colors = ["#0f766e", "#2563eb", "#b45309", "#15803d", "#7c3aed", "#b91c1c"];

type Props = {
  items: InsightBucket[];
  emptyText: string;
};

export function AnalyticsPieChart({ items, emptyText }: Props) {
  const total = items.reduce((sum, item) => sum + item.count, 0);
  if (!items.length || !total) {
    return <p className="teacher-analytics-empty-copy">{emptyText}</p>;
  }
  let cursor = 0;
  const segments = items.slice(0, 6).map((item, index) => {
    const value = item.count / total;
    const start = cursor;
    cursor += value;
    return `${colors[index % colors.length]} ${Math.round(start * 100)}% ${Math.round(cursor * 100)}%`;
  });
  return (
    <div className="teacher-analytics-pie-wrap">
      <div className="teacher-analytics-pie" style={{ background: `conic-gradient(${segments.join(", ")})` }} aria-hidden="true" />
      <div className="teacher-analytics-pie-list">
        {items.slice(0, 6).map((item, index) => (
          <span key={item.id}>
            <i style={{ background: colors[index % colors.length] }} />
            <strong>{item.label}</strong>
            <small>{Math.round(((item.rate || 0) * 100))}%</small>
          </span>
        ))}
      </div>
    </div>
  );
}
