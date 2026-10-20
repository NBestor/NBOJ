package com.onlinejudge.classroom.application;

import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrajectorySignalAnalyzerTest {

    private final TrajectorySignalAnalyzer analyzer = new TrajectorySignalAnalyzer();

    @Test
    void detectsLargeRepeatedCodeChangesWithSameIssue() {
        Submission latest = submission(3L, """
                data = input().split()
                result = []
                for item in data:
                    result.append(item.strip())
                print(",".join(result))
                """);
        Submission previous = submission(2L, """
                values = list(map(int, input().split()))
                total = 0
                for value in values:
                    total += value
                print(total)
                """);
        Submission first = submission(1L, """
                n = int(input())
                arr = []
                for i in range(n):
                    arr.append(input())
                print(len(arr))
                """);

        String signal = analyzer.detectLargeChangeSameIssue(
                List.of(latest, previous, first),
                Map.of(
                        1L, analysis(1L, "WA"),
                        2L, analysis(2L, "WA"),
                        3L, analysis(3L, "WA")
                )
        );

        assertThat(signal).contains("多次大幅修改代码");
        assertThat(signal).contains("WA");
    }

    @Test
    void ignoresSmallChangesWithSameIssue() {
        Submission latest = submission(3L, """
                n = int(input())
                print(n + 2)
                """);
        Submission previous = submission(2L, """
                n = int(input())
                print(n + 1)
                """);
        Submission first = submission(1L, """
                n = int(input())
                print(n)
                """);

        String signal = analyzer.detectLargeChangeSameIssue(
                List.of(latest, previous, first),
                Map.of(
                        1L, analysis(1L, "WA"),
                        2L, analysis(2L, "WA"),
                        3L, analysis(3L, "WA")
                )
        );

        assertThat(signal).isEmpty();
    }

    @Test
    void ignoresChangedIssueBecauseTrajectoryMovedForward() {
        Submission latest = submission(3L, "print(1)");
        Submission previous = submission(2L, "for i in range(10): print(i)");
        Submission first = submission(1L, "a = input(); b = input(); print(a + b)");

        String signal = analyzer.detectLargeChangeSameIssue(
                List.of(latest, previous, first),
                Map.of(
                        1L, analysis(1L, "CE"),
                        2L, analysis(2L, "WA"),
                        3L, analysis(3L, "TLE")
                )
        );

        assertThat(signal).isEmpty();
    }

    private Submission submission(Long id, String sourceCode) {
        return Submission.builder()
                .id(id)
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode(sourceCode)
                .submittedAt(LocalDateTime.now().minusMinutes(10 - id))
                .build();
    }

    private SubmissionAnalysis analysis(Long submissionId, String scenario) {
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .scenario(scenario)
                .headline(scenario)
                .build();
    }
}
