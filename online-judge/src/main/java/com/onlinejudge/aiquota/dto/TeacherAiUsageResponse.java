package com.onlinejudge.aiquota.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TeacherAiUsageResponse(
        UUID teacherId,
        String month,
        int baseUnits,
        int additionalUnits,
        int usedUnits,
        int reservedUnits,
        int remainingUnits,
        Instant resetsAt
) { }
