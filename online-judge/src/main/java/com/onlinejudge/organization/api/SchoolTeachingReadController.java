package com.onlinejudge.organization.api;

import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.organization.application.SchoolTeachingReadService;
import com.onlinejudge.shared.security.TeacherSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/school-admin/teaching")
@RequiredArgsConstructor
public class SchoolTeachingReadController {
    private final SchoolTeachingReadService teaching;
    private final CurrentTeacherContext current;

    @GetMapping("/classes")
    public ResponseEntity<List<SchoolTeachingReadService.TeachingClass>> classes(HttpServletRequest request) {
        return ResponseEntity.ok(teaching.classes(admin(), ip(request)));
    }
    @GetMapping("/classes/{id}/students")
    public ResponseEntity<List<SchoolTeachingReadService.TeachingStudent>> students(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(teaching.students(id, admin(), ip(request)));
    }
    @GetMapping("/classes/{id}/assignments")
    public ResponseEntity<List<SchoolTeachingReadService.TeachingAssignment>> assignments(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(teaching.assignments(id, admin(), ip(request)));
    }
    @GetMapping("/assignments/{id}/submissions")
    public ResponseEntity<List<SchoolTeachingReadService.TeachingSubmission>> submissions(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(teaching.assignmentSubmissions(id, admin(), ip(request)));
    }
    @GetMapping("/submissions/{id}")
    public ResponseEntity<SchoolTeachingReadService.TeachingSubmission> submission(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(teaching.submission(id, admin(), ip(request)));
    }
    @GetMapping(value = "/assignments/{id}/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> export(@PathVariable Long id, HttpServletRequest request) {
        String csv = teaching.exportAssignment(id, admin(), ip(request));
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assignment-" + id + ".csv")
                .body(csv);
    }
    private TeacherPrincipal admin() { return current.requireSchoolAdmin(); }
    private String ip(HttpServletRequest request) { return TeacherSessionService.clientIp(request); }
}
