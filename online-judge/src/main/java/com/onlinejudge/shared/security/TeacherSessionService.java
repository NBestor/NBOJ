package com.onlinejudge.shared.security;

import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.domain.TeacherSession;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.identity.persistence.TeacherSessionRepository;
import com.onlinejudge.organization.domain.School;
import com.onlinejudge.organization.persistence.SchoolRepository;
import com.onlinejudge.shared.web.PlatformApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.onlinejudge.system.application.TrialMetrics;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherSessionService {

    public static final String COOKIE_NAME = "OJ_TEACHER_SESSION";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RESOLUTION_ATTRIBUTE = TeacherSessionService.class.getName() + ".resolution";

    private final SchoolSecurityProperties properties;
    private final TeacherAccountRepository accounts;
    private final TeacherSessionRepository sessions;
    private final SchoolRepository schools;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private TrialMetrics trialMetrics;

    /** One-version development compatibility alias. Production never accepts the shared password. */
    public boolean login(String password, HttpServletResponse response) {
        if (properties.schoolProfile() || !properties.teacherPasswordConfigured()
                || !CryptoSupport.constantTimeEquals(properties.teacherPassword(), password == null ? "" : password)) {
            return false;
        }
        long maxAge = properties.teacherSessionTtlHours() * 3600;
        String expires = String.valueOf(Instant.now().plusSeconds(maxAge).getEpochSecond());
        String token = "legacy." + expires + "." + CryptoSupport.hmacSha256(properties.teacherSessionSecret(), expires);
        writeCookie(response, token, maxAge);
        return true;
    }

    @Transactional(noRollbackFor = PlatformApiException.class)
    public TeacherPrincipal login(String username, String password, HttpServletRequest request, HttpServletResponse response) {
        return login(username, password, null, request, response);
    }

    @Transactional(noRollbackFor = PlatformApiException.class)
    public TeacherPrincipal login(String username, String password, String portal,
                                  HttpServletRequest request, HttpServletResponse response) {
        Instant now = Instant.now();
        TeacherAccount account = accounts.findByUsernameNormalized(TeacherAccount.normalizeUsername(username))
                .orElseThrow(() -> new PlatformApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码不正确"));
        if (account.getStatus() == TeacherAccount.Status.PENDING) throw statusError("ACCOUNT_PENDING", "账号正在等待审核");
        if (account.getStatus() == TeacherAccount.Status.SUSPENDED) throw statusError("ACCOUNT_SUSPENDED", "账号已停用");
        if (account.getStatus() == TeacherAccount.Status.REJECTED) throw statusError("ACCOUNT_REJECTED", "注册申请未通过");
        if (!account.canAuthenticateAt(now)) throw statusError("ACCOUNT_LOCKED", "登录失败次数过多，请 15 分钟后重试");
        if (!passwordEncoder.matches(password == null ? "" : password, account.getPasswordHash())) {
            account.recordFailedLogin(now);
            if (trialMetrics != null) trialMetrics.loginFailed();
            throw new PlatformApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码不正确");
        }
        validatePortal(account, portal);
        validateSchool(account);
        account.recordSuccessfulLogin(now);
        String token = randomToken();
        long maxAge = properties.teacherSessionTtlHours() * 3600;
        sessions.save(TeacherSession.builder()
                .id(UUID.randomUUID()).teacherId(account.getId()).tokenHash(CryptoSupport.sha256(token))
                .createdAt(now).expiresAt(now.plusSeconds(maxAge)).lastSeenAt(now)
                .ipAddress(clientIp(request)).userAgent(limit(request.getHeader("User-Agent"), 300)).build());
        writeCookie(response, token, maxAge);
        return principal(account);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieValue(request);
        if (!token.isBlank()) sessions.findByTokenHash(CryptoSupport.sha256(token)).ifPresent(session -> session.setRevokedAt(Instant.now()));
        clearCookie(response);
    }

    public void logout(HttpServletResponse response) {
        clearCookie(response);
    }

    @Transactional
    public Optional<TeacherPrincipal> resolve(HttpServletRequest request) {
        return resolveInternal(request, null);
    }

    @Transactional
    public Optional<TeacherPrincipal> resolve(HttpServletRequest request, HttpServletResponse response) {
        return resolveInternal(request, response);
    }

    private Optional<TeacherPrincipal> resolveInternal(HttpServletRequest request, HttpServletResponse response) {
        Object cached = request.getAttribute(RESOLUTION_ATTRIBUTE);
        if (cached instanceof SessionResolution resolution) return resolution.principal();

        Optional<TeacherPrincipal> resolved = resolveUncached(request, response);
        request.setAttribute(RESOLUTION_ATTRIBUTE, new SessionResolution(resolved));
        return resolved;
    }

    private Optional<TeacherPrincipal> resolveUncached(HttpServletRequest request, HttpServletResponse response) {
        if (properties.teacherDevAutoAuth()) {
            return Optional.of(new TeacherPrincipal(TeacherAccount.BOOTSTRAP_ADMIN_ID, "dev-admin", "开发管理员",
                    TeacherAccount.Role.PLATFORM_ADMIN, false));
        }
        String token = cookieValue(request);
        if (token.isBlank()) return Optional.empty();
        if (!properties.schoolProfile() && validLegacyToken(token)) {
            return Optional.of(new TeacherPrincipal(TeacherAccount.BOOTSTRAP_ADMIN_ID, "legacy-dev-admin", "开发管理员",
                    TeacherAccount.Role.PLATFORM_ADMIN, false));
        }
        TeacherSession session = sessions.findByTokenHash(CryptoSupport.sha256(token)).orElse(null);
        Instant now = Instant.now();
        if (session == null || !session.validAt(now)) return Optional.empty();
        TeacherAccount account = accounts.findById(session.getTeacherId()).orElse(null);
        if (account == null || !account.canAuthenticateAt(now)) return Optional.empty();
        if (!schoolActive(account)) return Optional.empty();
        session.setLastSeenAt(now);
        renewIdleWindow(session, token, now, response);
        return Optional.of(principal(account));
    }

    public boolean authenticated(HttpServletRequest request) {
        return resolve(request).isPresent();
    }

    @Transactional
    public void revokeAll(UUID teacherId) {
        sessions.revokeAll(teacherId, Instant.now());
    }

    public static String clientIp(HttpServletRequest request) {
        // Do not trust a client-supplied forwarding header here: login and registration
        // throttles use this value. A deployment behind a reverse proxy must configure
        // the servlet container's trusted-proxy support so getRemoteAddr() is rewritten.
        return limit(request.getRemoteAddr(), 80);
    }

    private TeacherPrincipal principal(TeacherAccount account) {
        return new TeacherPrincipal(account.getId(), account.getUsernameNormalized(), account.getDisplayName(),
                account.getRole(), account.isMustChangePassword(), account.getSchoolId(), account.getSchoolName());
    }

    private void validatePortal(TeacherAccount account, String portal) {
        if (portal == null || portal.isBlank()) return;
        TeacherAccount.Role expected;
        try {
            expected = TeacherAccount.Role.valueOf(portal.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalidPortal) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_PORTAL", "登录入口无效");
        }
        if (account.getRole() != expected) {
            throw statusError("PORTAL_ROLE_MISMATCH", "账号角色与当前登录入口不匹配");
        }
    }

    private void validateSchool(TeacherAccount account) {
        if (!schoolActive(account)) throw statusError("SCHOOL_SUSPENDED", "学校已停用");
    }

    private boolean schoolActive(TeacherAccount account) {
        if (account.getRole() == TeacherAccount.Role.PLATFORM_ADMIN) return true;
        return account.getSchoolId() != null && schools.findById(account.getSchoolId())
                .map(school -> school.getStatus() == School.Status.ACTIVE
                        && (account.getRole() != TeacherAccount.Role.SCHOOL_ADMIN
                        || account.getId().equals(school.getAdminAccountId())))
                .orElse(false);
    }

    private PlatformApiException statusError(String code, String message) {
        return new PlatformApiException(HttpStatus.FORBIDDEN, code, message);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean validLegacyToken(String token) {
        String[] parts = token.split("\\.", 3);
        if (parts.length != 3 || !"legacy".equals(parts[0])) return false;
        try {
            long expiresAt = Long.parseLong(parts[1]);
            return Instant.now().getEpochSecond() < expiresAt
                    && CryptoSupport.constantTimeEquals(parts[2], CryptoSupport.hmacSha256(properties.teacherSessionSecret(), parts[1]));
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void writeCookie(HttpServletResponse response, String token, long maxAge) {
        String secure = properties.schoolProfile() ? "; Secure" : "";
        response.addHeader("Set-Cookie", COOKIE_NAME + "=" + token + "; Path=/; Max-Age=" + Math.min(Integer.MAX_VALUE, maxAge)
                + "; HttpOnly; SameSite=Lax" + secure);
    }

    private void renewIdleWindow(TeacherSession session, String token, Instant now, HttpServletResponse response) {
        if (response == null || session.getCreatedAt() == null || session.getExpiresAt() == null) return;
        long idleSeconds = properties.teacherSessionTtlHours() * 3600;
        long remainingSeconds = Duration.between(now, session.getExpiresAt()).getSeconds();
        if (remainingSeconds > idleSeconds / 2) return;

        Instant absoluteExpiry = session.getCreatedAt().plus(Duration.ofDays(properties.teacherSessionMaxTtlDays()));
        Instant renewedExpiry = now.plusSeconds(idleSeconds);
        if (renewedExpiry.isAfter(absoluteExpiry)) renewedExpiry = absoluteExpiry;
        if (!renewedExpiry.isAfter(session.getExpiresAt())) return;

        session.setExpiresAt(renewedExpiry);
        writeCookie(response, token, Math.max(1, Duration.between(now, renewedExpiry).getSeconds()));
    }

    private void clearCookie(HttpServletResponse response) {
        String secure = properties.schoolProfile() ? "; Secure" : "";
        response.addHeader("Set-Cookie", COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax" + secure);
    }

    private String cookieValue(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        String header = request.getHeader("Cookie");
        if (header != null) {
            for (String part : header.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && COOKIE_NAME.equals(pair[0])) return pair[1];
            }
        }
        return "";
    }

    private static String limit(String value, int max) {
        String normalized = value == null ? "" : value;
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }

    private record SessionResolution(Optional<TeacherPrincipal> principal) {}
}
