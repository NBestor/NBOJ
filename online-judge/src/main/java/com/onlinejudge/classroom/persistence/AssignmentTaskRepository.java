package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.AssignmentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AssignmentTaskRepository extends JpaRepository<AssignmentTask, Long> {
    List<AssignmentTask> findByAssignmentIdOrderByOrderIndexAsc(Long assignmentId);
    List<AssignmentTask> findByAssignmentIdIn(Collection<Long> assignmentIds);
    boolean existsByAssignmentIdAndProblemId(Long assignmentId, Long problemId);
    long deleteByAssignmentId(Long assignmentId);
}
