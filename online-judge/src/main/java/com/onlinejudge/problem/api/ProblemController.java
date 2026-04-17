package com.onlinejudge.problem.api;

import com.onlinejudge.problem.dto.CreateProblemRequest;
import com.onlinejudge.report.dto.GrowthReportResponse;
import com.onlinejudge.problem.dto.ProblemCatalogItemResponse;
import com.onlinejudge.problem.dto.ProblemManageResponse;
import com.onlinejudge.problem.dto.ProblemResponse;
import com.onlinejudge.report.application.GrowthReportService;
import com.onlinejudge.problem.application.ProblemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;
    private final GrowthReportService growthReportService;

    @GetMapping
    public ResponseEntity<List<ProblemResponse>> getAllProblems() {
        return ResponseEntity.ok(problemService.getAllProblems());
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<ProblemCatalogItemResponse>> getProblemCatalog() {
        return ResponseEntity.ok(problemService.getProblemCatalog());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemResponse> getProblemById(@PathVariable Long id) {
        return ResponseEntity.ok(problemService.getProblemById(id));
    }

    @GetMapping("/{id}/manage")
    public ResponseEntity<ProblemManageResponse> getProblemForManage(@PathVariable Long id) {
        return ResponseEntity.ok(problemService.getProblemForManage(id));
    }

    @PostMapping
    public ResponseEntity<ProblemResponse> createProblem(@Valid @RequestBody CreateProblemRequest request) {
        return ResponseEntity.ok(problemService.createProblem(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProblemResponse> updateProblem(@PathVariable Long id,
                                                         @Valid @RequestBody CreateProblemRequest request) {
        return ResponseEntity.ok(problemService.updateProblem(id, request));
    }

    @GetMapping("/{problemId}/growth-report")
    public ResponseEntity<GrowthReportResponse> getGrowthReport(@PathVariable Long problemId) {
        return ResponseEntity.ok(growthReportService.buildGrowthReport(problemId));
    }

    @GetMapping("/{problemId}/growth-report/export")
    public ResponseEntity<byte[]> exportGrowthReport(@PathVariable Long problemId,
                                                     @RequestParam(defaultValue = "markdown") String format) {
        byte[] payload = growthReportService.exportGrowthReport(problemId, format);
        String normalizedFormat = format == null ? "markdown" : format.trim().toLowerCase();
        String extension = normalizedFormat.equals("pdf") ? "pdf" : "md";
        MediaType mediaType = normalizedFormat.equals("pdf")
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("text/markdown;charset=UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"problem-" + problemId + "-growth-report." + extension + "\"")
                .contentType(mediaType)
                .contentLength(payload.length)
                .body(payload);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteProblem(@PathVariable Long id) {
        var deletedProblem = problemService.deleteProblem(id);
        return ResponseEntity.ok(Map.of(
                "message", "题目删除成功",
                "title", deletedProblem.getTitle()
        ));
    }
}

