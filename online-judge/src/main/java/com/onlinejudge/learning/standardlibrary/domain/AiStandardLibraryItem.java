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
        name = "ai_standard_library_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_standard_library_layer_code",
                columnNames = {"layer", "code"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStandardLibraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AiStandardLibraryLayer layer;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1600)
    private String description;

    @Column(length = 1200)
    private String studentExplanation;

    @Column(length = 1200)
    private String teacherExplanation;

    @Column(length = 160)
    private String skillUnitCode;

    @Column(length = 160)
    private String primaryKnowledgeNodeCode;

    @Column(length = 80)
    private String mistakeType;

    @Column(length = 1600)
    private String commonMisconception;

    @Column(length = 2400)
    private String evidenceSignals;

    @Column(length = 2400)
    private String commonCodePatterns;

    @Column(length = 1200)
    private String judgeSignals;

    @Column(length = 1600)
    private String requiredEvidence;

    @Column(length = 800)
    private String whenToUse;

    @Column(length = 800)
    private String studentBenefit;

    @Column(length = 800)
    private String hintL1;

    @Column(length = 800)
    private String hintL2;

    @Column(length = 800)
    private String hintL3;

    @Column(length = 120)
    private String abilityPoint;

    @Column(length = 40)
    private String severity;

    @Column(length = 800)
    private String applicableLanguages;

    @Column(length = 1600)
    private String relatedItems;

    @Column(length = 2400)
    private String knowledgeNodeCodes;

    @Column(length = 2400)
    private String relatedKnowledgeNodeCodes;

    @Column(length = 2400)
    private String prerequisiteKnowledgeCodes;

    @Column(length = 120)
    private String teachingAction;

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
            libraryVersion = "standard-library-db-v1";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
