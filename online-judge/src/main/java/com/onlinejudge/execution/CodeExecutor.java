package com.onlinejudge.execution;

/**
 * Interface for code execution strategies.
 * Implementations can run code locally or in Docker containers.
 */
public interface CodeExecutor {

    int DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    ExecutionResult execute(CodeExecutionRequest request);

    /**
     * Execute code and return the result.
     * 
     * @param sourceCode The source code to execute
     * @param languageId Language identifier (71=Python 3, 54=C++17)
     * @param stdin Input to provide to the program
     * @param timeLimitMs Maximum execution time in milliseconds
     * @param memoryLimitKb Maximum memory usage in KB
     * @return Execution result with stdout, stderr, and status
     */
    default ExecutionResult execute(String sourceCode, int languageId, String stdin,
                                    int timeLimitMs, int memoryLimitKb) {
        return execute(new CodeExecutionRequest(
                sourceCode,
                languageId,
                stdin,
                timeLimitMs,
                memoryLimitKb,
                DEFAULT_MAX_OUTPUT_BYTES
        ));
    }

    /**
     * Check if this executor is available (e.g., Docker is installed).
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Get the executor type name for logging.
     */
    String getExecutorType();

    /**
     * Result of code execution.
     */
    class ExecutionResult {
        public String stdout;
        public String stderr;
        public int exitCode;
        public long executionTimeMs;
        public ResultStatus status;
        public String errorMessage;
        public boolean stdoutTruncated;
        public boolean stderrTruncated;

        public ExecutionResult(String stdout, String stderr, int exitCode, long executionTimeMs) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
            this.executionTimeMs = executionTimeMs;
            this.status = exitCode == 0 ? ResultStatus.SUCCESS : ResultStatus.RUNTIME_ERROR;
        }

        public static ExecutionResult error(String message) {
            ExecutionResult r = new ExecutionResult("", "", -1, 0);
            r.status = ResultStatus.INTERNAL_ERROR;
            r.errorMessage = message;
            return r;
        }

        public static ExecutionResult compilationError(String stderr) {
            ExecutionResult r = new ExecutionResult("", stderr, 1, 0);
            r.status = ResultStatus.COMPILATION_ERROR;
            return r;
        }

        public static ExecutionResult runtimeError(String stderr, int exitCode) {
            ExecutionResult r = new ExecutionResult("", stderr, exitCode, 0);
            r.status = ResultStatus.RUNTIME_ERROR;
            return r;
        }

        public static ExecutionResult timeLimitExceeded() {
            ExecutionResult r = new ExecutionResult("", "", -1, 0);
            r.status = ResultStatus.TIME_LIMIT_EXCEEDED;
            return r;
        }

        public static ExecutionResult memoryLimitExceeded(String stdout, String stderr, int exitCode) {
            ExecutionResult r = new ExecutionResult(stdout, stderr, exitCode, 0);
            r.status = ResultStatus.MEMORY_LIMIT_EXCEEDED;
            return r;
        }

        public ExecutionResult withOutput(String capturedStdout, String capturedStderr) {
            this.stdout = capturedStdout == null ? "" : capturedStdout;
            this.stderr = capturedStderr == null ? "" : capturedStderr;
            return this;
        }

        public ExecutionResult withCapturedOutput(boolean stdoutWasTruncated, boolean stderrWasTruncated) {
            this.stdoutTruncated = stdoutWasTruncated;
            this.stderrTruncated = stderrWasTruncated;
            return this;
        }

        public enum ResultStatus {
            SUCCESS,
            COMPILATION_ERROR,
            RUNTIME_ERROR,
            TIME_LIMIT_EXCEEDED,
            MEMORY_LIMIT_EXCEEDED,
            INTERNAL_ERROR
        }
    }
}
