package com.onlinejudge.aiquota.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.aiquota.application.AiProviderGateway.AiProviderRequest;
import com.onlinejudge.aiquota.application.AiProviderGateway.AiProviderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.onlinejudge.system.application.TrialMetrics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ModelScopeAiProviderGateway implements AiProviderGateway {
    private final AiQuotaService quotaService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Autowired(required = false)
    private TrialMetrics trialMetrics;

    @Value("${ai.base-url:https://api-inference.modelscope.cn/v1}")
    private String baseUrl;
    @Value("${ai.api-key:}")
    private String apiKey;
    @Value("${ai.timeout-seconds:30}")
    private long timeoutSeconds;
    @Value("${ai.enabled:true}")
    private boolean enabled;

    @Override
    public String providerName() { return "ModelScope"; }

    @Override
    public String baseUrl() { return baseUrl; }

    @Override
    public boolean available() { return enabled && apiKey != null && !apiKey.isBlank(); }

    @Override
    public AiProviderResponse invoke(AiInvocationContext context, AiProviderRequest request)
            throws IOException, InterruptedException {
        if (!available()) throw new IOException("AI_PROVIDER_UNAVAILABLE");
        long startedAt = System.nanoTime();
        AiQuotaService.Reservation reservation = quotaService.reserve(context);
        try {
            AiProviderResponse response = send(request);
            TokenUsage usage = tokenUsage(response.responseBody(), request.stream());
            quotaService.settleSuccess(reservation, providerName(), request.model(), usage.input(), usage.output());
            if (trialMetrics != null) trialMetrics.aiCompleted(true, System.nanoTime() - startedAt);
            return response;
        } catch (IOException | InterruptedException | RuntimeException failure) {
            quotaService.settleFailure(reservation, providerName(), request.model(), failure.getMessage());
            if (trialMetrics != null) trialMetrics.aiCompleted(false, System.nanoTime() - startedAt);
            throw failure;
        }
    }

    @Override
    public AiProviderResponse healthCheck(AiProviderRequest request) throws IOException, InterruptedException {
        if (!available()) throw new IOException("AI_PROVIDER_UNAVAILABLE");
        return send(request);
    }

    private AiProviderResponse send(AiProviderRequest request) throws IOException, InterruptedException {
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 5)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(request.requestBody(), StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("AI API returned status " + response.statusCode() + ": " + response.body());
        }
        return new AiProviderResponse(response.statusCode(), response.body());
    }

    private TokenUsage tokenUsage(String body, boolean stream) {
        Integer input = null;
        Integer output = null;
        try {
            if (stream) {
                for (String line : body.replace("\r\n", "\n").split("\n")) {
                    String payload = line.trim().startsWith("data:") ? line.trim().substring(5).trim() : line.trim();
                    if (!payload.startsWith("{")) continue;
                    TokenUsage usage = usageNode(objectMapper.readTree(payload).path("usage"));
                    if (usage.input() != null) input = usage.input();
                    if (usage.output() != null) output = usage.output();
                }
            } else {
                return usageNode(objectMapper.readTree(body).path("usage"));
            }
        } catch (Exception ignored) {
            // Provider usage metadata is optional; a successful response still charges one classroom unit.
        }
        return new TokenUsage(input, output);
    }

    private TokenUsage usageNode(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) return new TokenUsage(null, null);
        return new TokenUsage(integer(usage, "prompt_tokens", "input_tokens"),
                integer(usage, "completion_tokens", "output_tokens"));
    }

    private Integer integer(JsonNode node, String primary, String fallback) {
        JsonNode value = node.path(primary);
        if (!value.isNumber()) value = node.path(fallback);
        return value.isNumber() ? value.asInt() : null;
    }

    private record TokenUsage(Integer input, Integer output) { }
}
