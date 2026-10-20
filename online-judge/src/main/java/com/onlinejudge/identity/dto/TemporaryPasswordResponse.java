package com.onlinejudge.identity.dto;

public record TemporaryPasswordResponse(String temporaryPassword, boolean mustChangePassword) {
}
