package com.onlinejudge.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContestLanguageRegistryTest {

    @Test
    void exposesCpp17AsContestSubmissionLanguage() {
        ContestLanguageRegistry.ContestLanguage cpp17 =
                ContestLanguageRegistry.findSubmissionLanguage(ContestLanguageRegistry.CPP17_ID).orElseThrow();

        assertThat(cpp17.id()).isEqualTo(54);
        assertThat(cpp17.displayName()).isEqualTo("C++17");
        assertThat(cpp17.extension()).isEqualTo("cpp");
        assertThat(cpp17.sourceFileName()).isEqualTo("solution.cpp");
        assertThat(cpp17.editorKind()).isEqualTo("cpp");
        assertThat(cpp17.compilerRequired()).isTrue();
    }

    @Test
    void cpp17CompileCommandsUseContestFlags() {
        ContestLanguageRegistry.ContestLanguage cpp17 =
                ContestLanguageRegistry.findSubmissionLanguage(ContestLanguageRegistry.CPP17_ID).orElseThrow();

        assertThat(cpp17.localCompileCommand()).contains("{compiler}", "-std=c++17", "-O2", "-pipe", "-o {exe}", "{file}");
        assertThat(cpp17.dockerImage()).isEqualTo("wenzhong-oj-cpp17-runner:13");
        assertThat(cpp17.dockerCompileCommand()).contains("g++", "-std=c++17", "-O2", "-pipe", "/workspace/solution.cpp");
        assertThat(ContestLanguageRegistry.cpp17CompileFlags()).containsExactly("-std=c++17", "-O2", "-pipe");
    }

    @Test
    void onlyPython3AndCpp17AreSubmissionLanguagesForThisPhase() {
        assertThat(ContestLanguageRegistry.supportedSubmissionLanguages())
                .extracting(ContestLanguageRegistry.ContestLanguage::displayName)
                .containsExactly("C++17", "Python 3");
        assertThat(ContestLanguageRegistry.findSubmissionLanguage(50)).isEmpty();
    }
}
