package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByIdentityKey(String identityKey);
    List<StudentProfile> findByIdentityKeyOrderByCreatedAtDesc(String identityKey);
    List<StudentProfile> findByIdentityKeyIn(Collection<String> identityKeys);
    List<StudentProfile> findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(Long classGroupId);
    List<StudentProfile> findByClassGroupIdAndStudentNoIgnoreCaseOrderByCreatedAtDesc(Long classGroupId, String studentNo);
    List<StudentProfile> findByClassGroupIdAndDisplayNameIgnoreCaseOrderByCreatedAtDesc(Long classGroupId, String displayName);
    default Optional<StudentProfile> findFirstByClassGroupIdAndStudentNoIgnoreCase(Long classGroupId, String studentNo) {
        return findByClassGroupIdAndStudentNoIgnoreCaseOrderByCreatedAtDesc(classGroupId, studentNo).stream().findFirst();
    }
    default long countByClassGroupIdAndStatus(Long classGroupId, StudentProfile.RosterStatus status) {
        return findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(classGroupId).stream()
                .filter(student -> student.getStatus() == status)
                .count();
    }
    default List<StudentProfile> findByClassGroupIdAndStatusOrderByStudentNoAscDisplayNameAsc(
            Long classGroupId,
            StudentProfile.RosterStatus status
    ) {
        return findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(classGroupId).stream()
                .filter(student -> student.getStatus() == status)
                .toList();
    }
}
