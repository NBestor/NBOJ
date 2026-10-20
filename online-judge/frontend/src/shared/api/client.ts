import type {
  Assignment,
  AssignmentReadiness,
  AssignmentOverview,
  AiStandardLibraryItem,
  AiStandardLibraryItemPayload,
  AiStandardLibraryLayer,
  AiStandardLibraryGrowthCandidate,
  AiStandardLibraryGrowthCandidatePayload,
  AiStandardLibraryGrowthGovernanceSummary,
  AiSmoke,
  AiQualityOverview,
  AiQualityTrend,
  AuthSession,
  ClassGroup,
  ClassLearningOverview,
  CoachPrompt,
  CodeRunResult,
  DiagnosisEvalCandidates,
  DiagnosisEvalFixtureDraft,
  DiagnosisTag,
  ExecutorStatus,
  InformaticsKnowledgeNode,
  ImportCommit,
  ImportPreview,
  LeaderboardEntry,
  LearningProof,
  Problem,
  ProblemCatalogItem,
  ProblemManage,
  Readiness,
  RecommendationEffectiveness,
  RecommendationActionEvidenceSignal,
  StudentAbilityProfile,
  StudentAssignmentLeaderboard,
  StudentAssignmentSubmissionPage,
  StudentIdentityAudit,
  StudentRecommendation,
  StudentProfile,
  StudentTrajectory,
  StudentAiFeedbackObservability,
  SubmissionAnalysisLookup,
  SubmissionHistorySummary,
  SubmissionResult,
  StudentAiFeedbackLookup,
  TeacherAccount,
  TeacherAiUsage,
  TeacherDiagnosisCorrection,
  SchoolSummary,
  CreatedSchool,
  SchoolAdminOverview,
  SchoolTeachingClass,
  SchoolTeachingStudent,
  SchoolTeachingAssignment,
  SchoolTeachingSubmission,
  SchoolQuotaSummary,
  TeacherProblemLearningProof,
  TeacherSubmissionEvidence
} from "./types";
import { YINGQI_SIGNATURE } from "../identity/yingqiSignature";

export class ApiError extends Error {
  status: number;
  payload: unknown;

  constructor(message: string, status: number, payload: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.payload = payload;
  }
}

const publicBasePath = import.meta.env.BASE_URL.replace(/\/$/, "");

export function publicApiUrl(url: string): string {
  return url === "/api" || url.startsWith("/api/") ? `${publicBasePath}${url}` : url;
}

async function readJson<T>(response: Response): Promise<T> {
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const message =
      typeof payload === "object" && payload && "error" in payload
        ? String((payload as { error?: unknown }).error)
        : typeof payload === "object" && payload && "message" in payload
          ? String((payload as { message?: unknown }).message)
          : response.status >= 500
            ? "服务暂时不可用"
            : "操作未完成";
    throw new ApiError(message, response.status, payload);
  }
  return payload as T;
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set(YINGQI_SIGNATURE.headers.owner, YINGQI_SIGNATURE.owner);
  headers.set(YINGQI_SIGNATURE.headers.signature, YINGQI_SIGNATURE.fingerprint);
  headers.set(YINGQI_SIGNATURE.headers.claim, YINGQI_SIGNATURE.claim);
  const hasBody = init?.body !== undefined && init.body !== null;
  if (hasBody && !headers.has("Content-Type") && !(init?.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  const method = (init?.method || "GET").toUpperCase();
  if (!["GET", "HEAD", "OPTIONS"].includes(method) && !headers.has("X-XSRF-TOKEN")) {
    const csrfToken = document.cookie.split(";")
      .map(item => item.trim())
      .find(item => item.startsWith("XSRF-TOKEN="))
      ?.slice("XSRF-TOKEN=".length);
    if (csrfToken) headers.set("X-XSRF-TOKEN", decodeURIComponent(csrfToken));
  }
  const response = await fetch(publicApiUrl(url), { ...init, headers, credentials: "same-origin" });
  return readJson<T>(response);
}

function jsonBody(payload: unknown): string {
  return JSON.stringify(payload);
}

function queryString(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      search.set(key, String(value));
    }
  });
  const value = search.toString();
  return value ? `?${value}` : "";
}

