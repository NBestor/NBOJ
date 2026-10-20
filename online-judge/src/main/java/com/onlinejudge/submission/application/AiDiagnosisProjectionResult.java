package com.onlinejudge.submission.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDiagnosisProjectionResult {
    private String projection;
    private String status;
    private Integer affectedRecords;
    private String detail;

    public static AiDiagnosisProjectionResult completed(String projection, int affectedRecords) {
        return AiDiagnosisProjectionResult.builder()
                .projection(projection)
                .status("COMPLETED")
                .affectedRecords(Math.max(0, affectedRecords))
                .detail("")
                .build();
    }
}
