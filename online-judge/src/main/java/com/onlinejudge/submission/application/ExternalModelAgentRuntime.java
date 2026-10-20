package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExternalModelAgentRuntime {

    public static final String RUNTIME_PROFILE_STANDARD = "standard";

    private final ModelDiagnosisBriefBuilder briefBuilder;
    private final PromptTemplateRegistry promptTemplateRegistry;
    private final ModelOutputValidator modelOutputValidator;
    private final ExternalModelOutputNormalizer outputNormalizer;
    private final AdviceGenerationOutputValidator adviceGenerationOutputValidator;
    private final AdviceGenerationOutputNormalizer adviceGenerationOutputNormalizer;
    private final AdviceGenerationFeedbackMapper adviceGenerationFeedbackMapper;

    public ExternalModelAgentRuntime(ModelDiagnosisBriefBuilder briefBuilder,
                                     StandardLibraryPackBuilder standardLibraryPackBuilder,
                                     PromptTemplateRegistry promptTemplateRegistry,
                                     ModelOutputValidator modelOutputValidator) {
        this(briefBuilder, standardLibraryPackBuilder, promptTemplateRegistry, modelOutputValidator,
                new ExternalModelOutputNormalizer(),
                new AdviceGenerationOutputValidator(),
                new AdviceGenerationOutputNormalizer(),
                new AdviceGenerationFeedbackMapper());
    }

    @Autowired
    public ExternalModelAgentRuntime(ModelDiagnosisBriefBuilder briefBuilder,
                                     StandardLibraryPackBuilder standardLibraryPackBuilder,
                                     PromptTemplateRegistry promptTemplateRegistry,
                                     ModelOutputValidator modelOutputValidator,
                                     ExternalModelOutputNormalizer outputNormalizer,
                                     AdviceGenerationOutputValidator adviceGenerationOutputValidator,
                                     AdviceGenerationOutputNormalizer adviceGenerationOutputNormalizer,
                                     AdviceGenerationFeedbackMapper adviceGenerationFeedbackMapper) {
        this.briefBuilder = briefBuilder;
        this.promptTemplateRegistry = promptTemplateRegistry;
        this.modelOutputValidator = modelOutputValidator;
        this.outputNormalizer = outputNormalizer == null ? new ExternalModelOutputNormalizer() : outputNormalizer;
        this.adviceGenerationOutputValidator = adviceGenerationOutputValidator == null
                ? new AdviceGenerationOutputValidator()
                : adviceGenerationOutputValidator;
        this.adviceGenerationOutputNormalizer = adviceGenerationOutputNormalizer == null
                ? new AdviceGenerationOutputNormalizer()
                : adviceGenerationOutputNormalizer;
        this.adviceGenerationFeedbackMapper = adviceGenerationFeedbackMapper == null
                ? new AdviceGenerationFeedbackMapper()
                : adviceGenerationFeedbackMapper;
    }

    public RuntimePlan prepare(DiagnosisEvidencePackage evidencePackage,
                               SubmissionAnalysisResponse fallback) {
        return prepare(evidencePackage, fallback, RUNTIME_PROFILE_STANDARD);
    }

    public RuntimePlan prepare(DiagnosisEvidencePackage evidencePackage,
                               SubmissionAnalysisResponse fallback,
                               String runtimeProfile) {
        ModelDiagnosisBrief brief = briefBuilder.build(evidencePackage, fallback);
        return RuntimePlan.builder()
                .brief(brief)
                .freeDiagnosisPrompt(promptTemplateRegistry.get(PromptTemplateRegistry.FREE_DIAGNOSIS_V2))
                .standardLibraryNavigationPrompt(promptTemplateRegistry.get(PromptTemplateRegistry.STANDARD_LIBRARY_NAVIGATION_V1))
                .advicePrompt(promptTemplateRegistry.get(PromptTemplateRegistry.DIAGNOSIS_REPORT_V4))
                .teacherInsightPrompt(promptTemplateRegistry.get(PromptTemplateRegistry.TEACHER_INSIGHT_V1))
                .runtimeProfile(RUNTIME_PROFILE_STANDARD)
                .requestCompact(false)
                .build();
    }

    public com.onlinejudge.submission.dto.SubmissionAnalysisResponse.StudentFeedback normalizeStudentFeedback(
            com.onlinejudge.submission.dto.SubmissionAnalysisResponse.StudentFeedback feedback,
            RuntimePlan runtimePlan) {
        return outputNormalizer.normalizeStudentFeedback(feedback, runtimePlan);
    }

    public ExternalModelStagePayloads.StageValidationResult validateStudentFeedback(
            com.onlinejudge.submission.dto.SubmissionAnalysisResponse.StudentFeedback feedback,
            RuntimePlan runtimePlan) {
        return modelOutputValidator.validateStudentFeedback(
                feedback,
                runtimePlan == null ? null : runtimePlan.getBrief(),
                runtimePlan == null ? null : runtimePlan.getStandardLibraryPack()
        );
    }

    public ExternalModelStagePayloads.StageValidationResult validateAdviceGeneration(
            AdviceGenerationOutput output,
            RuntimePlan runtimePlan) {
        return adviceGenerationOutputValidator.validate(
                output,
                runtimePlan == null ? null : runtimePlan.getBrief(),
                runtimePlan == null ? null : runtimePlan.getStandardLibraryPack()
        );
    }

    public AdviceGenerationOutput normalizeAdviceGeneration(AdviceGenerationOutput output,
                                                            RuntimePlan runtimePlan) {
        return adviceGenerationOutputNormalizer.normalize(
                output,
                runtimePlan == null ? null : runtimePlan.getStandardLibraryPack()
        );
    }

    public com.onlinejudge.submission.dto.SubmissionAnalysisResponse.StudentFeedback mapAdviceStudentFeedback(
            AdviceGenerationOutput output,
            RuntimePlan runtimePlan) {
        return adviceGenerationFeedbackMapper.toStudentFeedback(
                output,
                runtimePlan == null ? null : runtimePlan.getStandardLibraryPack()
        );
    }

    @Data
    @Builder
    public static class RuntimePlan {
        private ModelDiagnosisBrief brief;
        private StandardLibraryPack standardLibraryPack;
        private FreeDiagnosisOutput freeDiagnosisOutput;
        private java.util.List<IssueLibraryAnchor> issueLibraryAnchors;
        private StandardLibraryNavigationOutput standardLibraryNavigationOutput;
        private StandardLibraryNavigationResult standardLibraryNavigationResult;
        private AdviceGenerationResult adviceGenerationResult;
        private TeacherInsightOutput teacherInsightOutput;
        private PromptTemplateRegistry.PromptTemplate freeDiagnosisPrompt;
        private PromptTemplateRegistry.PromptTemplate standardLibraryNavigationPrompt;
        private PromptTemplateRegistry.PromptTemplate advicePrompt;
        private PromptTemplateRegistry.PromptTemplate teacherInsightPrompt;
        private String runtimeProfile;
        private boolean requestCompact;
    }
}
