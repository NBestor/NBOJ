package com.onlinejudge.learning.knowledge.application;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;

import java.util.List;

public record InformaticsKnowledgeSeed(
        String code,
        String parentCode,
        InformaticsKnowledgeNodeType type,
        String name,
        String description,
        String path,
        String stage,
        String difficulty,
        List<String> aliases,
        List<String> prerequisites,
        List<String> learningObjectives,
        List<String> typicalProblems,
        int sortOrder,
        String libraryVersion
) {
}
