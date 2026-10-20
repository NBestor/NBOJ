package com.onlinejudge.submission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "student_ai_feedback_revisions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_feedback_revision_generation", columnNames = {"submission_id", "generation_key"})
        },
        indexes = {
                @Index(name = "idx_feedback_revision_submission", columnList = "submission_id,version_number"),
                @Index(name = "idx_feedback_revision_status", columnList = "status,generated_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAiFeedbackRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "feedback_id")
    private Long feedbackId;

    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "diagnosis_run_id")
    private Long diagnosisRunId;

    @Column(name = "diagnosis_run_version")
    private Integer diagnosisRunVersion;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "generation_key", nullable = false, length = 64)
    private String generationKey;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "feedback_json", columnDefinition = "TEXT")
    private String feedbackJson;

    @Column(name = "analysis_json", columnDefinition = "TEXT")
    private String analysisJson;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(length = 120)
    private String provider;

    @Column(length = 180)
    private String model;

    @Column(name = "prompt_version", length = 120)
    private String promptVersion;

    @Column(name = "schema_version", length = 120)
    private String schemaVersion;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
    }
}
