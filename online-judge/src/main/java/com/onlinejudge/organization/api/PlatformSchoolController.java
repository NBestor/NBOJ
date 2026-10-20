package com.onlinejudge.organization.api;

import com.onlinejudge.aiquota.application.SchoolAiQuotaService;
import com.onlinejudge.aiquota.dto.AdjustTeacherQuotaRequest;
import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.application.AuditService;
import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.identity.dto.TemporaryPasswordResponse;
import com.onlinejudge.organization.application.SchoolAdministrationService;
import com.onlinejudge.organization.dto.*;
import com.onlinejudge.shared.security.TeacherSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform-admin/schools")
@RequiredArgsConstructor
public class PlatformSchoolController {
    private final SchoolAdministrationService schools;
    private final SchoolAiQuotaService quotas;
    private final CurrentTeacherContext current;
    private final AuditService audit;

    @PostMapping
    public ResponseEntity<CreatedSchoolResponse> create(@Valid @RequestBody CreateSchoolRequest request,
                                                        HttpServletRequest http) {
        return ResponseEntity.ok(schools.create(request, current.requirePlatformAdmin(), TeacherSessionService.clientIp(http)));
    }
    @GetMapping
    public ResponseEntity<List<SchoolResponse>> list() {
        current.requirePlatformAdmin();
        return ResponseEntity.ok(schools.list());
    }
    @PostMapping("/{id}/admin/reset-password")
    public ResponseEntity<TemporaryPasswordResponse> reset(@PathVariable UUID id, HttpServletRequest http) {
        return ResponseEntity.ok(new TemporaryPasswordResponse(
                schools.resetPassword(id, current.requirePlatformAdmin(), TeacherSessionService.clientIp(http)), true));
    }
    @PostMapping("/{id}/admin/replace")
    public ResponseEntity<CreatedSchoolResponse> replace(@PathVariable UUID id,
            @Valid @RequestBody ReplaceSchoolAdminRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(schools.replaceAdministrator(id, request, current.requirePlatformAdmin(),
                TeacherSessionService.clientIp(http)));
    }
    @PostMapping("/{id}/suspend")
    public ResponseEntity<SchoolResponse> suspend(@PathVariable UUID id, HttpServletRequest http) {
        return ResponseEntity.ok(schools.suspend(id, current.requirePlatformAdmin(), TeacherSessionService.clientIp(http)));
    }
    @PostMapping("/{id}/restore")
    public ResponseEntity<SchoolResponse> restore(@PathVariable UUID id, HttpServletRequest http) {
        return ResponseEntity.ok(schools.restore(id, current.requirePlatformAdmin(), TeacherSessionService.clientIp(http)));
    }
    @PutMapping("/{id}/quota")
    public ResponseEntity<SchoolAiQuotaService.SchoolQuotaSummary> quota(@PathVariable UUID id,
            @Valid @RequestBody AdjustTeacherQuotaRequest request, HttpServletRequest http) {
        TeacherPrincipal admin = current.requirePlatformAdmin();
        SchoolAiQuotaService.SchoolQuotaSummary result = quotas.setSchoolQuota(id, request.baseUnits(), request.additionalUnits());
        audit.record(admin.id(), "SCHOOL_QUOTA_ADJUSTED", "SCHOOL", id,
                "base=" + request.baseUnits() + ",additional=" + request.additionalUnits(), TeacherSessionService.clientIp(http));
        return ResponseEntity.ok(result);
    }
}
