package com.onlinejudge.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
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
    private static final int MAX_OUTPUT_CHARS = 128 * 1024;
    private static final String OUTPUT_TRUNCATED_MARKER = "\n[output truncated]";

    // Language ID to configuration mapping
    private static final Map<Integer, LanguageConfig> LANGUAGES = Map.of(
            71, new LanguageConfig("python", "py", null, getPythonCommand() + " {file}"),
            62, new LanguageConfig("java", "java", "javac {file}", "java -cp {dir} Main"),
            54, new LanguageConfig("cpp", "cpp", getGppCommand() + " -o {exe} {file}", "{exe}"),
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
    public ExecutionResult execute(String sourceCode, int languageId, String stdin, 
                                    int timeLimitMs, int memoryLimitKb) {
        LanguageConfig config = LANGUAGES.get(languageId);
        if (config == null) {
            return ExecutionResult.error("不支持的语言 ID: " + languageId);
        }

        String executionId = UUID.randomUUID().toString().substring(0, 8);
        Path workDir = Paths.get(TEMP_DIR, executionId);
        
        try {
            // Create work directory
            Files.createDirectories(workDir);
            
            // Write source code to file
            String filename = config.language.equals("java") ? "Main." + config.extension : "solution." + config.extension;
            Path sourceFile = workDir.resolve(filename);
            Files.writeString(sourceFile, sourceCode);
            
            // Determine executable path for compiled languages
            String exeName = IS_WINDOWS ? "a.exe" : "a.out";
            Path exePath = workDir.resolve(exeName);
            
            long startTime = System.currentTimeMillis();
            
            // Compile if needed
            if (config.compileCommand != null) {
                String compileCmd = config.compileCommand
                        .replace("{file}", sourceFile.toString())
                        .replace("{dir}", workDir.toString())
                        .replace("{exe}", exePath.toString());
                
                ExecutionResult compileResult = runProcess(compileCmd, workDir, null, 30000);
                
                if (compileResult.exitCode != 0) {
                    return ExecutionResult.compilationError(compileResult.stderr);
                }
            }
            
            // Execute
            String runCommand = config.runCommand
                    .replace("{file}", sourceFile.toString())
                    .replace("{dir}", workDir.toString())
                    .replace("{exe}", exePath.toString());
            
            ExecutionResult result = runProcess(runCommand, workDir, stdin, timeLimitMs);
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

    private ExecutionResult runProcess(String command, Path workDir, String stdin, int timeoutMs) {
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
            
            // Write stdin if provided
            if (stdin != null && !stdin.isEmpty()) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(stdin.getBytes());
                    os.flush();
                }
            } else {
                process.getOutputStream().close();
            }
            
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<String> stdoutFuture = executor.submit(() -> readStream(process.getInputStream()));
                Future<String> stderrFuture = executor.submit(() -> readStream(process.getErrorStream()));

                boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

                if (!completed) {
                    process.destroyForcibly();
                    return ExecutionResult.timeLimitExceeded();
                }

                String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
                String stderr = stderrFuture.get(1, TimeUnit.SECONDS);

                int exitCode = process.exitValue();

                if (exitCode != 0 && (stderr.contains("Exception") || stderr.contains("Error") ||
                        stderr.contains("Segmentation fault") || stderr.contains("core dumped"))) {
                    return ExecutionResult.runtimeError(stderr, exitCode);
                }

                return new ExecutionResult(stdout, stderr, exitCode, 0);
            } finally {
                executor.shutdownNow();
            }
            
        } catch (TimeoutException e) {
            return ExecutionResult.timeLimitExceeded();
        } catch (Exception e) {
            return ExecutionResult.error("进程执行失败: " + e.getMessage());
        }
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder output = new StringBuilder();
        boolean truncated = false;
        try (Reader reader = new InputStreamReader(is)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                int remaining = MAX_OUTPUT_CHARS - output.length();
                if (remaining > 0) {
                    output.append(buffer, 0, Math.min(read, remaining));
                }
                if (read > remaining) {
                    truncated = true;
                }
            }
        }
        if (truncated) {
            output.append(OUTPUT_TRUNCATED_MARKER);
        }
        return output.toString();
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

