package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CoachReplyRequest {

    @NotBlank(message = "请先写下你的思考。")
    @Size(max = 1200, message = "回答请控制在 1200 字以内。")
    private String answer;
}
