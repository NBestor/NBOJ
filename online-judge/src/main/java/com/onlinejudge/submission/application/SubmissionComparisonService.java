package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.dto.SubmissionComparisonResponse;
import com.onlinejudge.submission.dto.SubmissionResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubmissionComparisonService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionAnalysisService submissionAnalysisService;

    public SubmissionComparisonResponse compare(Long leftId, Long rightId) {
        Submission leftSubmission = submissionRepository.findById(leftId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + leftId));
        Submission rightSubmission = submissionRepository.findById(rightId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + rightId));

        if (!leftSubmission.getProblemId().equals(rightSubmission.getProblemId())) {
            throw new IllegalArgumentException("只能对比同一道题目的两次提交");
        }

        Problem problem = problemRepository.findById(leftSubmission.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + leftSubmission.getProblemId()));

        SubmissionResponse baseline = submissionAnalysisService.getDetailedSubmission(leftId);
        SubmissionResponse target = submissionAnalysisService.getDetailedSubmission(rightId);

        DiffBundle diffBundle = buildDiff(
                splitLines(leftSubmission.getSourceCode()),
                splitLines(rightSubmission.getSourceCode())
        );

        List<String> causeChanges = buildCauseChanges(baseline, target, diffBundle.stats);
        String progressSummary = buildProgressSummary(baseline, target, diffBundle.stats);

        return SubmissionComparisonResponse.builder()
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .baseline(buildSnapshot(baseline))
                .target(buildSnapshot(target))
                .progressSummary(progressSummary)
                .causeChanges(causeChanges)
                .diffStats(diffBundle.stats)
                .diffLines(diffBundle.lines)
                .build();
    }

    private SubmissionComparisonResponse.SubmissionSnapshot buildSnapshot(SubmissionResponse submission) {
        return SubmissionComparisonResponse.SubmissionSnapshot.builder()
                .submissionId(submission.getId())
                .languageName(submission.getLanguageName())
                .verdict(submissionAnalysisService.formatVerdict(submission.getVerdict()))
                .submittedAt(submission.getSubmittedAt())
                .analysisSummary(submission.getAnalysis() == null ? "" : submission.getAnalysis().getSummary())
                .build();
    }

    private List<String> buildCauseChanges(SubmissionResponse baseline,
                                           SubmissionResponse target,
                                           SubmissionComparisonResponse.DiffStats diffStats) {
        List<String> changes = new ArrayList<>();
        if (baseline.getVerdict() != target.getVerdict()) {
            changes.add("评测结果从“" + submissionAnalysisService.formatVerdict(baseline.getVerdict())
                    + "”变化为“" + submissionAnalysisService.formatVerdict(target.getVerdict()) + "”。");
        }

        SubmissionAnalysisResponse.FailedCaseSnapshot baselineFailed = baseline.getAnalysis() == null
                ? null
                : baseline.getAnalysis().getFirstFailedCase();
        SubmissionAnalysisResponse.FailedCaseSnapshot targetFailed = target.getAnalysis() == null
                ? null
                : target.getAnalysis().getFirstFailedCase();

        if (baselineFailed != null && targetFailed == null) {
            changes.add("原先的首个失败测试点已经被消除。");
        } else if (baselineFailed == null && targetFailed != null) {
            changes.add("目标提交出现了新的失败测试点 #" + targetFailed.getTestCaseNumber() + "。");
        } else if (baselineFailed != null && targetFailed != null
                && !baselineFailed.getTestCaseNumber().equals(targetFailed.getTestCaseNumber())) {
            changes.add("首个失败测试点从 #" + baselineFailed.getTestCaseNumber()
                    + " 变化为 #" + targetFailed.getTestCaseNumber() + "。");
        }

        String baselineSummary = baseline.getAnalysis() == null ? "" : baseline.getAnalysis().getSummary();
        String targetSummary = target.getAnalysis() == null ? "" : target.getAnalysis().getSummary();
        if (!baselineSummary.equals(targetSummary) && !targetSummary.isBlank()) {
            changes.add("错因分析发生变化，新的重点是：" + targetSummary);
        }

        changes.add("代码层面新增 " + diffStats.getAddedLines()
                + " 行，删除 " + diffStats.getRemovedLines()
                + " 行，保留 " + diffStats.getUnchangedLines() + " 行。");
        return changes;
    }

    private String buildProgressSummary(SubmissionResponse baseline,
                                        SubmissionResponse target,
                                        SubmissionComparisonResponse.DiffStats diffStats) {
        StringBuilder summary = new StringBuilder();
        summary.append("从“")
                .append(submissionAnalysisService.formatVerdict(baseline.getVerdict()))
                .append("”到“")
                .append(submissionAnalysisService.formatVerdict(target.getVerdict()))
                .append("”");

        if (target.getVerdict() == Submission.Verdict.ACCEPTED) {
            summary.append("，本次修改已经解决关键问题。");
        } else {
            summary.append("，说明问题定位在推进，但仍需继续修正。");
        }

        summary.append(" 本次共调整 ")
                .append(diffStats.getAddedLines() + diffStats.getRemovedLines())
                .append(" 行代码。");
        return summary.toString();
    }

    private DiffBundle buildDiff(List<String> leftLines, List<String> rightLines) {
        int leftSize = leftLines.size();
        int rightSize = rightLines.size();
        int[][] lcs = new int[leftSize + 1][rightSize + 1];

        for (int leftIndex = leftSize - 1; leftIndex >= 0; leftIndex--) {
            for (int rightIndex = rightSize - 1; rightIndex >= 0; rightIndex--) {
                if (leftLines.get(leftIndex).equals(rightLines.get(rightIndex))) {
                    lcs[leftIndex][rightIndex] = lcs[leftIndex + 1][rightIndex + 1] + 1;
                } else {
                    lcs[leftIndex][rightIndex] = Math.max(lcs[leftIndex + 1][rightIndex], lcs[leftIndex][rightIndex + 1]);
                }
            }
        }

        List<SubmissionComparisonResponse.DiffLine> diffLines = new ArrayList<>();
        int leftIndex = 0;
        int rightIndex = 0;
        int added = 0;
        int removed = 0;
        int unchanged = 0;

        while (leftIndex < leftSize && rightIndex < rightSize) {
            if (leftLines.get(leftIndex).equals(rightLines.get(rightIndex))) {
                unchanged++;
                diffLines.add(diffLine("same", leftIndex + 1, rightIndex + 1, leftLines.get(leftIndex)));
                leftIndex++;
                rightIndex++;
                continue;
            }

            if (lcs[leftIndex + 1][rightIndex] >= lcs[leftIndex][rightIndex + 1]) {
                removed++;
                diffLines.add(diffLine("remove", leftIndex + 1, null, leftLines.get(leftIndex)));
                leftIndex++;
            } else {
                added++;
                diffLines.add(diffLine("add", null, rightIndex + 1, rightLines.get(rightIndex)));
                rightIndex++;
            }
        }

        while (leftIndex < leftSize) {
            removed++;
            diffLines.add(diffLine("remove", leftIndex + 1, null, leftLines.get(leftIndex)));
            leftIndex++;
        }

        while (rightIndex < rightSize) {
            added++;
            diffLines.add(diffLine("add", null, rightIndex + 1, rightLines.get(rightIndex)));
            rightIndex++;
        }

        return new DiffBundle(
                diffLines,
                SubmissionComparisonResponse.DiffStats.builder()
                        .addedLines(added)
                        .removedLines(removed)
                        .unchangedLines(unchanged)
                        .build()
        );
    }

    private SubmissionComparisonResponse.DiffLine diffLine(String type,
                                                           Integer leftLineNumber,
                                                           Integer rightLineNumber,
                                                           String content) {
        return SubmissionComparisonResponse.DiffLine.builder()
                .type(type)
                .leftLineNumber(leftLineNumber)
                .rightLineNumber(rightLineNumber)
                .content(content)
                .build();
    }

    private List<String> splitLines(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return List.of();
        }
        return List.of(sourceCode.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1));
    }

    private record DiffBundle(List<SubmissionComparisonResponse.DiffLine> lines,
                              SubmissionComparisonResponse.DiffStats stats) {
    }
}

