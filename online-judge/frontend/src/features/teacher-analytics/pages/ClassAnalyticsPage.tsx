import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { ApiError, api } from "../../../shared/api/client";
import type { ClassLearningOverview } from "../../../shared/api/types";
import { useTranslation } from "../../../shared/i18n";
import { EmptyState } from "../../../shared/ui/EmptyState";
import { AnalyticsBreadcrumbs } from "../components/AnalyticsBreadcrumbs";
import { AnalyticsDashboard } from "../components/AnalyticsDashboard";
import { AnalyticsPageBar } from "../components/AnalyticsPageBar";
import { buildClassAnalyticsSnapshot } from "../selectors";

export default function ClassAnalyticsPage() {
  const { t } = useTranslation();
  const { classId = "" } = useParams();
  const classIdNumber = Number(classId);
  const [overview, setOverview] = useState<ClassLearningOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadClassAnalytics();
  }, [classIdNumber, t]);

  async function loadClassAnalytics() {
    if (!classIdNumber) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setOverview(await api.classLearningOverview(classIdNumber));
    } catch (currentError) {
      setError(currentError instanceof ApiError || currentError instanceof Error ? currentError.message : t("teacherAnalytics.errors.load"));
    } finally {
      setLoading(false);
    }
  }

  const snapshot = useMemo(() => {
    return overview ? buildClassAnalyticsSnapshot({ overview, t }) : null;
  }, [overview, t]);

  if (loading && !snapshot) {
    return <EmptyState title={t("teacherAnalytics.loading.class")} live />;
  }

  if (!snapshot) {
    return <EmptyState title={t("teacherAnalytics.empty.classNotFound")} description={error || t("teacherAnalytics.empty.classNotFoundDescription")} />;
  }

  return (
    <div className="teacher-analytics-page">
      <AnalyticsBreadcrumbs items={[{ label: t("teacherAnalytics.breadcrumb.classes"), to: "/teacher/classes" }, { label: snapshot.scope.className }]} />
      <AnalyticsPageBar title={snapshot.scope.className} metrics={snapshot.metrics} t={t} />
      {error ? <div className="alert alert--error">{error}</div> : null}
      <AnalyticsDashboard snapshot={snapshot} t={t} />
    </div>
  );
}
