package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.CoachPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoachPromptRepository extends JpaRepository<CoachPrompt, Long> {
    Optional<CoachPrompt> findTopBySubmissionIdOrderByCreatedAtDesc(Long submissionId);
    default Optional<CoachPrompt> findTopBySubmissionIdAndPromptTypeOrderByCreatedAtDesc(Long submissionId, String promptType) {
        return findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(submissionId).stream()
                .filter(prompt -> promptType != null && promptType.equals(prompt.getPromptType()))
                .reduce((left, right) -> right);
    }
    List<CoachPrompt> findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(Long submissionId);
    List<CoachPrompt> findBySubmissionIdIn(Collection<Long> submissionIds);
}
