package com.onlinejudge.classroom.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "assignment_tasks",
        indexes = {
                @Index(name = "idx_assignment_tasks_assignment", columnList = "assignment_id"),
                @Index(name = "idx_assignment_tasks_problem", columnList = "problem_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private Boolean required;
}
