package com.onlinejudge.learning.knowledge.dto;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class InformaticsKnowledgeNodeResponse {
    private Long id;
    private String code;
    private String parentCode;
    private String type;
    private String name;
    private String description;
    private String path;
    private String stage;
    private String difficulty;
    private List<String> aliases;
    private List<String> prerequisites;
    private List<String> learningObjectives;
    private List<String> typicalProblems;
    private int sortOrder;
    private boolean enabled;
    private String libraryVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private List<InformaticsKnowledgeNodeResponse> children = new ArrayList<>();

    public static InformaticsKnowledgeNodeResponse from(InformaticsKnowledgeNode node) {
        return InformaticsKnowledgeNodeResponse.builder()
                .id(node.getId())
                .code(node.getCode())
                .parentCode(node.getParentCode())
                .type(node.getType().name())
                .name(node.getName())
                .description(node.getDescription())
                .path(node.getPath())
                .stage(node.getStage())
                .difficulty(node.getDifficulty())
                .aliases(lines(node.getAliases()))
                .prerequisites(lines(node.getPrerequisites()))
                .learningObjectives(lines(node.getLearningObjectives()))
                .typicalProblems(lines(node.getTypicalProblems()))
                .sortOrder(node.getSortOrder())
                .enabled(node.isEnabled())
                .libraryVersion(node.getLibraryVersion())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .children(new ArrayList<>())
                .build();
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
