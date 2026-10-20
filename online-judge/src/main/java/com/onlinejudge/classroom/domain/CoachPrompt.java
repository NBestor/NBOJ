package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "coach_prompts",
        indexes = {
                @Index(name = "idx_coach_prompts_submission", columnList = "submission_id, created_at"),
                @Index(name = "idx_coach_prompts_assignment_student", columnList = "assignment_id, student_profile_id, created_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "student_profile_id")
    private Long studentProfileId;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "parent_prompt_id")
    private Long parentPromptId;

    @Column(name = "turn_index")
    private Integer turnIndex;

    @Column(name = "hint_policy", nullable = false)
    private String hintPolicy;

    @Column(name = "prompt_type", nullable = false)
    private String promptType;

    @Column(name = "model_failure_reason")
    private String modelFailureReason;

    @Column(name = "model_answer_leak_risk")
    private String modelAnswerLeakRisk;

    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(name = "student_answer", columnDefinition = "TEXT")
    private String studentAnswer;

    @Column(name = "coach_feedback", columnDefinition = "TEXT")
    private String coachFeedback;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "context_summary", columnDefinition = "TEXT")
    private String contextSummary;

    @Column(name = "evidence_refs", columnDefinition = "TEXT")
    private String evidenceRefs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
