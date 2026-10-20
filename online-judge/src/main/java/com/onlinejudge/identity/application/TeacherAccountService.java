package com.onlinejudge.identity.application;

import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.dto.TeacherAccountResponse;
import com.onlinejudge.identity.dto.TeacherRegisterRequest;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.identity.persistence.TeacherSessionRepository;
import com.onlinejudge.organization.application.SchoolRegistrationCodeService;
import com.onlinejudge.organization.domain.School;
import com.onlinejudge.organization.persistence.SchoolRepository;
import com.onlinejudge.shared.web.PlatformApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherAccountService {
    private static final String TEMP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TeacherAccountRepository accounts;
    private final TeacherSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SchoolRepository schools;
    private final SchoolRegistrationCodeService registrationCodes;

    @Transactional
    public TeacherAccountResponse register(TeacherRegisterRequest request, String ipAddress) {
        String username = normalizeAndValidateUsername(request.username());
        validatePassword(request.password());
        if (accounts.existsByUsernameNormalized(username)) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已被使用");
        }
        School school = schools.findByRegistrationCodeHash(registrationCodes.hash(request.schoolRegistrationCode()))
                .filter(candidate -> candidate.getStatus() == School.Status.ACTIVE)
                .orElseThrow(() -> new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHOOL_CODE", "学校注册码无效或已轮换"));
        TeacherAccount account = TeacherAccount.pending(UUID.randomUUID(), username,
                passwordEncoder.encode(request.password()), normalizeRequired(request.displayName(), "姓名不能为空"),
                school.getId(), school.getName(), Instant.now());
        accounts.save(account);
        auditService.record(account.getId(), "TEACHER_REGISTERED", "TEACHER", account.getId(), "PENDING", ipAddress);
        return TeacherAccountResponse.from(account);
    }

    public List<TeacherAccountResponse> applications(TeacherAccount.Status status) {
        return accounts.findByStatusOrderByCreatedAtAsc(status == null ? TeacherAccount.Status.PENDING : status)
                .stream().map(TeacherAccountResponse::from).toList();
    }

    public List<TeacherAccountResponse> schoolApplications(UUID schoolId, TeacherAccount.Status status) {
        return accounts.findBySchoolIdAndRoleAndStatusOrderByCreatedAtAsc(schoolId, TeacherAccount.Role.TEACHER,
                        status == null ? TeacherAccount.Status.PENDING : status)
                .stream().map(TeacherAccountResponse::from).toList();
    }

    public List<TeacherAccountResponse> schoolTeachers(UUID schoolId) {
        return accounts.findBySchoolIdAndRoleOrderByCreatedAtAsc(schoolId, TeacherAccount.Role.TEACHER)
                .stream().map(TeacherAccountResponse::from).toList();
    }

    @Transactional
    public TeacherAccountResponse approve(UUID id, TeacherPrincipal admin, String ipAddress) {
        TeacherAccount account = requireSchoolTeacher(id, admin);
        account.approve(admin.id(), Instant.now());
        auditService.record(admin.id(), "TEACHER_APPROVED", "TEACHER", id, null, ipAddress);
        return TeacherAccountResponse.from(account);
    }

    @Transactional
    public TeacherAccountResponse reject(UUID id, String reason, TeacherPrincipal admin, String ipAddress) {
        TeacherAccount account = requireSchoolTeacher(id, admin);
        account.reject(admin.id(), normalizeReason(reason), Instant.now());
        sessions.revokeAll(id, Instant.now());
        auditService.record(admin.id(), "TEACHER_REJECTED", "TEACHER", id, normalizeReason(reason), ipAddress);
        return TeacherAccountResponse.from(account);
    }

    @Transactional
    public TeacherAccountResponse suspend(UUID id, TeacherPrincipal admin, String ipAddress) {
        TeacherAccount account = requireSchoolTeacher(id, admin);
        account.suspend(Instant.now());
        sessions.revokeAll(id, Instant.now());
        auditService.record(admin.id(), "TEACHER_SUSPENDED", "TEACHER", id, null, ipAddress);
        return TeacherAccountResponse.from(account);
    }

    @Transactional
    public TeacherAccountResponse restore(UUID id, TeacherPrincipal admin, String ipAddress) {
        TeacherAccount account = requireSchoolTeacher(id, admin);
        account.restore(Instant.now());
        auditService.record(admin.id(), "TEACHER_RESTORED", "TEACHER", id, null, ipAddress);
        return TeacherAccountResponse.from(account);
    }

    @Transactional
    public String resetPassword(UUID id, TeacherPrincipal admin, String ipAddress) {
        TeacherAccount account = requireSchoolTeacher(id, admin);
        String temporaryPassword = generateTemporaryPassword();
        account.replacePassword(passwordEncoder.encode(temporaryPassword), true, Instant.now());
        sessions.revokeAll(id, Instant.now());
        auditService.record(admin.id(), "TEACHER_PASSWORD_RESET", "TEACHER", id, "temporary-password-issued", ipAddress);
        return temporaryPassword;
    }

    @Transactional
    public void changePassword(TeacherPrincipal principal, String currentPassword, String newPassword, String ipAddress) {
        validatePassword(newPassword);
        TeacherAccount account = require(principal.id());
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, account.getPasswordHash())) {
            throw new PlatformApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "当前密码不正确");
        }
        account.passwordChanged(passwordEncoder.encode(newPassword), Instant.now());
        sessions.revokeAll(account.getId(), Instant.now());
        auditService.record(account.getId(), "TEACHER_PASSWORD_CHANGED", "TEACHER", account.getId(), null, ipAddress);
    }

    public TeacherAccount require(UUID id) {
        return accounts.findById(id).orElseThrow(() ->
                new PlatformApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "教师账号不存在"));
    }

    private TeacherAccount requireNonBootstrap(UUID id) {
        if (TeacherAccount.BOOTSTRAP_ADMIN_ID.equals(id)) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "BOOTSTRAP_ADMIN_PROTECTED", "bootstrap 管理员不能执行该操作");
        }
        return require(id);
    }

    private TeacherAccount requireSchoolTeacher(UUID id, TeacherPrincipal admin) {
        if (admin.role() != TeacherAccount.Role.SCHOOL_ADMIN || admin.schoolId() == null) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "SCHOOL_ADMIN_REQUIRED", "需要学校管理员权限");
        }
        TeacherAccount account = requireNonBootstrap(id);
        if (account.getRole() != TeacherAccount.Role.TEACHER || !admin.schoolId().equals(account.getSchoolId())) {
            throw new PlatformApiException(HttpStatus.NOT_FOUND, "CROSS_SCHOOL_FORBIDDEN", "教师账号不存在");
        }
        return account;
    }

    private String normalizeAndValidateUsername(String value) {
        String username = TeacherAccount.normalizeUsername(value);
        if (!username.matches("[a-z0-9][a-z0-9._-]{3,49}")) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME", "用户名需为 4–50 位字母、数字、点、下划线或连字符");
        }
        return username;
    }

    private void validatePassword(String password) {
        String value = password == null ? "" : password;
        if (value.length() < 10 || value.length() > 100
                || !value.matches(".*[A-Za-z].*") || !value.matches(".*\\d.*")) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "密码至少 10 位，且同时包含字母和数字");
        }
    }

    private String generateTemporaryPassword() {
        StringBuilder result = new StringBuilder("A7!");
        for (int i = result.length(); i < 16; i++) {
            result.append(TEMP_ALPHABET.charAt(RANDOM.nextInt(TEMP_ALPHABET.length())));
        }
        return result.toString();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new PlatformApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
        return normalized;
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }
}
