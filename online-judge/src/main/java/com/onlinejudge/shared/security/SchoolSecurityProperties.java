package com.onlinejudge.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SchoolSecurityProperties {

    @Value("${app.profile:${APP_PROFILE:dev}}")
    private String appProfile;

    @Value("${security.teacher.password:${TEACHER_PASSWORD:}}")
    private String teacherPassword;

    @Value("${security.teacher.session-secret:${TEACHER_SESSION_SECRET:dev-teacher-session-secret-change-me}}")
    private String teacherSessionSecret;

    @Value("${security.teacher.session-ttl-hours:${TEACHER_SESSION_TTL_HOURS:12}}")
    private long teacherSessionTtlHours;

    @Value("${security.teacher.session-max-ttl-days:${TEACHER_SESSION_MAX_TTL_DAYS:7}}")
    private long teacherSessionMaxTtlDays;

    @Value("${security.teacher.dev-auto-auth:${TEACHER_DEV_AUTO_AUTH:false}}")
    private boolean teacherDevAutoAuth;

    @Value("${security.student.token-secret:${STUDENT_TOKEN_SECRET:dev-student-token-secret-change-me}}")
    private String studentTokenSecret;

    @Value("${security.student.token-ttl-days:${STUDENT_TOKEN_TTL_DAYS:30}}")
    private long studentTokenTtlDays;

    public String appProfile() {
        return trim(appProfile);
    }

    public boolean schoolProfile() {
        return "school".equalsIgnoreCase(appProfile());
    }

    public boolean teacherDevAutoAuth() {
        return teacherDevAutoAuth && !schoolProfile();
    }

    public String teacherPassword() {
        return trim(teacherPassword);
    }

    public boolean teacherPasswordConfigured() {
        return !teacherPassword().isBlank();
    }

    public String teacherSessionSecret() {
        return trim(teacherSessionSecret);
    }

    public boolean teacherSessionSecretConfigured() {
        return secretConfigured(teacherSessionSecret(), "dev-teacher-session-secret-change-me");
    }

    public long teacherSessionTtlHours() {
        return Math.min(24 * 30, Math.max(1, teacherSessionTtlHours));
    }

    public long teacherSessionMaxTtlDays() {
        long idleWindowDays = Math.max(1, (teacherSessionTtlHours() + 23) / 24);
        return Math.min(365, Math.max(idleWindowDays, teacherSessionMaxTtlDays));
    }

    public String studentTokenSecret() {
        return trim(studentTokenSecret);
    }

    public boolean studentTokenSecretConfigured() {
        return secretConfigured(studentTokenSecret(), "dev-student-token-secret-change-me");
    }

    public long studentTokenTtlDays() {
        return Math.max(1, studentTokenTtlDays);
    }

    private boolean secretConfigured(String value, String devDefault) {
        String trimmed = trim(value);
        return !trimmed.isBlank() && !devDefault.equals(trimmed);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
