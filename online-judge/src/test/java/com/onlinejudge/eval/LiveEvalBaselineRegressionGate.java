package com.onlinejudge.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.onlinejudge.submission.application.LiveModelEvalReport;

public class LiveEvalBaselineRegressionGate {

    private LiveEvalBaselineRegressionGate() {
    }

    static List<String> evaluate(AssistantLiveEvalReport current,
                                 List<LiveEvalQualityBaselineDraft> baselines) {
        List<String> violations = new ArrayList<>();
        if (baselines == null || baselines.isEmpty()) {
            return violations;
        }
        Map<String, AssistantLiveEvalReport.Entry> entriesByCase = current == null || current.getEntries() == null
                ? Map.of()
                : current.getEntries()
                .stream()
                .filter(entry -> entry.getCaseId() != null && !entry.getCaseId().isBlank())
                .collect(Collectors.toMap(
                        AssistantLiveEvalReport.Entry::getCaseId,
                        Function.identity(),
                        (left, ignored) -> left
                ));
        for (LiveEvalQualityBaselineDraft baseline : baselines) {
            if (baseline == null || baseline.getCaseId() == null || baseline.getCaseId().isBlank()) {
                continue;
            }
            AssistantLiveEvalReport.Entry entry = entriesByCase.get(baseline.getCaseId());
            if (entry == null) {
                violations.add(baseline.getCaseId() + ": missing case from current live eval");
                continue;
            }
            evaluateCase(baseline, entry, violations);
        }
        return violations;
    }

    public static List<String> evaluateModel(LiveModelEvalReport current,
                                             List<LiveEvalQualityBaselineDraft> baselines) {
        List<String> violations = new ArrayList<>();
        if (baselines == null || baselines.isEmpty()) {
            return violations;
        }
        Map<String, LiveModelEvalReport.Entry> entriesByCase = current == null || current.getEntries() == null
                ? Map.of()
                : current.getEntries()
                .stream()
                .filter(entry -> entry.getCaseId() != null && !entry.getCaseId().isBlank())
                .collect(Collectors.toMap(
                        LiveModelEvalReport.Entry::getCaseId,
                        Function.identity(),
                        (left, ignored) -> left
                ));
        for (LiveEvalQualityBaselineDraft baseline : baselines) {
            if (baseline == null || baseline.getCaseId() == null || baseline.getCaseId().isBlank()) {
                continue;
            }
            LiveModelEvalReport.Entry entry = entriesByCase.get(baseline.getCaseId());
            if (entry == null) {
                violations.add(baseline.getCaseId() + ": missing case from current live model eval");
                continue;
            }
            evaluateModelCase(baseline, entry, violations);
        }
        return violations;
    }

    private static void evaluateCase(LiveEvalQualityBaselineDraft baseline,
                                     AssistantLiveEvalReport.Entry entry,
                                     List<String> violations) {
        String caseId = baseline.getCaseId();
        if (!Boolean.TRUE.equals(entry.getCompletedOutput())) {
            violations.add(caseId + ": completed output regression");
        }
        if (Boolean.TRUE.equals(entry.getFallbackUsed())) {
            violations.add(caseId + ": fallback regression");
        }
        if (!Boolean.TRUE.equals(entry.getSafetyPassed())) {
            violations.add(caseId + ": safety regression");
        }
        if (!Boolean.TRUE.equals(entry.getExpectedSignalHit())) {
            violations.add(caseId + ": expected signal regression");
        }
        if (!Boolean.TRUE.equals(entry.getEvidenceValid())) {
            violations.add(caseId + ": evidence validity regression");
        }
        if (!Boolean.TRUE.equals(entry.getTeachingActionValid())) {
            violations.add(caseId + ": teaching action regression");
        }
        for (String token : baseline.getMustKeep() == null ? List.<String>of() : baseline.getMustKeep()) {
            if (token == null || token.isBlank() || liveEvalCaseToken(token)) {
                continue;
            }
            if (!containsMustKeep(entry, token)) {
                violations.add(caseId + ": missing mustKeep " + token);
            }
        }
    }

    private static boolean containsMustKeep(AssistantLiveEvalReport.Entry entry, String token) {
        if (token.startsWith("fine:")) {
            return containsValue(entry.getActualFineGrainedTags(), token.substring("fine:".length()));
        }
        if (token.startsWith("issue:")) {
            return containsValue(entry.getActualIssueTags(), token.substring("issue:".length()));
        }
        if (token.startsWith("teachingAction:")) {
            return token.substring("teachingAction:".length()).equalsIgnoreCase(safe(entry.getTeachingAction()));
        }
        if (containsValue(entry.getActualEvidenceRefs(), token)) {
            return true;
        }
        String combined = String.join("\n",
                safe(entry.getTeachingAction()),
                safe(entry.getTeacherExpectation()),
                safe(entry.getOutputSummary()),
                safe(entry.getOutputDetail()),
                safe(entry.getIterationSuggestion())
        );
        return combined.contains(token);
    }

