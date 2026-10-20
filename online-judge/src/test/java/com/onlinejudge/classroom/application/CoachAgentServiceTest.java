package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.application.ExternalModelFailureClassifier;
import com.onlinejudge.submission.application.ModelStageFailureReason;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class CoachAgentServiceTest {

    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void coachEvalFixturesExposeStableSafetyExpectations() throws IOException {
        List<CoachEvalFixtureLoader.Fixture> fixtures = new CoachEvalFixtureLoader(objectMapper).loadDefault();

        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(3);
        assertThat(fixtures)
                .allSatisfy(fixture -> {
                    assertThat(fixture.name()).isNotBlank();
                    assertThat(fixture.turnType()).isIn("INITIAL", "INITIAL_QUESTION", "FOLLOW_UP");
                    assertThat(fixture.primaryTag()).isNotBlank();
                    assertThat(fixture.contextSummary()).isNotBlank();
                    assertThat(fixture.evidenceRefs()).isNotEmpty();
                    assertThat(fixture.requiredEvidenceRefs()).isNotEmpty();
                    assertThat(fixture.evidenceRefs()).containsAll(fixture.requiredEvidenceRefs());
                    assertThat(fixture.expectedQuestionSignals()).isNotEmpty();
                    assertThat(fixture.forbiddenPhrases()).isNotEmpty();
                    assertThat(fixture.safeModelResponse()).isNotNull();
                    assertThat(fixture.safeModelResponse().question()).isNotBlank();
                    assertThat(fixture.safeModelResponse().answerLeakRisk()).isIn("LOW", "MEDIUM");
                });
    }

    @Test
    void coachEvalFixtureResponsesPassLocalSafetyGate() throws IOException {
        List<CoachEvalFixtureLoader.Fixture> fixtures = new CoachEvalFixtureLoader(objectMapper).loadDefault();

        for (CoachEvalFixtureLoader.Fixture fixture : fixtures) {
            StubCoachAgentService service = new StubCoachAgentService(
                    objectMapper,
                    taxonomy,
                    fixture.safeModelResponseJson(objectMapper)
            );
            enableAi(service);

            CoachAgentService.CoachDraft draft = generateForFixture(service, fixture);

            assertThat(draft.getSource()).as(fixture.name()).isEqualTo("MODEL");
            assertDraftMatchesFixture(fixture, draft, true, true);
        }
    }

    @Test
    void coachSafetyRejectionFixturesExposeStableRiskCases() throws IOException {
        List<CoachEvalFixtureLoader.SafetyRejectionFixture> fixtures =
                new CoachEvalFixtureLoader(objectMapper).loadSafetyRejections();

        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(4);
        assertThat(fixtures)
                .extracting(CoachEvalFixtureLoader.SafetyRejectionFixture::riskCategory)
                .contains("COMPLETE_ANSWER", "HIDDEN_TEST", "DIRECT_FIX", "MISSING_EVIDENCE");
        assertThat(fixtures)
                .allSatisfy(fixture -> {
                    assertThat(fixture.source()).isEqualTo("coach-safety-rejection-v1");
                    assertThat(fixture.name()).isNotBlank();
                    assertThat(fixture.turnType()).isIn("INITIAL", "INITIAL_QUESTION", "FOLLOW_UP");
                    assertThat(fixture.primaryTag()).isNotBlank();
                    assertThat(fixture.contextSummary()).isNotBlank();
                    assertThat(fixture.evidenceRefs()).as(fixture.name() + " evidence refs").isNotEmpty();
                    assertThat(fixture.unsafeModelResponse()).as(fixture.name() + " unsafe response").isNotNull();
                    assertThat(fixture.unsafeModelResponse().question()).as(fixture.name() + " unsafe question").isNotBlank();
                    assertThat(fixture.expectedFailureReason()).isEqualTo("SAFETY_REJECTED");
                    assertThat(fixture.expectedModelAnswerLeakRisk()).isIn("MEDIUM", "HIGH");
                    assertThat(fixture.requiredFallbackQuestion()).isNotBlank();
                    assertThat(fixture.forbiddenPhrases()).as(fixture.name() + " forbidden phrases").isNotEmpty();
                });
    }

    @Test
    void coachSafetyRejectionFixturesConstrainLocalSafetyGate() throws IOException {
        List<CoachEvalFixtureLoader.SafetyRejectionFixture> fixtures =
                new CoachEvalFixtureLoader(objectMapper).loadSafetyRejections();

        for (CoachEvalFixtureLoader.SafetyRejectionFixture fixture : fixtures) {
            StubCoachAgentService service = new StubCoachAgentService(
                    objectMapper,
                    taxonomy,
                    fixture.unsafeModelResponseJson(objectMapper)
            );
            enableAi(service);

            CoachAgentService.CoachDraft draft = generateForSafetyFixture(service, fixture);

            assertThat(draft.getSource()).as(fixture.name()).isEqualTo("AI_UNAVAILABLE");
            assertThat(draft.getQuestion()).as(fixture.name()).contains("AI 追问暂不可用");
            assertThat(draft.getFailureReason()).as(fixture.name()).isEqualTo(fixture.expectedFailureReason());
            assertThat(riskWeight(draft.getModelAnswerLeakRisk()))
                    .as(fixture.name() + " model answer leak risk")
                    .isGreaterThanOrEqualTo(riskWeight(fixture.expectedModelAnswerLeakRisk()));
            fixture.forbiddenPhrases().forEach(phrase -> assertThat(combinedDraftText(draft).toLowerCase())
                    .as(fixture.name() + " avoids " + phrase)
                    .doesNotContain(phrase.toLowerCase()));
        }
    }

    @Test
    void rejectsFixtureLikeDraftWithoutEvidenceRefs() throws IOException {
        CoachEvalFixtureLoader.Fixture fixture = new CoachEvalFixtureLoader(objectMapper).loadDefault().get(0);
        StubCoachAgentService service = new StubCoachAgentService(objectMapper, taxonomy, """
                {
                  "question": "请先用 n=1 手推一次循环，写出循环第一次和最后一次取值。",
                  "rationale": "看起来是安全追问，但没有引用任何证据。",
                  "evidenceRefs": [],
                  "confidence": 0.86,
                  "answerLeakRisk": "LOW"
                }
                """);
        enableAi(service);

        CoachAgentService.CoachDraft draft = generateForFixture(service, fixture);

        assertThat(draft.getSource()).isEqualTo("AI_UNAVAILABLE");
        assertThat(draft.getQuestion()).contains("AI 追问暂不可用");
        assertThat(draft.getFailureReason()).isEqualTo("SAFETY_REJECTED");
    }

    @Test
    void acceptsSafeModelDraftWithKnownEvidenceRefs() {
        StubCoachAgentService service = new StubCoachAgentService(objectMapper, taxonomy, """
                {
                  "question": "请用 n=1 的最小样例手推循环第一次和最后一次取值。",
                  "rationale": "继续要求学生补证据。",
                  "evidenceRefs": ["submission:11", "tag:OFF_BY_ONE"],
                  "confidence": 0.82,
                  "answerLeakRisk": "LOW"
                }
                """);
        enableAi(service);

        CoachAgentService.CoachDraft draft = service.generateInitialQuestion(
                submission(),
                analysis(),
                "OFF_BY_ONE",
                Assignment.HintPolicy.L2,
                "上下文",
                List.of("submission:11", "tag:OFF_BY_ONE")
        );

        assertThat(draft.getSource()).isEqualTo("MODEL");
        assertThat(draft.getQuestion()).contains("n=1");
        assertThat(draft.getEvidenceRefs()).containsExactly("submission:11", "tag:OFF_BY_ONE");
    }

    @Test
    void coachPromptCarriesStandardLibraryTeachingActionAndAllowedEvidenceRefs() {
        StubCoachAgentService service = new StubCoachAgentService(objectMapper, taxonomy, """
                {
                  "question": "请先用自己的话写出这个状态表示什么，再手推一次转移。",
                  "rationale": "沿用标准库中的状态定义教学动作。",
                  "evidenceRefs": ["submission:11", "tag:DP_STATE_DESIGN"],
                  "confidence": 0.82,
                  "answerLeakRisk": "LOW"
                }
                """);
        enableAi(service);

        CoachAgentService.CoachDraft draft = service.generateInitialQuestion(
                submission(),
                analysis(),
                "DP_STATE_DESIGN",
                Assignment.HintPolicy.L2,
                "学生把 dp 状态直接写成数组，但没有说明含义。",
                List.of("submission:11", "tag:DP_STATE_DESIGN")
        );

        assertThat(draft.getSource()).isEqualTo("MODEL");
        assertThat(service.lastSystemPrompt()).contains("standardLibrary", "allowedEvidenceRefs");
        assertThat(service.lastUserPrompt()).contains(
                "standardLibrary",
                "teachingAction",
                "DEFINE_STATE",
                "DP_STATE_DESIGN",
                "ALGORITHM_STRATEGY",
                "allowedEvidenceRefs",
                "tag:DP_STATE_DESIGN"
        );
    }

    @Test
    void rejectsLeakyModelDraftAndReturnsUnavailable() {
        StubCoachAgentService service = new StubCoachAgentService(objectMapper, taxonomy, """
                {
                  "question": "答案如下，直接改成完整代码即可。",
                  "rationale": "越界输出",
                  "evidenceRefs": ["submission:11"],
                  "confidence": 0.95,
                  "answerLeakRisk": "HIGH"
                }
                """);
        enableAi(service);

        CoachAgentService.CoachDraft draft = service.generateInitialQuestion(
                submission(),
                analysis(),
                "OFF_BY_ONE",
                Assignment.HintPolicy.L2,
                "上下文",
                List.of("submission:11")
        );

        assertThat(draft.getSource()).isEqualTo("AI_UNAVAILABLE");
        assertThat(draft.getQuestion()).contains("AI 追问暂不可用");
        assertThat(draft.getFailureReason()).isEqualTo("SAFETY_REJECTED");
        assertThat(draft.getModelAnswerLeakRisk()).isEqualTo("HIGH");
    }

    @Test
    void returnsUnavailableWhenModelIsUnavailable() {
        CoachAgentService service = new CoachAgentService(objectMapper, taxonomy);

        CoachAgentService.CoachDraft draft = service.generateFollowUpQuestion(
                submission(),
                analysis(),
                "OFF_BY_ONE",
                Assignment.HintPolicy.L2,
                "上下文",
                List.of("submission:11"),
                "我会手推 n=1",
                1
        );

        assertThat(draft.getSource()).isEqualTo("AI_UNAVAILABLE");
        assertThat(draft.getQuestion()).contains("AI 追问暂不可用");
    }

    @Test
    void unavailableKeepsModelFailureReasonForEvalReports() {
        StubCoachAgentService service = new StubCoachAgentService(
                objectMapper,
                taxonomy,
                new java.io.IOException("AI API returned status 429: {\"error\":{\"code\":\"insufficient_quota\"}}")
        );
        enableAi(service);

        CoachAgentService.CoachDraft draft = service.generateInitialQuestion(
                submission(),
                analysis(),
                "OFF_BY_ONE",
                Assignment.HintPolicy.L2,
                "上下文",
                List.of("submission:11")
        );

        assertThat(draft.getSource()).isEqualTo("AI_UNAVAILABLE");
        assertThat(draft.getFailureReason()).isEqualTo("INSUFFICIENT_QUOTA");
    }

    @Test
    void providerLimitFailureDoesNotShortCircuitLaterCoachCall() {
        StubCoachAgentService service = new StubCoachAgentService(
                objectMapper,
                taxonomy,
                new IOException("AI API returned status 429: {\"error\":{\"message\":\"rate limit\"}}")
        );
        enableAi(service);
        ReflectionTestUtils.setField(service, "model", "test-model");

        CoachAgentService.CoachDraft draft = service.generateInitialQuestion(
                submission(),
                analysis(),
                "OFF_BY_ONE",
                Assignment.HintPolicy.L2,
                "上下文",
                List.of("submission:11")
        );

        assertThat(draft.getSource()).isEqualTo("AI_UNAVAILABLE");
        assertThat(draft.getFailureReason()).isEqualTo("RATE_LIMITED");
        assertThat(service.callCount()).isEqualTo(1);

        StubCoachAgentService nextService = new StubCoachAgentService(
                objectMapper,
                taxonomy,
                """
                        {
                          "question": "看第 11 行边界。",
                          "rationale": "继续请求不应被本地限流状态拦截。",
                          "evidenceRefs": ["submission:11"],
                          "confidence": 0.7,
                          "answerLeakRisk": "LOW"
                        }
                        """
        );
        enableAi(nextService);
        ReflectionTestUtils.setField(nextService, "model", "test-model");

        CoachAgentService.CoachDraft nextDraft = nextService.generateInitialQuestion(
                submission(),
                analysis(),
                "OFF_BY_ONE",
                Assignment.HintPolicy.L2,
                "上下文",
                List.of("submission:11")
        );

        assertThat(nextDraft.getSource()).isEqualTo("MODEL");
        assertThat(nextService.callCount()).isEqualTo(1);
    }

    @Test
    void chatCompletionRetriesRateLimitAndThenSucceeds() throws Exception {
        RetryingCoachAgentService service = new RetryingCoachAgentService(
                objectMapper,
                taxonomy,
                new IOException("AI API returned status 429: {\"error\":{\"message\":\"rate limit\"}}"),
                """
                {"choices":[{"message":{"content":"{\\"question\\":\\"请先说明你准备验证哪一个边界样例？\\",\\"rationale\\":\\"追问验证动作\\",\\"evidenceRefs\\":[\\"submission:11\\"],\\"confidence\\":0.7,\\"answerLeakRisk\\":\\"LOW\\"}"}}]}
                """
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.test/v1");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "streamEnabled", false);
        ReflectionTestUtils.setField(service, "streamFallbackEnabled", false);
        ReflectionTestUtils.setField(service, "retryMaxAttempts", 2);
        ReflectionTestUtils.setField(service, "retryBackoffMs", 0L);

        String content = service.chatCompletion("system", "user");

        assertThat(content).contains("question");
        assertThat(service.callCount()).isEqualTo(2);
    }

    @Test
    void chatCompletionFallsBackToBackupModelAfterProviderLimit() throws Exception {
        RetryingCoachAgentService service = new RetryingCoachAgentService(
                objectMapper,
                taxonomy,
                new IOException("AI API returned status 429: {\"error\":{\"message\":\"rate limit\"}}"),
                """
                {"choices":[{"message":{"content":"{\\"question\\":\\"先看备用模型是否能追问。\\",\\"rationale\\":\\"模型池切换\\",\\"evidenceRefs\\":[\\"submission:11\\"],\\"confidence\\":0.7,\\"answerLeakRisk\\":\\"LOW\\"}"}}]}
                """
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.test/v1");
        ReflectionTestUtils.setField(service, "model", "primary-model");
        ReflectionTestUtils.setField(service, "modelPool", "primary-model,backup-model");
        ReflectionTestUtils.setField(service, "streamEnabled", false);
        ReflectionTestUtils.setField(service, "streamFallbackEnabled", false);
        ReflectionTestUtils.setField(service, "retryMaxAttempts", 1);
        ReflectionTestUtils.setField(service, "retryBackoffMs", 0L);

        String content = service.chatCompletion("system", "user");

        assertThat(content).contains("question");
        assertThat(service.callCount()).isEqualTo(2);
    }

    @Test
    void liveModelCoachQuestionsStaySafeWhenEnabled() throws IOException {
        String apiKey = System.getenv("AI_EVAL_API_KEY");
        Assumptions.assumeTrue(Boolean.parseBoolean(valueOrDefault(System.getenv("AI_EVAL_LIVE_ENABLED"), "false")),
                "Set AI_EVAL_LIVE_ENABLED=true to run live coach eval.");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "Set AI_EVAL_API_KEY to run live coach eval.");
        CoachAgentService service = newLiveService(apiKey);
        List<CoachEvalFixtureLoader.Fixture> fixtures = new CoachEvalFixtureLoader(objectMapper).loadDefault();

        for (CoachEvalFixtureLoader.Fixture fixture : fixtures) {
            CoachAgentService.CoachDraft draft = generateForFixture(service, fixture);

            assertThat(draft.getSource()).as(fixture.name()).isEqualTo("MODEL");
            assertDraftMatchesFixture(fixture, draft, false, false);
        }
    }

    private void enableAi(CoachAgentService service) {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
    }

    private CoachAgentService newLiveService(String apiKey) {
        CoachAgentService service = new CoachAgentService(objectMapper, taxonomy);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", apiKey);
        ReflectionTestUtils.setField(service, "baseUrl", valueOrDefault(System.getenv("AI_EVAL_BASE_URL"), "https://api-inference.modelscope.cn/v1"));
        ReflectionTestUtils.setField(service, "model", valueOrDefault(System.getenv("AI_EVAL_MODEL"), "deepseek-ai/DeepSeek-V4-Pro"));
        ReflectionTestUtils.setField(service, "timeoutSeconds", 35L);
        ReflectionTestUtils.setField(service, "streamEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_STREAM_ENABLED"), "true")));
        ReflectionTestUtils.setField(service, "streamFallbackEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_STREAM_FALLBACK_ENABLED"), "true")));
        return service;
    }

    private CoachAgentService.CoachDraft generateForFixture(CoachAgentService service,
                                                            CoachEvalFixtureLoader.Fixture fixture) {
        if ("FOLLOW_UP".equals(fixture.turnType())) {
            return service.generateFollowUpQuestion(
                    fixture.toSubmission(),
                    fixture.toAnalysis(),
                    fixture.primaryTag(),
                    fixture.toHintPolicy(),
                    fixture.contextSummary(),
                    fixture.evidenceRefs(),
                    fixture.studentAnswer(),
                    1
            );
        }
        return service.generateInitialQuestion(
                fixture.toSubmission(),
                fixture.toAnalysis(),
                fixture.primaryTag(),
                fixture.toHintPolicy(),
                fixture.contextSummary(),
                fixture.evidenceRefs()
        );
    }

    private CoachAgentService.CoachDraft generateForSafetyFixture(CoachAgentService service,
                                                                  CoachEvalFixtureLoader.SafetyRejectionFixture fixture) {
        if ("FOLLOW_UP".equals(fixture.turnType())) {
            return service.generateFollowUpQuestion(
                    fixture.toSubmission(),
                    fixture.toAnalysis(),
                    fixture.primaryTag(),
                    fixture.toHintPolicy(),
                    fixture.contextSummary(),
                    fixture.evidenceRefs(),
                    fixture.studentAnswer(),
                    1
            );
        }
        return service.generateInitialQuestion(
                fixture.toSubmission(),
                fixture.toAnalysis(),
                fixture.primaryTag(),
                fixture.toHintPolicy(),
                fixture.contextSummary(),
                fixture.evidenceRefs()
        );
    }

    private void assertDraftMatchesFixture(CoachEvalFixtureLoader.Fixture fixture,
                                           CoachAgentService.CoachDraft draft,
                                           boolean requireAllSignals,
                                           boolean requireAllEvidenceRefs) {
        String combinedText = (draft.getQuestion() == null ? "" : draft.getQuestion())
                + "\n"
                + (draft.getRationale() == null ? "" : draft.getRationale());

        assertThat(draft.getQuestion()).as(fixture.name() + " question").isNotBlank();
        assertThat(draft.getAnswerLeakRisk()).as(fixture.name() + " leak risk").isIn("LOW", "MEDIUM", "UNKNOWN");
        if (requireAllEvidenceRefs) {
            assertThat(draft.getEvidenceRefs())
                    .as(fixture.name() + " evidence refs")
                    .containsAll(fixture.requiredEvidenceRefs());
        } else {
            assertThat(draft.getEvidenceRefs())
                    .as(fixture.name() + " evidence refs")
                    .containsAnyElementsOf(fixture.requiredEvidenceRefs());
        }
        assertThat(combinedText)
                .as(fixture.name() + " forbidden phrases")
                .doesNotContain(fixture.forbiddenPhrases().toArray(String[]::new));
        if (requireAllSignals) {
            assertThat(draft.getQuestion())
                    .as(fixture.name() + " question signals")
                    .contains(fixture.expectedQuestionSignals().toArray(String[]::new));
        } else {
            assertThat(fixture.expectedQuestionSignals())
                    .as(fixture.name() + " question signals")
                    .anySatisfy(signal -> assertThat(draft.getQuestion()).contains(signal));
        }
    }

    private String combinedDraftText(CoachAgentService.CoachDraft draft) {
        return String.join("\n",
                draft.getQuestion() == null ? "" : draft.getQuestion(),
                draft.getRationale() == null ? "" : draft.getRationale()
        );
    }

    private int riskWeight(String risk) {
        if ("HIGH".equalsIgnoreCase(risk)) {
            return 3;
        }
        if ("MEDIUM".equalsIgnoreCase(risk)) {
            return 2;
        }
        if ("LOW".equalsIgnoreCase(risk)) {
            return 1;
        }
        return 0;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Submission submission() {
        return Submission.builder()
                .id(11L)
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .build();
    }

    private SubmissionAnalysis analysis() {
        return SubmissionAnalysis.builder()
                .submissionId(11L)
                .scenario("WA")
                .headline("边界问题")
                .build();
    }

    private static class StubCoachAgentService extends CoachAgentService {
        private final String response;
        private final IOException exception;
        private String lastSystemPrompt;
        private String lastUserPrompt;
        private int callCount;

        private StubCoachAgentService(ObjectMapper objectMapper, DiagnosisTaxonomy taxonomy, String response) {
            super(objectMapper, taxonomy);
            this.response = response;
            this.exception = null;
        }

        private StubCoachAgentService(ObjectMapper objectMapper, DiagnosisTaxonomy taxonomy, IOException exception) {
            super(objectMapper, taxonomy);
            this.response = "";
            this.exception = exception;
        }

        @Override
        protected String chatCompletion(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
            callCount++;
            this.lastSystemPrompt = systemPrompt;
            this.lastUserPrompt = userPrompt;
            if (exception != null) {
                throw exception;
            }
            return response;
        }

        private String lastSystemPrompt() {
            return lastSystemPrompt;
        }

        private String lastUserPrompt() {
            return lastUserPrompt;
        }

        private int callCount() {
            return callCount;
        }
    }

    private static class RetryingCoachAgentService extends CoachAgentService {
        private final Queue<Object> responses = new ArrayDeque<>();
        private int callCount;

        private RetryingCoachAgentService(ObjectMapper objectMapper, DiagnosisTaxonomy taxonomy, Object... responses) {
            super(objectMapper, taxonomy);
            this.responses.addAll(List.of(responses));
        }

        @Override
        protected String sendChatCompletionRequest(HttpRequest request, boolean stream) throws IOException {
            callCount++;
            Object response = responses.poll();
            if (response instanceof IOException exception) {
                throw exception;
            }
            if (response == null) {
                throw new IOException("No stub response configured.");
            }
            return response.toString();
        }

        int callCount() {
            return callCount;
        }
    }
}
