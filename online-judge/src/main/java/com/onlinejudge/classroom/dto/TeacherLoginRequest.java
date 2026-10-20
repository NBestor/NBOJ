package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeacherLoginRequest {

    @NotBlank(message = "教师口令不能为空")
    private String password;
}
