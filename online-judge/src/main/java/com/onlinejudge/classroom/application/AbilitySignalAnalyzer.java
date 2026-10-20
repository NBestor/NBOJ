package com.onlinejudge.classroom.application;

import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AbilitySignalAnalyzer {

    private static final int RECENT_WINDOW = 12;

    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;

    public List<AbilitySignal> summarize(List<Submission> submissions,
                                         Map<Long, SubmissionAnalysis> analyses) {
        List<AbilitySignal> failedSignals = summarizeInternal(submissions, analyses, true);
        return failedSignals.isEmpty() ? summarizeInternal(submissions, analyses, false) : failedSignals;
    }

    public String primaryAbilityFocus(List<Submission> submissions,
                                      Map<Long, SubmissionAnalysis> analyses) {
        return summarize(submissions, analyses).stream()
                .findFirst()
                .map(AbilitySignal::getAbilityPoint)
                .orElse(null);
    }

    public String buildCrossProblemSummary(List<Submission> submissions,
                                           Map<Long, SubmissionAnalysis> analyses) {
        if (submissions == null || submissions.isEmpty()) {
            return "还没有提交，暂不形成能力摘要。";
        }
        List<AbilitySignal> failedSignals = summarizeInternal(submissions, analyses, true);
        if (failedSignals.isEmpty()) {
            List<AbilitySignal> allSignals = summarizeInternal(submissions, analyses, false);
            if (allSignals.isEmpty()) {
                return "当前证据还不足，继续完成提交后再判断能力变化。";
            }
            return "本轮已进入通过后复盘，重点巩固：" + allSignals.get(0).getAbilityPoint() + "。";
        }

        AbilitySignal top = failedSignals.get(0);
        if (top.getTaskCount() >= 2) {
            return "同一作业内多题集中在：" + top.getAbilityPoint() + "。建议先复盘这些题共同的判断步骤。";
        }
        if (top.getSubmissionCount() >= 2) {
            return "最近多次提交主要集中在：" + top.getAbilityPoint() + "。建议先用一个最小样例验证这个能力点。";
        }
        return "当前主要关注：" + top.getAbilityPoint() + "。";
    }

    public boolean hasCrossProblemGap(List<Submission> submissions,
                                      Map<Long, SubmissionAnalysis> analyses) {
        return summarizeInternal(submissions, analyses, true).stream()
                .findFirst()
                .map(signal -> signal.getTaskCount() >= 2)
                .orElse(false);
    }

    private List<AbilitySignal> summarizeInternal(List<Submission> submissions,
                                                  Map<Long, SubmissionAnalysis> analyses,
                                                  boolean failedOnly) {
        if (submissions == null || submissions.isEmpty() || analyses == null || analyses.isEmpty()) {
            return List.of();
        }
        Map<String, AbilityAccumulator> accumulators = new LinkedHashMap<>();
        submissions.stream()
                .filter(Objects::nonNull)
                .filter(submission -> !failedOnly || submission.getVerdict() != Submission.Verdict.ACCEPTED)
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(RECENT_WINDOW)
                .forEach(submission -> collectSubmissionSignals(submission, analyses.get(submission.getId()), accumulators));

        return accumulators.values().stream()
                .map(AbilityAccumulator::toSignal)
                .sorted(Comparator.comparing(AbilitySignal::getTaskCount).reversed()
                        .thenComparing(AbilitySignal::getSubmissionCount, Comparator.reverseOrder())
                        .thenComparing(AbilitySignal::getAbilityPoint))
                .limit(5)
                .toList();
    }

    private void collectSubmissionSignals(Submission submission,
                                          SubmissionAnalysis analysis,
                                          Map<String, AbilityAccumulator> accumulators) {
        if (analysis == null) {
            return;
        }
        List<String> fineTags = diagnosisReportReader.fineGrainedTags(analysis);
        List<String> tags = fineTags.isEmpty() ? diagnosisReportReader.issueTags(analysis) : fineTags;
        for (String tagId : tags) {
            DiagnosisTaxonomy.DiagnosisTag tag = diagnosisTaxonomy.get(tagId);
            if (tag == null || tag.getAbilityPoint() == null || tag.getAbilityPoint().isBlank()) {
                continue;
            }
            AbilityAccumulator accumulator = accumulators.computeIfAbsent(
                    tag.getAbilityPoint(),
                    AbilityAccumulator::new
            );
            accumulator.evidenceTags.add(tag.getId());
            if (submission.getProblemId() != null) {
                accumulator.problemIds.add(submission.getProblemId());
            }
            if (submission.getId() != null) {
                accumulator.submissionIds.add(submission.getId());
            }
        }
    }

    private static class AbilityAccumulator {
        private final String abilityPoint;
        private final Set<Long> problemIds = new LinkedHashSet<>();
        private final Set<Long> submissionIds = new LinkedHashSet<>();
        private final Set<String> evidenceTags = new LinkedHashSet<>();

        private AbilityAccumulator(String abilityPoint) {
            this.abilityPoint = abilityPoint;
        }

        private AbilitySignal toSignal() {
            long taskCount = problemIds.isEmpty() ? submissionIds.size() : problemIds.size();
            return AbilitySignal.builder()
                    .abilityPoint(abilityPoint)
                    .taskCount(taskCount)
                    .submissionCount(submissionIds.size())
                    .evidenceTags(evidenceTags.stream().limit(4).toList())
                    .build();
        }
    }

    @Data
    @Builder
    public static class AbilitySignal {
        private String abilityPoint;
        private long taskCount;
        private long submissionCount;
        private List<String> evidenceTags;
    }
}
