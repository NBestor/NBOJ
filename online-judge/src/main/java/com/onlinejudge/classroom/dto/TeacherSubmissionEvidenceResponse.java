package com.onlinejudge.classroom.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlinejudge.submission.dto.StudentAiFeedbackResponse;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.dto.SubmissionResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TeacherSubmissionEvidenceResponse {
    private SubmissionResponse submission;
    private List<AnalysisVersion> analysisVersions;
    private List<TeacherDiagnosisCorrectionResponse> corrections;

    @Data
    @Builder
    public static class AnalysisVersion {
        private Long id;
        private Integer versionNumber;
        private String generationKey;
        private String status;
        private String source;
        private boolean officialVersion;
        private Long diagnosisRunId;
        private Integer diagnosisRunVersion;
        private String provider;
        private String model;
        private String promptVersion;
        private String schemaVersion;
        private LocalDateTime generatedAt;
        private String failureReason;
        private SubmissionAnalysisResponse analysis;
        private StudentAiFeedbackResponse feedback;
        private JsonNode evidence;
    }
}
