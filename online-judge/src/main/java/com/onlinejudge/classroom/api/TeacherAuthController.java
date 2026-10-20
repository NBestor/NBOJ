package com.onlinejudge.classroom.api;

import com.onlinejudge.classroom.dto.AuthSessionResponse;
import com.onlinejudge.classroom.dto.TeacherLoginRequest;
import com.onlinejudge.shared.security.TeacherSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/auth")
@RequiredArgsConstructor
public class TeacherAuthController {

    private final TeacherSessionService teacherSessionService;

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody TeacherLoginRequest request,
                                                     HttpServletResponse response) {
        if (!teacherSessionService.login(request.getPassword(), response)) {
            return ResponseEntity.status(401)
                    .body(AuthSessionResponse.builder().authenticated(false).build());
        }
        return ResponseEntity.ok(AuthSessionResponse.builder().authenticated(true).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthSessionResponse> logout(HttpServletResponse response) {
        teacherSessionService.logout(response);
        return ResponseEntity.ok(AuthSessionResponse.builder().authenticated(false).build());
    }

    @GetMapping("/session")
    public ResponseEntity<AuthSessionResponse> session(HttpServletRequest request) {
        return ResponseEntity.ok(AuthSessionResponse.builder()
                .authenticated(teacherSessionService.authenticated(request))
                .build());
    }
}
