package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.SubmissionEvidenceBackfillBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionEvidenceBackfillBatchRepository extends JpaRepository<SubmissionEvidenceBackfillBatch, Long> {
}
