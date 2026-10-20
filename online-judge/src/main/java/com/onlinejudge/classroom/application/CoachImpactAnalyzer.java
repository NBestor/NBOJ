package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.dto.CoachImpactResponse;
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
public class CoachImpactAnalyzer {

    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;

    public Map<Long, CoachImpactResponse> summarizeByCoachedSubmission(List<Submission> submissions,
                                                                       Map<Long, SubmissionAnalysis> analyses,
                                                                       List<CoachPrompt> prompts) {
        if (submissions == null || submissions.isEmpty() || prompts == null || prompts.isEmpty()) {
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
        Map<Long, CoachPrompt> answeredBySubmission = latestAnsweredPromptBySubmission(prompts);
        LinkedHashMap<Long, CoachImpactResponse> impacts = new LinkedHashMap<>();
        answeredBySubmission.forEach((submissionId, prompt) -> {
            Submission coached = submissionById.get(submissionId);
            if (coached == null) {
                return;
            }
            Submission followup = findFollowupSubmission(coached, prompt, submissionsByProblem.get(coached.getProblemId()));
            impacts.put(submissionId, buildImpact(coached, analyses.get(submissionId), prompt, followup, followup == null ? null : analyses.get(followup.getId())));
        });
        return impacts;
    }

    public CoachImpactResponse latestForOrderedSubmissions(List<Long> orderedSubmissionIds,
                                                           Map<Long, CoachImpactResponse> impacts) {
        if (orderedSubmissionIds == null || impacts == null || impacts.isEmpty()) {
            return null;
        }
        return orderedSubmissionIds.stream()
                .map(impacts::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Map<Long, CoachPrompt> latestAnsweredPromptBySubmission(List<CoachPrompt> prompts) {
        return prompts.stream()
                .filter(prompt -> prompt != null && prompt.getSubmissionId() != null)
                .filter(prompt -> hasText(prompt.getStudentAnswer()))
                .sorted(Comparator
                        .comparing(this::answeredAtOrCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(CoachPrompt::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toMap(
                        CoachPrompt::getSubmissionId,
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private Submission findFollowupSubmission(Submission coached, CoachPrompt prompt, List<Submission> sameProblemSubmissions) {
        if (coached == null || sameProblemSubmissions == null || sameProblemSubmissions.isEmpty()) {
            return null;
        }
        LocalDateTime anchor = laterOf(coached.getSubmittedAt(), answeredAtOrCreatedAt(prompt));
        return sameProblemSubmissions.stream()
                .filter(submission -> !Objects.equals(submission.getId(), coached.getId()))
                .filter(submission -> isAfter(submission.getSubmittedAt(), anchor))
                .findFirst()
                .orElse(null);
    }

    private CoachImpactResponse buildImpact(Submission coached,
                                            SubmissionAnalysis coachedAnalysis,
                                            CoachPrompt prompt,
                                            Submission followup,
                                            SubmissionAnalysis followupAnalysis) {
        String previousIssue = first(diagnosisReportReader.issueTags(coachedAnalysis));
        String previousFine = first(diagnosisReportReader.fineGrainedTags(coachedAnalysis));
        String followupIssue = first(diagnosisReportReader.issueTags(followupAnalysis));
        String followupFine = first(diagnosisReportReader.fineGrainedTags(followupAnalysis));
        String status = resolveStatus(coached, previousIssue, previousFine, followup, followupIssue, followupFine);
        return CoachImpactResponse.builder()
                .coachedSubmissionId(coached == null ? null : coached.getId())
                .followupSubmissionId(followup == null ? null : followup.getId())
                .problemId(coached == null ? null : coached.getProblemId())
                .status(status)
                .statusLabel(statusLabel(status))
                .summary(summary(status, previousIssue, previousFine, followupIssue, followupFine))
                .previousVerdict(verdict(coached))
                .followupVerdict(verdict(followup))
                .previousIssueTag(previousIssue)
                .previousFineGrainedTag(previousFine)
                .followupIssueTag(followupIssue)
                .followupFineGrainedTag(followupFine)
                .answeredAt(answeredAtOrCreatedAt(prompt))
                .followupSubmittedAt(followup == null ? null : followup.getSubmittedAt())
                .build();
    }

    private String resolveStatus(Submission coached,
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
        if (!Objects.equals(verdict(coached), verdict(followup))) {
            return "VERDICT_CHANGED";
        }
        return "NO_CLEAR_CHANGE";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "FOLLOWUP_ACCEPTED" -> "追问后通过";
            case "ISSUE_SHIFTED" -> "追问后错因变化";
            case "SAME_ISSUE" -> "追问后仍卡同类问题";
            case "VERDICT_CHANGED" -> "追问后评测阶段变化";
            case "NO_CLEAR_CHANGE" -> "追问后暂未明显变化";
            default -> "等待追问后提交";
        };
    }

    private String summary(String status,
                           String previousIssue,
                           String previousFine,
                           String followupIssue,
                           String followupFine) {
        return switch (status) {
            case "FOLLOWUP_ACCEPTED" -> "学生回答追问后的下一次同题提交已通过，说明这轮追问可能帮助其完成了关键修正。";
            case "ISSUE_SHIFTED" -> "学生回答追问后，下一次提交的错因从“" + tagLabel(firstNonBlank(previousFine, previousIssue))
                    + "”变化为“" + tagLabel(firstNonBlank(followupFine, followupIssue)) + "”，说明问题进入了新阶段。";
            case "SAME_ISSUE" -> "学生回答追问后仍命中“" + tagLabel(firstNonBlank(previousFine, previousIssue))
                    + "”，下一轮需要更小的样例或教师介入。";
            case "VERDICT_CHANGED" -> "学生回答追问后评测阶段发生变化，建议结合新诊断继续判断是否真的改善。";
            case "NO_CLEAR_CHANGE" -> "学生回答追问后已有同题提交，但暂未观察到明确错因变化。";
            default -> "学生已回答追问，但还没有同题后续提交，暂时不能判断追问是否带来改善。";
        };
    }

    private boolean isAfter(LocalDateTime candidate, LocalDateTime anchor) {
        if (candidate == null) {
            return false;
        }
        return anchor == null || candidate.isAfter(anchor);
    }

    private LocalDateTime laterOf(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private LocalDateTime answeredAtOrCreatedAt(CoachPrompt prompt) {
        if (prompt == null) {
            return null;
        }
        return prompt.getAnsweredAt() == null ? prompt.getCreatedAt() : prompt.getAnsweredAt();
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
