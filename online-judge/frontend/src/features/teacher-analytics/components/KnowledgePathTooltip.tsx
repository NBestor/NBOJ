import type { InsightBucket } from "../model";
import { useTranslation } from "../../../shared/i18n";

export function KnowledgePathTooltip({ item }: { item: InsightBucket }) {
  const { t } = useTranslation();
  const path = item.path.map(node => node.label).join(" > ");
  return (
    <span className="teacher-analytics-path" tabIndex={0}>
      <span className="teacher-analytics-path__trigger">{t("teacherAnalytics.pathLabel")}</span>
      <span className="teacher-analytics-path__bubble" role="tooltip">
        {path}
      </span>
    </span>
  );
}
