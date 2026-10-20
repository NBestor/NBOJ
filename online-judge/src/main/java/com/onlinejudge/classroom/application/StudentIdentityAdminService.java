package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.ClassGroup;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.dto.StudentIdentityAuditResponse;
import com.onlinejudge.classroom.dto.StudentIdentityMergeRequest;
import com.onlinejudge.classroom.dto.StudentIdentitySplitRequest;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentIdentityAdminService {

    private final ClassGroupRepository classGroupRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentIdentityService studentIdentityService;
    private final StudentIdentityAuditService studentIdentityAuditService;

    @Transactional
    public StudentIdentityAuditResponse mergeProfiles(Long classGroupId, StudentIdentityMergeRequest request) {
        ClassGroup classGroup = requireClass(classGroupId);
        List<Long> ids = normalizeIds(request == null ? null : request.getStudentProfileIds());
        if (ids.size() < 2) {
            throw new IllegalArgumentException("至少选择两个学生画像才能合并");
        }
        Map<Long, StudentProfile> profiles = loadProfiles(ids);
        ids.forEach(id -> requireProfileInClass(profiles.get(id), classGroup));
        Long targetId = request.getTargetStudentProfileId() == null ? ids.get(0) : request.getTargetStudentProfileId();
        StudentProfile target = profiles.get(targetId);
        requireProfileInClass(target, classGroup);

        String manualIdentityKey = studentIdentityService.buildManualMergeIdentityKey(classGroup.getId(), target.getId());
        ids.stream()
                .map(profiles::get)
                .filter(Objects::nonNull)
                .forEach(profile -> profile.setIdentityKey(manualIdentityKey));
        studentProfileRepository.saveAll(profiles.values());
        return studentIdentityAuditService.auditClass(classGroup.getId());
    }

    @Transactional
    public StudentIdentityAuditResponse splitProfile(Long classGroupId, StudentIdentitySplitRequest request) {
        ClassGroup classGroup = requireClass(classGroupId);
        if (request == null || request.getStudentProfileId() == null) {
            throw new IllegalArgumentException("学生画像不能为空");
        }
        StudentProfile profile = studentProfileRepository.findById(request.getStudentProfileId())
                .orElseThrow(() -> new IllegalArgumentException("学生画像不存在: " + request.getStudentProfileId()));
        requireProfileInClass(profile, classGroup);
        profile.setIdentityKey(studentIdentityService.buildManualSplitIdentityKey(classGroup.getId(), profile.getId()));
        studentProfileRepository.save(profile);
        return studentIdentityAuditService.auditClass(classGroup.getId());
    }

    private ClassGroup requireClass(Long classGroupId) {
        return classGroupRepository.findById(classGroupId)
                .orElseThrow(() -> new IllegalArgumentException("班级不存在: " + classGroupId));
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private Map<Long, StudentProfile> loadProfiles(List<Long> ids) {
        return studentProfileRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(StudentProfile::getId, Function.identity()));
    }

    private void requireProfileInClass(StudentProfile profile, ClassGroup classGroup) {
        if (profile == null) {
            throw new IllegalArgumentException("学生画像不存在");
        }
        if (!Objects.equals(profile.getClassGroupId(), classGroup.getId())) {
            throw new IllegalArgumentException("学生画像不属于当前班级: " + profile.getId());
        }
    }
}
