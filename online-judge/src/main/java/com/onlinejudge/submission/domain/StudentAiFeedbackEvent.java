package com.onlinejudge.submission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "student_ai_feedback_events",
        indexes = {
                @Index(name = "idx_student_ai_feedback_event_submission", columnList = "submission_id,event_type,created_at"),
                @Index(name = "idx_student_ai_feedback_event_student_problem", columnList = "student_profile_id,problem_id,created_at"),
                @Index(name = "idx_student_ai_feedback_event_assignment", columnList = "assignment_id,created_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAiFeedbackEvent {

    public static final String EVENT_READY = "AI_FEEDBACK_READY";
    public static final String EVENT_FAILED = "AI_FEEDBACK_FAILED";
    public static final String EVENT_VIEWED = "AI_FEEDBACK_VIEWED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "feedback_revision_id")
    private Long feedbackRevisionId;

    @Column(name = "student_profile_id")
    private Long studentProfileId;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "problem_id")
    private Long problemId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "feedback_status")
    private String feedbackStatus;

    @Column(name = "feedback_source")
    private String feedbackSource;

    @Column(name = "answer_leak_risk")
    private String answerLeakRisk;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
