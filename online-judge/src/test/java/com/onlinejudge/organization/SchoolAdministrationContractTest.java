package com.onlinejudge.organization;

import com.onlinejudge.aiquota.domain.SchoolAiQuota;
import com.onlinejudge.aiquota.domain.TeacherAiQuota;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.organization.domain.School;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchoolAdministrationContractTest {

    @Test
    void exposesThreeDistinctAccountRoles() {
        assertThat(TeacherAccount.Role.values()).containsExactlyInAnyOrder(
                TeacherAccount.Role.PLATFORM_ADMIN,
                TeacherAccount.Role.SCHOOL_ADMIN,
                TeacherAccount.Role.TEACHER);
    }

    @Test
    void bindsSchoolAdministratorAndTeacherToOneSchool() {
        UUID schoolId = UUID.randomUUID();
        UUID platformAdminId = TeacherAccount.BOOTSTRAP_ADMIN_ID;
        UUID schoolAdminId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-15T12:00:00Z");

        School school = School.create(schoolId, "温州中学", "registration-code-hash",
                schoolAdminId, platformAdminId, now);
        TeacherAccount administrator = TeacherAccount.schoolAdmin(schoolAdminId, "wz-admin", "hash",
                "校管", schoolId, "温州中学", now);
        TeacherAccount teacher = TeacherAccount.pending(UUID.randomUUID(), "teacher01", "hash",
                "张老师", schoolId, "温州中学", now);

        assertThat(school.getStatus()).isEqualTo(School.Status.ACTIVE);
        assertThat(school.getAdminAccountId()).isEqualTo(administrator.getId());
        assertThat(administrator.getRole()).isEqualTo(TeacherAccount.Role.SCHOOL_ADMIN);
        assertThat(teacher.getRole()).isEqualTo(TeacherAccount.Role.TEACHER);
        assertThat(administrator.getSchoolId()).isEqualTo(schoolId);
        assertThat(teacher.getSchoolId()).isEqualTo(schoolId);
    }

    @Test
    void enforcesSchoolPoolAndTeacherAllocationFloors() {
        UUID schoolId = UUID.randomUUID();
        SchoolAiQuota schoolQuota = SchoolAiQuota.forMonth(schoolId, YearMonth.of(2026, 8), 1000);
        TeacherAiQuota teacherQuota = TeacherAiQuota.forMonth(UUID.randomUUID(), YearMonth.of(2026, 8), 0);

        assertThat(schoolQuota.availableToAllocate(700)).isEqualTo(300);
        assertThatThrownBy(() -> schoolQuota.adjust(600, 0, 700))
                .hasMessageContaining("已分配");

        teacherQuota.allocate(20);
        teacherQuota.reserve();
        assertThatThrownBy(() -> teacherQuota.allocate(0))
                .hasMessageContaining("已用或预留");
    }
}
