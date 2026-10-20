package com.onlinejudge.system.api;

import com.onlinejudge.system.application.ExecutorStatusService;
import com.onlinejudge.system.application.AiSmokeService;
import com.onlinejudge.system.application.ReadinessService;
import com.onlinejudge.system.dto.AiSmokeResponse;
import com.onlinejudge.system.dto.ExecutorStatusResponse;
import com.onlinejudge.system.dto.ReadinessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final ExecutorStatusService executorStatusService;
    private final ReadinessService readinessService;
    private final AiSmokeService aiSmokeService;

    @GetMapping("/executor-status")
    public ResponseEntity<ExecutorStatusResponse> getExecutorStatus() {
        return ResponseEntity.ok(executorStatusService.getStatus());
    }

    @GetMapping("/readiness")
    public ResponseEntity<ReadinessResponse> getReadiness() {
        return ResponseEntity.ok(readinessService.getReadiness());
    }

    @PostMapping("/ai-smoke")
    public ResponseEntity<AiSmokeResponse> runAiSmoke() {
        return ResponseEntity.ok(aiSmokeService.runSmoke());
    }
}
