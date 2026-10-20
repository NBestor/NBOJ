package com.onlinejudge.system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiSmokeResponse {
    private String status;
    private String provider;
    private String model;
    private String failureReason;
    private String message;
    private Long latencyMs;
    private LocalDateTime checkedAt;
}
