package com.onlinejudge.submission.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExternalModelChatRequestFactory {

    public Map<String, Object> build(String baseUrl,
                                     String compatibleMode,
                                     boolean enableThinking,
                                     String model,
                                     String systemPrompt,
                                     String userPrompt,
                                     boolean stream,
                                     Integer outputTokens) {
        boolean compatible = shouldUseModelScopeCompatibleRequest(baseUrl, compatibleMode);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", compatible
                ? List.of(Map.of("role", "user", "content", mergeSystemIntoUser(systemPrompt, userPrompt)))
                : List.of(
                Map.of("role", "system", "content", safe(systemPrompt)),
                Map.of("role", "user", "content", safe(userPrompt))
        ));
        requestBody.put("stream", stream);
        if (compatible) {
            requestBody.put("enable_thinking", enableThinking);
        }
        if (!compatible) {
            requestBody.put("temperature", 0.2);
        }
        if (outputTokens != null) {
            requestBody.put("max_tokens", Math.max(128, outputTokens));
        }
        return requestBody;
    }

    boolean shouldUseModelScopeCompatibleRequest(String baseUrl, String compatibleMode) {
        String mode = compatibleMode == null ? "auto" : compatibleMode.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(mode) || "on".equals(mode) || "enabled".equals(mode)) {
            return true;
        }
        if ("false".equals(mode) || "off".equals(mode) || "disabled".equals(mode)) {
            return false;
        }
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        return normalizedBaseUrl.contains("api-inference.modelscope.cn")
                || normalizedBaseUrl.contains("modelscope.cn");
    }

    private String mergeSystemIntoUser(String systemPrompt, String userPrompt) {
        String system = safe(systemPrompt).trim();
        String user = safe(userPrompt).trim();
        if (system.isBlank()) {
            return user;
        }
        if (user.isBlank()) {
            return system;
        }
        return "系统指令：\n" + system + "\n\n用户任务：\n" + user;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
