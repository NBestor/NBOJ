package com.onlinejudge.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CoachInteractionSummaryResponse {
    private Long submissionId;
    private int turnCount;
    private int answeredTurnCount;
    private boolean prompted;
    private boolean answered;
    private String status;
    private String statusLabel;
    private String summary;
    private String latestQuestion;
    private String latestAnswer;
    private String latestFeedback;
    private LocalDateTime latestAt;
    private CoachImpactResponse impact;
    private CoachAnswerQualitySignal answerQualitySignal;
    private CoachSafetyRejectionSignal coachSafetyRejectionSignal;

    @Data
    @Builder
    public static class CoachAnswerQualitySignal {
        private String qualityLevel;
        private String qualityLabel;
        private String understandingLevel;
        private Double evidenceCompleteness;
        private Boolean verifiable;
        private String actionStatus;
        private String recommendedTeachingAction;
        private List<String> evidenceTypes;
        private List<String> missingEvidence;
        private String summary;
        private String nextCoachMove;
        private boolean needsTeacherAttention;
    }

    @Data
    @Builder
    public static class CoachSafetyRejectionSignal {
        private String status;
        private long rejectionCount;
        private String latestReason;
        private String latestAnswerLeakRisk;
        private String summary;
        private String recommendedAction;
        private boolean needsTeacherAttention;
        private List<String> evidenceRefs;
    }
}
