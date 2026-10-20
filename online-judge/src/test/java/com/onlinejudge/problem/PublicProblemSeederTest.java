package com.onlinejudge.problem;

import com.onlinejudge.problem.application.PublicProblemSeed;
import com.onlinejudge.problem.application.PublicProblemSeedCatalog;
import com.onlinejudge.problem.application.PublicProblemSeeder;
import com.onlinejudge.problem.application.PublicStarterCodeCatalog;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import com.onlinejudge.execution.CodeExecutor;
import com.onlinejudge.execution.ContestLanguageRegistry;
import com.onlinejudge.execution.LocalCodeExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicProblemSeederTest {

    @Test
    void seedsPublicProblemsWithLongDiagnosticStarterCode() {
        assertThat(PublicProblemSeedCatalog.seeds()).hasSize(11);
        assertThat(PublicProblemSeedCatalog.seeds())
                .noneMatch(seed -> seed.title().startsWith("AI评测题："));
        assertThat(PublicProblemSeedCatalog.seeds())
                .allSatisfy(seed -> {
                    assertThat(seed.description()).contains("## 题目描述");
                    assertThat(seed.testCases()).anySatisfy(testCase -> assertThat(testCase.hidden()).isFalse());
                    assertThat(seed.testCases()).anySatisfy(testCase -> assertThat(testCase.hidden()).isTrue());
                    assertThat(seed.starterCode().lines().count()).isGreaterThanOrEqualTo(50);
                    assertThat(seed.starterCode()).doesNotContainPattern("def\\s+helper_\\d+\\s*\\(");
                    assertThat(seed.commonMistakes()).isNotEmpty();
                });
    }

    @Test
    void includesFormalMultiErrorLongCodeBenchmarkProblem() {
        PublicProblemSeed seed = PublicProblemSeedCatalog.seeds()
                .stream()
                .filter(candidate -> candidate.title().equals("潮汐折扣最短路"))
                .findFirst()
                .orElseThrow();

        assertThat(seed.description())
                .contains("时间依赖最短路", "最多 `k` 张折扣券", "ceil(w / 2)");
        assertThat(seed.starterCode().lines().count()).isGreaterThanOrEqualTo(300);
        assertThat(seed.commonMistakes())
                .contains(
                        "把单向边误建成双向边",
                        "等待时间取模公式在 rem > r 时多等一轮",
                        "折扣券对奇数边权使用 floor 而不是 ceil",
                        "用单个节点最优时间错误剪掉不同优惠券状态"
                );
        assertThat(seed.testCases()).hasSize(3);
    }

    @Test
    void publicStarterCodesAreDiagnosticSamplesAndDoNotPassAllSeedTests() {
        LocalCodeExecutor executor = new LocalCodeExecutor();
        List<String> acceptedTitles = new ArrayList<>();

        for (PublicProblemSeed seed : PublicProblemSeedCatalog.seeds()) {
            String starterCode = PublicStarterCodeCatalog.findByTitle(seed.title());
            assertThat(starterCode).as(seed.title()).isNotBlank();

            boolean allAccepted = seed.testCases()
                    .stream()
                    .allMatch(testCase -> matchesExpectedOutput(executor, starterCode, seed, testCase));
            if (allAccepted) {
                acceptedTitles.add(seed.title());
            }
        }

        assertThat(acceptedTitles)
                .as("starterCode should be useful for AI diagnosis, not a passing reference solution")
                .isEmpty();
    }

    @Test
    void backfillsMissingStarterCodeForExistingSeedProblemWithoutOverwritingManualCode() {
        ProblemRepository problemRepository = mock(ProblemRepository.class);
        TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
        List<PublicProblemSeed> seeds = PublicProblemSeedCatalog.seeds();
        List<Problem> existingProblems = existingSeedProblems();
        Problem missingStarter = existingProblems.get(0);
        Problem manualStarter = existingProblems.get(1);
        missingStarter.setStarterCode(null);
        manualStarter.setStarterCode("print('teacher custom starter')\n");
        when(problemRepository.findAllByOrderByIdAsc()).thenReturn(existingProblems);

        new PublicProblemSeeder(problemRepository, testCaseRepository).run();

        assertThat(missingStarter.getStarterCode()).isEqualTo(normalized(seeds.get(0).starterCode()));
        assertThat(manualStarter.getStarterCode()).isEqualTo("print('teacher custom starter')\n");
        verify(problemRepository).save(missingStarter);
        verify(problemRepository, never()).save(manualStarter);
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    void backfillsStarterCodeForLegacyBasicsAndAiLoopProblems() {
        ProblemRepository problemRepository = mock(ProblemRepository.class);
        TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
        List<Problem> existingProblems = new java.util.ArrayList<>(existingSeedProblems());
        Problem twoSum = problem(101L, "两数求和", null);
        Problem aiLoopSum = problem(102L, "AI闭环测试：1 到 n 求和", "n = int(input())\n# 在这里计算 1 到 n 的和\n");
        existingProblems.add(twoSum);
        existingProblems.add(aiLoopSum);
        when(problemRepository.findAllByOrderByIdAsc()).thenReturn(existingProblems);

        new PublicProblemSeeder(problemRepository, testCaseRepository).run();

        assertThat(twoSum.getStarterCode())
                .contains("map(int, input().split())")
                .contains("answer = a");
        assertThat(aiLoopSum.getStarterCode())
                .contains("def sum_to_n")
                .contains("for i in range(1, n):")
                .contains("total += i");
        verify(problemRepository).save(twoSum);
        verify(problemRepository).save(aiLoopSum);
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    void upgradesOriginalSeedStarterCodeToDiagnosticCatalogVersion() {
        ProblemRepository problemRepository = mock(ProblemRepository.class);
        TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
        PublicProblemSeed seed = seedByTitle("双工位装配最短完成时间");
        List<Problem> existingProblems = new ArrayList<>();
        AtomicLong id = new AtomicLong(1);
        PublicProblemSeedCatalog.seeds()
                .stream()
                .filter(candidate -> !candidate.title().equals(seed.title()))
                .map(candidate -> problem(id.getAndIncrement(), candidate.title(), "already curated\n"))
                .forEach(existingProblems::add);
        Problem existingWithOriginalSeedStarter = problem(201L, seed.title(), seed.starterCode());
        existingProblems.add(existingWithOriginalSeedStarter);
        when(problemRepository.findAllByOrderByIdAsc()).thenReturn(existingProblems);

        new PublicProblemSeeder(problemRepository, testCaseRepository).run();

        assertThat(existingWithOriginalSeedStarter.getStarterCode())
                .isEqualTo(normalized(PublicStarterCodeCatalog.findByTitle(seed.title())))
                .isNotEqualTo(normalized(seed.starterCode()));
        verify(problemRepository).save(existingWithOriginalSeedStarter);
        verify(testCaseRepository, never()).save(any());
    }

    private List<Problem> existingSeedProblems() {
        AtomicLong id = new AtomicLong(1);
        return PublicProblemSeedCatalog.seeds()
                .stream()
                .map(seed -> problem(id.getAndIncrement(), seed.title(), "already curated\n"))
                .toList();
    }

    private Problem problem(Long id, String title, String starterCode) {
        return Problem.builder()
                .id(id)
                .title(title)
                .description("description")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(128000)
                .starterCode(starterCode)
                .build();
    }

    private PublicProblemSeed seedByTitle(String title) {
        return PublicProblemSeedCatalog.seeds()
                .stream()
                .filter(seed -> seed.title().equals(title))
                .findFirst()
                .orElseThrow();
    }

    private String normalized(String starterCode) {
        return starterCode.replace("\r\n", "\n").replace('\r', '\n').stripTrailing() + "\n";
    }

    private boolean matchesExpectedOutput(LocalCodeExecutor executor,
                                          String starterCode,
                                          PublicProblemSeed seed,
                                          PublicProblemSeed.TestCaseSeed testCase) {
        CodeExecutor.ExecutionResult result = executor.execute(
                starterCode,
                ContestLanguageRegistry.PYTHON3_ID,
                testCase.input(),
                seed.timeLimit(),
                seed.memoryLimit()
        );
        return result.status == CodeExecutor.ExecutionResult.ResultStatus.SUCCESS
                && normalizeOutput(result.stdout).equals(normalizeOutput(testCase.expectedOutput()));
    }

    private String normalizeOutput(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }
}
