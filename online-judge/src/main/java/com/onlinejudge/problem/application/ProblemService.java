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
import org.springframework.beans.factory.annotation.Autowired;
import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.shared.web.PlatformApiException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("[#>*`_\\n]");

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionCaseResultRepository submissionCaseResultRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;

    @Autowired(required = false)
    private CurrentTeacherContext currentTeacherContext;
    @Autowired(required = false)
    private ProblemAccessPolicy problemAccessPolicy;

    public List<ProblemResponse> getAllProblems() {
        return problemRepository.findAllByOrderByIdAsc()
                .stream()
                .filter(this::publicVisible)
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
        if (!publicVisible(problem)) {
            throw new PlatformApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "题目不存在");
        }
        
        List<TestCase> visibleTestCases = testCaseRepository
                .findByProblemIdAndIsHiddenFalseOrderByOrderIndexAsc(id);
        
        return ProblemResponse.from(problem, visibleTestCases);
    }

    public ProblemManageResponse getProblemForManage(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + id));
        requireEditableOrVisible(problem);

        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(id);
        return ProblemManageResponse.from(problem, testCases);
    }

    @Transactional
    public ProblemResponse createProblem(CreateProblemRequest request) {
        validateVisibleSamples(request);

        Problem problem = problemRepository.save(Problem.builder()
                .ownerTeacherId(currentTeacherId())
                .scope(Problem.Scope.PRIVATE)
                .versionState(Problem.VersionState.DRAFT)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .difficulty(request.getDifficulty())
                .timeLimit(request.getTimeLimit())
                .memoryLimit(request.getMemoryLimit())
                .aiPromptDirection(normalizePromptDirection(request.getAiPromptDirection()))
                .starterCode(normalizeStarterCode(request.getStarterCode()))
                .knowledgePoints(normalizeList(request.getKnowledgePoints()))
                .algorithmStrategies(normalizeList(request.getAlgorithmStrategies()))
                .commonMistakes(normalizeList(request.getCommonMistakes()))
                .boundaryTypes(normalizeList(request.getBoundaryTypes()))
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
        if (problemAccessPolicy != null && !problemAccessPolicy.canEdit(currentTeacherId(), problem)) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "只能编辑自己的私有草稿");
        }

        problem.setTitle(request.getTitle().trim());
        problem.setDescription(request.getDescription().trim());
        problem.setDifficulty(request.getDifficulty());
        problem.setTimeLimit(request.getTimeLimit());
        problem.setMemoryLimit(request.getMemoryLimit());
        problem.setAiPromptDirection(normalizePromptDirection(request.getAiPromptDirection()));
        problem.setStarterCode(normalizeStarterCode(request.getStarterCode()));
        problem.setKnowledgePoints(normalizeList(request.getKnowledgePoints()));
        problem.setAlgorithmStrategies(normalizeList(request.getAlgorithmStrategies()));
        problem.setCommonMistakes(normalizeList(request.getCommonMistakes()));
        problem.setBoundaryTypes(normalizeList(request.getBoundaryTypes()));

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
        if (!java.util.Objects.equals(problem.getOwnerTeacherId(), currentTeacherId())
                || problem.getScope() != Problem.Scope.PRIVATE) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "只能归档自己的私有题目");
        }
        problem.setVersionState(Problem.VersionState.ARCHIVED);
        problem.setArchivedAt(java.time.LocalDateTime.now());
        problemRepository.save(problem);
        return problem;
    }

    private boolean publicVisible(Problem problem) {
        if (problemAccessPolicy != null) return problemAccessPolicy.isAnonymousCatalogVisible(problem);
        return problem.getArchivedAt() == null && problem.getScope() == Problem.Scope.PUBLIC
                && problem.getVersionState() == Problem.VersionState.PUBLISHED;
    }

    private void requireEditableOrVisible(Problem problem) {
        if (problemAccessPolicy != null && !problemAccessPolicy.isTeacherVisible(currentTeacherId(), problem)) {
            throw new PlatformApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "题目不存在");
        }
    }

    private java.util.UUID currentTeacherId() {
        return currentTeacherContext == null ? TeacherAccount.BOOTSTRAP_ADMIN_ID : currentTeacherContext.requireTeacherId();
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

    private String normalizeStarterCode(String starterCode) {
        if (starterCode == null) {
            return null;
        }

        String normalized = starterCode.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
        return normalized.isBlank() ? null : normalized + "\n";
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(12)
                .toList();
    }
}
