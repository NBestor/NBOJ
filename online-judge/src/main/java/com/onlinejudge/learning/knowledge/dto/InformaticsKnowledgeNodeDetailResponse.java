package com.onlinejudge.learning.knowledge.dto;

import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InformaticsKnowledgeNodeDetailResponse {
    private InformaticsKnowledgeNodeResponse node;
    private List<InformaticsKnowledgeNodeResponse> ancestors;
    private List<InformaticsKnowledgeNodeResponse> children;
    private List<AiStandardLibraryItemResponse> standardLibraryItems;
}
