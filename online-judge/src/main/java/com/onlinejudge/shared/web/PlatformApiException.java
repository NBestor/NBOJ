package com.onlinejudge.shared.web;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PlatformApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public PlatformApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
