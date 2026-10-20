package com.onlinejudge.identity.application;

import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final TeacherAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    @Value("${security.bootstrap-admin.username:${BOOTSTRAP_ADMIN_USERNAME:}}")
    private String username;
    @Value("${security.bootstrap-admin.password:${BOOTSTRAP_ADMIN_PASSWORD:}}")
    private String password;
    @Value("${security.bootstrap-admin.display-name:${BOOTSTRAP_ADMIN_DISPLAY_NAME:平台管理员}}")
    private String displayName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        TeacherAccount account = accounts.findById(TeacherAccount.BOOTSTRAP_ADMIN_ID)
                .orElseGet(this::newBootstrapAccount);
        if (account.getStatus() != TeacherAccount.Status.BOOTSTRAP_REQUIRED) return;
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            accounts.save(account);
            return;
        }
        if (password.length() < 10 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD 必须至少 10 位并包含字母和数字");
        }
        String normalized = TeacherAccount.normalizeUsername(username);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{3,49}")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME 格式无效");
        }
        account.setUsernameNormalized(normalized);
        account.setDisplayName(displayName == null || displayName.isBlank() ? "平台管理员" : displayName.trim());
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setMustChangePassword(false);
        account.approve(TeacherAccount.BOOTSTRAP_ADMIN_ID, Instant.now());
        accounts.save(account);
    }

    private TeacherAccount newBootstrapAccount() {
        return TeacherAccount.builder()
                .id(TeacherAccount.BOOTSTRAP_ADMIN_ID)
                .usernameNormalized("__bootstrap_admin__")
                .passwordHash("!BOOTSTRAP_REQUIRED!")
                .displayName("平台管理员").schoolName("平台")
                .role(TeacherAccount.Role.PLATFORM_ADMIN).status(TeacherAccount.Status.BOOTSTRAP_REQUIRED)
                .mustChangePassword(true).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
