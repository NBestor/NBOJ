package com.onlinejudge.aiquota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "teacher_ai_quotas", uniqueConstraints =
        @UniqueConstraint(name = "uk_teacher_ai_quota_month", columnNames = {"teacher_id", "quota_month"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherAiQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "quota_month", nullable = false, length = 7)
    private String quotaMonth;

    @Column(name = "base_units", nullable = false)
    private int baseUnits;

    @Column(name = "additional_units", nullable = false)
    private int additionalUnits;

    @Column(name = "used_units", nullable = false)
    private int usedUnits;

    @Column(name = "reserved_units", nullable = false)
    private int reservedUnits;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static TeacherAiQuota forMonth(UUID teacherId, YearMonth month, int baseUnits) {
        return TeacherAiQuota.builder()
                .teacherId(teacherId)
                .quotaMonth(month.toString())
                .baseUnits(Math.max(0, baseUnits))
                .updatedAt(Instant.now())
                .build();
    }

    public int remaining() {
        return Math.max(0, baseUnits + additionalUnits - usedUnits - reservedUnits);
    }

    public void reserve() {
        if (remaining() < 1) throw new QuotaExhaustedException();
        reservedUnits++;
        updatedAt = Instant.now();
    }

    public void settleSuccess() {
        if (reservedUnits < 1) throw new IllegalStateException("没有可结算的额度预留");
        reservedUnits--;
        usedUnits++;
        updatedAt = Instant.now();
    }

    public void settleFailure() {
        if (reservedUnits > 0) reservedUnits--;
        updatedAt = Instant.now();
    }

    public void adjust(int base, int additional) {
        baseUnits = Math.max(0, base);
        additionalUnits = Math.max(0, additional);
        updatedAt = Instant.now();
    }

    public void allocate(int units) {
        int normalized = Math.max(0, units);
        if (normalized < usedUnits + reservedUnits) {
            throw new IllegalArgumentException("额度不能低于已用或预留数量");
        }
        baseUnits = normalized;
        additionalUnits = 0;
        updatedAt = Instant.now();
    }

    @PrePersist
    void initialize() {
        if (updatedAt == null) updatedAt = Instant.now();
    }
}
