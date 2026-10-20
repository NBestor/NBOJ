package com.onlinejudge.aiquota.application;

import java.io.IOException;

public interface AiProviderGateway {
    String providerName();
    String baseUrl();
    boolean available();
    AiProviderResponse invoke(AiInvocationContext context, AiProviderRequest request)
            throws IOException, InterruptedException;
    default AiProviderResponse healthCheck(AiProviderRequest request) throws IOException, InterruptedException {
        return invoke(AiInvocationContext.anonymous("PROVIDER_SMOKE", "provider-smoke"), request);
    }

    record AiProviderRequest(String requestBody, boolean stream, String model) { }
    record AiProviderResponse(int statusCode, String responseBody) { }
}
