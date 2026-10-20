import type { InsightBucket } from "../model";
import { KnowledgePathTooltip } from "./KnowledgePathTooltip";

type Props = {
  title: string;
  subtitle: string;
  items: InsightBucket[];
  emptyText: string;
  fitLabel: (fit: InsightBucket["fit"]) => string;
};

export function AiKnowledgeInsightPanel({ title, subtitle, items, emptyText, fitLabel }: Props) {
  return (
    <aside className="teacher-analytics-ai-panel" aria-label={title}>
      <div className="teacher-analytics-section-head">
        <span>{subtitle}</span>
        <h2>{title}</h2>
      </div>
      {items.length ? (
        <div className="teacher-analytics-insight-list">
          {items.slice(0, 5).map(item => (
            <article className="teacher-analytics-insight" key={item.id}>
              <div>
                <strong>{item.label}</strong>
                <KnowledgePathTooltip item={item} />
              </div>
              <p>
                {item.count} · {fitLabel(item.fit)}
              </p>
              {item.evidence[0] ? <small>{item.evidence[0].subtitle}</small> : null}
            </article>
          ))}
        </div>
      ) : (
        <p className="teacher-analytics-empty-copy">{emptyText}</p>
      )}
    </aside>
  );
}
