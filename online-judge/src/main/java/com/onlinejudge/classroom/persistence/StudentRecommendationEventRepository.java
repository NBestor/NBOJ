package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRecommendationEventRepository extends JpaRepository<StudentRecommendationEvent, Long> {
    List<StudentRecommendationEvent> findByStudentProfileIdOrderByCreatedAtDesc(Long studentProfileId);
    List<StudentRecommendationEvent> findTop500ByOrderByCreatedAtDesc();
    List<StudentRecommendationEvent> findTop500ByAssignmentIdOrderByCreatedAtDesc(Long assignmentId);
    List<StudentRecommendationEvent> findByFollowupSubmissionIdAndEventTypeOrderByCreatedAtDesc(Long followupSubmissionId, String eventType);
    Optional<StudentRecommendationEvent> findTopByRecommendationTokenAndEventTypeOrderByCreatedAtDesc(String recommendationToken, String eventType);
}
