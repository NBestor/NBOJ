package com.onlinejudge.classroom.dto;

import com.onlinejudge.classroom.domain.ClassGroup;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ClassGroupResponse {
    private Long id;
    private String name;
    private String grade;
    private String teacherName;
    private String joinCode;
    private long activeStudentCount;
    private LocalDateTime createdAt;

    public static ClassGroupResponse from(ClassGroup classGroup) {
        return from(classGroup, null, 0);
    }

    public static ClassGroupResponse from(ClassGroup classGroup, String joinCode, long activeStudentCount) {
        return ClassGroupResponse.builder()
                .id(classGroup.getId())
                .name(classGroup.getName())
                .grade(classGroup.getGrade())
                .teacherName(classGroup.getTeacherName())
                .joinCode(joinCode)
                .activeStudentCount(activeStudentCount)
                .createdAt(classGroup.getCreatedAt())
                .build();
    }
}
