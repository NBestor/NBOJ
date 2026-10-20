package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.TeacherDiagnosisCorrection;
import com.onlinejudge.classroom.persistence.TeacherDiagnosisCorrectionRepository;
import com.onlinejudge.submission.application.SubmissionAnalysisService;
import com.onlinejudge.submission.domain.AiDiagnosisRun;
import com.onlinejudge.submission.domain.StudentAiFeedback;
import com.onlinejudge.submission.domain.StudentAiFeedbackRevision;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.dto.SubmissionResponse;
import com.onlinejudge.submission.persistence.AiDiagnosisRunRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRevisionRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeacherSubmissionEvidenceServiceTest {

    @Test
    void returnsImmutableAnalysisSnapshotsAndVersionBoundCorrections() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionAnalysisService analysisService = mock(SubmissionAnalysisService.class);
        StudentAiFeedbackRepository feedbackRepository = mock(StudentAiFeedbackRepository.class);
        StudentAiFeedbackRevisionRepository revisionRepository = mock(StudentAiFeedbackRevisionRepository.class);
        AiDiagnosisRunRepository runRepository = mock(AiDiagnosisRunRepository.class);
        TeacherDiagnosisCorrectionRepository correctionRepository = mock(TeacherDiagnosisCorrectionRepository.class);
        TeacherSubmissionEvidenceService service = new TeacherSubmissionEvidenceService(
                submissionRepository,
                analysisService,
                feedbackRepository,
                revisionRepository,
                runRepository,
                correctionRepository,
                new ObjectMapper().findAndRegisterModules()
        );
        Submission submission = Submission.builder().id(31L).assignmentId(7L).problemId(9L).studentProfileId(11L).build();
        StudentAiFeedbackRevision latest = StudentAiFeedbackRevision.builder()
                .id(102L)
                .submissionId(31L)
                .diagnosisRunId(202L)
                .diagnosisRunVersion(2)
                .versionNumber(2)
                .generationKey("generation-2")
                .status("READY")
                .source("MODEL")
                .analysisJson("{\"submissionId\":31,\"headline\":\"保存时的判断\",\"summary\":\"版本二摘要\"}")
                .feedbackJson("{\"submissionId\":31,\"status\":\"READY\",\"source\":\"MODEL\",\"repairItems\":[]}")
                .evidenceJson("{\"sourceCode\":\"line:3\"}")
                .generatedAt(LocalDateTime.now())
                .build();
        StudentAiFeedbackRevision legacy = StudentAiFeedbackRevision.builder()
                .id(101L)
                .submissionId(31L)
                .versionNumber(1)
                .generationKey("generation-1")
                .status("READY")
                .source("MODEL")
                .feedbackJson("{\"submissionId\":31,\"status\":\"READY\",\"repairItems\":[]}")
                .generatedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        AiDiagnosisRun run = AiDiagnosisRun.builder()
                .id(202L)
                .submissionId(31L)
                .versionNumber(2)
                .generationKey("generation-2")
                .officialVersion(true)
                .build();
        TeacherDiagnosisCorrection correction = TeacherDiagnosisCorrection.builder()
                .id(301L)
                .assignmentId(7L)
                .submissionId(31L)
                .feedbackRevisionId(102L)
                .correctedIssueTag("BOUNDARY")
                .correctedAt(LocalDateTime.now())
                .build();

        when(submissionRepository.findById(31L)).thenReturn(Optional.of(submission));
        when(analysisService.getDetailedSubmission(31L)).thenReturn(SubmissionResponse.builder().id(31L).build());
        when(feedbackRepository.findBySubmissionId(31L)).thenReturn(Optional.of(StudentAiFeedback.builder().latestRevisionId(102L).build()));
        when(revisionRepository.findBySubmissionIdOrderByVersionNumberDesc(31L)).thenReturn(List.of(latest, legacy));
        when(runRepository.findBySubmissionIdOrderByVersionNumberDesc(31L)).thenReturn(List.of(run));
        when(correctionRepository.findBySubmissionIdIn(List.of(31L))).thenReturn(List.of(correction));

        var evidence = service.getEvidence(7L, 31L);

        assertThat(evidence.getSubmission().getId()).isEqualTo(31L);
        assertThat(evidence.getAnalysisVersions()).hasSize(2);
        assertThat(evidence.getAnalysisVersions().get(0).isOfficialVersion()).isTrue();
        assertThat(evidence.getAnalysisVersions().get(0).getAnalysis().getHeadline()).isEqualTo("保存时的判断");
        assertThat(evidence.getAnalysisVersions().get(0).getEvidence().get("sourceCode").asText()).isEqualTo("line:3");
        assertThat(evidence.getAnalysisVersions().get(1).getAnalysis()).isNull();
        assertThat(evidence.getCorrections()).singleElement()
                .extracting(item -> item.getFeedbackRevisionId())
                .isEqualTo(102L);
    }
}
