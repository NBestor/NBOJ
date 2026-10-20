package com.onlinejudge.learning.standardlibrary.api;

import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryGrowthAgentService;
import com.onlinejudge.learning.standardlibrary.application.StandardLibraryGrowthProposal;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryGrowthCandidateRequest;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryGrowthCandidateResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryGrowthGovernanceSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/teacher/ai-standard-library/growth-candidates")
@RequiredArgsConstructor
public class AiStandardLibraryGrowthCandidateController {

    private final AiStandardLibraryGrowthAgentService service;

    @GetMapping
    public ResponseEntity<List<AiStandardLibraryGrowthCandidateResponse>> list() {
        return ResponseEntity.ok(service.listCandidates().stream()
                .map(AiStandardLibraryGrowthCandidateResponse::from)
                .toList());
    }

    @GetMapping("/governance-summary")
    public ResponseEntity<AiStandardLibraryGrowthGovernanceSummaryResponse> governanceSummary() {
        return ResponseEntity.ok(service.governanceSummary());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiStandardLibraryGrowthCandidateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(AiStandardLibraryGrowthCandidateResponse.from(service.getCandidate(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AiStandardLibraryGrowthCandidateResponse> update(@PathVariable Long id,
                                                                           @RequestBody AiStandardLibraryGrowthCandidateRequest request) {
        return ResponseEntity.ok(AiStandardLibraryGrowthCandidateResponse.from(
                service.updateCandidate(id, toProposal(request))
        ));
    }

    @PostMapping("/{id}/ignore")
    public ResponseEntity<AiStandardLibraryGrowthCandidateResponse> ignore(@PathVariable Long id,
                                                                           @RequestBody(required = false) AiStandardLibraryGrowthCandidateRequest request) {
        return ResponseEntity.ok(AiStandardLibraryGrowthCandidateResponse.from(
                service.ignore(id, request == null ? "" : request.getTeacherNote())
        ));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AiStandardLibraryGrowthCandidateResponse> reject(@PathVariable Long id,
                                                                           @RequestBody(required = false) AiStandardLibraryGrowthCandidateRequest request) {
        return ResponseEntity.ok(AiStandardLibraryGrowthCandidateResponse.from(
                service.reject(id, request == null ? "" : request.getTeacherNote())
        ));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AiStandardLibraryGrowthCandidateResponse> approve(@PathVariable Long id,
                                                                            @RequestBody(required = false) AiStandardLibraryGrowthCandidateRequest request) {
        StandardLibraryGrowthProposal proposal = request == null || request.getSuggestedCode() == null
                ? null
                : toProposal(request);
        return ResponseEntity.ok(AiStandardLibraryGrowthCandidateResponse.from(
                service.approve(id, proposal)
        ));
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<AiStandardLibraryGrowthCandidateResponse> merge(@PathVariable Long id,
                                                                          @RequestBody(required = false) AiStandardLibraryGrowthCandidateRequest request) {
        StandardLibraryGrowthProposal proposal = request == null || request.getSuggestedCode() == null
                ? null
                : toProposal(request);
        return ResponseEntity.ok(AiStandardLibraryGrowthCandidateResponse.from(
                service.mergeToFormalLibrary(id, proposal)
        ));
    }

    @PostMapping("/{id}/auto-merge")
    public ResponseEntity<AiStandardLibraryGrowthCandidateResponse> autoMerge(@PathVariable Long id,
                                                                              @RequestBody(required = false) AiStandardLibraryGrowthCandidateRequest request) {
        StandardLibraryGrowthProposal proposal = request == null || request.getSuggestedCode() == null
                ? null
                : toProposal(request);
        return ResponseEntity.ok(AiStandardLibraryGrowthCandidateResponse.from(
                service.autoMergeToFormalLibrary(id, proposal)
        ));
    }

    private StandardLibraryGrowthProposal toProposal(AiStandardLibraryGrowthCandidateRequest request) {
        return StandardLibraryGrowthProposal.builder()
                .suggestedCode(request == null ? "" : request.getSuggestedCode())
                .suggestedName(request == null ? "" : request.getSuggestedName())
                .layer(parseLayer(request == null ? null : request.getLayer()))
                .suggestedPath(request == null ? List.of() : request.getSuggestedPath())
                .parentKnowledgeNodeCode(request == null ? "" : request.getParentKnowledgeNodeCode())
                .sourceProblemId(request == null ? null : request.getSourceProblemId())
                .sourceSubmissionId(request == null ? null : request.getSourceSubmissionId())
                .similarExistingItemCodes(request == null ? List.of() : request.getSimilarExistingItems())
                .changeReason(request == null ? "" : request.getChangeReason())
                .evidenceRefs(request == null ? List.of() : request.getEvidenceRefs())
                .evidenceStatus(request == null ? null : request.getEvidenceStatus())
                .confidence(request == null ? null : request.getConfidence())
                .build();
    }

    private AiStandardLibraryLayer parseLayer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AiStandardLibraryLayer.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
