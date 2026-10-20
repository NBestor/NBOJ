package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.SubmissionIssueTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubmissionIssueTransitionRepository extends JpaRepository<SubmissionIssueTransition, Long> {
    List<SubmissionIssueTransition> findByCurrentSubmissionIdOrderByDisplayCategoryAscTitleAsc(Long currentSubmissionId);
    List<SubmissionIssueTransition> findByCurrentSubmissionIdIn(Collection<Long> currentSubmissionIds);
    List<SubmissionIssueTransition> findByStudentProfileIdOrderByCurrentSubmissionIdAsc(Long studentProfileId);
    boolean existsByTransitionKey(String transitionKey);
}
