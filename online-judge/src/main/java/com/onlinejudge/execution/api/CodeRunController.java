package com.onlinejudge.execution.api;

import com.onlinejudge.execution.application.ClassroomProblemAccessService;
import com.onlinejudge.execution.application.CodeRunAdmissionService;
import com.onlinejudge.execution.application.CodeRunService;
import com.onlinejudge.execution.dto.CodeRunRequest;
import com.onlinejudge.execution.dto.CodeRunResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/code-runs")
@RequiredArgsConstructor
public class CodeRunController {

    private final ClassroomProblemAccessService problemAccessService;
    private final CodeRunAdmissionService admissionService;
    private final CodeRunService codeRunService;

    @PostMapping
    public ResponseEntity<CodeRunResponse> runCode(@Valid @RequestBody CodeRunRequest request,
                                                   HttpServletRequest httpRequest) {
        codeRunService.validateRequest(request);
        Long studentId = problemAccessService.requireAccess(
                request.getAssignmentId(),
                request.getProblemId(),
                httpRequest
        );
        String key = studentId == null
                ? "ip:" + safeRemoteAddress(httpRequest.getRemoteAddr())
                : "student:" + studentId;
        try (CodeRunAdmissionService.Lease ignored = admissionService.acquire(key)) {
            return ResponseEntity.ok(codeRunService.run(request));
        }
    }

    private String safeRemoteAddress(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
