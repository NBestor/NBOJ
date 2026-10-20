package com.onlinejudge.learning.standardlibrary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "ai_standard_mistake_points",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_standard_mistake_point_code",
                columnNames = "code"
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStandardMistakePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String code;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1600)
    private String description;

    @Column(nullable = false, length = 160)
    private String skillUnitCode;

    @Column(length = 80)
    private String mistakeType;

    @Column(length = 1600)
    private String misconception;

    @Column(length = 1200)
    private String symptom;

    @Column(length = 1200)
    private String repairStrategy;

    @Column(length = 40)
    private String severity;

    @Column(nullable = false, length = 160)
    private String primaryKnowledgeNodeCode;

    @Column(length = 2400)
    private String knowledgeNodeCodes;

    @Column(length = 2400)
    private String prerequisiteKnowledgeCodes;

    @Column(length = 800)
    private String applicableLanguages;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 80)
    private String libraryVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (libraryVersion == null || libraryVersion.isBlank()) {
            libraryVersion = "standard-library-normalized-v1";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
