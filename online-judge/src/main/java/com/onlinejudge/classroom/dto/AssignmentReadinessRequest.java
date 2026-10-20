package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AssignmentReadinessRequest {
    @NotEmpty(message = "作业至少需要绑定一个学习任务")
    private List<Long> problemIds;
}
