package com.onlinejudge.submission.api;

import com.onlinejudge.submission.dto.SubmissionAnalysisLookupResponse;
import com.onlinejudge.submission.dto.SubmissionComparisonResponse;
import com.onlinejudge.submission.dto.SubmissionHistorySummaryResponse;
import com.onlinejudge.submission.dto.SubmissionRequest;
import com.onlinejudge.submission.dto.SubmissionResponse;
import com.onlinejudge.submission.dto.StudentAiFeedbackLookupResponse;
import com.onlinejudge.classroom.application.CoachPromptService;
import com.onlinejudge.classroom.application.LearningProofService;
import com.onlinejudge.classroom.dto.CoachPromptResponse;
import com.onlinejudge.classroom.dto.CoachReplyRequest;
import com.onlinejudge.classroom.dto.LearningProofResponse;
import com.onlinejudge.submission.application.JudgeService;
import com.onlinejudge.submission.application.SubmissionAnalysisService;
import com.onlinejudge.submission.application.SubmissionComparisonService;
import com.onlinejudge.submission.application.StudentAiFeedbackAsyncService;
import com.onlinejudge.submission.application.StudentAiFeedbackService;
import com.onlinejudge.submission.application.ClassroomSubmissionContextService;
import com.onlinejudge.shared.security.AccessDeniedException;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import com.onlinejudge.submission.domain.Submission;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import com.onlinejudge.system.application.TrialMetrics;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final JudgeService judgeService;
    private final SubmissionAnalysisService submissionAnalysisService;
    private final SubmissionComparisonService submissionComparisonService;
    private final StudentAiFeedbackService studentAiFeedbackService;
    private final StudentAiFeedbackAsyncService studentAiFeedbackAsyncService;
    private final CoachPromptService coachPromptService;
    private final LearningProofService learningProofService;
    private final StudentAccessTokenService studentAccessTokenService;
    private final SubmissionRepository submissionRepository;
    private final ClassroomSubmissionContextService classroomSubmissionContextService;
    private final TrialMetrics trialMetrics;

    @PostMapping
    public ResponseEntity<SubmissionResponse> submitCode(@Valid @RequestBody SubmissionRequest request,
                                                         HttpServletRequest httpRequest) {
        SubmissionRequest resolved = classroomSubmissionContextService.resolve(request, httpRequest);
        SubmissionResponse response = judgeService.submitCode(resolved);
        trialMetrics.submissionSucceeded();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResponse> getSubmission(@PathVariable Long id,
                                                            HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        return ResponseEntity.ok(judgeService.getSubmission(id));
    }

    @GetMapping("/{id}/analysis")
    public ResponseEntity<SubmissionAnalysisLookupResponse> getSubmissionAnalysis(@PathVariable Long id,
                                                                                  HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        SubmissionAnalysisLookupResponse lookup = submissionAnalysisService.getSubmissionAnalysisLookup(id);
        if (lookup.getAnalysis() == null) {
            studentAiFeedbackAsyncService.enqueue(id);
            return ResponseEntity.accepted().body(lookup);
        }
        return ResponseEntity.ok(lookup);
    }

    @PostMapping("/{id}/analysis")
    public ResponseEntity<SubmissionAnalysisLookupResponse> triggerSubmissionAnalysis(@PathVariable Long id,
                                                                                      HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        SubmissionAnalysisLookupResponse lookup = submissionAnalysisService.getSubmissionAnalysisLookup(id);
        if (lookup.getAnalysis() == null) {
            studentAiFeedbackAsyncService.enqueue(id);
            return ResponseEntity.accepted().body(lookup);
        }
        return ResponseEntity.ok(lookup);
    }

    @GetMapping("/{id}/student-ai-feedback")
    public ResponseEntity<StudentAiFeedbackLookupResponse> getStudentAiFeedback(@PathVariable Long id,
                                                                                HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        StudentAiFeedbackLookupResponse lookup = studentAiFeedbackService.getLookup(id);
        if ("NOT_REQUESTED".equals(lookup.getStatus())
                || studentAiFeedbackService.isGeneratingExpired(id)
                || studentAiFeedbackService.needsContractRefresh(id)) {
            studentAiFeedbackAsyncService.enqueue(id);
            return ResponseEntity.accepted().body(studentAiFeedbackService.getLookup(id));
        }
        return ResponseEntity.ok(lookup);
    }

    @PostMapping("/{id}/student-ai-feedback")
    public ResponseEntity<StudentAiFeedbackLookupResponse> triggerStudentAiFeedback(@PathVariable Long id,
                                                                                    HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        StudentAiFeedbackLookupResponse lookup = studentAiFeedbackService.getLookup(id);
        if (!"READY".equals(lookup.getStatus()) || studentAiFeedbackService.needsContractRefresh(id)) {
            studentAiFeedbackAsyncService.enqueue(id);
            return ResponseEntity.accepted().body(studentAiFeedbackService.getLookup(id));
        }
        return ResponseEntity.ok(lookup);
    }

    @PostMapping("/{id}/student-ai-feedback/view")
    public ResponseEntity<Void> recordStudentAiFeedbackView(@PathVariable Long id,
                                                            HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        studentAiFeedbackService.recordViewed(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/coach-prompt")
    public ResponseEntity<CoachPromptResponse> getCoachPrompt(@PathVariable Long id,
                                                              HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        CoachPromptResponse prompt = coachPromptService.getLatestPrompt(id);
        if (prompt == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(prompt);
    }

    @PostMapping("/{id}/coach-prompt")
    public ResponseEntity<CoachPromptResponse> generateCoachPrompt(@PathVariable Long id,
                                                                   HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        return ResponseEntity.ok(coachPromptService.generateNextQuestion(id));
    }

    @PostMapping("/{id}/coach-turns")
    public ResponseEntity<CoachPromptResponse> replyCoachPrompt(@PathVariable Long id,
                                                                @Valid @RequestBody CoachReplyRequest coachRequest,
                                                                HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        return ResponseEntity.ok(coachPromptService.replyAndGenerateNext(id, coachRequest));
    }

    @GetMapping("/{id}/learning-proof")
    public ResponseEntity<LearningProofResponse> getLearningProof(@PathVariable Long id,
                                                                  HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        return ResponseEntity.ok(learningProofService.getForSubmission(id));
    }

    @PostMapping("/{id}/learning-reflection")
    public ResponseEntity<LearningProofResponse> createLearningReflection(@PathVariable Long id,
                                                                          HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        return ResponseEntity.ok(learningProofService.createReflection(id));
    }

    @PostMapping("/{id}/learning-reflection-answer")
    public ResponseEntity<LearningProofResponse> answerLearningReflection(
            @PathVariable Long id,
            @Valid @RequestBody CoachReplyRequest coachRequest,
            HttpServletRequest request) {
        requireSubmissionAccess(request, id);
        return ResponseEntity.ok(learningProofService.answerReflection(id, coachRequest));
    }

    @GetMapping("/problem/{problemId}/history-summary")
    public ResponseEntity<List<SubmissionHistorySummaryResponse>> getSubmissionHistorySummary(@PathVariable Long problemId,
                                                                                              @RequestParam(required = false) Long assignmentId,
                                                                                              HttpServletRequest request) {
        Long studentProfileId = studentAccessTokenService.currentStudentId(request);
        if (assignmentId != null && studentProfileId == null) {
            studentAccessTokenService.requireAnyOf(request);
        }
        return ResponseEntity.ok(submissionAnalysisService.getSubmissionHistorySummaries(problemId, assignmentId, studentProfileId));
    }

    @GetMapping("/compare")
    public ResponseEntity<SubmissionComparisonResponse> compareSubmissions(@RequestParam Long leftId,
                                                                           @RequestParam Long rightId,
                                                                           HttpServletRequest request) {
        requireComparisonAccess(request, leftId, rightId);
        return ResponseEntity.ok(submissionComparisonService.compare(leftId, rightId));
    }

    private void requireSubmissionAccess(HttpServletRequest request, Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));
        if (submission.getStudentProfileId() != null) {
            studentAccessTokenService.requireStudent(request, submission.getStudentProfileId());
        }
    }

    private void requireComparisonAccess(HttpServletRequest request, Long leftId, Long rightId) {
        Submission left = submissionRepository.findById(leftId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + leftId));
        Submission right = submissionRepository.findById(rightId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + rightId));
        Long leftStudent = left.getStudentProfileId();
        Long rightStudent = right.getStudentProfileId();
        if (leftStudent == null && rightStudent == null) {
            return;
        }
        if (leftStudent == null || rightStudent == null || !leftStudent.equals(rightStudent)) {
            throw new AccessDeniedException("只能对比自己的两次提交");
        }
        studentAccessTokenService.requireStudent(request, leftStudent);
    }
}
