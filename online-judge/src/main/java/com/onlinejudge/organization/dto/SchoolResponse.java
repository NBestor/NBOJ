package com.onlinejudge.organization.dto;

import com.onlinejudge.organization.domain.School;

import java.time.Instant;
import java.util.UUID;

public record SchoolResponse(UUID id, String name, School.Status status, UUID adminAccountId,
                             int monthlyAiUnits, int allocatedAiUnits, int usedAiUnits,
                             Instant createdAt) { }
