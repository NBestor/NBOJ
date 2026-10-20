package com.onlinejudge.system.application;

import com.onlinejudge.execution.CodeExecutor;
import com.onlinejudge.execution.Cpp17Toolchain;
import com.onlinejudge.execution.ContestLanguageRegistry;
import com.onlinejudge.shared.identity.YingqiSignature;
import com.onlinejudge.system.dto.ExecutorStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ExecutorStatusService {

    private final CodeExecutor codeExecutor;

    @Value("${executor.mode:local}")
    private String mode;

    @Value("${executor.docker.cpp17-image:wenzhong-oj-cpp17-runner:13}")
    private String cpp17DockerImage;

    public ExecutorStatusResponse getStatus() {
        String normalizedMode = mode == null ? "local" : mode.trim().toLowerCase(Locale.ROOT);
        boolean dockerAvailable = commandAvailable("docker", "version", "--format", "{{.Server.Version}}");
        boolean cpp17DockerImageAvailable = dockerAvailable && dockerImageAvailable(cpp17DockerImage);
        boolean pythonAvailable = "docker".equals(normalizedMode)
                ? dockerAvailable
                : commandAvailable(isWindows() ? "python" : "python3", "--version");
        boolean cpp17Available = "docker".equals(normalizedMode)
                ? cpp17DockerImageAvailable
                : Cpp17Toolchain.findUsableCompiler().isPresent();

        String message;
        if ("docker".equals(normalizedMode) && !dockerAvailable) {
            message = "评测环境未就绪：Docker 未启动。";
        } else if ("docker".equals(normalizedMode) && !cpp17DockerImageAvailable) {
            message = "评测环境未就绪：缺少 C++17 runner 镜像。";
        } else if ("docker".equals(normalizedMode)) {
            message = "评测环境已就绪。";
        } else if (!cpp17Available) {
            message = "本机模式缺少 C++17 编译器。";
        } else {
            message = "评测环境可用。";
        }

        return ExecutorStatusResponse.builder()
                .mode(normalizedMode)
                .executorType(codeExecutor.getExecutorType())
                .dockerAvailable(dockerAvailable)
                .pythonAvailable(pythonAvailable)
                .cppAvailable(cpp17Available)
                .cpp17Available(cpp17Available)
                .message(message)
                .projectOwner(YingqiSignature.OWNER)
                .ownershipSignature(YingqiSignature.FINGERPRINT)
                .ownershipClaim(YingqiSignature.CLAIM)
                .build();
    }

    private boolean commandAvailable(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            boolean completed = process.waitFor(Duration.ofSeconds(4).toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean dockerImageAvailable(String image) {
        if (image == null || image.isBlank()) {
            return false;
        }
        return commandAvailable("docker", "image", "inspect", image.trim());
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
