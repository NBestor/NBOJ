package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "student_profiles",
        indexes = {
                @Index(name = "idx_student_profiles_class", columnList = "class_group_id"),
                @Index(name = "idx_student_profiles_identity", columnList = "identity_key")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RosterStatus status;

    @Column(name = "class_group_id")
    private Long classGroupId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "student_no")
    private String studentNo;

    private String note;

    @Column(name = "identity_key", nullable = false)
    private String identityKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        lastSeenAt = now;
        if (status == null) {
            status = studentNo == null || studentNo.isBlank() ? RosterStatus.NEEDS_REVIEW : RosterStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastSeenAt = LocalDateTime.now();
    }

    public enum RosterStatus {
        ACTIVE, INACTIVE, NEEDS_REVIEW
    }
}
