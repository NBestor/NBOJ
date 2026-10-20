package com.onlinejudge.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@NotBlank String currentPassword,
                                    @NotBlank @Size(min = 10, max = 100) String newPassword) {
}
