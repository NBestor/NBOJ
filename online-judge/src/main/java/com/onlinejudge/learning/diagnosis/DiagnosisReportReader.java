package com.onlinejudge.learning.diagnosis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DiagnosisReportReader {

    private final ObjectMapper objectMapper;
    private final DiagnosisTaxonomy diagnosisTaxonomy;

    public List<String> issueTags(SubmissionAnalysis analysis) {
        if (analysis == null || analysis.getReportJson() == null || analysis.getReportJson().isBlank()) {
            return fallbackHeadline(analysis);
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(analysis.getReportJson(), Map.class);
            Object tags = payload.get("issueTags");
            if (tags instanceof Iterable<?> iterable) {
                List<String> values = new ArrayList<>();
                iterable.forEach(item -> values.add(String.valueOf(item)));
                return diagnosisTaxonomy.normalizeIssueTags(values);
            }
            return fallbackHeadline(analysis);
        } catch (JsonProcessingException ignored) {
            return fallbackHeadline(analysis);
        }
    }

    public List<String> fineGrainedTags(SubmissionAnalysis analysis) {
        if (analysis == null || analysis.getReportJson() == null || analysis.getReportJson().isBlank()) {
            return List.of();
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(analysis.getReportJson(), Map.class);
            Object tags = payload.get("fineGrainedTags");
            if (tags instanceof Iterable<?> iterable) {
                List<String> values = new ArrayList<>();
                iterable.forEach(item -> values.add(String.valueOf(item)));
                return diagnosisTaxonomy.normalizeFineGrainedTags(values);
            }
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
        return List.of();
    }

    public String studentHint(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "studentHint");
        return value == null ? "" : String.valueOf(value);
    }

    public List<String> abilityPoints(SubmissionAnalysis analysis) {
        return stringListValue(payloadValue(analysis, "abilityPoints"));
    }

    public List<String> focusPoints(SubmissionAnalysis analysis) {
        return stringListValue(payloadValue(analysis, "focusPoints"));
    }

    public StudentHintPlanSnapshot studentHintPlan(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "studentHintPlan");
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return new StudentHintPlanSnapshot(
                stringValue(map, "hintLevel"),
                stringValue(map, "problemType"),
                stringValue(map, "evidenceAnchor"),
                stringValue(map, "nextAction"),
                stringValue(map, "coachQuestion"),
                stringValue(map, "teachingAction"),
                stringListValue(map.get("evidenceRefs")),
                stringValue(map, "answerLeakRisk")
        );
    }

    public LearningInterventionPlanSnapshot learningInterventionPlan(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "learningInterventionPlan");
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return new LearningInterventionPlanSnapshot(
                stringValue(map, "interventionType"),
                stringValue(map, "goal"),
                stringValue(map, "studentTask"),
                stringValue(map, "checkQuestion"),
                stringValue(map, "completionSignal"),
                stringListValue(map.get("evidenceRefs")),
                integerValue(map.get("estimatedMinutes")),
                stringValue(map, "answerLeakRisk")
        );
    }

    public LearningActionEvidenceSnapshot learningActionEvidence(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "learningActionEvidence");
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return new LearningActionEvidenceSnapshot(
                stringValue(map, "expectedActionType"),
                normalizeExecutionStatus(stringValue(map, "executionStatus")),
                stringValue(map, "observedEvidence"),
                doubleValue(map.get("confidence")),
                stringListValue(map.get("evidenceRefs")),
                stringValue(map, "nextAdjustment")
        );
    }

    public TeacherCalibrationSignalSnapshot teacherCalibrationSignal(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "teacherCalibrationSignal");
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return new TeacherCalibrationSignalSnapshot(
                stringValue(map, "status"),
                stringValue(map, "summary"),
                stringValue(map, "originalIssueTag"),
                stringValue(map, "originalFineGrainedTag"),
                stringValue(map, "correctedIssueTag"),
                stringValue(map, "correctedFineGrainedTag"),
                longValue(map.get("correctionCount")),
                doubleValue(map.get("confidenceAdjustment")),
                stringListValue(map.get("evidenceRefs")),
                stringValue(map, "recommendedAction"),
                booleanValue(map.get("needsTeacherReview"))
        );
    }

    public String progressSignal(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "progressSignal");
        return value == null ? "" : String.valueOf(value);
    }

    public LearningTrajectorySignalSnapshot learningTrajectorySignal(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "learningTrajectorySignal");
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return new LearningTrajectorySignalSnapshot(
                stringValue(map, "phase"),
                stringValue(map, "label"),
                stringValue(map, "evidenceRef"),
                stringValue(map, "summary"),
                stringValue(map, "nextFocus"),
                booleanValue(map.get("needsTeacherAttention"))
        );
    }

    public List<String> evidenceRefs(SubmissionAnalysis analysis) {
        Object refs = payloadValue(analysis, "evidenceRefs");
        if (refs instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            iterable.forEach(item -> {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            });
            return values.stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    public Double confidence(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "confidence");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public String uncertainty(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "uncertainty");
        return value == null ? "" : String.valueOf(value);
    }

    public String answerLeakRisk(SubmissionAnalysis analysis) {
        if (analysis == null) {
            return "";
        }
        Object value = payloadValue(analysis, "answerLeakRisk");
        String normalized = value == null ? "UNKNOWN" : String.valueOf(value).trim().toUpperCase();
        return switch (normalized) {
            case "LOW", "MEDIUM", "HIGH", "UNKNOWN" -> normalized;
            default -> "UNKNOWN";
        };
    }

    public String analysisSchemaVersion(SubmissionAnalysis analysis) {
        String value = stringValue(analysis, "analysisSchemaVersion");
        return value.isBlank() ? "diagnosis-v1" : value;
    }

    public String diagnosticTrace(SubmissionAnalysis analysis) {
        return stringValue(analysis, "diagnosticTrace");
    }

    public AiInvocationSnapshot aiInvocation(SubmissionAnalysis analysis) {
        Object value = payloadValue(analysis, "aiInvocation");
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return new AiInvocationSnapshot(
                stringValue(map, "provider"),
                stringValue(map, "model"),
                stringValue(map, "modelVersion"),
                stringValue(map, "promptVersion"),
                stringValue(map, "agentVersion"),
                stringValue(map, "analysisSchemaVersion"),
                stringValue(map, "evidenceSchemaVersion"),
                stringValue(map, "taxonomyVersion"),
                stringValue(map, "status"),
                booleanValue(map.get("fallbackUsed")),
                stringValue(map, "runtimeMode"),
                stringValue(map, "failureStage"),
                stringValue(map, "failureReason"),
                stringValue(map, "transportMode"),
                intValueOrZero(map.get("streamChunkCount")),
                intValueOrZero(map.get("streamContentChunkCount")),
                intValueOrZero(map.get("streamReasoningChunkCount")),
                intValueOrZero(map.get("streamInvalidChunkCount")),
                stringValue(map, "streamFinishReason"),
                booleanValue(map.get("streamFallbackRetryUsed"))
        );
    }

    private String stringValue(SubmissionAnalysis analysis, String key) {
        Object value = payloadValue(analysis, key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Object payloadValue(SubmissionAnalysis analysis, String key) {
        if (analysis == null || analysis.getReportJson() == null || analysis.getReportJson().isBlank()) {
            return null;
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(analysis.getReportJson(), Map.class);
            return payload.get(key);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private List<String> fallbackHeadline(SubmissionAnalysis analysis) {
        if (analysis == null || analysis.getHeadline() == null || analysis.getHeadline().isBlank()) {
            return List.of();
        }
        return List.of(analysis.getHeadline());
    }

    private String stringValue(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return false;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int intValueOrZero(Object value) {
        Integer parsed = integerValue(value);
        return parsed == null ? 0 : parsed;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeExecutionStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return switch (normalized) {
            case "OBSERVED", "PARTIALLY_OBSERVED", "CONTRADICTED", "NOT_OBSERVED" -> normalized;
            default -> "";
        };
    }

    private List<String> stringListValue(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            iterable.forEach(item -> {
                if (item != null) {
                    String text = String.valueOf(item).trim();
                    if (!text.isBlank()) {
                        values.add(text);
                    }
                }
            });
            return values.stream().distinct().toList();
        }
        return List.of();
    }

    public record AiInvocationSnapshot(String provider,
                                       String model,
                                       String modelVersion,
                                       String promptVersion,
                                       String agentVersion,
                                       String analysisSchemaVersion,
                                       String evidenceSchemaVersion,
                                       String taxonomyVersion,
                                       String status,
                                       boolean fallbackUsed,
                                       String runtimeMode,
                                       String failureStage,
                                       String failureReason,
                                       String transportMode,
                                       int streamChunkCount,
                                       int streamContentChunkCount,
                                       int streamReasoningChunkCount,
                                       int streamInvalidChunkCount,
                                       String streamFinishReason,
                                       boolean streamFallbackRetryUsed) {
    }

    public record StudentHintPlanSnapshot(String hintLevel,
                                          String problemType,
                                          String evidenceAnchor,
                                          String nextAction,
                                          String coachQuestion,
                                          String teachingAction,
                                          List<String> evidenceRefs,
                                          String answerLeakRisk) {
    }

    public record LearningInterventionPlanSnapshot(String interventionType,
                                                   String goal,
                                                   String studentTask,
                                                   String checkQuestion,
                                                   String completionSignal,
                                                   List<String> evidenceRefs,
                                                   Integer estimatedMinutes,
                                                   String answerLeakRisk) {
    }

    public record LearningActionEvidenceSnapshot(String expectedActionType,
                                                 String executionStatus,
                                                 String observedEvidence,
                                                 Double confidence,
                                                 List<String> evidenceRefs,
                                                 String nextAdjustment) {
    }

    public record TeacherCalibrationSignalSnapshot(String status,
                                                   String summary,
                                                   String originalIssueTag,
                                                   String originalFineGrainedTag,
                                                   String correctedIssueTag,
                                                   String correctedFineGrainedTag,
                                                   Long correctionCount,
                                                   Double confidenceAdjustment,
                                                   List<String> evidenceRefs,
                                                   String recommendedAction,
                                                   boolean needsTeacherReview) {
    }

    public record LearningTrajectorySignalSnapshot(String phase,
                                                   String label,
                                                   String evidenceRef,
                                                   String summary,
                                                   String nextFocus,
                                                   boolean needsTeacherAttention) {
    }
}
