package com.onlinejudge.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.identity.application.TeacherPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.onlinejudge.system.application.TrialMetrics;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TeacherAuthFilter extends OncePerRequestFilter {

    private final TeacherSessionService teacherSessionService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private TrialMetrics trialMetrics;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<TeacherPrincipal> resolved = teacherSessionService.resolve(request, response);
        resolved.ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())))));

        if (!requiresTeacherSession(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (resolved.isEmpty()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_REQUIRED", "请先登录教师端");
            return;
        }
        if (resolved.get().mustChangePassword()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", "请先修改临时密码");
            return;
        }
        String path = request.getRequestURI();
        if (requiresPlatformRole(path)
                && resolved.get().role() != com.onlinejudge.identity.domain.TeacherAccount.Role.PLATFORM_ADMIN) {
            if (trialMetrics != null) trialMetrics.accessDenied();
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "PLATFORM_ADMIN_REQUIRED", "需要平台管理员权限");
            return;
        }
        if (path.startsWith("/api/admin/problem-reviews")
                && resolved.get().role() != com.onlinejudge.identity.domain.TeacherAccount.Role.PLATFORM_ADMIN) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "PLATFORM_ADMIN_REQUIRED", "需要平台管理员权限");
            return;
        }
        if (path.startsWith("/api/admin/") && !path.startsWith("/api/admin/problem-reviews")
                && resolved.get().role() != com.onlinejudge.identity.domain.TeacherAccount.Role.SCHOOL_ADMIN) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "SCHOOL_ADMIN_REQUIRED", "需要学校管理员权限");
            return;
        }
        if (path.startsWith("/api/school-admin/")
                && resolved.get().role() != com.onlinejudge.identity.domain.TeacherAccount.Role.SCHOOL_ADMIN) {
            if (trialMetrics != null) trialMetrics.accessDenied();
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "SCHOOL_ADMIN_REQUIRED", "需要学校管理员权限");
            return;
        }
        if (path.startsWith("/api/teacher/") && !path.startsWith("/api/teacher/auth/")
                && resolved.get().role() != com.onlinejudge.identity.domain.TeacherAccount.Role.TEACHER) {
            if (trialMetrics != null) trialMetrics.accessDenied();
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "需要教师权限");
            return;
        }
        if (requiresTeacherRole(request)
                && resolved.get().role() != com.onlinejudge.identity.domain.TeacherAccount.Role.TEACHER) {
            if (trialMetrics != null) trialMetrics.accessDenied();
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "需要教师权限");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("code", code, "error", message));
    }

    private boolean requiresTeacherSession(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path == null) return false;
        if (path.startsWith("/api/admin/")) return true;
        if (path.startsWith("/api/platform-admin/") || path.startsWith("/api/school-admin/")) return true;
        if (path.equals("/actuator/prometheus")) return true;
        if (path.startsWith("/api/teacher/")) return !path.startsWith("/api/teacher/auth/");
        if (path.matches("/api/problems/\\d+/manage")) return true;
        if (path.matches("/api/problems/\\d+/growth-report.*") || path.startsWith("/api/leaderboard/")) return true;
        return path.equals("/api/problems") && "POST".equalsIgnoreCase(method)
                || path.matches("/api/problems/\\d+") && !"GET".equalsIgnoreCase(method)
                || path.startsWith("/api/system/ai-smoke");
    }

    private boolean requiresPlatformRole(String path) {
        return path.startsWith("/api/platform-admin/") || path.startsWith("/api/system/ai-smoke")
                || path.equals("/actuator/prometheus");
    }

    private boolean requiresTeacherRole(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/api/leaderboard/") || path.matches("/api/problems/\\d+/manage")
                || path.matches("/api/problems/\\d+/growth-report.*")) return true;
        return path.equals("/api/problems") && "POST".equalsIgnoreCase(method)
                || path.matches("/api/problems/\\d+") && !"GET".equalsIgnoreCase(method);
    }
}
