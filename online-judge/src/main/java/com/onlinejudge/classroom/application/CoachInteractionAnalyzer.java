package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CoachInteractionAnalyzer {

    private final CoachPromptRepository coachPromptRepository;
    private final CoachAnswerQualityAnalyzer coachAnswerQualityAnalyzer;

    public Map<Long, CoachInteractionSummaryResponse> summarize(Collection<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return Map.of();
        }
        return coachPromptRepository.findBySubmissionIdIn(submissionIds)
                .stream()
                .filter(prompt -> prompt.getSubmissionId() != null)
                .collect(Collectors.groupingBy(CoachPrompt::getSubmissionId))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> summarize(entry.getKey(), entry.getValue())));
    }

    public List<CoachPrompt> findPrompts(Collection<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }
        return coachPromptRepository.findBySubmissionIdIn(submissionIds);
    }

    public CoachInteractionSummaryResponse latestForOrderedSubmissions(List<Long> orderedSubmissionIds,
                                                                       Map<Long, CoachInteractionSummaryResponse> summaries) {
        if (orderedSubmissionIds == null || summaries == null || summaries.isEmpty()) {
            return null;
        }
        return orderedSubmissionIds.stream()
                .map(summaries::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private CoachInteractionSummaryResponse summarize(Long submissionId, List<CoachPrompt> prompts) {
        List<CoachPrompt> sorted = prompts == null ? List.of() : prompts.stream()
                .sorted(Comparator
                        .comparing(CoachPrompt::getTurnIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CoachPrompt::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .toList();
        CoachPrompt latest = sorted.isEmpty() ? null : sorted.get(sorted.size() - 1);
        CoachPrompt latestAnswered = sorted.stream()
                .filter(prompt -> hasText(prompt.getStudentAnswer()) || hasText(prompt.getCoachFeedback()))
                .max(Comparator
                        .comparing(this::answeredAtOrCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(CoachPrompt::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .orElse(null);
        int answeredCount = (int) sorted.stream().filter(prompt -> hasText(prompt.getStudentAnswer())).count();
        String status = resolveStatus(sorted.size(), answeredCount, latest);
        String statusLabel = switch (status) {
            case "ANSWERED" -> "已回答追问";
            case "CONTINUED" -> "继续追问中";
            case "PROMPTED" -> "待回答追问";
            default -> "未追问";
        };
        String summary = buildSummary(sorted.size(), answeredCount, latestAnswered, statusLabel);
        return CoachInteractionSummaryResponse.builder()
                .submissionId(submissionId)
                .turnCount(sorted.size())
                .answeredTurnCount(answeredCount)
                .prompted(!sorted.isEmpty())
                .answered(answeredCount > 0)
                .status(status)
                .statusLabel(statusLabel)
                .summary(summary)
                .latestQuestion(latest == null ? null : latest.getQuestion())
                .latestAnswer(latestAnswered == null ? null : latestAnswered.getStudentAnswer())
                .latestFeedback(latestAnswered == null ? null : latestAnswered.getCoachFeedback())
                .latestAt(latestAnswered == null ? (latest == null ? null : latest.getCreatedAt()) : answeredAtOrCreatedAt(latestAnswered))
                .answerQualitySignal(coachAnswerQualityAnalyzer.analyze(latestAnswered == null ? null : latestAnswered.getStudentAnswer()))
                .coachSafetyRejectionSignal(coachSafetyRejectionSignal(submissionId, sorted))
                .build();
    }

    private CoachInteractionSummaryResponse.CoachSafetyRejectionSignal coachSafetyRejectionSignal(Long submissionId,
                                                                                                  List<CoachPrompt> prompts) {
        List<CoachPrompt> rejected = prompts == null ? List.of() : prompts.stream()
                .filter(prompt -> "SAFETY_REJECTED".equalsIgnoreCase(prompt.getModelFailureReason()))
                .sorted(Comparator
                        .comparing(CoachPrompt::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(CoachPrompt::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        if (rejected.isEmpty()) {
            return CoachInteractionSummaryResponse.CoachSafetyRejectionSignal.builder()
                    .status("HEALTHY")
                    .rejectionCount(0)
                    .latestReason("")
                    .latestAnswerLeakRisk("")
                    .summary("Coach 模型追问暂无安全拒绝记录。")
                    .recommendedAction("继续观察 Coach 追问安全门。")
                    .needsTeacherAttention(false)
                    .evidenceRefs(List.of())
                    .build();
        }
        CoachPrompt latestRejected = rejected.get(rejected.size() - 1);
        return CoachInteractionSummaryResponse.CoachSafetyRejectionSignal.builder()
                .status("SAFETY_REJECTED")
                .rejectionCount(rejected.size())
                .latestReason(latestRejected.getModelFailureReason())
                .latestAnswerLeakRisk(latestRejected.getModelAnswerLeakRisk())
                .summary("Coach 模型追问有 " + rejected.size() + " 次被安全门拒绝，系统已返回 AI 追问不可用。")
                .recommendedAction("复核该提交的 Coach 上下文和 prompt 策略，把风险样本沉淀为 Coach 安全评测。")
                .needsTeacherAttention(true)
                .evidenceRefs(coachSafetyEvidenceRefs(submissionId, rejected))
                .build();
    }

    private List<String> coachSafetyEvidenceRefs(Long submissionId, List<CoachPrompt> rejected) {
        List<String> refs = rejected.stream()
                .map(CoachPrompt::getId)
                .filter(Objects::nonNull)
                .map(id -> "coach_prompt:" + id)
                .limit(5)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        if (submissionId != null) {
            refs.add("coach_safety_rejection:submission:" + submissionId);
        }
        return refs.stream().distinct().toList();
    }

    private String resolveStatus(int turnCount, int answeredCount, CoachPrompt latest) {
        if (turnCount == 0) {
            return "NONE";
        }
        if (answeredCount == 0) {
            return "PROMPTED";
        }
        if (latest != null && !hasText(latest.getStudentAnswer())) {
            return "CONTINUED";
        }
        return "ANSWERED";
    }

    private String buildSummary(int turnCount, int answeredCount, CoachPrompt latestAnswered, String statusLabel) {
        if (turnCount == 0) {
            return "还没有进入 AI 教练追问。";
        }
        String summary = statusLabel + "，共 " + turnCount + " 轮，已回答 " + answeredCount + " 轮。";
        if (latestAnswered != null && hasText(latestAnswered.getCoachFeedback())) {
            return summary + " 最近反馈：" + latestAnswered.getCoachFeedback();
        }
        return summary;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDateTime answeredAtOrCreatedAt(CoachPrompt prompt) {
        if (prompt == null) {
            return null;
        }
        return prompt.getAnsweredAt() == null ? prompt.getCreatedAt() : prompt.getAnsweredAt();
    }
}
