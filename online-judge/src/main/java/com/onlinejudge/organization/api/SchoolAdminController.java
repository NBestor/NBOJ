package com.onlinejudge.organization.api;

import com.onlinejudge.aiquota.application.SchoolAiQuotaService;
import com.onlinejudge.aiquota.dto.AdjustTeacherQuotaRequest;
import com.onlinejudge.aiquota.dto.TeacherAiUsageResponse;
import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.application.AuditService;
import com.onlinejudge.identity.application.TeacherAccountService;
import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.dto.*;
import com.onlinejudge.organization.application.SchoolAdministrationService;
import com.onlinejudge.organization.dto.RegistrationCodeResponse;
import com.onlinejudge.organization.dto.SchoolAdminOverviewResponse;
import com.onlinejudge.shared.security.TeacherSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/school-admin")
@RequiredArgsConstructor
public class SchoolAdminController {
    private final CurrentTeacherContext current;
    private final TeacherAccountService accounts;
    private final SchoolAdministrationService schools;
    private final SchoolAiQuotaService quotas;
    private final AuditService audit;

    @GetMapping("/overview")
    public ResponseEntity<SchoolAdminOverviewResponse> overview() {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        List<TeacherAccountResponse> teachers = accounts.schoolTeachers(admin.schoolId());
        long pending = accounts.schoolApplications(admin.schoolId(), TeacherAccount.Status.PENDING).size();
        Map<UUID, TeacherAiUsageResponse> teacherQuotas = teachers.stream().collect(Collectors.toMap(
                TeacherAccountResponse::id, teacher -> quotas.teacherUsage(admin.schoolId(), teacher.id())));
        return ResponseEntity.ok(new SchoolAdminOverviewResponse(admin.schoolId(), admin.schoolName(),
                quotas.schoolSummary(admin.schoolId()), teachers, teacherQuotas, pending));
    }
    @GetMapping("/teacher-applications")
    public ResponseEntity<List<TeacherAccountResponse>> applications(
            @RequestParam(defaultValue = "PENDING") TeacherAccount.Status status) {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.schoolApplications(admin.schoolId(), status));
    }
    @PostMapping("/teacher-applications/{id}/approve")
    public ResponseEntity<TeacherAccountResponse> approve(@PathVariable UUID id, HttpServletRequest http) {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.approve(id, admin, TeacherSessionService.clientIp(http)));
    }
    @PostMapping("/teacher-applications/{id}/reject")
    public ResponseEntity<TeacherAccountResponse> reject(@PathVariable UUID id,
            @RequestBody(required = false) AdminDecisionRequest request, HttpServletRequest http) {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.reject(id, request == null ? null : request.reason(), admin,
                TeacherSessionService.clientIp(http)));
    }
    @GetMapping("/teachers")
    public ResponseEntity<List<TeacherAccountResponse>> teachers() {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.schoolTeachers(admin.schoolId()));
    }
    @PostMapping("/teachers/{id}/suspend")
    public ResponseEntity<TeacherAccountResponse> suspend(@PathVariable UUID id, HttpServletRequest http) {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.suspend(id, admin, TeacherSessionService.clientIp(http)));
    }
    @PostMapping("/teachers/{id}/restore")
    public ResponseEntity<TeacherAccountResponse> restore(@PathVariable UUID id, HttpServletRequest http) {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.restore(id, admin, TeacherSessionService.clientIp(http)));
    }
    @PostMapping("/teachers/{id}/reset-password")
    public ResponseEntity<TemporaryPasswordResponse> reset(@PathVariable UUID id, HttpServletRequest http) {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        return ResponseEntity.ok(new TemporaryPasswordResponse(accounts.resetPassword(id, admin,
                TeacherSessionService.clientIp(http)), true));
    }
    @PutMapping("/teachers/{id}/quota")
    public ResponseEntity<TeacherAiUsageResponse> quota(@PathVariable UUID id,
            @Valid @RequestBody AdjustTeacherQuotaRequest request, HttpServletRequest http) {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        TeacherAiUsageResponse result = quotas.allocateTeacher(admin.schoolId(), id,
                request.baseUnits() + request.additionalUnits());
        audit.record(admin.id(), "TEACHER_QUOTA_ADJUSTED", "TEACHER", id,
                "units=" + (request.baseUnits() + request.additionalUnits()), TeacherSessionService.clientIp(http));
        return ResponseEntity.ok(result);
    }
    @PostMapping("/registration-code/rotate")
    public ResponseEntity<RegistrationCodeResponse> rotate(HttpServletRequest http) {
        TeacherPrincipal admin = current.requireSchoolAdmin();
        return ResponseEntity.ok(new RegistrationCodeResponse(schools.rotateRegistrationCode(admin.schoolId(), admin,
                TeacherSessionService.clientIp(http))));
    }
}
