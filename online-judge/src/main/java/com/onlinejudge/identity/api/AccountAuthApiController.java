package com.onlinejudge.identity.api;

import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.application.RequestRateLimiter;
import com.onlinejudge.identity.application.TeacherAccountService;
import com.onlinejudge.identity.dto.AccountLoginRequest;
import com.onlinejudge.identity.dto.ChangePasswordRequest;
import com.onlinejudge.identity.dto.TeacherSessionResponse;
import com.onlinejudge.shared.security.TeacherSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/account")
@RequiredArgsConstructor
public class AccountAuthApiController {
    private final TeacherSessionService sessions;
    private final TeacherAccountService accounts;
    private final CurrentTeacherContext current;
    private final RequestRateLimiter rateLimiter;

    @PostMapping("/login")
    public ResponseEntity<TeacherSessionResponse> login(@Valid @RequestBody AccountLoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response, CsrfToken csrfToken) {
        if (csrfToken != null) csrfToken.getToken();
        rateLimiter.check("account-login", TeacherSessionService.clientIp(httpRequest), 30, 900);
        return ResponseEntity.ok(TeacherSessionResponse.from(sessions.login(request.username(), request.password(),
                request.portal(), httpRequest, response)));
    }

    @GetMapping("/session")
    public ResponseEntity<TeacherSessionResponse> session(HttpServletRequest request, CsrfToken csrfToken) {
        if (csrfToken != null) csrfToken.getToken();
        return ResponseEntity.ok(sessions.resolve(request).map(TeacherSessionResponse::from)
                .orElseGet(TeacherSessionResponse::anonymous));
    }

    @PostMapping("/logout")
    public ResponseEntity<TeacherSessionResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request, response);
        return ResponseEntity.ok(TeacherSessionResponse.anonymous());
    }

    @PostMapping("/change-password")
    public ResponseEntity<TeacherSessionResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        accounts.changePassword(current.require(), request.currentPassword(), request.newPassword(),
                TeacherSessionService.clientIp(httpRequest));
        sessions.logout(response);
        return ResponseEntity.ok(TeacherSessionResponse.anonymous());
    }
}
