package com.onlinejudge.identity.dto;

import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.identity.domain.TeacherAccount;

import java.util.UUID;

public record TeacherSessionResponse(boolean authenticated, UUID teacherId, String username,
                                     String displayName, TeacherAccount.Role role,
                                     boolean mustChangePassword, UUID schoolId, String schoolName) {
    public static TeacherSessionResponse anonymous() {
        return new TeacherSessionResponse(false, null, null, null, null, false, null, null);
    }

    public static TeacherSessionResponse from(TeacherPrincipal principal) {
        return new TeacherSessionResponse(true, principal.id(), principal.username(), principal.displayName(),
                principal.role(), principal.mustChangePassword(), principal.schoolId(), principal.schoolName());
    }
}
