package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.StudentAiFeedbackRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentAiFeedbackRevisionRepository extends JpaRepository<StudentAiFeedbackRevision, Long> {
    Optional<StudentAiFeedbackRevision> findBySubmissionIdAndGenerationKey(Long submissionId, String generationKey);
    Optional<StudentAiFeedbackRevision> findTopBySubmissionIdOrderByVersionNumberDesc(Long submissionId);
    List<StudentAiFeedbackRevision> findBySubmissionIdOrderByVersionNumberDesc(Long submissionId);
    List<StudentAiFeedbackRevision> findBySubmissionIdIn(Collection<Long> submissionIds);
}
