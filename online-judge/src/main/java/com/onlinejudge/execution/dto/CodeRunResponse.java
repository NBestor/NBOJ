package com.onlinejudge.execution.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CodeRunResponse {

    private Status status;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private long executionTimeMs;
    private boolean stdoutTruncated;
    private boolean stderrTruncated;
    private String message;

    public enum Status {
        SUCCESS,
        COMPILATION_ERROR,
        RUNTIME_ERROR,
        TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED,
        ENVIRONMENT_UNAVAILABLE,
        INTERNAL_ERROR
    }
}
