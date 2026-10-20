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

class TeacherCorrectionEvalFixtureLoader {

    private static final String DEFAULT_RESOURCE = "/diagnosis-eval-fixtures/teacher-corrections.json";

    private final ObjectMapper objectMapper;

    TeacherCorrectionEvalFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Fixture> loadDefault() throws IOException {
        try (InputStream inputStream = TeacherCorrectionEvalFixtureLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture resource: " + DEFAULT_RESOURCE);
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    record Fixture(String name,
                   String source,
                   Long correctionId,
                   Long submissionId,
                   ProblemFixture problem,
                   SubmissionFixture submission,
                   List<CaseResultFixture> caseResults,
                   AnalysisFixture analysis,
                   TeacherCorrectionFixture teacherCorrection,
                   List<String> expectedIssueTags,
                   List<String> expectedFineTags,
                   List<String> mustMention,
                   List<String> mustNotMention,
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
                    .id(submissionId)
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
                            .submissionId(submissionId)
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
                    .submissionId(submissionId)
                    .sourceType("TEACHER_CORRECTION_FIXTURE")
                    .scenario(analysis.scenario())
                    .headline(analysis.analysisHeadline())
                    .summary(teacherCorrection.teacherNote())
                    .issueTags(analysis.originalIssueTags())
                    .fineGrainedTags(analysis.originalFineGrainedTags())
                    .evidenceRefs(List.of("teacher_correction:" + correctionId))
                    .firstFailedCase(firstFailedCase())
                    .confidence(0.72)
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

    record AnalysisFixture(String scenario,
                           List<String> originalIssueTags,
                           List<String> originalFineGrainedTags,
                           String analysisHeadline) {
    }

    record TeacherCorrectionFixture(String correctedIssueTag,
                                    String correctedFineGrainedTag,
                                    String teacherNote) {
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
