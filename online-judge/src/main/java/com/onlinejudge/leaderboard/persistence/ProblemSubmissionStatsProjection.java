package com.onlinejudge.leaderboard.persistence;

import java.time.LocalDateTime;

public interface ProblemSubmissionStatsProjection {
    Long getProblemId();
    Long getTotalSubmissions();
    Long getAcceptedSubmissions();
    Double getBestAcceptedTime();
    LocalDateTime getLastSubmittedAt();
}

