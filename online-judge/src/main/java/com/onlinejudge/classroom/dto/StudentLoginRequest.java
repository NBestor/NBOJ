package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StudentLoginRequest {
    private Long classGroupId;

    private String classCode;

    @NotBlank(message = "姓名不能为空")
    private String displayName;

    @NotBlank(message = "学号不能为空")
    private String studentNo;
    private String note;
}
