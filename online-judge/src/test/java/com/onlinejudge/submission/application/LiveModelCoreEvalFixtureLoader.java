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

class LiveModelCoreEvalFixtureLoader {

    private static final String DEFAULT_RESOURCE = "/diagnosis-eval-fixtures/live-model-core-cases.json";

    private final ObjectMapper objectMapper;

    LiveModelCoreEvalFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Fixture> loadDefault() throws IOException {
        try (InputStream inputStream = LiveModelCoreEvalFixtureLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture resource: " + DEFAULT_RESOURCE);
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    record Fixture(String caseId,
                   String teacherExpectation,
                   ProblemFixture problem,
                   SubmissionFixture submission,
                   List<CaseResultFixture> caseResults,
                   BaselineFixture baseline,
                   List<String> expectedIssueTags,
                   List<String> expectedFineTags,
                   List<String> requiredEvidenceRefs,
                   List<String> mustMention,
                   List<String> mustNotMention,
                   QualityFixture quality) {

        Problem toProblem() {
            return problem.toProblem();
        }

        Submission toSubmission() {
            return submission.toSubmission();
        }

        List<SubmissionCaseResult> toCaseResults() {
            return caseResults == null ? List.of() : caseResults.stream()
                    .map(CaseResultFixture::toCaseResult)
                    .toList();
        }

        SubmissionAnalysisResponse toBaseline() {
            return baseline.toBaseline(submission.id());
        }
    }

    record ProblemFixture(Long id,
                          String title,
                          String description,
                          String difficulty,
                          Integer timeLimit,
                          Integer memoryLimit,
                          List<String> knowledgePoints,
                          List<String> algorithmStrategies,
                          List<String> commonMistakes,
                          List<String> boundaryTypes) {

        Problem toProblem() {
            return Problem.builder()
                    .id(id)
                    .title(title)
                    .description(description)
                    .difficulty(difficulty == null || difficulty.isBlank()
                            ? Problem.Difficulty.EASY
                            : Problem.Difficulty.valueOf(difficulty))
                    .timeLimit(timeLimit == null ? 1000 : timeLimit)
                    .memoryLimit(memoryLimit == null ? 65536 : memoryLimit)
                    .knowledgePoints(knowledgePoints == null ? List.of() : knowledgePoints)
                    .algorithmStrategies(algorithmStrategies == null ? List.of() : algorithmStrategies)
                    .commonMistakes(commonMistakes == null ? List.of() : commonMistakes)
                    .boundaryTypes(boundaryTypes == null ? List.of() : boundaryTypes)
                    .build();
        }
    }

    record SubmissionFixture(Long id,
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
                    .verdict(verdict == null || verdict.isBlank()
                            ? Submission.Verdict.WRONG_ANSWER
                            : Submission.Verdict.valueOf(verdict))
                    .sourceCode(sourceCode == null ? "" : sourceCode)
                    .compileOutput(compileOutput)
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    record CaseResultFixture(Integer testCaseNumber,
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

    record BaselineFixture(String scenario,
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
                    .sourceType("LIVE_MODEL_CORE_FIXTURE")
                    .scenario(scenario)
                    .headline(headline)
                    .summary(summary)
                    .issueTags(issueTags == null ? List.of() : issueTags)
                    .fineGrainedTags(fineGrainedTags == null ? List.of() : fineGrainedTags)
                    .evidenceRefs(evidenceRefs == null ? List.of() : evidenceRefs)
                    .studentHint(studentHint)
                    .answerLeakRisk(answerLeakRisk == null || answerLeakRisk.isBlank() ? "LOW" : answerLeakRisk)
                    .confidence(0.74)
                    .build();
        }
    }

    record QualityFixture(String bugPattern,
                          String misconception,
                          String expectedStudentMove,
                          String evalPurpose) {
    }
}
