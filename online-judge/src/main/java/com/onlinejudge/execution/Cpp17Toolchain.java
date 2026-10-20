package com.onlinejudge.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class Cpp17Toolchain {

    private static final Duration DETECTION_TTL = Duration.ofSeconds(30);
    private static final List<String> DEFAULT_COMPILER_CANDIDATES = List.of(
            "g++-14",
            "g++-13",
            "g++-12",
            "g++-11",
            "g++"
    );

    private static final String SMOKE_SOURCE = """
            #include <bits/stdc++.h>
            using namespace std;

            int main() {
                vector<int> values{1, 2, 3};
                cout << accumulate(values.begin(), values.end(), 0) << '\\n';
                return 0;
            }
            """;

    private static volatile Detection cachedDetection;

    private Cpp17Toolchain() {
    }

    public static Optional<String> findUsableCompiler() {
        Detection cached = cachedDetection;
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.checkedAtMs() < DETECTION_TTL.toMillis()) {
            return cached.compiler();
        }

        for (String candidate : compilerCandidates()) {
            if (canCompileContestSmoke(candidate)) {
                Optional<String> detected = Optional.of(candidate);
                cachedDetection = new Detection(detected, now);
                return detected;
            }
        }
        Optional<String> detected = Optional.empty();
        cachedDetection = new Detection(detected, now);
        return detected;
    }

    public static String compilerCommandOrDefault() {
        return findUsableCompiler().orElse("g++");
    }

    private static List<String> compilerCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        String configuredCompiler = System.getenv("OJ_CPP17_COMPILER");
        if (configuredCompiler != null && !configuredCompiler.isBlank()) {
            candidates.add(configuredCompiler.trim());
        }
        candidates.addAll(DEFAULT_COMPILER_CANDIDATES);
        return List.copyOf(candidates);
    }

    private static boolean canCompileContestSmoke(String compiler) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("cpp17-toolchain-smoke");
            Path source = workDir.resolve("solution.cpp");
            Path executable = workDir.resolve("solution");
            Files.writeString(source, SMOKE_SOURCE, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(compiler);
            command.addAll(ContestLanguageRegistry.cpp17CompileFlags());
            command.add("-o");
            command.add(executable.toString());
            command.add(source.toString());

            Process process = new ProcessBuilder(command).start();
            boolean completed = process.waitFor(Duration.ofSeconds(6).toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            cleanup(workDir);
        }
    }

    private static void cleanup(Path workDir) {
        if (workDir == null || !Files.exists(workDir)) {
            return;
        }
        try {
            try (var paths = Files.walk(workDir)) {
                paths.sorted((left, right) -> right.compareTo(left))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                                // Best-effort cleanup for tiny smoke-test files.
                            }
                        });
            }
        } catch (IOException ignored) {
            // Best-effort cleanup for tiny smoke-test files.
        }
    }

    private record Detection(Optional<String> compiler, long checkedAtMs) {
    }
}
