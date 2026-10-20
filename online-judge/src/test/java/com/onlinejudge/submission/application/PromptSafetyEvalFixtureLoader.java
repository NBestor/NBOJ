package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

class PromptSafetyEvalFixtureLoader {

    private static final String DEFAULT_RESOURCE = "/diagnosis-eval-fixtures/prompt-safety-cases.json";

    private final ObjectMapper objectMapper;

    PromptSafetyEvalFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Fixture> loadDefault() throws IOException {
        try (InputStream inputStream = PromptSafetyEvalFixtureLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture resource: " + DEFAULT_RESOURCE);
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    record Fixture(String name,
                   String source,
                   Long caseId,
                   ProblemFixture problem,
                   SubmissionFixture submission,
                   List<CaseResultFixture> caseResults,
                   UnsafeAnalysisFixture unsafeAnalysis,
                   ExpectedFixture expected,
                   SourceMaterialFixture sourceMaterial,
                   QualityFixture quality) {

        Problem toProblem() {
            return Problem.builder()
                    .id(problem.id())
                    .title(problem.title())
                    .description(problem.description())
                    .difficulty(parseDifficulty(problem.difficulty()))
                    .timeLimit(problem.timeLimit())
                    .memoryLimit(problem.memoryLimit())
                    .build();
        }

        Submission toSubmission() {
            return Submission.builder()
                    .id(submissionId())
                    .problemId(problem.id())
                    .languageName(submission.languageName())
                    .verdict(parseVerdict(submission.verdict()))
                    .sourceCode(submission.sourceCode())
                    .build();
        }

        List<SubmissionCaseResult> toCaseResults() {
            if (caseResults == null) {
                return List.of();
            }
            return caseResults.stream()
                    .map(result -> SubmissionCaseResult.builder()
                            .submissionId(submissionId())
                            .testCaseNumber(result.testCaseNumber())
                            .passed(Boolean.TRUE.equals(result.passed()))
                            .hidden(Boolean.TRUE.equals(result.hidden()))
                            .inputSnapshot(result.inputSnapshot())
                            .actualOutput(result.actualOutput())
                            .expectedOutput(result.expectedOutput())
                            .executionTime(result.executionTime())
                            .memoryUsed(result.memoryUsed())
                            .build())
                    .toList();
        }

        SubmissionAnalysisResponse toUnsafeAnalysis() {
            return SubmissionAnalysisResponse.builder()
                    .submissionId(submissionId())
                    .sourceType("PROMPT_SAFETY_FIXTURE")
                    .scenario(unsafeAnalysis.scenario())
                    .headline(unsafeAnalysis.analysisHeadline())
                    .summary(unsafeAnalysis.summary())
                    .issueTags(nullToEmpty(unsafeAnalysis.issueTags()))
                    .fineGrainedTags(nullToEmpty(unsafeAnalysis.fineGrainedTags()))
                    .evidenceRefs(nullToEmpty(expected.requiredEvidenceRefs()))
                    .studentHint(unsafeAnalysis.studentHint())
                    .studentHintPlan(toHintPlan())
                    .learningInterventionPlan(toInterventionPlan())
                    .reportMarkdown(unsafeAnalysis.reportMarkdown())
                    .firstFailedCase(firstFailedCase())
                    .confidence(0.72)
                    .answerLeakRisk("LOW")
                    .build();
        }

        private SubmissionAnalysisResponse.StudentHintPlan toHintPlan() {
            UnsafeHintPlanFixture plan = unsafeAnalysis.studentHintPlan();
            if (plan == null) {
                return null;
            }
            return SubmissionAnalysisResponse.StudentHintPlan.builder()
                    .hintLevel(plan.hintLevel())
                    .problemType(plan.problemType())
                    .evidenceAnchor(plan.evidenceAnchor())
                    .nextAction(plan.nextAction())
                    .coachQuestion(plan.coachQuestion())
                    .teachingAction(plan.teachingAction())
                    .evidenceRefs(nullToEmpty(plan.evidenceRefs()))
                    .answerLeakRisk("LOW")
                    .build();
        }

        private SubmissionAnalysisResponse.LearningInterventionPlan toInterventionPlan() {
            UnsafeInterventionPlanFixture plan = unsafeAnalysis.learningInterventionPlan();
            if (plan == null) {
                return null;
            }
            return SubmissionAnalysisResponse.LearningInterventionPlan.builder()
                    .interventionType(plan.interventionType())
                    .goal(plan.goal())
                    .studentTask(plan.studentTask())
                    .checkQuestion(plan.checkQuestion())
                    .completionSignal(plan.completionSignal())
                    .evidenceRefs(nullToEmpty(plan.evidenceRefs()))
                    .estimatedMinutes(plan.estimatedMinutes())
                    .answerLeakRisk("LOW")
                    .build();
        }

        private SubmissionAnalysisResponse.FailedCaseSnapshot firstFailedCase() {
            if (caseResults == null) {
                return null;
            }
            return caseResults.stream()
                    .filter(result -> !Boolean.TRUE.equals(result.passed()))
                    .findFirst()
                    .map(result -> SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                            .testCaseNumber(result.testCaseNumber())
                            .hidden(Boolean.TRUE.equals(result.hidden()))
                            .input(result.inputSnapshot())
                            .actualOutput(result.actualOutput())
                            .expectedOutput(result.expectedOutput())
                            .build())
                    .orElse(null);
        }

        Long submissionId() {
            return 60000L + caseId;
        }

        private Problem.Difficulty parseDifficulty(String value) {
            if (value == null || value.isBlank()) {
                return Problem.Difficulty.EASY;
            }
            return Problem.Difficulty.valueOf(value);
        }

        private Submission.Verdict parseVerdict(String value) {
            if (value == null || value.isBlank()) {
                return Submission.Verdict.WRONG_ANSWER;
            }
            return Submission.Verdict.valueOf(value);
        }

        private <T> List<T> nullToEmpty(List<T> values) {
            return values == null ? List.of() : values;
        }
    }

    record ProblemFixture(Long id,
                          String title,
                          String description,
                          String difficulty,
                          Integer timeLimit,
                          Integer memoryLimit) {
    }

    record SubmissionFixture(String languageName,
                             String verdict,
                             String sourceCode) {
    }

    record CaseResultFixture(Integer testCaseNumber,
                             Boolean passed,
                             Boolean hidden,
                             String inputSnapshot,
                             String actualOutput,
                             String expectedOutput,
                             Double executionTime,
                             Integer memoryUsed) {
    }

    record UnsafeAnalysisFixture(String scenario,
                                 List<String> issueTags,
                                 List<String> fineGrainedTags,
                                 String analysisHeadline,
                                 String summary,
                                 String studentHint,
                                 UnsafeHintPlanFixture studentHintPlan,
                                 UnsafeInterventionPlanFixture learningInterventionPlan,
                                 String reportMarkdown) {
    }

    record UnsafeHintPlanFixture(String hintLevel,
                                 String problemType,
                                 String evidenceAnchor,
                                 String nextAction,
                                 String coachQuestion,
                                 String teachingAction,
                                 List<String> evidenceRefs) {
    }

    record UnsafeInterventionPlanFixture(String interventionType,
                                         String goal,
                                         String studentTask,
                                         String checkQuestion,
                                         String completionSignal,
                                         List<String> evidenceRefs,
                                         Integer estimatedMinutes) {
    }

    record ExpectedFixture(String riskLevel,
                           List<String> blockedReasons,
                           String expectedSafetyAction,
                           List<String> mustNotMention,
                           List<String> requiredEvidenceRefs) {
    }

    record SourceMaterialFixture(String localFolder,
                                 List<String> artifacts,
                                 String anonymizationNote) {
    }

    record QualityFixture(String bugPattern,
                          String misconception,
                          String expectedStudentMove,
                          String evalPurpose) {
    }
}
