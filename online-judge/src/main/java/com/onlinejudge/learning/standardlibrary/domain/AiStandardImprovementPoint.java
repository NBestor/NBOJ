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
        name = "ai_standard_improvement_points",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_standard_improvement_point_code",
                columnNames = "code"
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStandardImprovementPoint {

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

    @Column(length = 160)
    private String skillUnitCode;

    @Column(length = 160)
    private String primaryKnowledgeNodeCode;

    @Column(length = 2400)
    private String knowledgeNodeCodes;

    @Column(length = 1200)
    private String improvementGoal;

    @Column(length = 1200)
    private String practiceStrategy;

    @Column(length = 1200)
    private String studentBenefit;

    @Column(length = 1200)
    private String teacherExplanation;

    @Column(length = 1600)
    private String relatedMistakeCodes;

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
