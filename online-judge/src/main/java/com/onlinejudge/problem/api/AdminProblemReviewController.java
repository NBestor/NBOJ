package com.onlinejudge.problem.api;

import com.onlinejudge.identity.dto.AdminDecisionRequest;
import com.onlinejudge.problem.application.ProblemGovernanceService;
import com.onlinejudge.problem.dto.ProblemManageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/admin/problem-reviews", "/api/platform-admin/problem-reviews"})
@RequiredArgsConstructor
public class AdminProblemReviewController {
    private final ProblemGovernanceService governance;

    @GetMapping
    public ResponseEntity<List<ProblemManageResponse>> pending() { return ResponseEntity.ok(governance.pendingReviews()); }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ProblemManageResponse> approve(@PathVariable Long id) { return ResponseEntity.ok(governance.approve(id)); }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ProblemManageResponse> reject(@PathVariable Long id,
                                                        @Valid @RequestBody(required = false) AdminDecisionRequest request) {
        return ResponseEntity.ok(governance.reject(id, request == null ? null : request.reason()));
    }

    @PostMapping("/{id}/publish-public")
    public ResponseEntity<ProblemManageResponse> publishPublic(@PathVariable Long id) {
        return ResponseEntity.ok(governance.publishPublic(id));
    }
}
