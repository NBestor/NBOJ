package com.onlinejudge.learning.standardlibrary.dto;

import com.onlinejudge.learning.standardlibrary.domain.AiStandardApplicationScenario;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardImprovementPoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardMistakePoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardSkillUnit;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiStandardLibraryDiagnosticLayerResponse {
    private AiStandardLibraryNavigationNodeResponse knowledgePoint;
    private List<SkillUnit> skillUnits;
    private List<ImprovementPoint> directImprovementPoints;
    private List<ProvisionalCandidate> provisionalCandidates;

    @Data
    @Builder
    public static class ProvisionalCandidate {
        private String code;
        private String name;
        private String layer;
        private String parentKnowledgeNodeCode;
        private List<String> suggestedPath;
        private String description;
        private Double confidence;
        private Integer occurrenceCount;
        private boolean provisional;
    }

    @Data
    @Builder
    public static class SkillUnit {
        private String code;
        private String category;
        private String name;
        private String description;
        private String learningGoal;
        private String primaryKnowledgeNodeCode;
        private List<String> knowledgeNodeCodes;
        private List<MistakePoint> mistakePoints;
        private List<ImprovementPoint> improvementPoints;
        private List<ApplicationScenario> applicationScenarios;

        public static SkillUnit from(AiStandardSkillUnit item,
                                     List<MistakePoint> mistakePoints,
                                     List<ImprovementPoint> improvementPoints,
                                     List<ApplicationScenario> applicationScenarios) {
            return SkillUnit.builder()
                    .code(item.getCode())
                    .category(item.getCategory())
                    .name(item.getName())
                    .description(item.getDescription())
                    .learningGoal(item.getLearningGoal())
                    .primaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode())
                    .knowledgeNodeCodes(lines(item.getKnowledgeNodeCodes()))
                    .mistakePoints(mistakePoints)
                    .improvementPoints(improvementPoints)
                    .applicationScenarios(applicationScenarios)
                    .build();
        }
    }

    @Data
    @Builder
    public static class ApplicationScenario {
        private String code;
        private String transferPairCode;
        private String contextType;
        private String learningPhase;
        private String title;
        private String knowledgePointCode;
        private String skillUnitCode;
        private List<String> linkedMistakeCodes;
        private List<String> linkedImprovementCodes;
        private String taskContext;
        private String studentTask;
        private String observableEvidence;
        private String commonFailure;
        private String teacherMove;
        private String studentCheck;
        private String constraintProfile;
        private String successCriteria;
        private String transferNote;
        private String difficultyLevel;
        private List<String> applicableLanguages;
        private String sourceFramework;
        private String sourceReference;
        private String reviewStatus;

        public static ApplicationScenario from(AiStandardApplicationScenario item) {
            return ApplicationScenario.builder()
                    .code(item.getCode())
                    .transferPairCode(item.getTransferPairCode())
                    .contextType(item.getContextType())
                    .learningPhase(item.getLearningPhase())
                    .title(item.getTitle())
                    .knowledgePointCode(item.getKnowledgePointCode())
                    .skillUnitCode(item.getSkillUnitCode())
                    .linkedMistakeCodes(lines(item.getLinkedMistakeCodes()))
                    .linkedImprovementCodes(lines(item.getLinkedImprovementCodes()))
                    .taskContext(item.getTaskContext())
                    .studentTask(item.getStudentTask())
                    .observableEvidence(item.getObservableEvidence())
                    .commonFailure(item.getCommonFailure())
                    .teacherMove(item.getTeacherMove())
                    .studentCheck(item.getStudentCheck())
                    .constraintProfile(item.getConstraintProfile())
                    .successCriteria(item.getSuccessCriteria())
                    .transferNote(item.getTransferNote())
                    .difficultyLevel(item.getDifficultyLevel())
                    .applicableLanguages(lines(item.getApplicableLanguages()))
                    .sourceFramework(item.getSourceFramework())
                    .sourceReference(item.getSourceReference())
                    .reviewStatus(item.getReviewStatus())
                    .build();
        }
    }

    @Data
    @Builder
    public static class MistakePoint {
        private String code;
        private String category;
        private String name;
        private String description;
        private String skillUnitCode;
        private String mistakeType;
        private String misconception;
        private String symptom;
        private String repairStrategy;
        private String severity;
        private String primaryKnowledgeNodeCode;
        private List<String> knowledgeNodeCodes;

        public static MistakePoint from(AiStandardMistakePoint item) {
            return MistakePoint.builder()
                    .code(item.getCode())
                    .category(item.getCategory())
                    .name(item.getName())
                    .description(item.getDescription())
                    .skillUnitCode(item.getSkillUnitCode())
                    .mistakeType(item.getMistakeType())
                    .misconception(item.getMisconception())
                    .symptom(item.getSymptom())
                    .repairStrategy(item.getRepairStrategy())
                    .severity(item.getSeverity())
                    .primaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode())
                    .knowledgeNodeCodes(lines(item.getKnowledgeNodeCodes()))
                    .build();
        }
    }

    @Data
    @Builder
    public static class ImprovementPoint {
        private String code;
        private String category;
        private String name;
        private String description;
        private String skillUnitCode;
        private String primaryKnowledgeNodeCode;
        private List<String> knowledgeNodeCodes;
        private String improvementGoal;
        private String practiceStrategy;
        private String studentBenefit;
        private String teacherExplanation;
        private List<String> relatedMistakeCodes;

        public static ImprovementPoint from(AiStandardImprovementPoint item) {
            return ImprovementPoint.builder()
                    .code(item.getCode())
                    .category(item.getCategory())
                    .name(item.getName())
                    .description(item.getDescription())
                    .skillUnitCode(item.getSkillUnitCode())
                    .primaryKnowledgeNodeCode(item.getPrimaryKnowledgeNodeCode())
                    .knowledgeNodeCodes(lines(item.getKnowledgeNodeCodes()))
                    .improvementGoal(item.getImprovementGoal())
                    .practiceStrategy(item.getPracticeStrategy())
                    .studentBenefit(item.getStudentBenefit())
                    .teacherExplanation(item.getTeacherExplanation())
                    .relatedMistakeCodes(lines(item.getRelatedMistakeCodes()))
                    .build();
        }
    }

    private static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }
}
