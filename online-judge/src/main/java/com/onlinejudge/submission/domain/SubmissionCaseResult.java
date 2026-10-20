package com.onlinejudge.submission.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "submission_case_results",
        indexes = {
                @Index(name = "idx_case_results_submission", columnList = "submission_id"),
                @Index(name = "idx_case_results_submission_case", columnList = "submission_id, test_case_number")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionCaseResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "test_case_number", nullable = false)
    private Integer testCaseNumber;

    @Column(name = "test_case_id")
    private Long testCaseId;

    @Column(nullable = false)
    private Boolean passed;

    @Column(name = "input_snapshot", columnDefinition = "TEXT")
    private String inputSnapshot;

    @Column(name = "actual_output", columnDefinition = "TEXT")
    private String actualOutput;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(name = "execution_time")
    private Double executionTime;

    @Column(name = "memory_used")
    private Integer memoryUsed;

    @Column(name = "is_hidden", nullable = false)
    private Boolean hidden;

    @Column(name = "test_semantic_code", length = 160)
    private String testSemanticCode;

    @Column(name = "test_intent_type", length = 40)
    private String testIntentType;

    @Column(name = "test_intent_title", length = 160)
    private String testIntentTitle;

    @Column(name = "test_intent_summary", length = 800)
    private String testIntentSummary;

    @Column(name = "test_learning_objective", length = 800)
    private String testLearningObjective;

    @Column(name = "test_contest_role", length = 40)
    private String testContestRole;

    @Column(name = "test_reveal_policy", length = 40)
    private String testRevealPolicy;
}
