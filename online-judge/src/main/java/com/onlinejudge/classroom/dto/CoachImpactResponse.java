package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CoachImpactResponse {
    private Long coachedSubmissionId;
    private Long followupSubmissionId;
    private Long problemId;
    private String status;
    private String statusLabel;
    private String summary;
    private String previousVerdict;
    private String followupVerdict;
    private String previousIssueTag;
    private String previousFineGrainedTag;
    private String followupIssueTag;
    private String followupFineGrainedTag;
    private LocalDateTime answeredAt;
    private LocalDateTime followupSubmittedAt;
}
