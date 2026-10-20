package com.onlinejudge.submission.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalModelFailureClassifierTest {

    private final ExternalModelFailureClassifier classifier = new ExternalModelFailureClassifier();

    @Test
    void classifiesQuotaAndRateLimitProviderErrors() {
        assertThat(classifier.classify(new IOException("""
                AI API returned status 429: {"error":{"code":"insufficient_quota","message":"You exceeded your current quota"}}
                """))).isEqualTo(ModelStageFailureReason.INSUFFICIENT_QUOTA);

        assertThat(classifier.classify(new IOException("""
                AI API returned status 429: {"error":{"message":"We have to rate limit you for model"}}
                """))).isEqualTo(ModelStageFailureReason.RATE_LIMITED);
    }

    @Test
    void classifiesUnsupportedModelAndAuthenticationFailure() {
        assertThat(classifier.classify(new IOException("model unsupported by provider")))
                .isEqualTo(ModelStageFailureReason.MODEL_UNSUPPORTED);
        assertThat(classifier.classify(new IOException("AI API returned status 401: Authentication failed, invalid token")))
                .isEqualTo(ModelStageFailureReason.AUTHENTICATION_FAILED);
    }

    @Test
    void exposesRetryPolicies() {
        assertThat(classifier.isRetryable(ModelStageFailureReason.INSUFFICIENT_QUOTA, "")).isFalse();
        assertThat(classifier.isRetryable(ModelStageFailureReason.AUTHENTICATION_FAILED, "")).isFalse();
        assertThat(classifier.isRetryable(ModelStageFailureReason.RATE_LIMITED, "status 429")).isTrue();
        assertThat(classifier.isRetryable(ModelStageFailureReason.TIMEOUT, "timeout")).isTrue();
    }
}
