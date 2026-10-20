package com.onlinejudge.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OwnershipTransferRequest(@NotNull UUID targetTeacherId) { }
