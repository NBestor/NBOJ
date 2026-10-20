package com.onlinejudge.identity.application;

import com.onlinejudge.shared.web.PlatformApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestRateLimiter {
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    public void check(String bucket, String key, int limit, long windowSeconds) {
        String composite = bucket + ':' + (key == null || key.isBlank() ? "unknown" : key);
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(windowSeconds);
        Deque<Instant> queue = attempts.computeIfAbsent(composite, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) queue.removeFirst();
            if (queue.size() >= limit) {
                throw new PlatformApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁，请稍后再试");
            }
            queue.addLast(now);
        }
    }
}
