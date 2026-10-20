package com.onlinejudge.learning.standardlibrary.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiStandardLibraryGrowthGovernanceSummaryResponse {
    private int totalCount;
    private int reviewPendingCount;
    private int proposedCount;
    private int needsReviewCount;
    private int blockedCount;
    private int mergedSimilarCount;
    private int teacherApprovedCount;
    private int mergedCount;
    private int rejectedCount;
    private int ignoredCount;
    private int duplicateAggregateCount;
    private List<StatusStat> statusStats;
    private List<PathStat> highFrequencyPaths;
    private List<PathStat> weakPaths;

    @Data
    @Builder
    public static class StatusStat {
        private String status;
        private int count;
    }

    @Data
    @Builder
    public static class PathStat {
        private List<String> path;
        private int candidateCount;
        private int pendingCount;
        private int occurrenceCount;
        private String recommendedAction;
    }
}
