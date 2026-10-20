package com.onlinejudge.learning.standardlibrary.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AiStandardLibraryItemRequest {
    @NotBlank
    private String layer;

    @NotBlank
    private String code;

    @NotBlank
    private String category;

    @NotBlank
    private String name;

    private String description;
    private String studentExplanation;
    private String teacherExplanation;
    private String skillUnitCode;
    private String primaryKnowledgeNodeCode;
    private String mistakeType;
    private String commonMisconception;
    private List<String> evidenceSignals;
    private List<String> commonCodePatterns;
    private List<String> judgeSignals;
    private List<String> requiredEvidence;
    private String whenToUse;
    private String studentBenefit;
    private String hintL1;
    private String hintL2;
    private String hintL3;
    private String abilityPoint;
    private String severity;
    private List<String> applicableLanguages;
    private List<String> relatedItems;
    private List<String> knowledgeNodeCodes;
    private List<String> relatedKnowledgeNodeCodes;
    private List<String> prerequisiteKnowledgeCodes;
    private String teachingAction;
    private Boolean enabled;
    private String libraryVersion;
}
