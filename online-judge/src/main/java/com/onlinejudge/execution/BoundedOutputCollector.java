package com.onlinejudge.execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class BoundedOutputCollector {

    private static final int BUFFER_SIZE = 8192;

    private BoundedOutputCollector() {
    }

    static CapturedOutput capture(InputStream inputStream, int maxBytes) throws IOException {
        int safeLimit = Math.max(maxBytes, 0);
        ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(safeLimit, BUFFER_SIZE));
        byte[] buffer = new byte[BUFFER_SIZE];
        boolean truncated = false;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            int remaining = safeLimit - captured.size();
            if (remaining > 0) {
                captured.write(buffer, 0, Math.min(remaining, read));
            }
            if (read > remaining) {
                truncated = true;
            }
        }
        return new CapturedOutput(validUtf8Prefix(captured.toByteArray(), safeLimit), truncated);
    }

    private static String validUtf8Prefix(byte[] bytes, int maxBytes) {
        String value = new String(bytes, StandardCharsets.UTF_8);
        while (!value.isEmpty() && value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
        }
        return value;
    }

    record CapturedOutput(String content, boolean truncated) {
    }
}
