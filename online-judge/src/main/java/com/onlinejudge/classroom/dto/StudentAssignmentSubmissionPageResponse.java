package com.onlinejudge.classroom.dto;

import com.onlinejudge.submission.domain.Submission;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StudentAssignmentSubmissionPageResponse {
    private Long assignmentId;
    private long totalSubmissionCount;
    private long acceptedSubmissionCount;
    private long distinctProblemCount;
    private LocalDateTime latestSubmittedAt;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private Long id;
        private Long problemId;
        private String problemTitle;
        private Submission.Verdict verdict;
        private String languageName;
        private Double executionTime;
        private Integer memoryUsed;
        private LocalDateTime submittedAt;
    }
}
