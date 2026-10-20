package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
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
public class LearningActionEvidenceAnalyzer {

    private final DiagnosisReportReader diagnosisReportReader;

    public Map<Long, StudentTrajectoryResponse.LearningActionEvidence> summarizeByInterventionSubmission(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            Map<Long, StudentTrajectoryResponse.LearningInterventionImpact> impacts) {
        if (submissions == null || submissions.isEmpty() || analyses == null || analyses.isEmpty()) {
            return Map.of();
        }
        Map<Long, Submission> submissionById = submissions.stream()
                .filter(submission -> submission.getId() != null)
                .collect(Collectors.toMap(Submission::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, List<Submission>> submissionsByProblem = submissions.stream()
                .filter(submission -> submission.getProblemId() != null)
                .collect(Collectors.groupingBy(
                        Submission::getProblemId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), values -> values.stream()
                                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                                .toList())
                ));

        LinkedHashMap<Long, StudentTrajectoryResponse.LearningActionEvidence> evidenceBySubmission = new LinkedHashMap<>();
        analyses.forEach((submissionId, analysis) -> {
            DiagnosisReportReader.LearningInterventionPlanSnapshot plan =
                    diagnosisReportReader.learningInterventionPlan(analysis);
            if (plan == null || !hasText(plan.interventionType())) {
                return;
            }
            Submission interventionSubmission = submissionById.get(submissionId);
            Submission followup = findFollowupSubmission(
                    interventionSubmission,
                    interventionSubmission == null ? null : submissionsByProblem.get(interventionSubmission.getProblemId())
            );
            StudentTrajectoryResponse.LearningInterventionImpact impact = impacts == null ? null : impacts.get(submissionId);
            evidenceBySubmission.put(submissionId, inferEvidence(
                    plan,
                    interventionSubmission,
                    followup,
                    followup == null ? null : analyses.get(followup.getId()),
                    impact
            ));
        });
        return evidenceBySubmission;
    }

