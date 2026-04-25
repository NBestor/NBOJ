package com.onlinejudge.submission.persistence;

public interface SubmissionCaseResultStatsProjection {
    Long getSubmissionId();
    Long getPassedTestCases();
    Long getTotalTestCases();
}
