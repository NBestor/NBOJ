package com.onlinejudge.submission.application;

import com.onlinejudge.execution.CodeExecutor;
import com.onlinejudge.submission.dto.SubmissionRequest;
import com.onlinejudge.submission.dto.SubmissionResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.problem.domain.TestCase;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JudgeService {

    private static final Map<Integer, String> LANGUAGE_NAMES = Map.of(
            71, "Python 3",
            62, "Java",
            54, "C++ (GCC)",
            63, "JavaScript (Node.js)",
            50, "C (GCC)"
    );

    private final CodeExecutor codeExecutor;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisService submissionAnalysisService;
    private final SubmissionAnalysisAsyncService submissionAnalysisAsyncService;

    @PostConstruct
    public void init() {
        log.info("JudgeService initialized with executor: {}", codeExecutor.getExecutorType());
    }

    public SubmissionResponse submitCode(SubmissionRequest request) {
        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + request.getProblemId()));

        Submission submission = submissionRepository.save(Submission.builder()
                .problemId(problem.getId())
                .languageId(request.getLanguageId())
                .languageName(LANGUAGE_NAMES.getOrDefault(request.getLanguageId(), "未知语言"))
                .sourceCode(request.getSourceCode())
                .verdict(Submission.Verdict.PENDING)
                .memoryUsed(0)
                .build());

        if (!LANGUAGE_NAMES.containsKey(request.getLanguageId())) {
            submission.setVerdict(Submission.Verdict.INTERNAL_ERROR);
            submission.setErrorMessage("暂不支持该语言，当前支持：Python、Java、C++、JavaScript、C");
            return finalizeAndQueue(problem, submission, List.of());
        }

        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId());
        if (testCases.isEmpty()) {
            submission.setVerdict(Submission.Verdict.INTERNAL_ERROR);
            submission.setErrorMessage("该题目尚未配置测试点");
            return finalizeAndQueue(problem, submission, List.of());
        }

        List<SubmissionCaseResult> caseResults = new ArrayList<>();
        Submission.Verdict finalVerdict = Submission.Verdict.ACCEPTED;
        long maxExecutionTimeMs = 0L;
        int maxMemoryUsed = 0;

        for (int index = 0; index < testCases.size(); index++) {
            TestCase testCase = testCases.get(index);

            CodeExecutor.ExecutionResult executionResult = codeExecutor.execute(
                    request.getSourceCode(),
                    request.getLanguageId(),
                    testCase.getInput(),
                    problem.getTimeLimit(),
                    problem.getMemoryLimit()
            );

            maxExecutionTimeMs = Math.max(maxExecutionTimeMs, executionResult.executionTimeMs);

            switch (executionResult.status) {
                case COMPILATION_ERROR -> {
                    submission.setVerdict(Submission.Verdict.COMPILATION_ERROR);
                    submission.setCompileOutput(executionResult.stderr);
                    submission.setExecutionTime(millisecondsToSeconds(maxExecutionTimeMs));
                    submission.setMemoryUsed(maxMemoryUsed);
                    return finalizeAndQueue(problem, submission, caseResults);
                }
                case TIME_LIMIT_EXCEEDED -> {
                    finalVerdict = Submission.Verdict.TIME_LIMIT_EXCEEDED;
                    submission.setVerdict(finalVerdict);
                    submission.setExecutionTime(millisecondsToSeconds(maxExecutionTimeMs));
                    submission.setMemoryUsed(maxMemoryUsed);
                    caseResults.add(buildCaseResult(index + 1, false, testCase, "", normalizeOutput(testCase.getExpectedOutput()), 0.0, 0));
                    return finalizeAndQueue(problem, submission, caseResults);
                }
                case MEMORY_LIMIT_EXCEEDED -> {
                    finalVerdict = Submission.Verdict.MEMORY_LIMIT_EXCEEDED;
                    submission.setVerdict(finalVerdict);
                    submission.setExecutionTime(millisecondsToSeconds(maxExecutionTimeMs));
                    submission.setMemoryUsed(problem.getMemoryLimit());
                    caseResults.add(buildCaseResult(index + 1, false, testCase, "", normalizeOutput(testCase.getExpectedOutput()), 0.0, problem.getMemoryLimit()));
                    return finalizeAndQueue(problem, submission, caseResults);
                }
                case RUNTIME_ERROR -> {
                    finalVerdict = Submission.Verdict.RUNTIME_ERROR;
                    String runtimeOutput = firstNonBlank(executionResult.stdout, executionResult.stderr);
                    submission.setVerdict(finalVerdict);
                    submission.setOutput(normalizeOutput(executionResult.stdout));
                    submission.setErrorMessage(executionResult.stderr);
                    submission.setExecutionTime(millisecondsToSeconds(maxExecutionTimeMs));
                    submission.setMemoryUsed(maxMemoryUsed);
                    caseResults.add(buildCaseResult(
                            index + 1,
                            false,
                            testCase,
                            normalizeOutput(runtimeOutput),
                            normalizeOutput(testCase.getExpectedOutput()),
                            millisecondsToSeconds(executionResult.executionTimeMs),
                            0
                    ));
                    return finalizeAndQueue(problem, submission, caseResults);
                }
                case INTERNAL_ERROR -> {
                    submission.setVerdict(Submission.Verdict.INTERNAL_ERROR);
                    submission.setErrorMessage(executionResult.errorMessage);
                    submission.setExecutionTime(millisecondsToSeconds(maxExecutionTimeMs));
                    submission.setMemoryUsed(maxMemoryUsed);
                    return finalizeAndQueue(problem, submission, caseResults);
                }
                case SUCCESS -> {
                    String actualOutput = normalizeOutput(executionResult.stdout);
                    String expectedOutput = normalizeOutput(testCase.getExpectedOutput());
                    boolean passed = actualOutput.equals(expectedOutput);

                    submission.setOutput(actualOutput);
                    caseResults.add(buildCaseResult(
                            index + 1,
                            passed,
                            testCase,
                            actualOutput,
                            expectedOutput,
                            millisecondsToSeconds(executionResult.executionTimeMs),
                            0
                    ));

                    if (!passed) {
                        finalVerdict = Submission.Verdict.WRONG_ANSWER;
                    }
                }
            }

            if (finalVerdict == Submission.Verdict.WRONG_ANSWER) {
                break;
            }
        }

        submission.setVerdict(finalVerdict);
        submission.setExecutionTime(millisecondsToSeconds(maxExecutionTimeMs));
        submission.setMemoryUsed(maxMemoryUsed);
        return finalizeAndQueue(problem, submission, caseResults);
    }

    public SubmissionResponse getSubmission(Long submissionId) {
        return submissionAnalysisService.getDetailedSubmission(submissionId);
    }

    private SubmissionCaseResult buildCaseResult(int testCaseNumber,
                                                 boolean passed,
                                                 TestCase testCase,
                                                 String actualOutput,
                                                 String expectedOutput,
                                                 double executionTime,
                                                 int memoryUsed) {
        return SubmissionCaseResult.builder()
                .testCaseNumber(testCaseNumber)
                .passed(passed)
                .inputSnapshot(testCase.getInput())
                .actualOutput(actualOutput)
                .expectedOutput(expectedOutput)
                .executionTime(executionTime)
                .memoryUsed(memoryUsed)
                .hidden(Boolean.TRUE.equals(testCase.getIsHidden()))
                .build();
    }

    private SubmissionResponse finalizeAndQueue(Problem problem,
                                                Submission submission,
                                                List<SubmissionCaseResult> caseResults) {
        SubmissionResponse response = submissionAnalysisService.finalizeSubmission(problem, submission, caseResults);
        submissionAnalysisAsyncService.enqueue(response.getId());
        return response;
    }

    private double millisecondsToSeconds(long milliseconds) {
        return milliseconds / 1000.0;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }

    private String normalizeOutput(String output) {
        if (output == null) {
            return "";
        }
        return output.trim()
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }
}

