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
        name = "ai_diagnosis_runs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_diagnosis_run_generation", columnNames = "generation_key"),
                @UniqueConstraint(name = "uk_ai_diagnosis_run_version", columnNames = {"submission_id", "version_number"})
        },
        indexes = {
                @Index(name = "idx_ai_diagnosis_run_submission", columnList = "submission_id,version_number"),
                @Index(name = "idx_ai_diagnosis_run_status", columnList = "status,updated_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDiagnosisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "generation_key", nullable = false, length = 64)
    private String generationKey;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "current_stage", length = 96)
    private String currentStage;

    @Column(name = "official_version", nullable = false)
    private boolean officialVersion;

    @Column(name = "result_saved", nullable = false)
    private boolean resultSaved;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

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
        if (startedAt == null) {
            startedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
