package com.onlinejudge.submission.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SubmissionEvidenceBackfillResponse {
    private String batchKey;
    private boolean dryRun;
    private Long cursorStart;
    private Long nextCursor;
    private boolean hasMore;
    private long processedCount;
    private long successCount;
    private long skippedCount;
    private long failedCount;
    private long identityMissingCount;
    private long analysisMissingCount;
    private long pathUnclassifiedCount;
    private long feedbackVersionMissingCount;
    private long feedbackVersionCreatedCount;
    private long diagnosisFactCreatedCount;
    private long feedbackEventLinkedCount;
    private List<String> failures;
}
