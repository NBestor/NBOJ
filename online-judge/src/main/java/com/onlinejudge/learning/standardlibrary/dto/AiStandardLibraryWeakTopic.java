package com.onlinejudge.learning.standardlibrary.dto;

public record AiStandardLibraryWeakTopic(
        String topicCode,
        String topicName,
        String domainCode,
        long knowledgePointCount,
        long handwrittenLinkCount,
        String recommendation
) {
}
