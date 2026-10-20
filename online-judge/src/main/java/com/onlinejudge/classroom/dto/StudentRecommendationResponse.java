package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StudentRecommendationResponse {
    private StudentProfileResponse student;
    private String summary;
    private List<RecommendationItem> recommendations;

    @Data
    @Builder
    public static class RecommendationItem {
        private String recommendationToken;
        private String type;
        private String title;
        private String reason;
        private String actionLabel;
        private Long assignmentId;
        private Long problemId;
        private String problemTitle;
        private String focusAbility;
        private List<String> focusTags;
        private List<Long> evidenceProblemIds;
        private String learningHypothesis;
        private String expectedCompletionSignal;
        private String strategy;
        private String riskLevel;
        private String fallbackAction;
        private String actionOutcome;
        private String actionOutcomeSummary;
        private String actionMatchBasis;
        private List<String> actionEvidenceRefs;
        private int priority;
    }
}
