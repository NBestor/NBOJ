package com.onlinejudge.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutorStatusResponse {
    private String mode;
    private String executorType;
    private boolean dockerAvailable;
    private boolean pythonAvailable;
    private boolean cppAvailable;
    private boolean cpp17Available;
    private String message;
    private String projectOwner;
    private String ownershipSignature;
    private String ownershipClaim;
}
