package com.onlinejudge.learning.standardlibrary.application;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;

import java.util.List;

public record AiStandardLibrarySeed(
        AiStandardLibraryLayer layer,
        String code,
        String category,
        String name,
        String description,
        String studentExplanation,
        String teacherExplanation,
        String skillUnitCode,
        String mistakeType,
        String commonMisconception,
        List<String> evidenceSignals,
        List<String> commonCodePatterns,
        List<String> judgeSignals,
        List<String> requiredEvidence,
        String whenToUse,
        String studentBenefit,
        String hintL1,
        String hintL2,
        String hintL3,
        String abilityPoint,
        String severity,
        List<String> applicableLanguages,
        List<String> relatedItems,
        List<String> knowledgeNodeCodes,
        List<String> prerequisiteKnowledgeCodes,
        String teachingAction,
        String libraryVersion
) {
}
