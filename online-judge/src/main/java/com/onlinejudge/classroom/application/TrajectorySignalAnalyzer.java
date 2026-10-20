package com.onlinejudge.classroom.application;

import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class TrajectorySignalAnalyzer {

    private static final double LARGE_CHANGE_RATIO = 0.45;

    public String detectLargeChangeSameIssue(List<Submission> submissions,
                                             Map<Long, SubmissionAnalysis> analyses) {
        if (submissions == null || submissions.size() < 3 || analyses == null || analyses.isEmpty()) {
            return "";
        }

        for (int index = 0; index + 2 < submissions.size(); index++) {
            Submission latest = submissions.get(index);
            Submission previous = submissions.get(index + 1);
            Submission beforePrevious = submissions.get(index + 2);
            if (latest == null || previous == null || beforePrevious == null) {
                continue;
            }
            if (isAccepted(latest) || isAccepted(previous) || isAccepted(beforePrevious)) {
                continue;
            }

            String latestIssue = primaryIssue(analyses.get(latest.getId()));
            String previousIssue = primaryIssue(analyses.get(previous.getId()));
            String beforePreviousIssue = primaryIssue(analyses.get(beforePrevious.getId()));
            if (latestIssue.isBlank() || !latestIssue.equals(previousIssue) || !latestIssue.equals(beforePreviousIssue)) {
                continue;
            }
            if (largeChange(previous, latest) && largeChange(beforePrevious, previous)) {
                return "最近多次大幅修改代码，但主要错因仍停留在“" + latestIssue + "”。建议先停止整体重写，改用一个最小样例验证单个假设。";
            }
        }
        return "";
    }

    private boolean isAccepted(Submission submission) {
        return submission.getVerdict() == Submission.Verdict.ACCEPTED;
    }

    private String primaryIssue(SubmissionAnalysis analysis) {
        if (analysis == null) {
            return "";
        }
        String scenario = safe(analysis.getScenario());
        if (!scenario.isBlank()) {
            return scenario;
        }
        return safe(analysis.getHeadline());
    }

    private boolean largeChange(Submission previous, Submission latest) {
        String left = normalizeCode(previous.getSourceCode());
        String right = normalizeCode(latest.getSourceCode());
        if (left.isBlank() || right.isBlank()) {
            return false;
        }
        int distance = levenshteinDistance(left, right);
        int baseline = Math.max(left.length(), right.length());
        return baseline > 0 && ((double) distance / baseline) >= LARGE_CHANGE_RATIO;
    }

    private String normalizeCode(String sourceCode) {
        return safe(sourceCode).replaceAll("\\s+", "");
    }

    private String safe(String value) {
        return Objects.toString(value, "").trim();
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