    private static void evaluateModelCase(LiveEvalQualityBaselineDraft baseline,
                                          LiveModelEvalReport.Entry entry,
                                          List<String> violations) {
        String caseId = baseline.getCaseId();
        if (Boolean.TRUE.equals(entry.getFallbackUsed())) {
            violations.add(caseId + ": fallback regression");
        }
        if (!Boolean.TRUE.equals(entry.getJsonValid())) {
            violations.add(caseId + ": json validity regression");
        }
        List<String> mustKeep = baseline.getMustKeep() == null ? List.of() : baseline.getMustKeep();
        if (requiresIssueHit(mustKeep) && !Boolean.TRUE.equals(entry.getModelIssueTagHit())) {
            violations.add(caseId + ": expected issue tag regression");
        }
        if (requiresFineHit(mustKeep) && !Boolean.TRUE.equals(entry.getModelFineTagHit())) {
            violations.add(caseId + ": expected fine tag regression");
        }
        if (!Boolean.TRUE.equals(entry.getEvidenceValid())) {
            violations.add(caseId + ": evidence validity regression");
        }
        if (!Boolean.TRUE.equals(entry.getSafetyPassed())) {
            violations.add(caseId + ": safety regression");
        }
        if (mustKeep.contains("latencyBudgetHealthy")
                && Boolean.TRUE.equals(entry.getLatencyBudgetExceeded())) {
            violations.add(caseId + ": latency budget regression");
        }
        if (requiresComplexQuality(mustKeep) && !Boolean.TRUE.equals(entry.getComplexQualityPassed())) {
            violations.add(caseId + ": complex quality regression");
        }
        if (requiresIntelligenceQuality(mustKeep) && !Boolean.TRUE.equals(entry.getIntelligenceQualityPassed())) {
            violations.add(caseId + ": external model intelligence regression");
        }
        if (requiresModelTraceQuality(mustKeep) && !Boolean.TRUE.equals(entry.getModelTraceQualityPassed())) {
            violations.add(caseId + ": external model native trace regression");
        }
        for (String token : mustKeep) {
            if (token == null || token.isBlank() || liveEvalCaseToken(token) || !modelRegressionToken(token)) {
                continue;
            }
            if ("latencyBudgetHealthy".equals(token)) {
                continue;
            }
            if (!containsModelMustKeep(entry, token)) {
                violations.add(caseId + ": missing mustKeep " + token);
            }
        }
    }

    private static boolean requiresIssueHit(List<String> mustKeep) {
        return mustKeep != null
                && (mustKeep.contains("modelIssueTagHit") || mustKeep.contains("expectedIssueTagHit"));
    }

    private static boolean requiresFineHit(List<String> mustKeep) {
        return mustKeep != null
                && (mustKeep.contains("modelFineTagHit") || mustKeep.contains("expectedFineTagHit"));
    }

    private static boolean containsModelMustKeep(LiveModelEvalReport.Entry entry, String token) {
        if (token.startsWith("fine:")) {
            return containsValue(entry.getActualFineGrainedTags(), token.substring("fine:".length()));
        }
        if (token.startsWith("issue:")) {
            return containsValue(entry.getActualIssueTags(), token.substring("issue:".length()));
        }
        if (containsValue(entry.getActualEvidenceRefs(), token)) {
            return true;
        }
        if (token.startsWith("complexMetric:")) {
            return containsValue(entry.getComplexPassedMetrics(), token);
        }
        if (token.startsWith("intelligenceMetric:")) {
            return containsValue(entry.getIntelligencePassedMetrics(), token);
        }
        if (token.startsWith("modelTraceMetric:")) {
            return containsValue(entry.getModelTracePassedMetrics(), token);
        }
        return switch (token) {
            case "modelIssueTagHit", "expectedIssueTagHit" -> Boolean.TRUE.equals(entry.getModelIssueTagHit());
            case "modelFineTagHit", "expectedFineTagHit" -> Boolean.TRUE.equals(entry.getModelFineTagHit());
            case "evidenceValid" -> Boolean.TRUE.equals(entry.getEvidenceValid());
            case "safetyPassed" -> Boolean.TRUE.equals(entry.getSafetyPassed());
            case "latencyBudgetHealthy" -> !Boolean.TRUE.equals(entry.getLatencyBudgetExceeded());
            case "complexQualityPassed" -> Boolean.TRUE.equals(entry.getComplexQualityPassed());
            case "intelligenceQualityPassed" -> Boolean.TRUE.equals(entry.getIntelligenceQualityPassed());
            case "modelTraceQualityPassed" -> Boolean.TRUE.equals(entry.getModelTraceQualityPassed());
            default -> false;
        };
    }

    private static boolean requiresComplexQuality(List<String> mustKeep) {
        return mustKeep != null
                && mustKeep.stream().anyMatch(token -> "complexQualityPassed".equals(token)
                || (token != null && token.startsWith("complexMetric:")));
    }

    private static boolean requiresIntelligenceQuality(List<String> mustKeep) {
        return mustKeep != null
                && mustKeep.stream().anyMatch(token -> "intelligenceQualityPassed".equals(token)
                || (token != null && token.startsWith("intelligenceMetric:")));
    }

    private static boolean requiresModelTraceQuality(List<String> mustKeep) {
        return mustKeep != null
                && mustKeep.stream().anyMatch(token -> "modelTraceQualityPassed".equals(token)
                || (token != null && token.startsWith("modelTraceMetric:")));
    }

    private static boolean containsValue(List<String> values, String expected) {
        if (values == null || expected == null || expected.isBlank()) {
            return false;
        }
        return values.stream().anyMatch(value -> expected.equalsIgnoreCase(safe(value)));
    }

    private static boolean liveEvalCaseToken(String token) {
        return token.startsWith("live_eval_case:");
    }

    private static boolean modelRegressionToken(String token) {
        return token.startsWith("fine:")
                || token.startsWith("issue:")
                || token.contains(":")
                || "modelIssueTagHit".equals(token)
                || "modelFineTagHit".equals(token)
                || "expectedIssueTagHit".equals(token)
                || "expectedFineTagHit".equals(token)
                || "evidenceValid".equals(token)
                || "safetyPassed".equals(token)
                || "latencyBudgetHealthy".equals(token)
                || "complexQualityPassed".equals(token)
                || token.startsWith("complexMetric:")
                || "intelligenceQualityPassed".equals(token)
                || token.startsWith("intelligenceMetric:")
                || "modelTraceQualityPassed".equals(token)
                || token.startsWith("modelTraceMetric:");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
