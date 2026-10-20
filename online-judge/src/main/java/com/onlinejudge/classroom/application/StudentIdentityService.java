package com.onlinejudge.classroom.application;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class StudentIdentityService {

    public String buildPreferredIdentityKey(Long assignmentId,
                                            Long classGroupId,
                                            String className,
                                            String displayName,
                                            String studentNo) {
        String stableKey = buildStableIdentityKey(classGroupId, className, displayName, studentNo);
        if (!stableKey.isBlank()) {
            return stableKey;
        }
        return buildLegacyAssignmentIdentityKey(assignmentId, classGroupId, className, displayName, studentNo);
    }

    public String buildStableIdentityKey(Long classGroupId,
                                         String className,
                                         String displayName,
                                         String studentNo) {
        String personKey = buildPersonKey(displayName, studentNo);
        if (personKey.isBlank()) {
            return "";
        }
        if (classGroupId != null) {
            return "class:" + classGroupId + ":" + personKey;
        }
        String normalizedClassName = normalizeKeyPart(className);
        if (!normalizedClassName.isBlank()) {
            return "class-name:" + normalizedClassName + ":" + personKey;
        }
        return "";
    }

    public String buildLegacyAssignmentIdentityKey(Long assignmentId,
                                                   Long classGroupId,
                                                   String className,
                                                   String displayName,
                                                   String studentNo) {
        return String.join(":",
                String.valueOf(assignmentId),
                String.valueOf(classGroupId == null ? normalizeNullable(className) : classGroupId),
                normalizeRequired(displayName, "姓名不能为空").toLowerCase(Locale.ROOT),
                normalizeNullable(studentNo).toLowerCase(Locale.ROOT)
        );
    }

    public boolean isStableIdentityKey(String identityKey) {
        String normalized = normalizeNullable(identityKey).toLowerCase(Locale.ROOT);
        return normalized.startsWith("class:") || normalized.startsWith("class-name:");
    }

    public boolean isManualIdentityKey(String identityKey) {
        String normalized = normalizeNullable(identityKey).toLowerCase(Locale.ROOT);
        return normalized.startsWith("manual-merge:") || normalized.startsWith("manual-split:");
    }

    public String buildManualMergeIdentityKey(Long classGroupId, Long targetStudentProfileId) {
        if (classGroupId == null || targetStudentProfileId == null) {
            throw new IllegalArgumentException("班级和目标学生画像不能为空");
        }
        return "manual-merge:" + classGroupId + ":" + targetStudentProfileId;
    }

    public String buildManualSplitIdentityKey(Long classGroupId, Long studentProfileId) {
        if (classGroupId == null || studentProfileId == null) {
            throw new IllegalArgumentException("班级和学生画像不能为空");
        }
        return "manual-split:" + classGroupId + ":" + studentProfileId;
    }

    private String buildPersonKey(String displayName, String studentNo) {
        String normalizedStudentNo = normalizeKeyPart(studentNo);
        if (!normalizedStudentNo.isBlank()) {
            return normalizedStudentNo;
        }
        return normalizeKeyPart(displayName);
    }

    private String normalizeKeyPart(String value) {
        return normalizeNullable(value)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        return value == null ? "" : value.trim();
    }
}
