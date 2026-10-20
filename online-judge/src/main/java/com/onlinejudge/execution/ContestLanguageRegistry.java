package com.onlinejudge.execution;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ContestLanguageRegistry {

    public static final int PYTHON3_ID = 71;
    public static final int CPP17_ID = 54;

    private static final String CPP17_COMPILE_FLAGS = "-std=c++17 -O2 -pipe";

    private static final Map<Integer, ContestLanguage> LANGUAGES = Map.of(
            PYTHON3_ID, new ContestLanguage(
                    PYTHON3_ID,
                    "Python 3",
                    "python",
                    "py",
                    "solution.py",
                    "python",
                    false,
                    null,
                    "python:3.12-slim",
                    null,
                    "python3 /workspace/solution.py"
            ),
            CPP17_ID, new ContestLanguage(
                    CPP17_ID,
                    "C++17",
                    "cpp",
                    "cpp",
                    "solution.cpp",
                    "cpp",
                    true,
                    "{compiler} " + CPP17_COMPILE_FLAGS + " -o {exe} {file}",
                    "wenzhong-oj-cpp17-runner:13",
                    "g++ " + CPP17_COMPILE_FLAGS + " -o /workspace/solution /workspace/solution.cpp",
                    "/workspace/solution"
            )
    );

    private ContestLanguageRegistry() {
    }

    public static Optional<ContestLanguage> findSubmissionLanguage(Integer languageId) {
        if (languageId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(LANGUAGES.get(languageId));
    }

    public static Collection<ContestLanguage> supportedSubmissionLanguages() {
        return LANGUAGES.values().stream()
                .sorted(Comparator.comparingInt(ContestLanguage::id))
                .toList();
    }

    public static String supportedLanguageNames() {
        return supportedSubmissionLanguages().stream()
                .map(ContestLanguage::displayName)
                .collect(Collectors.joining("、"));
    }

    public static boolean isCpp17(Integer languageId) {
        return languageId != null && languageId == CPP17_ID;
    }

    public static List<String> cpp17CompileFlags() {
        return List.of("-std=c++17", "-O2", "-pipe");
    }

    public record ContestLanguage(int id,
                                  String displayName,
                                  String runtimeName,
                                  String extension,
                                  String sourceFileName,
                                  String editorKind,
                                  boolean compilerRequired,
                                  String localCompileCommand,
                                  String dockerImage,
                                  String dockerCompileCommand,
                                  String dockerRunCommand) {
    }
}
