package com.onlinejudge.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@ConditionalOnProperty(name = "executor.mode", havingValue = "docker")
public class DockerCodeExecutor implements CodeExecutor {

    private static final Path TEMP_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "wenzhong-docker-judge");

    @Value("${executor.docker.cpp17-image:wenzhong-oj-cpp17-runner:13}")
    private String cpp17Image;

    @Value("${executor.docker.python3-image:python:3.12-slim}")
    private String python3Image;

    @Override
    public String getExecutorType() {
        return "DOCKER";
    }

    @Override
    public boolean isAvailable() {
        return runSimpleDockerCheck();
    }

    @Override
    public ExecutionResult execute(CodeExecutionRequest request) {
        DockerLanguage language = dockerLanguage(request.languageId());
        if (language == null) {
            return ExecutionResult.error("容器沙箱暂只开放 " + ContestLanguageRegistry.supportedLanguageNames());
        }
        if (!isAvailable()) {
            return ExecutionResult.error("容器沙箱未就绪：当前系统未检测到 Docker，请先在部署环境安装并启动 Docker。");
        }

        Path workDir = TEMP_ROOT.resolve(UUID.randomUUID().toString());
        String volumeName = "wenzhong-judge-" + UUID.randomUUID().toString().replace("-", "");
        try {
            Files.createDirectories(workDir);
            Path sourceFile = workDir.resolve("solution." + language.extension());
            Path stdinFile = workDir.resolve("stdin.txt");
            Files.writeString(sourceFile, request.sourceCode(), StandardCharsets.UTF_8);
            Files.writeString(stdinFile, request.stdin(), StandardCharsets.UTF_8);
            createDockerVolume(volumeName);
            prepareDockerWorkspace(language, volumeName);

            long started = System.currentTimeMillis();
            int compileTimeoutMs = Math.max(30000, request.timeLimitMs() * 3);
            if (language.compileCommand() != null) {
                ExecutionResult compileResult = runDocker(
                        language, workDir, volumeName, language.compileCommand(), false,
                        compileTimeoutMs, request.memoryLimitKb(), request.maxOutputBytes()
                );
                if (compileResult.status == ExecutionResult.ResultStatus.TIME_LIMIT_EXCEEDED) {
                    return ExecutionResult.compilationError("编译超时，请检查模板、头文件和代码规模。");
                }
                if (compileResult.exitCode != 0) {
                    return ExecutionResult.compilationError(firstNonBlank(compileResult.stderr, compileResult.stdout))
                            .withOutput(compileResult.stdout, compileResult.stderr)
                            .withCapturedOutput(compileResult.stdoutTruncated, compileResult.stderrTruncated);
                }
            }

            ExecutionResult runResult = runDocker(
                    language, workDir, volumeName, language.runCommand(), true,
                    request.timeLimitMs(), request.memoryLimitKb(), request.maxOutputBytes()
            );
            runResult.executionTimeMs = System.currentTimeMillis() - started;
            return runResult;
        } catch (IOException exception) {
            log.error("Docker execution failed before container start", exception);
            return ExecutionResult.error("容器沙箱执行失败：" + exception.getMessage());
        } finally {
            cleanup(workDir);
            removeDockerVolume(volumeName);
        }
    }

    private ExecutionResult runDocker(DockerLanguage language,
                                      Path workDir,
                                      String volumeName,
                                      String command,
                                      boolean feedStdinFile,
                                      int timeoutMs,
                                      int memoryLimitKb,
                                      int maxOutputBytes) {
        String script = "mkdir -p /workspace && tar -xf - -C /workspace && cd /workspace && " + command;
        if (feedStdinFile) {
            script += " < /workspace/stdin.txt";
        }

        List<String> args = new ArrayList<>();
        args.add("docker");
        args.add("run");
        args.add("--rm");
        args.add("--network");
        args.add("none");
        args.add("--cpus");
        args.add("1");
        args.add("--memory");
        args.add(Math.max(memoryLimitKb, 65536) + "k");
        args.add("--pids-limit");
        args.add("64");
        args.add("-i");
        args.add("--mount");
        args.add("type=volume,source=" + volumeName + ",target=/workspace");
        args.add("-w");
        args.add("/");
        args.add(language.image());
        args.add("sh");
        args.add("-lc");
        args.add(script);

        try {
            ProcessBuilder builder = new ProcessBuilder(args);
            Process process = builder.start();
            try (OutputStream outputStream = process.getOutputStream()) {
                streamWorkspaceArchive(workDir, outputStream);
            }

            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<BoundedOutputCollector.CapturedOutput> stdoutFuture = executor.submit(
                    () -> BoundedOutputCollector.capture(process.getInputStream(), maxOutputBytes)
            );
            Future<BoundedOutputCollector.CapturedOutput> stderrFuture = executor.submit(
                    () -> BoundedOutputCollector.capture(process.getErrorStream(), maxOutputBytes)
            );
            boolean completed = process.waitFor(Math.max(timeoutMs, 100), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                executor.shutdownNow();
                return ExecutionResult.timeLimitExceeded();
            }

            BoundedOutputCollector.CapturedOutput stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            BoundedOutputCollector.CapturedOutput stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            executor.shutdown();
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                if (exitCode == 137 || stderr.content().toLowerCase().contains("out of memory")) {
                    return ExecutionResult.memoryLimitExceeded(stdout.content(), stderr.content(), exitCode)
                            .withCapturedOutput(stdout.truncated(), stderr.truncated());
                }
                return new ExecutionResult(stdout.content(), stderr.content(), exitCode, 0)
                        .withCapturedOutput(stdout.truncated(), stderr.truncated());
            }
            return new ExecutionResult(stdout.content(), stderr.content(), exitCode, 0)
                    .withCapturedOutput(stdout.truncated(), stderr.truncated());
        } catch (TimeoutException exception) {
            return ExecutionResult.timeLimitExceeded();
        } catch (Exception exception) {
            log.warn("Docker process failed. image={}, command={}", language.image(), command, exception);
            return ExecutionResult.error("容器沙箱进程启动失败：" + exception.getMessage());
        }
    }

    private void createDockerVolume(String volumeName) throws IOException {
        runDockerVolumeCommand("create", volumeName);
    }

    private void prepareDockerWorkspace(DockerLanguage language, String volumeName) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        command.add("--network");
        command.add("none");
        command.add("--user");
        command.add("0:0");
        command.add("--mount");
        command.add("type=volume,source=" + volumeName + ",target=/workspace");
        command.add(language.image());
        command.add("sh");
        command.add("-lc");
        command.add("mkdir -p /workspace && chmod 0777 /workspace");

        try {
            Process process = new ProcessBuilder(command).start();
            boolean completed = process.waitFor(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("Docker workspace preparation timed out");
            }
            if (process.exitValue() != 0) {
                String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException(firstNonBlank(stderr, "Docker workspace preparation failed"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Docker workspace preparation interrupted", exception);
        }
    }

    private void removeDockerVolume(String volumeName) {
        if (volumeName == null || volumeName.isBlank()) {
            return;
        }
        try {
            runDockerVolumeCommand("rm", "-f", volumeName);
        } catch (IOException exception) {
            log.warn("Failed to remove docker judge volume: {}", volumeName, exception);
        }
    }

    private void runDockerVolumeCommand(String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("volume");
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).start();
            boolean completed = process.waitFor(Duration.ofSeconds(6).toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("Docker volume command timed out: " + String.join(" ", command));
            }
            if (process.exitValue() != 0) {
                String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException(firstNonBlank(stderr, "Docker volume command failed: " + String.join(" ", command)));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Docker volume command interrupted", exception);
        }
    }

    private void streamWorkspaceArchive(Path workDir, OutputStream outputStream) throws IOException {
        try (var files = Files.list(workDir)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                writeTarEntry(file.getFileName().toString(), file, outputStream);
            }
        }
        outputStream.write(new byte[1024]);
    }

    private void writeTarEntry(String name, Path file, OutputStream outputStream) throws IOException {
        byte[] header = new byte[512];
        long size = Files.size(file);
        long modifiedAt = Files.getLastModifiedTime(file).toMillis() / 1000;

        writeTarString(header, 0, 100, name);
        writeTarOctal(header, 100, 8, 0644);
        writeTarOctal(header, 108, 8, 0);
        writeTarOctal(header, 116, 8, 0);
        writeTarOctal(header, 124, 12, size);
        writeTarOctal(header, 136, 12, modifiedAt);
        Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = '0';
        writeTarString(header, 257, 6, "ustar");
        writeTarString(header, 263, 2, "00");

        long checksum = 0;
        for (byte value : header) {
            checksum += Byte.toUnsignedInt(value);
        }
        writeTarChecksum(header, checksum);

        outputStream.write(header);
        try (InputStream inputStream = Files.newInputStream(file)) {
            inputStream.transferTo(outputStream);
        }

        int padding = (int) ((512 - (size % 512)) % 512);
        if (padding > 0) {
            outputStream.write(new byte[padding]);
        }
    }

    private void writeTarString(byte[] header, int offset, int length, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > length) {
            throw new IOException("Tar entry name is too long: " + value);
        }
        System.arraycopy(bytes, 0, header, offset, bytes.length);
    }

    private void writeTarOctal(byte[] header, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        int valueStart = offset + length - 1 - octal.length();
        Arrays.fill(header, offset, Math.max(offset, valueStart), (byte) '0');
        for (int i = 0; i < octal.length() && valueStart + i < offset + length - 1; i++) {
            header[valueStart + i] = (byte) octal.charAt(i);
        }
        header[offset + length - 1] = 0;
    }

    private void writeTarChecksum(byte[] header, long checksum) {
        String octal = Long.toOctalString(checksum);
        String padded = "000000".substring(Math.min(octal.length(), 6)) + octal;
        byte[] bytes = padded.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, bytes.length - 6, header, 148, 6);
        header[154] = 0;
        header[155] = (byte) ' ';
    }

    private boolean runSimpleDockerCheck() {
        try {
            Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}").start();
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

    private DockerLanguage dockerLanguage(int languageId) {
        ContestLanguageRegistry.ContestLanguage language = ContestLanguageRegistry.findSubmissionLanguage(languageId)
                .orElse(null);
        if (language == null) {
            return null;
        }
        return new DockerLanguage(
                language.displayName(),
                language.extension(),
                configuredDockerImage(language),
                language.dockerCompileCommand(),
                language.dockerRunCommand()
        );
    }

    private String configuredDockerImage(ContestLanguageRegistry.ContestLanguage language) {
        if (language.id() == ContestLanguageRegistry.CPP17_ID) {
            return firstNonBlank(cpp17Image, language.dockerImage());
        }
        if (language.id() == ContestLanguageRegistry.PYTHON3_ID) {
            return firstNonBlank(python3Image, language.dockerImage());
        }
        return language.dockerImage();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }

    private void cleanup(Path workDir) {
        if (workDir == null || !Files.exists(workDir)) {
            return;
        }
        try {
            Files.walk(workDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            log.warn("Failed to delete docker judge temp file: {}", path);
                        }
                    });
        } catch (IOException exception) {
            log.warn("Failed to cleanup docker judge temp directory: {}", workDir);
        }
    }

    private record DockerLanguage(String displayName,
                                  String extension,
                                  String image,
                                  String compileCommand,
                                  String runCommand) {
    }
}
