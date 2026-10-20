package com.onlinejudge.learning.knowledge.api;

import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeService;
import com.onlinejudge.learning.knowledge.dto.InformaticsKnowledgeNodeDetailResponse;
import com.onlinejudge.learning.knowledge.dto.InformaticsKnowledgeNodeResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/informatics-knowledge")
@RequiredArgsConstructor
public class InformaticsKnowledgeController {

    private final InformaticsKnowledgeService service;

    @GetMapping("/tree")
    public ResponseEntity<List<InformaticsKnowledgeNodeResponse>> tree(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ResponseEntity.ok(service.tree(includeDisabled));
    }

    @GetMapping("/nodes/{code}")
    public ResponseEntity<InformaticsKnowledgeNodeDetailResponse> detail(@PathVariable String code) {
        return ResponseEntity.ok(service.detail(code));
    }

    @GetMapping("/nodes/{code}/standard-library-items")
    public ResponseEntity<List<AiStandardLibraryItemResponse>> standardLibraryItems(@PathVariable String code) {
        return ResponseEntity.ok(service.standardLibraryItems(code));
    }
}
