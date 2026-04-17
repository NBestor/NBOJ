package com.onlinejudge.submission.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "submission_case_results")
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
}

