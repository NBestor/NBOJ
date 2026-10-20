package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "student_recommendation_events",
        indexes = {
                @Index(name = "idx_reco_events_student", columnList = "student_profile_id, created_at"),
                @Index(name = "idx_reco_events_assignment", columnList = "assignment_id, created_at"),
                @Index(name = "idx_reco_events_token", columnList = "recommendation_token"),
                @Index(name = "idx_reco_events_submission", columnList = "followup_submission_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRecommendationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation_token", nullable = false)
    private String recommendationToken;

    @Column(name = "student_profile_id", nullable = false)
    private Long studentProfileId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "problem_id")
    private Long problemId;

    @Column(name = "focus_ability")
    private String focusAbility;

    @Column(name = "focus_tags", columnDefinition = "TEXT")
    private String focusTags;

    @Column(name = "source_submission_id")
    private Long sourceSubmissionId;

    @Column(name = "focus_issue_ids", columnDefinition = "TEXT")
    private String focusIssueIds;

    @Column(name = "focus_point_keys", columnDefinition = "TEXT")
    private String focusPointKeys;

    @Column(name = "focus_knowledge_node_codes", columnDefinition = "TEXT")
    private String focusKnowledgeNodeCodes;

    @Column(name = "focus_skill_unit_codes", columnDefinition = "TEXT")
    private String focusSkillUnitCodes;

    @Column(name = "focus_mistake_point_codes", columnDefinition = "TEXT")
    private String focusMistakePointCodes;

    @Column(name = "focus_test_semantic_codes", columnDefinition = "TEXT")
    private String focusTestSemanticCodes;

    @Column(name = "strategy")
    private String strategy;

    @Column(name = "learning_hypothesis", columnDefinition = "TEXT")
    private String learningHypothesis;

    @Column(name = "expected_completion_signal", columnDefinition = "TEXT")
    private String expectedCompletionSignal;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "fallback_action", columnDefinition = "TEXT")
    private String fallbackAction;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "followup_submission_id")
    private Long followupSubmissionId;

    @Column(name = "followup_verdict")
    private String followupVerdict;

    @Column(name = "followup_issue_tag")
    private String followupIssueTag;

    @Column(name = "followup_fine_grained_tag")
    private String followupFineGrainedTag;

    @Column(name = "followup_issue_ids", columnDefinition = "TEXT")
    private String followupIssueIds;

    @Column(name = "followup_point_keys", columnDefinition = "TEXT")
    private String followupPointKeys;

    @Column(name = "followup_knowledge_node_codes", columnDefinition = "TEXT")
    private String followupKnowledgeNodeCodes;

    @Column(name = "followup_skill_unit_codes", columnDefinition = "TEXT")
    private String followupSkillUnitCodes;

    @Column(name = "followup_mistake_point_codes", columnDefinition = "TEXT")
    private String followupMistakePointCodes;

    @Column(name = "followup_failed_test_semantic_codes", columnDefinition = "TEXT")
    private String followupFailedTestSemanticCodes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
