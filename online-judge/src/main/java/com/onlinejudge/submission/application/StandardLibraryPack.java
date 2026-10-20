package com.onlinejudge.submission.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardLibraryPack {

    public static final String SCHEMA_VERSION = "standard-library-pack-v2";
    public static final String STRUCTURE_VERSION = "standard-library-structure-v3";

    private String schemaVersion;
    private String taxonomyVersion;
    private String structureVersion;
    private List<KnowledgeGroupOption> knowledgeGroups;
    private List<BasicCauseOption> basicCauses;
    private List<ImprovementPointOption> improvementPoints;
    private List<KnowledgeAnchorOption> knowledgeAnchors;
    private List<SkillUnitOption> skillUnits;
    private List<MistakePointOption> mistakePoints;
    private List<ApplicationScenarioOption> applicationScenarios;
    private StandardLibraryNavigationSummary standardLibraryNavigationSummary;
    private List<TagOption> issueTags;
    private List<TagOption> fineGrainedTags;
    private List<ImprovementTagOption> improvementTags;
    private List<TeachingActionOption> teachingActions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BasicCauseOption {
        private String id;
        private String category;
        private String name;
        private String description;
        private String studentExplanation;
        private String teacherExplanation;
        private List<String> evidenceSignals;
        private List<String> commonCodePatterns;
        private List<String> judgeSignals;
        private String hintL1;
        private String hintL2;
        private String hintL3;
        private String abilityPoint;
        private String severity;
        private List<String> applicableLanguages;
        private List<String> relatedFineTags;
        private String teachingAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KnowledgeGroupOption {
        private String id;
        private String name;
        private String path;
        private String description;
        private List<SkillUnitGroupOption> skillUnits;
        private List<ImprovementPointOption> improvementPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkillUnitGroupOption {
        private SkillUnitOption skillUnit;
        private List<MistakePointOption> mistakePoints;
        private List<ImprovementPointOption> improvementPoints;
        private List<ApplicationScenarioOption> applicationScenarios;
        private List<String> candidateIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApplicationScenarioOption {
        private String id;
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
        private String reviewStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImprovementPointOption {
        private String id;
        private String category;
        private String name;
        private String description;
        private String whenToUse;
        private String studentBenefit;
        private String teacherExplanation;
        private List<String> requiredEvidence;
        private String hintL1;
        private String hintL2;
        private String hintL3;
        private String abilityPoint;
        private List<String> relatedBasicCauses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KnowledgeAnchorOption {
        private String id;
        private String name;
        private String path;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkillUnitOption {
        private String id;
        private String category;
        private String name;
        private String description;
        private String primaryKnowledgeNodeCode;
        private List<String> knowledgeNodeCodes;
        private List<String> relatedKnowledgeNodeCodes;
        private List<String> applicableLanguages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MistakePointOption {
        private String id;
        private String category;
        private String name;
        private String description;
        private String skillUnitCode;
        private String primaryKnowledgeNodeCode;
        private String mistakeType;
        private String commonMisconception;
        private List<String> knowledgeNodeCodes;
        private List<String> relatedKnowledgeNodeCodes;
        private List<String> applicableLanguages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StandardLibraryNavigationSummary {
        private String status;
        private String failureReason;
        private Integer selectedCount;
        private String uncertainty;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TagOption {
        private String id;
        private String label;
        private String studentExplanation;
        private String teacherExplanation;
        private String abilityPoint;
        private String parentTag;
        private String teachingAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TeachingActionOption {
        private String id;
        private String label;
        private String whenToUse;
        private String studentTaskTemplate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImprovementTagOption {
        private String id;
        private String label;
        private String whenToUse;
        private String studentBenefit;
    }
}
