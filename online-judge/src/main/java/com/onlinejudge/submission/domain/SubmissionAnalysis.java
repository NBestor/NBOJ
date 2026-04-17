package com.onlinejudge.submission.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "submission_analyses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false, unique = true)
    private Long submissionId;

    @Column(name = "analysis_source", nullable = false)
    private String analysisSource;

    @Column(nullable = false)
    private String scenario;

    @Column(nullable = false)
    private String headline;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "report_markdown", columnDefinition = "TEXT", nullable = false)
    private String reportMarkdown;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    @PreUpdate
    protected void updateGeneratedAt() {
        generatedAt = LocalDateTime.now();
    }
}

