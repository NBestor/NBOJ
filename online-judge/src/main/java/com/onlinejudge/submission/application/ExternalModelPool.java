package com.onlinejudge.submission.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ExternalModelPool {

    private ExternalModelPool() {
    }

    public static List<String> candidates(String primaryModel, String modelPool) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addModel(candidates, primaryModel);
        if (modelPool != null && !modelPool.isBlank()) {
            for (String item : modelPool.split("[,;\\n\\r]+")) {
                addModel(candidates, item);
            }
        }
        return List.copyOf(candidates);
    }

    public static boolean shouldFallback(ModelStageFailureReason reason, String text) {
        if (reason == ModelStageFailureReason.INSUFFICIENT_QUOTA
                || reason == ModelStageFailureReason.RATE_LIMITED
                || reason == ModelStageFailureReason.MODEL_UNSUPPORTED
                || reason == ModelStageFailureReason.TIMEOUT
                || reason == ModelStageFailureReason.API_ERROR) {
            return true;
        }
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return normalized.contains("did not include message content");
    }

    private static void addModel(LinkedHashSet<String> candidates, String model) {
        String normalized = model == null ? "" : model.trim();
        if (!normalized.isBlank()) {
            candidates.add(normalized);
        }
    }
}
