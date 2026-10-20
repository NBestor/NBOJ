package com.onlinejudge.identity.application;

import com.onlinejudge.identity.domain.TeacherAccount;

import java.io.Serializable;
import java.util.UUID;

public record TeacherPrincipal(UUID id, String username, String displayName,
                               TeacherAccount.Role role, boolean mustChangePassword,
                               UUID schoolId, String schoolName) implements Serializable {
    public TeacherPrincipal(UUID id, String username, String displayName,
                            TeacherAccount.Role role, boolean mustChangePassword) {
        this(id, username, displayName, role, mustChangePassword, null, null);
    }
}
