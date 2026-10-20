package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionAnalysisRepository extends JpaRepository<SubmissionAnalysis, Long> {
    Optional<SubmissionAnalysis> findBySubmissionId(Long submissionId);
    List<SubmissionAnalysis> findBySubmissionIdIn(Collection<Long> submissionIds);
    long deleteBySubmissionId(Long submissionId);
    long deleteBySubmissionIdIn(Collection<Long> submissionIds);
}

