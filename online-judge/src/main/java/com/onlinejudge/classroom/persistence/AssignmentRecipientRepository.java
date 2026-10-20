package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.AssignmentRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AssignmentRecipientRepository extends JpaRepository<AssignmentRecipient, Long> {
    List<AssignmentRecipient> findByAssignmentId(Long assignmentId);
    List<AssignmentRecipient> findByAssignmentIdIn(Collection<Long> assignmentIds);
    boolean existsByAssignmentIdAndStudentProfileId(Long assignmentId, Long studentProfileId);
    long deleteByAssignmentId(Long assignmentId);
}
