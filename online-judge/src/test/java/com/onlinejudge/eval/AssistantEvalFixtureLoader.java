package com.onlinejudge.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class AssistantEvalFixtureLoader {

    private static final String DEFAULT_RESOURCE = "/assistant-eval-fixtures/assistant-live-cases.json";

    private final ObjectMapper objectMapper;

    AssistantEvalFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Fixture> loadDefault() throws IOException {
        try (InputStream inputStream = AssistantEvalFixtureLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture resource: " + DEFAULT_RESOURCE);
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    List<Fixture> load(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), new TypeReference<>() {
        });
    }

    record Fixture(String caseId,
                   String assistantType,
                   String teacherExpectation,
                   Rubric rubric,
                   String qualityNotes,
                   DiagnosisCase diagnosis,
                   CoachCase coach,
                   GrowthReportCase growthReport) {

        boolean isDiagnosis() {
            return "SUBMISSION_DIAGNOSIS".equals(assistantType);
        }

        boolean isCoach() {
            return "COACH_QUESTION".equals(assistantType);
        }

        boolean isGrowthReport() {
            return "GROWTH_REPORT".equals(assistantType);
        }
    }

    record Rubric(List<String> expectedSignals,
                  List<String> expectedIssueTags,
                  List<String> expectedFineGrainedTags,
                  List<String> requiredEvidenceRefs,
                  List<String> forbiddenPhrases) {
    }

    record DiagnosisCase(ProblemInput problem,
                         SubmissionInput submission,
                         List<CaseResultInput> caseResults,
                         BaselineInput baseline) {

        Problem toProblem() {
            return problem.toProblem();
        }

        Submission toSubmission() {
            return submission.toSubmission();
        }

        List<SubmissionCaseResult> toCaseResults() {
            return caseResults == null ? List.of() : caseResults.stream()
                    .map(CaseResultInput::toCaseResult)
                    .toList();
        }

        SubmissionAnalysisResponse toBaseline() {
            return baseline.toBaseline(submission.id());
        }
    }

    record CoachCase(String turnType,
                     Long submissionId,
                     String primaryTag,
                     String hintPolicy,
                     String verdict,
                     String scenario,
                     String headline,
                     String summary,
                     String contextSummary,
                     List<String> evidenceRefs,
                     String studentAnswer) {

        Submission toSubmission() {
            return Submission.builder()
                    .id(submissionId)
                    .verdict(parseVerdict(verdict))
                    .build();
        }

        SubmissionAnalysis toAnalysis() {
            return SubmissionAnalysis.builder()
                    .submissionId(submissionId)
                    .scenario(scenario)
                    .headline(headline)
                    .summary(summary)
                    .analysisSource("FIXTURE_BASELINE")
                    .reportMarkdown(summary == null ? "" : summary)
                    .build();
        }
    }

    record GrowthReportCase(ProblemInput problem,
                            String fallbackMarkdown,
                            List<Map<String, Object>> timeline) {

        Problem toProblem() {
            return problem.toProblem();
        }
    }

    record ProblemInput(Long id,
                        String title,
                        String description,
                        String difficulty,
                        Integer timeLimit,
                        Integer memoryLimit,
                        String aiPromptDirection,
                        List<String> knowledgePoints,
                        List<String> algorithmStrategies,
                        List<String> commonMistakes,
                        List<String> boundaryTypes) {

        Problem toProblem() {
            return Problem.builder()
                    .id(id)
                    .title(title)
                    .description(description)
                    .difficulty(parseDifficulty(difficulty))
                    .timeLimit(timeLimit == null ? 1000 : timeLimit)
                    .memoryLimit(memoryLimit == null ? 65536 : memoryLimit)
                    .aiPromptDirection(aiPromptDirection)
                    .knowledgePoints(knowledgePoints == null ? List.of() : knowledgePoints)
                    .algorithmStrategies(algorithmStrategies == null ? List.of() : algorithmStrategies)
                    .commonMistakes(commonMistakes == null ? List.of() : commonMistakes)
                    .boundaryTypes(boundaryTypes == null ? List.of() : boundaryTypes)
                    .build();
        }
    }

    record SubmissionInput(Long id,
                           Long problemId,
                           Integer languageId,
                           String languageName,
                           String verdict,
                           String sourceCode,
                           String compileOutput,
                           String errorMessage) {

        Submission toSubmission() {
            return Submission.builder()
                    .id(id)
                    .problemId(problemId)
                    .languageId(languageId == null ? 71 : languageId)
                    .languageName(languageName == null ? "Python 3" : languageName)
                    .verdict(parseVerdict(verdict))
                    .sourceCode(sourceCode == null ? "" : sourceCode)
                    .compileOutput(compileOutput)
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    record CaseResultInput(Integer testCaseNumber,
                           Boolean passed,
                           Boolean hidden,
                           String inputSnapshot,
                           String actualOutput,
                           String expectedOutput,
                           Double executionTime,
                           Integer memoryUsed) {

        SubmissionCaseResult toCaseResult() {
            return SubmissionCaseResult.builder()
                    .testCaseNumber(testCaseNumber)
                    .passed(Boolean.TRUE.equals(passed))
                    .hidden(Boolean.TRUE.equals(hidden))
                    .inputSnapshot(inputSnapshot)
                    .actualOutput(actualOutput)
                    .expectedOutput(expectedOutput)
                    .executionTime(executionTime)
                    .memoryUsed(memoryUsed)
                    .build();
        }
    }

    record BaselineInput(String scenario,
                         String headline,
                         String summary,
                         List<String> issueTags,
                         List<String> fineGrainedTags,
                         List<String> evidenceRefs,
                         String studentHint,
                         String answerLeakRisk) {

        SubmissionAnalysisResponse toBaseline(Long submissionId) {
            return SubmissionAnalysisResponse.builder()
                    .submissionId(submissionId)
                    .sourceType("FIXTURE_BASELINE")
                    .scenario(scenario)
                    .headline(headline)
                    .summary(summary)
                    .issueTags(issueTags == null ? List.of() : issueTags)
                    .fineGrainedTags(fineGrainedTags == null ? List.of() : fineGrainedTags)
                    .evidenceRefs(evidenceRefs == null ? List.of() : evidenceRefs)
                    .studentHint(studentHint)
                    .answerLeakRisk(answerLeakRisk == null ? "LOW" : answerLeakRisk)
                    .confidence(0.7)
                    .build();
        }
    }

    private static Problem.Difficulty parseDifficulty(String value) {
        if (value == null || value.isBlank()) {
            return Problem.Difficulty.EASY;
        }
        return Problem.Difficulty.valueOf(value);
    }

    private static Submission.Verdict parseVerdict(String value) {
        if (value == null || value.isBlank()) {
            return Submission.Verdict.WRONG_ANSWER;
        }
        return Submission.Verdict.valueOf(value);
    }

}
