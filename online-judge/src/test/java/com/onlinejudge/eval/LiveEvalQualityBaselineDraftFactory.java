package com.onlinejudge.eval;

import com.onlinejudge.submission.application.LiveModelEvalReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LiveEvalQualityBaselineDraftFactory {

    private static final List<String> MUST_NOT_MENTION = List.of(
            "API Key",
            "api_key",
            "token",
            "密钥",
            "完整代码",
            "参考答案",
            "隐藏测试点",
            "学生姓名",
            "学号"
    );

    public List<LiveEvalQualityBaselineDraft> fromAssistantEntries(List<AssistantLiveEvalReport.Entry> entries) {
        return entries == null ? List.of() : entries.stream()
                .filter(this::assistantBaselineCandidate)
                .map(this::fromAssistantEntry)
                .toList();
    }

    public List<LiveEvalQualityBaselineDraft> fromModelEntries(List<LiveModelEvalReport.Entry> entries) {
        return entries == null ? List.of() : entries.stream()
                .filter(this::modelBaselineCandidate)
                .map(this::fromModelEntry)
                .toList();
    }

    private boolean assistantBaselineCandidate(AssistantLiveEvalReport.Entry entry) {
        if (entry == null) {
            return false;
        }
        return Boolean.TRUE.equals(entry.getCompletedOutput())
                && !Boolean.TRUE.equals(entry.getFallbackUsed())
                && Boolean.TRUE.equals(entry.getExpectedSignalHit())
                && Boolean.TRUE.equals(entry.getEvidenceValid())
                && Boolean.TRUE.equals(entry.getSafetyPassed())
                && !Boolean.FALSE.equals(entry.getTeachingActionValid())
                && !Boolean.FALSE.equals(entry.getStudentVisibleQualityPassed());
    }

    private boolean modelBaselineCandidate(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return false;
        }
        boolean tagHit = Boolean.TRUE.equals(entry.getModelIssueTagHit())
                || Boolean.TRUE.equals(entry.getModelFineTagHit());
        return Boolean.TRUE.equals(entry.getModelCompleted())
                && "MODEL_COMPLETED".equalsIgnoreCase(safe(entry.getStatus()))
                && !Boolean.TRUE.equals(entry.getFallbackUsed())
                && !Boolean.TRUE.equals(entry.getLatencyBudgetExceeded())
                && tagHit
                && Boolean.TRUE.equals(entry.getEvidenceValid())
                && Boolean.TRUE.equals(entry.getSafetyPassed())
                && (!Boolean.TRUE.equals(entry.getComplexCase()) || Boolean.TRUE.equals(entry.getRubricChainPassed()));
    }

    private LiveEvalQualityBaselineDraft fromAssistantEntry(AssistantLiveEvalReport.Entry entry) {
        String assistantType = firstNonBlank(entry.getAssistantType(), "UNKNOWN_ASSISTANT");
        String stage = "SUBMISSION_DIAGNOSIS".equals(assistantType)
                ? firstNonBlank(entry.getFailureStage(), "DIAGNOSIS_AND_ADVICE")
                : assistantType;
        List<String> evidenceRefs = evidenceRefs(entry.getCaseId(), entry.getActualEvidenceRefs());
        List<String> expectedSignals = assistantExpectedSignals(entry);
        return LiveEvalQualityBaselineDraft.builder()
                .name("assistant-quality-baseline-" + slug(entry.getCaseId()) + "-" + slug(assistantType))
                .sourceReportType("assistant-live-eval")
                .caseId(firstNonBlank(entry.getCaseId(), "unknown-case"))
                .assistantType(assistantType)
                .stage(stage)
                .model(firstNonBlank(entry.getModel(), "unknown-model"))
                .promptVersion(firstNonBlank(entry.getPromptVersion(), "unknown-prompt"))
                .status(firstNonBlank(entry.getStatus(), "MODEL_COMPLETED"))
                .baselineType(baselineType(assistantType))
                .expectedSignals(expectedSignals)
                .evidenceRefs(evidenceRefs)
                .mustKeep(mustKeep(entry, expectedSignals, evidenceRefs))
                .mustNotMention(MUST_NOT_MENTION)
                .teachingAction(sanitize(entry.getTeachingAction(), 160))
                .teacherExpectation(sanitize(entry.getTeacherExpectation(), 260))
                .outputSummary(sanitize(firstNonBlank(entry.getOutputSummary(), entry.getOutputDetail()), 320))
                .regressionPurpose(regressionPurpose(assistantType))
                .build();
    }

    private LiveEvalQualityBaselineDraft fromModelEntry(LiveModelEvalReport.Entry entry) {
        List<String> expectedSignals = modelExpectedSignals(entry);
        List<String> evidenceRefs = evidenceRefs(entry.getCaseId(), entry.getActualEvidenceRefs());
        return LiveEvalQualityBaselineDraft.builder()
                .name("model-quality-baseline-" + slug(entry.getCaseId()))
                .sourceReportType("live-model-eval")
                .caseId(firstNonBlank(entry.getCaseId(), "unknown-case"))
                .assistantType("SUBMISSION_DIAGNOSIS")
                .stage(firstNonBlank(entry.getStage(), "DIAGNOSIS_AGENT"))
                .model(firstNonBlank(entry.getModel(), "unknown-model"))
                .promptVersion(firstNonBlank(entry.getPromptVersion(), "unknown-prompt"))
                .status(firstNonBlank(entry.getStatus(), "MODEL_COMPLETED"))
                .baselineType("DIAGNOSIS_TAG_EVIDENCE_BASELINE")
                .expectedSignals(expectedSignals)
                .evidenceRefs(evidenceRefs)
                .mustKeep(mustKeep(entry, expectedSignals, evidenceRefs))
                .mustNotMention(MUST_NOT_MENTION)
                .teachingAction("")
                .teacherExpectation("")
                .outputSummary(sanitize(entry.getOutputSummary(), 320))
                .regressionPurpose("验证后续模型或 prompt 变更仍能命中诊断标签、保留证据引用并通过安全检查。")
                .build();
    }

    private List<String> assistantExpectedSignals(AssistantLiveEvalReport.Entry entry) {
        List<String> signals = new ArrayList<>();
        if (entry.getActualFineGrainedTags() != null) {
            entry.getActualFineGrainedTags().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> "fine:" + value.trim())
                    .forEach(signals::add);
        }
        if (entry.getActualIssueTags() != null) {
            entry.getActualIssueTags().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> "issue:" + value.trim())
                    .forEach(signals::add);
        }
        if (!safe(entry.getTeachingAction()).isBlank()) {
            signals.add("teachingAction:" + safe(entry.getTeachingAction()));
        }
        if (signals.isEmpty()) {
            signals.add("assistantType:" + firstNonBlank(entry.getAssistantType(), "UNKNOWN_ASSISTANT"));
        }
        return signals.stream().distinct().limit(8).toList();
    }

    private List<String> modelExpectedSignals(LiveModelEvalReport.Entry entry) {
        List<String> signals = new ArrayList<>();
        if (entry.getActualFineGrainedTags() != null) {
            entry.getActualFineGrainedTags().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> "fine:" + value.trim())
                    .forEach(signals::add);
        }
        if (entry.getActualIssueTags() != null) {
            entry.getActualIssueTags().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> "issue:" + value.trim())
                    .forEach(signals::add);
        }
        if (Boolean.TRUE.equals(entry.getModelIssueTagHit())) {
            signals.add("modelIssueTagHit");
        }
        if (Boolean.TRUE.equals(entry.getModelFineTagHit())) {
            signals.add("modelFineTagHit");
        }
        if (Boolean.TRUE.equals(entry.getEvidenceValid())) {
            signals.add("evidenceValid");
        }
        if (Boolean.TRUE.equals(entry.getSafetyPassed())) {
            signals.add("safetyPassed");
        }
        if (Boolean.TRUE.equals(entry.getRubricChainPassed())) {
            signals.add("rubricChainPassed");
        }
        if (entry.getRubricChainPassedStages() != null) {
            signals.addAll(entry.getRubricChainPassedStages().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .toList());
        }
        if (Boolean.TRUE.equals(entry.getComplexQualityPassed())) {
            signals.add("legacy:complexQualityPassed");
        }
        if (entry.getComplexPassedMetrics() != null) {
            signals.addAll(entry.getComplexPassedMetrics().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> "legacy:" + value)
                    .toList());
        }
        if (Boolean.TRUE.equals(entry.getIntelligenceQualityPassed())) {
            signals.add("legacy:intelligenceQualityPassed");
        }
        if (entry.getIntelligencePassedMetrics() != null) {
            signals.addAll(entry.getIntelligencePassedMetrics().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> "legacy:" + value)
                    .toList());
        }
        if (Boolean.TRUE.equals(entry.getModelTraceQualityPassed())) {
            signals.add("legacy:modelTraceQualityPassed");
        }
        if (entry.getModelTracePassedMetrics() != null) {
            signals.addAll(entry.getModelTracePassedMetrics().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> "legacy:" + value)
                    .toList());
        }
        if (!Boolean.TRUE.equals(entry.getLatencyBudgetExceeded())) {
            signals.add("latencyBudgetHealthy");
        }
        return signals;
    }

    private List<String> mustKeep(AssistantLiveEvalReport.Entry entry,
                                  List<String> expectedSignals,
                                  List<String> evidenceRefs) {
        List<String> values = new ArrayList<>();
        values.addAll(expectedSignals);
        values.addAll(evidenceRefs);
        values.add(sanitize(entry.getTeachingAction(), 120));
        values.add(sanitize(entry.getTeacherExpectation(), 160));
        values.add(sanitize(entry.getOutputSummary(), 160));
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(24)
                .toList();
    }

    private List<String> mustKeep(LiveModelEvalReport.Entry entry,
                                  List<String> expectedSignals,
                                  List<String> evidenceRefs) {
        List<String> values = new ArrayList<>();
        values.addAll(expectedSignals);
        values.addAll(evidenceRefs);
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(24)
                .toList();
    }

    private List<String> evidenceRefs(String caseId, List<String> actualEvidenceRefs) {
        List<String> refs = new ArrayList<>();
        refs.add("live_eval_case:" + firstNonBlank(caseId, "unknown-case"));
        if (actualEvidenceRefs != null) {
            refs.addAll(actualEvidenceRefs);
        }
        return refs.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
    }

    private String baselineType(String assistantType) {
        return switch (assistantType == null ? "" : assistantType) {
            case "SUBMISSION_DIAGNOSIS" -> "DIAGNOSIS_TAG_EVIDENCE_BASELINE";
            case "COACH_QUESTION" -> "COACH_QUESTION_BASELINE";
            case "GROWTH_REPORT" -> "GROWTH_REPORT_BASELINE";
            default -> "ASSISTANT_QUALITY_BASELINE";
        };
    }

    private String regressionPurpose(String assistantType) {
        return switch (assistantType == null ? "" : assistantType) {
            case "SUBMISSION_DIAGNOSIS" -> "验证后续模型或 prompt 变更仍能命中错因标签、保留证据引用、输出安全教学动作。";
            case "COACH_QUESTION" -> "验证后续 Coach prompt 仍能生成基于证据的安全追问，而不是直接给答案。";
            case "GROWTH_REPORT" -> "验证后续成长报告仍能引用提交轨迹并给出可执行下一步。";
            default -> "验证后续助手输出保持当前 live eval 成功样本的关键质量信号。";
        };
    }

    private String sanitize(String value, int maxLength) {
        String normalized = safe(value).replaceAll("[\\r\\n]+", " ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        normalized = normalized.replaceAll("(?i)(api[_-]?key|token|authorization|bearer)\\s*[:=]\\s*[^\\s,;]+", "$1=[redacted]");
        normalized = normalized.replaceAll("(?i)(ms-[a-z0-9-]{12,})", "[redacted-token]");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 15)) + "... [truncated]";
    }

    private String slug(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String normalized = safe(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
