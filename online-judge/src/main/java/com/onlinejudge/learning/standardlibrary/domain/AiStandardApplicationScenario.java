package com.onlinejudge.learning.standardlibrary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "ai_standard_application_scenarios",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ai_standard_application_scenario_code",
                        columnNames = "code"
                ),
                @UniqueConstraint(
                        name = "uk_ai_standard_application_scenario_pair_context",
                        columnNames = {"transfer_pair_code", "context_type"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_ai_standard_application_scenario_knowledge",
                        columnList = "knowledge_point_code, enabled, sort_order, code"
                ),
                @Index(
                        name = "idx_ai_standard_application_scenario_skill",
                        columnList = "skill_unit_code, enabled, sort_order, code"
                )
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStandardApplicationScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String code;

    @Column(nullable = false, length = 120)
    private String transferPairCode;

    @Column(nullable = false, length = 24)
    private String contextType;

    @Column(nullable = false, length = 40)
    private String learningPhase;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 160)
    private String knowledgePointCode;

    @Column(nullable = false, length = 160)
    private String skillUnitCode;

    @Column(length = 2400)
    private String linkedMistakeCodes;

    @Column(length = 2400)
    private String linkedImprovementCodes;

    @Column(nullable = false, length = 2400)
    private String taskContext;

    @Column(nullable = false, length = 2000)
    private String studentTask;

    @Column(nullable = false, length = 2000)
    private String observableEvidence;

    @Column(nullable = false, length = 2000)
    private String commonFailure;

    @Column(nullable = false, length = 2000)
    private String teacherMove;

    @Column(nullable = false, length = 1600)
    private String studentCheck;

    @Column(nullable = false, length = 1600)
    private String constraintProfile;

    @Column(nullable = false, length = 2000)
    private String successCriteria;

    @Column(nullable = false, length = 1600)
    private String transferNote;

    @Column(nullable = false, length = 24)
    private String difficultyLevel;

    @Column(length = 800)
    private String applicableLanguages;

    @Column(nullable = false, length = 80)
    private String sourceFramework;

    @Column(nullable = false, length = 1000)
    private String sourceReference;

    @Column(nullable = false, length = 40)
    private String reviewStatus;

    @Column(nullable = false)
    private int sortOrder;

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
            libraryVersion = "standard-library-application-scenario-v1";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
