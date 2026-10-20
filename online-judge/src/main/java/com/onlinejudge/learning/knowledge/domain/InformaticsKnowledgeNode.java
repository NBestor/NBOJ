package com.onlinejudge.learning.knowledge.domain;

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
        name = "informatics_knowledge_nodes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_informatics_knowledge_node_code",
                columnNames = "code"
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformaticsKnowledgeNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String code;

    @Column(length = 160)
    private String parentCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InformaticsKnowledgeNodeType type;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1600)
    private String description;

    @Column(nullable = false, length = 1200)
    private String path;

    @Column(length = 80)
    private String stage;

    @Column(length = 80)
    private String difficulty;

    @Column(length = 1600)
    private String aliases;

    @Column(length = 1600)
    private String prerequisites;

    @Column(length = 1600)
    private String learningObjectives;

    @Column(length = 1600)
    private String typicalProblems;

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
            libraryVersion = "informatics-knowledge-v1";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
