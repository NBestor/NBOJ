package com.onlinejudge.learning.standardlibrary.persistence;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardImprovementPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiStandardImprovementPointRepository extends JpaRepository<AiStandardImprovementPoint, Long> {
    boolean existsByCode(String code);

    Optional<AiStandardImprovementPoint> findByCode(String code);

    long countByEnabledTrue();

    List<AiStandardImprovementPoint> findByEnabledTrueOrderByCategoryAscCodeAsc();

    List<AiStandardImprovementPoint> findByEnabledTrueAndPrimaryKnowledgeNodeCodeOrderByCategoryAscCodeAsc(
            String primaryKnowledgeNodeCode);
}
