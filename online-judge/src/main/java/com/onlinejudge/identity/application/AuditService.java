package com.onlinejudge.identity.application;

import com.onlinejudge.identity.domain.PlatformAuditEvent;
import com.onlinejudge.identity.persistence.PlatformAuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final PlatformAuditEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String eventType, String targetType, Object targetId, String detail, String ipAddress) {
        repository.save(PlatformAuditEvent.builder()
                .actorTeacherId(actorId)
                .eventType(eventType)
                .targetType(targetType)
                .targetId(targetId == null ? null : String.valueOf(targetId))
                .detail(sanitize(detail))
                .ipAddress(ipAddress)
                .build());
    }

    private String sanitize(String detail) {
        if (detail == null) return null;
        String sanitized = detail.replaceAll("(?i)(password|token|api[-_ ]?key)\\s*[:=]\\s*\\S+", "$1=[REDACTED]");
        return sanitized.length() > 1000 ? sanitized.substring(0, 1000) : sanitized;
    }
}
