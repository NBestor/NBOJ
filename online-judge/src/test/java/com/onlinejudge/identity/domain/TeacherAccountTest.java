package com.onlinejudge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherAccountTest {

    @Test
    void pendingAccountCannotAuthenticateUntilAdminApprovesIt() {
        TeacherAccount account = TeacherAccount.pending(
                UUID.randomUUID(), "teacher_01", "hash", "王老师", "第一中学", Instant.parse("2026-08-01T00:00:00Z"));

        assertThat(account.canAuthenticateAt(Instant.parse("2026-08-01T01:00:00Z"))).isFalse();

        account.approve(UUID.randomUUID(), Instant.parse("2026-08-01T02:00:00Z"));

        assertThat(account.getStatus()).isEqualTo(TeacherAccount.Status.ACTIVE);
        assertThat(account.canAuthenticateAt(Instant.parse("2026-08-01T02:00:01Z"))).isTrue();
    }

    @Test
    void fifthConsecutiveFailureLocksAccountForFifteenMinutes() {
        Instant now = Instant.parse("2026-08-01T02:00:00Z");
        TeacherAccount account = TeacherAccount.active(
                UUID.randomUUID(), "teacher_01", "hash", "王老师", "第一中学", now);

        for (int attempt = 0; attempt < 5; attempt++) {
            account.recordFailedLogin(now.plusSeconds(attempt));
        }

        assertThat(account.canAuthenticateAt(now.plus(Duration.ofMinutes(14)))).isFalse();
        assertThat(account.canAuthenticateAt(now.plus(Duration.ofMinutes(16)))).isTrue();
    }
}
