package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LearningInterventionImpactAnalyzer {

    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;

    public Map<Long, StudentTrajectoryResponse.LearningInterventionImpact> summarizeByInterventionSubmission(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses) {
        if (submissions == null || submissions.isEmpty() || analyses == null || analyses.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Submission>> submissionsByProblem = submissions.stream()
                .filter(submission -> submission.getProblemId() != null)
                .collect(Collectors.groupingBy(
                        Submission::getProblemId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), values -> values.stream()
                                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                                .toList())
                ));
        Map<Long, Submission> submissionById = submissions.stream()
                .filter(submission -> submission.getId() != null)
                .collect(Collectors.toMap(Submission::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        LinkedHashMap<Long, StudentTrajectoryResponse.LearningInterventionImpact> impacts = new LinkedHashMap<>();
        analyses.forEach((submissionId, analysis) -> {
            DiagnosisReportReader.LearningInterventionPlanSnapshot plan =
                    diagnosisReportReader.learningInterventionPlan(analysis);
            if (plan == null || !hasText(plan.interventionType())) {
                return;
            }
            Submission interventionSubmission = submissionById.get(submissionId);
            if (interventionSubmission == null) {
                return;
            }
            Submission followup = findFollowupSubmission(
                    interventionSubmission,
                    submissionsByProblem.get(interventionSubmission.getProblemId())
            );
            impacts.put(submissionId, buildImpact(
                    interventionSubmission,
                    analysis,
                    plan,
                    followup,
                    followup == null ? null : analyses.get(followup.getId())
            ));
        });
        return impacts;
    }

    public StudentTrajectoryResponse.LearningInterventionImpact latestForOrderedSubmissions(
            List<Long> orderedSubmissionIds,
            Map<Long, StudentTrajectoryResponse.LearningInterventionImpact> impacts) {
        if (orderedSubmissionIds == null || impacts == null || impacts.isEmpty()) {
            return null;
        }
        return orderedSubmissionIds.stream()
                .map(impacts::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Submission findFollowupSubmission(Submission interventionSubmission, List<Submission> sameProblemSubmissions) {
        if (interventionSubmission == null || sameProblemSubmissions == null || sameProblemSubmissions.isEmpty()) {
            return null;
        }
        return sameProblemSubmissions.stream()
                .filter(submission -> !Objects.equals(submission.getId(), interventionSubmission.getId()))
                .filter(submission -> isAfter(submission.getSubmittedAt(), interventionSubmission.getSubmittedAt()))
                .findFirst()
                .orElse(null);
    }

    private StudentTrajectoryResponse.LearningInterventionImpact buildImpact(
            Submission interventionSubmission,
            SubmissionAnalysis interventionAnalysis,
            DiagnosisReportReader.LearningInterventionPlanSnapshot plan,
            Submission followup,
            SubmissionAnalysis followupAnalysis) {
        String previousIssue = first(diagnosisReportReader.issueTags(interventionAnalysis));
        String previousFine = first(diagnosisReportReader.fineGrainedTags(interventionAnalysis));
        String followupIssue = first(diagnosisReportReader.issueTags(followupAnalysis));
        String followupFine = first(diagnosisReportReader.fineGrainedTags(followupAnalysis));
        String status = resolveStatus(interventionSubmission, previousIssue, previousFine, followup, followupIssue, followupFine);
        return StudentTrajectoryResponse.LearningInterventionImpact.builder()
                .interventionSubmissionId(interventionSubmission == null ? null : interventionSubmission.getId())
                .followupSubmissionId(followup == null ? null : followup.getId())
                .problemId(interventionSubmission == null ? null : interventionSubmission.getProblemId())
                .interventionType(plan.interventionType())
                .status(status)
                .statusLabel(statusLabel(status))
                .summary(summary(status, plan.interventionType(), previousIssue, previousFine, followupIssue, followupFine))
                .previousVerdict(verdict(interventionSubmission))
                .followupVerdict(verdict(followup))
                .previousIssueTag(previousIssue)
                .previousFineGrainedTag(previousFine)
                .followupIssueTag(followupIssue)
                .followupFineGrainedTag(followupFine)
                .plannedAt(interventionSubmission == null ? null : interventionSubmission.getSubmittedAt())
                .followupSubmittedAt(followup == null ? null : followup.getSubmittedAt())
                .build();
    }

    private String resolveStatus(Submission interventionSubmission,
                                 String previousIssue,
                                 String previousFine,
                                 Submission followup,
                                 String followupIssue,
                                 String followupFine) {
        if (followup == null) {
            return "AWAITING_FOLLOWUP";
        }
        if (followup.getVerdict() == Submission.Verdict.ACCEPTED) {
            return "FOLLOWUP_ACCEPTED";
        }
        if (hasText(previousFine) && !Objects.equals(previousFine, followupFine)) {
            return "ISSUE_SHIFTED";
        }
        if (hasText(previousIssue) && !Objects.equals(previousIssue, followupIssue)) {
            return "ISSUE_SHIFTED";
        }
        if ((hasText(previousFine) && Objects.equals(previousFine, followupFine))
                || (hasText(previousIssue) && Objects.equals(previousIssue, followupIssue))) {
            return "SAME_ISSUE";
        }
        if (!Objects.equals(verdict(interventionSubmission), verdict(followup))) {
            return "VERDICT_CHANGED";
        }
        return "NO_CLEAR_CHANGE";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "FOLLOWUP_ACCEPTED" -> "干预后通过";
            case "ISSUE_SHIFTED" -> "干预后错因转移";
            case "SAME_ISSUE" -> "干预后仍卡同类问题";
            case "VERDICT_CHANGED" -> "干预后评测阶段变化";
            case "NO_CLEAR_CHANGE" -> "干预后暂未见明确变化";
            default -> "等待干预后提交";
        };
    }

    private String summary(String status,
                           String interventionType,
                           String previousIssue,
                           String previousFine,
                           String followupIssue,
                           String followupFine) {
        String label = interventionType == null || interventionType.isBlank() ? "学习干预" : interventionType;
        return switch (status) {
            case "FOLLOWUP_ACCEPTED" -> "学生收到“" + label + "”后，同题下一次提交已通过。这是观察性改善信号，仍需结合学生是否真正完成干预任务判断。";
            case "ISSUE_SHIFTED" -> "学生收到“" + label + "”后，后续错因从“" + tagLabel(firstNonBlank(previousFine, previousIssue))
                    + "”变化为“" + tagLabel(firstNonBlank(followupFine, followupIssue)) + "”，说明问题可能进入新阶段。";
            case "SAME_ISSUE" -> "学生收到“" + label + "”后，后续仍命中“" + tagLabel(firstNonBlank(previousFine, previousIssue))
                    + "”，下一轮需要更小样例、教师介入或降低提示粒度。";
            case "VERDICT_CHANGED" -> "学生收到“" + label + "”后，评测阶段发生变化；建议结合新诊断判断这是进步、回退还是暴露了新问题。";
            case "NO_CLEAR_CHANGE" -> "学生收到“" + label + "”后已有同题提交，但暂未观察到明确错因变化。";
            default -> "学生已收到“" + label + "”，但还没有同题后续提交，暂时不能评估干预效果。";
        };
    }

    private boolean isAfter(LocalDateTime candidate, LocalDateTime anchor) {
        if (candidate == null) {
            return false;
        }
        return anchor == null || candidate.isAfter(anchor);
    }

    private String verdict(Submission submission) {
        return submission == null || submission.getVerdict() == null ? null : submission.getVerdict().name();
    }

    private String tagLabel(String tag) {
        return tag == null || tag.isBlank() ? "待观察" : diagnosisTaxonomy.label(tag);
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
