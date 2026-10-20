package com.onlinejudge.execution.api;

import com.onlinejudge.execution.application.ClassroomProblemAccessService;
import com.onlinejudge.execution.application.CodeRunAdmissionService;
import com.onlinejudge.execution.application.CodeRunService;
import com.onlinejudge.execution.config.CodeRunProperties;
import com.onlinejudge.execution.dto.CodeRunResponse;
import com.onlinejudge.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CodeRunControllerTest {

    private ClassroomProblemAccessService accessService;
    private CodeRunService codeRunService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accessService = mock(ClassroomProblemAccessService.class);
        codeRunService = mock(CodeRunService.class);
        CodeRunProperties properties = new CodeRunProperties();
        properties.setMaxRunsPerMinute(1);
        CodeRunAdmissionService admissionService = new CodeRunAdmissionService(properties);
        CodeRunController controller = new CodeRunController(accessService, admissionService, codeRunService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTheTemporaryExecutionContractWithoutAcceptingClientLimits() throws Exception {
        when(accessService.requireAccess(any(), any(), any())).thenReturn(null);
        when(codeRunService.run(any())).thenReturn(CodeRunResponse.builder()
                .status(CodeRunResponse.Status.SUCCESS)
                .stdout("8\n")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(12)
                .stdoutTruncated(false)
                .stderrTruncated(false)
                .build());

        mockMvc.perform(post("/api/code-runs")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.8");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"problemId":101,"assignmentId":7,"languageId":71,
                                 "sourceCode":"print(sum(map(int,input().split())))","stdin":"3 5",
                                 "timeLimitMs":999999,"memoryLimitKb":999999}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.stdout").value("8\n"))
                .andExpect(jsonPath("$.executionTimeMs").value(12));

        verify(codeRunService).run(any());
    }

    @Test
    void mapsValidationAndRateLimitsTo400And429() throws Exception {
        mockMvc.perform(post("/api/code-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":101,\"languageId\":71,\"sourceCode\":\"\"}"))
                .andExpect(status().isBadRequest());

        when(accessService.requireAccess(any(), any(), any())).thenReturn(null);
        when(codeRunService.run(any())).thenReturn(CodeRunResponse.builder()
                .status(CodeRunResponse.Status.SUCCESS)
                .stdout("")
                .stderr("")
                .executionTimeMs(1)
                .build());
        String requestBody = "{\"problemId\":101,\"languageId\":71,\"sourceCode\":\"print(1)\"}";

        mockMvc.perform(post("/api/code-runs")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/code-runs")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }
}
