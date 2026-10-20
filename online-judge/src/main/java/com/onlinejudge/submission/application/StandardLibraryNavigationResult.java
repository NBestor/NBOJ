package com.onlinejudge.submission.application;

import lombok.Builder;

@Builder
public record StandardLibraryNavigationResult(
        boolean enabled,
        String status,
        String failureReason,
        Integer selectedCount,
        StandardLibraryPack selectedPack,
        StandardLibraryNavigationOutput output
) {
    public static StandardLibraryNavigationResult disabled() {
        return StandardLibraryNavigationResult.builder()
                .enabled(false)
                .status("DISABLED")
                .failureReason("")
                .selectedCount(0)
                .build();
    }
}
