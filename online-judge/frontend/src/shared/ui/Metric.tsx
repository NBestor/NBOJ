export function Metric({ label, value, note }: { label: string; value: React.ReactNode; note?: React.ReactNode }) {
  return (
    <div className="metric">
      <span className="metric__label">{label}</span>
      <strong className="metric__value">{value}</strong>
      {note && <small>{note}</small>}
    </div>
  );
}
