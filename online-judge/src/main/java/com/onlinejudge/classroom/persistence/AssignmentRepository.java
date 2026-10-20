package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findAllByOrderByCreatedAtDesc();
    List<Assignment> findByClassGroupIdOrderByCreatedAtDesc(Long classGroupId);
    default List<Assignment> findByOwnerTeacherIdOrderByCreatedAtDesc(UUID ownerTeacherId) {
        return findAllByOrderByCreatedAtDesc().stream()
                .filter(assignment -> ownerTeacherId.equals(assignment.getOwnerTeacherId()))
                .toList();
    }
    default Optional<Assignment> findByIdAndOwnerTeacherId(Long id, UUID ownerTeacherId) {
        return findById(id).filter(assignment -> ownerTeacherId.equals(assignment.getOwnerTeacherId()));
    }
}
