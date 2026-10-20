package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MasteryGrowthAnalyzer {

    public static final String STATUS_NO_SIGNAL = "NO_SIGNAL";
    public static final String STATUS_GROWING = "GROWING";
    public static final String STATUS_TRANSFER_CONFIRMED = "TRANSFER_CONFIRMED";
    public static final String STATUS_PLATEAU = "PLATEAU";
    public static final String STATUS_REGRESSION = "REGRESSION";
    public static final String STATUS_SPIRAL_REVIEW_NEEDED = "SPIRAL_REVIEW_NEEDED";

    private static final int RECENT_WINDOW = 16;
    private static final int MIN_SIGNAL_SUBMISSIONS = 2;

    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;
    private final AbilitySignalAnalyzer abilitySignalAnalyzer;

    public StudentAbilityProfileResponse.MasteryGrowthSignal analyze(List<Submission> submissions,
                                                                     Map<Long, SubmissionAnalysis> analyses) {
        List<Submission> recent = recent(submissions);
        Map<Long, SubmissionAnalysis> safeAnalyses = analyses == null ? Map.of() : analyses;
        if (recent.size() < MIN_SIGNAL_SUBMISSIONS) {
            return buildNoSignal(recent.size());
        }

        List<Submission> chronological = recent.stream()
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .toList();
        long acceptedCount = recent.stream()
                .filter(this::isAccepted)
                .count();
        long failedCount = recent.size() - acceptedCount;
        long acceptedProblems = recent.stream()
                .filter(this::isAccepted)
                .map(Submission::getProblemId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long regressionCount = countRegressions(chronological);
        long leadingFailedCount = recent.stream()
                .takeWhile(submission -> !isAccepted(submission))
                .count();
        List<AbilitySignalAnalyzer.AbilitySignal> failedAbilitySignals =
                abilitySignalAnalyzer.summarize(
                        recent.stream().filter(submission -> !isAccepted(submission)).toList(),
                        safeAnalyses
                );
        AbilitySignalAnalyzer.AbilitySignal topFailedAbility = failedAbilitySignals.stream().findFirst().orElse(null);
        TagFocus topTagFocus = topTagFocus(recent, safeAnalyses, false);
        TagFocus acceptedTagFocus = topTagFocus(recent, safeAnalyses, true);
        long plateauCount = Math.max(leadingFailedCount, topFailedAbility == null ? 0 : topFailedAbility.getSubmissionCount());
        long crossProblemEvidenceCount = Math.max(
                acceptedProblems,
                topFailedAbility == null ? 0 : topFailedAbility.getTaskCount()
        );

        String status = resolveStatus(
                recent,
                chronological,
                acceptedCount,
                failedCount,
                acceptedProblems,
                regressionCount,
                leadingFailedCount,
                topFailedAbility,
                topTagFocus
        );
        String focusAbility = focusAbility(status, topFailedAbility, topTagFocus, acceptedTagFocus, recent, safeAnalyses);
        String focusTag = focusTag(status, topTagFocus, acceptedTagFocus);
        String fineTag = fineTag(status, topTagFocus, acceptedTagFocus);
        double growthScore = growthScore(status, acceptedCount, failedCount, acceptedProblems, regressionCount, plateauCount);
        List<String> evidenceRefs = evidenceRefs(status, recent, safeAnalyses, focusAbility, focusTag, fineTag);

        return StudentAbilityProfileResponse.MasteryGrowthSignal.builder()
                .status(status)
                .label(label(status))
                .summary(summary(status, focusAbility, acceptedCount, failedCount, acceptedProblems, regressionCount, plateauCount))
                .growthScore(growthScore)
                .focusAbility(focusAbility)
                .focusTag(focusTag)
                .fineGrainedTag(fineTag)
                .recentSubmissionCount(recent.size())
                .recentAcceptedCount(acceptedCount)
                .recentFailedCount(failedCount)
                .crossProblemEvidenceCount(crossProblemEvidenceCount)
                .regressionCount(regressionCount)
                .plateauCount(plateauCount)
                .evidenceRefs(evidenceRefs)
                .recommendedAction(recommendedAction(status, focusAbility))
                .needsTeacherAttention(STATUS_SPIRAL_REVIEW_NEEDED.equals(status) || STATUS_REGRESSION.equals(status))
                .build();
    }

    public boolean isRisk(StudentAbilityProfileResponse.MasteryGrowthSignal signal) {
        if (signal == null || signal.getStatus() == null) {
            return false;
        }
        return switch (signal.getStatus()) {
            case STATUS_PLATEAU, STATUS_REGRESSION, STATUS_SPIRAL_REVIEW_NEEDED -> true;
            default -> false;
        };
    }

    public boolean isPositive(StudentAbilityProfileResponse.MasteryGrowthSignal signal) {
        if (signal == null || signal.getStatus() == null) {
            return false;
        }
        return STATUS_GROWING.equals(signal.getStatus()) || STATUS_TRANSFER_CONFIRMED.equals(signal.getStatus());
    }

    private List<Submission> recent(List<Submission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        return submissions.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(RECENT_WINDOW)
                .toList();
    }

    private StudentAbilityProfileResponse.MasteryGrowthSignal buildNoSignal(long submissionCount) {
        return StudentAbilityProfileResponse.MasteryGrowthSignal.builder()
                .status(STATUS_NO_SIGNAL)
                .label(label(STATUS_NO_SIGNAL))
                .summary("提交证据还不足，暂不能判断长期能力成长。")
                .growthScore(0.5)
                .recentSubmissionCount(submissionCount)
                .recentAcceptedCount(0)
                .recentFailedCount(submissionCount)
                .crossProblemEvidenceCount(0)
                .regressionCount(0)
                .plateauCount(0)
                .evidenceRefs(List.of())
                .recommendedAction("继续完成一次可诊断提交后再判断成长趋势。")
                .needsTeacherAttention(false)
                .build();
    }

    private String resolveStatus(List<Submission> recent,
                                 List<Submission> chronological,
                                 long acceptedCount,
                                 long failedCount,
                                 long acceptedProblems,
                                 long regressionCount,
                                 long leadingFailedCount,
                                 AbilitySignalAnalyzer.AbilitySignal topFailedAbility,
                                 TagFocus topTagFocus) {
        long failedProblemsForTop = topFailedAbility == null ? 0 : topFailedAbility.getTaskCount();
        long failedSubmissionsForTop = topFailedAbility == null ? 0 : topFailedAbility.getSubmissionCount();
        if (failedProblemsForTop >= 2 && failedSubmissionsForTop >= 3) {
            return STATUS_SPIRAL_REVIEW_NEEDED;
        }
        if (topTagFocus != null && topTagFocus.problemIds().size() >= 2 && topTagFocus.submissionIds().size() >= 3) {
            return STATUS_SPIRAL_REVIEW_NEEDED;
        }
        if (regressionCount >= 2 || regressionCount > 0 && leadingFailedCount >= 2) {
            return STATUS_REGRESSION;
        }
        if (leadingFailedCount >= 3 || acceptedCount == 0 && failedCount >= 3 || failedSubmissionsForTop >= 3) {
            return STATUS_PLATEAU;
        }
        if (acceptedProblems >= 2) {
            return STATUS_TRANSFER_CONFIRMED;
        }
        if (acceptedCount > 0 && hasFailureBeforeAccepted(chronological)) {
            return STATUS_GROWING;
        }
        Submission latest = recent.get(0);
        if (isAccepted(latest) && acceptedCount > 0) {
            return STATUS_GROWING;
        }
        return STATUS_NO_SIGNAL;
    }

    private boolean hasFailureBeforeAccepted(List<Submission> chronological) {
        boolean failedSeen = false;
        for (Submission submission : chronological) {
            if (isAccepted(submission)) {
                if (failedSeen) {
                    return true;
                }
            } else {
                failedSeen = true;
            }
        }
        return false;
    }

    private long countRegressions(List<Submission> chronological) {
        boolean acceptedSeen = false;
        long count = 0;
        for (Submission submission : chronological) {
            if (isAccepted(submission)) {
                acceptedSeen = true;
            } else if (acceptedSeen) {
                count++;
            }
        }
        return count;
    }

    private boolean isAccepted(Submission submission) {
        return submission != null && submission.getVerdict() == Submission.Verdict.ACCEPTED;
    }

    private TagFocus topTagFocus(List<Submission> submissions,
                                 Map<Long, SubmissionAnalysis> analyses,
                                 boolean acceptedOnly) {
        Map<String, TagAccumulator> accumulators = new LinkedHashMap<>();
        for (Submission submission : submissions) {
            if (acceptedOnly != isAccepted(submission)) {
                continue;
            }
            SubmissionAnalysis analysis = analyses.get(submission.getId());
            if (analysis == null) {
                continue;
            }
            List<String> fineTags = diagnosisReportReader.fineGrainedTags(analysis);
            List<String> tags = fineTags.isEmpty() ? diagnosisReportReader.issueTags(analysis) : fineTags;
            for (String tag : tags) {
                DiagnosisTaxonomy.DiagnosisTag diagnosisTag = diagnosisTaxonomy.get(tag);
                String normalizedTag = diagnosisTag == null ? tag : diagnosisTag.getId();
                TagAccumulator accumulator = accumulators.computeIfAbsent(
                        normalizedTag,
                        key -> new TagAccumulator(
                                key,
                                diagnosisTag == null ? null : diagnosisTag.getAbilityPoint(),
                                diagnosisTag != null && diagnosisTag.isFineGrained()
                        )
                );
                if (submission.getId() != null) {
                    accumulator.submissionIds.add(submission.getId());
                }
                if (submission.getProblemId() != null) {
                    accumulator.problemIds.add(submission.getProblemId());
                }
            }
        }
        return accumulators.values()
                .stream()
                .map(TagAccumulator::toFocus)
                .sorted(Comparator
                        .comparingInt((TagFocus focus) -> focus.problemIds().size()).reversed()
                        .thenComparingInt(focus -> focus.submissionIds().size()).reversed()
                        .thenComparing(TagFocus::tag))
                .findFirst()
                .orElse(null);
    }

    private String focusAbility(String status,
                                AbilitySignalAnalyzer.AbilitySignal topFailedAbility,
                                TagFocus topFailedTag,
                                TagFocus acceptedTag,
                                List<Submission> recent,
                                Map<Long, SubmissionAnalysis> analyses) {
        if (STATUS_TRANSFER_CONFIRMED.equals(status) && acceptedTag != null && notBlank(acceptedTag.abilityPoint())) {
            return acceptedTag.abilityPoint();
        }
        if (topFailedAbility != null && notBlank(topFailedAbility.getAbilityPoint())) {
            return topFailedAbility.getAbilityPoint();
        }
        if (topFailedTag != null && notBlank(topFailedTag.abilityPoint())) {
            return topFailedTag.abilityPoint();
        }
        return abilitySignalAnalyzer.primaryAbilityFocus(recent, analyses);
    }

    private String focusTag(String status, TagFocus failedTag, TagFocus acceptedTag) {
        TagFocus focus = STATUS_TRANSFER_CONFIRMED.equals(status) && acceptedTag != null ? acceptedTag : failedTag;
        return focus == null || focus.fineGrained() ? null : focus.tag();
    }

    private String fineTag(String status, TagFocus failedTag, TagFocus acceptedTag) {
        TagFocus focus = STATUS_TRANSFER_CONFIRMED.equals(status) && acceptedTag != null ? acceptedTag : failedTag;
        return focus != null && focus.fineGrained() ? focus.tag() : null;
    }

    private double growthScore(String status,
                               long acceptedCount,
                               long failedCount,
                               long acceptedProblems,
                               long regressionCount,
                               long plateauCount) {
        double total = Math.max(1, acceptedCount + failedCount);
        double score = acceptedCount * 0.55 / total + Math.min(0.25, acceptedProblems * 0.1);
        score -= Math.min(0.35, regressionCount * 0.12);
        score -= Math.min(0.35, plateauCount * 0.08);
        score += switch (status) {
            case STATUS_TRANSFER_CONFIRMED -> 0.25;
            case STATUS_GROWING -> 0.23;
            case STATUS_NO_SIGNAL -> 0.0;
            default -> -0.08;
        };
        score = Math.max(0.0, Math.min(1.0, score));
        return Math.round(score * 100.0) / 100.0;
    }

    private List<String> evidenceRefs(String status,
                                      List<Submission> recent,
                                      Map<Long, SubmissionAnalysis> analyses,
                                      String focusAbility,
                                      String focusTag,
                                      String fineTag) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        refs.add("mastery_growth:" + status);
        if (notBlank(fineTag)) {
            refs.add("fine_tag:" + fineTag);
        }
        if (notBlank(focusTag)) {
            refs.add("tag:" + focusTag);
        }
        if (notBlank(focusAbility)) {
            refs.add("ability:" + focusAbility);
        }
        recent.stream()
                .limit(4)
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .map(id -> "submission:" + id)
                .forEach(refs::add);
        recent.stream()
                .map(submission -> analyses.get(submission.getId()))
                .filter(Objects::nonNull)
                .map(SubmissionAnalysis::getSubmissionId)
                .filter(Objects::nonNull)
                .map(id -> "analysis:" + id)
                .limit(3)
                .forEach(refs::add);
        return refs.stream().limit(8).toList();
    }

    private String label(String status) {
        return switch (status) {
            case STATUS_GROWING -> "正在增长";
            case STATUS_TRANSFER_CONFIRMED -> "迁移已验证";
            case STATUS_PLATEAU -> "成长停滞";
            case STATUS_REGRESSION -> "近期回退";
            case STATUS_SPIRAL_REVIEW_NEEDED -> "需螺旋复习";
            default -> "成长待观察";
        };
    }

    private String summary(String status,
                           String focusAbility,
                           long acceptedCount,
                           long failedCount,
                           long acceptedProblems,
                           long regressionCount,
                           long plateauCount) {
        String ability = notBlank(focusAbility) ? focusAbility : "当前能力点";
        return switch (status) {
            case STATUS_TRANSFER_CONFIRMED -> "近期已在 " + acceptedProblems + " 个题目形成通过证据，" + ability + " 的迁移掌握正在稳定。";
            case STATUS_GROWING -> "近期出现从失败推进到通过的证据，可以继续用同能力新题确认掌握是否稳定。";
            case STATUS_PLATEAU -> "近期有 " + failedCount + " 次失败且停滞证据较多，建议先围绕 " + ability + " 做最小样例复盘。";
            case STATUS_REGRESSION -> "近期出现 " + regressionCount + " 次通过后回退，需要对比上次通过与当前失败的差异。";
            case STATUS_SPIRAL_REVIEW_NEEDED -> "同一能力点或细分错因跨题反复出现，建议围绕 " + ability + " 安排螺旋复习。";
            default -> "提交证据还不足，继续观察下一次提交是否形成清晰成长趋势。";
        };
    }

    private String recommendedAction(String status, String focusAbility) {
        String ability = notBlank(focusAbility) ? focusAbility : "当前能力点";
        return switch (status) {
            case STATUS_TRANSFER_CONFIRMED -> "安排一道同能力稍高阶题，验证迁移是否稳定。";
            case STATUS_GROWING -> "补一条通过前后的变化解释，再做一次同能力迁移验证。";
            case STATUS_PLATEAU -> "先写一个最小失败样例，说明 " + ability + " 的判断步骤，再只改一个假设。";
            case STATUS_REGRESSION -> "对比最近一次通过提交与当前失败提交，只保留一个差异点重新验证。";
            case STATUS_SPIRAL_REVIEW_NEEDED -> "做一次螺旋复习：把两道证据题放在一起复盘共同失败条件，必要时请教师示范一轮。";
            default -> "继续完成一次可诊断提交后再判断成长趋势。";
        };
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static class TagAccumulator {
        private final String tag;
        private final String abilityPoint;
        private final boolean fineGrained;
        private final Set<Long> submissionIds = new LinkedHashSet<>();
        private final Set<Long> problemIds = new LinkedHashSet<>();

        private TagAccumulator(String tag, String abilityPoint, boolean fineGrained) {
            this.tag = tag;
            this.abilityPoint = abilityPoint;
            this.fineGrained = fineGrained;
        }

        private TagFocus toFocus() {
            return new TagFocus(tag, abilityPoint, fineGrained, List.copyOf(submissionIds), List.copyOf(problemIds));
        }
    }

    private record TagFocus(String tag,
                            String abilityPoint,
                            boolean fineGrained,
                            List<Long> submissionIds,
                            List<Long> problemIds) {
    }
}
