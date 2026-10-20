package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.dto.TeacherDiagnosisCorrectionResponse;
import com.onlinejudge.classroom.dto.TeacherSubmissionEvidenceResponse;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.submission.application.SubmissionAnalysisService;
import com.onlinejudge.submission.domain.AiDiagnosisRun;
import com.onlinejudge.submission.domain.StudentAiFeedback;
import com.onlinejudge.submission.domain.StudentAiFeedbackRevision;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.dto.StudentAiFeedbackResponse;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.persistence.AiDiagnosisRunRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRevisionRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherSubmissionEvidenceService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisService submissionAnalysisService;
    private final StudentAiFeedbackRepository feedbackRepository;
    private final StudentAiFeedbackRevisionRepository revisionRepository;
    private final AiDiagnosisRunRepository runRepository;
    private final TeacherDiagnosisCorrectionRepository correctionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TeacherSubmissionEvidenceResponse getEvidence(Long assignmentId, Long submissionId) {
        Submission submission = requireSubmission(assignmentId, submissionId);
        List<StudentAiFeedbackRevision> revisions = revisionRepository.findBySubmissionIdOrderByVersionNumberDesc(submissionId);
        Map<Long, AiDiagnosisRun> runs = runRepository.findBySubmissionIdOrderByVersionNumberDesc(submissionId).stream()
                .collect(Collectors.toMap(AiDiagnosisRun::getId, Function.identity()));
        Long latestRevisionId = feedbackRepository.findBySubmissionId(submissionId)
                .map(StudentAiFeedback::getLatestRevisionId)
                .orElse(null);
        List<TeacherDiagnosisCorrectionResponse> corrections = correctionRepository.findBySubmissionIdIn(List.of(submissionId)).stream()
                .sorted(Comparator.comparing(
                        correction -> correction.getCorrectedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(TeacherDiagnosisCorrectionResponse::from)
                .toList();
        return TeacherSubmissionEvidenceResponse.builder()
                .submission(submissionAnalysisService.getDetailedSubmission(submission.getId()))
                .analysisVersions(revisions.stream()
                        .map(revision -> toVersion(revision, runs.get(revision.getDiagnosisRunId()), latestRevisionId))
                        .toList())
                .corrections(corrections)
                .build();
    }

    public void requireSubmissionAccess(Long assignmentId, Long submissionId) {
        requireSubmission(assignmentId, submissionId);
    }

    private Submission requireSubmission(Long assignmentId, Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));
        if (!Objects.equals(submission.getAssignmentId(), assignmentId)) {
            throw new IllegalArgumentException("提交记录不属于当前作业");
        }
        return submission;
    }

    private TeacherSubmissionEvidenceResponse.AnalysisVersion toVersion(
            StudentAiFeedbackRevision revision,
            AiDiagnosisRun run,
            Long latestRevisionId
    ) {
        boolean official = run != null ? run.isOfficialVersion() : Objects.equals(latestRevisionId, revision.getId());
        return TeacherSubmissionEvidenceResponse.AnalysisVersion.builder()
                .id(revision.getId())
                .versionNumber(revision.getVersionNumber())
                .generationKey(revision.getGenerationKey())
                .status(revision.getStatus())
                .source(revision.getSource())
                .officialVersion(official)
                .diagnosisRunId(revision.getDiagnosisRunId())
                .diagnosisRunVersion(revision.getDiagnosisRunVersion())
                .provider(revision.getProvider())
                .model(revision.getModel())
                .promptVersion(revision.getPromptVersion())
                .schemaVersion(revision.getSchemaVersion())
                .generatedAt(revision.getGeneratedAt())
                .failureReason(revision.getFailureReason())
                .analysis(read(revision.getAnalysisJson(), SubmissionAnalysisResponse.class))
                .feedback(read(revision.getFeedbackJson(), StudentAiFeedbackResponse.class))
                .evidence(readTree(revision.getEvidenceJson()))
                .build();
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }
}
