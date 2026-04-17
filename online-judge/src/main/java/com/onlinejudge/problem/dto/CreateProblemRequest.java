package com.onlinejudge.problem.dto;

import com.onlinejudge.problem.domain.Problem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateProblemRequest {

    @NotBlank(message = "题目标题不能为空")
    private String title;

    @NotBlank(message = "题目描述不能为空")
    private String description;

    @NotNull(message = "请选择题目难度")
    private Problem.Difficulty difficulty;

    @NotNull(message = "请填写时间限制")
    @Min(value = 100, message = "时间限制不能小于 100 ms")
    private Integer timeLimit;

    @NotNull(message = "请填写内存限制")
    @Min(value = 1024, message = "内存限制不能小于 1024 KB")
    private Integer memoryLimit;

    private String aiPromptDirection;

    @Valid
    @NotEmpty(message = "至少需要一个测试点")
    private List<TestCaseRequest> testCases;

    @Data
    public static class TestCaseRequest {
        @NotNull(message = "测试点输入不能为空")
        private String input;

        @NotNull(message = "测试点标准输出不能为空")
        private String expectedOutput;

        @NotNull(message = "请指定测试点是否隐藏")
        private Boolean hidden;
    }
}

