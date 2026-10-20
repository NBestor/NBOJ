package com.onlinejudge.classroom.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StudentIdentitySplitRequest {
    @NotNull
    private Long studentProfileId;
}
