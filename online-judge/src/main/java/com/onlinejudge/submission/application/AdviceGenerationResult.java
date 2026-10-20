package com.onlinejudge.submission.application;

import lombok.Builder;

@Builder
public record AdviceGenerationResult(
        boolean enabled,
        String status,
        String failureReason,
        int basicAdviceCount,
        int improvementAdviceCount,
        String promptVersion,
        AdviceGenerationOutput output
) {
    public static AdviceGenerationResult disabled() {
        return AdviceGenerationResult.builder()
                .enabled(false)
                .status("DISABLED")
                .failureReason("")
                .promptVersion("")
                .build();
    }

    public static AdviceGenerationResult failed(String reason, String promptVersion) {
        return AdviceGenerationResult.builder()
                .enabled(true)
                .status("FAILED")
                .failureReason(reason == null ? "" : reason)
                .promptVersion(promptVersion == null ? "" : promptVersion)
                .build();
    }

    public static AdviceGenerationResult success(AdviceGenerationOutput output, String promptVersion) {
        return AdviceGenerationResult.builder()
                .enabled(true)
                .status("SUCCESS")
                .failureReason("")
                .basicAdviceCount(output == null || output.getBasicLayerAdvice() == null
                        ? 0
                        : output.getBasicLayerAdvice().size())
                .improvementAdviceCount(output == null || output.getImprovementLayerAdvice() == null
                        ? 0
                        : output.getImprovementLayerAdvice().size())
                .promptVersion(promptVersion == null ? "" : promptVersion)
                .output(output)
                .build();
    }
}
