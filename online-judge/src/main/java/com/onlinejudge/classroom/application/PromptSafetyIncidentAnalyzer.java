package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.HintSafetyCheck;
import com.onlinejudge.classroom.dto.AiQualityOverviewResponse;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PromptSafetyIncidentAnalyzer {

    static final String SOURCE_DIAGNOSIS_HIGH_LEAK_RISK = "DIAGNOSIS_HIGH_LEAK_RISK";
    static final String SOURCE_HINT_SAFETY_CHECK = "HINT_SAFETY_CHECK";
    static final String SOURCE_COACH_SAFETY_RISK = "COACH_SAFETY_RISK";
    static final String SOURCE_NONE = "NONE";

    AiQualityOverviewResponse.PromptSafetyIncidentSignal analyze(
            List<SubmissionAnalysis> analyses,
            List<HintSafetyCheck> safetyChecks,
            Map<Long, CoachInteractionSummaryResponse> coachInteractions,
            DiagnosisReportReader diagnosisReportReader) {
        List<SubmissionAnalysis> safeAnalyses = analyses == null ? List.of() : analyses;
        List<HintSafetyCheck> riskySafetyChecks = safetyChecks == null ? List.of() : safetyChecks.stream()
                .filter(check -> riskWeight(check == null ? null : check.getRiskLevel()) >= 2)
                .sorted(Comparator.comparing(HintSafetyCheck::getCheckedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<CoachInteractionSummaryResponse> coachSafetyRisks = coachInteractions == null
                ? List.of()
                : coachInteractions.values()
                .stream()
                .filter(this::hasCoachSafetyRisk)
                .toList();
        long highLeakRiskCount = safeAnalyses.stream()
                .filter(analysis -> "HIGH".equalsIgnoreCase(diagnosisReportReader.answerLeakRisk(analysis)))
                .count();
        long highRiskSafetyDowngradeCount = riskySafetyChecks.stream()
                .filter(check -> riskWeight(check.getRiskLevel()) >= 3)
                .count();
        long safetyDowngradeCount = riskySafetyChecks.size();
        long coachSafetyRiskCount = coachSafetyRisks.size();
        long totalIncidentCount = highLeakRiskCount + safetyDowngradeCount + coachSafetyRiskCount;
        String status = status(highLeakRiskCount, highRiskSafetyDowngradeCount, safetyDowngradeCount, coachSafetyRiskCount);
        String primaryRiskSource = primaryRiskSource(highLeakRiskCount, safetyDowngradeCount, coachSafetyRiskCount);
        List<String> evidenceRefs = evidenceRefs(
                safeAnalyses,
                riskySafetyChecks,
                coachSafetyRisks,
                diagnosisReportReader
        );
        return AiQualityOverviewResponse.PromptSafetyIncidentSignal.builder()
                .status(status)
                .primaryRiskSource(primaryRiskSource)
                .totalIncidentCount(totalIncidentCount)
                .highLeakRiskCount(highLeakRiskCount)
                .safetyDowngradeCount(safetyDowngradeCount)
                .highRiskSafetyDowngradeCount(highRiskSafetyDowngradeCount)
                .coachSafetyRiskCount(coachSafetyRiskCount)
                .summary(summary(status, highLeakRiskCount, safetyDowngradeCount, coachSafetyRiskCount, primaryRiskSource))
                .recommendedAction(recommendedAction(primaryRiskSource, status))
                .evidenceRefs(evidenceRefs)
                .build();
    }

    private String status(long highLeakRiskCount,
                          long highRiskSafetyDowngradeCount,
                          long safetyDowngradeCount,
                          long coachSafetyRiskCount) {
        if (highLeakRiskCount > 0 || highRiskSafetyDowngradeCount > 0 || coachSafetyRiskCount > 0) {
            return "ACTION_NEEDED";
        }
        if (safetyDowngradeCount > 0) {
            return "WATCH";
        }
        return "HEALTHY";
    }

    private String primaryRiskSource(long highLeakRiskCount,
                                     long safetyDowngradeCount,
                                     long coachSafetyRiskCount) {
        if (highLeakRiskCount >= safetyDowngradeCount && highLeakRiskCount >= coachSafetyRiskCount && highLeakRiskCount > 0) {
            return SOURCE_DIAGNOSIS_HIGH_LEAK_RISK;
        }
        if (safetyDowngradeCount >= coachSafetyRiskCount && safetyDowngradeCount > 0) {
            return SOURCE_HINT_SAFETY_CHECK;
        }
        if (coachSafetyRiskCount > 0) {
            return SOURCE_COACH_SAFETY_RISK;
        }
        return SOURCE_NONE;
    }

    private List<String> evidenceRefs(List<SubmissionAnalysis> analyses,
                                      List<HintSafetyCheck> riskySafetyChecks,
                                      List<CoachInteractionSummaryResponse> coachSafetyRisks,
                                      DiagnosisReportReader diagnosisReportReader) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        analyses.stream()
                .filter(analysis -> "HIGH".equalsIgnoreCase(diagnosisReportReader.answerLeakRisk(analysis)))
                .limit(4)
                .forEach(analysis -> {
                    List<String> analysisRefs = diagnosisReportReader.evidenceRefs(analysis);
                    if (analysisRefs.isEmpty()) {
                        refs.add("high_leak_risk:submission:" + analysis.getSubmissionId());
                    } else {
                        refs.add(analysisRefs.get(0));
                    }
                    refs.add("prompt_safety_source:" + SOURCE_DIAGNOSIS_HIGH_LEAK_RISK);
                });
        riskySafetyChecks.stream()
                .limit(4)
                .forEach(check -> {
                    if (check.getId() != null) {
                        refs.add("hint_safety_check:" + check.getId());
                    }
                    if (check.getSubmissionId() != null) {
                        refs.add("hint_safety_submission:" + check.getSubmissionId());
                    }
                    refs.add("prompt_safety_source:" + SOURCE_HINT_SAFETY_CHECK);
                });
        coachSafetyRisks.stream()
                .limit(4)
                .forEach(summary -> {
                    if (summary.getSubmissionId() != null) {
                        refs.add("coach_safety:submission:" + summary.getSubmissionId());
                    }
                    refs.add("prompt_safety_source:" + SOURCE_COACH_SAFETY_RISK);
                });
        return refs.stream().limit(10).toList();
    }

    private boolean hasCoachSafetyRisk(CoachInteractionSummaryResponse summary) {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal =
                summary == null ? null : summary.getAnswerQualitySignal();
        if (signal == null) {
            return false;
        }
        return "SAFETY_RISK".equals(signal.getActionStatus())
                || "SAFETY_RISK".equals(signal.getQualityLevel())
                || signal.isNeedsTeacherAttention() && "SAFETY_RISK".equals(signal.getActionStatus());
    }

    private String summary(String status,
                           long highLeakRiskCount,
                           long safetyDowngradeCount,
                           long coachSafetyRiskCount,
                           String primaryRiskSource) {
        if ("HEALTHY".equals(status)) {
            return "暂未观察到提示安全事件，继续保持安全降级和证据引用校验。";
        }
        if (SOURCE_DIAGNOSIS_HIGH_LEAK_RISK.equals(primaryRiskSource)) {
            return "存在 " + highLeakRiskCount + " 条高泄题风险诊断，需要先复核模型输出和提示层级。";
        }
        if (SOURCE_HINT_SAFETY_CHECK.equals(primaryRiskSource)) {
            return "已有 " + safetyDowngradeCount + " 次提示被安全降级，需要复核降级原因并沉淀安全 fixture。";
        }
        if (SOURCE_COACH_SAFETY_RISK.equals(primaryRiskSource)) {
            return "存在 " + coachSafetyRiskCount + " 条 Coach 回答疑似越过证据层，需要把对话拉回样例、输出对比或变量现象。";
        }
        return "存在提示安全事件，需要教师复核风险来源。";
    }

    private String recommendedAction(String primaryRiskSource, String status) {
        if ("HEALTHY".equals(status)) {
            return "继续观察提示安全事件，并保留安全降级记录作为后续评测样本。";
        }
        return switch (primaryRiskSource) {
            case SOURCE_DIAGNOSIS_HIGH_LEAK_RISK -> "优先抽查高泄题风险诊断，确认是否需要收紧提示层级或加入安全 eval。";
            case SOURCE_HINT_SAFETY_CHECK -> "复核提示安全降级原因，把完整代码、直接改法或超层级样本沉淀为安全回归 fixture。";
            case SOURCE_COACH_SAFETY_RISK -> "将 Coach 对话拉回证据层，只讨论输入特征、输出对比或变量轨迹。";
            default -> "按证据引用抽查提示安全事件，确认是否需要教师介入。";
        };
    }

    private int riskWeight(String risk) {
        return switch (risk == null ? "" : risk.trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }
}
