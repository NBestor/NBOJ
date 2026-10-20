package com.onlinejudge.system.application;

import com.onlinejudge.submission.application.AiReportService;
import com.onlinejudge.submission.application.ExternalModelFailureClassifier;
import com.onlinejudge.system.dto.AiSmokeResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSmokeServiceTest {

    @Test
    void recordsRateLimitFailureForTeacherDiagnosis() throws Exception {
        AiReportService aiReportService = mock(AiReportService.class);
        when(aiReportService.providerName()).thenReturn("ModelScope");
        when(aiReportService.modelName()).thenReturn("test-model");
        when(aiReportService.smokeChatCompletion()).thenThrow(new IOException(
                "AI API returned status 429: {\"error\":{\"message\":\"rate limit\"}}"
        ));

        AiSmokeService service = new AiSmokeService(aiReportService, new ExternalModelFailureClassifier());

        AiSmokeResponse response = service.runSmoke();

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getFailureReason()).isEqualTo("RATE_LIMITED");
        assertThat(response.getMessage()).contains("限流");
        assertThat(service.latest()).isEqualTo(response);
    }
}
