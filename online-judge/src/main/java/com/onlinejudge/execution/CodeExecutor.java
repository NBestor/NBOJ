package com.onlinejudge.execution;

/**
 * Interface for code execution strategies.
 * Implementations can run code locally or in Docker containers.
 */
public interface CodeExecutor {

    /**
     * Execute code and return the result.
     * 
     * @param sourceCode The source code to execute
     * @param languageId Language identifier (71=Python, 62=Java, etc.)
     * @param stdin Input to provide to the program
     * @param timeLimitMs Maximum execution time in milliseconds
     * @param memoryLimitKb Maximum memory usage in KB
     * @return Execution result with stdout, stderr, and status
     */
    ExecutionResult execute(String sourceCode, int languageId, String stdin, 
                            int timeLimitMs, int memoryLimitKb);

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

