package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
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
public class SelfExplanationMasteryAnalyzer {

    public static final String STATUS_NO_EVIDENCE = "NO_EVIDENCE";
    public static final String STATUS_EMERGING = "EMERGING";
    public static final String STATUS_EVIDENCE_GROUNDED = "EVIDENCE_GROUNDED";
    public static final String STATUS_TRANSFER_READY = "TRANSFER_READY";
    public static final String STATUS_NEEDS_COACHING = "NEEDS_COACHING";
    public static final String STATUS_SAFETY_RISK = "SAFETY_RISK";

    private static final int WINDOW = 12;

    private final CoachAnswerQualityAnalyzer coachAnswerQualityAnalyzer;

    public StudentAbilityProfileResponse.SelfExplanationMasterySignal analyze(List<CoachPrompt> prompts) {
        List<CoachPrompt> answered = prompts == null ? List.of() : prompts.stream()
                .filter(Objects::nonNull)
                .filter(prompt -> hasText(prompt.getStudentAnswer()))
                .sorted(Comparator
                        .comparing(this::answeredAtOrCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .reversed())
                .limit(WINDOW)
                .toList();
        if (answered.isEmpty()) {
            long promptedCount = prompts == null ? 0 : prompts.stream().filter(Objects::nonNull).count();
            return signal(
                    STATUS_NO_EVIDENCE,
                    0.0,
                    promptedCount,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    evidenceRefs(prompts),
                    promptedCount >= 2
            );
        }

        long verifiableCount = 0;
        long transferReadyCount = 0;
        long vagueCount = 0;
        long safetyRiskCount = 0;
        double completenessSum = 0.0;
        Set<String> evidenceTypes = new LinkedHashSet<>();
        for (CoachPrompt prompt : answered) {
            CoachInteractionSummaryResponse.CoachAnswerQualitySignal quality =
                    coachAnswerQualityAnalyzer.analyze(prompt.getStudentAnswer());
            if (quality == null) {
                continue;
            }
            if (Boolean.TRUE.equals(quality.getVerifiable())) {
                verifiableCount++;
            }
            if ("TRANSFER_READY".equals(quality.getQualityLevel())) {
                transferReadyCount++;
            }
            if ("VAGUE_ACK".equals(quality.getQualityLevel()) || "DIRECTION_ONLY".equals(quality.getQualityLevel())) {
                vagueCount++;
            }
            if ("SAFETY_RISK".equals(quality.getQualityLevel()) || quality.isNeedsTeacherAttention()
                    && "SAFETY_RISK".equals(quality.getActionStatus())) {
                safetyRiskCount++;
            }
            completenessSum += quality.getEvidenceCompleteness() == null ? 0.0 : quality.getEvidenceCompleteness();
            if (quality.getEvidenceTypes() != null) {
                quality.getEvidenceTypes().stream()
                        .filter(this::hasText)
                        .forEach(evidenceTypes::add);
            }
        }
        double completeness = Math.round((completenessSum / Math.max(1, answered.size())) * 100.0) / 100.0;
        String status = resolveStatus(answered.size(), verifiableCount, transferReadyCount, vagueCount, safetyRiskCount, completeness);
        return signal(
                status,
                completeness,
                answered.size(),
                verifiableCount,
                transferReadyCount,
                vagueCount,
                safetyRiskCount,
                evidenceTypes.stream().limit(6).toList(),
                evidenceRefs(answered),
                STATUS_SAFETY_RISK.equals(status) || (STATUS_NO_EVIDENCE.equals(status) && answered.size() >= 2)
        );
    }

    public boolean isWeak(StudentAbilityProfileResponse.SelfExplanationMasterySignal signal) {
        return signal != null && (STATUS_NEEDS_COACHING.equals(signal.getStatus())
                || STATUS_SAFETY_RISK.equals(signal.getStatus())
                || (STATUS_NO_EVIDENCE.equals(signal.getStatus()) && signal.isNeedsTeacherAttention()));
    }

    public boolean needsPractice(StudentAbilityProfileResponse.SelfExplanationMasterySignal signal) {
        return signal != null && (STATUS_EMERGING.equals(signal.getStatus())
                || STATUS_NEEDS_COACHING.equals(signal.getStatus()));
    }

    private String resolveStatus(long answeredCount,
                                 long verifiableCount,
                                 long transferReadyCount,
                                 long vagueCount,
                                 long safetyRiskCount,
                                 double completeness) {
        if (safetyRiskCount > 0 && verifiableCount < 2) {
            return STATUS_SAFETY_RISK;
        }
        if (vagueCount >= 2 || vagueCount * 2 > answeredCount && verifiableCount == 0) {
            return STATUS_NEEDS_COACHING;
        }
        if (transferReadyCount >= 1 && completeness >= 0.7) {
            return STATUS_TRANSFER_READY;
        }
        if (verifiableCount >= 2 && completeness >= 0.55) {
            return STATUS_EVIDENCE_GROUNDED;
        }
        return STATUS_EMERGING;
    }

    private StudentAbilityProfileResponse.SelfExplanationMasterySignal signal(String status,
                                                                               double evidenceCompleteness,
                                                                               long answeredTurnCount,
                                                                               long verifiableAnswerCount,
                                                                               long transferReadyCount,
                                                                               long vagueAnswerCount,
                                                                               long safetyRiskCount,
                                                                               List<String> evidenceTypes,
                                                                               List<String> evidenceRefs,
                                                                               boolean teacherAttention) {
        return StudentAbilityProfileResponse.SelfExplanationMasterySignal.builder()
                .status(status)
                .label(label(status))
                .summary(summary(status, answeredTurnCount, verifiableAnswerCount, transferReadyCount, vagueAnswerCount, safetyRiskCount))
                .evidenceCompleteness(evidenceCompleteness)
                .answeredTurnCount(answeredTurnCount)
                .verifiableAnswerCount(verifiableAnswerCount)
                .transferReadyCount(transferReadyCount)
                .vagueAnswerCount(vagueAnswerCount)
                .safetyRiskCount(safetyRiskCount)
                .evidenceTypes(evidenceTypes == null ? List.of() : evidenceTypes)
                .evidenceRefs(evidenceRefs == null ? List.of() : evidenceRefs)
                .recommendedAction(recommendedAction(status))
                .needsTeacherAttention(teacherAttention || STATUS_SAFETY_RISK.equals(status))
                .build();
    }

    private String label(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_TRANSFER_READY -> "可迁移解释";
            case STATUS_EVIDENCE_GROUNDED -> "证据扎实";
            case STATUS_EMERGING -> "解释形成中";
            case STATUS_NEEDS_COACHING -> "需补证据";
            case STATUS_SAFETY_RISK -> "解释越界风险";
            default -> "暂无解释证据";
        };
    }

