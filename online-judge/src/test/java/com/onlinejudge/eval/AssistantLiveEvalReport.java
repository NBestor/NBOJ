package com.onlinejudge.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantLiveEvalReport {

    private String model;
    private Integer totalCount;
    private Integer completedCount;
    private Integer runtimeFailureCount;
    private Integer qualityMissCount;
    private Integer safetyFailureCount;
    private Integer expectedSignalHitCount;
    private Integer evidenceValidCount;
    private Integer studentVisibleQualityPassCount;
    private Integer studentVisibleQualityRiskCount;
    private Integer runtimeFixtureDraftCount;
    private Integer qualityBaselineDraftCount;
    private List<Entry> entries;
    private List<LiveEvalRuntimeFixtureDraft> runtimeFixtureDrafts;
    private List<LiveEvalQualityBaselineDraft> qualityBaselineDrafts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String caseId;
        private String assistantType;
        private String model;
        private String promptVersion;
        private Long latencyMs;
        private String status;
        private Boolean fallbackUsed;
        private Boolean completedOutput;
        private Boolean expectedSignalHit;
        private Boolean evidenceValid;
        private Boolean safetyPassed;
        private Boolean teachingActionValid;
        private List<String> actualIssueTags;
        private List<String> actualFineGrainedTags;
        private List<String> actualEvidenceRefs;
        private String teachingAction;
        private String safetyTrigger;
        private String failureStage;
        private String failureReason;
        private String teacherExpectation;
        private String outputSummary;
        private String outputDetail;
        private String studentVisibleBasicText;
        private String studentVisibleImprovementText;
        private String studentVisibleNextActionText;
        private String studentVisibleText;
        private String studentReportBasicText;
        private String studentReportImprovementText;
        private String studentReportNextActionText;
        private String studentReportText;
        private Boolean studentVisibleQualityPassed;
        private List<String> studentVisibleQualityFlags;
        private String aiBetterThanTeacher;
        private String teacherBetterThanAi;
        private String iterationSuggestion;
    }
}
