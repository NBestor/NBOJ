package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.dto.SubmissionAnalysisLookupResponse;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.dto.SubmissionHistorySummaryResponse;
import com.onlinejudge.submission.dto.SubmissionResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultStatsProjection;
import com.onlinejudge.submission.persistence.SubmissionHistoryProjection;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionCaseResultRepository submissionCaseResultRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final AiCodeAssistSupport aiCodeAssistSupport;
    private final AiReportService aiReportService;

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
        String problemTitle = problemRepository.findTitleById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + problemId));

        Problem problem = Problem.builder()
                .title(problemTitle)
                .build();

        List<SubmissionHistoryProjection> submissions = submissionRepository.findHistorySummariesByProblemId(problemId);
        if (submissions.isEmpty()) {
            return List.of();
        }

        List<Long> submissionIds = submissions.stream()
                .map(SubmissionHistoryProjection::getId)
                .toList();

        Map<Long, SubmissionAnalysis> analysisBySubmissionId = new HashMap<>();
        submissionAnalysisRepository.findBySubmissionIdIn(submissionIds)
                .forEach(analysis -> analysisBySubmissionId.put(analysis.getSubmissionId(), analysis));

        Map<Long, SubmissionCaseResultStatsProjection> caseResultStatsBySubmissionId = submissionCaseResultRepository
                .summarizeBySubmissionIdIn(submissionIds)
                .stream()
                .collect(Collectors.toMap(SubmissionCaseResultStatsProjection::getSubmissionId, stats -> stats));

        return submissions.stream()
                .map(submission -> toHistorySummary(
                        submission,
                        problem,
                        analysisBySubmissionId.get(submission.getId()),
                        caseResultStatsBySubmissionId.get(submission.getId())
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

    @Transactional
    public SubmissionAnalysisResponse generateAndStoreAnalysisForSubmission(Long submissionId) {
        log.info("Generating submission analysis from stored submission. submissionId={}", submissionId);
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));

        SubmissionAnalysisResponse existing = findExistingAnalysis(submissionId);
        if (existing != null) {
            log.info("Reusing existing submission analysis. submissionId={}, sourceType={}",
                    submissionId,
                    existing.getSourceType());
            return existing;
        }

        Problem problem = problemRepository.findById(submission.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + submission.getProblemId()));

        List<SubmissionCaseResult> caseResults = submissionCaseResultRepository
                .findBySubmissionIdOrderByTestCaseNumberAsc(submissionId);
        return generateAndStoreAnalysis(problem, submission, caseResults);
    }

    @Transactional
    public SubmissionAnalysisResponse generateAndStoreAnalysis(Problem problem, Submission submission, List<SubmissionCaseResult> caseResults) {
        log.info("Building submission analysis payload. submissionId={}, problemId={}, verdict={}, caseCount={}",
                submission.getId(),
                problem.getId(),
                submission.getVerdict(),
                caseResults == null ? 0 : caseResults.size());
        SubmissionAnalysisResponse analysis = buildAnalysis(problem, submission, caseResults);
        analysis = aiReportService.enhanceSubmissionAnalysis(problem, submission, analysis);

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

        SubmissionAnalysis savedAnalysis = submissionAnalysisRepository.save(entity);
        log.info("Persisted submission analysis. submissionId={}, sourceType={}, generatedAt={}",
                submission.getId(),
                analysis.getSourceType(),
                savedAnalysis.getGeneratedAt());
        analysis.setGeneratedAt(savedAnalysis.getGeneratedAt());
        return analysis;
    }

    public SubmissionResponse buildSubmissionResponse(Submission submission,
                                                      Problem problem,
                                                      List<SubmissionCaseResult> caseResults,
                                                      SubmissionAnalysisResponse analysis) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .problemId(submission.getProblemId())
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
        String reportMarkdown = buildMarkdown(problem, submission, headline, summary, focusPoints, fixDirections,
                wrongSolution, correctSolution, failedCase);

        return SubmissionAnalysisResponse.builder()
                .submissionId(submission.getId())
                .sourceType(ANALYSIS_SOURCE)
                .scenario(scenario)
                .headline(headline)
                .summary(summary)
                .focusPoints(focusPoints)
                .fixDirections(fixDirections)
                .wrongSolution(wrongSolution)
                .correctSolution(correctSolution)
                .lineIssues(initialLineIssues)
                .firstFailedCase(failedCase)
                .reportMarkdown(reportMarkdown)
                .build();
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
                response.setLineIssues(response.getLineIssues() == null ? List.of() : response.getLineIssues());
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
                .sourceType(analysis.getAnalysisSource())
                .scenario(analysis.getScenario())
                .headline(analysis.getHeadline())
                .summary(analysis.getSummary())
                .focusPoints(List.of())
                .fixDirections(List.of())
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
                                                              SubmissionCaseResultStatsProjection caseResultStats) {
        int totalTestCases = caseResultStats == null || caseResultStats.getTotalTestCases() == null
                ? 0
                : caseResultStats.getTotalTestCases().intValue();
        int passedTestCases = caseResultStats == null || caseResultStats.getPassedTestCases() == null
                ? 0
                : caseResultStats.getPassedTestCases().intValue();
        return SubmissionHistorySummaryResponse.builder()
                .id(submission.getId())
                .problemId(submission.getProblemId())
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
                .build();
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

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }
}

