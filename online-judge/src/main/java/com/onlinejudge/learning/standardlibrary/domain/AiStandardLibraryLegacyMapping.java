package com.onlinejudge.learning.standardlibrary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_standard_library_legacy_mappings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_standard_library_legacy_mapping",
                columnNames = {"legacy_layer", "legacy_code"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStandardLibraryLegacyMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "legacy_layer", nullable = false, length = 40)
    private AiStandardLibraryLayer legacyLayer;

    @Column(name = "legacy_code", nullable = false, length = 160)
    private String legacyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private AiStandardLibraryTargetType targetType;

    @Column(name = "target_code", nullable = false, length = 160)
    private String targetCode;

    @Column(name = "migration_status", nullable = false, length = 40)
    private String migrationStatus;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "source_version", length = 80)
    private String sourceVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (migrationStatus == null || migrationStatus.isBlank()) {
            migrationStatus = "MAPPED";
        }
        if (confidence <= 0) {
            confidence = 1.0;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
