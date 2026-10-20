package com.onlinejudge.submission.application;

import java.util.List;
import java.util.Locale;

final class ModelOutputSafetyPolicy {

    private static final List<String> UNSAFE_LEAK_MARKERS = List.of(
            "完整代码",
            "参考代码",
            "最终答案",
            "参考答案",
            "答案如下",
            "直接改成",
            "替换为",
            "把读取",
            "放进循环",
            "设为0",
            "设回0",
            "设回 0",
            "循环内部初始化",
            "每轮循环开始时显式清空",
            "显式清空全局状态",
            "正确最少只需",
            "只比较了字符串的第一个和最后一个字符",
            "只验证了字符串首尾字符是否相同",
            "首尾相同但中间内容不同",
            "首尾相同但中间不同",
            "中间字符是否也被纳入比较范围",
            "遗漏了对内部字符",
            "hidden test",
            "for _ in range",
            "while q",
            "while used < steps",
            "while used<steps",
            "while nums",
            "range(1, n + 1)",
            "range(1,n+1)",
            "sqrt",
            "dp[i - 2] +",
            "dp[i-2]+",
            "dp[i-1]",
            "dp[i - 1]",
            "skip_current",
            "take_current",
            "前前位置的最大和",
            "前前位置最大和",
            "```",
            "def ",
            "#include",
            "int main"
    );

    private ModelOutputSafetyPolicy() {
    }

    static String normalizeRisk(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return rawValue;
        }
        return rawValue.trim().toUpperCase(Locale.ROOT);
    }

    static boolean isHighRisk(String risk) {
        return "HIGH".equalsIgnoreCase(risk == null ? "" : risk.trim());
    }

    static String calibrateModelReportedRisk(String rawRisk, String... visibleTexts) {
        String normalized = normalizeRisk(rawRisk);
        if (isHighRisk(normalized) && !containsUnsafeLeak(visibleTexts)) {
            return "MEDIUM";
        }
        return normalized;
    }

    static boolean containsUnsafeLeak(String... texts) {
        if (texts == null || texts.length == 0) {
            return false;
        }
        for (String text : texts) {
            if (!unsafeLeakTrigger(text).isBlank()) {
                return true;
            }
        }
        return false;
    }

    static String unsafeLeakTrigger(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String compact = normalized.replaceAll("\\s+", "");
        if (compact.matches(".*第[0-9一二三四五六七八九十]+行.*(添加|删除|替换|改成|改为|设为|放进).*")) {
            return "direct line edit";
        }
        if (compact.matches(".*(把|将).{0,24}(改成|改为|替换为|设为|放进|移到|初始化|清空).*")) {
            return "direct replacement instruction";
        }
        if (compact.matches(".*(用|使用).{0,8}(变量|状态).{0,8}滚动更新.*")) {
            return "direct optimization recipe";
        }
        for (String marker : UNSAFE_LEAK_MARKERS) {
            if (normalized.contains(marker)) {
                return marker;
            }
        }
        return "";
    }
}
