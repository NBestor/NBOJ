package com.onlinejudge.learning.standardlibrary.dto;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiStandardLibraryNavigationNodeResponse {
    private String code;
    private String parentCode;
    private String type;
    private String name;
    private String description;
    private String path;
    private List<String> aliases;
    private int sortOrder;
    private boolean hasChildren;
    private boolean hasDiagnosticLayer;

    public static AiStandardLibraryNavigationNodeResponse from(InformaticsKnowledgeNode node,
                                                               boolean hasChildren,
                                                               boolean hasDiagnosticLayer) {
        return AiStandardLibraryNavigationNodeResponse.builder()
                .code(node.getCode())
                .parentCode(node.getParentCode())
                .type(node.getType().name())
                .name(node.getName())
                .description(node.getDescription())
                .path(node.getPath())
                .aliases(lines(node.getAliases()))
                .sortOrder(node.getSortOrder())
                .hasChildren(hasChildren)
                .hasDiagnosticLayer(hasDiagnosticLayer)
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
