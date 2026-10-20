package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.application.AbilitySignalAnalyzer;
import com.onlinejudge.classroom.application.HintSafetyService;
import com.onlinejudge.classroom.application.StudentRecommendationEventService;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisLookupResponse;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.dto.SubmissionHistorySummaryResponse;
import com.onlinejudge.submission.dto.SubmissionGrowthSummaryResponse;
import com.onlinejudge.submission.dto.SubmissionResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.StudentAiFeedback;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultStatsProjection;
import com.onlinejudge.submission.persistence.SubmissionHistoryProjection;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionAnalysisService {

    private static final String ANALYSIS_SOURCE = "RULE_BASED_V1";
    private static final Pattern LOOP_PATTERN = Pattern.compile("\\b(for|while)\\b");
    private static final Pattern RECURSION_PATTERN = Pattern.compile("\\b(recursion|dfs|bfs)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_PATTERN = Pattern.compile("\\b(new\\s+\\w+\\s*\\[|vector<|ArrayList|HashMap|HashSet|Map<|Set<|StringBuilder|malloc|calloc)\\b");
    private static final int LEARNING_MEMORY_WINDOW = 30;

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionCaseResultRepository submissionCaseResultRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final StudentAiFeedbackRepository studentAiFeedbackRepository;
    private final ObjectMapper objectMapper;
    private final AiCodeAssistSupport aiCodeAssistSupport;
    private final AssignmentRepository assignmentRepository;
    private final DiagnosisTaxonomy diagnosisTaxonomy;
    private final DiagnosticAgentService diagnosticAgentService;
    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisEvidencePackageReader diagnosisEvidencePackageReader;
    private final StudentRecommendationEventService recommendationEventService;
    private final AbilitySignalAnalyzer abilitySignalAnalyzer;
    private final TeacherDiagnosisCorrectionRepository teacherDiagnosisCorrectionRepository;
    private final SubmissionDiagnosisFactProjector submissionDiagnosisFactProjector;
    private AiDiagnosisWorkflowService diagnosisWorkflowService;
    private SubmissionGrowthSummaryService growthSummaryService;
    private Executor diagnosisBranchExecutor = Runnable::run;

    @Autowired(required = false)
    void setDiagnosisWorkflowService(AiDiagnosisWorkflowService diagnosisWorkflowService) {
        this.diagnosisWorkflowService = diagnosisWorkflowService;
    }

    @Autowired(required = false)
    void setGrowthSummaryService(SubmissionGrowthSummaryService growthSummaryService) {
        this.growthSummaryService = growthSummaryService;
    }

    @Autowired(required = false)
    void setDiagnosisBranchExecutor(@Qualifier("aiDiagnosisBranchExecutor") Executor diagnosisBranchExecutor) {
        if (diagnosisBranchExecutor != null) {
            this.diagnosisBranchExecutor = diagnosisBranchExecutor;
        }
    }

    @Transactional
    public SubmissionResponse finalizeSubmission(Problem problem, Submission submission, List<SubmissionCaseResult> caseResults) {
        Submission savedSubmission = submissionRepository.save(submission);

        submissionCaseResultRepository.deleteBySubmissionId(savedSubmission.getId());
        if (!caseResults.isEmpty()) {
            caseResults.forEach(result -> result.setSubmissionId(savedSubmission.getId()));
            submissionCaseResultRepository.saveAll(caseResults);
        }

        List<SubmissionCaseResult> storedCaseResults = submissionCaseResultRepository
                .findBySubmissionIdOrderByTestCaseNumberAsc(savedSubmission.getId());
        SubmissionAnalysisResponse analysis = findExistingAnalysis(savedSubmission.getId());
        return buildSubmissionResponse(savedSubmission, problem, storedCaseResults, analysis);
    }

    public SubmissionResponse getDetailedSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));

        Problem problem = problemRepository.findById(submission.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + submission.getProblemId()));

        List<SubmissionCaseResult> caseResults = submissionCaseResultRepository
                .findBySubmissionIdOrderByTestCaseNumberAsc(submissionId);
        SubmissionAnalysisResponse analysis = findExistingAnalysis(submissionId);
        return buildSubmissionResponse(submission, problem, caseResults, analysis);
    }

    public List<SubmissionHistorySummaryResponse> getSubmissionHistorySummaries(Long problemId) {
        return getSubmissionHistorySummaries(problemId, null);
    }

    public List<SubmissionHistorySummaryResponse> getSubmissionHistorySummaries(Long problemId, Long studentProfileId) {
        return getSubmissionHistorySummaries(problemId, null, studentProfileId);
    }

    public List<SubmissionHistorySummaryResponse> getSubmissionHistorySummaries(Long problemId,
                                                                                Long assignmentId,
                                                                                Long studentProfileId) {
        String problemTitle = problemRepository.findTitleById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + problemId));

        Problem problem = Problem.builder()
                .title(problemTitle)
                .build();

        List<SubmissionHistoryProjection> submissions;
        if (studentProfileId == null) {
            submissions = List.of();
        } else if (assignmentId == null) {
            submissions = submissionRepository.findPublicHistorySummariesByProblemIdAndStudentProfileId(
                    problemId,
                    studentProfileId
            );
        } else {
            submissions = submissionRepository.findHistorySummariesByAssignmentIdAndProblemIdAndStudentProfileId(
                    assignmentId,
                    problemId,
                    studentProfileId
            );
        }
        if (submissions.isEmpty()) {
            return List.of();
        }

        List<Long> submissionIds = submissions.stream()
                .map(SubmissionHistoryProjection::getId)
                .toList();

        Map<Long, SubmissionAnalysis> analysisBySubmissionId = new HashMap<>();
        submissionAnalysisRepository.findBySubmissionIdIn(submissionIds)
                .forEach(analysis -> analysisBySubmissionId.put(analysis.getSubmissionId(), analysis));

        Map<Long, StudentAiFeedback> feedbackBySubmissionId = new HashMap<>();
        studentAiFeedbackRepository.findBySubmissionIdIn(submissionIds)
                .forEach(feedback -> feedbackBySubmissionId.put(feedback.getSubmissionId(), feedback));

        Map<Long, SubmissionCaseResultStatsProjection> caseResultStatsBySubmissionId = submissionCaseResultRepository
                .summarizeBySubmissionIdIn(submissionIds)
                .stream()
                .collect(Collectors.toMap(SubmissionCaseResultStatsProjection::getSubmissionId, stats -> stats));
        Map<Long, SubmissionGrowthSummaryResponse> growthBySubmissionId = growthSummaryService == null
                ? Map.of()
                : growthSummaryService.summarizeByIds(submissionIds);

        return submissions.stream()
                .map(submission -> toHistorySummary(
                        submission,
                        problem,
                        analysisBySubmissionId.get(submission.getId()),
                        caseResultStatsBySubmissionId.get(submission.getId()),
                        feedbackBySubmissionId.get(submission.getId()),
                        growthBySubmissionId.get(submission.getId())
                ))
                .toList();
    }

    public SubmissionAnalysisLookupResponse getSubmissionAnalysisLookup(Long submissionId) {
        log.info("Loading submission analysis lookup. submissionId={}", submissionId);
        submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));

        SubmissionAnalysisResponse analysis = findExistingAnalysis(submissionId);
        return SubmissionAnalysisLookupResponse.builder()
                .status(analysis == null ? "PENDING" : "READY")
                .analysis(analysis)
                .build();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SubmissionAnalysisResponse generateAndStoreAnalysisForSubmission(Long submissionId) {
        log.info("Generating submission analysis from stored submission. submissionId={}", submissionId);
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));

        SubmissionAnalysisResponse existing = findExistingAnalysis(submissionId);
        if (existing != null) {
            log.info("Reusing existing submission analysis. submissionId={}, sourceType={}",
                    submissionId,
                    existing.getSourceType());
            submissionAnalysisRepository.findBySubmissionId(submissionId)
                    .ifPresent(analysis -> {
                        projectDiagnosisFactsSafely(analysis, existing);
                        backfillSubmissionAnalysisSafely(submission, analysis);
                    });
            return existing;
        }

        Problem problem = problemRepository.findById(submission.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + submission.getProblemId()));

        List<SubmissionCaseResult> caseResults = submissionCaseResultRepository
                .findBySubmissionIdOrderByTestCaseNumberAsc(submissionId);
        return generateAndStoreAnalysis(problem, submission, caseResults);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SubmissionAnalysisResponse generateAndStoreAnalysis(Problem problem, Submission submission, List<SubmissionCaseResult> caseResults) {
        log.info("Building submission analysis payload. submissionId={}, problemId={}, verdict={}, caseCount={}",
                submission.getId(),
                problem.getId(),
                submission.getVerdict(),
                caseResults == null ? 0 : caseResults.size());
        Assignment.HintPolicy hintPolicy = resolveHintPolicy(submission);
        SubmissionAnalysisResponse analysis = buildAnalysis(problem, submission, caseResults);
        DiagnosisEvidencePackage.HistoryEvidence historyEvidence = buildHistoryEvidence(submission);
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot learningMemory = buildLearningMemorySnapshot(submission);
        DiagnosticAgentService.AgentResult agentResult = diagnosticAgentService.diagnose(
                problem,
                submission,
                caseResults,
                analysis,
                hintPolicy,
                historyEvidence,
                learningMemory
        );
        analysis = agentResult.analysis();

        SubmissionAnalysis entity = submissionAnalysisRepository.findBySubmissionId(submission.getId())
                .orElse(SubmissionAnalysis.builder()
                        .submissionId(submission.getId())
                        .build());

        entity.setAnalysisSource(analysis.getSourceType());
        entity.setScenario(analysis.getScenario());
        entity.setHeadline(analysis.getHeadline());
        entity.setSummary(analysis.getSummary());
        entity.setReportMarkdown(analysis.getReportMarkdown());
        entity.setReportJson(serializeAnalysis(analysis));
        entity.setEvidenceJson(diagnosisEvidencePackageReader.serialize(agentResult.evidencePackage()));

        SubmissionAnalysis savedAnalysis = submissionAnalysisRepository.save(entity);
        log.info("Persisted submission analysis. submissionId={}, sourceType={}, generatedAt={}",
                submission.getId(),
                analysis.getSourceType(),
                savedAnalysis.getGeneratedAt());
        runPostDiagnosisProjections(submission, savedAnalysis, analysis);
        analysis.setGeneratedAt(savedAnalysis.getGeneratedAt());
        return analysis;
    }

    private void runPostDiagnosisProjections(Submission submission,
                                             SubmissionAnalysis savedAnalysis,
                                             SubmissionAnalysisResponse response) {
        Long runId = AiDiagnosisWorkflowContext.currentRunId().orElse(null);
        if (diagnosisWorkflowService == null || runId == null) {
            projectDiagnosisFactsSafely(savedAnalysis, response);
            backfillSubmissionAnalysisSafely(submission, savedAnalysis);
            return;
        }
        CompletableFuture<AiDiagnosisProjectionResult> facts = projectionFuture(
                runId,
                "FACT_LIFECYCLE_PROJECTION",
                () -> {
                    SubmissionDiagnosisFactProjector.ProjectionResult result =
                            submissionDiagnosisFactProjector.project(savedAnalysis, response);
                    return AiDiagnosisProjectionResult.completed(
                            "FACT_LIFECYCLE_PROJECTION",
                            result.inserted() + result.skipped()
                    );
                }
        );
        CompletableFuture<AiDiagnosisProjectionResult> recommendation = facts.thenCompose(ignored -> projectionFuture(
                runId,
                "RUNTIME_QUALITY_PROJECTION",
                () -> {
                    recommendationEventService.backfillSubmissionAnalysis(submission, savedAnalysis);
                    return AiDiagnosisProjectionResult.completed("RUNTIME_QUALITY_PROJECTION", 1);
                }
        ));
        CompletableFuture<AiDiagnosisProjectionResult> teacher = facts.thenCompose(ignored -> projectionFuture(
                runId,
                "TEACHER_ANALYTICS_PROJECTION",
                () -> AiDiagnosisProjectionResult.completed(
                        "TEACHER_ANALYTICS_PROJECTION",
                        submissionDiagnosisFactProjector == null ? 0 : 1)
        ));
        try {
            CompletableFuture.allOf(facts, recommendation, teacher).join();
        } catch (CompletionException failure) {
            log.warn("One or more post-diagnosis projections failed but the formal analysis is kept. submissionId={}, reason={}",
                    submission == null ? null : submission.getId(),
                    failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage());
        }
    }

    private CompletableFuture<AiDiagnosisProjectionResult> projectionFuture(
            Long runId,
            String stageKey,
            AiDiagnosisWorkflowService.StageCallable<AiDiagnosisProjectionResult> callable) {
        return CompletableFuture.supplyAsync(() -> {
            try (AiDiagnosisWorkflowContext.Scope ignored = AiDiagnosisWorkflowContext.activate(runId)) {
                return diagnosisWorkflowService.executeStage(
                        runId,
                        stageKey,
                        stageKey,
                        null,
                        Map.of("runId", runId, "stage", stageKey),
                        AiDiagnosisProjectionResult.class,
                        "INTERNAL",
                        "projection",
                        "projection-v1",
                        callable
                );
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, diagnosisBranchExecutor);
    }

    private void backfillSubmissionAnalysisSafely(Submission submission, SubmissionAnalysis analysis) {
        try {
            recommendationEventService.backfillSubmissionAnalysis(submission, analysis);
        } catch (Exception exception) {
            log.warn("Recommendation event backfill failed but analysis is kept. submissionId={}, reason={}",
                    submission == null ? null : submission.getId(),
                    exception.getMessage());
        }
    }

    private void projectDiagnosisFactsSafely(SubmissionAnalysis analysis, SubmissionAnalysisResponse response) {
        try {
            if (submissionDiagnosisFactProjector != null) {
                submissionDiagnosisFactProjector.project(analysis, response);
            }
        } catch (Exception exception) {
            log.warn("Diagnosis fact projection failed and will be retried on reuse or backfill. submissionId={}, analysisId={}, reason={}",
                    analysis == null ? null : analysis.getSubmissionId(),
                    analysis == null ? null : analysis.getId(),
                    exception.getMessage());
        }
    }

    public SubmissionResponse buildSubmissionResponse(Submission submission,
                                                      Problem problem,
                                                      List<SubmissionCaseResult> caseResults,
                                                      SubmissionAnalysisResponse analysis) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .problemId(submission.getProblemId())
                .assignmentId(submission.getAssignmentId())
                .studentProfileId(submission.getStudentProfileId())
                .problemTitle(problem != null ? problem.getTitle() : "未知题目")
                .languageId(submission.getLanguageId())
                .languageName(submission.getLanguageName())
                .sourceCode(submission.getSourceCode())
                .verdict(submission.getVerdict())
                .executionTime(submission.getExecutionTime())
                .memoryUsed(submission.getMemoryUsed())
                .output(submission.getOutput())
                .compileOutput(submission.getCompileOutput())
                .errorMessage(submission.getErrorMessage())
                .submittedAt(submission.getSubmittedAt())
                .analysisStatus(resolveAnalysisStatus(analysis))
                .analysis(analysis)
                .growthSummary(growthSummaryService == null ? null : growthSummaryService.summarizeSubmission(submission))
                .testCaseResults(toPublicResults(caseResults))
                .build();
    }

    private SubmissionAnalysisResponse findExistingAnalysis(Long submissionId) {
        return submissionAnalysisRepository.findBySubmissionId(submissionId)
                .map(analysis -> {
                    log.info("Found persisted submission analysis. submissionId={}, sourceType={}",
                            submissionId,
                            analysis.getAnalysisSource());
                    return deserializeAnalysis(analysis);
                })
                .orElse(null);
    }

    public String formatVerdict(Submission.Verdict verdict) {
        if (verdict == null) {
            return "未知";
        }

        return switch (verdict) {
            case ACCEPTED -> "通过";
            case WRONG_ANSWER -> "答案错误";
            case TIME_LIMIT_EXCEEDED -> "超时";
            case MEMORY_LIMIT_EXCEEDED -> "超内存";
            case RUNTIME_ERROR -> "运行错误";
            case COMPILATION_ERROR -> "编译错误";
            case PENDING -> "评测中";
            case INTERNAL_ERROR -> "系统错误";
        };
    }

    private List<SubmissionResponse.TestCaseResult> toPublicResults(List<SubmissionCaseResult> caseResults) {
        return caseResults.stream()
                .map(result -> SubmissionResponse.TestCaseResult.builder()
                        .testCaseNumber(result.getTestCaseNumber())
                        .passed(Boolean.TRUE.equals(result.getPassed()))
                        .actualOutput(maskHiddenValue(result.getActualOutput(), result.getHidden()))
                        .expectedOutput(maskHiddenValue(result.getExpectedOutput(), result.getHidden()))
                        .executionTime(result.getExecutionTime())
                        .memoryUsed(result.getMemoryUsed())
                        .hidden(Boolean.TRUE.equals(result.getHidden()))
                        .build())
                .toList();
    }

    private SubmissionAnalysisResponse buildAnalysis(Problem problem, Submission submission, List<SubmissionCaseResult> caseResults) {
        String scenario = mapScenario(submission.getVerdict());
        SubmissionAnalysisResponse.FailedCaseSnapshot failedCase = buildFailedCase(caseResults);
        List<SubmissionAnalysisResponse.LineIssue> initialLineIssues = buildInitialLineIssues(submission);
        List<String> focusPoints = new ArrayList<>();
        List<String> fixDirections = new ArrayList<>();
        String wrongSolution = null;
        String correctSolution = null;
        String headline;
        String summary;

        switch (scenario) {
            case "AC" -> {
                headline = "代码已通过全部测试点，可以继续做质量优化";
                summary = "当前提交已经 AC。下一步更适合从复杂度、可读性、命名规范和边界样例补强几方面继续提升。";
                focusPoints.add("复杂度观察：" + inferComplexity(submission.getSourceCode()));
                focusPoints.add("检查变量命名、分支组织和函数拆分是否足够清晰。");
                focusPoints.add("确认是否还存在可以压缩常数开销或减少重复逻辑的空间。");
                fixDirections.add("把重复逻辑提取成函数，降低主流程的阅读成本。");
                fixDirections.add("补充极值、空输入、重复元素等边界自测。");
                fixDirections.add("统一命名、缩进和注释风格，方便后续复盘。");
            }
            case "WA" -> {
                headline = "已定位到首个失败测试点，建议先修正核心逻辑";
                summary = failedCase != null && !failedCase.isHidden()
                        ? String.format("程序在测试点 #%d 首次出现输出不一致，说明当前解法在该场景下存在逻辑偏差。", failedCase.getTestCaseNumber())
                        : "程序首次失败出现在隐藏测试点，说明当前逻辑还没有覆盖完整的边界场景。";
                focusPoints.add("优先核对输入边界、条件分支和输出格式。");
                focusPoints.add(failedCase != null && !failedCase.isHidden()
                        ? "对照首个错误测试点，逐步推演变量变化，定位偏差产生的位置。"
                        : "重点回看极值、空值、重复元素、顺序变化等隐藏场景。");
                focusPoints.add("检查是否遗漏了题面中的特殊约束或状态重置。");
                wrongSolution = failedCase != null && !failedCase.isHidden()
                        ? String.format("当前代码在测试点 #%d 上输出 `%s`，而标准答案是 `%s`，说明这类输入的处理逻辑仍然有偏差。",
                        failedCase.getTestCaseNumber(),
                        truncateInline(failedCase.getActualOutput()),
                        truncateInline(failedCase.getExpectedOutput()))
                        : "当前代码未通过隐藏测试点，说明某类边界情况仍未被正确处理。";
                correctSolution = "建议严格按题意重新推导状态转移或判断条件，并用首个失败样例做手动验算。";
                fixDirections.add("先拿首个失败样例手推一遍当前代码。");
                fixDirections.add("把可疑判断拆出来单独验证，避免在大段逻辑里盲改。");
                fixDirections.add("补 2 到 3 个覆盖边界的自测样例后再重新提交。");
            }
            case "TLE" -> {
                headline = "程序超时，优先处理复杂度瓶颈";
                summary = "当前提交触发时间限制，通常意味着存在重复扫描、深层嵌套循环或高开销操作。";
                focusPoints.add("复杂度观察：" + inferComplexity(submission.getSourceCode()));
                focusPoints.add("检查是否对同一批数据做了重复遍历、重复排序或频繁字符串拼接。");
                focusPoints.add("如果当前是暴力做法，优先考虑哈希、双指针、前缀和、剪枝等更合适的思路。");
                wrongSolution = "当前实现无法在时限内完成，说明主流程仍然偏暴力或存在可避免的重复计算。";
                correctSolution = "应优先降低时间复杂度，并尽量把重复计算前置为预处理或缓存。";
                fixDirections.add("先找出最重的循环层级，确认是否能减少状态数。");
                fixDirections.add("避免在循环内部做全量排序、对象重建和大规模字符串拼接。");
                fixDirections.add("用最大输入规模做一次本地压测，确认优化是否真的生效。");
            }
            case "MLE" -> {
                headline = "程序超出内存限制，优先压缩数据结构";
                summary = "当前提交触发内存限制，通常和大数组、冗余缓存、对象复制或递归栈过深有关。";
                focusPoints.add("内存观察：" + inferMemoryPressure(submission.getSourceCode()));
                focusPoints.add("检查是否保存了整份输入、副本数组或不必要的中间状态。");
                focusPoints.add("如果使用递归，也要评估递归深度带来的额外栈空间。");
                wrongSolution = "当前实现占用了超出限制的内存，说明数据结构选择或缓存策略过重。";
                correctSolution = "建议只保留必要状态，并尽量使用原地处理、滚动数组或更轻量的容器。";
                fixDirections.add("把全量缓存改成按需处理或滚动更新。");
                fixDirections.add("排查大对象、字符串和集合是否发生了重复拷贝。");
                fixDirections.add("评估是否能改用更紧凑的数组或位运算表示状态。");
            }
            case "RE" -> {
                headline = "程序运行时发生异常，先修复稳定性问题";
                summary = submission.getErrorMessage() != null && !submission.getErrorMessage().isBlank()
                        ? "本次提交在运行中抛出了异常或执行错误，请先修复稳定性问题。"
                        : "本次提交出现运行错误，请优先排查越界、空指针、除零和非法输入处理。";
                focusPoints.add("检查数组、字符串和集合访问是否存在越界。");
                focusPoints.add("检查是否存在空值访问、除零、类型转换错误或递归过深。");
                if (submission.getErrorMessage() != null && !submission.getErrorMessage().isBlank()) {
                    focusPoints.add("运行日志提示：" + truncateInline(submission.getErrorMessage()));
                }
                wrongSolution = "当前代码在某条执行路径上触发了运行时异常，说明稳定性保护还不完整。";
                correctSolution = "建议先保证所有输入场景都能稳定运行，再继续追求正确性和性能。";
                fixDirections.add("为关键索引、除法和对象访问增加边界保护。");
                fixDirections.add("针对空输入、单元素、最大值、最小值等边界重新自测。");
                fixDirections.add("如果是递归实现，检查递归出口和深度是否可控。");
            }
            case "CE" -> {
                headline = "代码未通过编译，先修复语法或环境问题";
                summary = "当前提交在编译阶段失败，需要先解决语法、导包、类名或语言特性使用不当的问题。";
                focusPoints.add("优先查看编译日志中的第一条报错。");
                focusPoints.add("检查类名、函数签名、分号、括号和导包是否正确。");
                focusPoints.add("确认使用的语法特性和当前评测环境兼容。");
                wrongSolution = "当前代码还无法进入运行阶段，因此需要先消除编译错误。";
                correctSolution = "先修正第一条编译错误，再重新编译；后续连带错误通常会一起消失。";
                fixDirections.add("从第一条报错开始逐个修复，不要同时大范围改动。");
                fixDirections.add("确认入口函数、类名和模板结构符合评测环境要求。");
            }
            default -> {
                headline = "当前结果需要补充更多诊断信息";
                summary = "暂时无法完整归类这次提交，建议结合日志和测试点结果继续排查。";
                focusPoints.add("确认评测结果和测试点回传信息是否完整。");
                fixDirections.add("先补齐日志信息，再继续定位问题。");
            }
        }

        focusPoints = deduplicate(focusPoints);
        fixDirections = deduplicate(fixDirections);
        List<String> issueTags = inferIssueTags(scenario, submission);
        List<String> abilityPoints = inferAbilityPoints(issueTags, scenario);
        String studentHint = buildStudentHint(scenario, fixDirections);
        String teacherNote = buildTeacherNote(scenario, issueTags);
        String progressSignal = buildProgressSignal(scenario);
        double confidence = inferConfidence(scenario, failedCase, initialLineIssues);
        String answerLeakRisk = assessAnswerLeakRisk(studentHint, correctSolution);
        SubmissionAnalysisResponse.StudentHintPlan studentHintPlan = buildStudentHintPlan(
                scenario,
                issueTags,
                List.of(),
                List.of("verdict:" + Optional.ofNullable(submission.getVerdict()).map(Enum::name).orElse("UNKNOWN").toLowerCase()),
                studentHint,
                answerLeakRisk
        );
        String reportMarkdown = buildMarkdown(problem, submission, headline, summary, focusPoints, fixDirections,
                wrongSolution, correctSolution, failedCase);

        SubmissionAnalysisResponse response = SubmissionAnalysisResponse.builder()
                .analysisSchemaVersion("diagnosis-v1")
                .evidenceSchemaVersion(DiagnosisEvidencePackage.SCHEMA_VERSION)
                .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                .submissionId(submission.getId())
                .sourceType(ANALYSIS_SOURCE)
                .scenario(scenario)
                .headline(headline)
                .summary(summary)
                .issueTags(issueTags)
                .fineGrainedTags(List.of())
                .abilityPoints(abilityPoints)
                .focusPoints(focusPoints)
                .fixDirections(fixDirections)
                .evidenceRefs(List.of("verdict:" + Optional.ofNullable(submission.getVerdict()).map(Enum::name).orElse("UNKNOWN").toLowerCase()))
                .studentHint(studentHint)
                .studentHintPlan(studentHintPlan)
                .learningActionEvidence(null)
                .teacherNote(teacherNote)
                .progressSignal(progressSignal)
                .confidence(confidence)
                .uncertainty(buildUncertainty(scenario, failedCase))
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .provider("LOCAL_BASELINE")
                        .model("local-baseline")
                        .modelVersion("local-baseline-v1")
                        .promptVersion("diagnostic-baseline-v1")
                        .agentVersion("diagnostic-agent-v2")
                        .analysisSchemaVersion("diagnosis-v1")
                        .evidenceSchemaVersion(DiagnosisEvidencePackage.SCHEMA_VERSION)
                        .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                        .status("LOCAL_BASELINE")
                        .fallbackUsed(false)
                        .build())
                .answerLeakRisk(answerLeakRisk)
                .wrongSolution(wrongSolution)
                .correctSolution(correctSolution)
                .lineIssues(initialLineIssues)
                .firstFailedCase(failedCase)
                .reportMarkdown(reportMarkdown)
                .build();
        response.setStudentFeedback(new StudentFeedbackAssembler(diagnosisTaxonomy)
                .assemble(response, null, false));
        return response;
    }

    private DiagnosisEvidencePackage.HistoryEvidence buildHistoryEvidence(Submission submission) {
        if (submission == null || submission.getAssignmentId() == null || submission.getStudentProfileId() == null) {
            return DiagnosisEvidencePackage.HistoryEvidence.builder()
                    .previousIssueTags(List.of())
                    .previousFineGrainedTags(List.of())
                    .recentIssueTags(List.of())
                    .recentFineGrainedTags(List.of())
                    .transitionSignal("无课堂历史提交证据")
                    .build();
        }
        List<Submission> recentSubmissions = submissionRepository
                .findByAssignmentIdAndProblemIdAndStudentProfileIdOrderBySubmittedAtDesc(
                        submission.getAssignmentId(),
                        submission.getProblemId(),
                        submission.getStudentProfileId()
                )
                .stream()
                .filter(item -> !Objects.equals(item.getId(), submission.getId()))
                .limit(5)
                .toList();
        if (recentSubmissions.isEmpty()) {
            return DiagnosisEvidencePackage.HistoryEvidence.builder()
                    .previousIssueTags(List.of())
                    .previousFineGrainedTags(List.of())
                    .recentIssueTags(List.of())
                    .recentFineGrainedTags(List.of())
                    .transitionSignal("本题暂无历史提交")
                    .build();
        }

        List<Long> recentIds = recentSubmissions.stream().map(Submission::getId).toList();
        Map<Long, SubmissionAnalysis> analyses = submissionAnalysisRepository.findBySubmissionIdIn(recentIds)
                .stream()
                .collect(Collectors.toMap(SubmissionAnalysis::getSubmissionId, analysis -> analysis));
        Submission previous = recentSubmissions.get(0);
        SubmissionAnalysis previousAnalysis = analyses.get(previous.getId());
        DiagnosisReportReader.LearningInterventionPlanSnapshot previousPlan =
                diagnosisReportReader.learningInterventionPlan(previousAnalysis);
        DiagnosisReportReader.LearningActionEvidenceSnapshot previousActionEvidence =
                resolvePreviousLearningActionEvidence(previous, previousAnalysis, submission);
        List<String> recentIssueTags = recentSubmissions.stream()
                .map(item -> analyses.get(item.getId()))
                .filter(Objects::nonNull)
                .flatMap(analysis -> diagnosisReportReader.issueTags(analysis).stream())
                .toList();
        List<String> recentFineTags = recentSubmissions.stream()
                .map(item -> analyses.get(item.getId()))
                .filter(Objects::nonNull)
                .flatMap(analysis -> diagnosisReportReader.fineGrainedTags(analysis).stream())
                .toList();
        Map.Entry<String, Long> repeatedIssue = mostCommon(recentIssueTags);
        Map.Entry<String, Long> repeatedFine = mostCommon(recentFineTags);

        return DiagnosisEvidencePackage.HistoryEvidence.builder()
                .previousVerdict(previous.getVerdict() == null ? "UNKNOWN" : previous.getVerdict().name())
                .previousIssueTags(diagnosisReportReader.issueTags(previousAnalysis))
                .previousFineGrainedTags(diagnosisReportReader.fineGrainedTags(previousAnalysis))
                .recentIssueTags(recentIssueTags)
                .recentFineGrainedTags(recentFineTags)
                .repeatedIssueTag(repeatedIssue == null ? null : repeatedIssue.getKey())
                .repeatedFineGrainedTag(repeatedFine == null ? null : repeatedFine.getKey())
                .repeatedIssueCount(repeatedIssue == null ? 0 : repeatedIssue.getValue())
                .repeatedFineGrainedIssueCount(repeatedFine == null ? 0 : repeatedFine.getValue())
                .transitionSignal(buildTransitionSignal(previous, submission, repeatedIssue, repeatedFine))
                .previousInterventionType(previousPlan == null ? null : previousPlan.interventionType())
                .previousInterventionTask(previousPlan == null ? null : previousPlan.studentTask())
                .previousInterventionCompletionSignal(previousPlan == null ? null : previousPlan.completionSignal())
                .previousLearningActionStatus(previousActionEvidence == null ? null : previousActionEvidence.executionStatus())
                .previousLearningActionConfidence(previousActionEvidence == null ? null : previousActionEvidence.confidence())
                .previousLearningActionEvidenceRefs(previousActionEvidence == null
                        ? List.of()
                        : previousActionEvidence.evidenceRefs())
                .previousLearningActionSummary(previousActionEvidence == null ? null : previousActionEvidence.observedEvidence())
                .previousLearningActionNextAdjustment(previousActionEvidence == null ? null : previousActionEvidence.nextAdjustment())
                .build();
    }

    DiagnosisEvidencePackage.HistoryEvidence buildHistoryEvidenceForTest(Submission submission) {
        return buildHistoryEvidence(submission);
    }

    private DiagnosisReportReader.LearningActionEvidenceSnapshot resolvePreviousLearningActionEvidence(
            Submission previous,
            SubmissionAnalysis previousAnalysis,
            Submission current) {
        if (previousAnalysis == null) {
            return null;
        }
        DiagnosisReportReader.LearningInterventionPlanSnapshot previousPlan =
                diagnosisReportReader.learningInterventionPlan(previousAnalysis);
        if (previousPlan == null || previousPlan.interventionType() == null || previousPlan.interventionType().isBlank()) {
            return null;
        }
        DiagnosisReportReader.LearningActionEvidenceSnapshot persisted =
                diagnosisReportReader.learningActionEvidence(previousAnalysis);
        String status = inferLearningActionStatus(previous, previousAnalysis, current);
        List<String> evidenceRefs = mergeLearningActionEvidenceRefs(
                previousPlan.evidenceRefs(),
                persisted == null ? List.of() : persisted.evidenceRefs(),
                current == null || current.getId() == null ? null : "followup:submission:" + current.getId(),
                status == null || status.isBlank() ? null : "action:" + status
        );
        return new DiagnosisReportReader.LearningActionEvidenceSnapshot(
                previousPlan.interventionType(),
                status,
                buildLearningActionObservedEvidence(status, previousPlan, previous, current),
                learningActionConfidence(status, persisted),
                evidenceRefs,
                buildLearningActionNextAdjustment(status, persisted)
        );
    }

    private String inferLearningActionStatus(Submission previous,
                                             SubmissionAnalysis previousAnalysis,
                                             Submission current) {
        if (current == null) {
            return "NOT_OBSERVED";
        }
        if (current.getVerdict() == Submission.Verdict.ACCEPTED) {
            return "OBSERVED";
        }
        if (previous == null) {
            return "NOT_OBSERVED";
        }
        if (!Objects.equals(previous.getVerdict(), current.getVerdict())) {
            return "PARTIALLY_OBSERVED";
        }
        List<String> previousFineTags = diagnosisReportReader.fineGrainedTags(previousAnalysis);
        List<String> previousIssueTags = diagnosisReportReader.issueTags(previousAnalysis);
        if (!previousFineTags.isEmpty() || !previousIssueTags.isEmpty()) {
            return "CONTRADICTED";
        }
        return "PARTIALLY_OBSERVED";
    }

    private List<String> mergeLearningActionEvidenceRefs(List<String> planRefs,
                                                         List<String> actionRefs,
                                                         String followupRef,
                                                         String statusRef) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        if (planRefs != null) {
            planRefs.stream().filter(this::hasText).forEach(refs::add);
        }
        if (actionRefs != null) {
            actionRefs.stream().filter(this::hasText).forEach(refs::add);
        }
        if (hasText(followupRef)) {
            refs.add(followupRef);
        }
        if (hasText(statusRef)) {
            refs.add(statusRef);
        }
        return List.copyOf(refs);
    }

    private String buildLearningActionObservedEvidence(String status,
                                                       DiagnosisReportReader.LearningInterventionPlanSnapshot previousPlan,
                                                       Submission previous,
                                                       Submission current) {
        String action = previousPlan == null ? "学习动作" : firstNonBlank(previousPlan.interventionType(), "学习动作");
        String previousVerdict = previous == null || previous.getVerdict() == null ? "UNKNOWN" : previous.getVerdict().name();
        String currentVerdict = current == null || current.getVerdict() == null ? "UNKNOWN" : current.getVerdict().name();
        return switch (status == null ? "" : status) {
            case "OBSERVED" -> "上一轮要求执行 " + action + " 后，当前同题提交已经通过，可转入复盘或迁移。";
            case "PARTIALLY_OBSERVED" -> "上一轮要求执行 " + action + " 后，评测阶段从 "
                    + previousVerdict + " 变化为 " + currentVerdict + "，说明可能有行动痕迹但仍需验证。";
            case "CONTRADICTED" -> "上一轮要求执行 " + action + " 后，当前同题提交仍停留在 "
                    + currentVerdict + "，需要把动作缩小成更可观察的产出。";
            default -> "上一轮要求执行 " + action + "，但当前还没有足够证据证明学生已经完成该动作。";
        };
    }

    private Double learningActionConfidence(String status,
                                            DiagnosisReportReader.LearningActionEvidenceSnapshot persisted) {
        if (persisted != null
                && Objects.equals(status, persisted.executionStatus())
                && persisted.confidence() != null
                && persisted.confidence() > 0) {
            return persisted.confidence();
        }
        return switch (status == null ? "" : status) {
            case "OBSERVED" -> 0.82;
            case "PARTIALLY_OBSERVED" -> 0.66;
            case "CONTRADICTED" -> 0.74;
            default -> 0.5;
        };
    }

    private String buildLearningActionNextAdjustment(String status,
                                                     DiagnosisReportReader.LearningActionEvidenceSnapshot persisted) {
        if (persisted != null
                && Objects.equals(status, persisted.executionStatus())
                && hasText(persisted.nextAdjustment())) {
            return persisted.nextAdjustment();
        }
        return switch (status == null ? "" : status) {
            case "OBSERVED" -> "保持当前提示粒度，转向复盘、迁移或更高层解释。";
            case "PARTIALLY_OBSERVED" -> "保持方向，但把下一步动作缩小成一个更明确的可检查产出。";
            case "CONTRADICTED" -> "降低提示粒度，要求最小样例、变量跟踪，必要时建议教师介入。";
            default -> "不要假设学生已执行动作，等待同题后续提交或要求可观察产出。";
        };
    }

    private Map.Entry<String, Long> mostCommon(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        tags.stream()
                .filter(Objects::nonNull)
                .filter(tag -> !tag.isBlank())
                .forEach(tag -> counts.put(tag, counts.getOrDefault(tag, 0L) + 1));
        return counts.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
    }

    private String buildTransitionSignal(Submission previous,
                                         Submission current,
                                         Map.Entry<String, Long> repeatedIssue,
                                         Map.Entry<String, Long> repeatedFine) {
        String previousVerdict = previous == null || previous.getVerdict() == null ? "UNKNOWN" : previous.getVerdict().name();
        String currentVerdict = current == null || current.getVerdict() == null ? "UNKNOWN" : current.getVerdict().name();
        if ("ACCEPTED".equals(currentVerdict) && !"ACCEPTED".equals(previousVerdict)) {
            return "本次相对上次已推进到通过";
        }
        if (!Objects.equals(previousVerdict, currentVerdict)) {
            return "评测阶段从 " + previousVerdict + " 变化为 " + currentVerdict;
        }
        if (repeatedFine != null && repeatedFine.getValue() >= 2) {
            return "最近仍反复出现细分卡点：" + diagnosisTaxonomy.label(repeatedFine.getKey());
        }
        if (repeatedIssue != null && repeatedIssue.getValue() >= 2) {
            return "最近仍反复出现同类问题：" + diagnosisTaxonomy.label(repeatedIssue.getKey());
        }
        return "评测阶段暂未变化，需要继续观察修改效果";
    }

    private DiagnosisEvidencePackage.StudentLearningMemorySnapshot buildLearningMemorySnapshot(Submission submission) {
        if (submission == null || submission.getStudentProfileId() == null) {
            return null;
        }
        List<Submission> history = submissionRepository
                .findByStudentProfileIdInOrderBySubmittedAtDesc(List.of(submission.getStudentProfileId()))
                .stream()
                .filter(item -> item != null && !Objects.equals(item.getId(), submission.getId()))
                .limit(LEARNING_MEMORY_WINDOW)
                .toList();
        if (history.isEmpty()) {
            return null;
        }
        List<Long> historyIds = history.stream()
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SubmissionAnalysis> analyses = historyIds.isEmpty()
                ? Map.of()
                : submissionAnalysisRepository.findBySubmissionIdIn(historyIds)
                .stream()
                .collect(Collectors.toMap(SubmissionAnalysis::getSubmissionId, analysis -> analysis, (left, right) -> left));
        if (analyses.isEmpty()) {
            return null;
        }

        List<String> issueTags = history.stream()
                .map(item -> analyses.get(item.getId()))
                .filter(Objects::nonNull)
                .flatMap(analysis -> diagnosisReportReader.issueTags(analysis).stream())
                .toList();
        List<String> fineTags = history.stream()
                .map(item -> analyses.get(item.getId()))
                .filter(Objects::nonNull)
                .flatMap(analysis -> diagnosisReportReader.fineGrainedTags(analysis).stream())
                .toList();
        List<AbilitySignalAnalyzer.AbilitySignal> abilitySignals =
                abilitySignalAnalyzer.summarize(history, analyses);
        List<TeacherDiagnosisCorrection> corrections = teacherCorrections(historyIds);
        List<DiagnosisEvidencePackage.TeacherCalibrationPattern> calibrationPatterns =
                teacherCalibrationPatterns(corrections);
        List<String> evidenceRefs = buildLearningMemoryEvidenceRefs(
                submission,
                history,
                issueTags,
                fineTags,
                abilitySignals,
                corrections,
                calibrationPatterns
        );

        return DiagnosisEvidencePackage.StudentLearningMemorySnapshot.builder()
                .studentProfileId(submission.getStudentProfileId())
                .assignmentId(submission.getAssignmentId())
                .currentProblemId(submission.getProblemId())
                .observedSubmissionCount(history.size())
                .observedProblemCount((int) history.stream().map(Submission::getProblemId).filter(Objects::nonNull).distinct().count())
                .recurringIssueTags(toMemoryTagStats(issueTags, history, analyses, false))
                .recurringFineGrainedTags(toMemoryTagStats(fineTags, history, analyses, true))
                .abilityFocus(toAbilityFocus(abilitySignals))
                .recentTrend(buildLearningMemoryTrend(history, analyses))
                .interventionEffect(latestInterventionEffect(history, analyses))
                .teacherCorrectionSummary(teacherCorrectionSummary(corrections))
                .teacherCalibrationSignal(primaryTeacherCalibrationPattern(calibrationPatterns))
                .teacherCalibrationPatterns(calibrationPatterns)
                .evidenceRefs(evidenceRefs)
                .build();
    }

    private List<TeacherDiagnosisCorrection> teacherCorrections(List<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }
        return teacherDiagnosisCorrectionRepository.findBySubmissionIdIn(submissionIds)
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> buildLearningMemoryEvidenceRefs(Submission current,
                                                         List<Submission> history,
                                                         List<String> issueTags,
                                                         List<String> fineTags,
                                                         List<AbilitySignalAnalyzer.AbilitySignal> abilitySignals,
                                                         List<TeacherDiagnosisCorrection> corrections,
                                                         List<DiagnosisEvidencePackage.TeacherCalibrationPattern> calibrationPatterns) {
        List<String> refs = new ArrayList<>();
        if (current != null && current.getStudentProfileId() != null) {
            refs.add("memory:student:" + current.getStudentProfileId());
        }
        if (history != null && !history.isEmpty()) {
            refs.add("memory:recent_submissions:" + history.size());
        }
        Map.Entry<String, Long> repeatedIssue = mostCommon(issueTags);
        if (repeatedIssue != null && repeatedIssue.getValue() >= 2) {
            refs.add("memory:recurring_issue:" + repeatedIssue.getKey());
        }
        Map.Entry<String, Long> repeatedFine = mostCommon(fineTags);
        if (repeatedFine != null && repeatedFine.getValue() >= 2) {
            refs.add("memory:recurring_fine:" + repeatedFine.getKey());
        }
        if (abilitySignals != null && !abilitySignals.isEmpty()) {
            refs.add("memory:ability_focus:" + normalizeMemoryRef(abilitySignals.get(0).getAbilityPoint()));
        }
        if (corrections != null && !corrections.isEmpty()) {
            refs.add("memory:teacher_corrections:" + corrections.size());
        }
        if (calibrationPatterns != null && !calibrationPatterns.isEmpty()) {
            calibrationPatterns.stream()
                    .limit(3)
                    .flatMap(pattern -> pattern.getEvidenceRefs() == null
                            ? List.<String>of().stream()
                            : pattern.getEvidenceRefs().stream())
                    .forEach(refs::add);
        }
        return deduplicate(refs);
    }

    private List<DiagnosisEvidencePackage.TeacherCalibrationPattern> teacherCalibrationPatterns(
            List<TeacherDiagnosisCorrection> corrections) {
        if (corrections == null || corrections.isEmpty()) {
            return List.of();
        }
        Map<String, List<TeacherDiagnosisCorrection>> grouped = new LinkedHashMap<>();
        for (TeacherDiagnosisCorrection correction : corrections) {
            if (correction == null) {
                continue;
            }
            String correctedIssue = normalizeCorrectionTag(correction.getCorrectedIssueTag());
            String correctedFine = normalizeCorrectionTag(correction.getCorrectedFineGrainedTag());
            if (correctedIssue.isBlank() && correctedFine.isBlank()) {
                continue;
            }
            String key = String.join("->",
                    normalizeCorrectionTag(correction.getOriginalIssueTag()),
                    normalizeCorrectionTag(correction.getOriginalFineGrainedTag()),
                    correctedIssue,
                    correctedFine,
                    normalizeCorrectionDetail(correction.getCorrectionType()),
                    normalizeCorrectionDetail(correction.getTargetIssueId()),
                    normalizeCorrectionDetail(correction.getCorrectedKnowledgePath()),
                    normalizeCorrectionDetail(correction.getTargetEvidenceRef()));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(correction);
        }
        return grouped.values()
                .stream()
                .map(this::toTeacherCalibrationPattern)
                .sorted(Comparator
                        .comparingLong((DiagnosisEvidencePackage.TeacherCalibrationPattern pattern) ->
                                pattern.getCorrectionCount() == null ? 0L : pattern.getCorrectionCount())
                        .reversed()
                        .thenComparing(pattern -> pattern.getEvidenceSubmissionIds() == null
                                        || pattern.getEvidenceSubmissionIds().isEmpty()
                                        ? Long.MAX_VALUE
                                        : pattern.getEvidenceSubmissionIds().get(0),
                                Comparator.nullsLast(Long::compareTo)))
                .limit(5)
                .toList();
    }

    private DiagnosisEvidencePackage.TeacherCalibrationPattern toTeacherCalibrationPattern(
            List<TeacherDiagnosisCorrection> corrections) {
        List<TeacherDiagnosisCorrection> safeCorrections = corrections == null ? List.of() : corrections.stream()
                .filter(Objects::nonNull)
                .toList();
        TeacherDiagnosisCorrection latest = safeCorrections.stream()
                .max(Comparator.comparing(
                        TeacherDiagnosisCorrection::getCorrectedAt,
                        Comparator.nullsFirst(LocalDateTime::compareTo)))
                .orElse(null);
        TeacherDiagnosisCorrection sample = latest == null && !safeCorrections.isEmpty()
                ? safeCorrections.get(0)
                : latest;
        List<Long> evidenceSubmissionIds = safeCorrections.stream()
                .map(TeacherDiagnosisCorrection::getSubmissionId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .toList();
        long evalCandidateCount = safeCorrections.stream()
                .filter(TeacherDiagnosisCorrection::isEvalCandidate)
                .count();
        String correctedFine = normalizeCorrectionTag(sample == null ? null : sample.getCorrectedFineGrainedTag());
        String correctedIssue = normalizeCorrectionTag(sample == null ? null : sample.getCorrectedIssueTag());
        List<String> refs = teacherCalibrationRefs(correctedIssue, correctedFine, evidenceSubmissionIds);
        return DiagnosisEvidencePackage.TeacherCalibrationPattern.builder()
                .originalIssueTag(normalizeCorrectionTag(sample == null ? null : sample.getOriginalIssueTag()))
                .originalFineGrainedTag(normalizeCorrectionTag(sample == null ? null : sample.getOriginalFineGrainedTag()))
                .correctedIssueTag(correctedIssue)
                .correctedFineGrainedTag(correctedFine)
                .correctionType(defaultCorrectionType(sample == null ? null : sample.getCorrectionType()))
                .targetIssueId(normalizeCorrectionDetail(sample == null ? null : sample.getTargetIssueId()))
                .correctedKnowledgePath(normalizeCorrectionDetail(
                        sample == null ? null : sample.getCorrectedKnowledgePath()))
                .targetEvidenceRef(normalizeCorrectionDetail(sample == null ? null : sample.getTargetEvidenceRef()))
                .correctionCount((long) safeCorrections.size())
                .evalCandidateCount(evalCandidateCount)
                .latestTeacherNote(truncateInline(sample == null ? null : sample.getTeacherNote()))
                .evidenceSubmissionIds(evidenceSubmissionIds)
                .evidenceRefs(refs)
                .build();
    }

    DiagnosisEvidencePackage.StudentLearningMemorySnapshot buildLearningMemorySnapshotForTest(Submission submission) {
        return buildLearningMemorySnapshot(submission);
    }

    private String defaultCorrectionType(String value) {
        String normalized = normalizeCorrectionDetail(value).toUpperCase();
        return normalized.isBlank() ? "DIAGNOSIS" : normalized;
    }

    private String normalizeCorrectionDetail(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private DiagnosisEvidencePackage.TeacherCalibrationPattern primaryTeacherCalibrationPattern(
            List<DiagnosisEvidencePackage.TeacherCalibrationPattern> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }
        return patterns.get(0);
    }

    private List<String> teacherCalibrationRefs(String correctedIssue,
                                                String correctedFine,
                                                List<Long> evidenceSubmissionIds) {
        List<String> refs = new ArrayList<>();
        String tag = firstNonBlank(correctedFine, correctedIssue);
        if (tag != null && !tag.isBlank()) {
            refs.add("memory:teacher_calibration:" + normalizeMemoryRef(tag));
        }
        if (evidenceSubmissionIds != null) {
            evidenceSubmissionIds.stream()
                    .limit(3)
                    .map(id -> "teacher_correction:submission:" + id)
                    .forEach(refs::add);
        }
        return deduplicate(refs);
    }

    private String normalizeCorrectionTag(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private List<DiagnosisEvidencePackage.MemoryTagStat> toMemoryTagStats(List<String> tags,
                                                                           List<Submission> history,
                                                                           Map<Long, SubmissionAnalysis> analyses,
                                                                           boolean fineGrained) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        tags.stream()
                .filter(Objects::nonNull)
                .filter(tag -> !tag.isBlank())
                .forEach(tag -> counts.put(tag, counts.getOrDefault(tag, 0L) + 1));
        return counts.entrySet()
                .stream()
                .filter(entry -> entry.getValue() >= 2)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> DiagnosisEvidencePackage.MemoryTagStat.builder()
                        .tag(entry.getKey())
                        .count(entry.getValue())
                        .evidenceSubmissionIds(evidenceSubmissionIds(entry.getKey(), history, analyses, fineGrained))
                        .build())
                .toList();
    }

    private List<Long> evidenceSubmissionIds(String tag,
                                             List<Submission> history,
                                             Map<Long, SubmissionAnalysis> analyses,
                                             boolean fineGrained) {
        if (tag == null || history == null || analyses == null) {
            return List.of();
        }
        return history.stream()
                .filter(Objects::nonNull)
                .filter(submission -> {
                    SubmissionAnalysis analysis = analyses.get(submission.getId());
                    List<String> tags = fineGrained
                            ? diagnosisReportReader.fineGrainedTags(analysis)
                            : diagnosisReportReader.issueTags(analysis);
                    return tags.contains(tag);
                })
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .limit(5)
                .toList();
    }

    private List<DiagnosisEvidencePackage.AbilityFocus> toAbilityFocus(List<AbilitySignalAnalyzer.AbilitySignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        return signals.stream()
                .limit(5)
                .map(signal -> DiagnosisEvidencePackage.AbilityFocus.builder()
                        .abilityPoint(signal.getAbilityPoint())
                        .submissionCount(signal.getSubmissionCount())
                        .problemCount(signal.getTaskCount())
                        .evidenceTags(signal.getEvidenceTags())
                        .build())
                .toList();
    }

    private String buildLearningMemoryTrend(List<Submission> history, Map<Long, SubmissionAnalysis> analyses) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        long accepted = history.stream()
                .filter(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)
                .count();
        long failed = history.size() - accepted;
        if (history.size() >= 3 && history.stream().limit(3).noneMatch(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)) {
            return "最近 3 次历史提交仍未通过，诊断应收窄到最小证据，不要重复泛泛提示。";
        }
        if (accepted > 0 && failed > 0) {
            return "历史中既有通过也有失败记录，适合关注是否存在回退或迁移不稳。";
        }
        if (accepted > 0) {
            return "历史提交中已有通过记录，当前失败需要优先看最近改动或题目迁移差异。";
        }
        if (!analyses.isEmpty()) {
            return "历史诊断已形成但尚未观察到通过，下一步应验证重复错因是否仍存在。";
        }
        return "";
    }

    private String latestInterventionEffect(List<Submission> history, Map<Long, SubmissionAnalysis> analyses) {
        if (history == null || analyses == null || history.isEmpty()) {
            return "";
        }
        for (Submission item : history) {
            SubmissionAnalysis analysis = analyses.get(item.getId());
            DiagnosisReportReader.LearningActionEvidenceSnapshot evidence =
                    diagnosisReportReader.learningActionEvidence(analysis);
            if (evidence != null && evidence.executionStatus() != null && !evidence.executionStatus().isBlank()) {
                return "最近学习动作反馈：" + evidence.executionStatus()
                        + suffixIfPresent("，", evidence.observedEvidence());
            }
        }
        return "";
    }

    private String teacherCorrectionSummary(List<TeacherDiagnosisCorrection> corrections) {
        if (corrections == null || corrections.isEmpty()) {
            return "";
        }
        TeacherDiagnosisCorrection latest = corrections.get(0);
        String tag = firstNonBlank(latest.getCorrectedFineGrainedTag(), latest.getCorrectedIssueTag());
        return "教师曾修正 " + corrections.size() + " 次，最近修正标签：" + tag
                + suffixIfPresent("，备注：", truncateInline(latest.getTeacherNote()));
    }

    private String suffixIfPresent(String prefix, String value) {
        return value == null || value.isBlank() ? "" : prefix + value;
    }

    private String normalizeMemoryRef(String value) {
        return value == null ? "unknown" : value.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}_:-]", "_")
                .toLowerCase();
    }

    private List<SubmissionAnalysisResponse.LineIssue> buildInitialLineIssues(Submission submission) {
        if (submission == null) {
            return List.of();
        }

        if (submission.getVerdict() == Submission.Verdict.COMPILATION_ERROR) {
            return aiCodeAssistSupport.extractCompilerLineIssues(
                    submission.getCompileOutput(),
                    submission.getSourceCode()
            );
        }

        if (submission.getVerdict() == Submission.Verdict.RUNTIME_ERROR) {
            return aiCodeAssistSupport.extractRuntimeLineIssues(
                    firstNonBlank(submission.getErrorMessage(), submission.getOutput()),
                    submission.getSourceCode()
            );
        }

        return List.of();
    }

    private SubmissionAnalysisResponse.FailedCaseSnapshot buildFailedCase(List<SubmissionCaseResult> caseResults) {
        return caseResults.stream()
                .filter(result -> !Boolean.TRUE.equals(result.getPassed()))
                .findFirst()
                .map(result -> SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                        .testCaseNumber(result.getTestCaseNumber())
                        .hidden(Boolean.TRUE.equals(result.getHidden()))
                        .input(maskHiddenValue(result.getInputSnapshot(), result.getHidden(), "隐藏测试点，不展示输入内容"))
                        .expectedOutput(maskHiddenValue(result.getExpectedOutput(), result.getHidden()))
                        .actualOutput(maskHiddenValue(result.getActualOutput(), result.getHidden()))
                        .build())
                .orElse(null);
    }

    private String buildMarkdown(Problem problem,
                                 Submission submission,
                                 String headline,
                                 String summary,
                                 List<String> focusPoints,
                                 List<String> fixDirections,
                                 String wrongSolution,
                                 String correctSolution,
                                 SubmissionAnalysisResponse.FailedCaseSnapshot failedCase) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## ").append(headline).append("\n\n");
        markdown.append("- 分析来源：").append(ANALYSIS_SOURCE).append("\n");
        markdown.append("- 题目：").append(problem.getTitle()).append("\n");
        markdown.append("- 评测结果：").append(formatVerdict(submission.getVerdict())).append("\n\n");

        markdown.append("### 结论摘要\n");
        markdown.append(summary).append("\n\n");

        if (failedCase != null) {
            markdown.append("### 第一个失败测试点\n");
            markdown.append("- 编号：").append(failedCase.getTestCaseNumber()).append("\n");
            markdown.append("- 是否隐藏：").append(failedCase.isHidden() ? "是" : "否").append("\n");
            markdown.append("- 输入：").append(wrapInlineMarkdown(failedCase.getInput())).append("\n");
            markdown.append("- 实际输出：").append(wrapInlineMarkdown(failedCase.getActualOutput())).append("\n");
            markdown.append("- 期望输出：").append(wrapInlineMarkdown(failedCase.getExpectedOutput())).append("\n\n");
        }

        markdown.append("### 重点观察\n");
        focusPoints.forEach(point -> markdown.append("- ").append(point).append("\n"));
        markdown.append("\n");

        if (wrongSolution != null) {
            markdown.append("### 错因分析\n");
            markdown.append(wrongSolution).append("\n\n");
        }
        if (correctSolution != null) {
            markdown.append("### 修改方向\n");
            markdown.append(correctSolution).append("\n\n");
        }

        markdown.append("### 下一步建议\n");
        fixDirections.forEach(direction -> markdown.append("- ").append(direction).append("\n"));
        return markdown.toString();
    }

    private SubmissionAnalysisResponse deserializeAnalysis(SubmissionAnalysis analysis) {
        if (analysis.getReportJson() != null && !analysis.getReportJson().isBlank()) {
            try {
                SubmissionAnalysisResponse response = objectMapper.readValue(analysis.getReportJson(), SubmissionAnalysisResponse.class);
                if (response == null) {
                    throw new IllegalStateException("reportJson deserialized to null");
                }
                response.setFocusPoints(response.getFocusPoints() == null ? List.of() : response.getFocusPoints());
                response.setFixDirections(response.getFixDirections() == null ? List.of() : response.getFixDirections());
                response.setIssueTags(response.getIssueTags() == null ? List.of() : response.getIssueTags());
                response.setFineGrainedTags(response.getFineGrainedTags() == null ? List.of() : response.getFineGrainedTags());
                response.setAbilityPoints(response.getAbilityPoints() == null ? List.of() : response.getAbilityPoints());
                response.setEvidenceRefs(response.getEvidenceRefs() == null ? List.of() : response.getEvidenceRefs());
                response.setLineIssues(response.getLineIssues() == null ? List.of() : response.getLineIssues());
                response.setAnalysisSchemaVersion(firstNonBlank(response.getAnalysisSchemaVersion(), "diagnosis-v1"));
                response.setEvidenceSchemaVersion(firstNonBlank(response.getEvidenceSchemaVersion(), DiagnosisEvidencePackage.SCHEMA_VERSION));
                response.setTaxonomyVersion(firstNonBlank(response.getTaxonomyVersion(), DiagnosisTaxonomy.TAXONOMY_VERSION));
                response.setUncertainty(firstNonBlank(response.getUncertainty(), "旧版诊断未记录不确定性说明。"));
                if (response.getAiInvocation() == null) {
                    response.setAiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                            .provider("UNKNOWN")
                            .model(response.getSourceType())
                            .modelVersion(response.getSourceType())
                            .promptVersion("unknown")
                            .agentVersion("")
                            .analysisSchemaVersion(response.getAnalysisSchemaVersion())
                            .evidenceSchemaVersion(response.getEvidenceSchemaVersion())
                            .taxonomyVersion(response.getTaxonomyVersion())
                            .status("LEGACY_REPORT")
                            .fallbackUsed(false)
                            .build());
                }
                response.setGeneratedAt(analysis.getGeneratedAt());
                return response;
            } catch (Exception exception) {
                log.warn("Failed to deserialize stored submission analysis JSON. submissionId={}, sourceType={}",
                        analysis.getSubmissionId(),
                        analysis.getAnalysisSource(),
                        exception);
            }
        }

        log.info("Falling back to lightweight submission analysis payload. submissionId={}, sourceType={}",
                analysis.getSubmissionId(),
                analysis.getAnalysisSource());
        return SubmissionAnalysisResponse.builder()
                .submissionId(analysis.getSubmissionId())
                .analysisSchemaVersion("diagnosis-v1")
                .evidenceSchemaVersion(DiagnosisEvidencePackage.SCHEMA_VERSION)
                .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                .sourceType(analysis.getAnalysisSource())
                .scenario(analysis.getScenario())
                .headline(analysis.getHeadline())
                .summary(analysis.getSummary())
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .abilityPoints(List.of())
                .focusPoints(List.of())
                .fixDirections(List.of())
                .evidenceRefs(List.of())
                .studentHint(null)
                .studentHintPlan(null)
                .learningActionEvidence(null)
                .teacherNote(null)
                .progressSignal(buildProgressSignal(analysis.getScenario()))
                .confidence(0.55)
                .uncertainty("旧版轻量报告缺少完整证据结构，需要结合原始提交继续判断。")
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .provider("UNKNOWN")
                        .model(analysis.getAnalysisSource())
                        .modelVersion(analysis.getAnalysisSource())
                        .promptVersion("unknown")
                        .agentVersion("")
                        .analysisSchemaVersion("diagnosis-v1")
                        .evidenceSchemaVersion(DiagnosisEvidencePackage.SCHEMA_VERSION)
                        .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                        .status("LEGACY_LIGHTWEIGHT")
                        .fallbackUsed(false)
                        .build())
                .answerLeakRisk("UNKNOWN")
                .lineIssues(List.of())
                .reportMarkdown(analysis.getReportMarkdown())
                .generatedAt(analysis.getGeneratedAt())
                .build();
    }

    private String serializeAnalysis(SubmissionAnalysisResponse analysis) {
        try {
            return objectMapper.writeValueAsString(analysis);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize submission analysis. submissionId={}, sourceType={}",
                    analysis == null ? null : analysis.getSubmissionId(),
                    analysis == null ? null : analysis.getSourceType(),
                    e);
            return null;
        }
    }

    private Assignment.HintPolicy resolveHintPolicy(Submission submission) {
        if (submission == null || submission.getAssignmentId() == null) {
            return Assignment.HintPolicy.L2;
        }
        return assignmentRepository.findById(submission.getAssignmentId())
                .map(Assignment::getHintPolicy)
                .orElse(Assignment.HintPolicy.L2);
    }

    private String mapScenario(Submission.Verdict verdict) {
        if (verdict == null) {
            return "UNKNOWN";
        }

        return switch (verdict) {
            case ACCEPTED -> "AC";
            case WRONG_ANSWER -> "WA";
            case TIME_LIMIT_EXCEEDED -> "TLE";
            case MEMORY_LIMIT_EXCEEDED -> "MLE";
            case RUNTIME_ERROR -> "RE";
            case COMPILATION_ERROR -> "CE";
            default -> "UNKNOWN";
        };
    }

    private List<String> inferIssueTags(String scenario, Submission submission) {
        String sourceCode = submission == null ? "" : Optional.ofNullable(submission.getSourceCode()).orElse("");
        List<String> tags = new ArrayList<>();
        switch (scenario) {
            case "AC" -> {
                tags.add("CODE_QUALITY");
                tags.add("GENERALIZATION_CHECK");
            }
            case "WA" -> {
                tags.add("BOUNDARY_CONDITION");
                tags.add("CONDITION_BRANCH");
                if (sourceCode.contains("for ") || sourceCode.contains("while ")) {
                    tags.add("LOOP_BOUNDARY");
                }
            }
            case "TLE" -> {
                tags.add("TIME_COMPLEXITY");
                tags.add("ALGORITHM_STRATEGY");
            }
            case "MLE" -> {
                tags.add("SPACE_COMPLEXITY");
                tags.add("DATA_STRUCTURE_CHOICE");
            }
            case "RE" -> {
                tags.add("RUNTIME_STABILITY");
                tags.add("BOUNDARY_CONDITION");
                tags.add("VARIABLE_INITIALIZATION");
            }
            case "CE" -> tags.add("SYNTAX_ERROR");
            default -> tags.add("NEEDS_MORE_EVIDENCE");
        }
        return deduplicate(tags);
    }

    private String buildUncertainty(String scenario, SubmissionAnalysisResponse.FailedCaseSnapshot failedCase) {
        if ("WA".equals(scenario) && failedCase != null && failedCase.isHidden()) {
            return "失败发生在隐藏测试点，系统不能看到具体输入输出；当前结论是基于评测结果和代码形态的方向性推断。";
        }
        if ("UNKNOWN".equals(scenario)) {
            return "当前评测证据不足，需要更多提交或日志才能形成稳定诊断。";
        }
        return "当前结论基于评测事实、规则信号和代码形态；若后续提交表现变化，诊断会继续更新。";
    }

    private List<String> inferAbilityPoints(List<String> issueTags, String scenario) {
        List<String> points = new ArrayList<>();
        if (issueTags.contains("SYNTAX_ERROR")) {
            points.add("语法与运行环境");
        }
        if (issueTags.contains("BOUNDARY_CONDITION") || issueTags.contains("LOOP_BOUNDARY")) {
            points.add("边界条件意识");
        }
        if (issueTags.contains("CONDITION_BRANCH")) {
            points.add("条件分支推理");
        }
        if (issueTags.contains("TIME_COMPLEXITY") || issueTags.contains("ALGORITHM_STRATEGY")) {
            points.add("算法复杂度分析");
        }
        if (issueTags.contains("SPACE_COMPLEXITY") || issueTags.contains("DATA_STRUCTURE_CHOICE")) {
            points.add("数据结构与空间管理");
        }
        if (issueTags.contains("CODE_QUALITY")) {
            points.add("代码表达与复盘");
        }
        if (points.isEmpty()) {
            points.add("问题定位与调试");
        }
        if ("AC".equals(scenario)) {
            points.add("迁移泛化");
        }
        return deduplicate(points);
    }

    private String buildStudentHint(String scenario, List<String> fixDirections) {
        String firstDirection = fixDirections == null || fixDirections.isEmpty() ? "" : fixDirections.get(0);
        String hint = switch (scenario) {
            case "AC" -> "本次已经通过。请不要急着结束，尝试补一个自己设计的边界样例，并检查命名和主流程是否还能更清楚。";
            case "WA" -> "先不要整体重写。请从首个失败场景出发，手推关键变量变化，并重点检查边界条件和条件分支。";
            case "TLE" -> "先找最耗时的循环或重复计算。请估算最大输入下会执行多少次核心操作，再决定优化方向。";
            case "MLE" -> "先画出当前保存了哪些数据。请判断哪些状态可以滚动更新，哪些副本其实不用保留。";
            case "RE" -> "先保证程序稳定运行。请检查下标、空输入、除零、递归出口这些最容易触发异常的位置。";
            case "CE" -> "先只修第一条语法或编译错误。不要同时大改逻辑，避免引入新的问题。";
            default -> firstDirection;
        };
        return sanitizeStudentHint(hint.isBlank() ? firstDirection : hint);
    }

    private SubmissionAnalysisResponse.StudentHintPlan buildStudentHintPlan(String scenario,
                                                                            List<String> issueTags,
                                                                            List<String> fineGrainedTags,
                                                                            List<String> evidenceRefs,
                                                                            String studentHint,
                                                                            String answerLeakRisk) {
        String primaryTag = fineGrainedTags != null && !fineGrainedTags.isEmpty()
                ? fineGrainedTags.get(0)
                : issueTags != null && !issueTags.isEmpty() ? issueTags.get(0) : "NEEDS_MORE_EVIDENCE";
        return SubmissionAnalysisResponse.StudentHintPlan.builder()
                .hintLevel("L2")
                .problemType(diagnosisTaxonomy.label(primaryTag))
                .evidenceAnchor(buildHintEvidenceAnchor(scenario, primaryTag, evidenceRefs))
                .nextAction(studentHint)
                .coachQuestion(buildHintCoachQuestion(primaryTag))
                .teachingAction(diagnosisTaxonomy.teachingAction(primaryTag))
                .evidenceRefs(evidenceRefs == null ? List.of() : evidenceRefs)
                .answerLeakRisk(answerLeakRisk)
                .build();
    }

    private String buildHintEvidenceAnchor(String scenario, String primaryTag, List<String> evidenceRefs) {
        String label = diagnosisTaxonomy.label(primaryTag);
        if (evidenceRefs != null && !evidenceRefs.isEmpty()) {
            return "当前判断锚定在“" + label + "”，可先核对证据：" + evidenceRefs.get(0) + "。";
        }
        return switch (scenario) {
            case "WA" -> "当前判断来自答案错误和代码形态，先围绕“" + label + "”做最小样例验证。";
            case "TLE" -> "当前判断来自超时结果，先围绕“" + label + "”估算最大规模操作次数。";
            case "MLE" -> "当前判断来自超内存结果，先围绕“" + label + "”画出保留的数据结构。";
            case "RE" -> "当前判断来自运行错误，先围绕“" + label + "”检查极端输入下的稳定性。";
            case "CE" -> "当前判断来自编译错误，先围绕“" + label + "”修复第一条报错。";
            case "AC" -> "当前判断来自已通过结果，先围绕“" + label + "”补一个边界复盘。";
            default -> "当前证据不足，先补一个最小样例或日志证据。";
        };
    }

    private String buildHintCoachQuestion(String primaryTag) {
        return switch (primaryTag == null ? "NEEDS_MORE_EVIDENCE" : primaryTag) {
            case "IO_FORMAT" -> "你的输出和题面要求相比，多了或少了哪个字符？";
            case "TIME_COMPLEXITY" -> "当输入取最大值时，核心循环大约执行多少次？";
            case "SPACE_COMPLEXITY" -> "哪些数据必须保留，哪些只是中间副本？";
            case "SYNTAX_ERROR" -> "第一条编译错误指向的符号，和你原本想表达的结构一致吗？";
            case "RUNTIME_STABILITY" -> "哪一种极端输入最可能触发越界、空值或除零？";
            case "GENERALIZATION_CHECK", "CODE_QUALITY" -> "你能补一个和样例不同的边界用例说明代码为什么也能过吗？";
            default -> "你准备用哪个最小样例验证这个判断？";
        };
    }

    private String buildTeacherNote(String scenario, List<String> issueTags) {
        String tagSummary = issueTags == null || issueTags.isEmpty() ? "暂无标准标签" : String.join("、", issueTags);
        return switch (scenario) {
            case "AC" -> "学生已完成当前任务，可引导其做代码复盘、复杂度解释和边界样例扩展。标签：" + tagSummary;
            case "WA" -> "学生主要卡在正确性调试阶段，适合用手推样例和边界分类进行干预。标签：" + tagSummary;
            case "TLE" -> "学生可能已接近正确思路，但复杂度意识不足，适合安排算法策略对比。标签：" + tagSummary;
            case "MLE" -> "学生需要关注状态保留和数据结构选择，可引导其画出内存占用结构。标签：" + tagSummary;
            case "RE" -> "学生需要先补稳定性保护，建议从异常日志和边界输入开始。标签：" + tagSummary;
            case "CE" -> "学生仍处于语法/环境修正阶段，可观察是否连续出现同类编译问题。标签：" + tagSummary;
            default -> "本次诊断证据不足，建议结合下一次提交继续观察。标签：" + tagSummary;
        };
    }

    private String buildProgressSignal(String scenario) {
        return switch (scenario) {
            case "AC" -> "已通过，进入复盘优化阶段";
            case "WA" -> "正确性调试中，重点观察边界和条件分支";
            case "TLE" -> "正确性可能接近，复杂度是当前阻碍";
            case "MLE" -> "空间使用是当前阻碍";
            case "RE" -> "程序稳定性需要优先修复";
            case "CE" -> "语法或编译环境需要先过关";
            default -> "需要更多提交证据";
        };
    }

    private double inferConfidence(String scenario,
                                   SubmissionAnalysisResponse.FailedCaseSnapshot failedCase,
                                   List<SubmissionAnalysisResponse.LineIssue> lineIssues) {
        if ("UNKNOWN".equals(scenario)) {
            return 0.48;
        }
        if (lineIssues != null && !lineIssues.isEmpty()) {
            return 0.86;
        }
        if (failedCase != null && !failedCase.isHidden()) {
            return 0.8;
        }
        if (failedCase != null) {
            return 0.68;
        }
        return "AC".equals(scenario) ? 0.78 : 0.72;
    }

    private String assessAnswerLeakRisk(String studentHint, String correctSolution) {
        String text = (Optional.ofNullable(studentHint).orElse("") + "\n" + Optional.ofNullable(correctSolution).orElse(""))
                .toLowerCase();
        if (text.contains("完整代码") || text.contains("参考代码") || text.contains("答案如下")
                || text.contains("```") || text.contains("#include") || text.contains("def ")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String sanitizeStudentHint(String hint) {
        if (hint == null) {
            return "";
        }
        return hint.replace("完整代码", "完整思路")
                .replace("参考代码", "参考方向")
                .replace("答案如下", "建议方向")
                .replace("```", "`")
                .trim();
    }

    private String inferComplexity(String sourceCode) {
        int loopCount = countMatches(sourceCode, LOOP_PATTERN);
        int recursionCount = countMatches(sourceCode, RECURSION_PATTERN);

        if (loopCount >= 3) {
            return "出现了三层及以上循环，需要警惕 O(n^3) 或更高复杂度。";
        }
        if (loopCount == 2) {
            return "存在双层循环，重点确认是否已经退化为 O(n^2)。";
        }
        if (loopCount == 1) {
            return "整体更像单层遍历，优先确认是否能稳定保持在线性复杂度。";
        }
        if (recursionCount > 0) {
            return "存在递归或搜索逻辑，需要同时评估状态数和剪枝效率。";
        }
        return "显式循环层级不高，复杂度更可能集中在常数项或隐式操作。";
    }

    private String inferMemoryPressure(String sourceCode) {
        int allocations = countMatches(sourceCode, MEMORY_PATTERN);

        if (allocations >= 3) {
            return "代码中存在多处容器或数组分配，建议先压缩缓存和中间状态。";
        }
        if (allocations >= 1) {
            return "当前实现依赖若干容器或数组，建议检查是否发生了重复拷贝。";
        }
        return "显式大对象分配不多，建议继续排查递归栈和隐式复制带来的开销。";
    }

    private int countMatches(String input, Pattern pattern) {
        if (input == null || input.isBlank()) {
            return 0;
        }

        int count = 0;
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private List<String> deduplicate(List<String> input) {
        Set<String> unique = new LinkedHashSet<>(input.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
        return new ArrayList<>(unique);
    }

    private SubmissionHistorySummaryResponse toHistorySummary(SubmissionHistoryProjection submission,
                                                              Problem problem,
                                                              SubmissionAnalysis analysis,
                                                              SubmissionCaseResultStatsProjection caseResultStats,
                                                              StudentAiFeedback feedback,
                                                              SubmissionGrowthSummaryResponse growthSummary) {
        int totalTestCases = caseResultStats == null || caseResultStats.getTotalTestCases() == null
                ? 0
                : caseResultStats.getTotalTestCases().intValue();
        int passedTestCases = caseResultStats == null || caseResultStats.getPassedTestCases() == null
                ? 0
                : caseResultStats.getPassedTestCases().intValue();
        return SubmissionHistorySummaryResponse.builder()
                .id(submission.getId())
                .problemId(submission.getProblemId())
                .assignmentId(submission.getAssignmentId())
                .studentProfileId(submission.getStudentProfileId())
                .problemTitle(problem == null ? "未知题目" : problem.getTitle())
                .languageId(submission.getLanguageId())
                .languageName(submission.getLanguageName())
                .verdict(submission.getVerdict())
                .executionTime(submission.getExecutionTime())
                .memoryUsed(submission.getMemoryUsed())
                .submittedAt(submission.getSubmittedAt())
                .passedTestCases(passedTestCases)
                .totalTestCases(totalTestCases)
                .analysisStatus(resolveAnalysisStatus(analysis))
                .analysisSourceType(analysis == null ? null : analysis.getAnalysisSource())
                .analysisHeadline(analysis == null ? null : analysis.getHeadline())
                .analysisSummary(analysis == null ? null : analysis.getSummary())
                .feedbackStatus(feedback == null ? "NOT_REQUESTED" : feedback.getStatus())
                .feedbackSource(feedback == null ? null : feedback.getSource())
                .feedbackFailureReason(feedback == null ? null : feedback.getFailureReason())
                .feedbackRevisionId(feedback == null ? null : feedback.getLatestRevisionId())
                .dataCompletenessStatus(historyCompleteness(submission, analysis, feedback))
                .growthSummary(growthSummary)
                .build();
    }

    private String historyCompleteness(SubmissionHistoryProjection submission,
                                       SubmissionAnalysis analysis,
                                       StudentAiFeedback feedback) {
        if (submission.getAssignmentId() != null && submission.getStudentProfileId() == null) {
            return "IDENTITY_MISSING";
        }
        if (analysis == null) {
            return "ANALYSIS_MISSING";
        }
        if (feedback == null) {
            return "FEEDBACK_MISSING";
        }
        return "COMPLETE";
    }

    private String resolveAnalysisStatus(SubmissionAnalysis analysis) {
        return analysis == null ? "PROCESSING" : "READY";
    }

    private String resolveAnalysisStatus(SubmissionAnalysisResponse analysis) {
        return analysis == null ? "PROCESSING" : "READY";
    }

    private String maskHiddenValue(String value, Boolean hidden) {
        return maskHiddenValue(value, hidden, "[隐藏测试点]");
    }

    private String maskHiddenValue(String value, Boolean hidden, String hiddenText) {
        if (Boolean.TRUE.equals(hidden)) {
            return hiddenText;
        }
        return value == null ? "" : value;
    }

    private String wrapInlineMarkdown(String value) {
        return "`" + truncateInline(value == null ? "" : value) + "`";
    }

    private String truncateInline(String value) {
        String normalized = Optional.ofNullable(value).orElse("")
                .replace("\n", "\\n")
                .replace("\r", "");
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }

    private String firstNonBlank(String primary, String defaultValue) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return defaultValue == null ? "" : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
