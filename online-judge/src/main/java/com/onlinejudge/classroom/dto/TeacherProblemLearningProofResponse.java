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
public class TeacherProblemLearningProofResponse {
    private Long assignmentId;
    private Long problemId;
    private String problemTitle;
    private long failedStudentCount;
    private long repairedStudentCount;
    private long explainedStudentCount;
    private long independentVerifiedStudentCount;
    private List<StudentProof> students;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentProof {
        private Long studentProfileId;
        private String displayName;
        private String studentNo;
        private Long latestSubmissionId;
        private boolean hadFailure;
        private boolean repaired;
        private boolean explained;
        private boolean explanationCheckable;
        private boolean independentVerified;
        private LearningProofResponse proof;
    }
}
