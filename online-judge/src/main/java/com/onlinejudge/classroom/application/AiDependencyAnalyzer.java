package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import com.onlinejudge.submission.domain.Submission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AiDependencyAnalyzer {

    public static final String STATUS_NO_SIGNAL = "NO_SIGNAL";
    public static final String STATUS_INDEPENDENT_PROGRESS = "INDEPENDENT_PROGRESS";
    public static final String STATUS_SCAFFOLD_EFFECTIVE = "SCAFFOLD_EFFECTIVE";
    public static final String STATUS_SCAFFOLD_DENSE = "SCAFFOLD_DENSE";
    public static final String STATUS_DEPENDENCY_RISK = "DEPENDENCY_RISK";
    public static final String STATUS_TEACHER_FADE_REVIEW = "TEACHER_FADE_REVIEW";

    private static final int RECENT_SUBMISSION_WINDOW = 20;
    private static final int RECENT_PROMPT_WINDOW = 16;
    private static final int RECENT_EVENT_WINDOW = 40;

    public StudentAbilityProfileResponse.AiDependencySignal analyze(List<Submission> submissions,
                                                                    List<CoachPrompt> prompts,
                                                                    List<StudentRecommendationEvent> events) {
        List<Submission> recentSubmissions = safeSubmissions(submissions);
        List<CoachPrompt> recentPrompts = safePrompts(prompts);
        List<StudentRecommendationEvent> recentEvents = safeEvents(events);
        Set<Long> scaffoldedSubmissionIds = scaffoldedSubmissionIds(recentEvents);
        long coachPromptCount = recentPrompts.size();
        long answeredCoachCount = recentPrompts.stream()
                .filter(prompt -> hasText(prompt.getStudentAnswer()))
                .count();
        long recommendationClickCount = recentEvents.stream()
                .filter(event -> StudentRecommendationEventService.EVENT_CLICKED.equals(event.getEventType())
                        || StudentRecommendationEventService.EVENT_ENTERED_PROBLEM.equals(event.getEventType()))
                .count();
        long recommendationSubmissionCount = recentEvents.stream()
                .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                .count();
        long independentSubmissionCount = recentSubmissions.stream()
                .filter(submission -> submission.getId() != null && !scaffoldedSubmissionIds.contains(submission.getId()))
                .count();
        long independentAcceptedCount = recentSubmissions.stream()
                .filter(submission -> submission.getId() != null && !scaffoldedSubmissionIds.contains(submission.getId()))
                .filter(this::accepted)
                .count();
        long scaffoldedAcceptedCount = recentSubmissions.stream()
                .filter(submission -> submission.getId() != null && scaffoldedSubmissionIds.contains(submission.getId()))
                .filter(this::accepted)
                .count();
        long scaffoldUseCount = coachPromptCount + recommendationClickCount + recommendationSubmissionCount;
        long failedScaffoldedSubmissions = recentEvents.stream()
                .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                .filter(event -> event.getFollowupVerdict() != null)
                .filter(event -> !Submission.Verdict.ACCEPTED.name().equals(event.getFollowupVerdict()))
                .count();
        double independenceScore = independenceScore(
                recentSubmissions.size(),
                independentSubmissionCount,
                independentAcceptedCount,
                scaffoldedAcceptedCount,
                scaffoldUseCount,
                failedScaffoldedSubmissions);
        String status = resolveStatus(
                recentSubmissions.size(),
                coachPromptCount,
                recommendationClickCount,
                recommendationSubmissionCount,
                independentSubmissionCount,
                independentAcceptedCount,
                scaffoldedAcceptedCount,
                failedScaffoldedSubmissions,
                independenceScore);
        return StudentAbilityProfileResponse.AiDependencySignal.builder()
                .status(status)
                .label(label(status))
                .summary(summary(status, independentSubmissionCount, independentAcceptedCount, scaffoldUseCount, failedScaffoldedSubmissions))
                .independenceScore(independenceScore)
                .coachPromptCount(coachPromptCount)
                .answeredCoachCount(answeredCoachCount)
                .recommendationClickCount(recommendationClickCount)
                .recommendationSubmissionCount(recommendationSubmissionCount)
                .independentSubmissionCount(independentSubmissionCount)
                .independentAcceptedCount(independentAcceptedCount)
                .scaffoldedAcceptedCount(scaffoldedAcceptedCount)
                .dependencyEvidenceRefs(evidenceRefs(status, recentPrompts, recentEvents, recentSubmissions))
                .recommendedAction(recommendedAction(status))
                .needsTeacherAttention(STATUS_TEACHER_FADE_REVIEW.equals(status))
                .build();
    }

    public boolean isRisk(StudentAbilityProfileResponse.AiDependencySignal signal) {
        if (signal == null) {
            return false;
        }
        return STATUS_SCAFFOLD_DENSE.equals(signal.getStatus())
                || STATUS_DEPENDENCY_RISK.equals(signal.getStatus())
                || STATUS_TEACHER_FADE_REVIEW.equals(signal.getStatus());
    }

    public boolean needsIndependentAttempt(StudentAbilityProfileResponse.AiDependencySignal signal) {
        if (signal == null) {
            return false;
        }
        return STATUS_SCAFFOLD_DENSE.equals(signal.getStatus()) || STATUS_DEPENDENCY_RISK.equals(signal.getStatus());
    }

    private List<Submission> safeSubmissions(List<Submission> submissions) {
        return submissions == null ? List.of() : submissions.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(RECENT_SUBMISSION_WINDOW)
                .toList();
    }

    private List<CoachPrompt> safePrompts(List<CoachPrompt> prompts) {
        return prompts == null ? List.of() : prompts.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CoachPrompt::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(RECENT_PROMPT_WINDOW)
                .toList();
    }

    private List<StudentRecommendationEvent> safeEvents(List<StudentRecommendationEvent> events) {
        return events == null ? List.of() : events.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(RECENT_EVENT_WINDOW)
                .toList();
    }

    private Set<Long> scaffoldedSubmissionIds(List<StudentRecommendationEvent> events) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        events.stream()
                .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                .map(StudentRecommendationEvent::getFollowupSubmissionId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        return ids;
    }

    private boolean accepted(Submission submission) {
        return submission != null && submission.getVerdict() == Submission.Verdict.ACCEPTED;
    }

    private double independenceScore(long submissionCount,
                                     long independentSubmissionCount,
                                     long independentAcceptedCount,
                                     long scaffoldedAcceptedCount,
                                     long scaffoldUseCount,
                                     long failedScaffoldedSubmissions) {
        if (submissionCount == 0 && scaffoldUseCount == 0) {
            return 0.0;
        }
        double base = submissionCount == 0 ? 0.15 : independentSubmissionCount * 0.65 / Math.max(1, submissionCount);
        double independentAcceptedBonus = Math.min(0.25, independentAcceptedCount * 0.12);
        double scaffoldSuccessBonus = Math.min(0.12, scaffoldedAcceptedCount * 0.05);
        double scaffoldPenalty = Math.min(0.42, scaffoldUseCount * 0.035);
        double failedScaffoldPenalty = Math.min(0.28, failedScaffoldedSubmissions * 0.08);
        double score = base + independentAcceptedBonus + scaffoldSuccessBonus - scaffoldPenalty - failedScaffoldPenalty;
        score = Math.max(0.0, Math.min(1.0, score));
        return Math.round(score * 100.0) / 100.0;
    }

    private String resolveStatus(long submissionCount,
                                 long coachPromptCount,
                                 long recommendationClickCount,
                                 long recommendationSubmissionCount,
                                 long independentSubmissionCount,
                                 long independentAcceptedCount,
                                 long scaffoldedAcceptedCount,
                                 long failedScaffoldedSubmissions,
                                 double independenceScore) {
        long scaffoldUseCount = coachPromptCount + recommendationClickCount + recommendationSubmissionCount;
        if (submissionCount == 0 && scaffoldUseCount == 0) {
            return STATUS_NO_SIGNAL;
        }
        if (scaffoldUseCount >= 8 && independentSubmissionCount == 0 && failedScaffoldedSubmissions >= 2) {
            return STATUS_TEACHER_FADE_REVIEW;
        }
        if (scaffoldUseCount >= 5 && failedScaffoldedSubmissions >= 2 && independenceScore < 0.35) {
            return STATUS_DEPENDENCY_RISK;
        }
        if (scaffoldUseCount >= 4 && independentSubmissionCount <= 1 && scaffoldedAcceptedCount == 0) {
            return STATUS_SCAFFOLD_DENSE;
        }
        if (scaffoldedAcceptedCount > 0 && independentSubmissionCount > 0) {
            return STATUS_SCAFFOLD_EFFECTIVE;
        }
        if (independentAcceptedCount > 0 || independenceScore >= 0.55 && independentSubmissionCount >= 2) {
            return STATUS_INDEPENDENT_PROGRESS;
        }
        if (scaffoldUseCount >= 3 && independentSubmissionCount == 0) {
            return STATUS_SCAFFOLD_DENSE;
        }
        return STATUS_NO_SIGNAL;
    }

    private String label(String status) {
        return switch (status) {
            case STATUS_INDEPENDENT_PROGRESS -> "独立推进";
            case STATUS_SCAFFOLD_EFFECTIVE -> "支架有效";
            case STATUS_SCAFFOLD_DENSE -> "支架过密";
            case STATUS_DEPENDENCY_RISK -> "依赖风险";
            case STATUS_TEACHER_FADE_REVIEW -> "需撤支架复盘";
            default -> "证据不足";
        };
    }

    private String summary(String status,
                           long independentSubmissionCount,
                           long independentAcceptedCount,
                           long scaffoldUseCount,
                           long failedScaffoldedSubmissions) {
        return switch (status) {
            case STATUS_INDEPENDENT_PROGRESS -> "近期有 " + independentSubmissionCount
                    + " 次独立提交，其中 " + independentAcceptedCount + " 次通过或形成推进证据。";
            case STATUS_SCAFFOLD_EFFECTIVE -> "AI 支架后出现推进，同时保留了独立提交证据，可以逐步减少提示密度。";
            case STATUS_SCAFFOLD_DENSE -> "近期 AI 支架使用较密集，但独立提交证据不足，先要求一次不新增提示的最小尝试。";
            case STATUS_DEPENDENCY_RISK -> "近期多次依赖 AI 支架后仍未改善，存在把提示当成下一步指令的风险。";
            case STATUS_TEACHER_FADE_REVIEW -> "长期高密度使用 AI 支架且缺少独立推进，建议教师示范如何撤支架。";
            default -> scaffoldUseCount == 0
                    ? "还没有足够证据判断 AI 支架依赖度。"
                    : "已有少量 AI 支架证据，继续观察是否能转成独立提交。";
        };
    }

    private String recommendedAction(String status) {
        return switch (status) {
            case STATUS_INDEPENDENT_PROGRESS -> "保持当前节奏，下一题先独立写出最小样例再看提示。";
            case STATUS_SCAFFOLD_EFFECTIVE -> "逐步减少新提示，要求学生先复述证据和下一次验证点。";
            case STATUS_SCAFFOLD_DENSE -> "先暂停新提示，完成一次不新增 Coach/推荐的最小独立尝试。";
            case STATUS_DEPENDENCY_RISK -> "先做独立尝试复盘：写出输入、预期输出和一个修改假设，再决定是否继续追问。";
            case STATUS_TEACHER_FADE_REVIEW -> "请教师示范如何拆提示、设定独立尝试边界，并限制下一轮提示密度。";
            default -> "继续收集提交、Coach 和推荐事件，暂不调整支架剂量。";
        };
    }

    private List<String> evidenceRefs(String status,
                                      List<CoachPrompt> prompts,
                                      List<StudentRecommendationEvent> events,
                                      List<Submission> submissions) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        refs.add("ai_dependency:" + status);
        prompts.stream()
                .map(CoachPrompt::getId)
                .filter(Objects::nonNull)
                .limit(2)
                .map(id -> "coach_prompt:" + id)
                .forEach(refs::add);
        events.stream()
                .filter(event -> event.getId() != null || event.getRecommendationToken() != null)
                .limit(2)
                .map(event -> event.getId() == null
                        ? "recommendation:" + event.getRecommendationToken()
                        : "recommendation_event:" + event.getId())
                .forEach(refs::add);
        submissions.stream()
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .limit(2)
                .map(id -> "submission:" + id)
                .forEach(refs::add);
        return refs.stream().toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
