package com.onlinejudge.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDiagnosisProgressResponse {
    private Long runId;
    private Integer versionNumber;
    private String status;
    private String stage;
    private Integer completedStages;
    private Integer totalStages;
    private boolean retrying;
    private boolean resultAvailable;
    private LocalDateTime updatedAt;
}
