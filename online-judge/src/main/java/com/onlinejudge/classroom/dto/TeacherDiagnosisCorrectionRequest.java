package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TeacherDiagnosisCorrectionRequest {
    @NotNull(message = "提交 ID 不能为空")
    private Long submissionId;

    private Long feedbackRevisionId;

    @NotBlank(message = "请至少选择一个修正后的错因")
    private String correctedIssueTag;

    private String correctedFineGrainedTag;
    private String correctionType = "DIAGNOSIS";
    private String targetIssueId;
    private String correctedKnowledgePath;
    private String targetEvidenceRef;
    private String teacherNote;
    private Boolean evalCandidate = true;
    private String correctedBy;
}
