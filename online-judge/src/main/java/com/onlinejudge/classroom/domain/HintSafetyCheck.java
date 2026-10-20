package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "hint_safety_checks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HintSafetyCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel;

    @Column(name = "blocked_reasons_json", columnDefinition = "TEXT")
    private String blockedReasonsJson;

    @Column(name = "original_hint", columnDefinition = "TEXT")
    private String originalHint;

    @Column(name = "safe_hint", columnDefinition = "TEXT")
    private String safeHint;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @PrePersist
    protected void onCreate() {
        checkedAt = LocalDateTime.now();
    }
}
