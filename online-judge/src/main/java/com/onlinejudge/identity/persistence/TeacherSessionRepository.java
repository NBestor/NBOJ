package com.onlinejudge.identity.persistence;

import com.onlinejudge.identity.domain.TeacherSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TeacherSessionRepository extends JpaRepository<TeacherSession, UUID> {
    Optional<TeacherSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update TeacherSession s set s.revokedAt = :now where s.teacherId = :teacherId and s.revokedAt is null")
    int revokeAll(UUID teacherId, Instant now);
}
