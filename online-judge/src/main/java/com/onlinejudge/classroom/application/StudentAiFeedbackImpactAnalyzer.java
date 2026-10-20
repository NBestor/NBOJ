package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
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
public class StudentAiFeedbackImpactAnalyzer {

    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;

    public Map<Long, StudentTrajectoryResponse.AiFeedbackImpact> summarizeByFeedbackSubmission(
            List<Submission> submissions,
            Map<Long, SubmissionAnalysis> analyses,
            List<StudentAiFeedbackEvent> events) {
        if (submissions == null || submissions.isEmpty() || events == null || events.isEmpty()) {
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

        LinkedHashMap<Long, StudentTrajectoryResponse.AiFeedbackImpact> impacts = new LinkedHashMap<>();
        events.stream()
                .filter(event -> StudentAiFeedbackEvent.EVENT_VIEWED.equals(event.getEventType()))
                .sorted(Comparator.comparing(StudentAiFeedbackEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .forEach(event -> {
                    if (event.getSubmissionId() == null || impacts.containsKey(event.getSubmissionId())) {
                        return;
                    }
                    Submission feedbackSubmission = submissionById.get(event.getSubmissionId());
                    if (feedbackSubmission == null) {
                        return;
                    }
                    Submission followup = findFollowupSubmission(
                            feedbackSubmission,
                            submissionsByProblem.get(feedbackSubmission.getProblemId()),
                            event.getCreatedAt()
                    );
                    impacts.put(event.getSubmissionId(), buildImpact(
                            event,
                            feedbackSubmission,
                            analyses == null ? null : analyses.get(feedbackSubmission.getId()),
                            followup,
                            followup == null || analyses == null ? null : analyses.get(followup.getId())
                    ));
                });
        return impacts;
    }

    public StudentTrajectoryResponse.AiFeedbackImpact latestForOrderedSubmissions(
            List<Long> orderedSubmissionIds,
            Map<Long, StudentTrajectoryResponse.AiFeedbackImpact> impacts) {
        if (orderedSubmissionIds == null || impacts == null || impacts.isEmpty()) {
            return null;
        }
        return orderedSubmissionIds.stream()
                .map(impacts::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Submission findFollowupSubmission(Submission feedbackSubmission,
                                              List<Submission> sameProblemSubmissions,
                                              LocalDateTime viewedAt) {
        if (feedbackSubmission == null || sameProblemSubmissions == null || sameProblemSubmissions.isEmpty()) {
            return null;
        }
        LocalDateTime anchor = viewedAt == null ? feedbackSubmission.getSubmittedAt() : viewedAt;
        return sameProblemSubmissions.stream()
                .filter(submission -> !Objects.equals(submission.getId(), feedbackSubmission.getId()))
                .filter(submission -> isAfter(submission.getSubmittedAt(), anchor))
                .findFirst()
                .orElse(null);
    }

    private StudentTrajectoryResponse.AiFeedbackImpact buildImpact(StudentAiFeedbackEvent event,
                                                                   Submission feedbackSubmission,
                                                                   SubmissionAnalysis feedbackAnalysis,
                                                                   Submission followup,
                                                                   SubmissionAnalysis followupAnalysis) {
        String beforeIssue = first(diagnosisReportReader.issueTags(feedbackAnalysis));
        String beforeFine = first(diagnosisReportReader.fineGrainedTags(feedbackAnalysis));
        String afterIssue = first(diagnosisReportReader.issueTags(followupAnalysis));
        String afterFine = first(diagnosisReportReader.fineGrainedTags(followupAnalysis));
        String status = resolveStatus(feedbackSubmission, beforeIssue, beforeFine, followup, afterIssue, afterFine);
        return StudentTrajectoryResponse.AiFeedbackImpact.builder()
                .feedbackSubmissionId(feedbackSubmission.getId())
                .followupSubmissionId(followup == null ? null : followup.getId())
                .problemId(feedbackSubmission.getProblemId())
                .status(status)
                .statusLabel(statusLabel(status))
                .summary(summary(status, beforeIssue, beforeFine, afterIssue, afterFine))
                .feedbackStatus(event.getFeedbackStatus())
                .feedbackViewedAt(event.getCreatedAt())
                .previousVerdict(verdict(feedbackSubmission))
                .followupVerdict(verdict(followup))
                .previousIssueTag(beforeIssue)
                .previousFineGrainedTag(beforeFine)
                .followupIssueTag(afterIssue)
                .followupFineGrainedTag(afterFine)
                .evidenceRefs(evidenceRefs(event, feedbackSubmission, followup, status))
                .needsTeacherAttention("SAME_ISSUE_AFTER_AI".equals(status) || "REGRESSED_AFTER_AI".equals(status))
                .build();
    }

    private String resolveStatus(Submission feedbackSubmission,
                                 String beforeIssue,
                                 String beforeFine,
                                 Submission followup,
                                 String afterIssue,
                                 String afterFine) {
        if (followup == null) {
            return "AWAITING_FOLLOWUP";
        }
        if (followup.getVerdict() == Submission.Verdict.ACCEPTED) {
            return "IMPROVED_AFTER_AI";
        }
        if (isRegression(feedbackSubmission, followup)) {
            return "REGRESSED_AFTER_AI";
        }
        if (hasText(beforeFine) && Objects.equals(beforeFine, afterFine)
                || hasText(beforeIssue) && Objects.equals(beforeIssue, afterIssue)) {
            return "SAME_ISSUE_AFTER_AI";
        }
        if (hasText(beforeFine) && !Objects.equals(beforeFine, afterFine)
                || hasText(beforeIssue) && !Objects.equals(beforeIssue, afterIssue)) {
            return "SHIFTED_AFTER_AI";
        }
        if (!Objects.equals(verdict(feedbackSubmission), verdict(followup))) {
            return "VERDICT_CHANGED_AFTER_AI";
        }
        return "NO_CLEAR_CHANGE_AFTER_AI";
    }

    private boolean isRegression(Submission before, Submission after) {
        if (before == null || after == null || before.getVerdict() == null || after.getVerdict() == null) {
            return false;
        }
        return rank(after.getVerdict()) < rank(before.getVerdict());
    }

    private int rank(Submission.Verdict verdict) {
        return switch (verdict) {
            case ACCEPTED -> 5;
            case WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED -> 4;
            case RUNTIME_ERROR -> 3;
            case COMPILATION_ERROR -> 2;
            case INTERNAL_ERROR, PENDING -> 1;
        };
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "IMPROVED_AFTER_AI" -> "查看建议后改善";
            case "SHIFTED_AFTER_AI" -> "查看建议后错因转移";
            case "SAME_ISSUE_AFTER_AI" -> "查看建议后仍卡同类问题";
            case "REGRESSED_AFTER_AI" -> "查看建议后出现回退";
            case "VERDICT_CHANGED_AFTER_AI" -> "查看建议后评测阶段变化";
            case "NO_CLEAR_CHANGE_AFTER_AI" -> "查看建议后暂未见明确变化";
            default -> "等待查看建议后的提交";
        };
    }

    private String summary(String status,
                           String beforeIssue,
                           String beforeFine,
                           String afterIssue,
                           String afterFine) {
        return switch (status) {
            case "IMPROVED_AFTER_AI" -> "学生查看建议后，同题下一次提交已通过；这是观察到改善的相关证据，但不能单独证明由建议造成。";
            case "SHIFTED_AFTER_AI" -> "学生查看 AI 反馈后，错因从“" + tagLabel(firstNonBlank(beforeFine, beforeIssue))
                    + "”变化为“" + tagLabel(firstNonBlank(afterFine, afterIssue)) + "”，说明问题可能进入新阶段。";
            case "SAME_ISSUE_AFTER_AI" -> "学生查看 AI 反馈后，后续仍命中“" + tagLabel(firstNonBlank(beforeFine, beforeIssue))
                    + "”，建议教师降低任务粒度或要求学生先手推最小样例。";
            case "REGRESSED_AFTER_AI" -> "学生查看 AI 反馈后，后续评测阶段回退，需要检查是否误读提示或改动范围过大。";
            case "VERDICT_CHANGED_AFTER_AI" -> "学生查看 AI 反馈后，评测阶段发生变化；需要结合新失败点判断是推进还是暴露新问题。";
            case "NO_CLEAR_CHANGE_AFTER_AI" -> "学生查看 AI 反馈后已有同题提交，但目前没有明确改善证据。";
            default -> "学生已经查看 AI 反馈，但还没有同题后续提交，暂不能判断提示效果。";
        };
    }

    private List<String> evidenceRefs(StudentAiFeedbackEvent event,
                                      Submission feedbackSubmission,
                                      Submission followup,
                                      String status) {
        List<String> refs = new java.util.ArrayList<>();
        refs.add("student_ai_feedback:" + status);
        if (event.getId() != null) {
            refs.add("student_ai_feedback_event:" + event.getId());
        }
        refs.add("feedback_submission:" + feedbackSubmission.getId());
        if (followup != null && followup.getId() != null) {
            refs.add("followup_submission:" + followup.getId());
        }
        return refs;
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
