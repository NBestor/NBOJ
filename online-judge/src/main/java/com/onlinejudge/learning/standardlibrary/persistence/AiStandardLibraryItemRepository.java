package com.onlinejudge.learning.standardlibrary.persistence;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiStandardLibraryItemRepository extends JpaRepository<AiStandardLibraryItem, Long> {
    boolean existsByLayerAndCode(AiStandardLibraryLayer layer, String code);

    Optional<AiStandardLibraryItem> findByLayerAndCode(AiStandardLibraryLayer layer, String code);

    List<AiStandardLibraryItem> findByEnabledTrueOrderByLayerAscCategoryAscCodeAsc();

    List<AiStandardLibraryItem> findAllByOrderByLayerAscCategoryAscCodeAsc();

    List<AiStandardLibraryItem> findByEnabledTrueAndKnowledgeNodeCodesContainingOrderByLayerAscCategoryAscCodeAsc(String knowledgeNodeCode);
}
