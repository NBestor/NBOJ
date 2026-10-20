package com.onlinejudge.submission.application;

import com.onlinejudge.classroom.application.StudentRecommendationEventService;
import com.onlinejudge.execution.CodeExecutor;
import com.onlinejudge.execution.ContestLanguageRegistry;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.domain.TestCase;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionRequest;
import com.onlinejudge.submission.dto.SubmissionResponse;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import com.onlinejudge.system.application.ExecutorStatusService;
import com.onlinejudge.system.dto.ExecutorStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JudgeServiceCpp17Test {

    private final CodeExecutor codeExecutor = mock(CodeExecutor.class);
    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final SubmissionAnalysisService submissionAnalysisService = mock(SubmissionAnalysisService.class);
    private final StudentAiFeedbackAsyncService studentAiFeedbackAsyncService = mock(StudentAiFeedbackAsyncService.class);
    private final ExecutorStatusService executorStatusService = mock(ExecutorStatusService.class);
    private final StudentRecommendationEventService recommendationEventService = mock(StudentRecommendationEventService.class);
    private final AtomicLong submissionIds = new AtomicLong(100L);

    private JudgeService judgeService;

    @BeforeEach
    void setUp() {
        judgeService = new JudgeService(
                codeExecutor,
                problemRepository,
                testCaseRepository,
                submissionRepository,
                submissionAnalysisService,
                studentAiFeedbackAsyncService,
                executorStatusService,
                recommendationEventService
        );
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem()));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            if (submission.getId() == null) {
                submission.setId(submissionIds.incrementAndGet());
            }
            return submission;
        });
        when(executorStatusService.getStatus()).thenReturn(status(true));
        when(submissionAnalysisService.finalizeSubmission(any(Problem.class), any(Submission.class), anyList()))
                .thenAnswer(invocation -> response(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    }

    @Test
    void acceptsCpp17SubmissionAndRecordsJudgeFacts() {
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(testCase("7\n", "7\n")));
        when(codeExecutor.execute(eq(cpp17Source()), eq(ContestLanguageRegistry.CPP17_ID), eq("7\n"), eq(1000), eq(65536)))
                .thenReturn(new CodeExecutor.ExecutionResult("7\n", "", 0, 12));

        SubmissionResponse response = judgeService.submitCode(request(cpp17Source()));

        assertThat(response.getLanguageId()).isEqualTo(ContestLanguageRegistry.CPP17_ID);
        assertThat(response.getLanguageName()).isEqualTo("C++17");
        assertThat(response.getVerdict()).isEqualTo(Submission.Verdict.ACCEPTED);
        assertThat(response.getTestCaseResults()).singleElement()
                .satisfies(result -> {
                    assertThat(result.isPassed()).isTrue();
                    assertThat(result.getActualOutput()).isEqualTo("7");
                    assertThat(result.getExpectedOutput()).isEqualTo("7");
        });
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionCaseResult>> caseResultsCaptor = ArgumentCaptor.forClass(List.class);
        verify(submissionAnalysisService).finalizeSubmission(
                any(Problem.class),
                any(Submission.class),
                caseResultsCaptor.capture()
        );
        assertThat(caseResultsCaptor.getValue()).singleElement()
                .satisfies(result -> {
                    assertThat(result.getTestCaseId()).isEqualTo(81L);
                    assertThat(result.getTestSemanticCode()).isEqualTo("TCI_ECHO_REPRESENTATIVE");
                    assertThat(result.getTestIntentType()).isEqualTo("REPRESENTATIVE");
                    assertThat(result.getTestIntentSummary()).contains("普通整数");
                    assertThat(result.getTestLearningObjective()).contains("读取并输出");
                    assertThat(result.getTestContestRole()).isEqualTo("SAMPLE_EXPLANATION");
                    assertThat(result.getTestRevealPolicy()).isEqualTo("PUBLIC_EXAMPLE");
                });
        verify(studentAiFeedbackAsyncService).enqueue(response.getId());
    }

    @Test
    void recordsCpp17CompilationError() {
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(testCase("7\n", "7\n")));
        when(codeExecutor.execute(eq("int main() {"), eq(ContestLanguageRegistry.CPP17_ID), eq("7\n"), eq(1000), eq(65536)))
                .thenReturn(CodeExecutor.ExecutionResult.compilationError("solution.cpp: error: expected '}'"));

        SubmissionResponse response = judgeService.submitCode(request("int main() {"));

        assertThat(response.getVerdict()).isEqualTo(Submission.Verdict.COMPILATION_ERROR);
        assertThat(response.getCompileOutput()).contains("solution.cpp", "expected");
        assertThat(response.getTestCaseResults()).isEmpty();
    }

    @Test
    void blocksCpp17WhenExecutionEnvironmentIsMissing() {
        when(executorStatusService.getStatus()).thenReturn(status(false));

        SubmissionResponse response = judgeService.submitCode(request(cpp17Source()));

        assertThat(response.getLanguageName()).isEqualTo("C++17");
        assertThat(response.getVerdict()).isEqualTo(Submission.Verdict.INTERNAL_ERROR);
        assertThat(response.getErrorMessage()).contains("C++17 评测环境未就绪", "联系老师");
        verify(codeExecutor, never()).execute(any(), eq(ContestLanguageRegistry.CPP17_ID), any(), anyInt(), anyInt());
        verify(testCaseRepository, never()).findByProblemIdOrderByOrderIndexAsc(any());
    }

    @Test
    void rejectsUnsupportedCBeforeJudgeExecution() {
        SubmissionRequest request = request("int main(void) { return 0; }");
        request.setLanguageId(50);

        SubmissionResponse response = judgeService.submitCode(request);

        assertThat(response.getLanguageName()).isEqualTo("未知语言");
        assertThat(response.getVerdict()).isEqualTo(Submission.Verdict.INTERNAL_ERROR);
        assertThat(response.getErrorMessage()).contains("暂不支持该语言", "C++17", "Python 3");
        verify(codeExecutor, never()).execute(any(), anyInt(), any(), anyInt(), anyInt());
    }

    private SubmissionResponse response(Problem problem, Submission submission, List<SubmissionCaseResult> caseResults) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .problemId(submission.getProblemId())
                .problemTitle(problem.getTitle())
                .languageId(submission.getLanguageId())
                .languageName(submission.getLanguageName())
                .sourceCode(submission.getSourceCode())
                .verdict(submission.getVerdict())
                .executionTime(submission.getExecutionTime())
                .memoryUsed(submission.getMemoryUsed())
                .output(submission.getOutput())
                .compileOutput(submission.getCompileOutput())
                .errorMessage(submission.getErrorMessage())
                .testCaseResults(caseResults.stream()
                        .map(result -> SubmissionResponse.TestCaseResult.builder()
                                .testCaseNumber(result.getTestCaseNumber())
                                .passed(Boolean.TRUE.equals(result.getPassed()))
                                .actualOutput(result.getActualOutput())
                                .expectedOutput(result.getExpectedOutput())
                                .executionTime(result.getExecutionTime())
                                .memoryUsed(result.getMemoryUsed())
                                .hidden(Boolean.TRUE.equals(result.getHidden()))
                                .build())
                        .toList())
                .build();
    }

    private SubmissionRequest request(String sourceCode) {
        SubmissionRequest request = new SubmissionRequest();
        request.setProblemId(1L);
        request.setLanguageId(ContestLanguageRegistry.CPP17_ID);
        request.setSourceCode(sourceCode);
        return request;
    }

    private Problem problem() {
        return Problem.builder()
                .id(1L)
                .title("回显整数")
                .description("输入一个整数并输出。")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
    }

    private TestCase testCase(String input, String expectedOutput) {
        return TestCase.builder()
                .id(81L)
                .problemId(1L)
                .input(input)
                .expectedOutput(expectedOutput)
                .isHidden(false)
                .orderIndex(1)
                .semanticCode("TCI_ECHO_REPRESENTATIVE")
                .intentType("REPRESENTATIVE")
                .intentTitle("普通整数回显")
                .intentSummary("覆盖普通整数的读取与原样输出流程。")
                .learningObjective("能按题面顺序读取并输出一个整数。")
                .contestRole("SAMPLE_EXPLANATION")
                .revealPolicy("PUBLIC_EXAMPLE")
                .build();
    }

    private ExecutorStatusResponse status(boolean cpp17Available) {
        return ExecutorStatusResponse.builder()
                .mode("local")
                .executorType("TEST")
                .pythonAvailable(true)
                .cppAvailable(cpp17Available)
                .cpp17Available(cpp17Available)
                .message(cpp17Available ? "C++17 可用" : "C++17 未就绪")
                .build();
    }

    private String cpp17Source() {
        return """
                #include <bits/stdc++.h>
                using namespace std;

                int main() {
                    ios::sync_with_stdio(false);
                    cin.tie(nullptr);
                    int n;
                    cin >> n;
                    cout << n << '\\n';
                    return 0;
                }
                """;
    }
}
