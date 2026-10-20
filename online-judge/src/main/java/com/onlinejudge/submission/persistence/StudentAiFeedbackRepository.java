package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.StudentAiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentAiFeedbackRepository extends JpaRepository<StudentAiFeedback, Long> {
    Optional<StudentAiFeedback> findBySubmissionId(Long submissionId);
    List<StudentAiFeedback> findBySubmissionIdIn(Collection<Long> submissionIds);
}
