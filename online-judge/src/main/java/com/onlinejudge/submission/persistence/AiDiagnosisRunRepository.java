package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.AiDiagnosisRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiDiagnosisRunRepository extends JpaRepository<AiDiagnosisRun, Long> {
    Optional<AiDiagnosisRun> findByGenerationKey(String generationKey);

    Optional<AiDiagnosisRun> findTopBySubmissionIdOrderByVersionNumberDesc(Long submissionId);

    List<AiDiagnosisRun> findBySubmissionIdOrderByVersionNumberDesc(Long submissionId);

    List<AiDiagnosisRun> findByStatusIn(Collection<String> statuses);
}
