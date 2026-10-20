package com.onlinejudge.learning.standardlibrary.dto;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryGrowthCandidate;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AiStandardLibraryGrowthCandidateResponse {
    private Long id;
    private String layer;
    private String suggestedCode;
    private String suggestedName;
    private List<String> suggestedPath;
    private String parentKnowledgeNodeCode;
    private Long sourceProblemId;
    private Long sourceSubmissionId;
    private List<Long> observedSubmissionIds;
    private List<String> evidenceRefs;
    private String evidenceStatus;
    private List<String> similarExistingItems;
    private String changeReason;
    private String status;
    private String precheckMessage;
    private Double confidence;
    private Integer occurrenceCount;
    private LocalDateTime lastObservedAt;
    private String teacherNote;
    private String beforeSnapshot;
    private String diffSummary;
    private String rollbackInfo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AiStandardLibraryGrowthCandidateResponse from(AiStandardLibraryGrowthCandidate candidate) {
        return AiStandardLibraryGrowthCandidateResponse.builder()
                .id(candidate.getId())
                .layer(candidate.getLayer().name())
                .suggestedCode(candidate.getSuggestedCode())
                .suggestedName(candidate.getSuggestedName())
                .suggestedPath(path(candidate.getSuggestedPath()))
                .parentKnowledgeNodeCode(candidate.getParentKnowledgeNodeCode())
                .sourceProblemId(candidate.getSourceProblemId())
                .sourceSubmissionId(candidate.getSourceSubmissionId())
                .observedSubmissionIds(candidate.getObservedSubmissionIds() == null
                        ? List.of()
                        : candidate.getObservedSubmissionIds())
                .evidenceRefs(lines(candidate.getEvidenceRefs()))
                .evidenceStatus(candidate.getEvidenceStatus())
                .similarExistingItems(lines(candidate.getSimilarExistingItems()))
                .changeReason(candidate.getChangeReason())
                .status(candidate.getStatus().name())
                .precheckMessage(candidate.getPrecheckMessage())
                .confidence(candidate.getConfidence())
                .occurrenceCount(candidate.getOccurrenceCount())
                .lastObservedAt(candidate.getLastObservedAt())
                .teacherNote(candidate.getTeacherNote())
                .beforeSnapshot(candidate.getBeforeSnapshot())
                .diffSummary(candidate.getDiffSummary())
                .rollbackInfo(candidate.getRollbackInfo())
                .createdAt(candidate.getCreatedAt())
                .updatedAt(candidate.getUpdatedAt())
                .build();
    }

    private static List<String> path(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\.")).stream()
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }
}
