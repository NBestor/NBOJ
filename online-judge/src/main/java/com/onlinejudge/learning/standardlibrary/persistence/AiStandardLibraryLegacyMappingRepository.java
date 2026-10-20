package com.onlinejudge.learning.standardlibrary.persistence;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLegacyMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiStandardLibraryLegacyMappingRepository extends JpaRepository<AiStandardLibraryLegacyMapping, Long> {
    Optional<AiStandardLibraryLegacyMapping> findByLegacyLayerAndLegacyCode(
            AiStandardLibraryLayer legacyLayer,
            String legacyCode);
}
