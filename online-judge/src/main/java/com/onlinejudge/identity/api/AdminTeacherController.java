package com.onlinejudge.identity.api;

import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.application.TeacherAccountService;
import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.dto.AdminDecisionRequest;
import com.onlinejudge.identity.dto.TeacherAccountResponse;
import com.onlinejudge.identity.dto.TemporaryPasswordResponse;
import com.onlinejudge.shared.security.TeacherSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTeacherController {
    private final TeacherAccountService accounts;
    private final CurrentTeacherContext currentTeacher;

    @GetMapping("/teacher-applications")
    public ResponseEntity<List<TeacherAccountResponse>> applications(
            @RequestParam(defaultValue = "PENDING") TeacherAccount.Status status) {
        TeacherPrincipal admin = currentTeacher.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.schoolApplications(admin.schoolId(), status));
    }

    @PostMapping("/teacher-applications/{id}/approve")
    public ResponseEntity<TeacherAccountResponse> approve(@PathVariable UUID id, HttpServletRequest request) {
        TeacherPrincipal admin = currentTeacher.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.approve(id, admin, TeacherSessionService.clientIp(request)));
    }

    @PostMapping("/teacher-applications/{id}/reject")
    public ResponseEntity<TeacherAccountResponse> reject(@PathVariable UUID id,
                                                         @Valid @RequestBody(required = false) AdminDecisionRequest decision,
                                                         HttpServletRequest request) {
        TeacherPrincipal admin = currentTeacher.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.reject(id, decision == null ? null : decision.reason(), admin,
                TeacherSessionService.clientIp(request)));
    }

    @PostMapping("/teachers/{id}/suspend")
    public ResponseEntity<TeacherAccountResponse> suspend(@PathVariable UUID id, HttpServletRequest request) {
        TeacherPrincipal admin = currentTeacher.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.suspend(id, admin, TeacherSessionService.clientIp(request)));
    }

    @PostMapping("/teachers/{id}/restore")
    public ResponseEntity<TeacherAccountResponse> restore(@PathVariable UUID id, HttpServletRequest request) {
        TeacherPrincipal admin = currentTeacher.requireSchoolAdmin();
        return ResponseEntity.ok(accounts.restore(id, admin, TeacherSessionService.clientIp(request)));
    }

    @PostMapping("/teachers/{id}/reset-password")
    public ResponseEntity<TemporaryPasswordResponse> resetPassword(@PathVariable UUID id, HttpServletRequest request) {
        TeacherPrincipal admin = currentTeacher.requireSchoolAdmin();
        String temporaryPassword = accounts.resetPassword(id, admin, TeacherSessionService.clientIp(request));
        return ResponseEntity.ok(new TemporaryPasswordResponse(temporaryPassword, true));
    }

}
