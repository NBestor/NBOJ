type EmptyStateProps = {
  title: string;
  description?: string;
  live?: boolean;
};

export function EmptyState({ title, description, live = false }: EmptyStateProps) {
  const liveProps = live ? { role: "status" as const, "aria-live": "polite" as const } : {};

  return (
    <div className="empty-state" {...liveProps}>
      <strong>{title}</strong>
      {description && <p>{description}</p>}
    </div>
  );
}
