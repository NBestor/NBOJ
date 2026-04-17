package com.onlinejudge.submission.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "language_id", nullable = false)
    private Integer languageId;

    @Column(name = "language_name")
    private String languageName;

    @Column(name = "source_code", columnDefinition = "TEXT", nullable = false)
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    @Column(name = "execution_time")
    private Double executionTime; // in seconds

    @Column(name = "memory_used")
    private Integer memoryUsed; // in KB

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "compile_output", columnDefinition = "TEXT")
    private String compileOutput;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
    }

    public enum Verdict {
        PENDING,
        ACCEPTED,
        WRONG_ANSWER,
        TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED,
        RUNTIME_ERROR,
        COMPILATION_ERROR,
        INTERNAL_ERROR
    }
}

