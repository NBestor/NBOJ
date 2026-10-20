package com.onlinejudge.classroom.dto;

import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TeacherDiagnosisCorrectionResponse {
    private Long id;
    private Long assignmentId;
    private Long submissionId;
    private Long feedbackRevisionId;
    private Long studentProfileId;
    private String originalIssueTag;
    private String originalFineGrainedTag;
    private String correctedIssueTag;
    private String correctedFineGrainedTag;
    private String correctionType;
    private String targetIssueId;
    private String correctedKnowledgePath;
    private String targetEvidenceRef;
    private String teacherNote;
    private boolean evalCandidate;
    private String correctedBy;
    private LocalDateTime correctedAt;

    public static TeacherDiagnosisCorrectionResponse from(TeacherDiagnosisCorrection correction) {
        if (correction == null) {
            return null;
        }
        return TeacherDiagnosisCorrectionResponse.builder()
                .id(correction.getId())
                .assignmentId(correction.getAssignmentId())
                .submissionId(correction.getSubmissionId())
                .feedbackRevisionId(correction.getFeedbackRevisionId())
                .studentProfileId(correction.getStudentProfileId())
                .originalIssueTag(correction.getOriginalIssueTag())
                .originalFineGrainedTag(correction.getOriginalFineGrainedTag())
                .correctedIssueTag(correction.getCorrectedIssueTag())
                .correctedFineGrainedTag(correction.getCorrectedFineGrainedTag())
                .correctionType(correction.getCorrectionType())
                .targetIssueId(correction.getTargetIssueId())
                .correctedKnowledgePath(correction.getCorrectedKnowledgePath())
                .targetEvidenceRef(correction.getTargetEvidenceRef())
                .teacherNote(correction.getTeacherNote())
                .evalCandidate(correction.isEvalCandidate())
                .correctedBy(correction.getCorrectedBy())
                .correctedAt(correction.getCorrectedAt())
                .build();
    }
}
