package com.onlinejudge.classroom.dto;

import com.onlinejudge.classroom.domain.StudentProfile;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StudentProfileResponse {
    private Long id;
    private Long classGroupId;
    private String className;
    private String displayName;
    private String studentNo;
    private String note;
    private String identityKey;
    private String studentAccessToken;
    private StudentProfile.RosterStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;

    public static StudentProfileResponse from(StudentProfile student) {
        return StudentProfileResponse.builder()
                .id(student.getId())
                .classGroupId(student.getClassGroupId())
                .className(null)
                .displayName(student.getDisplayName())
                .studentNo(student.getStudentNo())
                .note(student.getNote())
                .identityKey(student.getIdentityKey())
                .studentAccessToken(null)
                .status(student.getStatus())
                .createdAt(student.getCreatedAt())
                .lastSeenAt(student.getLastSeenAt())
                .build();
    }

    public static StudentProfileResponse from(StudentProfile student, String className) {
        StudentProfileResponse response = from(student);
        response.setClassName(className);
        return response;
    }

    public static StudentProfileResponse from(StudentProfile student, String className, String studentAccessToken) {
        StudentProfileResponse response = from(student, className);
        response.setStudentAccessToken(studentAccessToken);
        return response;
    }
}
