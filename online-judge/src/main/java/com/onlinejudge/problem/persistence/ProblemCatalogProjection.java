package com.onlinejudge.problem.persistence;

import com.onlinejudge.problem.domain.Problem;

import java.time.LocalDateTime;

public interface ProblemCatalogProjection {
    Long getId();
    String getTitle();
    String getDescription();
    Problem.Difficulty getDifficulty();
    Integer getTimeLimit();
    Integer getMemoryLimit();
    LocalDateTime getCreatedAt();
}

