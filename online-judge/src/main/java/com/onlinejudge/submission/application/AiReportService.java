package com.onlinejudge.submission.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiReportService {

    private static final int MAX_SOURCE_CODE_LENGTH = 12000;
    private static final String AI_SOURCE = "MODEL_SCOPE_MINIMAX_M2_7";
    private static final Pattern NUMBERED_LINE_PATTERN = Pattern.compile("^(\\d+):\\s?(.*)$", Pattern.MULTILINE);
    private static final Pattern REPORT_LINE_ISSUE_PATTERN = Pattern.compile(
            "行号[：:]\\s*(\\d+)\\s*错误[：:]\\s*(.+?)\\s*建议[：:]\\s*(.+?)(?=(?:\\n\\s*行号[：:])|\\Z)",
            Pattern.DOTALL
    );

    private final ObjectMapper objectMapper;
    private final AiCodeAssistSupport aiCodeAssistSupport;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${ai.enabled:true}")
    private boolean enabled;

    @Value("${ai.base-url:https://api-inference.modelscope.cn/v1}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:MiniMax/MiniMax-M2.7}")
    private String model;

    @Value("${ai.timeout-seconds:25}")
    private long timeoutSeconds;

    public SubmissionAnalysisResponse enhanceSubmissionAnalysis(Problem problem,
                                                                Submission submission,
                                                                SubmissionAnalysisResponse fallback) {
        if (!canCallAi()) {
            log.info("AI submission analysis skipped because AI access is unavailable. submissionId={}", submission.getId());
            return fallback;
        }

        try {
            log.info("AI submission analysis started. submissionId={}, problemId={}, language={}",
                    submission.getId(),
                    submission.getProblemId(),
                    submission.getLanguageName());
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("problemTitle", problem.getTitle());
            context.put("problemDescription", problem.getDescription());
            context.put("aiPromptDirection", problem.getAiPromptDirection() == null ? "" : problem.getAiPromptDirection());
            context.put("verdict", submission.getVerdict() == null ? "UNKNOWN" : submission.getVerdict().name());
            context.put("language", submission.getLanguageName());
            context.put("sourceCode", truncateSourceCode(submission.getSourceCode()));
            context.put("numberedSourceCode", aiCodeAssistSupport.addLineNumbers(truncateSourceCode(submission.getSourceCode())));
            context.put("compileOutput", submission.getCompileOutput() == null ? "" : submission.getCompileOutput());
            context.put("runtimeErrorMessage", submission.getErrorMessage() == null ? "" : submission.getErrorMessage());
            context.put("baselineAnalysis", fallback);
            context.put("firstFailedCase", fallback == null ? null : fallback.getFirstFailedCase());

            String content = chatCompletion(
                    """
                    你是中文 OJ 智能教练。
                    请根据评测结果输出严格 JSON，不要输出 Markdown 代码块，不要输出 JSON 之外的任何解释，不要输出 <think>、思考过程或草稿。
                    JSON 字段必须包含：
                    headline(string),
                    summary(string),
                    focusPoints(string[]),
                    fixDirections(string[]),
                    wrongSolution(string|null),
                    correctSolution(string|null),
                    lineIssues([{lineNumber(number), error(string), suggestion(string)}]),
                    reportMarkdown(string)
                    如果 verdict 是 COMPILATION_ERROR，必须优先根据 compileOutput 给出带行号的 lineIssues；如果是 RUNTIME_ERROR 且 runtimeErrorMessage 含有行号，也必须优先使用这些行号。

                    纠错格式要求：
                    1. 必须优先基于带行号代码进行分析。
                    2. 只要指出代码问题，就必须给出具体 lineNumber。
                    3. 每条 lineIssues 都必须同时包含 error 和 suggestion。
                    4. 禁止返回“检查循环”“看看边界”这类不带行号的模糊建议放进 lineIssues。
                    5. 如果暂时无法定位到具体行，就返回空数组 []，不要编造行号。
                    6. reportMarkdown 中如果提到代码问题，也必须尽量引用具体行号，格式示例：
                       行号：5
                       错误：变量未定义
                       建议：定义变量后再使用

                    要求：
                    1. 全部使用中文。
                    2. 结论必须贴合 OJ 评测场景，不要空泛。
                    3. 如果失败测试点是隐藏的，不要猜测或泄露具体隐藏数据。
                    4. 可以比 baseline 更自然，但不能偏离评测事实。
                    """,
                    "请基于以下上下文生成 JSON：" + objectMapper.writeValueAsString(context)
            );

            AiAnalysisPayload payload = parseAnalysisPayload(content);
            if (payload == null || payload.reportMarkdown == null || payload.reportMarkdown.isBlank()) {
                log.warn("AI submission analysis returned no structured markdown payload. submissionId={}", submission.getId());
                String markdownFallback = cleanupAiText(content);
                if (!markdownFallback.isBlank()) {
                    return SubmissionAnalysisResponse.builder()
                            .submissionId(fallback.getSubmissionId())
                            .sourceType(AI_SOURCE)
                            .scenario(fallback.getScenario())
                            .headline(fallback.getHeadline())
                            .summary(fallback.getSummary())
                            .focusPoints(cleanList(fallback.getFocusPoints(), List.of()))
                            .fixDirections(cleanList(fallback.getFixDirections(), List.of()))
                            .wrongSolution(fallback.getWrongSolution())
                            .correctSolution(fallback.getCorrectSolution())
                            .lineIssues(fallback.getLineIssues() == null ? List.of() : fallback.getLineIssues())
                            .firstFailedCase(fallback.getFirstFailedCase())
                            .reportMarkdown(markdownFallback)
                            .generatedAt(fallback.getGeneratedAt())
                            .build();
                }
                return fallback;
            }

            return SubmissionAnalysisResponse.builder()
                    .submissionId(fallback.getSubmissionId())
                    .sourceType(AI_SOURCE)
                    .scenario(fallback.getScenario())
                    .headline(defaultIfBlank(payload.headline, fallback.getHeadline()))
                    .summary(defaultIfBlank(payload.summary, fallback.getSummary()))
                    .focusPoints(cleanList(payload.focusPoints, fallback.getFocusPoints()))
                    .fixDirections(cleanList(payload.fixDirections, fallback.getFixDirections()))
                    .wrongSolution(defaultNullable(payload.wrongSolution, fallback.getWrongSolution()))
                    .correctSolution(defaultNullable(payload.correctSolution, fallback.getCorrectSolution()))
                    .lineIssues(aiCodeAssistSupport.resolveLineIssues(
                            toLineIssueCandidates(payload),
                            payload == null ? null : payload.reportMarkdown,
                            content,
                            submission.getSourceCode(),
                            fallback.getLineIssues()
                    ))
                    .firstFailedCase(fallback.getFirstFailedCase())
                    .reportMarkdown(cleanupAiText(payload.reportMarkdown))
                    .generatedAt(fallback.getGeneratedAt())
                    .build();
        } catch (Exception exception) {
            log.error("AI submission analysis enhancement failed. submissionId={}", submission.getId(), exception);
            return fallback;
        }
    }

    public String enhanceGrowthReportMarkdown(Problem problem,
                                              List<Map<String, Object>> submissionTimeline,
                                              String fallbackMarkdown) {
        if (!canCallAi()) {
            log.info("AI growth report skipped because AI access is unavailable. problemId={}", problem.getId());
            return fallbackMarkdown;
        }

        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("problemTitle", problem.getTitle());
            context.put("problemDescription", problem.getDescription());
            context.put("aiPromptDirection", problem.getAiPromptDirection() == null ? "" : problem.getAiPromptDirection());
            context.put("submissionTimeline", submissionTimeline);
            context.put("fallbackMarkdown", fallbackMarkdown);

            String content = chatCompletion(
                    """
                    你是中文 OJ 成长报告助手。
                    请直接输出 Markdown，不要输出额外解释，不要输出 <think>、思考过程、XML 标签或草稿。

                    报告需要包含：
                    1. 总览
                    2. 错误复盘
                    3. 优化历程
                    4. 学习总结

                    内容要结合提交轨迹，语言简洁、自然、可执行。
                    """,
                    "请基于以下上下文生成成长报告 Markdown：" + objectMapper.writeValueAsString(context)
            );

            String markdown = cleanupAiText(content);
            return markdown.isBlank() ? fallbackMarkdown : markdown;
        } catch (Exception exception) {
            log.error("AI growth report generation failed. problemId={}", problem.getId(), exception);
            return fallbackMarkdown;
        }
    }

    private boolean canCallAi() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    private String chatCompletion(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2,
                "stream", false
        );
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        log.info("Calling AI chat completion. model={}, timeoutSeconds={}, endpoint={}",
                model,
                Math.max(timeoutSeconds, 5),
                endpoint);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 5)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("AI chat completion returned non-success status. status={}, bodyPreview={}",
                    response.statusCode(),
                    previewBody(response.body()));
            throw new IOException("AI API returned status " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode messageContent = root.path("choices").path(0).path("message").path("content");
        if (messageContent.isMissingNode() || messageContent.isNull()) {
            throw new IOException("AI response did not include message content");
        }

        if (messageContent.isTextual()) {
            return messageContent.asText();
        }

        if (messageContent.isArray()) {
            StringBuilder content = new StringBuilder();
            for (JsonNode node : messageContent) {
                if (node.hasNonNull("text")) {
                    content.append(node.get("text").asText());
                }
            }
            return content.toString();
        }

        return messageContent.toString();
    }

    private AiAnalysisPayload parseAnalysisPayload(String rawContent) {
        String normalized = cleanupAiText(rawContent);
        try {
            return objectMapper.readValue(normalized, AiAnalysisPayload.class);
        } catch (JsonProcessingException firstError) {
            int start = normalized.indexOf('{');
            int end = normalized.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readValue(normalized.substring(start, end + 1), AiAnalysisPayload.class);
                } catch (JsonProcessingException ignored) {
                    log.warn("AI analysis payload parsing failed. error={}, contentPreview={}",
                            ignored.getMessage(),
                            previewBody(normalized));
                }
            }
            return null;
        }
    }

    private String previewBody(String body) {
        String normalized = body == null ? "" : body.replace("\r", "").replace("\n", "\\n").trim();
        if (normalized.length() <= 320) {
            return normalized;
        }
        return normalized.substring(0, 317) + "...";
    }

    private List<AiCodeAssistSupport.LineIssueCandidate> toLineIssueCandidates(AiAnalysisPayload payload) {
        if (payload == null || payload.lineIssues == null || payload.lineIssues.isEmpty()) {
            return List.of();
        }

        return payload.lineIssues.stream()
                .filter(item -> item != null)
                .map(item -> new AiCodeAssistSupport.LineIssueCandidate(item.lineNumber, item.error, item.suggestion))
                .toList();
    }

    private List<String> cleanList(List<String> candidate, List<String> fallback) {
        List<String> source = candidate == null || candidate.isEmpty() ? fallback : candidate;
        Set<String> unique = new LinkedHashSet<>();
        for (String item : source) {
            if (item == null) {
                continue;
            }
            String normalized = cleanupAiText(item);
            if (!normalized.isBlank()) {
                unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    private String truncateSourceCode(String sourceCode) {
        if (sourceCode == null) {
            return "";
        }
        if (sourceCode.length() <= MAX_SOURCE_CODE_LENGTH) {
            return sourceCode;
        }
        return sourceCode.substring(0, MAX_SOURCE_CODE_LENGTH) + "\n// ... truncated ...";
    }

    private String addLineNumbers(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "";
        }

        String normalized = sourceCode.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(index + 1)
                    .append(": ")
                    .append(lines[index]);
        }
        return builder.toString();
    }

    private List<SubmissionAnalysisResponse.LineIssue> cleanLineIssues(List<AiLineIssuePayload> issues,
                                                                      String sourceCode,
                                                                      List<SubmissionAnalysisResponse.LineIssue> fallback) {
        int maxLineNumber = countSourceLines(sourceCode);
        if (issues == null || issues.isEmpty() || maxLineNumber == 0) {
            return fallback == null ? List.of() : fallback;
        }

        List<SubmissionAnalysisResponse.LineIssue> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AiLineIssuePayload issue : issues) {
            if (issue == null) {
                continue;
            }

            Integer lineNumber = normalizeLineNumber(issue.lineNumber, maxLineNumber);
            String error = cleanupAiText(issue.error);
            String suggestion = cleanupAiText(issue.suggestion);
            if (lineNumber == null || error.isBlank() || suggestion.isBlank()) {
                continue;
            }

            String dedupeKey = lineNumber + "|" + error + "|" + suggestion;
            if (!seen.add(dedupeKey)) {
                continue;
            }

            normalized.add(SubmissionAnalysisResponse.LineIssue.builder()
                    .lineNumber(lineNumber)
                    .error(error)
                    .suggestion(suggestion)
                    .build());
        }

        normalized.sort(Comparator.comparing(SubmissionAnalysisResponse.LineIssue::getLineNumber));
        return normalized;
    }

    private List<SubmissionAnalysisResponse.LineIssue> resolveLineIssues(AiAnalysisPayload payload,
                                                                         String rawContent,
                                                                         String sourceCode,
                                                                         SubmissionAnalysisResponse fallback) {
        List<SubmissionAnalysisResponse.LineIssue> fallbackIssues = fallback.getLineIssues() == null ? List.of() : fallback.getLineIssues();
        List<SubmissionAnalysisResponse.LineIssue> direct = cleanLineIssues(
                payload == null ? null : payload.lineIssues,
                sourceCode,
                List.of()
        );
        if (!direct.isEmpty()) {
            return direct;
        }

        List<AiLineIssuePayload> parsedFromMarkdown = parseLineIssuesFromText(payload == null ? null : payload.reportMarkdown);
        List<SubmissionAnalysisResponse.LineIssue> fromMarkdown = cleanLineIssues(parsedFromMarkdown, sourceCode, List.of());
        if (!fromMarkdown.isEmpty()) {
            return fromMarkdown;
        }

        List<AiLineIssuePayload> parsedFromRaw = parseLineIssuesFromText(rawContent);
        List<SubmissionAnalysisResponse.LineIssue> fromRaw = cleanLineIssues(parsedFromRaw, sourceCode, List.of());
        if (!fromRaw.isEmpty()) {
            return fromRaw;
        }

        return fallbackIssues;
    }

    private Integer normalizeLineNumber(Integer lineNumber, int maxLineNumber) {
        if (lineNumber == null || lineNumber < 1 || lineNumber > maxLineNumber) {
            return null;
        }
        return lineNumber;
    }

    private int countSourceLines(String sourceCode) {
        if (sourceCode == null) {
            return 0;
        }
        return sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).length;
    }

    private String cleanupAiText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = stripReasoningBlocks(text.trim());
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        normalized = normalized.replace("<think>", "")
                .replace("</think>", "")
                .trim();
        return normalized;
    }

    private String stripReasoningBlocks(String text) {
        String normalized = text;
        normalized = normalized.replaceAll("(?is)<think>.*?</think>", "");
        normalized = normalized.replaceAll("(?is)```(?:thinking|thought|analysis)[^\\n]*\\n.*?```", "");
        normalized = normalized.replaceAll("(?is)^思考过程[:：].*?(?=\\n#|\\n##|\\n\\{|\\Z)", "");
        return normalized.trim();
    }

    private String extractLineNumberedBlock(String text) {
        Matcher matcher = NUMBERED_LINE_PATTERN.matcher(text == null ? "" : text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(matcher.group(1)).append(": ").append(matcher.group(2));
        }
        return builder.toString();
    }

    private List<AiLineIssuePayload> parseLineIssuesFromText(String text) {
        String normalized = cleanupAiText(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<AiLineIssuePayload> issues = new ArrayList<>();
        Matcher matcher = REPORT_LINE_ISSUE_PATTERN.matcher(normalized);
        while (matcher.find()) {
            AiLineIssuePayload issue = new AiLineIssuePayload();
            issue.lineNumber = Integer.valueOf(matcher.group(1));
            issue.error = cleanupAiText(matcher.group(2));
            issue.suggestion = cleanupAiText(matcher.group(3));
            issues.add(issue);
        }
        return issues;
    }

    private String defaultIfBlank(String candidate, String fallback) {
        String normalized = cleanupAiText(candidate);
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized;
    }

    private String defaultNullable(String candidate, String fallback) {
        String normalized = cleanupAiText(candidate);
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AiAnalysisPayload {
        public String headline;
        public String summary;
        public List<String> focusPoints;
        public List<String> fixDirections;
        public String wrongSolution;
        public String correctSolution;
        public List<AiLineIssuePayload> lineIssues;
        public String reportMarkdown;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AiLineIssuePayload {
        public Integer lineNumber;
        public String error;
        public String suggestion;
    }
}

