package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StudentIdentityRequest {
    @NotNull(message = "作业 ID 不能为空")
    private Long assignmentId;

    private Long classGroupId;

    @NotBlank(message = "姓名不能为空")
    private String displayName;

    private String className;
    private String studentNo;
    private String note;
}
