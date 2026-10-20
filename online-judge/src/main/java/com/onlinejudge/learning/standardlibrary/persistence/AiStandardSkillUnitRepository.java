package com.onlinejudge.learning.standardlibrary.persistence;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardSkillUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiStandardSkillUnitRepository extends JpaRepository<AiStandardSkillUnit, Long> {
    boolean existsByCode(String code);

    Optional<AiStandardSkillUnit> findByCode(String code);

    long countByEnabledTrue();

    List<AiStandardSkillUnit> findByEnabledTrueOrderByCategoryAscCodeAsc();

    List<AiStandardSkillUnit> findByEnabledTrueAndCodeInOrderByCategoryAscCodeAsc(Collection<String> codes);

    List<AiStandardSkillUnit> findByEnabledTrueAndPrimaryKnowledgeNodeCodeOrderByCategoryAscCodeAsc(
            String primaryKnowledgeNodeCode);
}
