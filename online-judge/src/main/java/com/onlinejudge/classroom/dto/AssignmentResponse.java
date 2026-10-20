package com.onlinejudge.classroom.dto;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.AssignmentInvite;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AssignmentResponse {
    private Long id;
    private String title;
    private String description;
    private Long classGroupId;
    private String className;
    private Assignment.HintPolicy hintPolicy;
    private Assignment.AssignmentStatus status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime createdAt;
    private String inviteCode;
    private Assignment.TargetMode targetMode;
    private long targetCount;
    private long currentVisibleCount;
    private List<TaskSummary> tasks;

    public static AssignmentResponse from(Assignment assignment,
                                          String className,
                                          AssignmentInvite invite,
                                          List<TaskSummary> tasks) {
        return from(assignment, className, invite, tasks, 0, 0);
    }

    public static AssignmentResponse from(Assignment assignment, String className, AssignmentInvite invite,
                                          List<TaskSummary> tasks, long targetCount, long currentVisibleCount) {
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .classGroupId(assignment.getClassGroupId())
                .className(className)
                .hintPolicy(assignment.getHintPolicy())
                .status(assignment.getStatus())
                .startsAt(assignment.getStartsAt())
                .endsAt(assignment.getEndsAt())
                .createdAt(assignment.getCreatedAt())
                .inviteCode(invite == null ? null : invite.getCode())
                .targetMode(assignment.getTargetMode())
                .targetCount(targetCount)
                .currentVisibleCount(currentVisibleCount)
                .tasks(tasks)
                .build();
    }

    @Data
    @Builder
    public static class TaskSummary {
        private Long problemId;
        private String title;
        private String difficulty;
        private Integer orderIndex;
        private Boolean required;
    }
}
