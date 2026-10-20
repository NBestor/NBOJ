package com.onlinejudge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teacher_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherAccount {

    public static final UUID BOOTSTRAP_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    private UUID id;

    @Column(name = "username_normalized", nullable = false, unique = true, length = 80)
    private String usernameNormalized;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "school_name", nullable = false, length = 200)
    private String schoolName;

    @Column(name = "school_id")
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TeacherAccount pending(UUID id, String username, String passwordHash,
                                         String displayName, String schoolName, Instant now) {
        return pending(id, username, passwordHash, displayName, null, schoolName, now);
    }

    public static TeacherAccount pending(UUID id, String username, String passwordHash,
                                         String displayName, UUID schoolId, String schoolName, Instant now) {
        return base(id, username, passwordHash, displayName, schoolName, now)
                .schoolId(schoolId)
                .status(Status.PENDING)
                .role(Role.TEACHER)
                .build();
    }

    public static TeacherAccount schoolAdmin(UUID id, String username, String passwordHash,
                                             String displayName, UUID schoolId, String schoolName, Instant now) {
        return base(id, username, passwordHash, displayName, schoolName, now)
                .schoolId(schoolId)
                .status(Status.ACTIVE)
                .role(Role.SCHOOL_ADMIN)
                .mustChangePassword(true)
                .build();
    }

    public static TeacherAccount active(UUID id, String username, String passwordHash,
                                        String displayName, String schoolName, Instant now) {
        return base(id, username, passwordHash, displayName, schoolName, now)
                .status(Status.ACTIVE)
                .role(Role.TEACHER)
                .build();
    }

    private static TeacherAccountBuilder base(UUID id, String username, String passwordHash,
                                              String displayName, String schoolName, Instant now) {
        return TeacherAccount.builder()
                .id(id)
                .usernameNormalized(normalizeUsername(username))
                .passwordHash(passwordHash)
                .displayName(displayName)
                .schoolName(schoolName)
                .createdAt(now)
                .updatedAt(now);
    }

    public void approve(UUID reviewerId, Instant now) {
        if (status != Status.PENDING && status != Status.BOOTSTRAP_REQUIRED) {
            throw new IllegalStateException("只有待审核账号可以批准");
        }
        status = Status.ACTIVE;
        reviewedBy = reviewerId;
        reviewedAt = now;
        reviewReason = null;
        updatedAt = now;
    }

    public void reject(UUID reviewerId, String reason, Instant now) {
        status = Status.REJECTED;
        reviewedBy = reviewerId;
        reviewedAt = now;
        reviewReason = reason;
        updatedAt = now;
    }

    public void suspend(Instant now) {
        status = Status.SUSPENDED;
        updatedAt = now;
    }

    public void restore(Instant now) {
        status = Status.ACTIVE;
        failedLoginCount = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public boolean canAuthenticateAt(Instant now) {
        return status == Status.ACTIVE && (lockedUntil == null || !now.isBefore(lockedUntil));
    }

    public void recordFailedLogin(Instant now) {
        failedLoginCount++;
        if (failedLoginCount >= MAX_FAILED_LOGINS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedLoginCount = 0;
        }
        updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public void replacePassword(String encodedPassword, boolean forceChange, Instant now) {
        passwordHash = encodedPassword;
        mustChangePassword = forceChange;
        failedLoginCount = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public void passwordChanged(String encodedPassword, Instant now) {
        replacePassword(encodedPassword, false, now);
    }

    @PrePersist
    void initialize() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (role == null) role = Role.TEACHER;
        if (status == null) status = Status.PENDING;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        usernameNormalized = normalizeUsername(usernameNormalized);
    }

    public static String normalizeUsername(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public enum Role { PLATFORM_ADMIN, SCHOOL_ADMIN, TEACHER }

    public enum Status { BOOTSTRAP_REQUIRED, PENDING, ACTIVE, REJECTED, SUSPENDED }
}
