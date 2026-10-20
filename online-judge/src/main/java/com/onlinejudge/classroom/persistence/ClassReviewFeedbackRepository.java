package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.ClassReviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassReviewFeedbackRepository extends JpaRepository<ClassReviewFeedback, Long> {
    List<ClassReviewFeedback> findByAssignmentIdOrderByCreatedAtDesc(Long assignmentId);
    List<ClassReviewFeedback> findByAssignmentIdIn(Collection<Long> assignmentIds);
    Optional<ClassReviewFeedback> findTopByAssignmentIdAndSuggestionKeyOrderByCreatedAtDesc(Long assignmentId, String suggestionKey);
}
