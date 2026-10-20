package com.onlinejudge.leaderboard.application;

import com.onlinejudge.leaderboard.dto.LeaderboardEntryResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.application.ProblemAccessPolicy;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.identity.application.CurrentTeacherContext;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final CurrentTeacherContext currentTeacherContext;
    private final ProblemAccessPolicy problemAccessPolicy;

    public List<LeaderboardEntryResponse> getProblemLeaderboard() {
        UUID teacherId = currentTeacherContext.requireTeacherId();
        List<Problem> problems = problemRepository.findAllByOrderByIdAsc().stream()
                .filter(problem -> problemAccessPolicy.isTeacherVisible(teacherId, problem))
                .toList();
        List<Long> assignmentIds = assignmentRepository.findByOwnerTeacherIdOrderByCreatedAtDesc(teacherId)
                .stream()
                .map(assignment -> assignment.getId())
                .toList();
        List<Submission> tenantSubmissions = assignmentIds.isEmpty()
                ? List.of()
                : submissionRepository.findByAssignmentIdIn(assignmentIds);
        Map<Long, TenantProblemStats> statsByProblem = tenantSubmissions
                .stream()
                .collect(Collectors.groupingBy(Submission::getProblemId))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> TenantProblemStats.from(entry.getValue())));

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

    private LeaderboardEntryResponse buildEntry(Problem problem, TenantProblemStats stats) {
        int totalSubmissions = stats == null ? 0 : stats.totalSubmissions();
        int acceptedSubmissions = stats == null ? 0 : stats.acceptedSubmissions();
        double acceptanceRate = totalSubmissions == 0 ? 0.0 : (acceptedSubmissions * 100.0) / totalSubmissions;

        return LeaderboardEntryResponse.builder()
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .difficulty(problem.getDifficulty())
                .totalSubmissions(totalSubmissions)
                .acceptedSubmissions(acceptedSubmissions)
                .acceptanceRate(acceptanceRate)
                .bestAcceptedTime(stats == null ? null : stats.bestAcceptedTime())
                .lastSubmittedAt(stats == null ? null : stats.lastSubmittedAt())
                .build();
    }

    private record TenantProblemStats(int totalSubmissions, int acceptedSubmissions,
                                      Double bestAcceptedTime, LocalDateTime lastSubmittedAt) {
        private static TenantProblemStats from(List<Submission> submissions) {
            int accepted = (int) submissions.stream()
                    .filter(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)
                    .count();
            Double bestTime = submissions.stream()
                    .filter(submission -> submission.getVerdict() == Submission.Verdict.ACCEPTED)
                    .map(Submission::getExecutionTime)
                    .filter(java.util.Objects::nonNull)
                    .min(Double::compareTo)
                    .orElse(null);
            LocalDateTime lastSubmittedAt = submissions.stream()
                    .map(Submission::getSubmittedAt)
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            return new TenantProblemStats(submissions.size(), accepted, bestTime, lastSubmittedAt);
        }
    }
}

