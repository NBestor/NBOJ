package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ClassLearningOverviewResponse {
    private ClassGroupResponse classGroup;
    private long assignmentCount;
    private long rosterStudentCount;
    private long submittedStudentCount;
    private long unsubmittedStudentCount;
    private List<AssignmentSummary> assignments;

    @Data
    @Builder
    public static class AssignmentSummary {
        private Long assignmentId;
        private String title;
        private String status;
        private LocalDateTime createdAt;
        private long problemCount;
        private long rosterStudentCount;
        private long submittedStudentCount;
        private long unsubmittedStudentCount;
        private long completedRequiredStudentCount;
    }
}
