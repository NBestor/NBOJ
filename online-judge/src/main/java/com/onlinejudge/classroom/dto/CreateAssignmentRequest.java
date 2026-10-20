package com.onlinejudge.classroom.dto;

import com.onlinejudge.classroom.domain.Assignment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateAssignmentRequest {
    @NotBlank(message = "作业标题不能为空")
    private String title;

    private String description;
    @NotNull(message = "必须选择班级")
    private Long classGroupId;
    private Assignment.TargetMode targetMode = Assignment.TargetMode.CLASS;
    private List<Long> studentProfileIds;
    private Assignment.HintPolicy hintPolicy = Assignment.HintPolicy.L2;
    private Assignment.AssignmentStatus status = Assignment.AssignmentStatus.ACTIVE;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;

    @NotEmpty(message = "作业至少需要绑定一个学习任务")
    private List<Long> problemIds;
}
