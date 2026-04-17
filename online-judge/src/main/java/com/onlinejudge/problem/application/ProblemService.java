package com.onlinejudge.problem.application;

import com.onlinejudge.problem.dto.CreateProblemRequest;
import com.onlinejudge.problem.dto.ProblemCatalogItemResponse;
import com.onlinejudge.problem.dto.ProblemManageResponse;
import com.onlinejudge.problem.dto.ProblemResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.problem.domain.TestCase;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("[#>*`_\\-\\n]");

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionCaseResultRepository submissionCaseResultRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;

    public List<ProblemResponse> getAllProblems() {
        return problemRepository.findAllByOrderByIdAsc()
                .stream()
                .map(p -> ProblemResponse.from(p, List.of()))
                .toList();
    }

    public List<ProblemCatalogItemResponse> getProblemCatalog() {
        return problemRepository.findCatalogItems()
                .stream()
                .map(problem -> ProblemCatalogItemResponse.builder()
                        .id(problem.getId())
                        .title(problem.getTitle())
                        .summary(extractSummary(problem.getDescription()))
                        .difficulty(problem.getDifficulty())
                        .timeLimit(problem.getTimeLimit())
                        .memoryLimit(problem.getMemoryLimit())
                        .createdAt(problem.getCreatedAt())
                        .build())
                .toList();
    }

    public ProblemResponse getProblemById(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + id));
        
        List<TestCase> visibleTestCases = testCaseRepository
                .findByProblemIdAndIsHiddenFalseOrderByOrderIndexAsc(id);
        
        return ProblemResponse.from(problem, visibleTestCases);
    }

    public ProblemManageResponse getProblemForManage(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + id));

        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(id);
        return ProblemManageResponse.from(problem, testCases);
    }

    @Transactional
    public ProblemResponse createProblem(CreateProblemRequest request) {
        validateVisibleSamples(request);

        Problem problem = problemRepository.save(Problem.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .difficulty(request.getDifficulty())
                .timeLimit(request.getTimeLimit())
                .memoryLimit(request.getMemoryLimit())
                .aiPromptDirection(normalizePromptDirection(request.getAiPromptDirection()))
                .build());

        List<TestCase> savedTestCases = saveTestCases(problem.getId(), request);

        List<TestCase> visibleTestCases = savedTestCases.stream()
                .filter(testCase -> !Boolean.TRUE.equals(testCase.getIsHidden()))
                .toList();

        return ProblemResponse.from(problem, visibleTestCases);
    }

    @Transactional
    public ProblemResponse updateProblem(Long problemId, CreateProblemRequest request) {
        validateVisibleSamples(request);

        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + problemId));

        problem.setTitle(request.getTitle().trim());
        problem.setDescription(request.getDescription().trim());
        problem.setDifficulty(request.getDifficulty());
        problem.setTimeLimit(request.getTimeLimit());
        problem.setMemoryLimit(request.getMemoryLimit());
        problem.setAiPromptDirection(normalizePromptDirection(request.getAiPromptDirection()));

        Problem savedProblem = problemRepository.save(problem);
        testCaseRepository.deleteByProblemId(problemId);
        List<TestCase> savedTestCases = saveTestCases(problemId, request);

        List<TestCase> visibleTestCases = savedTestCases.stream()
                .filter(testCase -> !Boolean.TRUE.equals(testCase.getIsHidden()))
                .toList();

        return ProblemResponse.from(savedProblem, visibleTestCases);
    }

    @Transactional
    public Problem deleteProblem(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + problemId));

        List<Long> submissionIds = submissionRepository.findByProblemIdOrderBySubmittedAtDesc(problemId)
                .stream()
                .map(Submission::getId)
                .toList();

        if (!submissionIds.isEmpty()) {
            submissionAnalysisRepository.deleteBySubmissionIdIn(submissionIds);
            submissionCaseResultRepository.deleteBySubmissionIdIn(submissionIds);
        }

        submissionRepository.deleteByProblemId(problemId);
        testCaseRepository.deleteByProblemId(problemId);
        problemRepository.delete(problem);
        return problem;
    }

    private void validateVisibleSamples(CreateProblemRequest request) {
        long visibleSamples = request.getTestCases().stream()
                .filter(testCase -> !Boolean.TRUE.equals(testCase.getHidden()))
                .count();

        if (visibleSamples == 0) {
            throw new IllegalArgumentException("至少需要一个可见样例测试点");
        }
    }

    private List<TestCase> saveTestCases(Long problemId, CreateProblemRequest request) {
        return IntStream.range(0, request.getTestCases().size())
                .mapToObj(index -> {
                    CreateProblemRequest.TestCaseRequest testCase = request.getTestCases().get(index);
                    return TestCase.builder()
                            .problemId(problemId)
                            .input(testCase.getInput())
                            .expectedOutput(testCase.getExpectedOutput())
                            .isHidden(Boolean.TRUE.equals(testCase.getHidden()))
                            .orderIndex(index)
                            .build();
                })
                .map(testCaseRepository::save)
                .toList();
    }

    private String extractSummary(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "查看题目说明、样例和评测限制。";
        }

        String summary = CODE_BLOCK_PATTERN.matcher(markdown).replaceAll(" ");
        summary = MARKDOWN_PATTERN.matcher(summary).replaceAll(" ");
        summary = summary.replaceAll("\\s+", " ").trim();

        if (summary.isBlank()) {
            return "查看题目说明、样例和评测限制。";
        }

        return summary.length() > 96 ? summary.substring(0, 96) + "..." : summary;
    }

    private String normalizePromptDirection(String promptDirection) {
        if (promptDirection == null) {
            return null;
        }

        String trimmed = promptDirection.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}

