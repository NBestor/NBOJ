package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentAiFeedbackEventRepository extends JpaRepository<StudentAiFeedbackEvent, Long> {
    Optional<StudentAiFeedbackEvent> findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(Long submissionId, String eventType);
    List<StudentAiFeedbackEvent> findBySubmissionIdIn(Collection<Long> submissionIds);
    List<StudentAiFeedbackEvent> findByAssignmentIdOrderByCreatedAtDesc(Long assignmentId);
    List<StudentAiFeedbackEvent> findByStudentProfileIdAndAssignmentIdOrderByCreatedAtDesc(Long studentProfileId, Long assignmentId);
}
