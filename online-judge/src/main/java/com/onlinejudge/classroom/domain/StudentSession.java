package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSession {
    @Id
    private UUID id;
    @Column(name = "student_profile_id", nullable = false)
    private Long studentProfileId;
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
