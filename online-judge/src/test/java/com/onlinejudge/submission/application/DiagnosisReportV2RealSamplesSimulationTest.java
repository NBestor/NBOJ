package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.AiDiagnosisRun;
import com.onlinejudge.submission.domain.AiDiagnosisStageRun;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.persistence.AiDiagnosisStageRunRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:diagnosis-real-sample-simulation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "ai.enabled=true",
        "ai.api-key=${AI_EVAL_API_KEY:${MODELSCOPE_API_KEY:}}",
        "ai.base-url=${AI_EVAL_BASE_URL:${AI_BASE_URL:https://api-inference.modelscope.cn/v1}}",
        "ai.model=${AI_EVAL_MODEL:${AI_MODEL:deepseek-ai/DeepSeek-V4-Pro}}",
        "ai.timeout-seconds=${AI_EVAL_TIMEOUT_SECONDS:45}",
        "ai.external-runtime-enabled=true",
        "ai.external-runtime-profile=${AI_EVAL_RUNTIME_PROFILE:low-latency}",
        "ai.diagnosis-report-v3-enabled=true",
        "ai.max-output-tokens=${AI_EVAL_MAX_OUTPUT_TOKENS:1800}",
        "ai.stream-enabled=${AI_EVAL_STREAM_ENABLED:false}",
        "ai.structured-retry-enabled=${AI_EVAL_STRUCTURED_RETRY_ENABLED:true}",
        "ai.structured-retry-output-tokens=${AI_EVAL_STRUCTURED_RETRY_OUTPUT_TOKENS:2600}",
        "ai.retry.max-attempts=${AI_EVAL_RETRY_MAX_ATTEMPTS:1}",
        "ai.retry.backoff-ms=${AI_EVAL_RETRY_BACKOFF_MS:700}",
        "ai.standard-library-navigation.max-rounds=${AI_EVAL_STANDARD_LIBRARY_NAVIGATION_MAX_ROUNDS:6}",
        "ai.standard-library-growth.enabled=false",
        "app.content-migration.enabled=${AI_REAL_SAMPLE_CONTENT_SEED_ENABLED:true}"
})
class DiagnosisReportV2RealSamplesSimulationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DiagnosticAgentService diagnosticAgentService;

    @Autowired
    private AiDiagnosisWorkflowService diagnosisWorkflowService;

    @Autowired
    private AiDiagnosisStageRunRepository diagnosisStageRunRepository;

    @Value("${ai.standard-library-navigation.max-rounds}")
    private int standardLibraryNavigationMaxRounds;

    @Test
    void realSampleSimulationUsesProductionNavigationDepthByDefault() {
        assertThat(standardLibraryNavigationMaxRounds).isGreaterThanOrEqualTo(6);
    }

    @Test
    void runWebsiteVsCodexSimulationReportWhenEnabled() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(env("AI_REAL_SAMPLE_SIMULATION_ENABLED", "false")),
                "Set AI_REAL_SAMPLE_SIMULATION_ENABLED=true to run live real-sample simulation.");
        Assumptions.assumeTrue(!env("AI_EVAL_API_KEY", env("MODELSCOPE_API_KEY", "")).isBlank(),
                "Set AI_EVAL_API_KEY or MODELSCOPE_API_KEY to call the external model.");

        List<RealSample> samples = selectedSamples(loadSamples());
        int limit = (int) longEnv("AI_REAL_SAMPLE_SIMULATION_LIMIT", samples.size());
        List<RealSample> selected = samples.stream().limit(Math.max(1, Math.min(limit, samples.size()))).toList();

        List<ReportEntry> entries = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            waitBetweenCases(index, longEnv("AI_EVAL_CASE_DELAY_MS", 750));
            RealSample sample = selected.get(index);
            SimulationFixture fixture = fixtureFor(sample);
            List<AiReportService.ModelCallTraceEvent> traceEvents = new ArrayList<>();
            DiagnosticAgentService.AgentResult result;
            long latencyMs;
            AiDiagnosisRun diagnosisRun = diagnosisWorkflowService.beginRun(
                    fixture.submission().getId(),
                    UUID.randomUUID().toString()
            );
            try (AiDiagnosisWorkflowContext.Scope ignoredRun = AiDiagnosisWorkflowContext.activate(diagnosisRun.getId());
                 AutoCloseable ignoredTrace = AiReportService.captureModelCallTrace(traceEvents::add)) {
                long startedAt = System.nanoTime();
                result = diagnosticAgentService.diagnose(
                        fixture.problem(),
                        fixture.submission(),
                        fixture.caseResults(),
                        fixture.baseline(),
                        Assignment.HintPolicy.L3
                );
                latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            }
            List<AiDiagnosisStageRun> stageRuns = diagnosisStageRunRepository
                    .findByRunIdOrderByIdAsc(diagnosisRun.getId());
            Set<String> stageTypes = stageRuns.stream()
                    .map(AiDiagnosisStageRun::getStageType)
                    .collect(Collectors.toSet());
            assertThat(stageTypes).contains(
                    "CORE_DIAGNOSIS", "ISSUE_ATTACHMENT", "STUDENT_OUTPUT", "TEACHER_OUTPUT");
            assertThat(stageRuns.stream()
                    .filter(stage -> Set.of("CORE_DIAGNOSIS", "ISSUE_ATTACHMENT", "STUDENT_OUTPUT", "TEACHER_OUTPUT")
                            .contains(stage.getStageType())))
                    .allMatch(stage -> AiDiagnosisWorkflowService.STAGE_SUCCEEDED.equals(stage.getStatus()));
            System.out.println("Durable diagnosis stage summary: runId=" + diagnosisRun.getId()
                    + ", stages=" + stageRuns.stream()
                    .map(stage -> stage.getStageKey() + ":" + stage.getStatus() + ":" + stage.getLatencyMs() + "ms")
                    .toList());
            Path tracePath = writeTrace(sample, result.analysis(), traceEvents);
            entries.add(ReportEntry.from(sample, result.analysis(), latencyMs, codexSimulation(sample.id()),
                    tracePath, traceEvents));
        }

        Path reportPath = writeReport(entries);
        System.out.println("Real sample website-vs-codex simulation report saved to: " + reportPath.toAbsolutePath());
        assertThat(reportPath).exists();
        assertThat(entries).hasSize(selected.size());
        if (Boolean.parseBoolean(env("AI_REAL_SAMPLE_REQUIRE_LIBRARY", "true"))) {
            assertThat(entries)
                    .extracting(ReportEntry::standardLibraryNavigationStatus)
                    .doesNotContain("LIBRARY_EMPTY");
        }
    }

    @Test
    void firstFiveRealSamplesUseLongNaturalCodeResources() throws Exception {
        List<RealSample> samples = loadSamples().stream().limit(5).toList();

        assertThat(samples).hasSize(5);
        for (RealSample sample : samples) {
            String code = resolvedBuggyCode(sample);
            assertThat(code.lines().count()).as(sample.id()).isGreaterThanOrEqualTo(200);
            assertThat(code).as(sample.id()).doesNotContain("helper_1", "helper_2", "def helper_");
            assertThat(code).as(sample.id()).doesNotContain("# BUG", "# FIXME", "should add", "should subtract");
            assertThat(sample.buggyCodeResource()).as(sample.id()).isNotBlank();
        }
    }

    private List<RealSample> loadSamples() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/diagnosis-eval-fixtures/diagnosis-report-v2-real-samples.json")) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        }
    }

    private List<RealSample> selectedSamples(List<RealSample> samples) {
        String rawIds = env("AI_REAL_SAMPLE_SIMULATION_IDS", "");
        if (rawIds.isBlank()) {
            return samples;
        }
        List<String> ids = new ArrayList<>();
        for (String id : rawIds.split(",")) {
            String normalized = id.trim();
            if (!normalized.isBlank()) {
                ids.add(normalized);
            }
        }
        if (ids.isEmpty()) {
            return samples;
        }
        return samples.stream()
                .filter(sample -> ids.contains(sample.id()))
                .toList();
    }

    private SimulationFixture fixtureFor(RealSample sample) throws Exception {
        long stableId = 90_000L + Math.abs(sample.id().hashCode() % 10_000);
        FailureExample failure = failureExample(sample.id());
        Problem problem = Problem.builder()
                .id(stableId)
                .title(sample.title())
                .description("""
                        ## 题目描述
                        %s

                        ## 仿真说明
                        本题来自 diagnosis-report-v2 real sample，用于比较网站真实 AI 链路与人工 AI 仿真输出。
                        """.formatted(sample.problemSummary()))
                .difficulty(Problem.Difficulty.MEDIUM)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        Submission submission = Submission.builder()
                .id(stableId)
                .problemId(stableId)
                .languageName(languageName(sample.language()))
                .verdict(Submission.Verdict.valueOf(sample.judgeResult()))
                .sourceCode(resolvedBuggyCode(sample))
                .build();
        List<SubmissionCaseResult> caseResults = List.of(SubmissionCaseResult.builder()
                .submissionId(stableId)
                .testCaseNumber(1)
                .passed(false)
                .hidden(false)
                .inputSnapshot(failure.input())
                .actualOutput(failure.actual())
                .expectedOutput(failure.expected())
                .executionTime(1.0)
                .memoryUsed(1024)
                .build());
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(stableId)
                .sourceType("REAL_SAMPLE_SIMULATION_BASELINE")
                .scenario(sample.judgeResult())
                .headline("仿真基线诊断")
                .summary("基线只提供题目、代码和首个失败样例，正式判断交给网站 AI 链路。")
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of("judge:first_failed_case"))
                .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                        .testCaseNumber(1)
                        .hidden(false)
                        .input(failure.input())
                        .actualOutput(failure.actual())
                        .expectedOutput(failure.expected())
                        .build())
                .answerLeakRisk("LOW")
                .confidence(0.5)
                .build();
        return new SimulationFixture(problem, submission, caseResults, baseline);
    }

    private String resolvedBuggyCode(RealSample sample) throws Exception {
        String resource = safe(sample.buggyCodeResource());
        if (resource.isBlank()) {
            return sample.buggyCode();
        }
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as("Missing sample code resource: " + resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private FailureExample failureExample(String id) {
        Map<String, FailureExample> examples = new LinkedHashMap<>();
        examples.put("advanced-coupon-shortest-path-state", new FailureExample(
                "3 3 1\n1 2 5\n2 3 5\n1 3 100",
                "10",
                "7"));
        examples.put("advanced-interval-dp-order-sum", new FailureExample(
                "4\n1 2 3 4",
                "9",
                "19"));
        examples.put("advanced-segment-tree-lazy-range", new FailureExample(
                "3 4\n-5 -4 -6\n1 1 2 3\n1 2 3 4\n2 1 3\n2 3 3",
                "3\n0",
                "3\n-2"));
        examples.put("advanced-offline-connectivity-rollback", new FailureExample(
                "3 5\n1 1 2\n3 1 2\n2 1 2\n3 1 2\n3 2 3",
                "Yes\nYes\nNo",
                "Yes\nNo\nNo"));
        examples.put("advanced-tree-rerooting-weighted", new FailureExample(
                "3\n10 1 1\n1 2\n2 3",
                "-1 -12 -23",
                "3 2 13"));
        examples.put("sum-right-endpoint", new FailureExample("5", "10", "15"));
        examples.put("matrix-difference-prefix-restore", new FailureExample("2 2 1\n1 1 2 2 3", "3", "3 3\n3 3"));
        examples.put("monotonic-stack-duplicates", new FailureExample("4\n2 2 1 2", "0 0 2 0", "0 1 2 2"));
        examples.put("stone-merge-dp-interval", new FailureExample("4\n1 2 3 4", "10", "19"));
        examples.put("dynamic-connectivity-missing-union", new FailureExample("3 3\n1 1 2\n2 1 2\n2 2 3", "No\nNo", "Yes\nNo"));
        examples.put("string-window-boundary", new FailureExample("a\n1", "0", "1"));
        examples.put("sliding-window-balance-count", new FailureExample("3 3\n2 1 2", "1", "2"));
        examples.put("layered-shortest-path-state", new FailureExample("3 2 1\n1 2 5\n2 3 5", "10", "5"));
        return examples.getOrDefault(id, new FailureExample("visible sample", "wrong", "expected"));
    }

    private CodexSimulation codexSimulation(String id) {
        Map<String, CodexSimulation> simulations = new LinkedHashMap<>();
        simulations.put("advanced-coupon-shortest-path-state", new CodexSimulation(
                "分层最短路里优惠边没有折半、按节点做全局 best 剪枝会丢掉不同已用券层的状态，最后还只取恰好 k 层而不是最多 k。",
                "HIT", 3, 1, "先用 3 个点、1 张券的小图手推 dist[node][used]，分别记录普通转移和优惠转移。"));
        simulations.put("advanced-interval-dp-order-sum", new CodexSimulation(
                "区间 DP 没有按区间长度推进，区间和函数把右端当成开区间，还把线性合并误切到环形模板和贪心下界。",
                "HIT", 3, 1, "用 4 堆石子画出长度 1、2、3 的区间表，检查每个状态依赖是否已经算好。"));
        simulations.put("advanced-segment-tree-lazy-range", new CodexSimulation(
                "区间加的 lazy 被覆盖而不是累加；负数区间查询的无交集返回 0；输入右端坐标没有正确转成 0-based。",
                "PARTIAL", 3, 1, "连续做两次区间加，再查一个全负子区间，手推每个节点的 lazy 和 max。"));
        simulations.put("advanced-offline-connectivity-rollback", new CodexSimulation(
                "离线动态连通性的删除时间右端应到 t-1，rollback 对 no-op 合并和 components 的恢复不完整，递归回退后状态被污染。",
                "HIT", 3, 1, "把一条边的 add/remove/query 时间轴画出来，标出它应该覆盖哪些叶子节点。"));
        simulations.put("advanced-tree-rerooting-weighted", new CodexSimulation(
                "树形换根 DP 把子树点数当作子树权重使用，换根时对子树内外贡献方向也写反，非全 1 权重链会立刻暴露。",
                "PARTIAL", 2, 1, "用 3 个点链且点权不相等的例子，分别从每个根计算加权距离和。"));
        simulations.put("sum-right-endpoint", new CodexSimulation(
                "循环右端点漏取；range 的右端不包含 n，导致 1..n 少算最后一项。",
                "HIT", 1, 1, "手推 n=1 或 n=2，列出循环变量实际出现过哪些值。"));
        simulations.put("matrix-difference-prefix-restore", new CodexSimulation(
                "二维差分还原时状态来源不完整/被污染，还只输出右下角，无法代表最终矩阵。",
                "PARTIAL", 2, 1, "用 2x2 表格手写差分标记和还原过程，先核对每个格子的来源。"));
        simulations.put("monotonic-stack-duplicates", new CodexSimulation(
                "重复值场景下弹栈条件和题意的严格/非严格关系不一致。",
                "PARTIAL", 1, 1, "构造含相等元素的小样例，观察相等元素应不应该保留在栈里。"));
        simulations.put("stone-merge-dp-interval", new CodexSimulation(
                "区间 DP 依赖短区间先算，当前按左端点推进会读到未完成的大/小区间状态。",
                "HIT", 1, 1, "按区间长度从 1、2、3 手推依赖关系，确认每次转移依赖是否已算好。"));
        simulations.put("dynamic-connectivity-missing-union", new CodexSimulation(
                "并查集合并条件反了，只在已经连通时合并，原本不连通的加边没有生效。",
                "HIT", 1, 1, "手推两个原本不连通点执行加边后，父节点集合有没有发生合并。"));
        simulations.put("string-window-boundary", new CodexSimulation(
                "窗口长度计算没有包含当前右端点，长度为 1 的合法窗口会被算成 0。",
                "PARTIAL", 1, 1, "用单字符窗口手推 left、right 和窗口长度之间的关系。"));
        simulations.put("sliding-window-balance-count", new CodexSimulation(
                "缩窗时先移动 left 再扣元素，扣掉的是新 left 元素，窗口状态和指针不同步。",
                "PARTIAL", 1, 1, "写出 left 移动前后窗口实际包含的元素，再核对 cost 是否等于窗口和。"));
        simulations.put("layered-shortest-path-state", new CodexSimulation(
                "最短路状态缺少已使用特殊操作次数，无法区分同一节点在不同层数下的代价。",
                "HIT", 1, 1, "把状态写成“节点 + 已用次数”的语义检查表，再判断原代码记录了哪些信息。"));
        return simulations.get(id);
    }

    private Path writeReport(List<ReportEntry> entries) throws Exception {
        Path dir = Path.of("target", "ai-simulation-reports");
        Files.createDirectories(dir);
        Path reportPath = dir.resolve("real-samples-website-vs-codex-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md");
        StringBuilder out = new StringBuilder();
        out.append("# Real Samples 网站链路 vs Codex 仿真对照\n\n");
        out.append("- 样本数：").append(entries.size()).append("\n");
        out.append("- 网站线：Spring `DiagnosticAgentService` + 当前标准库 + 当前 prompt/validator + 外部模型。\n");
        out.append("- Codex 仿真线：Codex 按同题题意、代码、失败样例给出的理想诊断摘要。\n\n");
        out.append("| 题目 | 网站状态 | 阶段 trace | 标准库挂接 | advice | 网站建议数 | trace 文件 | 对照判断 |\n");
        out.append("|---|---|---|---|---|---:|---|---|\n");
        for (ReportEntry entry : entries) {
            out.append("| ")
                    .append(escape(entry.title()))
                    .append(" | ")
                    .append(escape(entry.websiteStatus()))
                    .append(" | ")
                    .append(escape(entry.traceStages()))
                    .append(" | ")
                    .append(escape(entry.standardLibraryNavigationStatus()))
                    .append(" | ")
                    .append(escape(entry.adviceGenerationStatus()))
                    .append(" | ")
                    .append(entry.websiteAdviceCount())
                    .append(" | ")
                    .append(escape(entry.tracePath()))
                    .append(" | ")
                    .append(escape(entry.comparison()))
                    .append(" |\n");
        }
        out.append("\n## 逐题明细\n\n");
        for (ReportEntry entry : entries) {
            out.append("### ").append(entry.title()).append("\n\n");
            out.append("- 网站耗时：").append(entry.latencyMs()).append(" ms\n");
            out.append("- 网站状态：").append(entry.websiteStatus()).append("，fallback=").append(entry.fallbackUsed()).append("\n");
            out.append("- 失败原因：").append(entry.websiteFailureReason()).append("\n");
            out.append("- trace 文件：`").append(entry.tracePath()).append("`\n");
            out.append("- 阶段 trace：").append(entry.traceStages()).append("\n");
            out.append("- 标准库挂接：").append(entry.standardLibraryNavigationStatus()).append("\n");
            out.append("- advice 状态：").append(entry.adviceGenerationStatus()).append("\n");
            out.append("- 网站摘要：").append(entry.websiteSummary()).append("\n");
            out.append("- 网站基础建议：").append(entry.websiteBasicAdvice()).append("\n");
            out.append("- 网站提高建议：").append(entry.websiteImprovementAdvice()).append("\n");
            out.append("- 网站下一步：").append(entry.websiteNextAction()).append("\n");
            out.append("- Codex 主因：").append(entry.codex().rootCause()).append("\n");
            out.append("- Codex 下一步：").append(entry.codex().nextAction()).append("\n");
            out.append("- 对照：").append(entry.comparison()).append("\n\n");
        }
        Files.writeString(reportPath, out.toString());
        return reportPath;
    }

    private Path writeTrace(RealSample sample,
                            SubmissionAnalysisResponse analysis,
                            List<AiReportService.ModelCallTraceEvent> traceEvents) throws Exception {
        Path dir = Path.of("target", "ai-simulation-reports");
        Files.createDirectories(dir);
        Path tracePath = dir.resolve("ai-request-trace-" + sample.id() + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".jsonl");
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (AiReportService.ModelCallTraceEvent event : traceEvents) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "model-call");
            row.put("sampleId", sample.id());
            row.put("sampleTitle", sample.title());
            row.put("index", index++);
            row.put("stage", event.stage());
            row.put("stream", event.stream());
            row.put("outputTokens", event.outputTokens());
            row.put("runtimeProfile", event.runtimeProfile());
            row.put("requestCompact", event.requestCompact());
            row.put("latencyMs", event.latencyMs());
            row.put("systemPrompt", event.systemPrompt());
            row.put("userPrompt", event.userPrompt());
            row.put("requestBody", event.requestBody());
            row.put("responseBody", event.responseBody());
            row.put("content", event.content());
            row.put("error", event.error());
            lines.add(objectMapper.writeValueAsString(row));
        }
        SubmissionAnalysisResponse.AiInvocation invocation = analysis == null ? null : analysis.getAiInvocation();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", "backend-summary");
        summary.put("sampleId", sample.id());
        summary.put("status", invocation == null ? "" : invocation.getStatus());
        summary.put("failureStage", invocation == null ? "" : invocation.getFailureStage());
        summary.put("failureReason", invocation == null ? "" : invocation.getFailureReason());
        summary.put("standardLibraryNavigationStatus", invocation == null ? "" : invocation.getStandardLibraryNavigationStatus());
        summary.put("standardLibraryNavigationFailureReason",
                invocation == null ? "" : invocation.getStandardLibraryNavigationFailureReason());
        summary.put("adviceGenerationStatus", invocation == null ? "" : invocation.getAdviceGenerationStatus());
        summary.put("adviceGenerationFailureReason", invocation == null ? "" : invocation.getAdviceGenerationFailureReason());
        summary.put("basicAdviceCount", invocation == null ? null : invocation.getBasicAdviceCount());
        summary.put("improvementAdviceCount", invocation == null ? null : invocation.getImprovementAdviceCount());
        lines.add(objectMapper.writeValueAsString(summary));
        Files.writeString(tracePath, String.join("\n", lines) + "\n");
        return tracePath;
    }

    private static String comparison(RealSample sample, SubmissionAnalysisResponse analysis, CodexSimulation codex) {
        SubmissionAnalysisResponse.AiInvocation invocation = analysis.getAiInvocation();
        String websiteFit = invocation == null ? "" : safe(invocation.getDiagnosisLibraryFit());
        boolean fitCompatible = websiteFit.isBlank() || websiteFit.equalsIgnoreCase(sample.expectedLibraryFit())
                || ("PARTIAL".equals(websiteFit) && "HIT".equals(sample.expectedLibraryFit()))
                || ("MISS".equals(websiteFit) && sample.shouldGenerateGrowthCandidate());
        boolean hasStudentAction = analysis.getStudentFeedback() != null
                && analysis.getStudentFeedback().getNextLearningAction() != null
                && !safe(analysis.getStudentFeedback().getNextLearningAction().getTask()).isBlank();
        boolean safe = !"HIGH".equalsIgnoreCase(safe(analysis.getAnswerLeakRisk()));
        if (invocation == null || invocation.isFallbackUsed()) {
            return "网站线未完成模型诊断，Codex 线仅作目标参照。";
        }
        if (fitCompatible && hasStudentAction && safe) {
            return "方向基本一致，可继续人工复核表达颗粒度。";
        }
        List<String> gaps = new ArrayList<>();
        if (!fitCompatible) {
            gaps.add("libraryFit 不一致");
        }
        if (!hasStudentAction) {
            gaps.add("下一步动作不足");
        }
        if (!safe) {
            gaps.add("安全风险偏高");
        }
        return "存在差距：" + String.join("、", gaps);
    }

    private static String languageName(String language) {
        return "CPP17".equalsIgnoreCase(language) ? "C++17" : "Python 3";
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long longEnv(String name, long fallback) {
        try {
            return Long.parseLong(env(name, Long.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void waitBetweenCases(int index, long delayMs) throws InterruptedException {
        if (index > 0 && delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String excerpt(String value, int maxLength) {
        String safe = safe(value).replace('\n', ' ');
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength - 1) + "…";
    }

    private static String escape(String value) {
        return safe(value).replace("|", "\\|").replace("\n", " ");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RealSample(
            String id,
            String title,
            String problemSummary,
            String language,
            String buggyCode,
            String buggyCodeResource,
            String judgeResult,
            String expectedLibraryFit,
            List<String> expectedAnchors,
            List<String> expectedStudentFeedbackQuality,
            boolean shouldGenerateGrowthCandidate,
            String quickFeedbackKnownLimit,
            List<String> formalMustImproveAtLeastOneOf
    ) {
    }

    private record SimulationFixture(
            Problem problem,
            Submission submission,
            List<SubmissionCaseResult> caseResults,
            SubmissionAnalysisResponse baseline
    ) {
    }

    private record FailureExample(String input, String actual, String expected) {
    }

    private record CodexSimulation(
            String rootCause,
            String libraryFit,
            int basicAdviceCount,
            int improvementAdviceCount,
            String nextAction
    ) {
    }

    private record ReportEntry(
            String title,
            long latencyMs,
            String websiteStatus,
            boolean fallbackUsed,
            String websiteFailureReason,
            String websiteLibraryFit,
            int websiteAdviceCount,
            String websiteSummary,
            String websiteBasicAdvice,
            String websiteImprovementAdvice,
            String websiteNextAction,
            String tracePath,
            String traceStages,
            String standardLibraryNavigationStatus,
            String adviceGenerationStatus,
            CodexSimulation codex,
            String comparison
    ) {
        static ReportEntry from(RealSample sample,
                                SubmissionAnalysisResponse analysis,
                                long latencyMs,
                                CodexSimulation codex,
                                Path tracePath,
                                List<AiReportService.ModelCallTraceEvent> traceEvents) {
            SubmissionAnalysisResponse.AiInvocation invocation = analysis.getAiInvocation();
            int basicCount = invocation == null || invocation.getBasicAdviceCount() == null
                    ? safeList(analysis.getBasicLayerAdvice()).size()
                    : invocation.getBasicAdviceCount();
            int improvementCount = invocation == null || invocation.getImprovementAdviceCount() == null
                    ? safeList(analysis.getImprovementLayerAdvice()).size()
                    : invocation.getImprovementAdviceCount();
            String nextAction = "";
            if (analysis.getStudentFeedback() != null && analysis.getStudentFeedback().getNextLearningAction() != null) {
                nextAction = excerpt(analysis.getStudentFeedback().getNextLearningAction().getTask(), 140);
            }
            String basicAdvice = joinedBasicAdvice(analysis.getBasicLayerAdvice());
            String improvementAdvice = joinedImprovementAdvice(analysis.getImprovementLayerAdvice());
            String status = invocation == null ? analysis.getSourceType() : invocation.getStatus();
            String libraryFit = invocation == null ? "" : invocation.getDiagnosisLibraryFit();
            boolean fallback = invocation == null || invocation.isFallbackUsed();
            String failureReason = invocation == null
                    ? ""
                    : safe(invocation.getFailureStage()) + "/" + safe(invocation.getFailureReason());
            String uncertainty = safe(analysis.getUncertainty());
            if (!uncertainty.isBlank() && !"/".equals(failureReason)) {
                failureReason = failureReason + " " + excerpt(uncertainty, 180);
            }
            ReportEntry draft = new ReportEntry(
                    sample.title(),
                    latencyMs,
                    status,
                    fallback,
                    failureReason,
                    safe(libraryFit),
                    basicCount + improvementCount,
                    excerpt(analysis.getSummary(), 180),
                    basicAdvice,
                    improvementAdvice,
                    nextAction,
                    tracePath == null ? "" : tracePath.toString(),
                    traceStages(traceEvents),
                    invocation == null ? "" : safe(invocation.getStandardLibraryNavigationStatus()),
                    invocation == null ? "" : safe(invocation.getAdviceGenerationStatus()),
                    codex,
                    ""
            );
            return new ReportEntry(
                    draft.title(),
                    draft.latencyMs(),
                    draft.websiteStatus(),
                    draft.fallbackUsed(),
                    draft.websiteFailureReason(),
                    draft.websiteLibraryFit(),
                    draft.websiteAdviceCount(),
                    draft.websiteSummary(),
                    draft.websiteBasicAdvice(),
                    draft.websiteImprovementAdvice(),
                    draft.websiteNextAction(),
                    draft.tracePath(),
                    draft.traceStages(),
                    draft.standardLibraryNavigationStatus(),
                    draft.adviceGenerationStatus(),
                    draft.codex(),
                    DiagnosisReportV2RealSamplesSimulationTest.comparison(sample, analysis, codex)
            );
        }

        private static <T> List<T> safeList(List<T> values) {
            return values == null ? List.of() : values;
        }

        private static String joinedBasicAdvice(List<SubmissionAnalysisResponse.BasicLayerAdvice> values) {
            List<SubmissionAnalysisResponse.BasicLayerAdvice> items = safeList(values);
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                SubmissionAnalysisResponse.BasicLayerAdvice item = items.get(i);
                if (item == null) {
                    continue;
                }
                lines.add((lines.size() + 1) + ". "
                        + excerpt(item.getTitle() + "：" + item.getStudentAction(), 120));
            }
            return String.join("；", lines);
        }

        private static String traceStages(List<AiReportService.ModelCallTraceEvent> values) {
            List<String> stages = new ArrayList<>();
            for (AiReportService.ModelCallTraceEvent event : safeList(values)) {
                if (event == null) {
                    continue;
                }
                String suffix = safe(event.error()).isBlank() ? "OK" : "ERR";
                stages.add(event.stage() + ":" + suffix);
            }
            return String.join(" -> ", stages);
        }

        private static String joinedImprovementAdvice(List<SubmissionAnalysisResponse.ImprovementLayerAdvice> values) {
            List<SubmissionAnalysisResponse.ImprovementLayerAdvice> items = safeList(values);
            List<String> lines = new ArrayList<>();
            for (SubmissionAnalysisResponse.ImprovementLayerAdvice item : items) {
                if (item == null) {
                    continue;
                }
                lines.add((lines.size() + 1) + ". "
                        + excerpt(item.getTitle() + "：" + item.getSuggestion(), 120));
            }
            return String.join("；", lines);
        }
    }
}
