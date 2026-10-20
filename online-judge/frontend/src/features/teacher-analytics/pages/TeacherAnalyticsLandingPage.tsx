import { useEffect, useState } from "react";
import { ArrowRight, Plus } from "lucide-react";
import { Link } from "react-router-dom";
import { ApiError, api } from "../../../shared/api/client";
import type { ClassGroup, ClassLearningOverview } from "../../../shared/api/types";
import { useTranslation } from "../../../shared/i18n";
import { ButtonLink } from "../../../shared/ui/Button";
import { EmptyState } from "../../../shared/ui/EmptyState";
import { AnalyticsPageBar } from "../components/AnalyticsPageBar";
import { AnalyticsSubmissionProgress } from "../components/AnalyticsSubmissionProgress";

export default function TeacherAnalyticsLandingPage() {
  const { t } = useTranslation();
  const [classes, setClasses] = useState<ClassGroup[]>([]);
  const [overviews, setOverviews] = useState<Record<number, ClassLearningOverview | null>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadClasses();
  }, []);

  async function loadClasses() {
    setLoading(true);
    setError(null);
    try {
      const classResult = await api.classes();
      setClasses(classResult);
      const overviewEntries = await Promise.all(classResult.map(async classGroup => {
        const overview = await api.classLearningOverview(classGroup.id).catch(() => null);
        return [classGroup.id, overview] as const;
      }));
      setOverviews(Object.fromEntries(overviewEntries));
    } catch (currentError) {
      setError(currentError instanceof ApiError || currentError instanceof Error ? currentError.message : t("teacherAnalytics.errors.load"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="teacher-analytics-page">
      <AnalyticsPageBar
        title={t("teacherAnalytics.landing.title")}
        t={t}
        action={<ButtonLink to="/teacher/assignment/new" variant="primary" icon={<Plus size={17} />}>
          {t("teacherAnalytics.actions.newAssignment")}
        </ButtonLink>}
      />

      {error ? <div className="alert alert--error">{error}</div> : null}

      <section className="teacher-analytics-list-panel" aria-label={t("teacherAnalytics.landing.classList")}>
        {loading ? (
          <EmptyState title={t("teacherAnalytics.loading.classes")} live />
        ) : classes.length ? (
          <div className="teacher-analytics-class-grid">
            {classes.map(classGroup => {
              const overview = overviews[classGroup.id];
              return (
                <Link className="teacher-analytics-class-card" to={`/teacher/classes/${classGroup.id}`} key={classGroup.id}>
                  <div className="teacher-analytics-class-card__identity">
                    <strong>{classGroup.name}</strong>
                    <small>{classGroup.grade || t("teacherAnalytics.landing.classFallback")}</small>
                  </div>
                  {overview ? (
                    <>
                      <AnalyticsSubmissionProgress
                        label={t("teacherAnalytics.metrics.submittedStudents")}
                        submitted={overview.submittedStudentCount}
                        total={overview.rosterStudentCount}
                      />
                      <div className="teacher-analytics-class-card__metrics">
                        <span><small>{t("teacherAnalytics.metrics.rosterStudents")}</small><strong>{overview.rosterStudentCount}</strong></span>
                        <span><small>{t("teacherAnalytics.metrics.unsubmittedStudents")}</small><strong>{overview.unsubmittedStudentCount}</strong></span>
                      </div>
                    </>
                  ) : <span>{classGroup.teacherName || t("teacherAnalytics.landing.noTeacherName")}</span>}
                  <ArrowRight size={18} aria-hidden="true" />
                </Link>
              );
            })}
          </div>
        ) : (
          <EmptyState title={t("teacherAnalytics.empty.noClasses")} description={t("teacherAnalytics.empty.noClassesDescription")} />
        )}
      </section>
    </div>
  );
}
