package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "class_review_feedback",
        indexes = {
                @Index(name = "idx_class_review_feedback_assignment", columnList = "assignment_id, created_at"),
                @Index(name = "idx_class_review_feedback_key", columnList = "assignment_id, suggestion_key")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassReviewFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "suggestion_key", nullable = false)
    private String suggestionKey;

    @Column(name = "target_ability")
    private String targetAbility;

    @Column(name = "example_problem_id")
    private Long exampleProblemId;

    @Column(name = "evidence_tags", columnDefinition = "TEXT")
    private String evidenceTags;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "teacher_note", columnDefinition = "TEXT")
    private String teacherNote;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
