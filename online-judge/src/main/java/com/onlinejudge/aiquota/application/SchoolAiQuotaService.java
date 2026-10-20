package com.onlinejudge.aiquota.application;

import com.onlinejudge.aiquota.domain.SchoolAiQuota;
import com.onlinejudge.aiquota.domain.TeacherAiQuota;
import com.onlinejudge.aiquota.dto.TeacherAiUsageResponse;
import com.onlinejudge.aiquota.persistence.SchoolAiQuotaRepository;
import com.onlinejudge.aiquota.persistence.TeacherAiQuotaRepository;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.organization.domain.School;
import com.onlinejudge.organization.persistence.SchoolRepository;
import com.onlinejudge.shared.web.PlatformApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolAiQuotaService {
    private final SchoolAiQuotaRepository schoolQuotas;
    private final TeacherAiQuotaRepository teacherQuotas;
    private final TeacherAccountRepository accounts;
    private final SchoolRepository schools;

    @Transactional
    public SchoolQuotaSummary setSchoolQuota(UUID schoolId, int base, int additional) {
        SchoolAiQuota quota = lockedSchoolQuota(schoolId);
        int allocated = allocated(schoolId);
        try {
            quota.adjust(base, additional, allocated);
        } catch (IllegalArgumentException belowAllocated) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "SCHOOL_QUOTA_EXCEEDED", belowAllocated.getMessage());
        }
        schoolQuotas.save(quota);
        return summary(quota, allocated, used(schoolId));
    }

    @Transactional
    public TeacherAiUsageResponse allocateTeacher(UUID schoolId, UUID teacherId, int units) {
        School school = schools.findById(schoolId).orElseThrow(() -> notFound("学校不存在"));
        if (school.getStatus() != School.Status.ACTIVE)
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "SCHOOL_SUSPENDED", "学校已停用");
        TeacherAccount teacher = accounts.findById(teacherId).filter(account -> account.getRole() == TeacherAccount.Role.TEACHER)
                .filter(account -> schoolId.equals(account.getSchoolId())).orElseThrow(() -> notFound("教师不存在"));
        SchoolAiQuota pool = lockedSchoolQuota(schoolId);
        TeacherAiQuota quota = lockedTeacherQuota(teacher.getId());
        int otherAllocated = allocated(schoolId) - quota.getBaseUnits() - quota.getAdditionalUnits();
        if (otherAllocated + units > pool.total()) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "SCHOOL_QUOTA_EXCEEDED", "分配总量超过学校额度");
        }
        try {
            quota.allocate(units);
        } catch (IllegalArgumentException belowUsed) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "QUOTA_BELOW_USED", belowUsed.getMessage());
        }
        teacherQuotas.save(quota);
        return response(quota);
    }

    @Transactional(readOnly = true)
    public SchoolQuotaSummary schoolSummary(UUID schoolId) {
        SchoolAiQuota quota = schoolQuotas.findBySchoolIdAndQuotaMonth(schoolId, currentMonth().toString())
                .orElseGet(() -> SchoolAiQuota.forMonth(schoolId, currentMonth(), 0));
        return summary(quota, allocated(schoolId), used(schoolId));
    }

    @Transactional(readOnly = true)
    public TeacherAiUsageResponse teacherUsage(UUID schoolId, UUID teacherId) {
        accounts.findById(teacherId)
                .filter(account -> account.getRole() == TeacherAccount.Role.TEACHER)
                .filter(account -> schoolId.equals(account.getSchoolId()))
                .orElseThrow(() -> notFound("教师不存在"));
        return teacherQuotas.findByTeacherIdAndQuotaMonth(teacherId, currentMonth().toString())
                .map(this::response)
                .orElseGet(() -> response(TeacherAiQuota.forMonth(teacherId, currentMonth(), 0)));
    }

    private SchoolQuotaSummary summary(SchoolAiQuota quota, int allocated, int used) {
        return new SchoolQuotaSummary(quota.getSchoolId(), quota.getQuotaMonth(), quota.total(), allocated, used,
                quota.availableToAllocate(allocated), resetAt());
    }

    private int allocated(UUID schoolId) {
        return accounts.findBySchoolIdAndRoleOrderByCreatedAtAsc(schoolId, TeacherAccount.Role.TEACHER).stream()
                .mapToInt(account -> teacherQuotas.findByTeacherIdAndQuotaMonth(account.getId(), currentMonth().toString())
                        .map(quota -> quota.getBaseUnits() + quota.getAdditionalUnits()).orElse(0)).sum();
    }

    private int used(UUID schoolId) {
        return accounts.findBySchoolIdAndRoleOrderByCreatedAtAsc(schoolId, TeacherAccount.Role.TEACHER).stream()
                .mapToInt(account -> teacherQuotas.findByTeacherIdAndQuotaMonth(account.getId(), currentMonth().toString())
                        .map(TeacherAiQuota::getUsedUnits).orElse(0)).sum();
    }

    private SchoolAiQuota lockedSchoolQuota(UUID schoolId) {
        YearMonth month = currentMonth();
        return schoolQuotas.findLockedBySchoolIdAndQuotaMonth(schoolId, month.toString())
                .orElseGet(() -> createSchoolQuota(schoolId, month));
    }

    private SchoolAiQuota createSchoolQuota(UUID schoolId, YearMonth month) {
        try { return schoolQuotas.saveAndFlush(SchoolAiQuota.forMonth(schoolId, month, 0)); }
        catch (DataIntegrityViolationException conflict) {
            return schoolQuotas.findLockedBySchoolIdAndQuotaMonth(schoolId, month.toString()).orElseThrow(() -> conflict);
        }
    }

    private TeacherAiQuota lockedTeacherQuota(UUID teacherId) {
        YearMonth month = currentMonth();
        return teacherQuotas.findLockedByTeacherIdAndQuotaMonth(teacherId, month.toString())
                .orElseGet(() -> teacherQuotas.saveAndFlush(TeacherAiQuota.forMonth(teacherId, month, 0)));
    }

    private TeacherAiUsageResponse response(TeacherAiQuota quota) {
        return TeacherAiUsageResponse.builder().teacherId(quota.getTeacherId()).month(quota.getQuotaMonth())
                .baseUnits(quota.getBaseUnits()).additionalUnits(quota.getAdditionalUnits())
                .usedUnits(quota.getUsedUnits()).reservedUnits(quota.getReservedUnits()).remainingUnits(quota.remaining())
                .resetsAt(resetAt()).build();
    }

    private Instant resetAt() { return currentMonth().plusMonths(1).atDay(1).atStartOfDay(AiQuotaService.BILLING_ZONE).toInstant(); }
    private YearMonth currentMonth() { return YearMonth.now(AiQuotaService.BILLING_ZONE); }
    private PlatformApiException notFound(String message) { return new PlatformApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }

    public record SchoolQuotaSummary(UUID schoolId, String month, int totalUnits, int allocatedUnits,
                                     int usedUnits, int availableUnits, Instant resetsAt) { }
}
