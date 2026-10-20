package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportCommitResponse {
    private String importType;
    private int createdCount;
    private int updatedCount;
    private int skippedCount;
    private int failedCount;
    private String message;
    private List<Long> createdIds;
    private List<ImportPreviewResponse.RowIssue> issues;
}
