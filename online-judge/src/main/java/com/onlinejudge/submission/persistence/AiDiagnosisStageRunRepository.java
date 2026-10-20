package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.AiDiagnosisStageRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiDiagnosisStageRunRepository extends JpaRepository<AiDiagnosisStageRun, Long> {
    Optional<AiDiagnosisStageRun> findByRunIdAndStageKey(Long runId, String stageKey);

    List<AiDiagnosisStageRun> findByRunIdOrderByIdAsc(Long runId);

    List<AiDiagnosisStageRun> findByRunIdAndStatusIn(Long runId, Collection<String> statuses);
}