    private String summary(String status,
                           long answeredTurnCount,
                           long verifiableAnswerCount,
                           long transferReadyCount,
                           long vagueAnswerCount,
                           long safetyRiskCount) {
        if (STATUS_NO_EVIDENCE.equals(status)) {
            return "近期还没有可用于判断自解释能力的 Coach 回答证据。";
        }
        if (STATUS_SAFETY_RISK.equals(status)) {
            return "近期有 " + safetyRiskCount + " 次回答疑似越过证据层，需要回到样例、变量或输出对比。";
        }
        if (STATUS_NEEDS_COACHING.equals(status)) {
            return "近期 " + vagueAnswerCount + " 次回答仍停留在空泛确认或方向描述，需要补最小证据。";
        }
        if (STATUS_TRANSFER_READY.equals(status)) {
            return "近期已有 " + transferReadyCount + " 次回答能解释迁移条件，可进入通过后复盘或同能力新题。";
        }
        if (STATUS_EVIDENCE_GROUNDED.equals(status)) {
            return "近期 " + verifiableAnswerCount + " 次回答包含可验证证据，适合基于证据做最小修改。";
        }
        return "近期已有 " + answeredTurnCount + " 次回答，但证据还不稳定，需要继续追问样例或变量轨迹。";
    }

    private String recommendedAction(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_TRANSFER_READY -> "让学生写出旧题到新题的共同判断条件，并做一道同能力迁移题。";
            case STATUS_EVIDENCE_GROUNDED -> "要求学生基于已有证据做一次最小修改，并预测下一次评测现象。";
            case STATUS_EMERGING -> "继续要求学生补一个最小样例、变量轨迹或输出对比。";
            case STATUS_NEEDS_COACHING -> "暂停加新提示，先让学生写一个可检查的最小样例和预期输出。";
            case STATUS_SAFETY_RISK -> "教师示范如何只描述证据，不讨论完整代码或直接改法。";
            default -> "先回答一个 Coach 追问，补充最小样例、变量变化或复杂度数量级。";
        };
    }

    private List<String> evidenceRefs(List<CoachPrompt> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return List.of();
        }
        return prompts.stream()
                .filter(Objects::nonNull)
                .filter(prompt -> prompt.getId() != null)
                .sorted(Comparator
                        .comparing(this::answeredAtOrCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .reversed())
                .limit(5)
                .map(prompt -> "self-explanation:coach-prompt:" + prompt.getId())
                .toList();
    }

    private LocalDateTime answeredAtOrCreatedAt(CoachPrompt prompt) {
        if (prompt == null) {
            return null;
        }
        return prompt.getAnsweredAt() == null ? prompt.getCreatedAt() : prompt.getAnsweredAt();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
