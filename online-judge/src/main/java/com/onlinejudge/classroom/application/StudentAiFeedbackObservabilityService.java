package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.dto.StudentAiFeedbackObservabilityResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.submission.domain.StudentAiFeedback;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.dto.StudentAiFeedbackResponse;
import com.onlinejudge.submission.persistence.StudentAiFeedbackEventRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAiFeedbackObservabilityService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final StudentAiFeedbackRepository feedbackRepository;
    private final StudentAiFeedbackEventRepository eventRepository;
    private final StudentAiFeedbackImpactAnalyzer impactAnalyzer;
    private final ObjectMapper objectMapper;

    public StudentAiFeedbackObservabilityResponse buildForAssignment(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new IllegalArgumentException("作业不存在: " + assignmentId);
        }
        List<Submission> submissions = submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId);
        List<Long> submissionIds = submissions.stream()
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .toList();
        List<StudentAiFeedback> feedbacks = submissionIds.isEmpty()
                ? List.of()
                : feedbackRepository.findBySubmissionIdIn(submissionIds);
        List<StudentAiFeedbackEvent> events = submissionIds.isEmpty()
                ? List.of()
                : eventRepository.findBySubmissionIdIn(submissionIds);
        Map<Long, SubmissionAnalysis> analyses = submissionIds.isEmpty()
                ? Map.of()
                : submissionAnalysisRepository.findBySubmissionIdIn(submissionIds)
                .stream()
                .filter(analysis -> analysis.getSubmissionId() != null)
                .collect(Collectors.toMap(
                        SubmissionAnalysis::getSubmissionId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        long failedSubmissionCount = submissions.stream().filter(this::isFailed).count();
        long modelReadyCount = feedbacks.stream().filter(this::isModelReady).count();
        long feedbackFailedCount = feedbacks.stream().filter(this::isFailureStatus).count();
        long timeoutCount = feedbacks.stream().filter(feedback -> hasStatus(feedback, "TIMEOUT")).count();
        long safetyRejectedCount = feedbacks.stream().filter(feedback -> hasStatus(feedback, "SAFETY_REJECTED")).count();
        long viewedCount = events.stream()
                .filter(event -> StudentAiFeedbackEvent.EVENT_VIEWED.equals(event.getEventType()))
                .map(StudentAiFeedbackEvent::getSubmissionId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        List<Long> latencies = feedbacks.stream()
                .map(this::latencyMs)
                .filter(Objects::nonNull)
                .filter(value -> value >= 0)
                .sorted()
                .toList();
        List<StudentAiFeedbackObservabilityResponse.FailureReasonStat> failureReasons = failureReasons(feedbacks, events, analyses);
        List<StudentAiFeedbackObservabilityResponse.ImpactStat> impactStats = impactStats(submissions, analyses, events);

        return StudentAiFeedbackObservabilityResponse.builder()
                .assignmentId(assignmentId)
                .submissionCount(submissions.size())
                .failedSubmissionCount(failedSubmissionCount)
                .feedbackRecordCount(feedbacks.size())
                .modelReadyCount(modelReadyCount)
                .feedbackFailedCount(feedbackFailedCount)
                .timeoutCount(timeoutCount)
                .safetyRejectedCount(safetyRejectedCount)
                .viewedCount(viewedCount)
                .modelReadyRate(percent(modelReadyCount, failedSubmissionCount))
                .viewRate(percent(viewedCount, modelReadyCount))
                .p50LatencyMs(percentile(latencies, 0.50))
                .p95LatencyMs(percentile(latencies, 0.95))
                .latencySampleCount(latencies.size())
                .failureReasons(failureReasons)
                .impactStats(impactStats)
                .summary(summary(failedSubmissionCount, modelReadyCount, feedbackFailedCount, viewedCount, impactStats))
                .recommendedAction(recommendedAction(failedSubmissionCount, modelReadyCount, feedbackFailedCount, viewedCount, impactStats))
                .build();
    }

    private List<StudentAiFeedbackObservabilityResponse.FailureReasonStat> failureReasons(
            List<StudentAiFeedback> feedbacks,
            List<StudentAiFeedbackEvent> events,
            Map<Long, SubmissionAnalysis> analyses) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<Long, StudentAiFeedback> feedbackBySubmission = feedbacks.stream()
                .filter(feedback -> feedback.getSubmissionId() != null)
                .collect(Collectors.toMap(
                        StudentAiFeedback::getSubmissionId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        feedbacks.stream()
                .filter(this::isFailureStatus)
                .map(feedback -> feedbackFailureReason(feedback, analyses))
                .map(this::normalizeReason)
                .forEach(reason -> counts.merge(reason, 1L, Long::sum));
        events.stream()
                .filter(event -> StudentAiFeedbackEvent.EVENT_FAILED.equals(event.getEventType()))
                .filter(event -> event.getSubmissionId() == null || !feedbackBySubmission.containsKey(event.getSubmissionId()))
                .map(event -> firstNonBlank(event.getFailureReason(), event.getFeedbackStatus(), "UNKNOWN"))
                .map(this::normalizeReason)
                .forEach(reason -> counts.merge(reason, 1L, Long::sum));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> StudentAiFeedbackObservabilityResponse.FailureReasonStat.builder()
                        .reason(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private String feedbackFailureReason(StudentAiFeedback feedback, Map<Long, SubmissionAnalysis> analyses) {
        String reason = firstNonBlank(feedback == null ? null : feedback.getFailureReason(),
                feedback == null ? null : feedback.getStatus(),
                "UNKNOWN");
        if (!"FULL_CHAIN_FAILED".equalsIgnoreCase(reason)) {
            return reason;
        }
        SubmissionAnalysis analysis = analyses == null || feedback == null
                ? null
                : analyses.get(feedback.getSubmissionId());
        String invocationReason = aiInvocationFailureReason(analysis);
        return firstNonBlank(invocationReason, reason);
    }

    private String aiInvocationFailureReason(SubmissionAnalysis analysis) {
        if (analysis == null || !hasText(analysis.getReportJson())) {
            return "";
        }
        try {
            SubmissionAnalysisResponse response =
                    objectMapper.readValue(analysis.getReportJson(), SubmissionAnalysisResponse.class);
            SubmissionAnalysisResponse.AiInvocation invocation =
                    response == null ? null : response.getAiInvocation();
            if (invocation == null) {
                return "";
            }
            String stage = firstNonBlank(
                    invocation.getFailureStage(),
                    "FAILED".equalsIgnoreCase(invocation.getAdviceGenerationStatus()) ? "DIAGNOSIS_AND_ADVICE" : null,
                    "FAILED".equalsIgnoreCase(invocation.getStandardLibraryNavigationStatus()) ? "STANDARD_LIBRARY_NAVIGATION" : null,
                    "MODEL"
            );
            String reason = firstNonBlank(
                    invocation.getFailureReason(),
                    invocation.getAdviceGenerationFailureReason(),
                    invocation.getStandardLibraryNavigationFailureReason(),
                    invocation.getStatus()
            );
            return reason.isBlank() ? stage : stage + ":" + reason;
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<StudentAiFeedbackObservabilityResponse.ImpactStat> impactStats(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            List<StudentAiFeedbackEvent> events) {
        Map<Long, StudentTrajectoryResponse.AiFeedbackImpact> impacts =
                impactAnalyzer.summarizeByFeedbackSubmission(submissions, analyses, events);
        if (impacts.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        impacts.values().stream()
                .map(StudentTrajectoryResponse.AiFeedbackImpact::getStatus)
                .map(status -> firstNonBlank(status, "NO_CLEAR_CHANGE_AFTER_AI"))
                .forEach(status -> counts.merge(status, 1L, Long::sum));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> StudentAiFeedbackObservabilityResponse.ImpactStat.builder()
                        .status(entry.getKey())
                        .label(impactLabel(entry.getKey()))
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private Long latencyMs(StudentAiFeedback feedback) {
        if (feedback == null || !hasText(feedback.getFeedbackJson())) {
            return null;
        }
        try {
            StudentAiFeedbackResponse response = objectMapper.readValue(feedback.getFeedbackJson(), StudentAiFeedbackResponse.class);
            return response == null ? null : response.getLatencyMs();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isFailed(Submission submission) {
        return submission != null
                && submission.getVerdict() != null
                && submission.getVerdict() != Submission.Verdict.ACCEPTED
                && submission.getVerdict() != Submission.Verdict.PENDING;
    }

    private boolean isModelReady(StudentAiFeedback feedback) {
        return hasStatus(feedback, "READY") && "MODEL".equalsIgnoreCase(firstNonBlank(feedback.getSource(), ""));
    }

    private boolean isFailureStatus(StudentAiFeedback feedback) {
        return hasStatus(feedback, "FAILED") || hasStatus(feedback, "TIMEOUT") || hasStatus(feedback, "SAFETY_REJECTED");
    }

    private boolean hasStatus(StudentAiFeedback feedback, String expected) {
        return feedback != null && expected.equalsIgnoreCase(firstNonBlank(feedback.getStatus(), ""));
    }

    private Long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return null;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    private double percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round((numerator * 10000.0) / denominator) / 100.0;
    }

    private String summary(long failedSubmissionCount,
                           long modelReadyCount,
                           long feedbackFailedCount,
                           long viewedCount,
                           List<StudentAiFeedbackObservabilityResponse.ImpactStat> impactStats) {
        if (failedSubmissionCount == 0) {
            return "当前作业暂时没有失败提交，学生 AI 反馈还没有形成课堂样本。";
        }
        if (modelReadyCount == 0) {
            return "已有失败提交，但暂时没有可用的模型反馈。";
        }
        long improved = countImpact(impactStats, "IMPROVED_AFTER_AI");
        if (improved > 0) {
            return "学生 AI 反馈已有查看和后续改善证据。";
        }
        if (feedbackFailedCount > 0 && modelReadyCount < failedSubmissionCount) {
            return "部分失败提交没有拿到可用模型反馈，需要关注生成稳定性。";
        }
        if (viewedCount == 0) {
            return "模型反馈已生成，但学生查看样本不足。";
        }
        return "学生 AI 反馈已生成，正在等待更多后续提交验证效果。";
    }

    private String recommendedAction(long failedSubmissionCount,
                                     long modelReadyCount,
                                     long feedbackFailedCount,
                                     long viewedCount,
                                     List<StudentAiFeedbackObservabilityResponse.ImpactStat> impactStats) {
        if (failedSubmissionCount == 0) {
            return "继续观察下一轮失败提交。";
        }
        if (modelReadyCount == 0 || feedbackFailedCount > modelReadyCount) {
            return "先检查模型生成链路，再判断提示质量。";
        }
        long stuck = countImpact(impactStats, "SAME_ISSUE_AFTER_AI") + countImpact(impactStats, "REGRESSED_AFTER_AI");
        if (stuck > 0) {
            return "优先查看仍卡住或回退的学生，必要时降低任务粒度。";
        }
        if (viewedCount == 0) {
            return "引导学生提交后打开结果反馈，积累查看后的后续提交样本。";
        }
        return "持续比较查看后改善、仍卡住和等待后续提交的比例。";
    }

    private long countImpact(List<StudentAiFeedbackObservabilityResponse.ImpactStat> impactStats, String status) {
        if (impactStats == null) {
            return 0;
        }
        return impactStats.stream()
                .filter(stat -> status.equals(stat.getStatus()))
                .mapToLong(StudentAiFeedbackObservabilityResponse.ImpactStat::getCount)
                .sum();
    }

    private String impactLabel(String status) {
        return switch (firstNonBlank(status, "")) {
            case "IMPROVED_AFTER_AI" -> "查看后改善";
            case "SAME_ISSUE_AFTER_AI" -> "仍卡同类问题";
            case "SHIFTED_AFTER_AI" -> "问题进入新阶段";
            case "REGRESSED_AFTER_AI" -> "出现回退";
            case "VERDICT_CHANGED_AFTER_AI" -> "评测阶段变化";
            case "NO_CLEAR_CHANGE_AFTER_AI" -> "暂未见明确变化";
            case "AWAITING_FOLLOWUP" -> "等待后续提交";
            default -> "其他变化";
        };
    }

    private String normalizeReason(String value) {
        String normalized = firstNonBlank(value, "UNKNOWN").trim();
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
