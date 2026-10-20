package com.onlinejudge.submission.application;

public enum ModelStageFailureReason {
    NONE,
    TIMEOUT,
    INSUFFICIENT_QUOTA,
    RATE_LIMITED,
    AUTHENTICATION_FAILED,
    MODEL_UNSUPPORTED,
    OUTPUT_TRUNCATED,
    EMPTY_RESPONSE,
    INVALID_JSON,
    INVALID_TAG,
    INVALID_EVIDENCE_REF,
    SAFETY_RISK,
    API_ERROR,
    UNKNOWN_ERROR
}
