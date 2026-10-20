package com.onlinejudge.execution;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedOutputCollectorTest {

    @Test
    void preservesWhitespaceAndMarksContentBeyondTheUtf8ByteLimit() throws Exception {
        String output = "第一行\nsecond line\n";

        BoundedOutputCollector.CapturedOutput captured = BoundedOutputCollector.capture(
                new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)),
                12
        );

        assertThat(captured.content().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(12);
        assertThat(captured.content()).startsWith("第一行\n");
        assertThat(captured.truncated()).isTrue();
    }

    @Test
    void keepsEmptyAndTrailingNewlineOutputExactWhenWithinLimit() throws Exception {
        BoundedOutputCollector.CapturedOutput empty = BoundedOutputCollector.capture(
                new ByteArrayInputStream(new byte[0]),
                64
        );
        BoundedOutputCollector.CapturedOutput newline = BoundedOutputCollector.capture(
                new ByteArrayInputStream("ok\n".getBytes(StandardCharsets.UTF_8)),
                64
        );

        assertThat(empty.content()).isEmpty();
        assertThat(empty.truncated()).isFalse();
        assertThat(newline.content()).isEqualTo("ok\n");
        assertThat(newline.truncated()).isFalse();
    }
}
