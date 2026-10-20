package com.onlinejudge.execution;

public record CodeExecutionRequest(String sourceCode,
                                   int languageId,
                                   String stdin,
                                   int timeLimitMs,
                                   int memoryLimitKb,
                                   int maxOutputBytes) {

    public CodeExecutionRequest {
        sourceCode = sourceCode == null ? "" : sourceCode;
        stdin = stdin == null ? "" : stdin;
        timeLimitMs = Math.max(timeLimitMs, 100);
        memoryLimitKb = Math.max(memoryLimitKb, 1024);
        maxOutputBytes = Math.max(maxOutputBytes, 1024);
    }
}
