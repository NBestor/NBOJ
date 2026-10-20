package com.onlinejudge.identity.api;

import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.application.RequestRateLimiter;
import com.onlinejudge.identity.application.TeacherAccountService;
import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.identity.dto.ChangePasswordRequest;
import com.onlinejudge.identity.dto.TeacherAccountResponse;
import com.onlinejudge.identity.dto.TeacherPasswordLoginRequest;
import com.onlinejudge.identity.dto.TeacherRegisterRequest;
import com.onlinejudge.identity.dto.TeacherSessionResponse;
import com.onlinejudge.shared.security.TeacherSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/teacher")
@RequiredArgsConstructor
public class TeacherAuthApiController {
    private final TeacherAccountService accounts;
    private final TeacherSessionService sessions;
    private final CurrentTeacherContext currentTeacher;
    private final RequestRateLimiter rateLimiter;

    @PostMapping("/register")
    public ResponseEntity<TeacherAccountResponse> register(@Valid @RequestBody TeacherRegisterRequest request,
                                                           HttpServletRequest httpRequest) {
        String ip = TeacherSessionService.clientIp(httpRequest);
        rateLimiter.check("teacher-register", ip, 10, 3600);
        return ResponseEntity.ok(accounts.register(request, ip));
    }

    @PostMapping("/login")
    public ResponseEntity<TeacherSessionResponse> login(@Valid @RequestBody TeacherPasswordLoginRequest request,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse response,
                                                        CsrfToken csrfToken) {
        if (csrfToken != null) csrfToken.getToken();
        String ip = TeacherSessionService.clientIp(httpRequest);
        rateLimiter.check("teacher-login", ip, 30, 900);
        return ResponseEntity.ok(TeacherSessionResponse.from(
                sessions.login(request.username(), request.password(), "TEACHER", httpRequest, response)));
    }

    @GetMapping("/session")
    public ResponseEntity<TeacherSessionResponse> session(HttpServletRequest request, CsrfToken csrfToken) {
        if (csrfToken != null) csrfToken.getToken();
        return ResponseEntity.ok(sessions.resolve(request)
                .map(TeacherSessionResponse::from).orElseGet(TeacherSessionResponse::anonymous));
    }

    @PostMapping("/logout")
    public ResponseEntity<TeacherSessionResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request, response);
        return ResponseEntity.ok(TeacherSessionResponse.anonymous());
    }

    @PostMapping("/change-password")
    public ResponseEntity<TeacherSessionResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                                 HttpServletRequest httpRequest,
                                                                 HttpServletResponse response) {
        TeacherPrincipal principal = currentTeacher.require();
        accounts.changePassword(principal, request.currentPassword(), request.newPassword(),
                TeacherSessionService.clientIp(httpRequest));
        sessions.logout(response);
        return ResponseEntity.ok(TeacherSessionResponse.anonymous());
    }
}
