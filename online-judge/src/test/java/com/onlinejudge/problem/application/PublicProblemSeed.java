package com.onlinejudge.problem.application;

import com.onlinejudge.problem.domain.Problem;

import java.util.List;

public record PublicProblemSeed(
        String title,
        String description,
        Problem.Difficulty difficulty,
        int timeLimit,
        int memoryLimit,
        String aiPromptDirection,
        String starterCode,
        List<String> knowledgePoints,
        List<String> algorithmStrategies,
        List<String> commonMistakes,
        List<String> boundaryTypes,
        List<TestCaseSeed> testCases
) {
    public record TestCaseSeed(String input, String expectedOutput, boolean hidden) {
    }
}
