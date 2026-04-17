package com.onlinejudge.leaderboard.application;

import com.onlinejudge.leaderboard.dto.LeaderboardEntryResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.leaderboard.persistence.ProblemSubmissionStatsProjection;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;

    public List<LeaderboardEntryResponse> getProblemLeaderboard() {
        List<Problem> problems = problemRepository.findAllByOrderByIdAsc();
        Map<Long, ProblemSubmissionStatsProjection> statsByProblem = submissionRepository.summarizeByProblem()
                .stream()
                .collect(Collectors.toMap(ProblemSubmissionStatsProjection::getProblemId, stats -> stats));

        List<LeaderboardEntryResponse> entries = problems.stream()
                .map(problem -> buildEntry(problem, statsByProblem.get(problem.getId())))
                .sorted(Comparator
                        .comparing(LeaderboardEntryResponse::getAcceptedSubmissions, Comparator.reverseOrder())
                        .thenComparing(LeaderboardEntryResponse::getTotalSubmissions, Comparator.reverseOrder())
                        .thenComparing(LeaderboardEntryResponse::getBestAcceptedTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LeaderboardEntryResponse::getProblemId))
                .toList();

        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }

        return entries;
    }

    private LeaderboardEntryResponse buildEntry(Problem problem, ProblemSubmissionStatsProjection stats) {
        int totalSubmissions = stats == null ? 0 : toInt(stats.getTotalSubmissions());
        int acceptedSubmissions = stats == null ? 0 : toInt(stats.getAcceptedSubmissions());
        double acceptanceRate = totalSubmissions == 0 ? 0.0 : (acceptedSubmissions * 100.0) / totalSubmissions;

        return LeaderboardEntryResponse.builder()
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .difficulty(problem.getDifficulty())
                .totalSubmissions(totalSubmissions)
                .acceptedSubmissions(acceptedSubmissions)
                .acceptanceRate(acceptanceRate)
                .bestAcceptedTime(stats == null ? null : stats.getBestAcceptedTime())
                .lastSubmittedAt(stats == null ? null : stats.getLastSubmittedAt())
                .build();
    }

    private int toInt(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }
}

