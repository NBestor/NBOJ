package com.onlinejudge.execution.application;

public class CodeRunLimitException extends RuntimeException {

    public CodeRunLimitException(String message) {
        super(message);
    }
}
