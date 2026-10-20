package com.onlinejudge.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateSchoolRequest(
        @NotBlank @Size(max = 200) String schoolName,
        @NotBlank @Size(min = 4, max = 50) String adminUsername,
        @NotBlank @Size(max = 120) String adminDisplayName,
        @PositiveOrZero Integer monthlyAiUnits) { }
