package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StudentIdentityAuditResponse {
    private Long classGroupId;
    private String className;
    private long totalProfiles;
    private long stableIdentityCount;
    private long manualIdentityCount;
    private long legacyIdentityCount;
    private long missingStudentNoCount;
    private long duplicateGroupCount;
    private List<DuplicateGroup> duplicateGroups;

    @Data
    @Builder
    public static class DuplicateGroup {
        private String stableIdentityKey;
        private String reason;
        private List<Long> studentProfileIds;
        private List<String> displayNames;
        private List<String> studentNos;
        private List<String> identityKeys;
    }
}
