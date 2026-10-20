package com.onlinejudge.learning.standardlibrary.persistence;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryRelation;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryRelationType;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiStandardLibraryRelationRepository extends JpaRepository<AiStandardLibraryRelation, Long> {
    Optional<AiStandardLibraryRelation> findBySourceTypeAndSourceCodeAndRelationTypeAndTargetTypeAndTargetCode(
            AiStandardLibraryTargetType sourceType,
            String sourceCode,
            AiStandardLibraryRelationType relationType,
            AiStandardLibraryTargetType targetType,
            String targetCode);
}
