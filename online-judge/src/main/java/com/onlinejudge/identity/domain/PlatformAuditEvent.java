package com.onlinejudge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_audit_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_teacher_id")
    private UUID actorTeacherId;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(name = "target_type", length = 80)
    private String targetType;
    @Column(name = "target_id", length = 100)
    private String targetId;
    @Column(length = 1000)
    private String detail;
    @Column(name = "ip_address", length = 80)
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
