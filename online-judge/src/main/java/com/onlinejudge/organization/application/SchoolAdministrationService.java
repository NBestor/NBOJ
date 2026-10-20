package com.onlinejudge.organization.application;

import com.onlinejudge.aiquota.application.SchoolAiQuotaService;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.classroom.persistence.StudentSessionRepository;
import com.onlinejudge.identity.application.AuditService;
import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.identity.persistence.TeacherSessionRepository;
import com.onlinejudge.organization.domain.School;
import com.onlinejudge.organization.dto.*;
import com.onlinejudge.organization.persistence.SchoolRepository;
import com.onlinejudge.shared.security.TeacherSessionService;
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
public class SchoolAdministrationService {
    private static final String TEMP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SchoolRepository schools;
    private final TeacherAccountRepository accounts;
    private final TeacherSessionRepository teacherSessions;
    private final ClassGroupRepository classes;
    private final StudentProfileRepository students;
    private final StudentSessionRepository studentSessions;
    private final PasswordEncoder passwordEncoder;
    private final SchoolRegistrationCodeService registrationCodes;
    private final SchoolAiQuotaService quotas;
    private final AuditService audit;

    @Transactional
    public CreatedSchoolResponse create(CreateSchoolRequest request, TeacherPrincipal platformAdmin, String ip) {
        String name = required(request.schoolName(), "学校名称不能为空");
        String username = validUsername(request.adminUsername());
        if (schools.existsByNameIgnoreCase(name)) conflict("SCHOOL_NAME_TAKEN", "学校名称已存在");
        if (accounts.existsByUsernameNormalized(username)) conflict("USERNAME_TAKEN", "用户名已被使用");
        Instant now = Instant.now();
        UUID schoolId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String temporaryPassword = temporaryPassword();
        String registrationCode = registrationCodes.generate();
        TeacherAccount administrator = TeacherAccount.schoolAdmin(adminId, username,
                passwordEncoder.encode(temporaryPassword), required(request.adminDisplayName(), "管理员姓名不能为空"),
                schoolId, name, now);
        School school = schools.save(School.create(schoolId, name, registrationCodes.hash(registrationCode),
                adminId, platformAdmin.id(), now));
        accounts.save(administrator);
        quotas.setSchoolQuota(schoolId, request.monthlyAiUnits() == null ? 0 : request.monthlyAiUnits(), 0);
        audit.record(platformAdmin.id(), "SCHOOL_CREATED", "SCHOOL", schoolId, "admin=" + adminId, ip);
        return new CreatedSchoolResponse(toResponse(school), temporaryPassword, registrationCode);
    }

    @Transactional(readOnly = true)
    public List<SchoolResponse> list() {
        return schools.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public String resetPassword(UUID schoolId, TeacherPrincipal actor, String ip) {
        School school = require(schoolId);
        TeacherAccount admin = accounts.findById(school.getAdminAccountId()).orElseThrow();
        String password = temporaryPassword();
        admin.replacePassword(passwordEncoder.encode(password), true, Instant.now());
        teacherSessions.revokeAll(admin.getId(), Instant.now());
        audit.record(actor.id(), "SCHOOL_ADMIN_PASSWORD_RESET", "SCHOOL", schoolId, "admin=" + admin.getId(), ip);
        return password;
    }

    @Transactional
    public CreatedSchoolResponse replaceAdministrator(UUID schoolId, ReplaceSchoolAdminRequest request,
                                                       TeacherPrincipal actor, String ip) {
        School school = require(schoolId);
        String username = validUsername(request.username());
        if (accounts.existsByUsernameNormalized(username)) conflict("USERNAME_TAKEN", "用户名已被使用");
        Instant now = Instant.now();
        accounts.findById(school.getAdminAccountId()).ifPresent(previous -> {
            previous.suspend(now);
            teacherSessions.revokeAll(previous.getId(), now);
        });
        UUID id = UUID.randomUUID();
        String password = temporaryPassword();
        TeacherAccount replacement = TeacherAccount.schoolAdmin(id, username, passwordEncoder.encode(password),
                required(request.displayName(), "管理员姓名不能为空"), schoolId, school.getName(), now);
        accounts.save(replacement);
        school.replaceAdministrator(id, now);
        audit.record(actor.id(), "SCHOOL_ADMIN_REPLACED", "SCHOOL", schoolId, "admin=" + id, ip);
        return new CreatedSchoolResponse(toResponse(school), password, null);
    }

    @Transactional
    public SchoolResponse suspend(UUID schoolId, TeacherPrincipal actor, String ip) {
        School school = require(schoolId);
        Instant now = Instant.now();
        school.suspend(now);
        accounts.findBySchoolId(schoolId).forEach(account -> teacherSessions.revokeAll(account.getId(), now));
        classes.findAllByOrderByCreatedAtDesc().stream()
                .filter(group -> accounts.findById(group.getOwnerTeacherId())
                        .map(owner -> schoolId.equals(owner.getSchoolId())).orElse(false))
                .flatMap(group -> students.findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(group.getId()).stream())
                .forEach(student -> studentSessions.revokeAll(student.getId(), now));
        audit.record(actor.id(), "SCHOOL_SUSPENDED", "SCHOOL", schoolId, null, ip);
        return toResponse(school);
    }

    @Transactional
    public SchoolResponse restore(UUID schoolId, TeacherPrincipal actor, String ip) {
        School school = require(schoolId);
        school.restore(Instant.now());
        audit.record(actor.id(), "SCHOOL_RESTORED", "SCHOOL", schoolId, null, ip);
        return toResponse(school);
    }

    @Transactional
    public String rotateRegistrationCode(UUID schoolId, TeacherPrincipal actor, String ip) {
        School school = require(schoolId);
        if (actor.role() != TeacherAccount.Role.PLATFORM_ADMIN && !schoolId.equals(actor.schoolId())) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "CROSS_SCHOOL_FORBIDDEN", "不能管理其他学校");
        }
        String code = registrationCodes.generate();
        school.rotateRegistrationCode(registrationCodes.hash(code), Instant.now());
        audit.record(actor.id(), "SCHOOL_REGISTRATION_CODE_ROTATED", "SCHOOL", schoolId, null, ip);
        return code;
    }

    public School require(UUID id) {
        return schools.findById(id).orElseThrow(() ->
                new PlatformApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "学校不存在"));
    }

    private SchoolResponse toResponse(School school) {
        var summary = quotas.schoolSummary(school.getId());
        return new SchoolResponse(school.getId(), school.getName(), school.getStatus(), school.getAdminAccountId(),
                summary.totalUnits(), summary.allocatedUnits(), summary.usedUnits(), school.getCreatedAt());
    }

    private String validUsername(String value) {
        String username = TeacherAccount.normalizeUsername(value);
        if (!username.matches("[a-z0-9][a-z0-9._-]{3,49}"))
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME", "用户名格式无效");
        return username;
    }
    private String required(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new PlatformApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
        return normalized;
    }
    private String temporaryPassword() {
        StringBuilder result = new StringBuilder("A7!");
        while (result.length() < 16) result.append(TEMP_ALPHABET.charAt(RANDOM.nextInt(TEMP_ALPHABET.length())));
        return result.toString();
    }
    private void conflict(String code, String message) { throw new PlatformApiException(HttpStatus.CONFLICT, code, message); }
}
