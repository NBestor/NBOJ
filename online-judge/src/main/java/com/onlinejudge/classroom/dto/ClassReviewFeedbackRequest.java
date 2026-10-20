package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ClassReviewFeedbackRequest {
    @NotBlank(message = "复盘建议标识不能为空")
    private String suggestionKey;

    @NotBlank(message = "反馈动作不能为空")
    private String actionType;

    private String targetAbility;
    private Long exampleProblemId;
    private List<String> evidenceTags;
    private String teacherNote;
    private String createdBy;
}
