package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.ClassGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassGroupRepository extends JpaRepository<ClassGroup, Long> {
    List<ClassGroup> findAllByOrderByCreatedAtDesc();
    Optional<ClassGroup> findByNameIgnoreCase(String name);
    default List<ClassGroup> findByOwnerTeacherIdOrderByCreatedAtDesc(UUID ownerTeacherId) {
        return findAllByOrderByCreatedAtDesc().stream()
                .filter(classGroup -> ownerTeacherId.equals(classGroup.getOwnerTeacherId()))
                .toList();
    }
    default Optional<ClassGroup> findByIdAndOwnerTeacherId(Long id, UUID ownerTeacherId) {
        return findById(id).filter(classGroup -> ownerTeacherId.equals(classGroup.getOwnerTeacherId()));
    }
    default List<ClassGroup> findByOwnerTeacherIdAndNameIgnoreCase(UUID ownerTeacherId, String name) {
        return findByNameIgnoreCase(name)
                .filter(classGroup -> ownerTeacherId.equals(classGroup.getOwnerTeacherId()))
                .map(List::of)
                .orElseGet(List::of);
    }
}
