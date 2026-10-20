package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssignmentReadinessResponse {
    private boolean publishable;
    private int blockerCount;
    private int warningCount;
    private List<ProblemReadiness> problems;

    @Data
    @Builder
    public static class ProblemReadiness {
        private Long problemId;
        private String problemTitle;
        private boolean ready;
        private int testCaseCount;
        private int reviewedSemanticCount;
        private List<Issue> blockers;
        private List<Issue> warnings;
    }

    @Data
    @Builder
    public static class Issue {
        private String code;
        private String severity;
        private String message;
    }
}
