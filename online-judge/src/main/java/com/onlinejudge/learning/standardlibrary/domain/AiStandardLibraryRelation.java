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
        name = "ai_standard_library_relations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_standard_library_relation",
                columnNames = {"source_type", "source_code", "relation_type", "target_type", "target_code"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStandardLibraryRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private AiStandardLibraryTargetType sourceType;

    @Column(name = "source_code", nullable = false, length = 160)
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 40)
    private AiStandardLibraryRelationType relationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private AiStandardLibraryTargetType targetType;

    @Column(name = "target_code", nullable = false, length = 160)
    private String targetCode;

    @Column(length = 800)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
