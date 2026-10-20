package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.AssignmentInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssignmentInviteRepository extends JpaRepository<AssignmentInvite, Long> {
    Optional<AssignmentInvite> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}
