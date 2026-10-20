package com.onlinejudge.system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReadinessResponse {
    private String status;
    private LocalDateTime updatedAt;
    private List<Check> checks;

    @Data
    @Builder
    public static class Check {
        private String id;
        private String label;
        private String status;
        private boolean blocking;
        private String message;
        private String action;
    }
}
