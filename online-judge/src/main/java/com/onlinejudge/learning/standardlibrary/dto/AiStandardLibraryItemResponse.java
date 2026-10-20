package com.onlinejudge.learning.standardlibrary.dto;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AiStandardLibraryItemResponse {
    private Long id;
    private String layer;
    private String code;
    private String category;
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
    private boolean enabled;
    private String libraryVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AiStandardLibraryItemResponse from(AiStandardLibraryItem item) {
        return AiStandardLibraryItemResponse.builder()
                .id(item.getId())
                .layer(item.getLayer().name())
                .code(item.getCode())
                .category(item.getCategory())
                .name(item.getName())
                .description(item.getDescription())
                .studentExplanation(item.getStudentExplanation())
                .teacherExplanation(item.getTeacherExplanation())
                .skillUnitCode(item.getSkillUnitCode())
                .primaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode())
                .mistakeType(item.getMistakeType())
                .commonMisconception(item.getCommonMisconception())
                .evidenceSignals(lines(item.getEvidenceSignals()))
                .commonCodePatterns(lines(item.getCommonCodePatterns()))
                .judgeSignals(lines(item.getJudgeSignals()))
                .requiredEvidence(lines(item.getRequiredEvidence()))
                .whenToUse(item.getWhenToUse())
                .studentBenefit(item.getStudentBenefit())
                .hintL1(item.getHintL1())
                .hintL2(item.getHintL2())
                .hintL3(item.getHintL3())
                .abilityPoint(item.getAbilityPoint())
                .severity(item.getSeverity())
                .applicableLanguages(lines(item.getApplicableLanguages()))
                .relatedItems(lines(item.getRelatedItems()))
                .knowledgeNodeCodes(lines(item.getKnowledgeNodeCodes()))
                .relatedKnowledgeNodeCodes(lines(item.getRelatedKnowledgeNodeCodes()))
                .prerequisiteKnowledgeCodes(lines(item.getPrerequisiteKnowledgeCodes()))
                .teachingAction(item.getTeachingAction())
                .enabled(item.isEnabled())
                .libraryVersion(item.getLibraryVersion())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
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
