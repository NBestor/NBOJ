package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentAssignmentSubmissionQueryRepository extends JpaRepository<Submission, Long> {

    @Query("""
            select s from Submission s
            where s.assignmentId = :assignmentId
              and s.studentProfileId = :studentProfileId
              and (:problemId is null or s.problemId = :problemId)
              and (:accepted is null
                   or (:accepted = true and s.verdict = :acceptedVerdict)
                   or (:accepted = false and s.verdict <> :acceptedVerdict))
              and (:verdict is null or s.verdict = :verdict)
              and (:languageName is null or lower(s.languageName) = lower(:languageName))
              and (:submissionId is null or s.id = :submissionId)
            order by s.submittedAt desc, s.id desc
            """)
    Page<Submission> findStudentAssignmentSubmissions(@Param("assignmentId") Long assignmentId,
                                                       @Param("studentProfileId") Long studentProfileId,
                                                       @Param("problemId") Long problemId,
                                                       @Param("accepted") Boolean accepted,
                                                       @Param("acceptedVerdict") Submission.Verdict acceptedVerdict,
                                                       @Param("verdict") Submission.Verdict verdict,
                                                       @Param("languageName") String languageName,
                                                       @Param("submissionId") Long submissionId,
                                                       Pageable pageable);
}
