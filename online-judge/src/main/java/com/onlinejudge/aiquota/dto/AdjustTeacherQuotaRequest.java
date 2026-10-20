package com.onlinejudge.aiquota.dto;

import jakarta.validation.constraints.Min;

public record AdjustTeacherQuotaRequest(@Min(0) int baseUnits, @Min(0) int additionalUnits) { }
