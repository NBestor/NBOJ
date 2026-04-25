package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class AiCodeAssistSupport {

    private static final Pattern COMPILER_LINE_PATTERN = Pattern.compile(
            "(?im)^([^\\n:]+\\.(?:java|c|cc|cpp|cxx|h|hpp)):(\\d+)(?::\\d+)?:\\s*(?:fatal\\s+)?(?:error|warning):\\s*(.+)$"
    );
    private static final Pattern PYTHON_FILE_LINE_PATTERN = Pattern.compile(
            "(?im)^\\s*File\\s+\"[^\"]+\",\\s+line\\s+(\\d+)(?:,\\s+in\\s+.+)?$"
    );
    private static final Pattern PYTHON_ERROR_LINE_PATTERN = Pattern.compile(
            "(?im)^\\s*(SyntaxError|IndentationError|TabError|NameError|TypeError|ValueError|IndexError|KeyError|AttributeError|ZeroDivisionError):\\s*(.+)$"
    );
    private static final Pattern JAVA_STACK_LINE_PATTERN = Pattern.compile(
            "(?im)\\((?:Main|solution)\\.java:(\\d+)\\)"
    );
    private static final Pattern GENERIC_LINE_PATTERN = Pattern.compile("(?im)\\bline\\s+(\\d+)\\b");
    private static final Pattern LINE_REFERENCE_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:[-*]\\s*)?(?:行号|行|line|l)\\s*[:：#-]?\\s*(\\d+)\\b(.*)$"
    );
    private static final Pattern ORDINAL_LINE_REFERENCE_PATTERN = Pattern.compile(
            "^\\s*(?:[-*]\\s*)?第\\s*(\\d+)\\s*行\\b(.*)$"
    );
    private static final Pattern ERROR_LABEL_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:错误|问题|error|issue)\\s*[:：-]?\\s*(.+)$"
    );
    private static final Pattern SUGGESTION_LABEL_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:建议|修复|修改建议|suggestion|fix)\\s*[:：-]?\\s*(.+)$"
    );

    public String addLineNumbers(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "";
        }

        String normalized = normalizeLineEndings(sourceCode);
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

    public List<SubmissionAnalysisResponse.LineIssue> resolveLineIssues(List<LineIssueCandidate> directIssues,
                                                                        String reportMarkdown,
                                                                        String rawContent,
                                                                        String sourceCode,
                                                                        List<SubmissionAnalysisResponse.LineIssue> fallback) {
        List<SubmissionAnalysisResponse.LineIssue> fallbackIssues = fallback == null ? List.of() : fallback;

        List<SubmissionAnalysisResponse.LineIssue> direct = normalizeLineIssues(directIssues, sourceCode);
        if (!direct.isEmpty()) {
            log.info("AI code assist resolved {} line issues from direct structured payload.", direct.size());
            return direct;
        }

        List<SubmissionAnalysisResponse.LineIssue> fromMarkdown = normalizeLineIssues(parseLineIssuesFromText(reportMarkdown), sourceCode);
        if (!fromMarkdown.isEmpty()) {
            log.info("AI code assist resolved {} line issues from report markdown fallback.", fromMarkdown.size());
            return fromMarkdown;
        }

        List<SubmissionAnalysisResponse.LineIssue> fromRaw = normalizeLineIssues(parseLineIssuesFromText(rawContent), sourceCode);
        if (!fromRaw.isEmpty()) {
            log.info("AI code assist resolved {} line issues from raw response fallback.", fromRaw.size());
            return fromRaw;
        }

        log.info("AI code assist fell back to {} precomputed line issues.", fallbackIssues.size());
        return fallbackIssues;
    }

    public List<SubmissionAnalysisResponse.LineIssue> extractCompilerLineIssues(String compileOutput,
                                                                                String sourceCode) {
        List<SubmissionAnalysisResponse.LineIssue> issues = normalizeLineIssues(
                parseCompilerLineIssues(compileOutput),
                sourceCode
        );
        if (!issues.isEmpty()) {
            log.info("AI code assist extracted {} line issues from compiler diagnostics.", issues.size());
        }
        return issues;
    }

    public List<SubmissionAnalysisResponse.LineIssue> extractRuntimeLineIssues(String runtimeOutput,
                                                                               String sourceCode) {
        List<SubmissionAnalysisResponse.LineIssue> issues = normalizeLineIssues(
                parseRuntimeLineIssues(runtimeOutput),
                sourceCode
        );
        if (!issues.isEmpty()) {
            log.info("AI code assist extracted {} line issues from runtime diagnostics.", issues.size());
        }
        return issues;
    }

    private List<SubmissionAnalysisResponse.LineIssue> normalizeLineIssues(List<LineIssueCandidate> issues,
                                                                           String sourceCode) {
        int maxLineNumber = countSourceLines(sourceCode);
        if (issues == null || issues.isEmpty() || maxLineNumber == 0) {
            return List.of();
        }

        List<SubmissionAnalysisResponse.LineIssue> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (LineIssueCandidate issue : issues) {
            if (issue == null) {
                continue;
            }

            Integer lineNumber = normalizeLineNumber(issue.lineNumber(), maxLineNumber);
            String error = sanitizeText(issue.error());
            String suggestion = sanitizeText(issue.suggestion());
            if (suggestion.isBlank()) {
                suggestion = buildSuggestionForDiagnostic(error, false);
            }
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

    private List<LineIssueCandidate> parseLineIssuesFromText(String text) {
        String normalized = sanitizeText(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<LineIssueCandidate> issues = new ArrayList<>();
        String[] lines = normalizeLineEndings(normalized).split("\n", -1);

        Integer currentLineNumber = null;
        List<String> currentBlock = new ArrayList<>();
        for (String rawLine : lines) {
            String line = sanitizeText(rawLine);
            if (line.isBlank()) {
                if (currentLineNumber != null) {
                    currentBlock.add("");
                }
                continue;
            }

            LineReference reference = parseLineReference(line);
            if (reference != null) {
                flushLineIssueBlock(issues, currentLineNumber, currentBlock);
                currentLineNumber = reference.lineNumber();
                currentBlock = new ArrayList<>();
                if (!reference.remainder().isBlank()) {
                    currentBlock.add(reference.remainder());
                }
                continue;
            }

            if (currentLineNumber != null) {
                currentBlock.add(line);
            }
        }
        flushLineIssueBlock(issues, currentLineNumber, currentBlock);

        if (!issues.isEmpty()) {
            log.info("AI code assist parsed {} candidate line issues from plain text.", issues.size());
        }
        return issues;
    }

    private LineReference parseLineReference(String line) {
        Matcher directMatcher = LINE_REFERENCE_PATTERN.matcher(line);
        if (directMatcher.matches()) {
            return new LineReference(
                    parseInteger(directMatcher.group(1)),
                    cleanupLineReferenceRemainder(directMatcher.group(2))
            );
        }

        Matcher ordinalMatcher = ORDINAL_LINE_REFERENCE_PATTERN.matcher(line);
        if (ordinalMatcher.matches()) {
            return new LineReference(
                    parseInteger(ordinalMatcher.group(1)),
                    cleanupLineReferenceRemainder(ordinalMatcher.group(2))
            );
        }

        return null;
    }

    private String cleanupLineReferenceRemainder(String value) {
        return sanitizeText(value)
                .replaceFirst("^[：:，,;；\\-\\s]+", "")
                .trim();
    }

    private void flushLineIssueBlock(List<LineIssueCandidate> issues,
                                     Integer lineNumber,
                                     List<String> blockLines) {
        if (lineNumber == null || blockLines == null || blockLines.isEmpty()) {
            return;
        }

        String blockText = sanitizeText(String.join("\n", blockLines));
        String error = extractLabeledValue(blockText, ERROR_LABEL_PATTERN);
        String suggestion = extractLabeledValue(blockText, SUGGESTION_LABEL_PATTERN);

        if (error.isBlank()) {
            error = extractFallbackError(blockLines);
        }
        if (error.isBlank()) {
            return;
        }
        if (suggestion.isBlank()) {
            suggestion = buildSuggestionForDiagnostic(error, false);
        }

        issues.add(new LineIssueCandidate(lineNumber, error, suggestion));
    }

    private String extractLabeledValue(String blockText, Pattern pattern) {
        Matcher matcher = pattern.matcher(blockText);
        if (!matcher.find()) {
            return "";
        }
        return sanitizeText(matcher.group(1));
    }

    private String extractFallbackError(List<String> blockLines) {
        for (String line : blockLines) {
            String normalized = sanitizeText(line);
            if (normalized.isBlank()) {
                continue;
            }
            if (SUGGESTION_LABEL_PATTERN.matcher(normalized).find()) {
                continue;
            }

            String withoutLabel = normalized
                    .replaceFirst("(?i)^(?:错误|问题|error|issue)\\s*[:：-]?\\s*", "")
                    .trim();
            if (!withoutLabel.isBlank()) {
                return withoutLabel;
            }
        }
        return "";
    }

    private List<LineIssueCandidate> parseCompilerLineIssues(String text) {
        String normalized = sanitizeText(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<LineIssueCandidate> issues = new ArrayList<>();
        Matcher matcher = COMPILER_LINE_PATTERN.matcher(normalized);
        while (matcher.find()) {
            Integer lineNumber = parseInteger(matcher.group(2));
            String error = sanitizeText(matcher.group(3));
            issues.add(new LineIssueCandidate(
                    lineNumber,
                    error,
                    buildSuggestionForDiagnostic(error, true)
            ));
        }
        return issues;
    }

    private List<LineIssueCandidate> parseRuntimeLineIssues(String text) {
        String normalized = sanitizeText(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<LineIssueCandidate> issues = new ArrayList<>();
        String[] lines = normalized.split("\n");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];

            Matcher pythonFileMatcher = PYTHON_FILE_LINE_PATTERN.matcher(line);
            if (pythonFileMatcher.find()) {
                Integer lineNumber = parseInteger(pythonFileMatcher.group(1));
                String error = findNearbyPythonError(lines, index + 1);
                issues.add(new LineIssueCandidate(
                        lineNumber,
                        error,
                        buildSuggestionForDiagnostic(error, false)
                ));
                continue;
            }

            Matcher javaStackMatcher = JAVA_STACK_LINE_PATTERN.matcher(line);
            if (javaStackMatcher.find()) {
                Integer lineNumber = parseInteger(javaStackMatcher.group(1));
                String error = sanitizeText(line);
                issues.add(new LineIssueCandidate(
                        lineNumber,
                        error,
                        buildSuggestionForDiagnostic(error, false)
                ));
                continue;
            }

            Matcher genericLineMatcher = GENERIC_LINE_PATTERN.matcher(line);
            if (genericLineMatcher.find()) {
                Integer lineNumber = parseInteger(genericLineMatcher.group(1));
                String error = sanitizeText(line);
                issues.add(new LineIssueCandidate(
                        lineNumber,
                        error,
                        buildSuggestionForDiagnostic(error, false)
                ));
            }
        }
        return issues;
    }

    private Integer normalizeLineNumber(Integer lineNumber, int maxLineNumber) {
        if (lineNumber == null || lineNumber < 1 || lineNumber > maxLineNumber) {
            return null;
        }
        return lineNumber;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int countSourceLines(String sourceCode) {
        if (sourceCode == null) {
            return 0;
        }
        return normalizeLineEndings(sourceCode).split("\n", -1).length;
    }

    private String normalizeLineEndings(String text) {
        return String.valueOf(text).replace("\r\n", "\n").replace('\r', '\n');
    }

    private String sanitizeText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.trim();
        normalized = normalized.replaceAll("(?is)<think>.*?</think>", "");
        normalized = normalized.replaceAll("(?is)```(?:thinking|thought|analysis)[^\\n]*\\n.*?```", "");
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized
                .replace("<think>", "")
                .replace("</think>", "")
                .trim();
    }

    private String findNearbyPythonError(String[] lines, int startIndex) {
        for (int index = startIndex; index < lines.length && index < startIndex + 4; index++) {
            Matcher matcher = PYTHON_ERROR_LINE_PATTERN.matcher(lines[index]);
            if (matcher.find()) {
                return sanitizeText(matcher.group(1) + ": " + matcher.group(2));
            }
        }
        return "运行时报错";
    }

    private String buildSuggestionForDiagnostic(String error, boolean compilePhase) {
        String normalized = sanitizeText(error).toLowerCase();
        if (normalized.contains("cannot find symbol")
                || normalized.contains("not declared")
                || normalized.contains("undeclared")
                || normalized.contains("nameerror")) {
            return "检查变量、函数或类名是否拼写正确，并先声明后使用。";
        }
        if (normalized.contains("expected")
                || normalized.contains("syntaxerror")
                || normalized.contains("indentationerror")
                || normalized.contains("taberror")) {
            return "检查这一行附近的语法结构，补全缺失的括号、分号、冒号或缩进。";
        }
        if (normalized.contains("incompatible types")
                || normalized.contains("cannot be converted")
                || normalized.contains("typeerror")) {
            return "检查这一行参与运算或赋值的类型是否一致。";
        }
        if (normalized.contains("missing return")) {
            return "确认所有分支都返回了符合要求的值。";
        }
        if (normalized.contains("no suitable method")
                || normalized.contains("argument")) {
            return "核对方法名、参数数量和参数类型是否与定义一致。";
        }
        if (normalized.contains("outofbounds")
                || normalized.contains("indexerror")) {
            return "检查下标或索引边界，避免访问越界。";
        }
        if (normalized.contains("nullpointerexception")
                || normalized.contains("attributeerror")) {
            return "在访问对象属性或方法前先确认对象已经正确初始化。";
        }
        return compilePhase
                ? "先修复这一行及其附近的编译问题，再重新提交。"
                : "检查这一行及其附近的逻辑和边界处理，再重新运行。";
    }

    private record LineReference(Integer lineNumber, String remainder) {
    }

    public record LineIssueCandidate(Integer lineNumber, String error, String suggestion) {
    }
}
