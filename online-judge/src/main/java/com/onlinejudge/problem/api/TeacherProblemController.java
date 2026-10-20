package com.onlinejudge.problem.api;

import com.onlinejudge.problem.application.ProblemGovernanceService;
import com.onlinejudge.problem.application.ProblemService;
import com.onlinejudge.problem.dto.CreateProblemRequest;
import com.onlinejudge.problem.dto.ProblemManageResponse;
import com.onlinejudge.problem.dto.ProblemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/problems")
@RequiredArgsConstructor
public class TeacherProblemController {
    private final ProblemService problemService;
    private final ProblemGovernanceService governance;

    @GetMapping
    public ResponseEntity<List<ProblemManageResponse>> catalog() { return ResponseEntity.ok(governance.teacherCatalog()); }

    @PostMapping
    public ResponseEntity<ProblemResponse> create(@Valid @RequestBody CreateProblemRequest request) {
        return ResponseEntity.ok(problemService.createProblem(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProblemResponse> update(@PathVariable Long id, @Valid @RequestBody CreateProblemRequest request) {
        return ResponseEntity.ok(problemService.updateProblem(id, request));
    }

    @PostMapping("/{id}/submit-review")
    public ResponseEntity<ProblemManageResponse> submitReview(@PathVariable Long id) {
        return ResponseEntity.ok(governance.submitReview(id));
    }

    @PostMapping("/{id}/revise")
    public ResponseEntity<ProblemManageResponse> revise(@PathVariable Long id) {
        return ResponseEntity.ok(governance.revise(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }
}
