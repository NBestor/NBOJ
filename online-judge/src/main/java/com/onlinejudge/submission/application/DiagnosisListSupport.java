package com.onlinejudge.submission.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

final class DiagnosisListSupport {

    private DiagnosisListSupport() {
    }

    static List<String> merge(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return merged;
    }

    static List<String> deduplicate(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(input.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList()));
    }

    static <T> List<T> append(List<T> input, T item) {
        List<T> output = new ArrayList<>();
        if (input != null) {
            output.addAll(input);
        }
        if (item != null) {
            output.add(item);
        }
        return output;
    }
}
