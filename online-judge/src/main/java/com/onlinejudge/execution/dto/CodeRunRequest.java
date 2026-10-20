package com.onlinejudge.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CodeRunRequest {

    @NotNull(message = "题目 ID 不能为空")
    private Long problemId;

    private Long assignmentId;

    @NotNull(message = "语言不能为空")
    private Integer languageId;

    @NotBlank(message = "代码内容不能为空")
    private String sourceCode;

    private String stdin = "";
}
