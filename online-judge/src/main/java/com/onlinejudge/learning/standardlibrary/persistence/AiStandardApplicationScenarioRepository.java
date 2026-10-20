package com.onlinejudge.learning.standardlibrary.persistence;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardApplicationScenario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiStandardApplicationScenarioRepository
        extends JpaRepository<AiStandardApplicationScenario, Long> {

    Optional<AiStandardApplicationScenario> findByCode(String code);

    List<AiStandardApplicationScenario>
    findByEnabledTrueAndKnowledgePointCodeOrderBySortOrderAscCodeAsc(String knowledgePointCode);

    List<AiStandardApplicationScenario>
    findByEnabledTrueAndSkillUnitCodeInOrderBySortOrderAscCodeAsc(Collection<String> skillUnitCodes);

    List<AiStandardApplicationScenario>
    findByEnabledTrueAndKnowledgePointCodeInOrderBySortOrderAscCodeAsc(Collection<String> knowledgePointCodes);
}
