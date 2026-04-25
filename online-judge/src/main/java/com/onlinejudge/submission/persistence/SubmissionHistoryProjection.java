package com.onlinejudge.submission.persistence;

import com.onlinejudge.submission.domain.Submission;

import java.time.LocalDateTime;

public interface SubmissionHistoryProjection {
    Long getId();

    Long getProblemId();

    Integer getLanguageId();

    String getLanguageName();

    Submission.Verdict getVerdict();

    Double getExecutionTime();

    Integer getMemoryUsed();

    LocalDateTime getSubmittedAt();
}
