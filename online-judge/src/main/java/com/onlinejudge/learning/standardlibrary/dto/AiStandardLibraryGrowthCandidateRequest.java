package com.onlinejudge.learning.standardlibrary.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiStandardLibraryGrowthCandidateRequest {
    private String layer;
    private String suggestedCode;
    private String suggestedName;
    private List<String> suggestedPath;
    private String parentKnowledgeNodeCode;
    private Long sourceProblemId;
    private Long sourceSubmissionId;
    private List<String> evidenceRefs;
    private String evidenceStatus;
    private List<String> similarExistingItems;
    private String changeReason;
    private Double confidence;
    private String teacherNote;
}
