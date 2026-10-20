package com.onlinejudge.classroom.persistence;

import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherDiagnosisCorrectionRepository extends JpaRepository<TeacherDiagnosisCorrection, Long> {
    Optional<TeacherDiagnosisCorrection> findTopBySubmissionIdOrderByCorrectedAtDesc(Long submissionId);
    List<TeacherDiagnosisCorrection> findBySubmissionIdIn(Collection<Long> submissionIds);
    List<TeacherDiagnosisCorrection> findByAssignmentIdIn(Collection<Long> assignmentIds);
    List<TeacherDiagnosisCorrection> findByAssignmentIdOrderByCorrectedAtDesc(Long assignmentId);
    List<TeacherDiagnosisCorrection> findByAssignmentIdAndEvalCandidateTrueOrderByCorrectedAtDesc(Long assignmentId);
}
