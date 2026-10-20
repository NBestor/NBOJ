import type { AnalyticsGranularity } from "../model";

type Props = {
  value: AnalyticsGranularity;
  labels: Record<AnalyticsGranularity, string>;
  onChange: (value: AnalyticsGranularity) => void;
};

const options: AnalyticsGranularity[] = ["chapter", "knowledgePoint", "skillUnit", "mistakePoint"];

export function GranularitySelector({ value, labels, onChange }: Props) {
  return (
    <div className="teacher-analytics-granularity" role="tablist" aria-label={labels[value]}>
      {options.map(option => (
        <button
          key={option}
          type="button"
          role="tab"
          aria-selected={value === option}
          className={value === option ? "is-active" : ""}
          onClick={() => onChange(option)}
        >
          {labels[option]}
        </button>
      ))}
    </div>
  );
}
