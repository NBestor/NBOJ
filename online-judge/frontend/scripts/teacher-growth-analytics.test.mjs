import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("../src/", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

test("formal teacher route keeps the student identity in the drill-down path", async () => {
  const app = await source("App.tsx");
  assert.match(app, /problems\/:problemId\/students\/:studentProfileId/);
  assert.match(app, /level === "student" && studentProfileId/);
  assert.match(app, /StudentProblemAnalyticsPage/);
});

test("problem analysis exposes three overlapping student counts without a pie chart", async () => {
  const dashboard = await source("features/teacher-analytics/components/AnalyticsDashboard.tsx");
  assert.match(dashboard, /teacherAnalytics\.focus\.affected/);
  assert.match(dashboard, /teacherAnalytics\.focus\.repeated/);
  assert.match(dashboard, /teacherAnalytics\.focus\.resolved/);
  assert.match(dashboard, /affectedStudentIds/);
  assert.match(dashboard, /repeatedStudentIds/);
  assert.match(dashboard, /resolvedStudentIds/);
  assert.doesNotMatch(dashboard, /AnalyticsPieChart/);
  assert.doesNotMatch(dashboard, /dataCompleteness/);
});

test("student evidence page connects growth, saved analysis versions, and version-bound correction", async () => {
  const page = await source("features/teacher-analytics/pages/StudentProblemAnalyticsPage.tsx");
  const client = await source("shared/api/client.ts");
  assert.match(page, /SingleProblemGrowthDashboard/);
  assert.match(page, /analysisVersions/);
  assert.match(page, /feedbackRevisionId: selectedVersionId/);
  assert.match(page, /regenerateTeacherSubmissionAnalysis/);
  assert.match(page, /StudentWorkspace = "growth" \| "evidence" \| "analysis"/);
  assert.match(page, /teacher-student-workspace-tabs/);
  assert.doesNotMatch(page, /teacher-analytics-hero/);
  assert.match(client, /teacherSubmissionEvidence/);
  assert.match(client, /analysis\/regenerate/);
});

test("teacher analytics starts with a compact page bar instead of hero cards", async () => {
  const pageBar = await source("features/teacher-analytics/components/AnalyticsPageBar.tsx");
  const summary = await source("features/teacher-analytics/components/AnalyticsSummaryCards.tsx");
  const dashboard = await source("features/teacher-analytics/components/AnalyticsDashboard.tsx");
  assert.match(pageBar, /teacher-analytics-pagebar/);
  assert.match(summary, /teacher-analytics-summary/);
  assert.match(dashboard, /teacher-problem-issue-sidebar/);
  assert.match(dashboard, /teacher-problem-students/);
  assert.doesNotMatch(dashboard, /SectionTitle step=/);
});
