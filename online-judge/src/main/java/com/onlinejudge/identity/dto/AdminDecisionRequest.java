package com.onlinejudge.identity.dto;

import jakarta.validation.constraints.Size;

public record AdminDecisionRequest(@Size(max = 500) String reason) {
}
