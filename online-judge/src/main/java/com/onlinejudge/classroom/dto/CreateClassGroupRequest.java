package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateClassGroupRequest {
    @NotBlank(message = "班级名称不能为空")
    private String name;

    private String grade;
    private String teacherName;
}
