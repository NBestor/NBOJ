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
        name = "submission_diagnosis_facts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_submission_diagnosis_fact_key", columnNames = "fact_key")
        },
        indexes = {
                @Index(name = "idx_diagnosis_fact_submission", columnList = "submission_id"),
                @Index(name = "idx_diagnosis_fact_analysis", columnList = "analysis_id"),
                @Index(name = "idx_diagnosis_fact_path", columnList = "knowledge_path_status,fact_type"),
                @Index(name = "idx_diagnosis_fact_skill", columnList = "skill_unit_id"),
                @Index(name = "idx_diagnosis_fact_mistake", columnList = "mistake_point_id"),
                @Index(name = "idx_diagnosis_fact_provisional", columnList = "provisional_node_code"),
                @Index(name = "idx_diagnosis_fact_normalized_point", columnList = "normalized_point_key,fact_type")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDiagnosisFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Column(name = "fact_key", nullable = false, length = 180)
    private String factKey;

    @Column(name = "issue_id", length = 120)
    private String issueId;

    @Column(name = "fact_type", nullable = false, length = 32)
    private String factType;

    @Column(name = "display_category", length = 32)
    private String displayCategory;

    @Column(name = "normalized_point_key", length = 220)
    private String normalizedPointKey;

    @Column(name = "point_key_source", length = 32)
    private String pointKeySource;

    @Column(name = "point_key_version", length = 24)
    private String pointKeyVersion;

    @Column(name = "primary_issue", nullable = false)
    private boolean primaryIssue;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "skill_unit_id", length = 160)
    private String skillUnitId;

    @Column(name = "mistake_point_id", length = 160)
    private String mistakePointId;

    @Column(name = "improvement_point_id", length = 160)
    private String improvementPointId;

    @Column(name = "provisional_node_code", length = 160)
    private String provisionalNodeCode;

    @Column(name = "knowledge_path_json", columnDefinition = "TEXT")
    private String knowledgePathJson;

    @Column(name = "knowledge_path_status", nullable = false, length = 32)
    private String knowledgePathStatus;

    @Column(name = "library_fit", nullable = false, length = 32)
    private String libraryFit;

    @Column(name = "evidence_refs_json", columnDefinition = "TEXT")
    private String evidenceRefsJson;

    private Double confidence;

    @Column(name = "projection_status", nullable = false, length = 32)
    private String projectionStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (projectionStatus == null || projectionStatus.isBlank()) {
            projectionStatus = "READY";
        }
        if (displayCategory == null || displayCategory.isBlank()) {
            displayCategory = "IMPROVEMENT".equalsIgnoreCase(factType) ? "IMPROVEMENT" : "REPAIR";
        }
    }
}
