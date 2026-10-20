package com.onlinejudge.submission.application;

import java.util.Optional;

public final class AiDiagnosisWorkflowContext {

    private static final ThreadLocal<Long> ACTIVE_RUN = new ThreadLocal<>();

    private AiDiagnosisWorkflowContext() {
    }

    public static Scope activate(Long runId) {
        Long previous = ACTIVE_RUN.get();
        if (runId == null) {
            ACTIVE_RUN.remove();
        } else {
            ACTIVE_RUN.set(runId);
        }
        return () -> {
            if (previous == null) {
                ACTIVE_RUN.remove();
            } else {
                ACTIVE_RUN.set(previous);
            }
        };
    }

    public static Optional<Long> currentRunId() {
        return Optional.ofNullable(ACTIVE_RUN.get());
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
