package com.onlinejudge.learning.standardlibrary.persistence;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardMistakePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiStandardMistakePointRepository extends JpaRepository<AiStandardMistakePoint, Long> {
    boolean existsByCode(String code);

    Optional<AiStandardMistakePoint> findByCode(String code);

    long countByEnabledTrue();

    List<AiStandardMistakePoint> findByEnabledTrueOrderByCategoryAscCodeAsc();

    List<AiStandardMistakePoint> findByEnabledTrueAndPrimaryKnowledgeNodeCodeOrderByCategoryAscCodeAsc(
            String primaryKnowledgeNodeCode);
}
