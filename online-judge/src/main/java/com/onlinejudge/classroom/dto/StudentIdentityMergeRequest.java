package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class StudentIdentityMergeRequest {
    @NotEmpty
    private List<Long> studentProfileIds;
    private Long targetStudentProfileId;
}
