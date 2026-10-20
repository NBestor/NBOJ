package com.onlinejudge.aiquota.domain;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeacherAiQuotaTest {

    @Test
    void reservationPreventsOverdraftAndSuccessfulSettlementChargesOnce() {
        TeacherAiQuota quota = TeacherAiQuota.forMonth(UUID.randomUUID(), YearMonth.of(2026, 8), 1);

        quota.reserve();
        assertThat(quota.remaining()).isZero();
        assertThatThrownBy(quota::reserve).isInstanceOf(QuotaExhaustedException.class);

        quota.settleSuccess();
        assertThat(quota.getReservedUnits()).isZero();
        assertThat(quota.getUsedUnits()).isEqualTo(1);
    }

    @Test
    void failedInvocationReleasesReservationWithoutCharging() {
        TeacherAiQuota quota = TeacherAiQuota.forMonth(UUID.randomUUID(), YearMonth.of(2026, 8), 500);

        quota.reserve();
        quota.settleFailure();

        assertThat(quota.getUsedUnits()).isZero();
        assertThat(quota.getReservedUnits()).isZero();
        assertThat(quota.remaining()).isEqualTo(500);
    }
}
