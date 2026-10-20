package com.onlinejudge.organization.persistence;

import com.onlinejudge.organization.domain.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolRepository extends JpaRepository<School, UUID> {
    Optional<School> findByRegistrationCodeHash(String registrationCodeHash);
    boolean existsByNameIgnoreCase(String name);
    Optional<School> findByNameIgnoreCase(String name);
    List<School> findAllByOrderByCreatedAtDesc();
}
