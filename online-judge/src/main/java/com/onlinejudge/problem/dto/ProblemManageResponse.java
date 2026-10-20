package com.onlinejudge.problem.dto;

import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.domain.TestCase;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
    private String starterCode;
    private List<String> knowledgePoints;
    private List<String> algorithmStrategies;
    private List<String> commonMistakes;
    private List<String> boundaryTypes;
    private LocalDateTime createdAt;
    private UUID ownerTeacherId;
    private Problem.Scope scope;
    private Problem.VersionState versionState;
    private UUID seriesId;
    private Integer versionNo;
    private Long sourceProblemId;
    private LocalDateTime archivedAt;
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
                .starterCode(problem.getStarterCode())
                .knowledgePoints(safeList(problem.getKnowledgePoints()))
                .algorithmStrategies(safeList(problem.getAlgorithmStrategies()))
                .commonMistakes(safeList(problem.getCommonMistakes()))
                .boundaryTypes(safeList(problem.getBoundaryTypes()))
                .createdAt(problem.getCreatedAt())
                .ownerTeacherId(problem.getOwnerTeacherId())
                .scope(problem.getScope())
                .versionState(problem.getVersionState())
                .seriesId(problem.getSeriesId())
                .versionNo(problem.getVersionNo())
                .sourceProblemId(problem.getSourceProblemId())
                .archivedAt(problem.getArchivedAt())
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

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
