package com.onlinejudge.aiquota.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "school_ai_quotas", uniqueConstraints =
        @UniqueConstraint(name = "uk_school_ai_quota_month", columnNames = {"school_id", "quota_month"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolAiQuota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "school_id", nullable = false)
    private UUID schoolId;
    @Column(name = "quota_month", nullable = false, length = 7)
    private String quotaMonth;
    @Column(name = "base_units", nullable = false)
    private int baseUnits;
    @Column(name = "additional_units", nullable = false)
    private int additionalUnits;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    public static SchoolAiQuota forMonth(UUID schoolId, YearMonth month, int baseUnits) {
        return SchoolAiQuota.builder().schoolId(schoolId).quotaMonth(month.toString())
                .baseUnits(Math.max(0, baseUnits)).updatedAt(Instant.now()).build();
    }

    public int total() { return baseUnits + additionalUnits; }
    public int availableToAllocate(int allocated) { return Math.max(0, total() - Math.max(0, allocated)); }

    public void adjust(int base, int additional, int allocated) {
        int normalizedBase = Math.max(0, base);
        int normalizedAdditional = Math.max(0, additional);
        if (normalizedBase + normalizedAdditional < allocated) {
            throw new IllegalArgumentException("学校额度不能低于已分配总量");
        }
        baseUnits = normalizedBase;
        additionalUnits = normalizedAdditional;
        updatedAt = Instant.now();
    }

    @PrePersist
    void initialize() { if (updatedAt == null) updatedAt = Instant.now(); }
}
