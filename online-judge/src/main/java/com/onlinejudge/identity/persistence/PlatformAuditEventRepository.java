package com.onlinejudge.identity.persistence;

import com.onlinejudge.identity.domain.PlatformAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditEventRepository extends JpaRepository<PlatformAuditEvent, Long> {
}
