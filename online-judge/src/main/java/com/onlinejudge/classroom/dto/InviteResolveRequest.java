package com.onlinejudge.classroom.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InviteResolveRequest {
    @NotBlank(message = "邀请码不能为空")
    @JsonAlias("inviteCode")
    private String code;
}
