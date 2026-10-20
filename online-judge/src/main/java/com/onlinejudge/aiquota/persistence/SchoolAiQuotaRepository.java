package com.onlinejudge.aiquota.persistence;

import com.onlinejudge.aiquota.domain.SchoolAiQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface SchoolAiQuotaRepository extends JpaRepository<SchoolAiQuota, Long> {
    Optional<SchoolAiQuota> findBySchoolIdAndQuotaMonth(UUID schoolId, String quotaMonth);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SchoolAiQuota> findLockedBySchoolIdAndQuotaMonth(UUID schoolId, String quotaMonth);
}
