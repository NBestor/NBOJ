package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.StudentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface StudentSessionRepository extends JpaRepository<StudentSession, UUID> {
    Optional<StudentSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update StudentSession s set s.revokedAt = :now where s.studentProfileId = :studentId and s.revokedAt is null")
    int revokeAll(Long studentId, Instant now);
}
