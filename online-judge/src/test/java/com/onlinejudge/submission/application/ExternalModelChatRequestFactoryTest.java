package com.onlinejudge.submission.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalModelChatRequestFactoryTest {

    private final ExternalModelChatRequestFactory factory = new ExternalModelChatRequestFactory();

    @Test
    void autoModeUsesModelScopeCompatibleSingleUserMessage() {
        Map<String, Object> request = factory.build(
                "https://api-inference.modelscope.cn/v1",
                "auto",
                false,
                "deepseek-ai/DeepSeek-V4-Pro",
                "Return strict JSON.",
                "Analyze this code.",
                true,
                128
        );

        assertThat(request).doesNotContainKey("temperature");
        assertThat(request).containsEntry("enable_thinking", false);
        assertThat(request).containsEntry("max_tokens", 128);
        assertThat(request.get("stream")).isEqualTo(true);
        List<?> messages = (List<?>) request.get("messages");
        assertThat(messages).hasSize(1);
        Map<?, ?> message = (Map<?, ?>) messages.get(0);
        assertThat(message.get("role")).isEqualTo("user");
        assertThat(message.get("content").toString()).contains("系统指令", "Return strict JSON.", "用户任务", "Analyze this code.");
    }

    @Test
    void explicitThinkingModeUsesSameModelScopeRequestShape() {
        Map<String, Object> request = factory.build(
                "https://api-inference.modelscope.cn/v1",
                "auto",
                true,
                "deepseek-ai/DeepSeek-V4-Pro",
                "Return strict JSON.",
                "Analyze this code.",
                true,
                256
        );

        assertThat(request).containsEntry("enable_thinking", true);
        assertThat(request).doesNotContainKey("temperature");
        assertThat((List<?>) request.get("messages")).hasSize(1);
    }

    @Test
    void falseModeKeepsStandardOpenAiCompatibleShape() {
        Map<String, Object> request = factory.build(
                "https://api-inference.modelscope.cn/v1",
                "false",
                true,
                "deepseek-ai/DeepSeek-V4-Pro",
                "Return strict JSON.",
                "Analyze this code.",
                false,
                64
        );

        assertThat(request).containsEntry("temperature", 0.2);
        assertThat(request).doesNotContainKey("enable_thinking");
        assertThat(request).containsEntry("max_tokens", 128);
        List<?> messages = (List<?>) request.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(((Map<?, ?>) messages.get(0)).get("role")).isEqualTo("system");
        assertThat(((Map<?, ?>) messages.get(1)).get("role")).isEqualTo("user");
    }
}
