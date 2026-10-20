package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.dto.RecommendationEffectivenessResponse;
import com.onlinejudge.classroom.persistence.StudentRecommendationEventRepository;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.submission.domain.Submission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationEffectivenessService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final StudentRecommendationEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final RecommendationActionEvidenceAnalyzer actionEvidenceAnalyzer;
    @Autowired(required = false)
    private AssignmentRepository assignmentRepository;
    @Autowired(required = false)
    private CurrentTeacherContext currentTeacherContext;

    @Autowired
    public RecommendationEffectivenessService(StudentRecommendationEventRepository eventRepository,
                                              ObjectMapper objectMapper) {
        this(eventRepository, objectMapper, new RecommendationActionEvidenceAnalyzer());
    }

    public RecommendationEffectivenessService(StudentRecommendationEventRepository eventRepository,
                                               ObjectMapper objectMapper,
                                               RecommendationActionEvidenceAnalyzer actionEvidenceAnalyzer) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.actionEvidenceAnalyzer = actionEvidenceAnalyzer == null ? new RecommendationActionEvidenceAnalyzer() : actionEvidenceAnalyzer;
    }

    public RecommendationEffectivenessResponse buildOverview() {
        List<StudentRecommendationEvent> recent = eventRepository.findTop500ByOrderByCreatedAtDesc();
        if (assignmentRepository == null || currentTeacherContext == null) return buildFrom(recent);
        Set<Long> ownedAssignmentIds = assignmentRepository
                .findByOwnerTeacherIdOrderByCreatedAtDesc(currentTeacherContext.requireTeacherId()).stream()
                .map(assignment -> assignment.getId()).filter(Objects::nonNull).collect(Collectors.toSet());
        return buildFrom(recent.stream()
                .filter(event -> event.getAssignmentId() != null && ownedAssignmentIds.contains(event.getAssignmentId()))
                .toList());
    }

    public RecommendationEffectivenessResponse buildOverview(Long assignmentId) {
        if (assignmentId == null) {
            return buildOverview();
        }
        return buildFrom(eventRepository.findTop500ByAssignmentIdOrderByCreatedAtDesc(assignmentId));
    }

    public List<RecommendationEffectivenessResponse.ActionEvidenceSignal> buildInterventionQueue() {
        return actionEvidenceAnalyzer.analyze(eventRepository.findTop500ByOrderByCreatedAtDesc())
                .stream()
                .filter(signal -> signal.isNeedsTeacherAttention() || actionEvidenceAnalyzer.isUnresolved(signal))
                .limit(20)
                .toList();
    }

    RecommendationEffectivenessResponse buildFrom(List<StudentRecommendationEvent> events) {
        List<StudentRecommendationEvent> safeEvents = normalizeEvents(events);
        Metrics overall = metricsFor(safeEvents);
        List<RecommendationEffectivenessResponse.ActionEvidenceSignal> actionEvidenceSignals =
                actionEvidenceAnalyzer.analyze(safeEvents);
        List<RecommendationEffectivenessResponse.FeedbackSignal> feedbackSignals =
                feedbackSignals(safeEvents, actionEvidenceSignals);
        return RecommendationEffectivenessResponse.builder()
                .recentEventCount(safeEvents.size())
                .uniqueRecommendationCount(overall.uniqueRecommendationCount)
                .exposureCount(overall.exposureCount)
                .clickCount(overall.clickCount)
                .enteredProblemCount(overall.enteredProblemCount)
                .followupSubmissionCount(overall.followupSubmissionCount)
                .acceptedFollowupCount(overall.acceptedFollowupCount)
                .sameFocusIssueCount(overall.sameFocusIssueCount)
                .clickedWithoutSubmissionCount(overall.clickedWithoutSubmissionCount)
                .unresolvedLearningSignalCount(overall.unresolvedLearningSignalCount)
                .teacherInterventionRecommendedCount(overall.teacherInterventionRecommendedCount)
                .clickThroughRate(overall.clickThroughRate())
                .followupSubmissionRate(overall.followupSubmissionRate())
                .acceptedFollowupRate(overall.acceptedFollowupRate())
                .sameFocusIssueRate(overall.sameFocusIssueRate())
                .summary(summary(overall))
                .byType(segmentsByType(safeEvents))
                .byStrategy(segmentsByStrategy(safeEvents))
                .focusTags(segmentsByFocusTag(safeEvents))
                .feedbackSignals(feedbackSignals)
                .actionEvidenceSignals(actionEvidenceSignals)
                .build();
    }

    private List<StudentRecommendationEvent> normalizeEvents(List<StudentRecommendationEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .filter(Objects::nonNull)
                .filter(event -> event.getRecommendationToken() != null && !event.getRecommendationToken().isBlank())
                .toList();
    }

    private List<RecommendationEffectivenessResponse.SegmentStat> segmentsByType(List<StudentRecommendationEvent> events) {
        return events.stream()
                .collect(Collectors.groupingBy(event -> blankToUnknown(event.getType()), LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> toSegment(entry.getKey(), typeLabel(entry.getKey()), entry.getValue()))
                .sorted(segmentComparator())
                .limit(8)
                .toList();
    }

    private List<RecommendationEffectivenessResponse.SegmentStat> segmentsByStrategy(List<StudentRecommendationEvent> events) {
        return events.stream()
                .collect(Collectors.groupingBy(event -> blankToUnknown(event.getStrategy()), LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> toSegment(entry.getKey(), strategyLabel(entry.getKey()), entry.getValue()))
                .sorted(segmentComparator())
                .limit(8)
                .toList();
    }

    private List<RecommendationEffectivenessResponse.SegmentStat> segmentsByFocusTag(List<StudentRecommendationEvent> events) {
        Map<String, List<StudentRecommendationEvent>> byTag = new LinkedHashMap<>();
        for (StudentRecommendationEvent event : events) {
            Set<String> tags = focusTags(event);
            if (tags.isEmpty()) {
                continue;
            }
            for (String tag : tags) {
                byTag.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(event);
            }
        }
        return byTag.entrySet()
                .stream()
                .map(entry -> toSegment(entry.getKey(), entry.getKey(), entry.getValue()))
                .sorted(segmentComparator())
                .limit(8)
                .toList();
    }

    private Comparator<RecommendationEffectivenessResponse.SegmentStat> segmentComparator() {
        return Comparator
                .comparing(RecommendationEffectivenessResponse.SegmentStat::getFollowupSubmissionCount)
                .thenComparing(RecommendationEffectivenessResponse.SegmentStat::getClickCount)
                .thenComparing(RecommendationEffectivenessResponse.SegmentStat::getExposureCount)
                .reversed()
                .thenComparing(RecommendationEffectivenessResponse.SegmentStat::getKey);
    }

    private RecommendationEffectivenessResponse.SegmentStat toSegment(String key,
                                                                      String label,
                                                                      List<StudentRecommendationEvent> events) {
        Metrics metrics = metricsFor(events);
        return RecommendationEffectivenessResponse.SegmentStat.builder()
                .key(key)
                .label(label)
                .exposureCount(metrics.exposureCount)
                .clickCount(metrics.clickCount)
                .enteredProblemCount(metrics.enteredProblemCount)
                .followupSubmissionCount(metrics.followupSubmissionCount)
                .acceptedFollowupCount(metrics.acceptedFollowupCount)
                .sameFocusIssueCount(metrics.sameFocusIssueCount)
                .unresolvedLearningSignalCount(metrics.unresolvedLearningSignalCount)
                .teacherInterventionRecommendedCount(metrics.teacherInterventionRecommendedCount)
                .clickThroughRate(metrics.clickThroughRate())
                .followupSubmissionRate(metrics.followupSubmissionRate())
                .acceptedFollowupRate(metrics.acceptedFollowupRate())
                .sameFocusIssueRate(metrics.sameFocusIssueRate())
                .build();
    }

    private Metrics metricsFor(Collection<StudentRecommendationEvent> events) {
        List<StudentRecommendationEvent> safeEvents = normalizeEvents(events == null ? List.of() : List.copyOf(events));
        Map<String, List<StudentRecommendationEvent>> byToken = safeEvents.stream()
                .collect(Collectors.groupingBy(StudentRecommendationEvent::getRecommendationToken, LinkedHashMap::new, Collectors.toList()));
        long exposureCount = countEvents(safeEvents, StudentRecommendationEventService.EVENT_EXPOSED);
        long clickCount = countEvents(safeEvents, StudentRecommendationEventService.EVENT_CLICKED);
        long enteredProblemCount = countEvents(safeEvents, StudentRecommendationEventService.EVENT_ENTERED_PROBLEM);
        List<StudentRecommendationEvent> submissions = safeEvents.stream()
                .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                .toList();
        long accepted = submissions.stream()
                .filter(event -> Submission.Verdict.ACCEPTED.name().equals(event.getFollowupVerdict()))
                .count();
        long sameFocus = submissions.stream()
                .filter(this::sameFocusIssue)
                .count();
        long unresolvedLearningSignals = submissions.stream()
                .filter(event -> !Submission.Verdict.ACCEPTED.name().equals(event.getFollowupVerdict()))
                .filter(this::sameFocusIssue)
                .count();
        long teacherInterventionRecommended = byToken.values()
                .stream()
                .filter(this::needsTeacherIntervention)
                .count();
        long exposedTokens = countTokenGroups(byToken.values(), StudentRecommendationEventService.EVENT_EXPOSED);
        long clickedTokens = countTokenGroups(byToken.values(), StudentRecommendationEventService.EVENT_CLICKED);
        long enteredTokens = countTokenGroups(byToken.values(), StudentRecommendationEventService.EVENT_ENTERED_PROBLEM);
        long submittedTokens = countTokenGroups(byToken.values(), StudentRecommendationEventService.EVENT_SUBMITTED);
        long clickedWithoutSubmission = byToken.values()
                .stream()
                .filter(group -> containsEvent(group, StudentRecommendationEventService.EVENT_CLICKED)
                        || containsEvent(group, StudentRecommendationEventService.EVENT_ENTERED_PROBLEM))
                .filter(group -> !containsEvent(group, StudentRecommendationEventService.EVENT_SUBMITTED))
                .count();
        return new Metrics(
                byToken.size(),
                exposureCount,
                clickCount,
                enteredProblemCount,
                submissions.size(),
                accepted,
                sameFocus,
                clickedWithoutSubmission,
                unresolvedLearningSignals,
                teacherInterventionRecommended,
                exposedTokens,
                clickedTokens,
                enteredTokens,
                submittedTokens
        );
    }

    private long countEvents(List<StudentRecommendationEvent> events, String eventType) {
        return events.stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .count();
    }

    private long countTokenGroups(Collection<List<StudentRecommendationEvent>> groups, String eventType) {
        return groups.stream()
                .filter(group -> containsEvent(group, eventType))
                .count();
    }

    private boolean containsEvent(List<StudentRecommendationEvent> group, String eventType) {
        return group.stream().anyMatch(event -> eventType.equals(event.getEventType()));
    }

    private boolean needsTeacherIntervention(List<StudentRecommendationEvent> group) {
        if (group == null || group.isEmpty()) {
            return false;
        }
        boolean highRisk = group.stream().anyMatch(event -> "HIGH".equals(event.getRiskLevel()));
        boolean unresolved = group.stream()
                .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                .filter(event -> !Submission.Verdict.ACCEPTED.name().equals(event.getFollowupVerdict()))
                .anyMatch(this::sameFocusIssue);
        return highRisk && (unresolved || containsEvent(group, StudentRecommendationEventService.EVENT_SUBMITTED));
    }

    private boolean sameFocusIssue(StudentRecommendationEvent event) {
        Set<String> tags = focusTags(event).stream()
                .map(this::normalizeTag)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (tags.isEmpty()) {
            return false;
        }
        return tags.contains(normalizeTag(event.getFollowupFineGrainedTag()))
                || tags.contains(normalizeTag(event.getFollowupIssueTag()));
    }

    private Set<String> focusTags(StudentRecommendationEvent event) {
        if (event == null || event.getFocusTags() == null || event.getFocusTags().isBlank()) {
            return Set.of();
        }
        try {
            return objectMapper.readValue(event.getFocusTags(), STRING_LIST)
                    .stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception ignored) {
            return List.of(event.getFocusTags().split("[,;\\s]+"))
                    .stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private List<RecommendationEffectivenessResponse.FeedbackSignal> feedbackSignals(
            List<StudentRecommendationEvent> events,
            List<RecommendationEffectivenessResponse.ActionEvidenceSignal> actionEvidenceSignals) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<RecommendationEffectivenessResponse.ActionEvidenceSignal> safeSignals =
                actionEvidenceSignals == null ? List.of() : actionEvidenceSignals;
        List<String> unresolvedTokens = safeSignals.stream()
                .filter(actionEvidenceAnalyzer::isUnresolved)
                .map(RecommendationEffectivenessResponse.ActionEvidenceSignal::getRecommendationToken)
                .filter(token -> token != null && !token.isBlank())
                .toList();
        List<String> clickedWithoutSubmissionTokens = safeSignals.stream()
                .filter(signal -> RecommendationActionEvidenceAnalyzer.OUTCOME_NO_FOLLOWUP_SUBMISSION.equals(signal.getOutcome()))
                .map(RecommendationEffectivenessResponse.ActionEvidenceSignal::getRecommendationToken)
                .filter(token -> token != null && !token.isBlank())
                .toList();
        if (unresolvedTokens.isEmpty() && clickedWithoutSubmissionTokens.isEmpty()) {
            Map<String, List<StudentRecommendationEvent>> byToken = events.stream()
                    .collect(Collectors.groupingBy(StudentRecommendationEvent::getRecommendationToken, LinkedHashMap::new, Collectors.toList()));
            unresolvedTokens = byToken.entrySet()
                .stream()
                .filter(entry -> entry.getValue().stream()
                        .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                        .filter(event -> !Submission.Verdict.ACCEPTED.name().equals(event.getFollowupVerdict()))
                        .anyMatch(this::sameFocusIssue))
                .map(Map.Entry::getKey)
                .toList();
            clickedWithoutSubmissionTokens = byToken.entrySet()
                .stream()
                .filter(entry -> containsEvent(entry.getValue(), StudentRecommendationEventService.EVENT_CLICKED)
                        || containsEvent(entry.getValue(), StudentRecommendationEventService.EVENT_ENTERED_PROBLEM))
                .filter(entry -> !containsEvent(entry.getValue(), StudentRecommendationEventService.EVENT_SUBMITTED))
                .map(Map.Entry::getKey)
                .toList();
        }
        List<RecommendationEffectivenessResponse.FeedbackSignal> signals = new ArrayList<>();
        if (!unresolvedTokens.isEmpty()) {
            signals.add(RecommendationEffectivenessResponse.FeedbackSignal.builder()
                    .signal("UNRESOLVED_SAME_FOCUS")
                    .strategy(dominantStrategy(events, unresolvedTokens))
                    .severity("HIGH")
                    .evidenceCount(unresolvedTokens.size())
                    .summary("推荐后仍有 " + unresolvedTokens.size() + " 个推荐命中同类错因。")
                    .recommendedAction("下一轮先降级为最小样例复盘；高风险学生建议教师介入。")
                    .evidenceTokens(unresolvedTokens.stream().limit(5).toList())
                    .build());
        }
        if (!clickedWithoutSubmissionTokens.isEmpty()) {
            signals.add(RecommendationEffectivenessResponse.FeedbackSignal.builder()
                    .signal("CLICKED_WITHOUT_SUBMISSION")
                    .strategy(dominantStrategy(events, clickedWithoutSubmissionTokens))
                    .severity(unresolvedTokens.isEmpty() ? "WATCH" : "MEDIUM")
                    .evidenceCount(clickedWithoutSubmissionTokens.size())
                    .summary("有 " + clickedWithoutSubmissionTokens.size() + " 个推荐被点击或进入题目但没有提交。")
                    .recommendedAction("先判断学生是否卡在读题或起步阶段，必要时改成更小复盘任务。")
                    .evidenceTokens(clickedWithoutSubmissionTokens.stream().limit(5).toList())
                    .build());
        }
        return signals.stream().limit(5).toList();
    }

    private String dominantStrategy(List<StudentRecommendationEvent> events, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "UNKNOWN";
        }
        Set<String> tokenSet = new LinkedHashSet<>(tokens);
        return events.stream()
                .filter(event -> tokenSet.contains(event.getRecommendationToken()))
                .map(StudentRecommendationEvent::getStrategy)
                .map(this::blankToUnknown)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }

    private String summary(Metrics metrics) {
        if (metrics.uniqueRecommendationCount == 0) {
            return "还没有推荐使用数据。先让学生看到并点击推荐，系统才能形成效果证据。";
        }
        if (metrics.unresolvedLearningSignalCount > 0) {
            return "推荐后仍有 " + metrics.unresolvedLearningSignalCount + " 次命中同类错因，下一轮应降级复盘或安排教师介入。";
        }
        if (metrics.acceptedFollowupCount > 0) {
            return "推荐后已有 " + metrics.acceptedFollowupCount + " 次后续提交通过。这是观察性信号，建议继续看错因是否能迁移到新题。";
        }
        if (metrics.sameFocusIssueCount > 0) {
            return "推荐后仍有 " + metrics.sameFocusIssueCount + " 次命中同类错因，说明推荐可能需要更明确的复盘动作或更小的练习台阶。";
        }
        if (metrics.followupSubmissionCount > 0) {
            return "推荐后已有后续提交，但暂未观察到通过。先看后续诊断标签是否从原错因转移。";
        }
        if (metrics.clickedWithoutSubmissionCount > 0) {
            return "已有 " + metrics.clickedWithoutSubmissionCount + " 个推荐被点击或进入题目但尚未提交，建议关注学生是否卡在读题或起步阶段。";
        }
        return "推荐已经曝光，但还缺少点击和提交证据。当前只能判断触达，不能判断学习效果。";
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "REDO" -> "重做";
            case "NEXT_PROBLEM" -> "下一题";
            case "REVIEW" -> "复盘";
            default -> type;
        };
    }

    private String strategyLabel(String strategy) {
        return switch (strategy) {
            case "REPAIR_SAME_PROBLEM" -> "同题修复验证";
            case "TRANSFER_TO_NEW_PROBLEM" -> "同类迁移练习";
            case "REFLECTION_EVIDENCE" -> "证据复盘";
            case "STEP_DOWN_REVIEW" -> "降级复盘";
            case "UNKNOWN" -> "未知策略";
            default -> strategy;
        };
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private String normalizeTag(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record Metrics(long uniqueRecommendationCount,
                           long exposureCount,
                           long clickCount,
                           long enteredProblemCount,
                           long followupSubmissionCount,
                           long acceptedFollowupCount,
                           long sameFocusIssueCount,
                           long clickedWithoutSubmissionCount,
                           long unresolvedLearningSignalCount,
                           long teacherInterventionRecommendedCount,
                           long exposedTokenCount,
                           long clickedTokenCount,
                           long enteredTokenCount,
                           long submittedTokenCount) {
        double clickThroughRate() {
            return AiQualityMetrics.rate(clickedTokenCount, exposedTokenCount);
        }

        double followupSubmissionRate() {
            long denominator = Math.max(clickedTokenCount, enteredTokenCount);
            return AiQualityMetrics.rate(submittedTokenCount, denominator == 0 ? exposedTokenCount : denominator);
        }

        double acceptedFollowupRate() {
            return AiQualityMetrics.rate(acceptedFollowupCount, followupSubmissionCount);
        }

        double sameFocusIssueRate() {
            return AiQualityMetrics.rate(sameFocusIssueCount, followupSubmissionCount);
        }
    }
}
