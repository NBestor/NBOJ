package com.onlinejudge.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountLoginRequest(@NotBlank String username, @NotBlank String password,
                                  @NotBlank String portal) { }