export const api = {
  accountSession: (signal?: AbortSignal) => request<AuthSession>("/api/auth/account/session", { signal }),
  accountLogin: (username: string, password: string, portal: "TEACHER" | "SCHOOL_ADMIN" | "PLATFORM_ADMIN") =>
    request<AuthSession>("/api/auth/account/login", { method: "POST", body: jsonBody({ username, password, portal }) }),
  accountLogout: () => request<AuthSession>("/api/auth/account/logout", { method: "POST" }),
  accountChangePassword: (currentPassword: string, newPassword: string) =>
    request<AuthSession>("/api/auth/account/change-password", { method: "POST", body: jsonBody({ currentPassword, newPassword }) }),
  teacherSession: () => request<AuthSession>("/api/auth/account/session"),
  teacherRegister: (payload: { username: string; password: string; displayName: string; schoolRegistrationCode: string }) =>
    request<TeacherAccount>("/api/auth/teacher/register", { method: "POST", body: jsonBody(payload) }),
  teacherLogin: (username: string, password: string) =>
    request<AuthSession>("/api/auth/account/login", { method: "POST", body: jsonBody({ username, password, portal: "TEACHER" }) }),
  teacherLogout: () => request<AuthSession>("/api/auth/account/logout", { method: "POST" }),
  teacherChangePassword: (currentPassword: string, newPassword: string) =>
    request<AuthSession>("/api/auth/account/change-password", {
      method: "POST", body: jsonBody({ currentPassword, newPassword })
    }),

  resolveInvite: (code: string) =>
    request<Assignment>("/api/invites/resolve", {
      method: "POST",
      body: jsonBody({ code })
    }),

  bindStudent: (payload: {
    assignmentId: number;
    classGroupId?: number | null;
    className?: string;
    displayName: string;
    studentNo?: string;
  }) =>
    request<StudentProfile>("/api/student/identity", {
      method: "POST",
      body: jsonBody(payload)
    }),

  loginStudent: (payload: { classCode: string; displayName: string; studentNo: string }) =>
    request<StudentProfile>("/api/auth/student/login", {
      method: "POST",
      body: jsonBody(payload)
    }),
  studentSession: () => request<StudentProfile>("/api/auth/student/session"),
  studentLogout: () => request<void>("/api/auth/student/logout", { method: "POST" }),
  studentAssignments: (studentProfileId: number) => request<Assignment[]>(`/api/student/profile/${studentProfileId}/assignments`),

  studentTrajectory: (assignmentId: number, studentProfileId: number) =>
    request<StudentTrajectory>(`/api/student/assignments/${assignmentId}/profile/${studentProfileId}/trajectory`),
  studentAssignmentLeaderboard: (assignmentId: number) =>
    request<StudentAssignmentLeaderboard>(`/api/student/assignments/${assignmentId}/leaderboard`),
  studentAssignmentSubmissions: (assignmentId: number, params?: {
    problemId?: number | null;
    accepted?: boolean | null;
    verdict?: string | null;
    languageName?: string | null;
    submissionId?: number | null;
    page?: number;
    size?: number;
  }) => request<StudentAssignmentSubmissionPage>(
    `/api/student/assignments/${assignmentId}/submissions${queryString(params || {})}`
  ),
  studentAbilityProfile: (studentProfileId: number) =>
    request<StudentAbilityProfile>(`/api/student/profile/${studentProfileId}/ability-profile`),
  studentRecommendations: (studentProfileId: number) =>
    request<StudentRecommendation>(`/api/student/profile/${studentProfileId}/recommendations`),
  recordRecommendationEvent: (studentProfileId: number, recommendationToken: string, eventType = "CLICKED") =>
    request<void>(`/api/student/profile/${studentProfileId}/recommendation-clicks`, {
      method: "POST",
      body: jsonBody({ recommendationToken, eventType })
    }),

  problems: () => request<Problem[]>("/api/problems"),
  problemCatalog: () => request<ProblemCatalogItem[]>("/api/problems/catalog"),
  problem: (id: number) => request<Problem>(`/api/problems/${id}`),
  problemManage: (id: number) => request<ProblemManage>(`/api/problems/${id}/manage`),
  createProblem: (payload: unknown) =>
    request<Problem>("/api/problems", { method: "POST", body: jsonBody(payload) }),
  updateProblem: (id: number, payload: unknown) =>
    request<Problem>(`/api/problems/${id}`, { method: "PUT", body: jsonBody(payload) }),

  codeRun: (payload: {
    problemId: number;
    assignmentId?: number | null;
    languageId: number;
    sourceCode: string;
    stdin: string;
  }) =>
    request<CodeRunResult>("/api/code-runs", {
      method: "POST",
      body: jsonBody(payload)
    }),

  submit: (payload: {
    problemId: number;
    assignmentId?: number | null;
    studentProfileId?: number | null;
    recommendationToken?: string | null;
    languageId: number;
    sourceCode: string;
  }) =>
    request<SubmissionResult>("/api/submissions", {
      method: "POST",
      body: jsonBody(payload)
    }),

  submission: (id: number) => request<SubmissionResult>(`/api/submissions/${id}`),
  submissionAnalysis: (id: number) => request<SubmissionAnalysisLookup>(`/api/submissions/${id}/analysis`),
  triggerAnalysis: (id: number) =>
    request<SubmissionAnalysisLookup>(`/api/submissions/${id}/analysis`, { method: "POST" }),
  studentAiFeedback: (id: number) => request<StudentAiFeedbackLookup>(`/api/submissions/${id}/student-ai-feedback`),
  triggerStudentAiFeedback: (id: number) =>
    request<StudentAiFeedbackLookup>(`/api/submissions/${id}/student-ai-feedback`, { method: "POST" }),
  recordStudentAiFeedbackView: (id: number) =>
    request<void>(`/api/submissions/${id}/student-ai-feedback/view`, { method: "POST" }),
  coachPrompt: (id: number) => request<CoachPrompt | null>(`/api/submissions/${id}/coach-prompt`),
  generateCoachPrompt: (id: number) =>
    request<CoachPrompt>(`/api/submissions/${id}/coach-prompt`, { method: "POST" }),
  replyCoachPrompt: (id: number, answer: string) =>
    request<CoachPrompt>(`/api/submissions/${id}/coach-turns`, {
      method: "POST",
      body: jsonBody({ answer })
    }),
  learningProof: (id: number) => request<LearningProof>(`/api/submissions/${id}/learning-proof`),
  createLearningReflection: (id: number) =>
    request<LearningProof>(`/api/submissions/${id}/learning-reflection`, { method: "POST" }),
  answerLearningReflection: (id: number, answer: string) =>
    request<LearningProof>(`/api/submissions/${id}/learning-reflection-answer`, {
      method: "POST",
      body: jsonBody({ answer })
    }),
  history: (problemId: number, assignmentId?: number | null) =>
    request<SubmissionHistorySummary[]>(
      `/api/submissions/problem/${problemId}/history-summary${queryString({ assignmentId })}`
    ),

  classes: () => request<ClassGroup[]>("/api/teacher/classes"),
  classLearningOverview: (id: number) => request<ClassLearningOverview>(`/api/teacher/classes/${id}/learning-overview`),
  createClass: (payload: { name: string; grade?: string; teacherName?: string }) =>
    request<ClassGroup>("/api/teacher/classes", { method: "POST", body: jsonBody(payload) }),
  rotateClassJoinCode: (classGroupId: number) =>
    request<ClassGroup>(`/api/teacher/classes/${classGroupId}/join-code/rotate`, { method: "POST" }),
  classRoster: (classGroupId: number) =>
    request<StudentProfile[]>(`/api/teacher/classes/${classGroupId}/students`),
  updateRosterStatus: (classGroupId: number, studentProfileId: number, status: "ACTIVE" | "INACTIVE" | "NEEDS_REVIEW") =>
    request<StudentProfile>(`/api/teacher/classes/${classGroupId}/students/${studentProfileId}/status`, {
      method: "PUT", body: jsonBody({ status })
    }),
  studentIdentityAudit: (classGroupId: number) =>
    request<StudentIdentityAudit>(`/api/teacher/classes/${classGroupId}/identity-audit`),
  mergeStudentIdentities: (classGroupId: number, payload: { studentProfileIds: number[]; targetStudentProfileId?: number | null }) =>
    request<StudentIdentityAudit>(`/api/teacher/classes/${classGroupId}/identity-merge`, {
      method: "POST",
      body: jsonBody(payload)
    }),
  splitStudentIdentity: (classGroupId: number, payload: { studentProfileId: number }) =>
    request<StudentIdentityAudit>(`/api/teacher/classes/${classGroupId}/identity-split`, {
      method: "POST",
      body: jsonBody(payload)
    }),

  assignments: () => request<Assignment[]>("/api/teacher/assignments"),
  assignment: (id: number) => request<Assignment>(`/api/teacher/assignments/${id}`),
  createAssignment: (payload: unknown) =>
    request<Assignment>("/api/teacher/assignments", { method: "POST", body: jsonBody(payload) }),
  assignmentReadiness: (problemIds: number[]) =>
    request<AssignmentReadiness>("/api/teacher/assignments/readiness", {
      method: "POST",
      body: jsonBody({ problemIds })
    }),
  updateAssignment: (id: number, payload: unknown) =>
    request<Assignment>(`/api/teacher/assignments/${id}`, { method: "PUT", body: jsonBody(payload) }),
  teacherProblems: () => request<ProblemManage[]>("/api/teacher/problems"),
  submitProblemReview: (id: number) =>
    request<ProblemManage>(`/api/teacher/problems/${id}/submit-review`, { method: "POST" }),
  reviseProblem: (id: number) =>
    request<ProblemManage>(`/api/teacher/problems/${id}/revise`, { method: "POST" }),
  teacherUsage: () => request<TeacherAiUsage>("/api/teacher/usage/current"),
  adminTeacherAccounts: (status: TeacherAccount["status"] = "PENDING") =>
    request<TeacherAccount[]>(`/api/admin/teacher-applications${queryString({ status })}`),
  approveTeacher: (id: string) =>
    request<TeacherAccount>(`/api/admin/teacher-applications/${id}/approve`, { method: "POST" }),
  rejectTeacher: (id: string, reason: string) =>
    request<TeacherAccount>(`/api/admin/teacher-applications/${id}/reject`, { method: "POST", body: jsonBody({ reason }) }),
  suspendTeacher: (id: string) =>
    request<TeacherAccount>(`/api/admin/teachers/${id}/suspend`, { method: "POST" }),
  restoreTeacher: (id: string) =>
    request<TeacherAccount>(`/api/admin/teachers/${id}/restore`, { method: "POST" }),
  resetTeacherPassword: (id: string) =>
    request<{ temporaryPassword: string; mustChangePassword: boolean }>(`/api/admin/teachers/${id}/reset-password`, { method: "POST" }),
  adjustTeacherQuota: (id: string, baseUnits: number, additionalUnits: number) =>
    request<TeacherAiUsage>(`/api/admin/teachers/${id}/quota`, { method: "PUT", body: jsonBody({ baseUnits, additionalUnits }) }),
  transferTeacherOwnership: (sourceId: string, targetTeacherId: string) =>
    request<{ sourceTeacherId: string; targetTeacherId: string; classCount: number; assignmentCount: number; problemCount: number }>(
      `/api/admin/teachers/${sourceId}/transfer-ownership`, { method: "POST", body: jsonBody({ targetTeacherId }) }
    ),
  adminProblemReviews: () => request<ProblemManage[]>("/api/admin/problem-reviews"),
  approveProblemReview: (id: number) =>
    request<ProblemManage>(`/api/admin/problem-reviews/${id}/approve`, { method: "POST" }),
  rejectProblemReview: (id: number, reason: string) =>
    request<ProblemManage>(`/api/admin/problem-reviews/${id}/reject`, { method: "POST", body: jsonBody({ reason }) }),
  publishProblemPublic: (id: number) =>
    request<ProblemManage>(`/api/admin/problem-reviews/${id}/publish-public`, { method: "POST" }),

  platformSchools: () => request<SchoolSummary[]>("/api/platform-admin/schools"),
  createSchool: (payload: { schoolName: string; adminUsername: string; adminDisplayName: string; monthlyAiUnits: number }) =>
    request<CreatedSchool>("/api/platform-admin/schools", { method: "POST", body: jsonBody(payload) }),
  setSchoolQuota: (id: string, baseUnits: number, additionalUnits = 0) =>
    request<SchoolQuotaSummary>(`/api/platform-admin/schools/${id}/quota`, { method: "PUT", body: jsonBody({ baseUnits, additionalUnits }) }),
  suspendSchool: (id: string) => request<SchoolSummary>(`/api/platform-admin/schools/${id}/suspend`, { method: "POST" }),
  restoreSchool: (id: string) => request<SchoolSummary>(`/api/platform-admin/schools/${id}/restore`, { method: "POST" }),
  resetSchoolAdminPassword: (id: string) => request<{ temporaryPassword: string }>(`/api/platform-admin/schools/${id}/admin/reset-password`, { method: "POST" }),
  replaceSchoolAdmin: (id: string, payload: { username: string; displayName: string }) =>
    request<CreatedSchool>(`/api/platform-admin/schools/${id}/admin/replace`, { method: "POST", body: jsonBody(payload) }),
  platformProblemReviews: () => request<ProblemManage[]>("/api/platform-admin/problem-reviews"),
  approvePlatformProblemReview: (id: number) => request<ProblemManage>(`/api/platform-admin/problem-reviews/${id}/approve`, { method: "POST" }),
  rejectPlatformProblemReview: (id: number, reason: string) => request<ProblemManage>(`/api/platform-admin/problem-reviews/${id}/reject`, { method: "POST", body: jsonBody({ reason }) }),
  publishPlatformProblemPublic: (id: number) => request<ProblemManage>(`/api/platform-admin/problem-reviews/${id}/publish-public`, { method: "POST" }),

  schoolAdminOverview: () => request<SchoolAdminOverview>("/api/school-admin/overview"),
  schoolTeacherApplications: (status: TeacherAccount["status"] = "PENDING") =>
    request<TeacherAccount[]>(`/api/school-admin/teacher-applications${queryString({ status })}`),
  schoolApproveTeacher: (id: string) => request<TeacherAccount>(`/api/school-admin/teacher-applications/${id}/approve`, { method: "POST" }),
  schoolRejectTeacher: (id: string, reason: string) => request<TeacherAccount>(`/api/school-admin/teacher-applications/${id}/reject`, { method: "POST", body: jsonBody({ reason }) }),
  schoolSuspendTeacher: (id: string) => request<TeacherAccount>(`/api/school-admin/teachers/${id}/suspend`, { method: "POST" }),
  schoolRestoreTeacher: (id: string) => request<TeacherAccount>(`/api/school-admin/teachers/${id}/restore`, { method: "POST" }),
  schoolResetTeacherPassword: (id: string) => request<{ temporaryPassword: string }>(`/api/school-admin/teachers/${id}/reset-password`, { method: "POST" }),
  schoolSetTeacherQuota: (id: string, units: number) => request<TeacherAiUsage>(`/api/school-admin/teachers/${id}/quota`, { method: "PUT", body: jsonBody({ baseUnits: units, additionalUnits: 0 }) }),
  rotateSchoolRegistrationCode: () => request<{ schoolRegistrationCode: string }>("/api/school-admin/registration-code/rotate", { method: "POST" }),
  schoolTeachingClasses: () => request<SchoolTeachingClass[]>("/api/school-admin/teaching/classes"),
  schoolTeachingStudents: (classId: number) => request<SchoolTeachingStudent[]>(`/api/school-admin/teaching/classes/${classId}/students`),
  schoolTeachingAssignments: (classId: number) => request<SchoolTeachingAssignment[]>(`/api/school-admin/teaching/classes/${classId}/assignments`),
  schoolTeachingSubmissions: (assignmentId: number) => request<SchoolTeachingSubmission[]>(`/api/school-admin/teaching/assignments/${assignmentId}/submissions`),
  rotateInvite: (id: number) =>
    request<Assignment>(`/api/teacher/assignments/${id}/invite`, { method: "POST" }),
  assignmentOverview: (id: number) => request<AssignmentOverview>(`/api/teacher/assignments/${id}/overview`),
  teacherStudentProblemGrowth: (assignmentId: number, problemId: number, studentProfileId: number) =>
    request<SubmissionHistorySummary[]>(
      `/api/teacher/assignments/${assignmentId}/problems/${problemId}/students/${studentProfileId}/growth`
    ),
  teacherProblemLearningProof: (assignmentId: number, problemId: number) =>
    request<TeacherProblemLearningProof>(
      `/api/teacher/assignments/${assignmentId}/problems/${problemId}/learning-proof`
    ),
  teacherSubmissionEvidence: (assignmentId: number, submissionId: number) =>
    request<TeacherSubmissionEvidence>(`/api/teacher/assignments/${assignmentId}/submissions/${submissionId}/evidence`),
  regenerateTeacherSubmissionAnalysis: (assignmentId: number, submissionId: number) =>
    request<void>(`/api/teacher/assignments/${assignmentId}/submissions/${submissionId}/analysis/regenerate`, { method: "POST" }),
  aiQualityOverview: (id: number) => request<AiQualityOverview>(`/api/teacher/assignments/${id}/ai-quality`),
  studentAiFeedbackObservability: (id: number) =>
    request<StudentAiFeedbackObservability>(`/api/teacher/assignments/${id}/student-ai-feedback-observability`),
  diagnosisEvalCandidates: (id: number) =>
    request<DiagnosisEvalCandidates>(`/api/teacher/assignments/${id}/diagnosis-eval-candidates`),
  diagnosisEvalFixtureDraft: (id: number) =>
    request<DiagnosisEvalFixtureDraft>(`/api/teacher/assignments/${id}/diagnosis-eval-fixture-draft`),
  aiQualityTrend: () => request<AiQualityTrend>("/api/teacher/ai-quality/trend"),
  recommendationEffectiveness: () => request<RecommendationEffectiveness>("/api/teacher/recommendations/effectiveness"),
  recommendationInterventions: () =>
    request<RecommendationActionEvidenceSignal[]>("/api/teacher/recommendations/interventions"),
  recordClassReviewFeedback: (
    assignmentId: number,
    payload: {
      suggestionKey: string;
      actionType: "ACCEPTED" | "DISMISSED" | "MODIFIED";
      targetAbility?: string | null;
      exampleProblemId?: number | null;
      evidenceTags?: string[];
      teacherNote?: string;
      createdBy?: string;
    }
  ) =>
    request<void>(`/api/teacher/assignments/${assignmentId}/class-review-feedback`, {
      method: "POST",
      body: jsonBody(payload)
    }),
  diagnosisTags: () => request<DiagnosisTag[]>("/api/teacher/diagnosis-tags"),
  correctDiagnosis: (
    assignmentId: number,
    payload: {
      submissionId: number;
      feedbackRevisionId?: number | null;
      correctedIssueTag: string;
      correctedFineGrainedTag?: string | null;
      correctionType?: "DIAGNOSIS" | "KNOWLEDGE_PATH" | "EVIDENCE" | "ADVICE";
      targetIssueId?: string | null;
      correctedKnowledgePath?: string | null;
      targetEvidenceRef?: string | null;
      teacherNote?: string;
      evalCandidate?: boolean;
      correctedBy?: string;
    }
  ) =>
    request<TeacherDiagnosisCorrection>(`/api/teacher/assignments/${assignmentId}/diagnosis-corrections`, {
      method: "POST",
      body: jsonBody(payload)
    }),

  classImportPreview: (payload: unknown) =>
    request<ImportPreview>("/api/teacher/classes/import-preview", { method: "POST", body: jsonBody(payload) }),
  classImportCommit: (payload: unknown) =>
    request<ImportCommit>("/api/teacher/classes/import-commit", { method: "POST", body: jsonBody(payload) }),
  problemImportPreview: (payload: unknown) =>
    request<ImportPreview>("/api/teacher/problems/import-preview", { method: "POST", body: jsonBody(payload) }),
  problemImportCommit: (payload: unknown) =>
    request<ImportCommit>("/api/teacher/problems/import-commit", { method: "POST", body: jsonBody(payload) }),

  aiStandardLibraryItems: (params?: {
    layer?: AiStandardLibraryLayer | "";
    category?: string;
    enabled?: boolean | "";
    query?: string;
  }) => request<AiStandardLibraryItem[]>(`/api/teacher/ai-standard-library/items${queryString(params || {})}`),
  aiStandardLibraryItem: (id: number) => request<AiStandardLibraryItem>(`/api/teacher/ai-standard-library/items/${id}`),
  createAiStandardLibraryItem: (payload: AiStandardLibraryItemPayload) =>
    request<AiStandardLibraryItem>("/api/teacher/ai-standard-library/items", { method: "POST", body: jsonBody(payload) }),
  updateAiStandardLibraryItem: (id: number, payload: AiStandardLibraryItemPayload) =>
    request<AiStandardLibraryItem>(`/api/teacher/ai-standard-library/items/${id}`, { method: "PUT", body: jsonBody(payload) }),
  enableAiStandardLibraryItem: (id: number) =>
    request<AiStandardLibraryItem>(`/api/teacher/ai-standard-library/items/${id}/enable`, { method: "POST" }),
  disableAiStandardLibraryItem: (id: number) =>
    request<AiStandardLibraryItem>(`/api/teacher/ai-standard-library/items/${id}/disable`, { method: "POST" }),
  aiStandardLibraryGrowthCandidates: () =>
    request<AiStandardLibraryGrowthCandidate[]>("/api/teacher/ai-standard-library/growth-candidates"),
  aiStandardLibraryGrowthGovernanceSummary: () =>
    request<AiStandardLibraryGrowthGovernanceSummary>("/api/teacher/ai-standard-library/growth-candidates/governance-summary"),
  updateAiStandardLibraryGrowthCandidate: (id: number, payload: AiStandardLibraryGrowthCandidatePayload) =>
    request<AiStandardLibraryGrowthCandidate>(`/api/teacher/ai-standard-library/growth-candidates/${id}`, { method: "PUT", body: jsonBody(payload) }),
  ignoreAiStandardLibraryGrowthCandidate: (id: number, payload?: AiStandardLibraryGrowthCandidatePayload) =>
    request<AiStandardLibraryGrowthCandidate>(`/api/teacher/ai-standard-library/growth-candidates/${id}/ignore`, { method: "POST", body: jsonBody(payload || {}) }),
  rejectAiStandardLibraryGrowthCandidate: (id: number, payload?: AiStandardLibraryGrowthCandidatePayload) =>
    request<AiStandardLibraryGrowthCandidate>(`/api/teacher/ai-standard-library/growth-candidates/${id}/reject`, { method: "POST", body: jsonBody(payload || {}) }),
  approveAiStandardLibraryGrowthCandidate: (id: number, payload?: AiStandardLibraryGrowthCandidatePayload) =>
    request<AiStandardLibraryGrowthCandidate>(`/api/teacher/ai-standard-library/growth-candidates/${id}/approve`, { method: "POST", body: jsonBody(payload || {}) }),
  mergeAiStandardLibraryGrowthCandidate: (id: number, payload?: AiStandardLibraryGrowthCandidatePayload) =>
    request<AiStandardLibraryGrowthCandidate>(`/api/teacher/ai-standard-library/growth-candidates/${id}/merge`, { method: "POST", body: jsonBody(payload || {}) }),
  informaticsKnowledgeTree: () => request<InformaticsKnowledgeNode[]>("/api/teacher/informatics-knowledge/tree"),

  executorStatus: () => request<ExecutorStatus>("/api/system/executor-status"),
  readiness: () => request<Readiness>("/api/system/readiness"),
  aiSmoke: () => request<AiSmoke>("/api/system/ai-smoke", { method: "POST" }),
  classOverview: () => request<LeaderboardEntry[]>("/api/leaderboard/problems")
};
