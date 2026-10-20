package com.onlinejudge.learning.standardlibrary.dto;

import java.util.List;
import java.util.Map;

public record AiStandardLibraryQualityReport(
        AiStandardLibraryQualitySummary summary,
        List<AiStandardLibraryDomainCoverage> domainCoverage,
        List<AiStandardLibraryWeakTopic> weakTopics,
        Map<String, Long> mistakeTypeDistribution,
        List<String> recommendations
) {
}
