package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

class CoachEvalFixtureLoader {

    private static final String DEFAULT_RESOURCE = "/coach-eval-fixtures/coach-turns.json";
    private static final String SAFETY_REJECTION_RESOURCE = "/coach-eval-fixtures/coach-safety-rejection-cases.json";

    private final ObjectMapper objectMapper;

    CoachEvalFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Fixture> loadDefault() throws IOException {
        try (InputStream inputStream = CoachEvalFixtureLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture resource: " + DEFAULT_RESOURCE);
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    List<SafetyRejectionFixture> loadSafetyRejections() throws IOException {
        try (InputStream inputStream = CoachEvalFixtureLoader.class.getResourceAsStream(SAFETY_REJECTION_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture resource: " + SAFETY_REJECTION_RESOURCE);
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    record Fixture(String name,
                   String turnType,
                   Long submissionId,
                   String primaryTag,
                   String hintPolicy,
                   String verdict,
                   String scenario,
                   String headline,
                   String contextSummary,
                   List<String> evidenceRefs,
                   String studentAnswer,
                   ModelResponse safeModelResponse,
                   List<String> expectedQuestionSignals,
                   List<String> requiredEvidenceRefs,
                   List<String> forbiddenPhrases) {

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
                    .summary(headline)
                    .reportMarkdown(headline)
                    .build();
        }

        Assignment.HintPolicy toHintPolicy() {
            if (hintPolicy == null || hintPolicy.isBlank()) {
                return Assignment.HintPolicy.L2;
            }
            return Assignment.HintPolicy.valueOf(hintPolicy);
        }

        String safeModelResponseJson(ObjectMapper objectMapper) {
            try {
                return objectMapper.writeValueAsString(safeModelResponse);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to serialize coach fixture response", exception);
            }
        }

        private Submission.Verdict parseVerdict(String value) {
            if (value == null || value.isBlank()) {
                return Submission.Verdict.WRONG_ANSWER;
            }
            return Submission.Verdict.valueOf(value);
        }
    }

    record ModelResponse(String question,
                         String rationale,
                         List<String> evidenceRefs,
                         Double confidence,
                         String answerLeakRisk) {
    }

    record SafetyRejectionFixture(String name,
                                  String source,
                                  String riskCategory,
                                  String turnType,
                                  Long submissionId,
                                  String primaryTag,
                                  String hintPolicy,
                                  String verdict,
                                  String scenario,
                                  String headline,
                                  String contextSummary,
                                  List<String> evidenceRefs,
                                  String studentAnswer,
                                  ModelResponse unsafeModelResponse,
                                  String expectedFailureReason,
                                  String expectedModelAnswerLeakRisk,
                                  String requiredFallbackQuestion,
                                  List<String> forbiddenPhrases) {

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
                    .summary(headline)
                    .reportMarkdown(headline)
                    .build();
        }

        Assignment.HintPolicy toHintPolicy() {
            if (hintPolicy == null || hintPolicy.isBlank()) {
                return Assignment.HintPolicy.L2;
            }
            return Assignment.HintPolicy.valueOf(hintPolicy);
        }

        String unsafeModelResponseJson(ObjectMapper objectMapper) {
            try {
                return objectMapper.writeValueAsString(unsafeModelResponse);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to serialize coach safety fixture response", exception);
            }
        }

        private Submission.Verdict parseVerdict(String value) {
            if (value == null || value.isBlank()) {
                return Submission.Verdict.WRONG_ANSWER;
            }
            return Submission.Verdict.valueOf(value);
        }
    }
}
