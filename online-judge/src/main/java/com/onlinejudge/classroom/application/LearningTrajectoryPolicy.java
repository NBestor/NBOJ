package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import org.springframework.stereotype.Component;

@Component
public class LearningTrajectoryPolicy {

    public String nextStep(StudentTrajectoryResponse.LearningTrajectorySignal signal, String fallback) {
        if (signal == null) {
            return fallback;
        }
        String nextFocus = signal.getNextFocus();
        if (nextFocus != null && !nextFocus.isBlank()) {
            return nextFocus;
        }
        return switch (signal.getPhase() == null ? "" : signal.getPhase()) {
            case "REPEATED_STUCK" -> "先停止连续试错，只保留一个最小失败样例，写出关键变量的预期变化再改代码。";
            case "REGRESSION" -> "先比较最近两次提交的最小差异，找出哪一处修改让 verdict 回退。";
            case "FIXED_COMPILATION" -> "编译阶段已经推进，下一步只围绕当前 verdict 构造一个最小验证样例。";
            case "RUNTIME_FIXED_CORRECTNESS_REMAINS" -> "保留已生效的运行保护，再用一个小样例检查答案或复杂度。";
            case "ACCEPTED_AFTER_FIX", "ACCEPTED_REVIEW" -> "本轮进入复盘：说明关键修复、复杂度和一个边界样例。";
            default -> fallback;
        };
    }

    public String attentionReason(StudentTrajectoryResponse.LearningTrajectorySignal signal, String fallback) {
        if (signal == null) {
            return fallback;
        }
        String summary = signal.getSummary();
        if (signal.isNeedsTeacherAttention()) {
            return summary == null || summary.isBlank()
                    ? "学习轨迹显示需要老师关注：存在回退或反复卡住。"
                    : summary;
        }
        if ("ACCEPTED_AFTER_FIX".equals(signal.getPhase()) || "ACCEPTED_REVIEW".equals(signal.getPhase())) {
            return summary == null || summary.isBlank()
                    ? "本次已经通过，适合进入复盘迁移。"
                    : summary;
        }
        return fallback;
    }

    public String improvementSignal(StudentTrajectoryResponse.LearningTrajectorySignal signal, String fallback) {
        if (signal == null || signal.getSummary() == null || signal.getSummary().isBlank()) {
            return fallback;
        }
        return signal.getSummary();
    }

    public String nextStep(StudentTrajectoryResponse.LearningActionEvidence evidence, String fallback) {
        if (evidence == null) {
            return fallback;
        }
        return switch (evidence.getExecutionStatus() == null ? "" : evidence.getExecutionStatus()) {
            case "CONTRADICTED" -> "先不要继续叠加修改。请补交学习动作要求的可观察产出，再做下一次代码改动。";
            case "PARTIALLY_OBSERVED" -> "当前方向可能有效。请把这次学习动作再缩小成一个可检查的样例或变量表。";
            case "OBSERVED" -> "学习动作已有执行迹象，下一步做复盘：说明这次动作为什么能修正问题。";
            default -> fallback;
        };
    }

    public String attentionReason(StudentTrajectoryResponse.LearningActionEvidence evidence, String fallback) {
        if (evidence == null) {
            return fallback;
        }
        if ("CONTRADICTED".equals(evidence.getExecutionStatus())) {
            return evidence.getObservedEvidence() == null || evidence.getObservedEvidence().isBlank()
                    ? "后续证据显示学生可能没有真正执行学习动作，需要老师检查过程产出。"
                    : evidence.getObservedEvidence();
        }
        return fallback;
    }

    public String improvementSignal(StudentTrajectoryResponse.LearningActionEvidence evidence, String fallback) {
        if (evidence == null || evidence.getObservedEvidence() == null || evidence.getObservedEvidence().isBlank()) {
            return fallback;
        }
        if ("NOT_OBSERVED".equals(evidence.getExecutionStatus())) {
            return fallback;
        }
        return evidence.getObservedEvidence();
    }
}
