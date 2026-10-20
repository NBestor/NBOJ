package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import com.onlinejudge.classroom.dto.ClassReviewFeedbackRequest;
import com.onlinejudge.classroom.dto.ClassReviewFeedbackResponse;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.ClassReviewFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClassReviewFeedbackService {

    public static final String ACTION_ACCEPTED = "ACCEPTED";
    public static final String ACTION_DISMISSED = "DISMISSED";
    public static final String ACTION_MODIFIED = "MODIFIED";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ClassReviewFeedbackRepository feedbackRepository;
    private final AssignmentRepository assignmentRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ClassReviewFeedbackResponse recordFeedback(Long assignmentId, ClassReviewFeedbackRequest request) {
        assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("作业不存在: " + assignmentId));
        String suggestionKey = normalizeRequired(request.getSuggestionKey(), "复盘建议标识不能为空");
        String actionType = normalizeActionType(request.getActionType());
        ClassReviewFeedback feedback = feedbackRepository.save(ClassReviewFeedback.builder()
                .assignmentId(assignmentId)
                .suggestionKey(suggestionKey)
                .targetAbility(normalizeNullable(request.getTargetAbility()))
                .exampleProblemId(request.getExampleProblemId())
                .evidenceTags(toJson(request.getEvidenceTags()))
                .actionType(actionType)
                .teacherNote(trimToLength(normalizeNullable(request.getTeacherNote()), 600))
                .createdBy(trimToLength(normalizeNullable(request.getCreatedBy()), 80))
                .build());
        return ClassReviewFeedbackResponse.from(feedback);
    }

    public List<ClassReviewFeedback> latestByAssignment(Long assignmentId) {
        if (assignmentId == null) {
            return List.of();
        }
        return feedbackRepository.findByAssignmentIdOrderByCreatedAtDesc(assignmentId);
    }

    public List<String> evidenceTags(ClassReviewFeedback feedback) {
        if (feedback == null || feedback.getEvidenceTags() == null || feedback.getEvidenceTags().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(feedback.getEvidenceTags(), STRING_LIST).stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeActionType(String value) {
        String normalized = normalizeRequired(value, "反馈动作不能为空").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case ACTION_ACCEPTED, ACTION_DISMISSED, ACTION_MODIFIED -> normalized;
            default -> throw new IllegalArgumentException("未知复盘反馈动作: " + value);
        };
    }

    private String toJson(List<String> values) {
        try {
            List<String> normalized = values == null ? List.of() : values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                    .stream()
                    .limit(12)
                    .toList();
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        return value == null ? "" : value.trim();
    }
}
