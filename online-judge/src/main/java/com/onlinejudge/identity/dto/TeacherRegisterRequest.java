package com.onlinejudge.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeacherRegisterRequest(
        @NotBlank @Size(min = 4, max = 50) String username,
        @NotBlank @Size(min = 10, max = 100) String password,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 200) String schoolRegistrationCode) {
}
