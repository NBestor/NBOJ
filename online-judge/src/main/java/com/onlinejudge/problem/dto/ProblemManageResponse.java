package com.onlinejudge.problem.dto;

import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.domain.TestCase;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProblemManageResponse {
    private Long id;
    private String title;
    private String description;
    private Problem.Difficulty difficulty;
    private Integer timeLimit;
    private Integer memoryLimit;
    private String aiPromptDirection;
    private LocalDateTime createdAt;
    private List<TestCaseItem> testCases;

    @Data
    @Builder
    public static class TestCaseItem {
        private Long id;
        private String input;
        private String expectedOutput;
        private Boolean hidden;
        private Integer orderIndex;
    }

    public static ProblemManageResponse from(Problem problem, List<TestCase> testCases) {
        return ProblemManageResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                .aiPromptDirection(problem.getAiPromptDirection())
                .createdAt(problem.getCreatedAt())
                .testCases(testCases.stream()
                        .map(testCase -> TestCaseItem.builder()
                                .id(testCase.getId())
                                .input(testCase.getInput())
                                .expectedOutput(testCase.getExpectedOutput())
                                .hidden(Boolean.TRUE.equals(testCase.getIsHidden()))
                                .orderIndex(testCase.getOrderIndex())
                                .build())
                        .toList())
                .build();
    }
}

