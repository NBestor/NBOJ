package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OfflineRuntimeProfileEvalReportFactory {

    private static final String MODEL = "offline-profile-eval-model";
    private static final int MAX_OUTPUT_TOKENS = 900;

    private final ObjectMapper objectMapper;
    private final ExternalModelAgentRuntime runtime;

    public OfflineRuntimeProfileEvalReportFactory(ObjectMapper objectMapper,
                                                  ExternalModelAgentRuntime runtime) {
        this.objectMapper = objectMapper;
        this.runtime = runtime;
    }

    public OfflineRuntimeProfileEvalReport fromCases(List<OfflineEvalCase> cases) {
        List<OfflineRuntimeProfileEvalReport.Entry> entries = cases == null ? List.of() : cases.stream()
                .map(this::fromCase)
                .toList();
        int reducedCount = (int) entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getRequestBytesReduced()))
                .count();
        int autoReducedCount = (int) entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getAutoRequestBytesReduced()))
                .count();
        int autoCompactCount = (int) entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getAutoRequestCompact()))
                .count();
        int qualityCount = (int) entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getQualityPreserved()))
                .count();
        int autoQualityCount = (int) entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getAutoQualityPreserved()))
                .count();
        double averageRatio = entries.stream()
                .map(OfflineRuntimeProfileEvalReport.Entry::getCompressionRatio)
                .filter(value -> value != null && !value.isNaN())
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        double averageAutoRatio = entries.stream()
                .map(OfflineRuntimeProfileEvalReport.Entry::getAutoCompressionRatio)
                .filter(value -> value != null && !value.isNaN())
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        return OfflineRuntimeProfileEvalReport.builder()
                .reportType("offline-runtime-profile-eval")
                .totalCount(entries.size())
                .reducedCount(reducedCount)
                .autoReducedCount(autoReducedCount)
                .autoCompactCount(autoCompactCount)
                .qualityPreservedCount(qualityCount)
                .autoQualityPreservedCount(autoQualityCount)
                .averageCompressionRatio(round(averageRatio))
                .averageAutoCompressionRatio(round(averageAutoRatio))
                .entries(entries)
                .build();
    }

    private OfflineRuntimeProfileEvalReport.Entry fromCase(OfflineEvalCase evalCase) {
        ExternalModelAgentRuntime.RuntimePlan standardPlan = runtime.prepare(
                evalCase.evidencePackage(),
                evalCase.baseline(),
                ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD
        );
        ExternalModelAgentRuntime.RuntimePlan lowLatencyPlan = runtime.prepare(
                evalCase.evidencePackage(),
                evalCase.baseline(),
                "low-latency"
        );
        ExternalModelAgentRuntime.RuntimePlan autoPlan = runtime.prepare(
                evalCase.evidencePackage(),
                evalCase.baseline(),
                "auto"
        );
        int standardBytes = requestBytes(standardPlan);
        int lowLatencyBytes = requestBytes(lowLatencyPlan);
        int autoBytes = requestBytes(autoPlan);
        boolean reduced = lowLatencyBytes > 0 && standardBytes > 0 && lowLatencyBytes < standardBytes;
        boolean autoReduced = autoBytes > 0 && standardBytes > 0 && autoBytes < standardBytes;
        ModelDiagnosisBrief brief = lowLatencyPlan.getBrief();
        int evidenceRefCount = size(brief == null ? null : brief.getEvidenceRefs());
        int issueTagCount = size(brief == null ? null : brief.getAllowedIssueTags());
        int fineTagCount = size(brief == null ? null : brief.getAllowedFineGrainedTags());
        int teachingActionCount = issueTagCount;
        boolean hiddenBoundaryPresent = brief != null && brief.getHiddenDataBoundary() != null;
        List<String> failureReasons = failureReasons(
                reduced,
                evidenceRefCount,
                issueTagCount,
                teachingActionCount,
                hiddenBoundaryPresent
        );
        ModelDiagnosisBrief autoBrief = autoPlan.getBrief();
        int autoEvidenceRefCount = size(autoBrief == null ? null : autoBrief.getEvidenceRefs());
        int autoIssueTagCount = size(autoBrief == null ? null : autoBrief.getAllowedIssueTags());
        int autoFineTagCount = size(autoBrief == null ? null : autoBrief.getAllowedFineGrainedTags());
        int autoTeachingActionCount = autoIssueTagCount;
        boolean autoHiddenBoundaryPresent = autoBrief != null && autoBrief.getHiddenDataBoundary() != null;
        List<String> autoFailureReasons = autoFailureReasons(
                autoPlan.isRequestCompact(),
                autoReduced,
                autoEvidenceRefCount,
                autoIssueTagCount,
                autoTeachingActionCount,
                autoHiddenBoundaryPresent
        );
        return OfflineRuntimeProfileEvalReport.Entry.builder()
                .caseId(evalCase.caseId())
                .promptVersion(lowLatencyPlan.getAdvicePrompt().getVersion())
                .standardRequestBytes(standardBytes)
                .lowLatencyRequestBytes(lowLatencyBytes)
                .autoRequestBytes(autoBytes)
                .requestBytesReduced(reduced)
                .autoRequestBytesReduced(autoReduced)
                .compressionRatio(ratio(lowLatencyBytes, standardBytes))
                .autoCompressionRatio(ratio(autoBytes, standardBytes))
                .lowLatencyRuntimeProfile(lowLatencyPlan.getRuntimeProfile())
                .lowLatencyRequestCompact(lowLatencyPlan.isRequestCompact())
                .autoRuntimeProfile(autoPlan.getRuntimeProfile())
                .autoRequestCompact(autoPlan.isRequestCompact())
                .evidenceRefCount(evidenceRefCount)
                .issueTagCount(issueTagCount)
                .fineTagCount(fineTagCount)
                .teachingActionCount(teachingActionCount)
                .hiddenBoundaryPresent(hiddenBoundaryPresent)
                .autoEvidenceRefCount(autoEvidenceRefCount)
                .autoIssueTagCount(autoIssueTagCount)
                .autoFineTagCount(autoFineTagCount)
                .autoTeachingActionCount(autoTeachingActionCount)
                .autoHiddenBoundaryPresent(autoHiddenBoundaryPresent)
                .qualityPreserved(failureReasons.isEmpty())
                .autoQualityPreserved(autoFailureReasons.isEmpty())
                .failureReasons(failureReasons)
                .autoFailureReasons(autoFailureReasons)
                .build();
    }

    private int requestBytes(ExternalModelAgentRuntime.RuntimePlan plan) {
        Map<String, Object> stagePayload = new LinkedHashMap<>();
        stagePayload.put("brief", plan.getBrief());
        stagePayload.put("freeDiagnosis", Map.of("status", "PENDING_FREE_DIAGNOSIS"));
        stagePayload.put("navigationResult", Map.of("status", "PENDING_AI_NAVIGATION"));
        stagePayload.put("standardLibrary", plan.getStandardLibraryPack());
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", MODEL);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", plan.getAdvicePrompt().getSystemPrompt()),
                Map.of("role", "user", "content", write(stagePayload))
        ));
        requestBody.put("temperature", 0.2);
        requestBody.put("stream", true);
        requestBody.put("max_tokens", MAX_OUTPUT_TOKENS);
        return write(requestBody).getBytes(StandardCharsets.UTF_8).length;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize offline runtime profile eval payload", exception);
        }
    }

    private List<String> failureReasons(boolean reduced,
                                        int evidenceRefCount,
                                        int issueTagCount,
                                        int teachingActionCount,
                                        boolean hiddenBoundaryPresent) {
        List<String> reasons = new ArrayList<>();
        if (!reduced) {
            reasons.add("LOW_LATENCY_REQUEST_NOT_SMALLER");
        }
        if (evidenceRefCount <= 0) {
            reasons.add("MISSING_EVIDENCE_REFS");
        }
        if (issueTagCount <= 0) {
            reasons.add("MISSING_ISSUE_TAGS");
        }
        if (teachingActionCount <= 0) {
            reasons.add("MISSING_TEACHING_ACTIONS");
        }
        if (!hiddenBoundaryPresent) {
            reasons.add("MISSING_HIDDEN_BOUNDARY");
        }
        return reasons;
    }

    private List<String> autoFailureReasons(boolean autoCompact,
                                            boolean autoReduced,
                                            int evidenceRefCount,
                                            int issueTagCount,
                                            int teachingActionCount,
                                            boolean hiddenBoundaryPresent) {
        List<String> reasons = new ArrayList<>();
        if (autoCompact && !autoReduced) {
            reasons.add("AUTO_REQUEST_NOT_SMALLER");
        }
        if (evidenceRefCount <= 0) {
            reasons.add("AUTO_MISSING_EVIDENCE_REFS");
        }
        if (issueTagCount <= 0) {
            reasons.add("AUTO_MISSING_ISSUE_TAGS");
        }
        if (teachingActionCount <= 0) {
            reasons.add("AUTO_MISSING_TEACHING_ACTIONS");
        }
        if (!hiddenBoundaryPresent) {
            reasons.add("AUTO_MISSING_HIDDEN_BOUNDARY");
        }
        return reasons;
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private Double ratio(int lowLatencyBytes, int standardBytes) {
        if (standardBytes <= 0) {
            return 0.0;
        }
        return round((double) lowLatencyBytes / standardBytes);
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    public record OfflineEvalCase(String caseId,
                                  DiagnosisEvidencePackage evidencePackage,
                                  com.onlinejudge.submission.dto.SubmissionAnalysisResponse baseline) {
    }
}
