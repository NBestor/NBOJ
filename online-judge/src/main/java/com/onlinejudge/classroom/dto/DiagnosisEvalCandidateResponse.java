package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DiagnosisEvalCandidateResponse {
    private Long assignmentId;
    private int candidateCount;
    private List<Candidate> candidates;

    @Data
    @Builder
    public static class Candidate {
        private Long correctionId;
        private Long submissionId;
        private Long studentProfileId;
        private Long problemId;
        private String problemTitle;
        private String problemDescription;
        private String problemDifficulty;
        private Integer problemTimeLimit;
        private Integer problemMemoryLimit;
        private String verdict;
        private String languageName;
        private String sourceCode;
        private String scenario;
        private String originalIssueTag;
        private String originalFineGrainedTag;
        private String correctedIssueTag;
        private String correctedFineGrainedTag;
        private String teacherNote;
        private String analysisHeadline;
        private String analysisSource;
        private String sourceCodePreview;
        private LocalDateTime correctedAt;
    }
}
