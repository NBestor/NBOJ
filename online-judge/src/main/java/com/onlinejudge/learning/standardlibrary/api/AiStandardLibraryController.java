package com.onlinejudge.learning.standardlibrary.api;

import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemRequest;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/ai-standard-library/items")
@RequiredArgsConstructor
public class AiStandardLibraryController {

    private final AiStandardLibraryService service;

    @GetMapping
    public ResponseEntity<List<AiStandardLibraryItemResponse>> list(@RequestParam(required = false) String layer,
                                                                    @RequestParam(required = false) String category,
                                                                    @RequestParam(required = false) String enabled,
                                                                    @RequestParam(required = false) String query) {
        return ResponseEntity.ok(service.list(layer, category, enabled, query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiStandardLibraryItemResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<AiStandardLibraryItemResponse> create(@Valid @RequestBody AiStandardLibraryItemRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AiStandardLibraryItemResponse> update(@PathVariable Long id,
                                                                @Valid @RequestBody AiStandardLibraryItemRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<AiStandardLibraryItemResponse> enable(@PathVariable Long id) {
        return ResponseEntity.ok(service.setEnabled(id, true));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<AiStandardLibraryItemResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(service.setEnabled(id, false));
    }
}
