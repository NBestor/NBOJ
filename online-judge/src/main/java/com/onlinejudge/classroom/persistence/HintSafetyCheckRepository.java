package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.HintSafetyCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface HintSafetyCheckRepository extends JpaRepository<HintSafetyCheck, Long> {
    Optional<HintSafetyCheck> findTopBySubmissionIdOrderByCheckedAtDesc(Long submissionId);

    List<HintSafetyCheck> findBySubmissionIdIn(Collection<Long> submissionIds);
}
