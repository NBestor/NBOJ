package com.onlinejudge.eval;

import com.onlinejudge.submission.application.LiveModelEvalReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LiveEvalRuntimeFixtureDraftFactory {

    private static final String OFFLINE_PROFILE_REPORT_PATTERN =
            "target/ai-eval-reports/offline-runtime-profile-eval-*.json";
    private static final List<String> OFFLINE_PROFILE_REQUIRED_CHECKS = List.of(
            "lowLatencyRequestBytes < standardRequestBytes",
            "lowLatencyRequestCompact=true",
            "compressionRatio < 1.0",
            "evidenceRefCount > 0",
            "issueTagCount > 0",
            "teachingActionCount > 0",
            "hiddenBoundaryPresent=true"
    );
    private static final List<String> MODEL_RECOVERY_SMOKE_REQUIRED_CHECKS = List.of(
            "status=MODEL_COMPLETED",
            "fallbackUsed=false",
            "modelCompleted=true",
            "modelIssueTagHit=true or modelFineTagHit=true",
            "evidenceValid=true",
            "safetyPassed=true"
    );
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

    public List<LiveEvalRuntimeFixtureDraft> fromAssistantEntries(List<AssistantLiveEvalReport.Entry> entries) {
        return entries == null ? List.of() : entries.stream()
                .filter(this::shouldExportAssistant)
                .map(this::fromAssistantEntry)
                .toList();
    }

    public List<LiveEvalRuntimeFixtureDraft> fromModelEntries(List<LiveModelEvalReport.Entry> entries) {
        return entries == null ? List.of() : entries.stream()
                .filter(this::shouldExportModel)
                .map(this::fromModelEntry)
                .toList();
    }

    private boolean shouldExportAssistant(AssistantLiveEvalReport.Entry entry) {
        if (entry == null) {
            return false;
        }
        return Boolean.TRUE.equals(entry.getFallbackUsed())
                || !Boolean.TRUE.equals(entry.getCompletedOutput())
                || isStatus(entry.getStatus(), "MODEL_RUNTIME_FALLBACK")
                || isStatus(entry.getStatus(), "MODEL_PARTIAL_COMPLETED")
                || isStatus(entry.getStatus(), "EXCEPTION")
                || containsFailureReason(entry.getFailureReason(), "SAFETY_REJECTED")
                || containsFailureReason(entry.getFailureReason(), "QUALITY_MISS")
                || containsFailureReason(entry.getFailureReason(), "STUDENT_VISIBLE_QUALITY_RISK")
                || containsFailureReason(entry.getFailureReason(), "TEACHING_ACTION_MISMATCH");
    }

    private boolean shouldExportModel(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return false;
        }
        return Boolean.TRUE.equals(entry.getFallbackUsed())
                || Boolean.TRUE.equals(entry.getLatencyBudgetExceeded())
                || isStatus(entry.getStatus(), "MODEL_RUNTIME_FALLBACK")
                || isStatus(entry.getStatus(), "MODEL_PARTIAL_COMPLETED")
                || isStatus(entry.getStatus(), "EXCEPTION")
                || containsFailureReason(entry.getFailureReason(), "SAFETY_REJECTED")
                || containsFailureReason(entry.getFailureReason(), "QUALITY_MISS")
                || containsFailureReason(entry.getFailureReason(), "TEACHING_ACTION_MISMATCH");
    }

    private LiveEvalRuntimeFixtureDraft fromAssistantEntry(AssistantLiveEvalReport.Entry entry) {
        String failureType = classify(
                entry.getStatus(),
                entry.getFailureStage(),
                entry.getFailureReason(),
                entry.getFallbackUsed(),
                entry.getCompletedOutput()
        );
        String failureReason = sanitize(entry.getFailureReason(), 180);
        String failureStage = firstNonBlank(entry.getFailureStage(), entry.getAssistantType(), "UNKNOWN_STAGE");
        return LiveEvalRuntimeFixtureDraft.builder()
                .name("assistant-live-eval-" + slug(entry.getCaseId()) + "-" + slug(failureType))
                .sourceReportType("assistant-live-eval")
                .caseId(firstNonBlank(entry.getCaseId(), "unknown-case"))
                .assistantType(firstNonBlank(entry.getAssistantType(), "UNKNOWN_ASSISTANT"))
                .stage(failureStage)
                .model(firstNonBlank(entry.getModel(), "unknown-model"))
                .promptVersion(firstNonBlank(entry.getPromptVersion(), "unknown-prompt"))
                .status(firstNonBlank(entry.getStatus(), "UNKNOWN_STATUS"))
                .fallbackUsed(Boolean.TRUE.equals(entry.getFallbackUsed()))
                .completedOutput(Boolean.TRUE.equals(entry.getCompletedOutput()))
                .failureType(failureType)
                .failureStage(failureStage)
                .failureReason(failureReason)
                .offlineProfileEvalRecommended(false)
                .offlineProfileReportPattern("")
                .offlineProfileCaseId("")
                .offlineProfileRequiredChecks(List.of())
                .recoverySmokeRecommended(false)
                .recoverySmokeCaseId("")
                .recoverySmokeRuntimeProfile("")
                .recoverySmokeCommandHint("")
                .recoverySmokeRequiredChecks(List.of())
                .expectedRuntimeAction(expectedRuntimeAction(failureType))
                .evidenceRefs(evidenceRefs(entry.getCaseId(), entry.getActualEvidenceRefs()))
                .mustMention(mustMention(failureType, failureStage, failureReason))
                .mustNotMention(MUST_NOT_MENTION)
                .teacherExpectation(sanitize(entry.getTeacherExpectation(), 260))
                .outputSummary(sanitize(firstNonBlank(entry.getOutputSummary(), entry.getOutputDetail()), 260))
                .iterationSuggestion(sanitize(firstNonBlank(entry.getIterationSuggestion(), expectedRuntimeAction(failureType)), 260))
                .build();
    }

    private LiveEvalRuntimeFixtureDraft fromModelEntry(LiveModelEvalReport.Entry entry) {
        String failureType = classify(
                entry.getStatus(),
                entry.getFailureStage(),
                entry.getFailureReason(),
                entry.getFallbackUsed(),
                completedOutputForClassification(entry)
        );
        if (Boolean.TRUE.equals(entry.getLatencyBudgetExceeded())
                && !Boolean.TRUE.equals(entry.getFallbackUsed())
                && !isStatus(entry.getStatus(), "MODEL_PARTIAL_COMPLETED")) {
            failureType = "SLOW_RESPONSE";
        }
        String failureReason = sanitize(entry.getFailureReason(), 180);
        String failureStage = firstNonBlank(entry.getFailureStage(), entry.getStage(), "UNKNOWN_STAGE");
        boolean offlineProfileRecommended = "QUOTA_LIMIT".equals(failureType)
                && isInsufficientQuotaFailure(failureReason);
        boolean recoverySmokeRecommended = recoverySmokeRecommended(failureType, entry);
        String recoverySmokeProfile = recoverySmokeRecommended
                ? firstNonBlank(entry.getRuntimeProfile(), "low-latency")
                : "";
        String runtimeAction = expectedRuntimeAction(failureType, entry);
        return LiveEvalRuntimeFixtureDraft.builder()
                .name("model-live-eval-" + slug(entry.getCaseId()) + "-" + slug(failureType))
                .sourceReportType("live-model-eval")
                .caseId(firstNonBlank(entry.getCaseId(), "unknown-case"))
                .assistantType("SUBMISSION_DIAGNOSIS")
                .stage(failureStage)
                .model(firstNonBlank(entry.getModel(), "unknown-model"))
                .promptVersion(firstNonBlank(entry.getPromptVersion(), "unknown-prompt"))
                .status(firstNonBlank(entry.getStatus(), "UNKNOWN_STATUS"))
                .fallbackUsed(Boolean.TRUE.equals(entry.getFallbackUsed()))
                .completedOutput(!Boolean.TRUE.equals(entry.getFallbackUsed()))
                .runtimeProfile(firstNonBlank(entry.getRuntimeProfile(), "standard"))
                .requestBytes(intOrZero(entry.getRequestBytes()))
                .requestCompact(Boolean.TRUE.equals(entry.getRequestCompact()))
                .transportMode(firstNonBlank(entry.getTransportMode(), ""))
                .streamChunkCount(intOrZero(entry.getStreamChunkCount()))
                .streamContentChunkCount(intOrZero(entry.getStreamContentChunkCount()))
                .streamReasoningChunkCount(intOrZero(entry.getStreamReasoningChunkCount()))
                .streamInvalidChunkCount(intOrZero(entry.getStreamInvalidChunkCount()))
                .streamFinishReason(firstNonBlank(entry.getStreamFinishReason(), ""))
                .streamFallbackRetryUsed(Boolean.TRUE.equals(entry.getStreamFallbackRetryUsed()))
                .offlineProfileEvalRecommended(offlineProfileRecommended)
                .offlineProfileReportPattern(offlineProfileRecommended ? OFFLINE_PROFILE_REPORT_PATTERN : "")
                .offlineProfileCaseId(offlineProfileRecommended ? firstNonBlank(entry.getCaseId(), "unknown-case") : "")
                .offlineProfileRequiredChecks(offlineProfileRecommended ? OFFLINE_PROFILE_REQUIRED_CHECKS : List.of())
                .recoverySmokeRecommended(recoverySmokeRecommended)
                .recoverySmokeCaseId(recoverySmokeRecommended ? firstNonBlank(entry.getCaseId(), "unknown-case") : "")
                .recoverySmokeRuntimeProfile(recoverySmokeProfile)
                .recoverySmokeCommandHint(recoverySmokeRecommended
                        ? recoverySmokeCommandHint(firstNonBlank(entry.getCaseId(), "unknown-case"), recoverySmokeProfile)
                        : "")
                .recoverySmokeRequiredChecks(recoverySmokeRecommended ? recoverySmokeRequiredChecks(entry) : List.of())
                .failureType(failureType)
                .failureStage(failureStage)
                .failureReason(failureReason)
                .expectedRuntimeAction(runtimeAction)
                .evidenceRefs(evidenceRefs(entry.getCaseId(), List.of()))
                .mustMention(mustMention(failureType, failureStage, failureReason, entry))
                .mustNotMention(MUST_NOT_MENTION)
                .teacherExpectation("")
                .outputSummary(sanitize(entry.getOutputSummary(), 260))
                .iterationSuggestion(runtimeAction)
                .build();
    }

    private String classify(String status,
                            String failureStage,
                            String failureReason,
                            Boolean fallbackUsed,
                            Boolean completedOutput) {
        if (isStatus(status, "MODEL_PARTIAL_COMPLETED")) {
            return "PARTIAL_COMPLETION";
        }
        if (Boolean.TRUE.equals(completedOutput)) {
            return "QUALITY_MISS";
        }
        String reason = (firstNonBlank(failureReason, status, "") + " " + firstNonBlank(failureStage, ""))
                .toUpperCase(Locale.ROOT);
        if (reason.contains("INSUFFICIENT_QUOTA")
                || reason.contains("QUOTA")
                || reason.contains("RATE_LIMITED")
                || reason.contains("RATE_LIMIT")
                || reason.contains("STATUS 429")
                || reason.contains("\"429\"")) {
            return "QUOTA_LIMIT";
        }
        if (reason.contains("SAFETY")) {
            return "SAFETY_REJECTED";
        }
        if (reason.contains("TIMEOUT")) {
            return "TIMEOUT";
        }
        if (reason.contains("OUTPUT_TRUNCATED") || reason.contains("TRUNCATED")) {
            return "OUTPUT_TRUNCATED";
        }
        if (reason.contains("INVALID") || reason.contains("VALIDATION") || reason.contains("JSON")) {
            return "VALIDATION_FAILED";
        }
        if (reason.contains("RATE_LIMIT") || reason.contains("HTTP") || reason.contains("PROVIDER") || reason.contains("EXCEPTION")) {
            return "PROVIDER_ERROR";
        }
        if (reason.contains("TEACHING_ACTION_MISMATCH")) {
            return "TEACHING_ACTION_MISMATCH";
        }
        if (reason.contains("STUDENT_VISIBLE_QUALITY_RISK")) {
            return "QUALITY_MISS";
        }
        if (reason.contains("QUALITY_MISS")) {
            return "QUALITY_MISS";
        }
        if (Boolean.TRUE.equals(fallbackUsed) || isStatus(status, "MODEL_RUNTIME_FALLBACK")) {
            return "UNKNOWN_RUNTIME_FAILURE";
        }
        return "UNKNOWN_RUNTIME_FAILURE";
    }

    private String expectedRuntimeAction(String type) {
        return switch (type == null ? "" : type) {
            case "QUOTA_LIMIT" -> "先检查 ModelScope 额度、计费状态或 rate limit；恢复前降低 live eval 调用规模。";
            case "SAFETY_REJECTED" -> "把该样本沉淀为提示安全 fixture，复核 prompt 是否诱导直接给答案。";
            case "VALIDATION_FAILED" -> "补充结构化输出 fixture，优先修复 JSON/schema/证据引用校验。";
            case "TIMEOUT" -> "降低上下文体积或调整超时阈值，再用小批量 live eval 验证时延。";
            case "PROVIDER_ERROR" -> "检查 provider、网络和重试策略，并保留该样本做稳定性回归。";
            case "PARTIAL_COMPLETION" -> "保留可用诊断，同时复核教学提示阶段的安全和结构校验规则。";
            case "TEACHING_ACTION_MISMATCH" -> "复核教学动作 rubric 与 prompt 契约，让输出行动与错因标签一致。";
            case "QUALITY_MISS" -> "优先优化 prompt、rubric 或 fixture 质量约束，再对同类样本重跑 live eval。";
            case "SLOW_RESPONSE" -> "收缩诊断上下文或降低 max output tokens，并复核 stream reasoning chunk 体积。";
            case "OUTPUT_TRUNCATED" -> "提高输出 token 预算或收缩 JSON schema/上下文；必要时收缩 advice 输出 schema 或增加结构化重试。";
            default -> "先补充失败归因分类，再决定是否扩大外部模型评测。";
        };
    }

    private String expectedRuntimeAction(String type, LiveModelEvalReport.Entry entry) {
        String defaultAction = expectedRuntimeAction(type);
        if (entry == null) {
            return defaultAction;
        }
        List<String> guidance = new ArrayList<>();
        if ("QUOTA_LIMIT".equals(type) && isInsufficientQuotaFailure(entry.getFailureReason())) {
            guidance.add("额度恢复前先运行 offline runtime profile eval，并查看 "
                    + OFFLINE_PROFILE_REPORT_PATTERN
                    + "，确认 low-latency request bytes 更小、requestCompact=true、compressionRatio<1 且结构锚点保留。");
        }
        if (Boolean.TRUE.equals(entry.getLatencyBudgetExceeded())) {
            guidance.add("用同 case 小样本复测 latency budget，确认慢响应是否来自上下文体积、输出 token 或 provider 抖动。");
        }
        if ("stream".equalsIgnoreCase(firstNonBlank(entry.getTransportMode(), ""))) {
            if (intOrZero(entry.getStreamContentChunkCount()) == 0) {
                if ("QUOTA_LIMIT".equals(type)) {
                    guidance.add("同时用单条最小 stream smoke 验证 ModelScope 是否恢复 content chunk 输出。");
                } else {
                    guidance.add("用单条最小 stream smoke 验证是否仍无 content chunk。");
                }
            }
            if (intOrZero(entry.getStreamInvalidChunkCount()) > 0) {
                guidance.add("复核 SSE/JSON 增量解析，确认 invalid chunk 不会吞掉有效诊断。");
            }
            if ("OUTPUT_TRUNCATED".equals(type)
                    && "length".equalsIgnoreCase(firstNonBlank(entry.getStreamFinishReason(), ""))) {
                guidance.add("当前 stream finish_reason=length，优先验证 max_tokens 与 schema 字段体积是否匹配。");
            }
        }
        if (Boolean.TRUE.equals(entry.getStreamFallbackRetryUsed())) {
            guidance.add("复核非 stream 到 stream fallback retry 是否只触发一次且保留最终内容。");
        }
        if (guidance.isEmpty()) {
            return defaultAction;
        }
        return defaultAction + guidance.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining());
    }

    private boolean recoverySmokeRecommended(String failureType, LiveModelEvalReport.Entry entry) {
        if (List.of("QUOTA_LIMIT", "PROVIDER_ERROR", "TIMEOUT")
                .contains(firstNonBlank(failureType, ""))) {
            return true;
        }
        return entry != null
                && "stream".equalsIgnoreCase(firstNonBlank(entry.getTransportMode(), ""))
                && intOrZero(entry.getStreamContentChunkCount()) == 0;
    }

    private boolean isInsufficientQuotaFailure(String failureReason) {
        String reason = firstNonBlank(failureReason, "").toUpperCase(Locale.ROOT);
        return reason.contains("INSUFFICIENT_QUOTA");
    }

    private String recoverySmokeCommandHint(String caseId, String runtimeProfile) {
        return "AI_EVAL_RUNTIME_PROFILE=" + firstNonBlank(runtimeProfile, "low-latency")
                + " AI_EVAL_SMOKE_LIMIT=1 "
                + "./mvnw -Dexec.skip=true -Dtest=ModelDiagnosisEvalTest#liveModelSmokeProducesPerCaseReportWhenEnabled test"
                + " # verify caseId=" + firstNonBlank(caseId, "unknown-case");
    }

    private List<String> recoverySmokeRequiredChecks(LiveModelEvalReport.Entry entry) {
        List<String> checks = new ArrayList<>(MODEL_RECOVERY_SMOKE_REQUIRED_CHECKS);
        if (entry != null && "stream".equalsIgnoreCase(firstNonBlank(entry.getTransportMode(), ""))) {
            checks.add("streamContentChunkCount>0");
        }
        return checks.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
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

    private List<String> mustMention(String failureType, String failureStage, String failureReason) {
        return List.of(
                        firstNonBlank(failureType, "UNKNOWN_RUNTIME_FAILURE"),
                        label(failureType),
                        firstNonBlank(failureStage, "UNKNOWN_STAGE"),
                        firstNonBlank(failureReason, "")
                )
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(6)
                .toList();
    }

    private List<String> mustMention(String failureType,
                                     String failureStage,
                                     String failureReason,
                                     LiveModelEvalReport.Entry entry) {
        List<String> values = new ArrayList<>(mustMention(failureType, failureStage, failureReason));
        if (entry != null && !firstNonBlank(entry.getTransportMode(), "").isBlank()) {
            values.add("transport:" + entry.getTransportMode());
            if ("stream".equalsIgnoreCase(entry.getTransportMode())) {
                values.add("streamContentChunkCount=" + intOrZero(entry.getStreamContentChunkCount()));
                if (intOrZero(entry.getStreamInvalidChunkCount()) > 0) {
                    values.add("streamInvalidChunkCount=" + intOrZero(entry.getStreamInvalidChunkCount()));
                }
                if (Boolean.TRUE.equals(entry.getStreamFallbackRetryUsed())) {
                values.add("streamFallbackRetryUsed=true");
            }
            if ("length".equalsIgnoreCase(firstNonBlank(entry.getStreamFinishReason(), ""))) {
                values.add("streamFinishReason=length");
            }
        }
        }
        if (entry != null && Boolean.TRUE.equals(entry.getLatencyBudgetExceeded())) {
            values.add("latencyBudgetExceeded=true");
            values.add("runtimeProfile=" + firstNonBlank(entry.getRuntimeProfile(), "standard"));
            if (entry.getRequestBytes() != null && entry.getRequestBytes() > 0) {
                values.add("requestBytes=" + entry.getRequestBytes());
            }
            if (Boolean.TRUE.equals(entry.getRequestCompact())) {
                values.add("requestCompact=true");
            }
            if (entry.getLatencyMs() != null) {
                values.add("latencyMs=" + entry.getLatencyMs());
            }
            if (entry.getLatencyBudgetMs() != null) {
                values.add("latencyBudgetMs=" + entry.getLatencyBudgetMs());
            }
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(12)
                .toList();
    }

    private String label(String type) {
        return switch (type == null ? "" : type) {
            case "QUOTA_LIMIT" -> "额度不足";
            case "SAFETY_REJECTED" -> "安全拒绝";
            case "VALIDATION_FAILED" -> "结构校验失败";
            case "TIMEOUT" -> "调用超时";
            case "PROVIDER_ERROR" -> "provider 或网络错误";
            case "PARTIAL_COMPLETION" -> "部分完成";
            case "TEACHING_ACTION_MISMATCH" -> "教学动作不匹配";
            case "QUALITY_MISS" -> "质量未命中";
            case "SLOW_RESPONSE" -> "慢响应";
            case "OUTPUT_TRUNCATED" -> "输出截断";
            default -> "未知运行失败";
        };
    }

    private Boolean completedOutputForClassification(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return false;
        }
        if (Boolean.TRUE.equals(entry.getLatencyBudgetExceeded())
                && !Boolean.TRUE.equals(entry.getFallbackUsed())) {
            return false;
        }
        return entry.getStatus() != null
                && !isStatus(entry.getStatus(), "MODEL_RUNTIME_FALLBACK")
                && !Boolean.TRUE.equals(entry.getFallbackUsed());
    }

    private boolean containsFailureReason(String failureReason, String marker) {
        return safe(failureReason).toUpperCase(Locale.ROOT).contains(marker);
    }

    private boolean isStatus(String actual, String expected) {
        return expected.equalsIgnoreCase(safe(actual));
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

    private int intOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
