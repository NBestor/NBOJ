package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "assignment_invites",
        indexes = {
                @Index(name = "idx_assignment_invites_code", columnList = "code", unique = true),
                @Index(name = "idx_assignment_invites_assignment", columnList = "assignment_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (enabled == null) {
            enabled = true;
        }
    }
}
