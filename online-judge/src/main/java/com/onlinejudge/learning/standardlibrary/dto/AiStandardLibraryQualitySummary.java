package com.onlinejudge.learning.standardlibrary.dto;

public record AiStandardLibraryQualitySummary(
        long domainCount,
        long chapterCount,
        long topicCount,
        long knowledgePointCount,
        long skillUnitCount,
        long mistakePointCount,
        long handwrittenSkillUnitCount,
        long handwrittenMistakePointCount,
        long compatibilitySkillUnitCount,
        long compatibilityMistakePointCount,
        long totalItemCount
) {
}
