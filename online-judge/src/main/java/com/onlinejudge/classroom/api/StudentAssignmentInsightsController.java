package com.onlinejudge.classroom.api;

import com.onlinejudge.classroom.application.StudentAssignmentInsightsService;
import com.onlinejudge.classroom.dto.StudentAssignmentLeaderboardResponse;
import com.onlinejudge.classroom.dto.StudentAssignmentSubmissionPageResponse;
import com.onlinejudge.shared.security.AuthenticationRequiredException;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import com.onlinejudge.submission.domain.Submission;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StudentAssignmentInsightsController {

    private final StudentAssignmentInsightsService insightsService;
    private final StudentAccessTokenService studentAccessTokenService;

    @GetMapping("/api/student/assignments/{assignmentId}/leaderboard")
    public ResponseEntity<StudentAssignmentLeaderboardResponse> getLeaderboard(
            @PathVariable Long assignmentId,
            HttpServletRequest request) {
        Long studentProfileId = requireStudentId(request);
        return ResponseEntity.ok(insightsService.getLeaderboard(assignmentId, studentProfileId));
    }

    @GetMapping("/api/student/assignments/{assignmentId}/submissions")
    public ResponseEntity<StudentAssignmentSubmissionPageResponse> getSubmissions(
            @PathVariable Long assignmentId,
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) Boolean accepted,
            @RequestParam(required = false) Submission.Verdict verdict,
            @RequestParam(required = false) String languageName,
            @RequestParam(required = false) Long submissionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            HttpServletRequest request) {
        Long studentProfileId = requireStudentId(request);
        return ResponseEntity.ok(insightsService.getSubmissions(
                assignmentId,
                studentProfileId,
                problemId,
                accepted,
                verdict,
                languageName,
                submissionId,
                page,
                size
        ));
    }

    private Long requireStudentId(HttpServletRequest request) {
        Long studentProfileId = studentAccessTokenService.currentStudentId(request);
        if (studentProfileId == null) {
            throw new AuthenticationRequiredException("请先登录学生端");
        }
        return studentProfileId;
    }
}
