package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisEvidencePackage {

    public static final String SCHEMA_VERSION = "evidence-v1";

    private String schemaVersion;
    private SubmissionEvidence submission;
    private ProblemEvidence problem;
    private JudgeFacts judgeFacts;
    private HistoryEvidence history;
    private StudentLearningMemorySnapshot learningMemory;
    private PolicyEvidence policy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionEvidence {
        private Long id;
        private String language;
        private String verdict;
        private String sourceCode;
        private String sourceCodeWithLineNumbers;
        private Integer sourceCodeLineCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProblemEvidence {
        private Long id;
        private String title;
        private String description;
        private String difficulty;
        private Integer timeLimit;
        private Integer memoryLimit;
        private String aiPromptDirection;
        private List<String> knowledgePoints;
        private List<String> algorithmStrategies;
        private List<String> commonMistakes;
        private List<String> boundaryTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JudgeFacts {
        private String compileOutput;
        private String runtimeErrorMessage;
        private Integer passedCount;
        private Integer totalCount;
        private Boolean hiddenFailureObserved;
        private SubmissionAnalysisResponse.FailedCaseSnapshot firstFailedCase;
        private List<CaseSummary> caseResultsSummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseSummary {
        private Integer testCaseNumber;
        private Long testCaseId;
        private Boolean passed;
        private Boolean hidden;
        private Double executionTime;
        private Integer memoryUsed;
        private String inputPreview;
        private String actualOutputPreview;
        private String expectedOutputPreview;
        private String semanticCode;
        private String intentType;
        private String intentTitle;
        private String intentSummary;
        private String learningObjective;
        private String contestRole;
        private String revealPolicy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryEvidence {
        private String previousVerdict;
        private List<String> previousIssueTags;
        private List<String> previousFineGrainedTags;
        private List<String> recentIssueTags;
        private List<String> recentFineGrainedTags;
        private String repeatedIssueTag;
        private String repeatedFineGrainedTag;
        private Long repeatedIssueCount;
        private Long repeatedFineGrainedIssueCount;
        private String transitionSignal;
        private String previousInterventionType;
        private String previousInterventionTask;
        private String previousInterventionCompletionSignal;
        private String previousLearningActionStatus;
        private Double previousLearningActionConfidence;
        private List<String> previousLearningActionEvidenceRefs;
        private String previousLearningActionSummary;
        private String previousLearningActionNextAdjustment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentLearningMemorySnapshot {
        private Long studentProfileId;
        private Long assignmentId;
        private Long currentProblemId;
        private Integer observedSubmissionCount;
        private Integer observedProblemCount;
        private List<MemoryTagStat> recurringIssueTags;
        private List<MemoryTagStat> recurringFineGrainedTags;
        private List<AbilityFocus> abilityFocus;
        private String recentTrend;
        private String interventionEffect;
        private String teacherCorrectionSummary;
        private TeacherCalibrationPattern teacherCalibrationSignal;
        private List<TeacherCalibrationPattern> teacherCalibrationPatterns;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeacherCalibrationPattern {
        private String originalIssueTag;
        private String originalFineGrainedTag;
        private String correctedIssueTag;
        private String correctedFineGrainedTag;
        private String correctionType;
        private String targetIssueId;
        private String correctedKnowledgePath;
        private String targetEvidenceRef;
        private Long correctionCount;
        private Long evalCandidateCount;
        private String latestTeacherNote;
        private List<Long> evidenceSubmissionIds;
        private List<String> evidenceRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryTagStat {
        private String tag;
        private Long count;
        private List<Long> evidenceSubmissionIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AbilityFocus {
        private String abilityPoint;
        private Long submissionCount;
        private Long problemCount;
        private List<String> evidenceTags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyEvidence {
        private String hintPolicy;
        private List<String> allowedHintLevels;
    }
}
