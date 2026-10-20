package com.onlinejudge.submission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "submission_evidence_backfill_batches",
        indexes = @Index(name = "idx_evidence_backfill_started", columnList = "started_at")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionEvidenceBackfillBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_key", nullable = false, unique = true, length = 64)
    private String batchKey;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "cursor_start")
    private Long cursorStart;

    @Column(name = "cursor_end")
    private Long cursorEnd;

    @Column(name = "processed_count", nullable = false)
    private long processedCount;

    @Column(name = "success_count", nullable = false)
    private long successCount;

    @Column(name = "skipped_count", nullable = false)
    private long skippedCount;

    @Column(name = "failed_count", nullable = false)
    private long failedCount;

    @Column(name = "error_json", columnDefinition = "TEXT")
    private String errorJson;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
