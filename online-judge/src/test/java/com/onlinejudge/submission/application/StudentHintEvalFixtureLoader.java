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

class StudentHintEvalFixtureLoader {

    private static final String DEFAULT_RESOURCE = "/diagnosis-eval-fixtures/student-hint-cases.json";
    private static final String NEGATIVE_RESOURCE = "/diagnosis-eval-fixtures/student-hint-negative-cases.json";
    private static final String PUBLIC_REPLAY_RESOURCE = "/diagnosis-eval-fixtures/student-hint-public-replay-cases.json";
    private static final String PUBLIC_ATTEMPT_CHAIN_RESOURCE = "/diagnosis-eval-fixtures/student-hint-public-attempt-chain-cases.json";

    private final ObjectMapper objectMapper;

    StudentHintEvalFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Fixture> loadDefault() throws IOException {
        return loadResource(DEFAULT_RESOURCE);
    }

    List<Fixture> loadNegative() throws IOException {
        return loadResource(NEGATIVE_RESOURCE);
    }

    List<Fixture> loadPublicReplay() throws IOException {
        return loadResource(PUBLIC_REPLAY_RESOURCE);
    }

    List<Fixture> loadPublicAttemptChains() throws IOException {
        return loadResource(PUBLIC_ATTEMPT_CHAIN_RESOURCE);
    }

    private List<Fixture> loadResource(String resource) throws IOException {
        try (InputStream inputStream = StudentHintEvalFixtureLoader.class.getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture resource: " + resource);
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
                   BaselineFixture baseline,
                   ExpectedFixture expected,
                   QualityFixture quality,
                   HistoryFixture history) {

        Problem toProblem() {
            return Problem.builder()
                    .id(problem.id())
                    .title(problem.title())
                    .description(problem.description())
                    .difficulty(parseDifficulty(problem.difficulty()))
                    .timeLimit(problem.timeLimit())
                    .memoryLimit(problem.memoryLimit())
                    .knowledgePoints(nullToEmpty(problem.knowledgePoints()))
                    .algorithmStrategies(nullToEmpty(problem.algorithmStrategies()))
                    .commonMistakes(nullToEmpty(problem.commonMistakes()))
                    .boundaryTypes(nullToEmpty(problem.boundaryTypes()))
                    .build();
        }

        Submission toSubmission() {
            return Submission.builder()
                    .id(submissionId())
                    .problemId(problem.id())
                    .languageId(1)
                    .languageName(submission.languageName())
                    .verdict(parseVerdict(submission.verdict()))
                    .sourceCode(submission.sourceCode())
                    .compileOutput(submission.compileOutput())
                    .errorMessage(submission.errorMessage())
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

        SubmissionAnalysisResponse toBaseline() {
            return SubmissionAnalysisResponse.builder()
                    .submissionId(submissionId())
                    .sourceType("STUDENT_HINT_EVAL_FIXTURE")
                    .scenario(baseline.scenario())
                    .headline(baseline.analysisHeadline())
                    .summary(baseline.analysisHeadline())
                    .issueTags(nullToEmpty(baseline.originalIssueTags()))
                    .fineGrainedTags(nullToEmpty(baseline.originalFineGrainedTags()))
                    .evidenceRefs(List.of("eval:student_hint:" + caseId))
                    .studentHint(baseline.studentHint())
                    .firstFailedCase(firstFailedCase())
                    .confidence(0.72)
                    .answerLeakRisk("LOW")
                    .build();
        }

        DiagnosisEvidencePackage.HistoryEvidence toHistoryEvidence() {
            if (history == null) {
                return null;
            }
            return DiagnosisEvidencePackage.HistoryEvidence.builder()
                    .previousVerdict(history.previousVerdict())
                    .transitionSignal(history.transitionSignal())
                    .recentIssueTags(nullToEmpty(history.recentIssueTags()))
                    .recentFineGrainedTags(nullToEmpty(history.recentFineGrainedTags()))
                    .previousIssueTags(List.of())
                    .previousFineGrainedTags(List.of())
                    .repeatedIssueTag(history.repeatedIssueTag())
                    .repeatedFineGrainedTag(history.repeatedFineGrainedTag())
                    .repeatedIssueCount(history.repeatedIssueCount())
                    .repeatedFineGrainedIssueCount(history.repeatedFineGrainedIssueCount())
                    .build();
        }

        private Long submissionId() {
            return 30000L + caseId;
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
                          Integer memoryLimit,
                          List<String> knowledgePoints,
                          List<String> algorithmStrategies,
                          List<String> commonMistakes,
                          List<String> boundaryTypes) {
    }

    record SubmissionFixture(String languageName,
                             String verdict,
                             String sourceCode,
                             String compileOutput,
                             String errorMessage) {
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

    record BaselineFixture(String scenario,
                           List<String> originalIssueTags,
                           List<String> originalFineGrainedTags,
                           String analysisHeadline,
                           String studentHint) {
    }

    record ExpectedFixture(List<String> issueTags,
                           List<String> fineTags,
                           String teachingAction,
                           List<String> acceptableTeachingActions,
                           String hintLevel,
                           List<String> mustMention,
                           List<String> mustNotMention,
                           List<String> requiredEvidenceRefs,
                           List<String> forbiddenIssueTags,
                           List<String> forbiddenFineTags,
                           String trajectoryPhase,
                           Boolean needsTeacherAttention) {
    }

    record QualityFixture(String bugPattern,
                          String misconception,
                          String expectedStudentMove) {
    }

    record HistoryFixture(String previousVerdict,
                          String transitionSignal,
                          List<String> recentIssueTags,
                          List<String> recentFineGrainedTags,
                          String repeatedIssueTag,
                          String repeatedFineGrainedTag,
                          Long repeatedIssueCount,
                          Long repeatedFineGrainedIssueCount) {
    }
}
