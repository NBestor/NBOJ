package com.onlinejudge.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Local code executor that runs code directly on the machine.
 * Supports Python, Java, JavaScript, C, and C++.
 * Works on Windows, Linux, and macOS.
 * 
 * Enabled when: executor.mode=local (default)
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "executor.mode", havingValue = "local", matchIfMissing = true)
public class LocalCodeExecutor implements CodeExecutor {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + File.separator + "onlinejudge";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    // Language ID to configuration mapping. Submission languages come from ContestLanguageRegistry;
    // legacy local-only configs remain here for development compatibility.
    private static final Map<Integer, LanguageConfig> LANGUAGES = Map.of(
            ContestLanguageRegistry.PYTHON3_ID, new LanguageConfig("python", "py", null, getPythonCommand() + " {file}"),
            62, new LanguageConfig("java", "java", "javac {file}", "java -cp {dir} Main"),
            ContestLanguageRegistry.CPP17_ID, new LanguageConfig(
                    "cpp",
                    "cpp",
                    ContestLanguageRegistry.findSubmissionLanguage(ContestLanguageRegistry.CPP17_ID)
                            .orElseThrow()
                            .localCompileCommand(),
                    "{exe}"),
            63, new LanguageConfig("javascript", "js", null, "node {file}"),
            50, new LanguageConfig("c", "c", getGccCommand() + " -o {exe} {file}", "{exe}")
    );

    private static String getPythonCommand() {
        // Windows typically uses "python", Linux/Mac use "python3"
        if (IS_WINDOWS) {
            return "python";
        }
        return "python3";
    }

    private static String getGppCommand() {
        return "g++";
    }

    private static String getGccCommand() {
        return "gcc";
    }

    @Override
    public String getExecutorType() {
        return "LOCAL";
    }

    @Override
    public ExecutionResult execute(CodeExecutionRequest request) {
        LanguageConfig config = LANGUAGES.get(request.languageId());
        if (config == null) {
            return ExecutionResult.error("不支持的语言 ID: " + request.languageId());
        }

        String executionId = UUID.randomUUID().toString().substring(0, 8);
        Path workDir = Paths.get(TEMP_DIR, executionId);
        
        try {
            // Create work directory
            Files.createDirectories(workDir);
            
            // Write source code to file
            String filename = config.language.equals("java") ? "Main." + config.extension : "solution." + config.extension;
            Path sourceFile = workDir.resolve(filename);
            Files.writeString(sourceFile, request.sourceCode(), StandardCharsets.UTF_8);
            
            // Determine executable path for compiled languages
            String exeName = IS_WINDOWS ? "a.exe" : "a.out";
            Path exePath = workDir.resolve(exeName);
            
            long startTime = System.currentTimeMillis();
            
            // Compile if needed
            if (config.compileCommand != null) {
                String compileCmd = config.compileCommand
                        .replace("{compiler}", Cpp17Toolchain.compilerCommandOrDefault())
                        .replace("{file}", sourceFile.toString())
                        .replace("{dir}", workDir.toString())
                        .replace("{exe}", exePath.toString());
                
                ExecutionResult compileResult = runProcess(compileCmd, workDir, null, 30000, request.maxOutputBytes());
                
                if (compileResult.exitCode != 0) {
                    return ExecutionResult.compilationError(compileResult.stderr)
                            .withOutput(compileResult.stdout, compileResult.stderr)
                            .withCapturedOutput(compileResult.stdoutTruncated, compileResult.stderrTruncated);
                }
            }
            
            // Execute
            String runCommand = config.runCommand
                    .replace("{file}", sourceFile.toString())
                    .replace("{dir}", workDir.toString())
                    .replace("{exe}", exePath.toString());
            
            ExecutionResult result = runProcess(
                    runCommand,
                    workDir,
                    request.stdin(),
                    request.timeLimitMs(),
                    request.maxOutputBytes()
            );
            result.executionTimeMs = System.currentTimeMillis() - startTime;
            
            return result;
            
        } catch (Exception e) {
            log.error("Execution failed", e);
            return ExecutionResult.error("执行失败: " + e.getMessage());
        } finally {
            // Cleanup
            try {
                deleteDirectory(workDir);
            } catch (IOException e) {
                log.warn("Failed to cleanup temp directory: {}", workDir);
            }
        }
    }

    private ExecutionResult runProcess(String command,
                                       Path workDir,
                                       String stdin,
                                       int timeoutMs,
                                       int maxOutputBytes) {
        try {
            ProcessBuilder pb;
            
            if (IS_WINDOWS) {
                // Windows: use cmd /c
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                // Linux/Mac: use bash -c
                pb = new ProcessBuilder("bash", "-c", command);
            }
            
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false);
            
            Process process = pb.start();
            
            // Drain output and feed stdin concurrently so a program that ignores input
            // cannot fill the OS pipe and bypass the execution timeout.
            ExecutorService executor = Executors.newFixedThreadPool(3);
            Future<BoundedOutputCollector.CapturedOutput> stdoutFuture = executor.submit(
                    () -> BoundedOutputCollector.capture(process.getInputStream(), maxOutputBytes)
            );
            Future<BoundedOutputCollector.CapturedOutput> stderrFuture = executor.submit(
                    () -> BoundedOutputCollector.capture(process.getErrorStream(), maxOutputBytes)
            );
            executor.submit(() -> {
                try (OutputStream outputStream = process.getOutputStream()) {
                    if (stdin != null && !stdin.isEmpty()) {
                        outputStream.write(stdin.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    }
                } catch (IOException ignored) {
                    // The child may exit before consuming all stdin; its exit status is authoritative.
                }
            });
            
            boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                executor.shutdownNow();
                return ExecutionResult.timeLimitExceeded();
            }
            
            BoundedOutputCollector.CapturedOutput stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            BoundedOutputCollector.CapturedOutput stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            executor.shutdown();
            
            int exitCode = process.exitValue();
            
            return new ExecutionResult(stdout.content(), stderr.content(), exitCode, 0)
                    .withCapturedOutput(stdout.truncated(), stderr.truncated());
            
        } catch (TimeoutException e) {
            return ExecutionResult.timeLimitExceeded();
        } catch (Exception e) {
            return ExecutionResult.error("进程执行失败: " + e.getMessage());
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", p);
                        }
                    });
        }
    }

    private record LanguageConfig(String language, String extension, String compileCommand, String runCommand) {}
}
