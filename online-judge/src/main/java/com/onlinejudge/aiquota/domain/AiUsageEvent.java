package com.onlinejudge.aiquota.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_usage_events", indexes = {
        @Index(name = "idx_ai_usage_teacher_month", columnList = "teacher_id,created_at"),
        @Index(name = "idx_ai_usage_idempotency", columnList = "teacher_id,idempotency_key")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;
    @Column(name = "school_id", nullable = false)
    private UUID schoolId;
    @Column(name = "student_profile_id")
    private Long studentProfileId;
    @Column(name = "assignment_id")
    private Long assignmentId;
    @Column(name = "submission_id")
    private Long submissionId;
    @Column(name = "usage_purpose", nullable = false, length = 60)
    private String usagePurpose;
    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;
    @Column(length = 80)
    private String provider;
    @Column(length = 160)
    private String model;
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;
    @Column(nullable = false)
    private boolean success;
    @Column(nullable = false)
    private boolean charged;
    @Column(name = "input_tokens")
    private Integer inputTokens;
    @Column(name = "output_tokens")
    private Integer outputTokens;
    @Column(name = "quota_units", nullable = false)
    private int quotaUnits;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = Instant.now();
        if (attemptNo < 1) attemptNo = 1;
    }
}
