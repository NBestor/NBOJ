package com.onlinejudge.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record TeacherPasswordLoginRequest(@NotBlank String username, @NotBlank String password) {
}
