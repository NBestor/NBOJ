package com.onlinejudge.classroom.dto;

import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnosisTagResponse {
    private String id;
    private String label;
    private String teacherExplanation;
    private String abilityPoint;
    private boolean fineGrained;
    private String parentTag;
    private String teachingAction;

    public static DiagnosisTagResponse from(DiagnosisTaxonomy.DiagnosisTag tag) {
        return DiagnosisTagResponse.builder()
                .id(tag.getId())
                .label(tag.getLabel())
                .teacherExplanation(tag.getTeacherExplanation())
                .abilityPoint(tag.getAbilityPoint())
                .fineGrained(tag.isFineGrained())
                .parentTag(tag.getParentTag())
                .teachingAction(tag.getTeachingAction())
                .build();
    }
}
