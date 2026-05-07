package com.onlinejudge.execution;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCodeExecutorTest {

    @Test
    void readStreamCapsVeryLargeOutput() throws Exception {
        LocalCodeExecutor executor = new LocalCodeExecutor();
        Method readStream = LocalCodeExecutor.class.getDeclaredMethod("readStream", java.io.InputStream.class);
        readStream.setAccessible(true);

        String largeOutput = "x".repeat(150 * 1024);
        String result = (String) readStream.invoke(
                executor,
                new ByteArrayInputStream(largeOutput.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(result).hasSize(128 * 1024 + "\n[output truncated]".length());
        assertThat(result).endsWith("\n[output truncated]");
    }
}
