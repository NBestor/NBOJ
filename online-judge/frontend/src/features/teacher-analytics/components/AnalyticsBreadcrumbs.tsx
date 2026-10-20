import { Link } from "react-router-dom";
import { useTranslation } from "../../../shared/i18n";

export type BreadcrumbItem = {
  label: string;
  to?: string;
};

export function AnalyticsBreadcrumbs({ items }: { items: BreadcrumbItem[] }) {
  const { t } = useTranslation();
  return (
    <nav className="teacher-analytics-breadcrumbs" aria-label={t("teacherAnalytics.breadcrumb.aria")}>
      {items.map((item, index) => (
        <span key={`${item.label}-${index}`}>
          {item.to ? <Link to={item.to}>{item.label}</Link> : <strong aria-current="page">{item.label}</strong>}
        </span>
      ))}
    </nav>
  );
}
