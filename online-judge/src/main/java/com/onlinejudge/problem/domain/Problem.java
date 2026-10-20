package com.onlinejudge.problem.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.onlinejudge.shared.persistence.StringListJsonConverter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "problems")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_teacher_id")
    private UUID ownerTeacherId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Scope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_state", nullable = false, length = 30)
    private VersionState versionState;

    @Column(name = "series_id", nullable = false)
    private UUID seriesId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "source_problem_id")
    private Long sourceProblemId;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private Integer timeLimit; // in milliseconds

    @Column(nullable = false)
    private Integer memoryLimit; // in KB

    @Column(columnDefinition = "TEXT")
    private String aiPromptDirection;

    @Column(name = "starter_code", columnDefinition = "TEXT")
    private String starterCode;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "knowledge_points", columnDefinition = "TEXT")
    private List<String> knowledgePoints;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "algorithm_strategies", columnDefinition = "TEXT")
    private List<String> algorithmStrategies;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "common_mistakes", columnDefinition = "TEXT")
    private List<String> commonMistakes;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "boundary_types", columnDefinition = "TEXT")
    private List<String> boundaryTypes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (scope == null) scope = Scope.PUBLIC;
        if (versionState == null) versionState = VersionState.PUBLISHED;
        if (seriesId == null) seriesId = UUID.randomUUID();
        if (versionNo == null) versionNo = 1;
    }

    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    public enum Scope {
        PUBLIC, SHARED, PRIVATE
    }

    public enum VersionState {
        DRAFT, REVIEW_PENDING, PUBLISHED, FROZEN, REJECTED, ARCHIVED
    }
}
