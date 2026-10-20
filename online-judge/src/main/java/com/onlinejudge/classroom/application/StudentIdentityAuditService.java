package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.ClassGroup;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.dto.StudentIdentityAuditResponse;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StudentIdentityAuditService {

    private final ClassGroupRepository classGroupRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentIdentityService studentIdentityService;

    public StudentIdentityAuditResponse auditClass(Long classGroupId) {
        ClassGroup classGroup = classGroupRepository.findById(classGroupId)
                .orElseThrow(() -> new IllegalArgumentException("班级不存在: " + classGroupId));
        List<StudentProfile> profiles = studentProfileRepository.findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(classGroupId);
        Map<String, List<StudentProfile>> groups = profiles.stream()
                .filter(Objects::nonNull)
                .collect(LinkedHashMap::new,
                        (target, profile) -> target.computeIfAbsent(stableKeyFor(profile, classGroup.getName()), ignored -> new java.util.ArrayList<>()).add(profile),
                        Map::putAll);

        List<StudentIdentityAuditResponse.DuplicateGroup> duplicateGroups = groups.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().isBlank()
                        && entry.getValue().size() > 1
                        && !studentIdentityService.isManualIdentityKey(entry.getKey()))
                .map(entry -> StudentIdentityAuditResponse.DuplicateGroup.builder()
                        .stableIdentityKey(entry.getKey())
                        .reason("同一稳定身份下存在多个学生画像，建议后续合并学习记录。")
                        .studentProfileIds(entry.getValue().stream().map(StudentProfile::getId).toList())
                        .displayNames(entry.getValue().stream().map(StudentProfile::getDisplayName).distinct().toList())
                        .studentNos(entry.getValue().stream().map(StudentProfile::getStudentNo).filter(value -> value != null && !value.isBlank()).distinct().toList())
                        .identityKeys(entry.getValue().stream().map(StudentProfile::getIdentityKey).distinct().toList())
                        .build())
                .toList();

        return StudentIdentityAuditResponse.builder()
                .classGroupId(classGroup.getId())
                .className(classGroup.getName())
                .totalProfiles(profiles.size())
                .stableIdentityCount(profiles.stream().filter(profile -> studentIdentityService.isStableIdentityKey(profile.getIdentityKey())).count())
                .manualIdentityCount(profiles.stream().filter(profile -> studentIdentityService.isManualIdentityKey(profile.getIdentityKey())).count())
                .legacyIdentityCount(profiles.stream().filter(profile -> !studentIdentityService.isStableIdentityKey(profile.getIdentityKey())
                        && !studentIdentityService.isManualIdentityKey(profile.getIdentityKey())).count())
                .missingStudentNoCount(profiles.stream().filter(profile -> profile.getStudentNo() == null || profile.getStudentNo().isBlank()).count())
                .duplicateGroupCount(duplicateGroups.size())
                .duplicateGroups(duplicateGroups.stream().limit(20).toList())
                .build();
    }

    private String stableKeyFor(StudentProfile profile, String className) {
        if (studentIdentityService.isManualIdentityKey(profile.getIdentityKey())) {
            return profile.getIdentityKey();
        }
        return studentIdentityService.buildStableIdentityKey(
                profile.getClassGroupId(),
                className,
                profile.getDisplayName(),
                profile.getStudentNo()
        );
    }
}
