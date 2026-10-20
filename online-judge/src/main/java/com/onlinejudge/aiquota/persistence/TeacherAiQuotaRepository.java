package com.onlinejudge.aiquota.persistence;

import com.onlinejudge.aiquota.domain.TeacherAiQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeacherAiQuotaRepository extends JpaRepository<TeacherAiQuota, Long> {
    Optional<TeacherAiQuota> findByTeacherIdAndQuotaMonth(UUID teacherId, String quotaMonth);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TeacherAiQuota> findLockedByTeacherIdAndQuotaMonth(UUID teacherId, String quotaMonth);
}
