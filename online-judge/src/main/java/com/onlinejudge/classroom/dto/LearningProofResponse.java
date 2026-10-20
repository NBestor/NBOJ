package com.onlinejudge.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningProofResponse {
    private Long studentProfileId;
    private Long assignmentId;
    private Long problemId;
    private String problemTitle;
    private Long latestSubmissionId;
    private RepairProof repair;
    private ExplanationProof explanation;
    private IndependentUseProof independentUse;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepairProof {
        private String status;
        private Long baselineSubmissionId;
        private Long targetSubmissionId;
        private Integer passedTestCaseDelta;
        private long recoveredIssueCount;
        private List<String> recoveredIssues;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExplanationProof {
        private String status;
        private Long promptId;
        private Long submissionId;
        private String question;
        private String answer;
        private String feedback;
        private boolean checkable;
        private List<String> evidenceTypes;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndependentUseProof {
        private String status;
        private Long sourceSubmissionId;
        private Long targetProblemId;
        private String targetProblemTitle;
        private Long targetSubmissionId;
        private List<String> evidenceRefs;
    }
}
