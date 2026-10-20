import { useId, type ReactNode } from "react";

type PanelProps = {
  title?: ReactNode;
  eyebrow?: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
};

export function Panel({ title, eyebrow, description, action, children, className = "" }: PanelProps) {
  const titleId = useId();
  const hasHeader = Boolean(title || eyebrow || description || action);

  return (
    <section className={`panel ${className}`} aria-labelledby={title ? titleId : undefined}>
      {hasHeader && (
        <header className="panel__header">
          <div>
            {eyebrow && <p className="eyebrow">{eyebrow}</p>}
            {title && <h2 id={titleId}>{title}</h2>}
            {description && <p>{description}</p>}
          </div>
          {action && <div className="panel__action">{action}</div>}
        </header>
      )}
      <div className="panel__body">{children}</div>
    </section>
  );
}
