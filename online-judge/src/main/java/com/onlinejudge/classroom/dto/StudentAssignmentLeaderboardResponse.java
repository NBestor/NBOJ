package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StudentAssignmentLeaderboardResponse {
    private Long assignmentId;
    private int totalStudents;
    private int totalTasks;
    private int myRank;
    private int tiedStudentCount;
    private String rankingRule;
    private LocalDateTime generatedAt;
    private List<Row> rows;

    @Data
    @Builder
    public static class Row {
        private int rank;
        private Long studentProfileId;
        private String displayName;
        private int completedTasks;
        private int totalTasks;
        private int attemptCount;
        private LocalDateTime lastSubmittedAt;
        private boolean currentStudent;
    }
}
