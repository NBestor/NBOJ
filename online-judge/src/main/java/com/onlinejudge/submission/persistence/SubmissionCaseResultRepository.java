package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.SubmissionCaseResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubmissionCaseResultRepository extends JpaRepository<SubmissionCaseResult, Long> {
    List<SubmissionCaseResult> findBySubmissionIdOrderByTestCaseNumberAsc(Long submissionId);
    List<SubmissionCaseResult> findBySubmissionIdIn(Collection<Long> submissionIds);
    long deleteBySubmissionId(Long submissionId);
    long deleteBySubmissionIdIn(Collection<Long> submissionIds);
}

