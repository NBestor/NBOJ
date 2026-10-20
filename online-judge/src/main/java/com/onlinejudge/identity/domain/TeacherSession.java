package com.onlinejudge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teacher_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherSession {
    @Id
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    public boolean validAt(Instant now) {
        return revokedAt == null && expiresAt != null && now.isBefore(expiresAt);
    }
}
