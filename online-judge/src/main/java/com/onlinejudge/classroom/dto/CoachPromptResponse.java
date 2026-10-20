package com.onlinejudge.classroom.dto;

import com.onlinejudge.classroom.domain.CoachPrompt;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CoachPromptResponse {
    private Long id;
    private Long assignmentId;
    private Long studentProfileId;
    private Long submissionId;
    private Long parentPromptId;
    private Integer turnIndex;
    private String hintPolicy;
    private String promptType;
    private String modelFailureReason;
    private String modelAnswerLeakRisk;
    private String question;
    private String studentAnswer;
    private String coachFeedback;
    private LocalDateTime answeredAt;
    private String rationale;
    private String contextSummary;
    private List<String> evidenceRefs;
    private CoachAdaptiveStrategySignal adaptiveStrategySignal;
    private List<CoachPromptResponse> turns;
    private LocalDateTime createdAt;

    public static CoachPromptResponse from(CoachPrompt prompt, List<String> evidenceRefs) {
        return from(prompt, evidenceRefs, (CoachAdaptiveStrategySignal) null);
    }

    public static CoachPromptResponse from(CoachPrompt prompt,
                                           List<String> evidenceRefs,
                                           CoachAdaptiveStrategySignal adaptiveStrategySignal) {
        if (prompt == null) {
            return null;
        }
        return CoachPromptResponse.builder()
                .id(prompt.getId())
                .assignmentId(prompt.getAssignmentId())
                .studentProfileId(prompt.getStudentProfileId())
                .submissionId(prompt.getSubmissionId())
                .parentPromptId(prompt.getParentPromptId())
                .turnIndex(prompt.getTurnIndex())
                .hintPolicy(prompt.getHintPolicy())
                .promptType(prompt.getPromptType())
                .modelFailureReason(prompt.getModelFailureReason())
                .modelAnswerLeakRisk(prompt.getModelAnswerLeakRisk())
                .question(prompt.getQuestion())
                .studentAnswer(prompt.getStudentAnswer())
                .coachFeedback(prompt.getCoachFeedback())
                .answeredAt(prompt.getAnsweredAt())
                .rationale(prompt.getRationale())
                .contextSummary(prompt.getContextSummary())
                .evidenceRefs(evidenceRefs)
                .adaptiveStrategySignal(adaptiveStrategySignal)
                .turns(List.of())
                .createdAt(prompt.getCreatedAt())
                .build();
    }

    public static CoachPromptResponse from(CoachPrompt prompt, List<String> evidenceRefs, List<CoachPromptResponse> turns) {
        return from(prompt, evidenceRefs, turns, null);
    }

    public static CoachPromptResponse from(CoachPrompt prompt,
                                           List<String> evidenceRefs,
                                           List<CoachPromptResponse> turns,
                                           CoachAdaptiveStrategySignal adaptiveStrategySignal) {
        CoachPromptResponse response = from(prompt, evidenceRefs, adaptiveStrategySignal);
        if (response != null) {
            response.setTurns(turns == null ? List.of() : turns);
        }
        return response;
    }

    @Data
    @Builder
    public static class CoachAdaptiveStrategySignal {
        private String strategy;
        private String reason;
        private String recommendedCoachMove;
        private boolean needsTeacherAttention;
        private List<String> evidenceRefs;
    }
}
