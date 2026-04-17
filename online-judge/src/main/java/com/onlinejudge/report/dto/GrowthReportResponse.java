package com.onlinejudge.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthReportResponse {
    private Long problemId;
    private String problemTitle;
    private Integer submissionCount;
    private Integer acceptedCount;
    private LocalDateTime generatedAt;
    private String markdown;
    private List<Milestone> milestones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Milestone {
        private Long submissionId;
        private String verdict;
        private LocalDateTime submittedAt;
        private String summary;
    }
}

