package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubmissionDiagnosisFactRepository extends JpaRepository<SubmissionDiagnosisFact, Long> {
    List<SubmissionDiagnosisFact> findByAnalysisId(Long analysisId);
    List<SubmissionDiagnosisFact> findBySubmissionId(Long submissionId);
    List<SubmissionDiagnosisFact> findBySubmissionIdIn(Collection<Long> submissionIds);
    List<SubmissionDiagnosisFact> findByNormalizedPointKey(String normalizedPointKey);
    boolean existsByFactKey(String factKey);
}
