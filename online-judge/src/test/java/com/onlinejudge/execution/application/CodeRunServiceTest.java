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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeRunServiceTest {

    private final CodeExecutor executor = mock(CodeExecutor.class);
    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ExecutorStatusService statusService = mock(ExecutorStatusService.class);
    private final CodeRunProperties properties = new CodeRunProperties();
    private final CodeRunService service = new CodeRunService(executor, problemRepository, statusService, properties);

    @BeforeEach
    void setUp() {
        when(problemRepository.findById(101L)).thenReturn(Optional.of(problem()));
        when(statusService.getStatus()).thenReturn(status(true, true));
    }

    @Test
    void runsCodeWithServerOwnedProblemLimitsAndMapsExactOutput() {
        CodeExecutor.ExecutionResult execution = new CodeExecutor.ExecutionResult("8\n", "debug\n", 0, 14);
        execution.stdoutTruncated = true;
        when(executor.execute(any(CodeExecutionRequest.class))).thenReturn(execution);

        CodeRunResponse response = service.run(request("3 5\n"));

        assertThat(response.getStatus()).isEqualTo(CodeRunResponse.Status.SUCCESS);
        assertThat(response.getStdout()).isEqualTo("8\n");
        assertThat(response.getStderr()).isEqualTo("debug\n");
        assertThat(response.isStdoutTruncated()).isTrue();
        assertThat(response.getExecutionTimeMs()).isEqualTo(14);

        ArgumentCaptor<CodeExecutionRequest> captor = ArgumentCaptor.forClass(CodeExecutionRequest.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().stdin()).isEqualTo("3 5\n");
        assertThat(captor.getValue().timeLimitMs()).isEqualTo(1000);
        assertThat(captor.getValue().memoryLimitKb()).isEqualTo(131072);
        assertThat(captor.getValue().maxOutputBytes()).isEqualTo(properties.getMaxOutputBytes());
    }

    @Test
    void returnsEnvironmentUnavailableWithoutCallingTheExecutor() {
        when(statusService.getStatus()).thenReturn(status(true, false));
        CodeRunRequest request = request("");
        request.setLanguageId(ContestLanguageRegistry.CPP17_ID);

        CodeRunResponse response = service.run(request);

        assertThat(response.getStatus()).isEqualTo(CodeRunResponse.Status.ENVIRONMENT_UNAVAILABLE);
        assertThat(response.getMessage()).contains("C++17");
        verify(executor, never()).execute(any(CodeExecutionRequest.class));
    }

    @Test
    void mapsCompilationRuntimeAndTimeoutResultsWithoutPersistingAnything() {
        when(executor.execute(any(CodeExecutionRequest.class)))
                .thenReturn(CodeExecutor.ExecutionResult.compilationError("syntax error"))
                .thenReturn(CodeExecutor.ExecutionResult.runtimeError("boom", 2))
                .thenReturn(CodeExecutor.ExecutionResult.timeLimitExceeded());

        assertThat(service.run(request("")).getStatus()).isEqualTo(CodeRunResponse.Status.COMPILATION_ERROR);
        assertThat(service.run(request("")).getStatus()).isEqualTo(CodeRunResponse.Status.RUNTIME_ERROR);
        assertThat(service.run(request("")).getStatus()).isEqualTo(CodeRunResponse.Status.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void validatesUtf8RequestSizesAndEmergencySwitchBeforeExecution() {
        properties.setMaxSourceBytes(8);
        properties.setMaxStdinBytes(4);

        CodeRunRequest oversizedSource = request("");
        oversizedSource.setSourceCode("打印打印打印");
        assertThatThrownBy(() -> service.run(oversizedSource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("源代码");

        properties.setMaxSourceBytes(1024);
        CodeRunRequest oversizedInput = request("你好");
        assertThatThrownBy(() -> service.run(oversizedInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("标准输入");

        properties.setEnabled(false);
        CodeRunResponse disabled = service.run(request(""));
        assertThat(disabled.getStatus()).isEqualTo(CodeRunResponse.Status.ENVIRONMENT_UNAVAILABLE);
        verify(executor, never()).execute(any(CodeExecutionRequest.class));
    }

    private CodeRunRequest request(String stdin) {
        CodeRunRequest request = new CodeRunRequest();
        request.setProblemId(101L);
        request.setLanguageId(ContestLanguageRegistry.PYTHON3_ID);
        request.setSourceCode("a, b = map(int, input().split())\nprint(a + b)\n");
        request.setStdin(stdin);
        return request;
    }

    private Problem problem() {
        return Problem.builder()
                .id(101L)
                .title("两数求和")
                .description("输入两个整数。")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(131072)
                .build();
    }

    private ExecutorStatusResponse status(boolean python, boolean cpp17) {
        return ExecutorStatusResponse.builder()
                .mode("local")
                .executorType("TEST")
                .pythonAvailable(python)
                .cppAvailable(cpp17)
                .cpp17Available(cpp17)
                .message(cpp17 ? "评测环境可用。" : "本机模式缺少 C++17 编译器。")
                .build();
    }
}
