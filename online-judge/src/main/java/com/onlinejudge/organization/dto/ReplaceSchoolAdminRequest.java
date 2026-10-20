package com.onlinejudge.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplaceSchoolAdminRequest(@NotBlank @Size(min = 4, max = 50) String username,
                                        @NotBlank @Size(max = 120) String displayName) { }
