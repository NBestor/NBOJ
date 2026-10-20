package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
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
public class RecurringMisconceptionAnalyzer {

    public static final String STATUS_NONE = "NONE";
    public static final String STATUS_WATCH = "WATCH";
    public static final String STATUS_RECURRING = "RECURRING";
    public static final String STATUS_ESCALATE = "ESCALATE";

    private static final int WINDOW = 40;

    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;

    public StudentAbilityProfileResponse.RecurringMisconceptionSignal analyze(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses) {
        List<Submission> safeSubmissions = submissions == null ? List.of() : submissions.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(WINDOW)
                .toList();
        if (safeSubmissions.isEmpty() || analyses == null || analyses.isEmpty()) {
            return signal(STATUS_NONE, null, null, null, 0, 0, 0, List.of(), List.of(), false);
        }
        Map<String, Accumulator> byTag = new LinkedHashMap<>();
        for (Submission submission : safeSubmissions) {
            if (submission.getVerdict() == Submission.Verdict.ACCEPTED) {
                continue;
            }
            SubmissionAnalysis analysis = analyses.get(submission.getId());
            if (analysis == null) {
                continue;
            }
            List<String> fineTags = diagnosisReportReader.fineGrainedTags(analysis);
            List<String> tags = fineTags.isEmpty() ? diagnosisReportReader.issueTags(analysis) : fineTags;
            for (String tag : tags) {
                String normalized = normalize(tag);
                if (normalized.isBlank() || "CODE_QUALITY".equals(normalized) || "GENERALIZATION_CHECK".equals(normalized)) {
                    continue;
                }
                byTag.computeIfAbsent(normalized, Accumulator::new).add(submission, primaryIssueTag(analysis), abilityPoint(normalized));
            }
        }
        Accumulator selected = byTag.values()
                .stream()
                .filter(item -> item.submissionIds.size() >= 2)
                .max(Comparator
                        .comparingInt((Accumulator item) -> statusRank(resolveStatus(item)))
                        .thenComparingLong(item -> item.problemIds.size())
                        .thenComparingLong(item -> item.assignmentIds.size())
                        .thenComparingLong(item -> item.submissionIds.size()))
                .orElse(null);
        if (selected == null) {
            return signal(STATUS_NONE, null, null, null, 0, 0, 0, List.of(), List.of(), false);
        }
        String status = resolveStatus(selected);
        return signal(
                status,
                selected.primaryIssueTag,
                selected.tag,
                selected.abilityPoint,
                selected.problemIds.size(),
                selected.assignmentIds.size(),
                selected.submissionIds.size(),
                selected.evidenceRefs(),
                selected.problemIds.stream().limit(5).toList(),
                STATUS_ESCALATE.equals(status)
        );
    }

    public boolean isActionable(StudentAbilityProfileResponse.RecurringMisconceptionSignal signal) {
        return signal != null && (STATUS_RECURRING.equals(signal.getStatus()) || STATUS_ESCALATE.equals(signal.getStatus()));
    }

    private String resolveStatus(Accumulator item) {
        boolean crossProblem = item.problemIds.size() >= 2;
        boolean crossAssignment = item.assignmentIds.size() >= 2;
        boolean latestStillFailed = item.latestVerdict != Submission.Verdict.ACCEPTED;
        if (crossProblem && crossAssignment) {
            return STATUS_ESCALATE;
        }
        if (crossProblem || crossAssignment) {
            return item.submissionIds.size() >= 4 && latestStillFailed ? STATUS_ESCALATE : STATUS_RECURRING;
        }
        if (item.submissionIds.size() >= 3) {
            return STATUS_WATCH;
        }
        return STATUS_NONE;
    }

    private StudentAbilityProfileResponse.RecurringMisconceptionSignal signal(String status,
                                                                              String issueTag,
                                                                              String fineTag,
                                                                              String abilityPoint,
                                                                              long problemCount,
                                                                              long assignmentCount,
                                                                              long submissionCount,
                                                                              List<String> evidenceRefs,
                                                                              List<Long> evidenceProblemIds,
                                                                              boolean needsTeacherAttention) {
        return StudentAbilityProfileResponse.RecurringMisconceptionSignal.builder()
                .status(status)
                .label(label(status))
                .summary(summary(status, fineTag, abilityPoint, problemCount, assignmentCount, submissionCount))
                .misconceptionTag(issueTag)
                .fineGrainedTag(fineTag)
                .abilityPoint(abilityPoint)
                .problemCount(problemCount)
                .assignmentCount(assignmentCount)
                .submissionCount(submissionCount)
                .evidenceRefs(evidenceRefs == null ? List.of() : evidenceRefs)
                .evidenceProblemIds(evidenceProblemIds == null ? List.of() : evidenceProblemIds)
                .recommendedAction(recommendedAction(status, fineTag, abilityPoint))
                .needsTeacherAttention(needsTeacherAttention)
                .build();
    }

