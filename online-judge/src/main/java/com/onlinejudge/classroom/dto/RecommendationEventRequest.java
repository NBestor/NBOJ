package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecommendationEventRequest {
    @NotBlank(message = "推荐 token 不能为空")
    private String recommendationToken;

    private String eventType;
}
