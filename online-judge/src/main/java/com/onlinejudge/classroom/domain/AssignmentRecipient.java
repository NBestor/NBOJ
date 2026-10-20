package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_recipients", uniqueConstraints =
        @UniqueConstraint(name = "uk_assignment_recipient", columnNames = {"assignment_id", "student_profile_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentRecipient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;
    @Column(name = "student_profile_id", nullable = false)
    private Long studentProfileId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
