package com.onlinejudge.shared.security;

import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.domain.StudentSession;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.classroom.persistence.StudentSessionRepository;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.organization.domain.School;
import com.onlinejudge.organization.persistence.SchoolRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class StudentAccessTokenService {
    public static final String HEADER_NAME = "X-Student-Token";
    public static final String COOKIE_NAME = "OJ_STUDENT_SESSION";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SchoolSecurityProperties properties;
    private final StudentSessionRepository sessions;
    private final StudentProfileRepository students;
    private final ClassGroupRepository classes;
    private final TeacherAccountRepository accounts;
    private final SchoolRepository schools;

    public StudentAccessTokenService(SchoolSecurityProperties properties) {
        this(properties, null, null, null, null, null);
    }

    @Autowired
    public StudentAccessTokenService(SchoolSecurityProperties properties,
                                     StudentSessionRepository sessions,
                                     StudentProfileRepository students,
                                     ClassGroupRepository classes,
                                     TeacherAccountRepository accounts,
                                     SchoolRepository schools) {
        this.properties = properties;
        this.sessions = sessions;
        this.students = students;
        this.classes = classes;
        this.accounts = accounts;
        this.schools = schools;
    }

    /** Development compatibility token; the production frontend uses only the HttpOnly cookie. */
    public String issue(StudentProfile student) {
        if (student == null || student.getId() == null) throw new IllegalArgumentException("学生身份未保存，无法签发访问令牌");
        long expiresAt = Instant.now().plusSeconds(properties.studentTokenTtlDays() * 24 * 3600).getEpochSecond();
        String identityKey = student.getIdentityKey() == null ? "" : student.getIdentityKey();
        String payload = student.getId() + ":" + expiresAt + ":" + base64(identityKey);
        return payload + "." + CryptoSupport.hmacSha256(properties.studentTokenSecret(), payload);
    }

    @Transactional
    public void issueCookie(StudentProfile student, HttpServletRequest request, HttpServletResponse response) {
        if (student == null || student.getId() == null || student.getStatus() != StudentProfile.RosterStatus.ACTIVE) {
            throw new AuthenticationRequiredException("ROSTER_MISMATCH");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        long maxAge = properties.studentTokenTtlDays() * 24 * 3600;
        sessions.save(StudentSession.builder().id(UUID.randomUUID()).studentProfileId(student.getId())
                .tokenHash(CryptoSupport.sha256(token)).createdAt(now).expiresAt(now.plusSeconds(maxAge)).lastSeenAt(now)
                .ipAddress(TeacherSessionService.clientIp(request)).userAgent(limit(request.getHeader("User-Agent"), 300)).build());
        String secure = properties.schoolProfile() ? "; Secure" : "";
        response.addHeader("Set-Cookie", COOKIE_NAME + "=" + token + "; Path=/; Max-Age=" + maxAge
                + "; HttpOnly; SameSite=Lax" + secure);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieValue(request);
        if (!token.isBlank()) sessions.findByTokenHash(CryptoSupport.sha256(token))
                .ifPresent(session -> session.setRevokedAt(Instant.now()));
        String secure = properties.schoolProfile() ? "; Secure" : "";
        response.addHeader("Set-Cookie", COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax" + secure);
    }

    @Transactional
    public Long currentStudentId(HttpServletRequest request) {
        String cookieToken = cookieValue(request);
        if (!cookieToken.isBlank()) {
            StudentSession session = sessions.findByTokenHash(CryptoSupport.sha256(cookieToken)).orElse(null);
            Instant now = Instant.now();
            if (session != null && session.validAt(now)) {
                StudentProfile student = students.findById(session.getStudentProfileId()).orElse(null);
                if (student != null && student.getStatus() == StudentProfile.RosterStatus.ACTIVE && activeSchool(student)) {
                    session.setLastSeenAt(now);
                    return student.getId();
                }
            }
        }
        if (!properties.schoolProfile()) {
            ParsedToken token = parseLegacy(request.getHeader(HEADER_NAME));
            return token == null ? null : token.studentProfileId();
        }
        return null;
    }

    public void requireStudent(HttpServletRequest request, Long expectedStudentProfileId) {
        if (expectedStudentProfileId == null) return;
        Long currentId = currentStudentId(request);
        if (currentId == null) throw new AuthenticationRequiredException("AUTH_REQUIRED");
        if (!expectedStudentProfileId.equals(currentId)) throw new AccessDeniedException("FORBIDDEN");
    }

    public void requireAnyOf(HttpServletRequest request, Long... studentProfileIds) {
        Long currentId = currentStudentId(request);
        if (currentId == null) throw new AuthenticationRequiredException("AUTH_REQUIRED");
        for (Long studentProfileId : studentProfileIds) if (currentId.equals(studentProfileId)) return;
        throw new AccessDeniedException("FORBIDDEN");
    }

    @Transactional
    public void revokeAll(Long studentId) {
        sessions.revokeAll(studentId, Instant.now());
    }

    private ParsedToken parseLegacy(String token) {
        if (token == null || token.isBlank()) return null;
        String[] tokenParts = token.trim().split("\\.", 2);
        if (tokenParts.length != 2) return null;
        String payload = tokenParts[0];
        if (!CryptoSupport.constantTimeEquals(CryptoSupport.hmacSha256(properties.studentTokenSecret(), payload), tokenParts[1])) return null;
        String[] payloadParts = payload.split(":", 3);
        try {
            Long studentProfileId = Long.parseLong(payloadParts[0]);
            long expiresAt = Long.parseLong(payloadParts[1]);
            if (Instant.now().getEpochSecond() > expiresAt) return null;
            if (students == null) return new ParsedToken(studentProfileId);
            StudentProfile student = students.findById(studentProfileId).orElse(null);
            return student != null && student.getStatus() == StudentProfile.RosterStatus.ACTIVE && activeSchool(student)
                    ? new ParsedToken(studentProfileId) : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String cookieValue(HttpServletRequest request) {
        if (request.getCookies() != null) for (var cookie : request.getCookies()) if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        String header = request.getHeader("Cookie");
        if (header != null) {
            for (String part : header.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && COOKIE_NAME.equals(pair[0])) return pair[1];
            }
        }
        return "";
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String limit(String value, int max) {
        String normalized = value == null ? "" : value;
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }

    private boolean activeSchool(StudentProfile student) {
        if (classes == null || accounts == null || schools == null) return true;
        return classes.findById(student.getClassGroupId()).flatMap(group -> accounts.findById(group.getOwnerTeacherId()))
                .map(account -> account.getSchoolId() != null && schools.findById(account.getSchoolId())
                        .map(school -> school.getStatus() == School.Status.ACTIVE).orElse(false))
                .orElse(false);
    }

    private record ParsedToken(Long studentProfileId) { }
}
