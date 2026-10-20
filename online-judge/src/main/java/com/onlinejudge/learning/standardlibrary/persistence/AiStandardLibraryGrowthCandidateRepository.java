package com.onlinejudge.learning.standardlibrary.persistence;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryGrowthCandidate;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiStandardLibraryGrowthCandidateRepository
        extends JpaRepository<AiStandardLibraryGrowthCandidate, Long> {

    Optional<AiStandardLibraryGrowthCandidate> findByLayerAndSuggestedCode(
            AiStandardLibraryLayer layer,
            String suggestedCode
    );

    Optional<AiStandardLibraryGrowthCandidate> findByLayerAndParentKnowledgeNodeCodeAndSuggestedCode(
            AiStandardLibraryLayer layer,
            String parentKnowledgeNodeCode,
            String suggestedCode
    );

    List<AiStandardLibraryGrowthCandidate> findAllByOrderByCreatedAtDesc();
}
