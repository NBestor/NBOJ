package com.onlinejudge.submission.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class ExternalModelStagePayloads {

    private ExternalModelStagePayloads() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageValidationResult {
        private boolean valid;
        private String stage;
        private ModelStageFailureReason failureReason;
        private String message;
        private List<String> softFixes;
        private List<String> hardFailures;
    }
}
