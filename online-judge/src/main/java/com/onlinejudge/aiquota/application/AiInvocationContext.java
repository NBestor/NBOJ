package com.onlinejudge.aiquota.application;

import java.util.UUID;

public record AiInvocationContext(
        UUID teacherId,
        UUID schoolId,
        Long studentProfileId,
        Long assignmentId,
        Long submissionId,
        String purpose,
        String idempotencyKey
) {
    public AiInvocationContext(UUID teacherId, Long studentProfileId, Long assignmentId, Long submissionId,
                               String purpose, String idempotencyKey) {
        this(teacherId, null, studentProfileId, assignmentId, submissionId, purpose, idempotencyKey);
    }
    public static AiInvocationContext anonymous(String purpose, String idempotencyKey) {
        return new AiInvocationContext(null, null, null, null, null, purpose, idempotencyKey);
    }
}
