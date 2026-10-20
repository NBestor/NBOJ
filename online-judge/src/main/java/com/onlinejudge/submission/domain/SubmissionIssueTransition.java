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
        name = "submission_issue_transitions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_submission_issue_transition_key",
                columnNames = "transition_key"
        ),
        indexes = {
                @Index(name = "idx_issue_transition_submission", columnList = "current_submission_id"),
                @Index(name = "idx_issue_transition_student_point", columnList = "student_profile_id,normalized_point_key"),
                @Index(name = "idx_issue_transition_scope", columnList = "student_profile_id,assignment_id,problem_id,current_submission_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionIssueTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transition_key", nullable = false, length = 320)
    private String transitionKey;

    @Column(name = "student_profile_id", nullable = false)
    private Long studentProfileId;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "current_submission_id", nullable = false)
    private Long currentSubmissionId;

    @Column(name = "previous_submission_id")
    private Long previousSubmissionId;

    @Column(name = "current_fact_id")
    private Long currentFactId;

    @Column(name = "previous_fact_id")
    private Long previousFactId;

    @Column(name = "normalized_point_key", nullable = false, length = 220)
    private String normalizedPointKey;

    @Column(name = "point_key_source", length = 32)
    private String pointKeySource;

    @Column(name = "fact_type", length = 32)
    private String factType;

    @Column(name = "display_category", length = 32)
    private String displayCategory;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "transition_type", nullable = false, length = 32)
    private String transitionType;

    @Column(name = "personal_label", length = 48)
    private String personalLabel;

    @Column(name = "raw_occurrence_count", nullable = false)
    private long rawOccurrenceCount;

    @Column(name = "effective_occurrence_count", nullable = false)
    private long effectiveOccurrenceCount;

    @Column(name = "consecutive_effective_count", nullable = false)
    private long consecutiveEffectiveCount;

    @Column(name = "affected_problem_count", nullable = false)
    private long affectedProblemCount;

    @Column(name = "effective_attempt", nullable = false)
    private boolean effectiveAttempt;

    @Column(name = "source_fingerprint", length = 80)
    private String sourceFingerprint;

    @Column(name = "first_seen_submission_id")
    private Long firstSeenSubmissionId;

    @Column(name = "last_seen_submission_id")
    private Long lastSeenSubmissionId;

    @Column(name = "evidence_submission_ids_json", columnDefinition = "TEXT")
    private String evidenceSubmissionIdsJson;

    @Column(name = "projection_version", nullable = false, length = 24)
    private String projectionVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (projectionVersion == null || projectionVersion.isBlank()) {
            projectionVersion = "issue-lifecycle-v1";
        }
    }
}
