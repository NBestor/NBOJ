package com.onlinejudge.learning.knowledge.persistence;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InformaticsKnowledgeNodeRepository extends JpaRepository<InformaticsKnowledgeNode, Long> {

    boolean existsByCode(String code);

    Optional<InformaticsKnowledgeNode> findByCode(String code);

    List<InformaticsKnowledgeNode> findByCodeIn(Collection<String> codes);

    List<InformaticsKnowledgeNode> findByEnabledTrueAndParentCodeIsNullOrderBySortOrderAscCodeAsc();

    List<InformaticsKnowledgeNode> findByEnabledTrueAndParentCodeOrderBySortOrderAscCodeAsc(String parentCode);

    Page<InformaticsKnowledgeNode> findByEnabledTrueAndParentCodeOrderBySortOrderAscCodeAsc(
            String parentCode,
            Pageable pageable);

    boolean existsByEnabledTrueAndParentCode(String parentCode);

    long countByEnabledTrue();

    List<InformaticsKnowledgeNode> findByEnabledTrueOrderByPathAscSortOrderAscCodeAsc();

    List<InformaticsKnowledgeNode> findAllByOrderByPathAscSortOrderAscCodeAsc();
}
