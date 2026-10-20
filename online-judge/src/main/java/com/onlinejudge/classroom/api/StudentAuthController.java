package com.onlinejudge.classroom.api;

import com.onlinejudge.classroom.application.ClassroomService;
import com.onlinejudge.classroom.dto.StudentLoginRequest;
import com.onlinejudge.classroom.dto.StudentProfileResponse;
import com.onlinejudge.identity.application.RequestRateLimiter;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import com.onlinejudge.shared.security.TeacherSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/student")
@RequiredArgsConstructor
public class StudentAuthController {
    private final ClassroomService classroomService;
    private final StudentAccessTokenService sessions;
    private final RequestRateLimiter rateLimiter;

    @PostMapping("/login")
    public ResponseEntity<StudentProfileResponse> login(@Valid @RequestBody StudentLoginRequest request,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse response,
                                                        CsrfToken csrfToken) {
        if (csrfToken != null) csrfToken.getToken();
        rateLimiter.check("student-login", TeacherSessionService.clientIp(httpRequest), 40, 900);
        StudentProfileResponse student = classroomService.loginStudent(request);
        var profile = new com.onlinejudge.classroom.domain.StudentProfile();
        profile.setId(student.getId());
        profile.setStatus(student.getStatus());
        sessions.issueCookie(profile, httpRequest, response);
        return ResponseEntity.ok(student);
    }

    @GetMapping("/session")
    public ResponseEntity<StudentProfileResponse> session(HttpServletRequest request, CsrfToken csrfToken) {
        if (csrfToken != null) csrfToken.getToken();
        Long studentId = sessions.currentStudentId(request);
        return studentId == null ? ResponseEntity.status(401).build()
                : ResponseEntity.ok(classroomService.getStudentProfile(studentId));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
