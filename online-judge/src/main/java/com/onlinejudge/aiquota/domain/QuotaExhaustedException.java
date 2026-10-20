package com.onlinejudge.aiquota.domain;

public class QuotaExhaustedException extends RuntimeException {
    public QuotaExhaustedException() {
        super("QUOTA_EXHAUSTED");
    }
}
