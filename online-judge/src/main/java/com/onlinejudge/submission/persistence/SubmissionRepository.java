package com.onlinejudge.submission.persistence;

import com.onlinejudge.leaderboard.persistence.ProblemSubmissionStatsProjection;
import com.onlinejudge.submission.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByProblemIdOrderBySubmittedAtDesc(Long problemId);
    List<Submission> findByProblemIdOrderBySubmittedAtAsc(Long problemId);
    List<Submission> findTop10ByOrderBySubmittedAtDesc();

    @Query(value = """
            select
                problem_id as problemId,
                count(*) as totalSubmissions,
                sum(case when verdict = 'ACCEPTED' then 1 else 0 end) as acceptedSubmissions,
                min(case when verdict = 'ACCEPTED' then execution_time else null end) as bestAcceptedTime,
                max(submitted_at) as lastSubmittedAt
            from submissions
            group by problem_id
            """, nativeQuery = true)
    List<ProblemSubmissionStatsProjection> summarizeByProblem();

    long deleteByProblemId(Long problemId);
}

