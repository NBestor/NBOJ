package com.onlinejudge.execution.application;

import com.onlinejudge.execution.config.CodeRunProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;

@Service
public class CodeRunAdmissionService {

    private static final long WINDOW_MILLIS = 60_000L;

    private final CodeRunProperties properties;
    private final LongSupplier nowMillis;
    private final Map<String, KeyState> states = new HashMap<>();
    private Semaphore globalSlots;
    private int configuredGlobalSlots;
    private long lastSweepMillis;

    @Autowired
    public CodeRunAdmissionService(CodeRunProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    CodeRunAdmissionService(CodeRunProperties properties, LongSupplier nowMillis) {
        this.properties = properties;
        this.nowMillis = nowMillis;
        rebuildGlobalSlots();
    }

    public Lease acquire(String key) {
        String safeKey = key == null || key.isBlank() ? "anonymous:unknown" : key;
        synchronized (states) {
            ensureGlobalSlotsConfiguration();
            long now = nowMillis.getAsLong();
            sweepExpiredKeys(now);
            KeyState state = states.computeIfAbsent(safeKey, ignored -> new KeyState());
            while (!state.starts.isEmpty() && now - state.starts.peekFirst() >= WINDOW_MILLIS) {
                state.starts.removeFirst();
            }
            if (state.starts.size() >= Math.max(properties.getMaxRunsPerMinute(), 1)) {
                throw new CodeRunLimitException("运行过于频繁，请稍后再试。");
            }
            if (state.inFlight >= Math.max(properties.getMaxConcurrentPerKey(), 1)) {
                throw new CodeRunLimitException("已有代码正在运行，请等待本次运行完成。");
            }
            if (!globalSlots.tryAcquire()) {
                throw new CodeRunLimitException("运行服务繁忙，请稍后再试。");
            }
            state.inFlight++;
            state.starts.addLast(now);
            return new Lease(this, safeKey);
        }
    }

    private void release(String key) {
        synchronized (states) {
            KeyState state = states.get(key);
            if (state != null) {
                state.inFlight = Math.max(0, state.inFlight - 1);
                if (state.inFlight == 0 && state.starts.isEmpty()) {
                    states.remove(key);
                }
            }
            globalSlots.release();
        }
    }

    private void ensureGlobalSlotsConfiguration() {
        int expected = Math.max(properties.getMaxConcurrentGlobal(), 1);
        if (configuredGlobalSlots != expected && globalSlots.availablePermits() == configuredGlobalSlots) {
            rebuildGlobalSlots();
        }
    }

    private void rebuildGlobalSlots() {
        configuredGlobalSlots = Math.max(properties.getMaxConcurrentGlobal(), 1);
        globalSlots = new Semaphore(configuredGlobalSlots, true);
    }

    private void sweepExpiredKeys(long now) {
        if (now - lastSweepMillis < WINDOW_MILLIS) {
            return;
        }
        states.values().forEach(state -> {
            while (!state.starts.isEmpty() && now - state.starts.peekFirst() >= WINDOW_MILLIS) {
                state.starts.removeFirst();
            }
        });
        states.entrySet().removeIf(entry -> entry.getValue().inFlight == 0 && entry.getValue().starts.isEmpty());
        lastSweepMillis = now;
    }

    private static final class KeyState {
        private final ArrayDeque<Long> starts = new ArrayDeque<>();
        private int inFlight;
    }

    public static final class Lease implements AutoCloseable {
        private final CodeRunAdmissionService owner;
        private final String key;
        private boolean closed;

        private Lease(CodeRunAdmissionService owner, String key) {
            this.owner = owner;
            this.key = key;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                owner.release(key);
            }
        }
    }
}
