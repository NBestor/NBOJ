package com.onlinejudge.execution.application;

import com.onlinejudge.execution.CodeExecutionRequest;
import com.onlinejudge.execution.CodeExecutor;
import com.onlinejudge.execution.ContestLanguageRegistry;
import com.onlinejudge.execution.config.CodeRunProperties;
import com.onlinejudge.execution.dto.CodeRunRequest;
import com.onlinejudge.execution.dto.CodeRunResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.system.application.ExecutorStatusService;
import com.onlinejudge.system.dto.ExecutorStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeRunService {

    private static final Pattern LOCAL_SOLUTION_PATH = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/]|/)[^\\r\\n\\\"']*?[\\\\/]solution\\.(py|cpp|java|js|c)"
    );

    private final CodeExecutor codeExecutor;
    private final ProblemRepository problemRepository;
    private final ExecutorStatusService executorStatusService;
    private final CodeRunProperties properties;

    public CodeRunResponse run(CodeRunRequest request) {
        if (!properties.isEnabled()) {
            return unavailable("代码运行功能暂时关闭，请稍后再试。");
        }
        validateRequest(request);

        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + request.getProblemId()));
        ContestLanguageRegistry.ContestLanguage language = ContestLanguageRegistry
                .findSubmissionLanguage(request.getLanguageId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "暂不支持该语言，当前开放：" + ContestLanguageRegistry.supportedLanguageNames()
                ));

        ExecutorStatusResponse executorStatus = executorStatusService.getStatus();
        if (language.id() == ContestLanguageRegistry.PYTHON3_ID && !executorStatus.isPythonAvailable()) {
            return unavailable("Python 3 运行环境未就绪，请联系老师处理。");
        }
        if (ContestLanguageRegistry.isCpp17(language.id()) && !executorStatus.isCpp17Available()) {
            return unavailable("C++17 运行环境未就绪，请联系老师处理。");
        }

        CodeExecutionRequest executionRequest = new CodeExecutionRequest(
                request.getSourceCode(),
                language.id(),
                request.getStdin(),
                problem.getTimeLimit(),
                problem.getMemoryLimit(),
                properties.getMaxOutputBytes()
        );
        CodeExecutor.ExecutionResult executionResult = codeExecutor.execute(executionRequest);
        CodeRunResponse response = map(executionResult);
        log.info("Custom code run completed. languageId={}, status={}, durationMs={}, sourceBytes={}, stdinBytes={}, stdoutBytes={}, stderrBytes={}, stdoutTruncated={}, stderrTruncated={}",
                language.id(),
                response.getStatus(),
                response.getExecutionTimeMs(),
                utf8Length(request.getSourceCode()),
                utf8Length(request.getStdin()),
                utf8Length(response.getStdout()),
                utf8Length(response.getStderr()),
                response.isStdoutTruncated(),
                response.isStderrTruncated());
        return response;
    }

    public void validateRequest(CodeRunRequest request) {
        validateSize("源代码", request.getSourceCode(), properties.getMaxSourceBytes());
        validateSize("标准输入", request.getStdin(), properties.getMaxStdinBytes());
    }

    private CodeRunResponse map(CodeExecutor.ExecutionResult result) {
        if (result == null) {
            return internalError();
        }
        CodeRunResponse.Status status = switch (result.status) {
            case SUCCESS -> CodeRunResponse.Status.SUCCESS;
            case COMPILATION_ERROR -> CodeRunResponse.Status.COMPILATION_ERROR;
            case RUNTIME_ERROR -> CodeRunResponse.Status.RUNTIME_ERROR;
            case TIME_LIMIT_EXCEEDED -> CodeRunResponse.Status.TIME_LIMIT_EXCEEDED;
            case MEMORY_LIMIT_EXCEEDED -> CodeRunResponse.Status.MEMORY_LIMIT_EXCEEDED;
            case INTERNAL_ERROR -> CodeRunResponse.Status.INTERNAL_ERROR;
        };
        String message = switch (status) {
            case TIME_LIMIT_EXCEEDED -> "运行超过题目时间限制。";
            case MEMORY_LIMIT_EXCEEDED -> "运行超过题目内存限制。";
            case INTERNAL_ERROR -> "执行环境暂时不可用，请稍后重试。";
            default -> null;
        };
        return CodeRunResponse.builder()
                .status(status)
                .stdout(sanitizeOutput(result.stdout))
                .stderr(sanitizeOutput(result.stderr))
                .exitCode(result.exitCode)
                .executionTimeMs(result.executionTimeMs)
                .stdoutTruncated(result.stdoutTruncated)
                .stderrTruncated(result.stderrTruncated)
                .message(message)
                .build();
    }

    private CodeRunResponse unavailable(String message) {
        return CodeRunResponse.builder()
                .status(CodeRunResponse.Status.ENVIRONMENT_UNAVAILABLE)
                .stdout("")
                .stderr("")
                .executionTimeMs(0)
                .stdoutTruncated(false)
                .stderrTruncated(false)
                .message(message)
                .build();
    }

    private CodeRunResponse internalError() {
        return CodeRunResponse.builder()
                .status(CodeRunResponse.Status.INTERNAL_ERROR)
                .stdout("")
                .stderr("")
                .executionTimeMs(0)
                .message("执行环境暂时不可用，请稍后重试。")
                .build();
    }

    private void validateSize(String label, String value, int maxBytes) {
        if (utf8Length(value) > Math.max(maxBytes, 0)) {
            throw new IllegalArgumentException(label + "超过允许大小（最大 " + Math.max(maxBytes, 0) + " 字节）");
        }
    }

    private int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String sanitizeOutput(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return LOCAL_SOLUTION_PATH.matcher(value).replaceAll("solution.$1");
    }
}
