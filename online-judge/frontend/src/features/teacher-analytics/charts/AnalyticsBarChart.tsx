import type { InsightBucket } from "../model";
import { KnowledgePathTooltip } from "../components/KnowledgePathTooltip";

type Props = {
  items: InsightBucket[];
  emptyText: string;
  countLabel: string;
  onSelect?: (item: InsightBucket) => void;
};

export function AnalyticsBarChart({ items, emptyText, countLabel, onSelect }: Props) {
  const max = Math.max(1, ...items.map(item => item.count));
  if (!items.length) {
    return <p className="teacher-analytics-empty-copy">{emptyText}</p>;
  }
  return (
    <div className="teacher-analytics-bars">
      {items.map(item => (
        <button type="button" className="teacher-analytics-bar-row" key={item.id} onClick={() => onSelect?.(item)}>
          <span className="teacher-analytics-bar-label">
            <strong>{item.label}</strong>
            <KnowledgePathTooltip item={item} />
          </span>
          <span className="teacher-analytics-bar-track" aria-hidden="true">
            <span style={{ width: `${Math.max(8, (item.count / max) * 100)}%` }} />
          </span>
          <span className="teacher-analytics-bar-count">
            {item.count} {countLabel}
          </span>
        </button>
      ))}
    </div>
  );
}
