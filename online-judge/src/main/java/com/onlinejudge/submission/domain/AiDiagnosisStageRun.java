package com.onlinejudge.submission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_diagnosis_stage_runs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_diagnosis_stage_key", columnNames = {"run_id", "stage_key"})
        },
        indexes = {
                @Index(name = "idx_ai_diagnosis_stage_run", columnList = "run_id,status"),
                @Index(name = "idx_ai_diagnosis_stage_type", columnList = "stage_type,status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDiagnosisStageRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "stage_key", nullable = false, length = 160)
    private String stageKey;

    @Column(name = "stage_type", nullable = false, length = 64)
    private String stageType;

    @Column(name = "issue_id", length = 120)
    private String issueId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "input_fingerprint", length = 96)
    private String inputFingerprint;

    @Column(name = "output_json", columnDefinition = "TEXT")
    private String outputJson;

    @Column(length = 120)
    private String provider;

    @Column(length = 180)
    private String model;

    @Column(name = "prompt_version", length = 120)
    private String promptVersion;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
