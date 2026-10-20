package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CoachAnswerQualityAnalyzer {

    private static final Pattern NUMBER_PATTERN = Pattern.compile(".*\\d+.*");

    public CoachInteractionSummaryResponse.CoachAnswerQualitySignal analyze(String answer) {
        String normalized = normalize(answer);
        if (normalized.isBlank()) {
            return signal(
                    "NO_ANSWER",
                    "NONE",
                    0.0,
                    false,
                    "NOT_ANSWERED",
                    "COLLECT_EVIDENCE",
                    List.of(),
                    List.of("需要先回答一个可验证想法。"),
                    "学生还没有回答 AI 追问。",
                    "先要求学生补一个最小样例、变量变化或复杂度数量级。",
                    false
            );
        }

        List<String> evidenceTypes = evidenceTypes(normalized);
        if (mentionsAnswerLikeContent(normalized)) {
            return signal(
                    "SAFETY_RISK",
                    "RISKY",
                    evidenceCompleteness(evidenceTypes),
                    false,
                    "SAFETY_RISK",
                    "COLLECT_EVIDENCE",
                    evidenceTypes,
                    List.of("回答疑似包含答案、完整代码或直接改法，需要回到证据描述。"),
                    "学生回答疑似越过证据层，存在把追问变成答案复述的风险。",
                    "要求学生只描述触发问题的输入特征、输出对比或变量现象，不讨论完整改法。",
                    true
            );
        }
        if (isVagueAck(normalized) && evidenceTypes.isEmpty()) {
            return signal(
                    "VAGUE_ACK",
                    "NONE",
                    0.1,
                    false,
                    "NEEDS_EVIDENCE",
                    "COLLECT_EVIDENCE",
                    evidenceTypes,
                    List.of("缺少样例、变量轨迹、输出对照或复杂度估算。"),
                    "学生只是表示知道或准备修改，还没有形成可验证证据。",
                    "继续追问一个最小证据，不进入更高层提示。",
                    true
            );
        }

        if (evidenceTypes.contains("GENERALIZATION")
                && (evidenceTypes.size() >= 2 || containsTransferReadyText(normalized))) {
            return signal(
                    "TRANSFER_READY",
                    "TRANSFER",
                    evidenceCompleteness(evidenceTypes),
                    true,
                    "READY_TO_TRANSFER",
                    "EXPLAIN_GENERALITY",
                    evidenceTypes,
                    List.of(),
                    "学生已经能解释规律、边界或迁移条件，适合进入复盘泛化。",
                    "进入通过后复盘、迁移样例或复杂度解释。",
                    false
            );
        }

        if (evidenceTypes.size() >= 2) {
            return signal(
                    "VERIFICATION_READY",
                    "VERIFICATION",
                    evidenceCompleteness(evidenceTypes),
                    true,
                    "READY_TO_VERIFY",
                    recommendedTeachingAction(evidenceTypes),
                    evidenceTypes,
                    List.of(),
                    "学生回答中包含多类可验证证据，已经可以进入最小修改、提交预测或复盘验证。",
                    "要求学生基于这些证据做一次最小修改，并预测下一次评测现象。",
                    false
            );
        }

        if (!evidenceTypes.isEmpty()) {
            return signal(
                    "EVIDENCE_GROUNDED",
                    "EVIDENCE",
                    evidenceCompleteness(evidenceTypes),
                    true,
                    "READY_TO_VERIFY",
                    recommendedTeachingAction(evidenceTypes),
                    evidenceTypes,
                    List.of(),
                    "学生回答中已经出现可验证证据，可以据此做最小修改或下一次提交验证。",
                    "要求学生基于该证据做一次最小修改，并预测评测现象会如何变化。",
                    false
            );
        }

        return signal(
                "DIRECTION_ONLY",
                "DIRECTION",
                0.25,
                false,
                "NEEDS_EVIDENCE",
                "COLLECT_EVIDENCE",
                List.of(),
                List.of("方向还没有落到可检查证据。"),
                "学生给出了方向，但还没有提供样例、变量轨迹、复杂度估算、反例或提交对比。",
                "要求学生把方向转成一个可验证证据。",
                false
        );
    }

    private CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal(String level,
                                                                            String understandingLevel,
                                                                            double evidenceCompleteness,
                                                                            boolean verifiable,
                                                                            String actionStatus,
                                                                            String recommendedTeachingAction,
                                                                            List<String> evidenceTypes,
                                                                            List<String> missingEvidence,
                                                                            String summary,
                                                                            String nextCoachMove,
                                                                            boolean needsTeacherAttention) {
        return CoachInteractionSummaryResponse.CoachAnswerQualitySignal.builder()
                .qualityLevel(level)
                .qualityLabel(label(level))
                .understandingLevel(understandingLevel)
                .evidenceCompleteness(Math.round(evidenceCompleteness * 100.0) / 100.0)
                .verifiable(verifiable)
                .actionStatus(actionStatus)
                .recommendedTeachingAction(recommendedTeachingAction)
                .evidenceTypes(evidenceTypes)
                .missingEvidence(missingEvidence)
                .summary(summary)
                .nextCoachMove(nextCoachMove)
                .needsTeacherAttention(needsTeacherAttention)
                .build();
    }

    private String label(String level) {
        return switch (level) {
            case "NO_ANSWER" -> "未回答";
            case "VAGUE_ACK" -> "空泛确认";
            case "DIRECTION_ONLY" -> "只有方向";
            case "EVIDENCE_GROUNDED" -> "已有证据";
            case "VERIFICATION_READY" -> "可验证";
            case "TRANSFER_READY" -> "可复盘迁移";
            case "SAFETY_RISK" -> "疑似越界";
            default -> "待判断";
        };
    }

    private List<String> evidenceTypes(String normalized) {
        Set<String> types = new LinkedHashSet<>();
        if (containsAny(normalized, "最小样例", "小样例", "边界样例", "边界输入", "n=", "n =", "输入")
                || NUMBER_PATTERN.matcher(normalized).matches()) {
            types.add("MIN_CASE");
        }
        if (containsAny(normalized, "预期", "实际", "输出", "对比", "expected", "actual")) {
            types.add("EXPECTED_ACTUAL_COMPARE");
        }
        if (containsAny(normalized, "变量", "状态", "取值", "第一次", "最后一次", "trace")
                || (normalized.contains("循环") && containsAny(normalized, "第一次", "最后一次", "取值", "到 2", "到2"))) {
            types.add("VARIABLE_TRACE");
        }
        if (containsAny(normalized, "复杂度", "次数", "数量级", "o(", "超时", "最大")) {
            types.add("COMPLEXITY_ESTIMATE");
        }
        if (containsAny(normalized, "反例", "不成立", "破坏", "样例外", "特殊情况")) {
            types.add("COUNTEREXAMPLE");
        }
        if (containsAny(normalized, "提交", "改动", "diff", "对比两次", "上次", "这次修改")) {
            types.add("SUBMISSION_DIFF");
        }
        if (containsAny(normalized, "规律", "不变量", "泛化", "迁移", "为什么", "证明", "复杂度是")) {
            types.add("GENERALIZATION");
        }
        return new ArrayList<>(types);
    }

    private boolean isVagueAck(String normalized) {
        return normalized.length() <= 30
                && containsAny(normalized, "知道", "懂了", "明白", "我改", "试试", "看看", "好的", "ok", "会了");
    }

    private boolean containsTransferReadyText(String normalized) {
        return containsAny(normalized, "边界样例", "复杂度", "不变量", "泛化", "迁移", "为什么能处理");
    }

    private boolean mentionsAnswerLikeContent(String normalized) {
        return containsAny(normalized,
                "完整代码",
                "参考代码",
                "最终答案",
                "答案如下",
                "直接改成",
                "替换为",
                "#include",
                "int main",
                "def ",
                "class solution",
                "for _ in range",
                "while q");
    }

    private double evidenceCompleteness(List<String> evidenceTypes) {
        int count = evidenceTypes == null ? 0 : evidenceTypes.size();
        if (count <= 0) {
            return 0.0;
        }
        if (count == 1) {
            return 0.45;
        }
        if (count == 2) {
            return 0.75;
        }
        return 1.0;
    }

    private String recommendedTeachingAction(List<String> evidenceTypes) {
        if (evidenceTypes == null || evidenceTypes.isEmpty()) {
            return "COLLECT_EVIDENCE";
        }
        if (evidenceTypes.contains("COMPLEXITY_ESTIMATE")) {
            return "COUNT_COMPLEXITY";
        }
        if (evidenceTypes.contains("EXPECTED_ACTUAL_COMPARE")) {
            return "COMPARE_OUTPUT";
        }
        if (evidenceTypes.contains("VARIABLE_TRACE")) {
            return "TRACE_VARIABLES";
        }
        if (evidenceTypes.contains("COUNTEREXAMPLE")) {
            return "BUILD_COUNTEREXAMPLE";
        }
        if (evidenceTypes.contains("SUBMISSION_DIFF")) {
            return "COMPARE_SUBMISSIONS";
        }
        if (evidenceTypes.contains("GENERALIZATION")) {
            return "EXPLAIN_GENERALITY";
        }
        return "ASK_MIN_CASE";
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
