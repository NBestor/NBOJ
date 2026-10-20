package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthSessionResponse {
    private boolean authenticated;
}
