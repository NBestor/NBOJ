package com.onlinejudge.leaderboard.dto;

import com.onlinejudge.problem.domain.Problem;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LeaderboardEntryResponse {
    private Integer rank;
    private Long problemId;
    private String problemTitle;
    private Problem.Difficulty difficulty;
    private Integer totalSubmissions;
    private Integer acceptedSubmissions;
    private Double acceptanceRate;
    private Double bestAcceptedTime;
    private LocalDateTime lastSubmittedAt;
}

