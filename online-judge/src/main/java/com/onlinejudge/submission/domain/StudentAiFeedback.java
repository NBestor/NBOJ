package com.onlinejudge.submission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_ai_feedbacks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAiFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false, unique = true)
    private Long submissionId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String source;

    @Column(name = "feedback_json", columnDefinition = "TEXT")
    private String feedbackJson;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "generation_key", length = 64)
    private String generationKey;

    @Column(name = "latest_revision_id")
    private Long latestRevisionId;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @PrePersist
    @PreUpdate
    protected void touchGeneratedAt() {
        generatedAt = LocalDateTime.now();
    }
}