    public StudentTrajectoryResponse.LearningActionEvidence latestForOrderedSubmissions(
            List<Long> orderedSubmissionIds,
            Map<Long, StudentTrajectoryResponse.LearningActionEvidence> actionEvidence) {
        if (orderedSubmissionIds == null || actionEvidence == null || actionEvidence.isEmpty()) {
            return null;
        }
        return orderedSubmissionIds.stream()
                .map(actionEvidence::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private StudentTrajectoryResponse.LearningActionEvidence inferEvidence(
            DiagnosisReportReader.LearningInterventionPlanSnapshot plan,
            Submission interventionSubmission,
            Submission followup,
            SubmissionAnalysis followupAnalysis,
            StudentTrajectoryResponse.LearningInterventionImpact impact) {
        String status = resolveStatus(plan, interventionSubmission, followup, followupAnalysis, impact);
        double confidence = confidence(status, followup, impact);
        return StudentTrajectoryResponse.LearningActionEvidence.builder()
                .expectedActionType(plan.interventionType())
                .executionStatus(status)
                .statusLabel(statusLabel(status))
                .observedEvidence(observedEvidence(status, plan, followup, impact))
                .confidence(confidence)
                .evidenceRefs(evidenceRefs(plan, followup, impact))
                .nextAdjustment(nextAdjustment(status, plan, impact))
                .build();
    }

    private String resolveStatus(DiagnosisReportReader.LearningInterventionPlanSnapshot plan,
                                 Submission interventionSubmission,
                                 Submission followup,
                                 SubmissionAnalysis followupAnalysis,
                                 StudentTrajectoryResponse.LearningInterventionImpact impact) {
        if (followup == null) {
            return "NOT_OBSERVED";
        }
        String impactStatus = impact == null ? "" : impact.getStatus();
        if ("SAME_ISSUE".equals(impactStatus)) {
            return "CONTRADICTED";
        }
        if ("FOLLOWUP_ACCEPTED".equals(impactStatus)) {
            return "OBSERVED";
        }
        if ("ISSUE_SHIFTED".equals(impactStatus) || "VERDICT_CHANGED".equals(impactStatus)) {
            return "PARTIALLY_OBSERVED";
        }
        if (followup.getVerdict() == Submission.Verdict.ACCEPTED) {
            return "OBSERVED";
        }
        if (!Objects.equals(verdict(interventionSubmission), verdict(followup))) {
            return "PARTIALLY_OBSERVED";
        }
        List<String> followupFineTags = diagnosisReportReader.fineGrainedTags(followupAnalysis);
        List<String> followupIssueTags = diagnosisReportReader.issueTags(followupAnalysis);
        if (followupFineTags.isEmpty() && followupIssueTags.isEmpty()) {
            return "PARTIALLY_OBSERVED";
        }
        return "CONTRADICTED";
    }

    private double confidence(String status, Submission followup, StudentTrajectoryResponse.LearningInterventionImpact impact) {
        if ("NOT_OBSERVED".equals(status)) {
            return 0.5;
        }
        if (impact != null && hasText(impact.getStatus())) {
            return switch (status) {
                case "OBSERVED" -> 0.82;
                case "PARTIALLY_OBSERVED" -> 0.68;
                case "CONTRADICTED" -> 0.74;
                default -> 0.5;
            };
        }
        return followup == null ? 0.5 : 0.6;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "OBSERVED" -> "已观察到执行迹象";
            case "PARTIALLY_OBSERVED" -> "部分观察到执行迹象";
            case "CONTRADICTED" -> "后续证据与学习动作相反";
            default -> "等待后续证据";
        };
    }

    private String observedEvidence(String status,
                                    DiagnosisReportReader.LearningInterventionPlanSnapshot plan,
                                    Submission followup,
                                    StudentTrajectoryResponse.LearningInterventionImpact impact) {
        String label = plan.interventionType();
        return switch (status) {
            case "OBSERVED" -> "学生收到“" + label + "”后，同题后续提交已通过或原错因消失。";
            case "PARTIALLY_OBSERVED" -> "学生收到“" + label + "”后，同题后续提交的 verdict 或错因发生变化，但还不能确认动作完整完成。";
            case "CONTRADICTED" -> "学生收到“" + label + "”后，同题后续仍停留在相同错因，说明可能没有真正完成该学习动作。";
            default -> "学生已收到“" + label + "”，但还没有同题后续提交。";
        };
    }

    private List<String> evidenceRefs(DiagnosisReportReader.LearningInterventionPlanSnapshot plan,
                                      Submission followup,
                                      StudentTrajectoryResponse.LearningInterventionImpact impact) {
        LinkedHashMap<String, Boolean> refs = new LinkedHashMap<>();
        if (plan.evidenceRefs() != null) {
            plan.evidenceRefs().stream().filter(this::hasText).forEach(ref -> refs.put(ref, true));
        }
        if (followup != null && followup.getId() != null) {
            refs.put("followup:submission:" + followup.getId(), true);
        }
        if (impact != null && hasText(impact.getStatus())) {
            refs.put("impact:" + impact.getStatus(), true);
        }
        return List.copyOf(refs.keySet());
    }

    private String nextAdjustment(String status,
                                  DiagnosisReportReader.LearningInterventionPlanSnapshot plan,
                                  StudentTrajectoryResponse.LearningInterventionImpact impact) {
        return switch (status) {
            case "OBSERVED" -> "保持当前提示粒度，下一轮转入复盘、迁移或更高层策略解释。";
            case "PARTIALLY_OBSERVED" -> "保留当前方向，但把学习动作缩小成一个更明确的可检查产出。";
            case "CONTRADICTED" -> "降低提示粒度，要求最小样例或教师介入检查学生是否真的完成动作。";
            default -> "等待一次同题后续提交；暂不根据结果评价这次学习动作。";
        };
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

    private boolean isAfter(LocalDateTime candidate, LocalDateTime anchor) {
        if (candidate == null) {
            return false;
        }
        return anchor == null || candidate.isAfter(anchor);
    }

    private String verdict(Submission submission) {
        return submission == null || submission.getVerdict() == null ? null : submission.getVerdict().name();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
