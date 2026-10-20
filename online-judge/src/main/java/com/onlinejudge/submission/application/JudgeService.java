package com.onlinejudge.submission.application;

import com.onlinejudge.execution.CodeExecutor;
import com.onlinejudge.execution.ContestLanguageRegistry;
import com.onlinejudge.classroom.application.StudentRecommendationEventService;
import com.onlinejudge.system.application.ExecutorStatusService;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JudgeService {

    private final CodeExecutor codeExecutor;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisService submissionAnalysisService;
    private final StudentAiFeedbackAsyncService studentAiFeedbackAsyncService;
    private final ExecutorStatusService executorStatusService;
    private final StudentRecommendationEventService recommendationEventService;

    @PostConstruct
    public void init() {
        log.info("JudgeService initialized with executor: {}", codeExecutor.getExecutorType());
    }

    public SubmissionResponse submitCode(SubmissionRequest request) {
        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + request.getProblemId()));

        Optional<ContestLanguageRegistry.ContestLanguage> language =
                ContestLanguageRegistry.findSubmissionLanguage(request.getLanguageId());

        Submission submission = submissionRepository.save(Submission.builder()
                .problemId(problem.getId())
                .assignmentId(request.getAssignmentId())
                .studentProfileId(request.getStudentProfileId())
                .languageId(request.getLanguageId())
                .languageName(language.map(ContestLanguageRegistry.ContestLanguage::displayName).orElse("未知语言"))
                .sourceCode(request.getSourceCode())
                .verdict(Submission.Verdict.PENDING)
                .memoryUsed(0)
                .build());

        if (language.isEmpty()) {
            submission.setVerdict(Submission.Verdict.INTERNAL_ERROR);
            submission.setErrorMessage("暂不支持该语言，当前信息竞赛试点开放：" + ContestLanguageRegistry.supportedLanguageNames());
            return finalizeAndQueue(problem, submission, List.of(), request.getRecommendationToken());
        }

        if (ContestLanguageRegistry.isCpp17(request.getLanguageId()) && !executorStatusService.getStatus().isCpp17Available()) {
            submission.setVerdict(Submission.Verdict.INTERNAL_ERROR);
            submission.setErrorMessage("C++17 评测环境未就绪，请联系老师处理。");
            return finalizeAndQueue(problem, submission, List.of(), request.getRecommendationToken());
        }

        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId());
        if (testCases.isEmpty()) {
            submission.setVerdict(Submission.Verdict.INTERNAL_ERROR);
            submission.setErrorMessage("该题目尚未配置测试点");
            return finalizeAndQueue(problem, submission, List.of(), request.getRecommendationToken());
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
                    return finalizeAndQueue(problem, submission, caseResults, request.getRecommendationToken());
                }
                case TIME_LIMIT_EXCEEDED -> {
                    finalVerdict = Submission.Verdict.TIME_LIMIT_EXCEEDED;
                    submission.setVerdict(finalVerdict);
                    submission.setExecutionTime(millisecondsToSeconds(maxExecutionTimeMs));
                    submission.setMemoryUsed(maxMemoryUsed);
                    caseResults.add(buildCaseResult(index + 1, false, testCase, "", normalizeOutput(testCase.getExpectedOutput()), 0.0, 0));
                    return finalizeAndQueue(problem, submission, caseResults, request.getRecommendationToken());
                }
                case MEMORY_LIMIT_EXCEEDED -> {
                    finalVerdict = Submission.Verdict.MEMORY_LIMIT_EXCEEDED;
                    submission.setVerdict(finalVerdict);
                    submission.setExecutionTime(millisecondsToSeconds(maxExecutionTimeMs));
                    submission.setMemoryUsed(problem.getMemoryLimit());
                    caseResults.add(buildCaseResult(index + 1, false, testCase, "", normalizeOutput(testCase.getExpectedOutput()), 0.0, problem.getMemoryLimit()));
                    return finalizeAndQueue(problem, submission, caseResults, request.getRecommendationToken());
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
                    return finalizeAndQueue(problem, submission, caseResults, request.getRecommendationToken());
                }
                case INTERNAL_ERROR -> {
                    submission.setVerdict(Submission.Verdict.INTERNAL_ERROR);
                    submission.setErrorMessage(executionResult.errorMessage);
                    submission.setExecutionTime(millisecondsToSeconds(maxExecutionTimeMs));
                    submission.setMemoryUsed(maxMemoryUsed);
                    return finalizeAndQueue(problem, submission, caseResults, request.getRecommendationToken());
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
        return finalizeAndQueue(problem, submission, caseResults, request.getRecommendationToken());
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
                .testCaseId(testCase.getId())
                .passed(passed)
                .inputSnapshot(testCase.getInput())
                .actualOutput(actualOutput)
                .expectedOutput(expectedOutput)
                .executionTime(executionTime)
                .memoryUsed(memoryUsed)
                .hidden(Boolean.TRUE.equals(testCase.getIsHidden()))
                .testSemanticCode(testCase.getSemanticCode())
                .testIntentType(testCase.getIntentType())
                .testIntentTitle(testCase.getIntentTitle())
                .testIntentSummary(testCase.getIntentSummary())
                .testLearningObjective(testCase.getLearningObjective())
                .testContestRole(testCase.getContestRole())
                .testRevealPolicy(testCase.getRevealPolicy())
                .build();
    }

    private SubmissionResponse finalizeAndQueue(Problem problem,
                                                Submission submission,
                                                List<SubmissionCaseResult> caseResults,
                                                String recommendationToken) {
        SubmissionResponse response = submissionAnalysisService.finalizeSubmission(problem, submission, caseResults);
        recommendationEventService.recordSubmission(submission, recommendationToken);
        studentAiFeedbackAsyncService.enqueue(response.getId());
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
