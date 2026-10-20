package com.onlinejudge.execution.application;

import com.onlinejudge.execution.config.CodeRunProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeRunAdmissionServiceTest {

    @Test
    void limitsRateAndAllowsTheKeyAgainAfterTheWindow() {
        CodeRunProperties properties = properties(2, 1, 2);
        AtomicLong now = new AtomicLong(1_000L);
        CodeRunAdmissionService service = new CodeRunAdmissionService(properties, now::get);

        service.acquire("student:41").close();
        service.acquire("student:41").close();

        assertThatThrownBy(() -> service.acquire("student:41"))
                .isInstanceOf(CodeRunLimitException.class)
                .hasMessageContaining("频繁");

        now.addAndGet(60_001L);
        service.acquire("student:41").close();
    }

    @Test
    void rejectsConcurrentRunsPerKeyAndAcrossTheGlobalPool() {
        CodeRunProperties properties = properties(10, 1, 1);
        CodeRunAdmissionService service = new CodeRunAdmissionService(properties, () -> 1_000L);

        CodeRunAdmissionService.Lease first = service.acquire("student:41");
        try {
            assertThatThrownBy(() -> service.acquire("student:41"))
                    .isInstanceOf(CodeRunLimitException.class)
                    .hasMessageContaining("正在运行");
            assertThatThrownBy(() -> service.acquire("ip:127.0.0.1"))
                    .isInstanceOf(CodeRunLimitException.class)
                    .hasMessageContaining("繁忙");
        } finally {
            first.close();
        }

        service.acquire("ip:127.0.0.1").close();
    }

    private CodeRunProperties properties(int rate, int perKey, int global) {
        CodeRunProperties properties = new CodeRunProperties();
        properties.setMaxRunsPerMinute(rate);
        properties.setMaxConcurrentPerKey(perKey);
        properties.setMaxConcurrentGlobal(global);
        return properties;
    }
}
