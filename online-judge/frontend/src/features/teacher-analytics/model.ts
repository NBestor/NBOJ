import type { AiFeedbackImpact, Assignment, AssignmentOverview, ClassGroup } from "../../shared/api/types";

export type AnalyticsScopeType = "class" | "assignment" | "problem";
export type AnalyticsGranularity = "chapter" | "knowledgePoint" | "skillUnit" | "mistakePoint";
export type LibraryFit = "HIT" | "PARTIAL" | "MISS" | "UNKNOWN";

export type AnalyticsScope = {
  type: AnalyticsScopeType;
  classId: number;
  className: string;
  assignmentId?: number;
  assignmentTitle?: string;
  problemId?: number;
  problemTitle?: string;
};

export type AnalyticsMetric = {
  key: string;
  labelKey: string;
  value: string | number;
  note?: string | number;
};

export type KnowledgePathNode = {
  label: string;
  kind: AnalyticsGranularity;
};

export type AnalyticsEvidenceSample = {
  id: string;
  title: string;
  subtitle: string;
  href?: string;
  meta?: string;
  assignmentId?: number;
  submissionId?: number | null;
  studentProfileId?: number;
  problemId?: number;
  issueTag?: string | null;
  fineGrainedTag?: string | null;
  aiFeedbackImpact?: AiFeedbackImpact | null;
};

export type InsightBucket = {
  id: string;
  label: string;
  count: number;
  rate?: number | null;
  affectedStudentCount?: number;
  repeatedStudentCount?: number;
  affectedProblemCount?: number;
  rawOccurrenceCount?: number;
  effectiveWeightedOccurrenceCount?: number;
  unresolvedStudentCount?: number;
  recurringStudentCount?: number;
  recoveredStudentCount?: number;
  resolvedStudentCount?: number;
  affectedStudentIds?: number[];
  repeatedStudentIds?: number[];
  resolvedStudentIds?: number[];
  recoveryRate?: number | null;
  difficultyClassification?: string | null;
  pathStatus?: "FORMAL" | "PROVISIONAL" | "INFERRED" | "UNCLASSIFIED" | string;
  path: KnowledgePathNode[];
  fit: LibraryFit;
  evidence: AnalyticsEvidenceSample[];
};

export type AssignmentAnalyticsRecord = Assignment & {
  title: string;
  className: string;
};

export type AssignmentOverviewMap = Record<number, AssignmentOverview | null>;

export type AnalyticsSnapshot = {
  scope: AnalyticsScope;
  classGroup?: ClassGroup | null;
  assignment?: AssignmentAnalyticsRecord | null;
  overview?: AssignmentOverview | null;
  metrics: AnalyticsMetric[];
  insightBuckets: Record<AnalyticsGranularity, InsightBucket[]>;
  assignmentRows: AssignmentRow[];
  problemRows: ProblemRow[];
  evidenceSamples: AnalyticsEvidenceSample[];
  emptyReason?: string;
};

export type AssignmentRow = {
  id: number;
  title: string;
  status: string;
  href: string;
  problemCount: number;
  submittedStudentCount: number;
  participantCount: number;
  completedRequiredStudentCount: number;
  passRate: number | null;
  topIssue?: string | null;
};

export type ProblemRow = {
  id: number;
  title: string;
  href: string;
  difficulty?: string | null;
  submittedStudentCount: number;
  unsubmittedStudentCount: number;
  passedStudentCount: number;
  firstPassStudentCount: number;
  effectiveAttemptCount: number;
  medianEffectiveAttempts?: number | null;
  participantCount: number;
  passRate: number | null;
  topIssue?: string | null;
};

export const GRANULARITIES: AnalyticsGranularity[] = ["chapter", "knowledgePoint", "skillUnit", "mistakePoint"];