    private String label(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_ESCALATE -> "复发需介入";
            case STATUS_RECURRING -> "跨题复发";
            case STATUS_WATCH -> "同题观察";
            default -> "暂无复发";
        };
    }

    private String summary(String status,
                           String fineTag,
                           String abilityPoint,
                           long problemCount,
                           long assignmentCount,
                           long submissionCount) {
        if (STATUS_NONE.equals(status)) {
            return "近期没有足够证据显示长期复发误区。";
        }
        String tagLabel = fineTag == null ? "同类误区" : diagnosisTaxonomy.label(fineTag);
        if (STATUS_ESCALATE.equals(status)) {
            return "证据显示「" + tagLabel + "」已在 " + problemCount + " 道题、" + assignmentCount
                    + " 个作业中反复出现，需要教师或更小复盘任务介入。";
        }
        if (STATUS_RECURRING.equals(status)) {
            return "证据显示「" + tagLabel + "」已跨题或跨作业重复出现，关联能力点："
                    + safeText(abilityPoint, "待判断") + "。";
        }
        return "同一题内已有 " + submissionCount + " 次命中「" + tagLabel + "」，先观察是否会迁移成跨题问题。";
    }

    private String recommendedAction(String status, String fineTag, String abilityPoint) {
        String focus = safeText(abilityPoint, fineTag == null ? "当前误区" : diagnosisTaxonomy.label(fineTag));
        return switch (status == null ? "" : status) {
            case STATUS_ESCALATE -> "先暂停加新题，由教师带学生对比两道证据题，写出 " + focus + " 的共同判断步骤。";
            case STATUS_RECURRING -> "安排一个小复盘：列出两道题共同的失败条件，再做一道同能力短题验证。";
            case STATUS_WATCH -> "先把本题失败样例缩到最小，观察下一题是否还命中同类误区。";
            default -> "继续收集提交证据。";
        };
    }

    private String primaryIssueTag(SubmissionAnalysis analysis) {
        List<String> issueTags = diagnosisReportReader.issueTags(analysis);
        return issueTags.isEmpty() ? null : issueTags.get(0);
    }

    private String abilityPoint(String tagId) {
        DiagnosisTaxonomy.DiagnosisTag tag = diagnosisTaxonomy.get(tagId);
        return tag == null ? null : tag.getAbilityPoint();
    }

    private int statusRank(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_ESCALATE -> 4;
            case STATUS_RECURRING -> 3;
            case STATUS_WATCH -> 2;
            default -> 1;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static class Accumulator {
        private final String tag;
        private String primaryIssueTag;
        private String abilityPoint;
        private Submission.Verdict latestVerdict;
        private final Set<Long> problemIds = new LinkedHashSet<>();
        private final Set<Long> assignmentIds = new LinkedHashSet<>();
        private final Set<Long> submissionIds = new LinkedHashSet<>();

        private Accumulator(String tag) {
            this.tag = tag;
        }

        private void add(Submission submission, String primaryIssueTag, String abilityPoint) {
            if (this.primaryIssueTag == null || this.primaryIssueTag.isBlank()) {
                this.primaryIssueTag = primaryIssueTag;
            }
            if (this.abilityPoint == null || this.abilityPoint.isBlank()) {
                this.abilityPoint = abilityPoint;
            }
            if (latestVerdict == null) {
                latestVerdict = submission.getVerdict();
            }
            if (submission.getProblemId() != null) {
                problemIds.add(submission.getProblemId());
            }
            if (submission.getAssignmentId() != null) {
                assignmentIds.add(submission.getAssignmentId());
            }
            if (submission.getId() != null) {
                submissionIds.add(submission.getId());
            }
        }

        private List<String> evidenceRefs() {
            return submissionIds.stream()
                    .limit(5)
                    .map(id -> "recurring-misconception:submission:" + id)
                    .toList();
        }
    }
}
