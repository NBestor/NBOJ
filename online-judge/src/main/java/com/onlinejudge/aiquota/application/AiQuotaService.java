package com.onlinejudge.aiquota.application;

import com.onlinejudge.aiquota.domain.AiUsageEvent;
import com.onlinejudge.aiquota.domain.QuotaExhaustedException;
import com.onlinejudge.aiquota.domain.TeacherAiQuota;
import com.onlinejudge.aiquota.dto.TeacherAiUsageResponse;
import com.onlinejudge.aiquota.persistence.AiUsageEventRepository;
import com.onlinejudge.aiquota.persistence.TeacherAiQuotaRepository;
import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.organization.domain.School;
import com.onlinejudge.organization.persistence.SchoolRepository;
import com.onlinejudge.shared.web.PlatformApiException;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiQuotaService {
    public static final int DEFAULT_MONTHLY_UNITS = 0;
    public static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Shanghai");

    private final TeacherAiQuotaRepository quotas;
    private final AiUsageEventRepository events;
    private final CurrentTeacherContext currentTeacher;
    private final TeacherAccountRepository accounts;
    private final SchoolRepository schools;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(AiInvocationContext context) {
        if (context == null || context.teacherId() == null) {
            throw new PaidAiNotAllowedException();
        }
        UUID schoolId = context.schoolId() != null ? context.schoolId()
                : accounts.findById(context.teacherId()).map(account -> account.getSchoolId()).orElse(null);
        if (schoolId == null || schools.findById(schoolId).map(school -> school.getStatus() != School.Status.ACTIVE).orElse(true)) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "SCHOOL_SUSPENDED", "学校已停用或不存在");
        }
        context = new AiInvocationContext(context.teacherId(), schoolId, context.studentProfileId(), context.assignmentId(),
                context.submissionId(), context.purpose(), context.idempotencyKey());
        String key = normalizedKey(context.idempotencyKey());
        if (events.existsByTeacherIdAndIdempotencyKeyAndChargedTrue(context.teacherId(), key)) {
            return new Reservation(context, currentMonth(), false);
        }
        TeacherAiQuota quota = lockedQuota(context.teacherId(), currentMonth());
        quota.reserve();
        quotas.save(quota);
        return new Reservation(context, currentMonth(), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleSuccess(Reservation reservation, String provider, String model,
                              Integer inputTokens, Integer outputTokens) {
        AiInvocationContext context = reservation.context();
        String key = normalizedKey(context.idempotencyKey());
        boolean alreadyCharged = events.existsByTeacherIdAndIdempotencyKeyAndChargedTrue(context.teacherId(), key);
        boolean charged = false;
        if (reservation.reserved()) {
            TeacherAiQuota quota = lockedQuota(context.teacherId(), reservation.month());
            if (alreadyCharged) quota.settleFailure();
            else {
                quota.settleSuccess();
                charged = true;
            }
            quotas.save(quota);
        }
        events.save(event(context, provider, model, true, charged, inputTokens, outputTokens, null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleFailure(Reservation reservation, String provider, String model, String reason) {
        AiInvocationContext context = reservation.context();
        if (reservation.reserved()) {
            TeacherAiQuota quota = lockedQuota(context.teacherId(), reservation.month());
            quota.settleFailure();
            quotas.save(quota);
        }
        events.save(event(context, provider, model, false, false, null, null, limit(reason, 500)));
    }

    @Transactional(readOnly = true)
    public TeacherAiUsageResponse currentUsage() {
        return usage(currentTeacher.requireTeacherId(), currentMonth());
    }

    @Transactional(readOnly = true)
    public TeacherAiUsageResponse usage(UUID teacherId, YearMonth month) {
        return quotas.findByTeacherIdAndQuotaMonth(teacherId, month.toString())
                .map(this::response)
                .orElseGet(() -> response(TeacherAiQuota.forMonth(teacherId, month, DEFAULT_MONTHLY_UNITS)));
    }

    private TeacherAiQuota lockedQuota(UUID teacherId, YearMonth month) {
        return quotas.findLockedByTeacherIdAndQuotaMonth(teacherId, month.toString()).orElseGet(() -> createQuota(teacherId, month));
    }

    private TeacherAiQuota createQuota(UUID teacherId, YearMonth month) {
        try {
            return quotas.saveAndFlush(TeacherAiQuota.forMonth(teacherId, month, DEFAULT_MONTHLY_UNITS));
        } catch (DataIntegrityViolationException conflict) {
            return quotas.findLockedByTeacherIdAndQuotaMonth(teacherId, month.toString()).orElseThrow(() -> conflict);
        }
    }

    private AiUsageEvent event(AiInvocationContext context, String provider, String model, boolean success,
                               boolean charged, Integer inputTokens, Integer outputTokens, String failure) {
        return AiUsageEvent.builder().teacherId(context.teacherId()).schoolId(context.schoolId()).studentProfileId(context.studentProfileId())
                .assignmentId(context.assignmentId()).submissionId(context.submissionId())
                .usagePurpose(limit(context.purpose(), 60)).idempotencyKey(normalizedKey(context.idempotencyKey()))
                .provider(limit(provider, 80)).model(limit(model, 160))
                .attemptNo((int) events.countByTeacherIdAndIdempotencyKey(context.teacherId(), normalizedKey(context.idempotencyKey())) + 1)
                .success(success).charged(charged).inputTokens(inputTokens).outputTokens(outputTokens)
                .quotaUnits(charged ? 1 : 0).failureReason(failure).createdAt(Instant.now()).build();
    }

    private TeacherAiUsageResponse response(TeacherAiQuota quota) {
        YearMonth month = YearMonth.parse(quota.getQuotaMonth());
        Instant resetsAt = month.plusMonths(1).atDay(1).atStartOfDay(BILLING_ZONE).toInstant();
        return TeacherAiUsageResponse.builder().teacherId(quota.getTeacherId()).month(quota.getQuotaMonth())
                .baseUnits(quota.getBaseUnits()).additionalUnits(quota.getAdditionalUnits())
                .usedUnits(quota.getUsedUnits()).reservedUnits(quota.getReservedUnits())
                .remainingUnits(quota.remaining()).resetsAt(resetsAt).build();
    }

    private YearMonth currentMonth() { return YearMonth.now(BILLING_ZONE); }
    private String normalizedKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isBlank()) throw new IllegalArgumentException("AI 调用必须提供幂等键");
        return limit(key, 160);
    }
    private String limit(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    public record Reservation(AiInvocationContext context, YearMonth month, boolean reserved) { }

    public static class PaidAiNotAllowedException extends RuntimeException {
        public PaidAiNotAllowedException() { super("PAID_AI_NOT_ALLOWED"); }
    }
}
