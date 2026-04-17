package com.onlinejudge.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmissionRequest {
    
    @NotNull(message = "题目 ID 不能为空")
    private Long problemId;
    
    @NotNull(message = "语言不能为空")
    private Integer languageId;
    
    @NotBlank(message = "代码内容不能为空")
    private String sourceCode;
}

