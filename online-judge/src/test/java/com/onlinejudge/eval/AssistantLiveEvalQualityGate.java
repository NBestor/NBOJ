package com.onlinejudge.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class AssistantLiveEvalQualityGate {

    private static final Pattern NUMERIC_ARRAY = Pattern.compile("\\[[0-9]+(?:\\s*,\\s*[0-9]+)+\\]");
    private static final Pattern LOOSE_NUMERIC_SEQUENCE = Pattern.compile("(?<!\\d)(\\d+)(?:\\s+\\d+){2,}(?!\\d)");
    private static final Pattern CPP_CAST_FIX = Pattern.compile("(?i)(\\(double\\)\\s*[a-z_][a-z0-9_]*|1\\.0\\s*\\*\\s*[a-z_][a-z0-9_]*)");
    private static final Pattern DP_FORMULA_HINT = Pattern.compile("(?i)(dp\\s*\\[|skip_current|take_current|prev\\d*\\s*=)");

    private AssistantLiveEvalQualityGate() {
    }

    static List<String> evaluate(AssistantLiveEvalReport report, Thresholds thresholds) {
        List<String> violations = new ArrayList<>();
        if (report == null || report.getTotalCount() == null || report.getTotalCount() <= 0) {
            violations.add("report is empty");
            return violations;
        }

        int total = report.getTotalCount();
        double signalHitRate = rate(report.getExpectedSignalHitCount(), total);
        double evidenceValidRate = rate(report.getEvidenceValidCount(), total);
        double safetyPassRate = rate(total - nullToZero(report.getSafetyFailureCount()), total);
        double fallbackRate = rate(report.getRuntimeFailureCount(), total);
        double teachingActionValidRate = rate(validTeachingActionCount(report), total);
        int completed = nullToZero(report.getCompletedCount());
        double studentVisibleQualityPassRate = completed == 0
                ? 1.0
                : rate(report.getStudentVisibleQualityPassCount(), completed);
        long averageCompletedLatencyMs = averageCompletedLatencyMs(report);

        if (signalHitRate < thresholds.minSignalHitRate()) {
            violations.add("signalHitRate " + format(signalHitRate)
                    + " < " + format(thresholds.minSignalHitRate()));
        }
        if (evidenceValidRate < thresholds.minEvidenceValidRate()) {
            violations.add("evidenceValidRate " + format(evidenceValidRate)
                    + " < " + format(thresholds.minEvidenceValidRate()));
        }
        if (safetyPassRate < thresholds.minSafetyPassRate()) {
            violations.add("safetyPassRate " + format(safetyPassRate)
                    + " < " + format(thresholds.minSafetyPassRate()));
        }
        if (fallbackRate > thresholds.maxFallbackRate()) {
            violations.add("fallbackRate " + format(fallbackRate)
                    + " > " + format(thresholds.maxFallbackRate()));
        }
        if (teachingActionValidRate < thresholds.minTeachingActionValidRate()) {
            violations.add("teachingActionValidRate " + format(teachingActionValidRate)
                    + " < " + format(thresholds.minTeachingActionValidRate()));
        }
        if (studentVisibleQualityPassRate < thresholds.minStudentVisibleQualityPassRate()) {
            violations.add("studentVisibleQualityPassRate " + format(studentVisibleQualityPassRate)
                    + " < " + format(thresholds.minStudentVisibleQualityPassRate()));
        }
        if (completed > 0 && averageCompletedLatencyMs > thresholds.maxAverageCompletedLatencyMs()) {
            violations.add("averageCompletedLatencyMs " + averageCompletedLatencyMs
                    + " > " + thresholds.maxAverageCompletedLatencyMs());
        }
        return violations;
    }

    static List<String> studentVisibleQualityFlags(String basicText,
                                                   String improvementText,
                                                   String nextActionText) {
        return studentVisibleQualityFlags(basicText, improvementText, nextActionText, List.of());
    }

    static List<String> studentVisibleQualityFlags(String basicText,
                                                   String improvementText,
                                                   String nextActionText,
                                                   List<String> visibleInputSnapshots) {
        List<String> flags = new ArrayList<>();
        String basic = safe(basicText);
        String improvement = safe(improvementText);
        String allText = String.join("\n", List.of(basic, improvement, safe(nextActionText)).stream()
                .filter(text -> !text.isBlank())
                .toList());
        String compact = allText.replaceAll("\\s+", "");
        if (allText.isBlank()) {
            flags.add("EMPTY_VISIBLE_TEXT");
            return flags;
        }
        if (containsAny(allText, List.of(
                "Use candidate signals",
                "evidenceRefs",
                "teacherNote",
                "外部模型已生成",
                "AI 完整诊断",
                "MODEL_",
                "DIAGNOSIS_",
                "verdict:",
                "code:",
                "evidenceRefs",
                "judgeFacts",
                "candidateSignals"
        ))) {
            flags.add("INTERNAL_TRACE");
        }
        if (containsAny(compact, List.of(
                "直接改成",
                "替换为",
                "删除这行",
                "加上这行",
                "把代码改成",
                "把读取",
                "放进循环",
                "设为0",
                "设回0",
                "从0开始",
                "循环内部初始化",
                "每轮循环开始时显式清空",
                "显式清空全局状态",
                "改用",
                "完整代码",
                "参考代码",
                "两状态",
                "两个状态",
                "两种情况",
                "选或不选",
                "不选当前",
                "选当前",
                "正确最少只需",
                "用两个",
                "只比较了字符串的第一个和最后一个字符",
                "只验证了字符串首尾字符是否相同",
                "首尾相同但中间内容不同",
                "首尾相同但中间不同",
                "中间字符是否也被纳入比较范围",
                "遗漏了对内部字符",
                "定义状态表示",
                "从小到大枚举",
                "进行转移",
                "多一个维度",
                "多一维",
                "滚动更新",
                "空间优化",
                "空间压缩",
                "前驱状态"
        ))) {
            flags.add("DIRECT_FIX");
        }
        if (compact.matches(".*选第\\d+[、,，]\\d+.*")) {
            flags.add("DIRECT_FIX");
        }
        if (DP_FORMULA_HINT.matcher(allText).find()) {
            flags.add("DIRECT_FIX");
        }
        if (compact.matches(".*(第\\d+行|第[一二三四五六七八九十]+行).*(添加|删除|替换|改成|设为).*")) {
            flags.add("DIRECT_FIX");
        }
        if (allText.length() > 760) {
            flags.add("TOO_LONG_VISIBLE_TEXT");
        }
        if (improvement.isBlank() || sameCompact(basic, improvement)) {
            flags.add("WEAK_IMPROVEMENT");
        }
        if (safe(nextActionText).replaceAll("\\s+", "").equals("你能用一个小样例验证这条判断吗？")) {
            flags.add("GENERIC_NEXT_ACTION");
        }
        if (isMultiAction(nextActionText)) {
            flags.add("MULTI_STEP_NEXT_ACTION");
        }
        if (containsUnsupportedNumericArray(allText, visibleInputSnapshots)
                || containsUnsupportedNumericSequence(allText, visibleInputSnapshots)) {
            flags.add("INVENTED_NUMERIC_EXAMPLE");
        }
        if (CPP_CAST_FIX.matcher(allText).find()) {
            flags.add("DIRECT_TYPE_CAST_FIX");
        }
        return flags;
    }

    private static boolean isMultiAction(String nextActionText) {
        String text = safe(nextActionText).replaceAll("\\s+", "");
        return text.matches(".*(1[.、．]).*(2[.、．]).*")
                || text.matches(".*(第一步|第二步|第三步).*")
                || text.chars().filter(ch -> ch == '；' || ch == ';').count() >= 2;
    }

    private static boolean containsUnsupportedNumericArray(String text, List<String> visibleInputSnapshots) {
        Matcher matcher = NUMERIC_ARRAY.matcher(safe(text));
        while (matcher.find()) {
            if (!visibleInputContainsArray(matcher.group(), visibleInputSnapshots)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visibleInputContainsArray(String arrayText, List<String> visibleInputSnapshots) {
        String normalizedArray = normalizeNumbers(arrayText);
        if (normalizedArray.isBlank()) {
            return false;
        }
        return visibleInputSnapshots != null && visibleInputSnapshots.stream()
                .map(AssistantLiveEvalQualityGate::normalizeNumbers)
                .anyMatch(input -> !input.isBlank() && input.contains(normalizedArray));
    }

    private static boolean containsUnsupportedNumericSequence(String text, List<String> visibleInputSnapshots) {
        Matcher matcher = LOOSE_NUMERIC_SEQUENCE.matcher(safe(text));
        while (matcher.find()) {
            if (!visibleInputContainsArray(matcher.group(), visibleInputSnapshots)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeNumbers(String value) {
        return safe(value).replaceAll("[^0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static int validTeachingActionCount(AssistantLiveEvalReport report) {
        if (report == null || report.getEntries() == null || report.getEntries().isEmpty()) {
            return report == null ? 0 : nullToZero(report.getTotalCount());
        }
        return (int) report.getEntries().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getTeachingActionValid()))
                .count();
    }

    private static long averageCompletedLatencyMs(AssistantLiveEvalReport report) {
        if (report == null || report.getEntries() == null || report.getEntries().isEmpty()) {
            return 0L;
        }
        return Math.round(report.getEntries().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getCompletedOutput()))
                .map(AssistantLiveEvalReport.Entry::getLatencyMs)
                .filter(value -> value != null && value >= 0)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0));
    }

    private static double rate(Integer numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return nullToZero(numerator) / (double) denominator;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static boolean containsAny(String actual, List<String> expected) {
        if (actual == null || expected == null || expected.isEmpty()) {
            return false;
        }
        return expected.stream().anyMatch(actual::contains);
    }

    private static boolean sameCompact(String left, String right) {
        String l = safe(left).replaceAll("\\s+", "");
        String r = safe(right).replaceAll("\\s+", "");
        return !l.isBlank() && l.equals(r);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    record Thresholds(double minSignalHitRate,
                      double minEvidenceValidRate,
                      double minSafetyPassRate,
                      double maxFallbackRate,
                      double minTeachingActionValidRate,
                      double minStudentVisibleQualityPassRate,
                      long maxAverageCompletedLatencyMs) {
    }
}
