package com.onlinejudge.identity.dto;

import java.util.UUID;

public record OwnershipTransferResponse(UUID sourceTeacherId, UUID targetTeacherId,
                                        int classCount, int assignmentCount, int problemCount) { }
