package com.onlinejudge.shared.bootstrap;

import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.domain.TestCase;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    @Override
    public void run(String... args) {
        if (problemRepository.count() > 0) {
            int upgraded = localizeBuiltInProblems();
            if (upgraded > 0) {
                log.info("Localized {} built-in problems", upgraded);
            } else {
                log.info("Sample problems already initialized, skipping seed data");
            }
            return;
        }

        log.info("Initializing sample problems...");

        createProblem(
                "两数求和",
                """
                ## 题目描述
                给定两个整数 `a` 和 `b`，输出它们的和。

                ## 输入格式
                一行两个整数 `a b`，满足 `-1000 <= a, b <= 1000`。

                ## 输出格式
                输出一个整数，表示 `a + b` 的结果。

                ## 样例
                **输入**
                ```
                3 5
                ```
                **输出**
                ```
                8
                ```
                """,
                Problem.Difficulty.EASY,
                1000,
                128000,
                List.of(
                        testcase("3 5", "8", false, 0),
                        testcase("-10 20", "10", false, 1),
                        testcase("0 0", "0", true, 2),
                        testcase("-500 500", "0", true, 3)
                )
        );

        createProblem(
                "回文判断",
                """
                ## 题目描述
                给定一个字符串，判断它是否为回文串。回文串正着读和反着读都相同。

                ## 输入格式
                一行字符串 `s`，长度满足 `1 <= |s| <= 100`，仅由小写英文字母组成。

                ## 输出格式
                如果是回文串输出 `YES`，否则输出 `NO`。

                ## 样例
                **输入**
                ```
                racecar
                ```
                **输出**
                ```
                YES
                ```
                """,
                Problem.Difficulty.EASY,
                1000,
                128000,
                List.of(
                        testcase("racecar", "YES", false, 0),
                        testcase("hello", "NO", false, 1),
                        testcase("a", "YES", true, 2),
                        testcase("abba", "YES", true, 3)
                )
        );

        createProblem(
                "FizzBuzz",
                """
                ## 题目描述
                给定一个整数 `n`：

                - 如果同时能被 3 和 5 整除，输出 `FizzBuzz`
                - 如果只能被 3 整除，输出 `Fizz`
                - 如果只能被 5 整除，输出 `Buzz`
                - 否则输出它本身

                ## 输入格式
                一个整数 `n`，满足 `1 <= n <= 1000`。

                ## 输出格式
                输出对应的结果。

                ## 样例
                **输入**
                ```
                15
                ```
                **输出**
                ```
                FizzBuzz
                ```
                """,
                Problem.Difficulty.EASY,
                1000,
                128000,
                List.of(
                        testcase("15", "FizzBuzz", false, 0),
                        testcase("9", "Fizz", false, 1),
                        testcase("10", "Buzz", false, 2),
                        testcase("7", "7", true, 3),
                        testcase("30", "FizzBuzz", true, 4)
                )
        );

        createProblem(
                "阶乘计算",
                """
                ## 题目描述
                计算非负整数 `n` 的阶乘。

                阶乘定义为：`n! = 1 * 2 * 3 * ... * n`，其中 `0! = 1`。

                ## 输入格式
                一个非负整数 `n`，满足 `0 <= n <= 12`。

                ## 输出格式
                输出 `n!` 的值。

                ## 样例
                **输入**
                ```
                5
                ```
                **输出**
                ```
                120
                ```
                """,
                Problem.Difficulty.MEDIUM,
                1000,
                128000,
                List.of(
                        testcase("5", "120", false, 0),
                        testcase("0", "1", false, 1),
                        testcase("10", "3628800", true, 2),
                        testcase("12", "479001600", true, 3)
                )
        );

        createProblem(
                "质数判断",
                """
                ## 题目描述
                给定一个正整数 `n`，判断它是否为质数。

                质数是大于 1 且只能被 1 和自身整除的自然数。

                ## 输入格式
                一个正整数 `n`，满足 `1 <= n <= 10^6`。

                ## 输出格式
                如果 `n` 是质数输出 `YES`，否则输出 `NO`。

                ## 样例
                **输入**
                ```
                17
                ```
                **输出**
                ```
                YES
                ```
                """,
                Problem.Difficulty.MEDIUM,
                2000,
                128000,
                List.of(
                        testcase("17", "YES", false, 0),
                        testcase("1", "NO", false, 1),
                        testcase("2", "YES", false, 2),
                        testcase("999983", "YES", true, 3),
                        testcase("100", "NO", true, 4)
                )
        );

        log.info("Initialized {} sample problems", problemRepository.count());
    }

    private void createProblem(String title,
                               String description,
                               Problem.Difficulty difficulty,
                               int timeLimit,
                               int memoryLimit,
                               List<TestCaseSeed> testCases) {
        Problem problem = problemRepository.save(Problem.builder()
                .title(title)
                .description(description)
                .difficulty(difficulty)
                .timeLimit(timeLimit)
                .memoryLimit(memoryLimit)
                .build());

        testCases.forEach(seed -> testCaseRepository.save(TestCase.builder()
                .problemId(problem.getId())
                .input(seed.input())
                .expectedOutput(seed.expectedOutput())
                .isHidden(seed.hidden())
                .orderIndex(seed.orderIndex())
                .build()));
    }

    private int localizeBuiltInProblems() {
        int updated = 0;
        for (Problem problem : problemRepository.findAllByOrderByIdAsc()) {
            LocalizedProblem localized = lookupLocalization(problem.getTitle());
            if (localized == null) {
                continue;
            }

            if (!localized.title().equals(problem.getTitle()) || !localized.description().equals(problem.getDescription())) {
                problem.setTitle(localized.title());
                problem.setDescription(localized.description());
                problemRepository.save(problem);
                updated++;
            }
        }
        return updated;
    }

    private LocalizedProblem lookupLocalization(String title) {
        return switch (title) {
            case "Two Sum" -> new LocalizedProblem("两数求和", """
                    ## 题目描述
                    给定两个整数 `a` 和 `b`，输出它们的和。

                    ## 输入格式
                    一行两个整数 `a b`，满足 `-1000 <= a, b <= 1000`。

                    ## 输出格式
                    输出一个整数，表示 `a + b` 的结果。

                    ## 样例
                    **输入**
                    ```
                    3 5
                    ```
                    **输出**
                    ```
                    8
                    ```
                    """);
            case "Palindrome Check" -> new LocalizedProblem("回文判断", """
                    ## 题目描述
                    给定一个字符串，判断它是否为回文串。回文串正着读和反着读都相同。

                    ## 输入格式
                    一行字符串 `s`，长度满足 `1 <= |s| <= 100`，仅由小写英文字母组成。

                    ## 输出格式
                    如果是回文串输出 `YES`，否则输出 `NO`。

                    ## 样例
                    **输入**
                    ```
                    racecar
                    ```
                    **输出**
                    ```
                    YES
                    ```
                    """);
            case "FizzBuzz" -> new LocalizedProblem("FizzBuzz", """
                    ## 题目描述
                    给定一个整数 `n`：

                    - 如果同时能被 3 和 5 整除，输出 `FizzBuzz`
                    - 如果只能被 3 整除，输出 `Fizz`
                    - 如果只能被 5 整除，输出 `Buzz`
                    - 否则输出它本身

                    ## 输入格式
                    一个整数 `n`，满足 `1 <= n <= 1000`。

                    ## 输出格式
                    输出对应的结果。

                    ## 样例
                    **输入**
                    ```
                    15
                    ```
                    **输出**
                    ```
                    FizzBuzz
                    ```
                    """);
            case "Factorial" -> new LocalizedProblem("阶乘计算", """
                    ## 题目描述
                    计算非负整数 `n` 的阶乘。

                    阶乘定义为：`n! = 1 * 2 * 3 * ... * n`，其中 `0! = 1`。

                    ## 输入格式
                    一个非负整数 `n`，满足 `0 <= n <= 12`。

                    ## 输出格式
                    输出 `n!` 的值。

                    ## 样例
                    **输入**
                    ```
                    5
                    ```
                    **输出**
                    ```
                    120
                    ```
                    """);
            case "Prime Number Check" -> new LocalizedProblem("质数判断", """
                    ## 题目描述
                    给定一个正整数 `n`，判断它是否为质数。

                    质数是大于 1 且只能被 1 和自身整除的自然数。

                    ## 输入格式
                    一个正整数 `n`，满足 `1 <= n <= 10^6`。

                    ## 输出格式
                    如果 `n` 是质数输出 `YES`，否则输出 `NO`。

                    ## 样例
                    **输入**
                    ```
                    17
                    ```
                    **输出**
                    ```
                    YES
                    ```
                    """);
            default -> null;
        };
    }

    private TestCaseSeed testcase(String input, String expectedOutput, boolean hidden, int orderIndex) {
        return new TestCaseSeed(input, expectedOutput, hidden, orderIndex);
    }

    private record TestCaseSeed(String input, String expectedOutput, boolean hidden, int orderIndex) {
    }

    private record LocalizedProblem(String title, String description) {
    }
}

