package com.onlinejudge.learning.standardlibrary.dto;

public record AiStandardLibraryDomainCoverage(
        String domainCode,
        String domainName,
        long chapterCount,
        long topicCount,
        long knowledgePointCount,
        long skillUnitCount,
        long mistakePointCount,
        long handwrittenSkillLinkCount,
        long handwrittenMistakeLinkCount
) {
}
