package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "class_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_teacher_id", nullable = false)
    private UUID ownerTeacherId;

    @Column(name = "join_code_hash", length = 100)
    private String joinCodeHash;

    @Column(nullable = false)
    private String name;

    private String grade;

    @Column(name = "teacher_name")
    private String teacherName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (ownerTeacherId == null) {
            ownerTeacherId = com.onlinejudge.identity.domain.TeacherAccount.BOOTSTRAP_ADMIN_ID;
        }
    }
}
