type Props = {
  label: string;
  submitted: number;
  total: number;
};

export function AnalyticsSubmissionProgress({ label, submitted, total }: Props) {
  const safeTotal = Math.max(0, total);
  const safeSubmitted = Math.max(0, submitted);

  return (
    <div className="teacher-analytics-submission-progress">
      <span>
        <small>{label}</small>
        <strong>{safeSubmitted}/{safeTotal || "-"}</strong>
      </span>
      <progress value={safeSubmitted} max={Math.max(safeTotal, safeSubmitted, 1)} aria-label={`${label} ${safeSubmitted}/${safeTotal || "-"}`} />
    </div>
  );
}
