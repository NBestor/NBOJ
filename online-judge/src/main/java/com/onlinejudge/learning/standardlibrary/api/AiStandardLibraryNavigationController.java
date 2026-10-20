package com.onlinejudge.learning.standardlibrary.api;

import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryDiagnosticLayerResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationExpansionResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationNodeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/ai-standard-library/navigation")
@RequiredArgsConstructor
public class AiStandardLibraryNavigationController {

    private final AiStandardLibraryService service;

    @GetMapping("/roots")
    public ResponseEntity<List<AiStandardLibraryNavigationNodeResponse>> roots() {
        return ResponseEntity.ok(service.listRootKnowledgeAreas());
    }

    @GetMapping("/nodes/{code}")
    public ResponseEntity<AiStandardLibraryNavigationExpansionResponse> expandNode(
            @PathVariable String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(service.expandKnowledgeNode(code, page, size));
    }

    @GetMapping("/nodes/{code}/diagnostic-layer")
    public ResponseEntity<AiStandardLibraryDiagnosticLayerResponse> diagnosticLayer(@PathVariable String code) {
        return ResponseEntity.ok(service.expandDiagnosticLayer(code));
    }
}
