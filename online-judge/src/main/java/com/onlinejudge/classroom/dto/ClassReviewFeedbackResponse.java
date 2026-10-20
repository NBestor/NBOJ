package com.onlinejudge.classroom.dto;

import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ClassReviewFeedbackResponse {
    private Long id;
    private Long assignmentId;
    private String suggestionKey;
    private String targetAbility;
    private Long exampleProblemId;
    private String actionType;
    private String teacherNote;
    private String createdBy;
    private LocalDateTime createdAt;

    public static ClassReviewFeedbackResponse from(ClassReviewFeedback feedback) {
        if (feedback == null) {
            return null;
        }
        return ClassReviewFeedbackResponse.builder()
                .id(feedback.getId())
                .assignmentId(feedback.getAssignmentId())
                .suggestionKey(feedback.getSuggestionKey())
                .targetAbility(feedback.getTargetAbility())
                .exampleProblemId(feedback.getExampleProblemId())
                .actionType(feedback.getActionType())
                .teacherNote(feedback.getTeacherNote())
                .createdBy(feedback.getCreatedBy())
                .createdAt(feedback.getCreatedAt())
                .build();
    }
}
