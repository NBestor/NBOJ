package com.onlinejudge.problem.dto;

import com.onlinejudge.problem.domain.Problem;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProblemCatalogItemResponse {
    private Long id;
    private String title;
    private String summary;
    private Problem.Difficulty difficulty;
    private Integer timeLimit;
    private Integer memoryLimit;
    private LocalDateTime createdAt;
}

