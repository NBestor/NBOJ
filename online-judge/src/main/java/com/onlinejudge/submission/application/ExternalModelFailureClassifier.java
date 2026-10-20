package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

@Component
public class ExternalModelFailureClassifier {

    public ModelStageFailureReason classify(Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            return ModelStageFailureReason.TIMEOUT;
        }
        if (throwable instanceof JsonProcessingException) {
            return ModelStageFailureReason.INVALID_JSON;
        }
        String text = throwable == null ? "" : throwable.getClass().getName() + " " + throwable.getMessage();
        return classify(text, throwable instanceof IOException);
    }

    public ModelStageFailureReason classify(String text) {
        return classify(text, false);
    }

    public boolean isRetryable(ModelStageFailureReason reason, String text) {
        if (reason == ModelStageFailureReason.INSUFFICIENT_QUOTA
                || reason == ModelStageFailureReason.AUTHENTICATION_FAILED
                || reason == ModelStageFailureReason.MODEL_UNSUPPORTED) {
            return false;
        }
        String normalized = normalize(text);
        return reason == ModelStageFailureReason.RATE_LIMITED
                || reason == ModelStageFailureReason.TIMEOUT
                || normalized.contains("did not include message content");
    }

    private ModelStageFailureReason classify(String text, boolean ioFailure) {
        String normalized = normalize(text);
        if (normalized.contains("timeout") || normalized.contains("timed out")) {
            return ModelStageFailureReason.TIMEOUT;
        }
        if (normalized.contains("insufficient_quota")
                || normalized.contains("exceeded your current quota")
                || normalized.contains("current quota")
                || normalized.contains("token-limit")) {
            return ModelStageFailureReason.INSUFFICIENT_QUOTA;
        }
        if (normalized.contains("status 401")
                || normalized.contains("authentication failed")
                || normalized.contains("invalid api key")
                || normalized.contains("invalid token")) {
            return ModelStageFailureReason.AUTHENTICATION_FAILED;
        }
        if (normalized.contains("status 429")
                || normalized.contains("\"429\"")
                || normalized.contains("rate limit")
                || normalized.contains("rate_limit")
                || normalized.contains("too many requests")) {
            return ModelStageFailureReason.RATE_LIMITED;
        }
        if (normalized.contains("has no provider supported")
                || normalized.contains("model unsupported")
                || normalized.contains("unsupported model")) {
            return ModelStageFailureReason.MODEL_UNSUPPORTED;
        }
        if (normalized.contains("output_truncated")
                || normalized.contains("finish_reason=length")
                || normalized.contains("finish reason length")
                || normalized.contains("streamfinishreason=length")) {
            return ModelStageFailureReason.OUTPUT_TRUNCATED;
        }
        if (normalized.contains("json")) {
            return ModelStageFailureReason.INVALID_JSON;
        }
        return ioFailure ? ModelStageFailureReason.API_ERROR : ModelStageFailureReason.UNKNOWN_ERROR;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
