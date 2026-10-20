package com.onlinejudge.organization.domain;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schools")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class School {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "registration_code_hash", nullable = false, unique = true, length = 64)
    private String registrationCodeHash;

    @Column(name = "admin_account_id", nullable = false, unique = true)
    private UUID adminAccountId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static School create(UUID id, String name, String registrationCodeHash,
                                UUID adminAccountId, UUID createdBy, Instant now) {
        return School.builder().id(id).name(name).status(Status.ACTIVE)
                .registrationCodeHash(registrationCodeHash).adminAccountId(adminAccountId)
                .createdBy(createdBy).createdAt(now).updatedAt(now).build();
    }

    public void suspend(Instant now) { status = Status.SUSPENDED; updatedAt = now; }
    public void restore(Instant now) { status = Status.ACTIVE; updatedAt = now; }
    public void rotateRegistrationCode(String hash, Instant now) { registrationCodeHash = hash; updatedAt = now; }
    public void replaceAdministrator(UUID accountId, Instant now) { adminAccountId = accountId; updatedAt = now; }

    @PrePersist
    void initialize() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = Status.ACTIVE;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public enum Status { ACTIVE, SUSPENDED }
}
