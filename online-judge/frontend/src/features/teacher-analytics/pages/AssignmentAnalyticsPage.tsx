import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { ApiError, api } from "../../../shared/api/client";
import type { Assignment, AssignmentOverview, ClassGroup } from "../../../shared/api/types";
import { useTranslation } from "../../../shared/i18n";
import { EmptyState } from "../../../shared/ui/EmptyState";
import { AnalyticsBreadcrumbs } from "../components/AnalyticsBreadcrumbs";
import { AnalyticsDashboard } from "../components/AnalyticsDashboard";
import { AnalyticsPageBar } from "../components/AnalyticsPageBar";
import { buildAssignmentAnalyticsSnapshot, findAssignment, findClass } from "../selectors";

export default function AssignmentAnalyticsPage() {
  const { t } = useTranslation();
  const { classId = "", assignmentId = "" } = useParams();
  const classIdNumber = Number(classId);
  const assignmentIdNumber = Number(assignmentId);
  const [classes, setClasses] = useState<ClassGroup[]>([]);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [overview, setOverview] = useState<AssignmentOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadAssignmentAnalytics();
  }, [classIdNumber, assignmentIdNumber]);

  async function loadAssignmentAnalytics() {
    setLoading(true);
    setError(null);
    try {
      const [classResult, assignmentResult, overviewResult] = await Promise.all([
        api.classes(),
        api.assignments(),
        api.assignmentOverview(assignmentIdNumber)
      ]);
      setClasses(classResult);
      setAssignments(assignmentResult);
      setOverview(overviewResult);
    } catch (currentError) {
      setError(currentError instanceof ApiError || currentError instanceof Error ? currentError.message : t("teacherAnalytics.errors.load"));
    } finally {
      setLoading(false);
    }
  }

  const snapshot = useMemo(() => {
    const assignment = findAssignment(assignments, assignmentIdNumber, t);
    const currentClass =
      findClass(classes, classIdNumber) ||
      (assignment?.classGroupId ? findClass(classes, assignment.classGroupId) : null) ||
      (assignment
        ? { id: assignment.classGroupId || classIdNumber || 0, name: assignment.className, grade: null, teacherName: null, activeStudentCount: 0 }
        : null);
    return currentClass && assignment && overview ? buildAssignmentAnalyticsSnapshot({ classGroup: currentClass, assignment, overview, t }) : null;
  }, [assignmentIdNumber, assignments, classIdNumber, classes, overview, t]);

  if (loading && !snapshot) {
    return <EmptyState title={t("teacherAnalytics.loading.assignment")} live />;
  }

  if (!snapshot) {
    return <EmptyState title={t("teacherAnalytics.empty.assignmentNotFound")} description={error || t("teacherAnalytics.empty.assignmentNotFoundDescription")} />;
  }

  return (
    <div className="teacher-analytics-page">
      <AnalyticsBreadcrumbs
        items={[
          { label: t("teacherAnalytics.breadcrumb.classes"), to: "/teacher/classes" },
          { label: snapshot.scope.className, to: `/teacher/classes/${snapshot.scope.classId}` },
          { label: snapshot.scope.assignmentTitle || t("teacherAnalytics.scope.assignment") }
        ]}
      />
      <AnalyticsPageBar title={snapshot.scope.assignmentTitle || t("teacherAnalytics.scope.assignment")} metrics={snapshot.metrics} t={t} />
      {error ? <div className="alert alert--error">{error}</div> : null}
      <AnalyticsDashboard snapshot={snapshot} t={t} />
    </div>
  );
}
