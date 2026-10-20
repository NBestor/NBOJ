package com.onlinejudge.learning.standardlibrary.application;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StandardLibraryGrowthProposal {
    private String suggestedCode;
    private String suggestedName;
    private AiStandardLibraryLayer layer;
    private List<String> suggestedPath;
    private String parentKnowledgeNodeCode;
    private Long sourceProblemId;
    private Long sourceSubmissionId;
    private List<String> similarExistingItemCodes;
    private String changeReason;
    private List<String> evidenceRefs;
    private String evidenceStatus;
    private Double confidence;
}
