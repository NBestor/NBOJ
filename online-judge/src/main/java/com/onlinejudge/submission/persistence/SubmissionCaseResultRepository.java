package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.SubmissionCaseResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubmissionCaseResultRepository extends JpaRepository<SubmissionCaseResult, Long> {
    List<SubmissionCaseResult> findBySubmissionIdOrderByTestCaseNumberAsc(Long submissionId);
    List<SubmissionCaseResult> findBySubmissionIdIn(Collection<Long> submissionIds);

    @Query(value = """
            select
                submission_id as submissionId,
                sum(case when passed = true then 1 else 0 end) as passedTestCases,
                count(*) as totalTestCases
            from submission_case_results
            where submission_id in (:submissionIds)
            group by submission_id
            """, nativeQuery = true)
    List<SubmissionCaseResultStatsProjection> summarizeBySubmissionIdIn(Collection<Long> submissionIds);

    long deleteBySubmissionId(Long submissionId);
    long deleteBySubmissionIdIn(Collection<Long> submissionIds);
}

