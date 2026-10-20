package com.onlinejudge.aiquota.persistence;

import com.onlinejudge.aiquota.domain.AiUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AiUsageEventRepository extends JpaRepository<AiUsageEvent, Long> {
    boolean existsByTeacherIdAndIdempotencyKeyAndChargedTrue(UUID teacherId, String idempotencyKey);
    long countByTeacherIdAndIdempotencyKey(UUID teacherId, String idempotencyKey);
    List<AiUsageEvent> findByTeacherIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID teacherId, Instant from, Instant to);
}
