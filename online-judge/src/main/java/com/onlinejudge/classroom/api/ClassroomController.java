package com.onlinejudge.classroom.api;

import com.onlinejudge.classroom.application.ClassroomService;
import com.onlinejudge.classroom.application.AssignmentReadinessService;
import com.onlinejudge.classroom.application.AiQualityOverviewService;
import com.onlinejudge.classroom.application.AiQualityTrendService;
import com.onlinejudge.classroom.application.ClassReviewFeedbackService;
import com.onlinejudge.classroom.application.RecommendationEffectivenessService;
import com.onlinejudge.classroom.application.StudentAbilityProfileService;
import com.onlinejudge.classroom.application.StudentIdentityAdminService;
import com.onlinejudge.classroom.application.StudentIdentityAuditService;
import com.onlinejudge.classroom.application.StudentRecommendationEventService;
import com.onlinejudge.classroom.application.StudentRecommendationService;
import com.onlinejudge.classroom.application.StudentAiFeedbackObservabilityService;
import com.onlinejudge.classroom.application.StudentTrajectoryService;
import com.onlinejudge.classroom.application.TeacherSubmissionEvidenceService;
import com.onlinejudge.classroom.application.TeacherLearningAnalysisService;
import com.onlinejudge.classroom.application.LearningProofService;
import com.onlinejudge.submission.application.SubmissionEvidenceBackfillService;
import com.onlinejudge.submission.application.SubmissionAnalysisService;
import com.onlinejudge.submission.application.StudentAiFeedbackAsyncService;
import com.onlinejudge.submission.dto.SubmissionEvidenceBackfillResponse;
import com.onlinejudge.submission.dto.SubmissionHistorySummaryResponse;
import com.onlinejudge.classroom.dto.*;
import com.onlinejudge.classroom.importing.ClassroomImportService;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import com.onlinejudge.identity.application.CurrentTeacherContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;
    private final AssignmentReadinessService assignmentReadinessService;
    private final AiQualityOverviewService aiQualityOverviewService;
    private final AiQualityTrendService aiQualityTrendService;
    private final ClassReviewFeedbackService classReviewFeedbackService;
    private final RecommendationEffectivenessService recommendationEffectivenessService;
    private final StudentAiFeedbackObservabilityService studentAiFeedbackObservabilityService;
    private final ClassroomImportService classroomImportService;
    private final StudentTrajectoryService studentTrajectoryService;
    private final StudentAbilityProfileService studentAbilityProfileService;
    private final StudentIdentityAdminService studentIdentityAdminService;
    private final StudentIdentityAuditService studentIdentityAuditService;
    private final StudentRecommendationService studentRecommendationService;
    private final StudentRecommendationEventService studentRecommendationEventService;
    private final DiagnosisTaxonomy diagnosisTaxonomy;
    private final StudentAccessTokenService studentAccessTokenService;
    private final SubmissionEvidenceBackfillService submissionEvidenceBackfillService;
    private final SubmissionAnalysisService submissionAnalysisService;
    private final CurrentTeacherContext currentTeacherContext;
    private final TeacherSubmissionEvidenceService teacherSubmissionEvidenceService;
    private final TeacherLearningAnalysisService teacherLearningAnalysisService;
    private final StudentAiFeedbackAsyncService studentAiFeedbackAsyncService;
    private final LearningProofService learningProofService;

    @GetMapping("/api/teacher/classes")
    public ResponseEntity<List<ClassGroupResponse>> getClasses() {
        return ResponseEntity.ok(classroomService.getClassGroups());
    }

    @PostMapping("/api/teacher/classes")
    public ResponseEntity<ClassGroupResponse> createClass(@Valid @RequestBody CreateClassGroupRequest request) {
        return ResponseEntity.ok(classroomService.createClassGroup(request));
    }

    @PostMapping("/api/teacher/classes/{classGroupId}/join-code/rotate")
    public ResponseEntity<ClassGroupResponse> rotateClassJoinCode(@PathVariable Long classGroupId) {
        return ResponseEntity.ok(classroomService.rotateClassJoinCode(classGroupId));
    }

    @GetMapping("/api/teacher/classes/{classGroupId}/students")
    public ResponseEntity<List<StudentProfileResponse>> getClassRoster(@PathVariable Long classGroupId) {
        return ResponseEntity.ok(classroomService.getClassRoster(classGroupId));
    }

    @PutMapping("/api/teacher/classes/{classGroupId}/students/{studentProfileId}/status")
    public ResponseEntity<StudentProfileResponse> updateRosterStatus(@PathVariable Long classGroupId,
                                                                      @PathVariable Long studentProfileId,
                                                                      @Valid @RequestBody UpdateRosterStatusRequest request) {
        return ResponseEntity.ok(classroomService.updateRosterStatus(classGroupId, studentProfileId, request.status()));
    }

    @GetMapping("/api/teacher/classes/{classGroupId}/identity-audit")
    public ResponseEntity<StudentIdentityAuditResponse> getStudentIdentityAudit(@PathVariable Long classGroupId) {
        classroomService.findOwnedClass(classGroupId);
        return ResponseEntity.ok(studentIdentityAuditService.auditClass(classGroupId));
    }

    @GetMapping("/api/teacher/classes/{classGroupId}/learning-overview")
    public ResponseEntity<ClassLearningOverviewResponse> getClassLearningOverview(@PathVariable Long classGroupId) {
        return ResponseEntity.ok(teacherLearningAnalysisService.getClassOverview(classGroupId));
    }

    @PostMapping("/api/teacher/classes/{classGroupId}/identity-merge")
    public ResponseEntity<StudentIdentityAuditResponse> mergeStudentIdentities(@PathVariable Long classGroupId,
                                                                               @Valid @RequestBody StudentIdentityMergeRequest request) {
        classroomService.findOwnedClass(classGroupId);
        return ResponseEntity.ok(studentIdentityAdminService.mergeProfiles(classGroupId, request));
    }

    @PostMapping("/api/teacher/classes/{classGroupId}/identity-split")
    public ResponseEntity<StudentIdentityAuditResponse> splitStudentIdentity(@PathVariable Long classGroupId,
                                                                             @Valid @RequestBody StudentIdentitySplitRequest request) {
        classroomService.findOwnedClass(classGroupId);
        return ResponseEntity.ok(studentIdentityAdminService.splitProfile(classGroupId, request));
    }

    @GetMapping("/api/teacher/assignments")
    public ResponseEntity<List<AssignmentResponse>> getAssignments() {
        return ResponseEntity.ok(classroomService.getAssignments());
    }

    @GetMapping("/api/teacher/assignments/{assignmentId}")
    public ResponseEntity<AssignmentResponse> getAssignment(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(classroomService.getAssignment(assignmentId));
    }

    @PostMapping("/api/teacher/assignments")
    public ResponseEntity<AssignmentResponse> createAssignment(@Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.ok(classroomService.createAssignment(request));
    }

    @PostMapping("/api/teacher/assignments/readiness")
    public ResponseEntity<AssignmentReadinessResponse> inspectAssignmentReadiness(
            @Valid @RequestBody AssignmentReadinessRequest request
    ) {
        return ResponseEntity.ok(assignmentReadinessService.inspect(request.getProblemIds()));
    }

    @PutMapping("/api/teacher/assignments/{assignmentId}")
    public ResponseEntity<AssignmentResponse> updateAssignment(@PathVariable Long assignmentId,
                                                               @Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.ok(classroomService.updateAssignment(assignmentId, request));
    }

    @PostMapping("/api/teacher/assignments/{assignmentId}/invite")
    public ResponseEntity<AssignmentResponse> rotateInvite(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(classroomService.rotateInvite(assignmentId));
    }

    @PostMapping("/api/teacher/classes/import-preview")
    public ResponseEntity<ImportPreviewResponse> previewClassImport(@RequestBody ImportRequest request) {
        return ResponseEntity.ok(classroomImportService.previewStudents(request));
    }

    @PostMapping("/api/teacher/classes/import-commit")
    public ResponseEntity<ImportCommitResponse> commitClassImport(@RequestBody ImportRequest request) {
        return ResponseEntity.ok(classroomImportService.commitStudents(request));
    }

    @PostMapping("/api/teacher/problems/import-preview")
    public ResponseEntity<ImportPreviewResponse> previewProblemImport(@RequestBody ImportRequest request) {
        return ResponseEntity.ok(classroomImportService.previewProblems(request));
    }

    @PostMapping("/api/teacher/problems/import-commit")
    public ResponseEntity<ImportCommitResponse> commitProblemImport(@RequestBody ImportRequest request) {
        return ResponseEntity.ok(classroomImportService.commitProblems(request));
    }

    @GetMapping("/api/teacher/assignments/{assignmentId}/overview")
    public ResponseEntity<AssignmentOverviewResponse> getAssignmentOverview(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(classroomService.getAssignmentOverview(assignmentId));
    }

    @GetMapping("/api/teacher/assignments/{assignmentId}/problems/{problemId}/students/{studentProfileId}/growth")
    public ResponseEntity<List<SubmissionHistorySummaryResponse>> getStudentProblemGrowth(
            @PathVariable Long assignmentId,
            @PathVariable Long problemId,
            @PathVariable Long studentProfileId) {
        classroomService.findOwnedAssignment(assignmentId);
        return ResponseEntity.ok(submissionAnalysisService.getSubmissionHistorySummaries(
                problemId,
                assignmentId,
                studentProfileId
        ));
    }

    @GetMapping("/api/teacher/assignments/{assignmentId}/problems/{problemId}/learning-proof")
    public ResponseEntity<TeacherProblemLearningProofResponse> getTeacherProblemLearningProof(
            @PathVariable Long assignmentId,
            @PathVariable Long problemId) {
        return ResponseEntity.ok(learningProofService.getTeacherProblemProof(assignmentId, problemId));
    }

    @GetMapping("/api/teacher/assignments/{assignmentId}/submissions/{submissionId}/evidence")
    public ResponseEntity<TeacherSubmissionEvidenceResponse> getTeacherSubmissionEvidence(
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId) {
        return ResponseEntity.ok(teacherSubmissionEvidenceService.getEvidence(assignmentId, submissionId));
    }

    @PostMapping("/api/teacher/assignments/{assignmentId}/submissions/{submissionId}/analysis/regenerate")
    public ResponseEntity<Void> regenerateTeacherSubmissionAnalysis(
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId) {
        teacherSubmissionEvidenceService.requireSubmissionAccess(assignmentId, submissionId);
        studentAiFeedbackAsyncService.enqueueRegeneration(submissionId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/teacher/submission-evidence/backfill-preview")
    public ResponseEntity<SubmissionEvidenceBackfillResponse> previewSubmissionEvidenceBackfill(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer batchSize) {
        currentTeacherContext.requireAdmin();
        return ResponseEntity.ok(submissionEvidenceBackfillService.preview(cursor, batchSize));
    }

    @PostMapping("/api/teacher/submission-evidence/backfill")
    public ResponseEntity<SubmissionEvidenceBackfillResponse> backfillSubmissionEvidence(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer batchSize) {
        currentTeacherContext.requireAdmin();
        return ResponseEntity.ok(submissionEvidenceBackfillService.backfill(cursor, batchSize));
    }

    /*
     * AI quality, feedback observability, and diagnosis eval endpoints are system-detail support.
     * They should not drive the teacher's primary classroom workflow or expose engineering terms in main UI copy.
     */
    @GetMapping("/api/teacher/assignments/{assignmentId}/ai-quality")
    public ResponseEntity<AiQualityOverviewResponse> getAiQualityOverview(@PathVariable Long assignmentId) {
        classroomService.findOwnedAssignment(assignmentId);
        return ResponseEntity.ok(aiQualityOverviewService.buildOverview(assignmentId));
    }

    @GetMapping("/api/teacher/assignments/{assignmentId}/student-ai-feedback-observability")
    public ResponseEntity<StudentAiFeedbackObservabilityResponse> getStudentAiFeedbackObservability(@PathVariable Long assignmentId) {
        classroomService.findOwnedAssignment(assignmentId);
        return ResponseEntity.ok(studentAiFeedbackObservabilityService.buildForAssignment(assignmentId));
    }

    @GetMapping("/api/teacher/ai-quality/trend")
    public ResponseEntity<AiQualityTrendResponse> getAiQualityTrend() {
        return ResponseEntity.ok(aiQualityTrendService.buildTrend());
    }

    @GetMapping("/api/teacher/recommendations/effectiveness")
    public ResponseEntity<RecommendationEffectivenessResponse> getRecommendationEffectiveness() {
        return ResponseEntity.ok(recommendationEffectivenessService.buildOverview());
    }

    @GetMapping("/api/teacher/recommendations/interventions")
    public ResponseEntity<List<RecommendationEffectivenessResponse.ActionEvidenceSignal>> getRecommendationInterventions() {
        return ResponseEntity.ok(recommendationEffectivenessService.buildInterventionQueue());
    }

    @PostMapping("/api/teacher/assignments/{assignmentId}/class-review-feedback")
    public ResponseEntity<ClassReviewFeedbackResponse> recordClassReviewFeedback(@PathVariable Long assignmentId,
                                                                                 @Valid @RequestBody ClassReviewFeedbackRequest request) {
        classroomService.findOwnedAssignment(assignmentId);
        return ResponseEntity.ok(classReviewFeedbackService.recordFeedback(assignmentId, request));
    }

    @PostMapping("/api/teacher/assignments/{assignmentId}/diagnosis-corrections")
    public ResponseEntity<TeacherDiagnosisCorrectionResponse> correctDiagnosis(@PathVariable Long assignmentId,
                                                                               @Valid @RequestBody TeacherDiagnosisCorrectionRequest request) {
        return ResponseEntity.ok(classroomService.correctDiagnosis(assignmentId, request));
    }

    @GetMapping("/api/teacher/assignments/{assignmentId}/diagnosis-eval-candidates")
    public ResponseEntity<DiagnosisEvalCandidateResponse> getDiagnosisEvalCandidates(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(classroomService.getDiagnosisEvalCandidates(assignmentId));
    }

    @GetMapping("/api/teacher/assignments/{assignmentId}/diagnosis-eval-fixture-draft")
    public ResponseEntity<DiagnosisEvalFixtureDraftResponse> exportDiagnosisEvalFixtureDraft(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(classroomService.exportDiagnosisEvalFixtureDraft(assignmentId));
    }

    @GetMapping("/api/teacher/diagnosis-tags")
    public ResponseEntity<List<DiagnosisTagResponse>> getDiagnosisTags() {
        return ResponseEntity.ok(diagnosisTaxonomy.allTags()
                .stream()
                .map(DiagnosisTagResponse::from)
                .toList());
    }

    @PostMapping("/api/invites/resolve")
    public ResponseEntity<AssignmentResponse> resolveInvite(@Valid @RequestBody InviteResolveRequest request) {
        return ResponseEntity.ok(classroomService.resolveInvite(request.getCode()));
    }

    @GetMapping("/api/student/assignments/{inviteCode}")
    public ResponseEntity<AssignmentResponse> getStudentAssignment(@PathVariable String inviteCode) {
        return ResponseEntity.ok(classroomService.resolveInvite(inviteCode));
    }

    @PostMapping("/api/student/identity")
    public ResponseEntity<StudentProfileResponse> bindStudentIdentity(@Valid @RequestBody StudentIdentityRequest request) {
        return ResponseEntity.ok(classroomService.bindStudentIdentity(request));
    }

    @PostMapping("/api/student/login")
    public ResponseEntity<StudentProfileResponse> loginStudent(@Valid @RequestBody StudentLoginRequest request,
                                                               HttpServletRequest httpRequest,
                                                               HttpServletResponse response) {
        StudentProfileResponse student = classroomService.loginStudent(request);
        var profile = new com.onlinejudge.classroom.domain.StudentProfile();
        profile.setId(student.getId());
        profile.setStatus(student.getStatus());
        studentAccessTokenService.issueCookie(profile, httpRequest, response);
        return ResponseEntity.ok(student);
    }

    @GetMapping("/api/student/profile/{studentProfileId}/assignments")
    public ResponseEntity<List<AssignmentResponse>> getStudentAssignments(@PathVariable Long studentProfileId,
                                                                          HttpServletRequest request) {
        studentAccessTokenService.requireStudent(request, studentProfileId);
        return ResponseEntity.ok(classroomService.getStudentAssignments(studentProfileId));
    }

    @GetMapping("/api/student/assignments/{assignmentId}/profile/{studentProfileId}/trajectory")
    public ResponseEntity<StudentTrajectoryResponse> getStudentTrajectory(@PathVariable Long assignmentId,
                                                                          @PathVariable Long studentProfileId,
                                                                          HttpServletRequest request) {
        studentAccessTokenService.requireStudent(request, studentProfileId);
        return ResponseEntity.ok(studentTrajectoryService.buildTrajectory(assignmentId, studentProfileId));
    }

    @GetMapping("/api/student/profile/{studentProfileId}/ability-profile")
    public ResponseEntity<StudentAbilityProfileResponse> getStudentAbilityProfile(@PathVariable Long studentProfileId,
                                                                                  HttpServletRequest request) {
        studentAccessTokenService.requireStudent(request, studentProfileId);
        return ResponseEntity.ok(studentAbilityProfileService.buildProfile(studentProfileId));
    }

    @GetMapping("/api/student/profile/{studentProfileId}/recommendations")
    public ResponseEntity<StudentRecommendationResponse> getStudentRecommendations(@PathVariable Long studentProfileId,
                                                                                  HttpServletRequest request) {
        studentAccessTokenService.requireStudent(request, studentProfileId);
        return ResponseEntity.ok(studentRecommendationService.recommend(studentProfileId));
    }

    @PostMapping("/api/student/profile/{studentProfileId}/recommendation-clicks")
    public ResponseEntity<Void> recordRecommendationClick(@PathVariable Long studentProfileId,
                                                          @Valid @RequestBody RecommendationEventRequest recommendationRequest,
                                                          HttpServletRequest request) {
        studentAccessTokenService.requireStudent(request, studentProfileId);
        if (StudentRecommendationEventService.EVENT_ENTERED_PROBLEM.equals(recommendationRequest.getEventType())) {
            studentRecommendationEventService.recordEnteredProblem(studentProfileId, recommendationRequest.getRecommendationToken());
        } else {
            studentRecommendationEventService.recordClick(studentProfileId, recommendationRequest.getRecommendationToken());
        }
        return ResponseEntity.noContent().build();
    }
}
