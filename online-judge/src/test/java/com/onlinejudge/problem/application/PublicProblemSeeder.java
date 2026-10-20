package com.onlinejudge.problem.application;

import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.domain.TestCase;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.content-migration", name = "enabled", havingValue = "true")
public class PublicProblemSeeder implements CommandLineRunner {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    @Override
    public void run(String... args) {
        int inserted = 0;
        List<Problem> existingProblems = problemRepository.findAllByOrderByIdAsc();
        existingProblems.forEach(problem -> {
                    String starterCode = PublicStarterCodeCatalog.findByTitle(problem.getTitle());
                    if (starterCode != null && shouldUpgradeStarterCode(problem.getTitle(), problem.getStarterCode(), starterCode)) {
                        problem.setStarterCode(normalizeStarterCode(starterCode));
                        problemRepository.save(problem);
                    }
                });

        Set<String> existingTitles = existingProblems.stream()
                .map(Problem::getTitle)
                .collect(Collectors.toSet());
        for (PublicProblemSeed seed : PublicProblemSeedCatalog.seeds()) {
            if (existingTitles.contains(seed.title())) {
                continue;
            }
            Problem problem = problemRepository.save(Problem.builder()
                    .title(seed.title())
                    .description(seed.description())
                    .difficulty(seed.difficulty())
                    .timeLimit(seed.timeLimit())
                    .memoryLimit(seed.memoryLimit())
                    .aiPromptDirection(seed.aiPromptDirection())
                    .starterCode(normalizeStarterCode(seed.starterCode()))
                    .knowledgePoints(seed.knowledgePoints())
                    .algorithmStrategies(seed.algorithmStrategies())
                    .commonMistakes(seed.commonMistakes())
                    .boundaryTypes(seed.boundaryTypes())
                    .build());
            saveTestCases(problem.getId(), seed.testCases());
            existingTitles.add(seed.title());
            inserted++;
        }
        log.info("One-shot content migration processed public problems: inserted={}, total={}",
                inserted, problemRepository.count());
    }

    private boolean shouldUpgradeStarterCode(String title, String currentStarterCode, String catalogStarterCode) {
        if (isBlank(currentStarterCode)) {
            return true;
        }
        String normalizedCurrent = normalizeForComparison(currentStarterCode);
        String normalizedCatalog = normalizeForComparison(catalogStarterCode);
        if (normalizedCurrent.equals(normalizedCatalog)) {
            return false;
        }
        return isLegacyStarterCode(normalizedCurrent)
                || isOriginalSeedStarterCode(title, normalizedCurrent);
    }

    private boolean isLegacyStarterCode(String normalizedCurrent) {
        return normalizedCurrent.equals("n = int(input())\nprint(n)")
                || normalizedCurrent.equals("n = int(input())\n# 在这里计算 1 到 n 的和");
    }

    private boolean isOriginalSeedStarterCode(String title, String normalizedCurrent) {
        return PublicProblemSeedCatalog.seeds()
                .stream()
                .filter(seed -> seed.title().equals(title))
                .map(PublicProblemSeed::starterCode)
                .map(this::normalizeForComparison)
                .anyMatch(normalizedCurrent::equals);
    }

    private String normalizeForComparison(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void saveTestCases(Long problemId, List<PublicProblemSeed.TestCaseSeed> testCases) {
        IntStream.range(0, testCases.size())
                .mapToObj(index -> {
                    PublicProblemSeed.TestCaseSeed testCase = testCases.get(index);
                    return TestCase.builder()
                            .problemId(problemId)
                            .input(testCase.input())
                            .expectedOutput(testCase.expectedOutput())
                            .isHidden(testCase.hidden())
                            .orderIndex(index)
                            .build();
                })
                .forEach(testCaseRepository::save);
    }

    private String normalizeStarterCode(String starterCode) {
        if (starterCode == null || starterCode.isBlank()) {
            return null;
        }
        return starterCode.replace("\r\n", "\n").replace('\r', '\n').stripTrailing() + "\n";
    }
}
