package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import com.onlinejudge.classroom.persistence.StudentRecommendationEventRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.leaderboard.persistence.ProblemSubmissionStatsProjection;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemCatalogProjection;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionHistoryProjection;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRecommendationServiceTest {

    private final FakeStudentProfileRepository studentProfileRepository = new FakeStudentProfileRepository();
    private final FakeSubmissionRepository submissionRepository = new FakeSubmissionRepository();
    private final FakeSubmissionAnalysisRepository analysisRepository = new FakeSubmissionAnalysisRepository();
    private final FakeProblemRepository problemRepository = new FakeProblemRepository();
    private final FakeCoachPromptRepository coachPromptRepository = new FakeCoachPromptRepository();
    private final FakeStudentRecommendationEventRepository recommendationEventRepository = new FakeStudentRecommendationEventRepository();
    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StudentAbilityProfileService abilityProfileService = new StudentAbilityProfileService(
            studentProfileRepository,
            submissionRepository,
            analysisRepository,
            problemRepository,
            new AbilitySignalAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new CoachInteractionAnalyzer(coachPromptRepository, new CoachAnswerQualityAnalyzer()),
            new StudentIdentityService(),
            recommendationEventRepository,
            new CoachImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new RecurringMisconceptionAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new SelfExplanationMasteryAnalyzer(new CoachAnswerQualityAnalyzer()),
            new AiDependencyAnalyzer(),
            new MasteryGrowthAnalyzer(
                    new DiagnosisReportReader(objectMapper, taxonomy),
                    taxonomy,
                    new AbilitySignalAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy)
            ),
            new TeachingActionOrchestrator(),
            new DiagnosisReportReader(objectMapper, taxonomy)
    );
    private final StudentRecommendationEventService recommendationEventService = new StudentRecommendationEventService(
            recommendationEventRepository,
            analysisRepository,
            submissionRepository,
            org.mockito.Mockito.mock(com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository.class),
            org.mockito.Mockito.mock(com.onlinejudge.submission.persistence.SubmissionCaseResultRepository.class),
            new DiagnosisReportReader(objectMapper, taxonomy),
            objectMapper
    );
    private final StudentRecommendationService service = new StudentRecommendationService(
            abilityProfileService,
            problemRepository,
            submissionRepository,
            analysisRepository,
            new CoachInteractionAnalyzer(coachPromptRepository, new CoachAnswerQualityAnalyzer()),
            recommendationEventService,
            new PostAcTransferAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new RecurringMisconceptionAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new SelfExplanationMasteryAnalyzer(new CoachAnswerQualityAnalyzer()),
            new AiDependencyAnalyzer(),
            new MasteryGrowthAnalyzer(
                    new DiagnosisReportReader(objectMapper, taxonomy),
                    taxonomy,
                    new AbilitySignalAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy)
            ),
            new TeachingActionOrchestrator()
    );

    @Test
    void returnsOnlyTheMostRecentUnfinishedProblem() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "张三", "08"));
        problemRepository.items.put(101L, problem(101L, "旧题：数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")));
        problemRepository.items.put(102L, problem(102L, "新题：边界统计", List.of("数组"), List.of("边界漏判"), List.of("最大规模")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 1));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var response = service.recommend(1L);

        assertThat(response.getSummary()).contains("最近未通过");
        assertThat(response.getRecommendations()).extracting("type")
                .containsExactly("REDO");
        assertThat(response.getRecommendations().get(0).getProblemId()).isEqualTo(101L);
        assertThat(response.getRecommendations().get(0).getAssignmentId()).isEqualTo(7L);
        assertThat(response.getRecommendations().get(0).getReason()).contains("诊断");
        assertThat(response.getRecommendations().get(0).getExpectedCompletionSignal()).isNotBlank();
        assertThat(response.getRecommendations().get(0).getStrategy()).isEqualTo("REPAIR_SAME_PROBLEM");
    }

    @Test
    void recommendationsCarryTokensAndEventsCanTrackClickAndSubmission() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "old boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        problemRepository.items.put(102L, problem(102L, "new boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("large")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var response = service.recommend(1L);
        String token = response.getRecommendations().get(0).getRecommendationToken();
        Submission followup = submission(12L, 1L, 101L, 7L, Submission.Verdict.ACCEPTED, 1);
        analysisRepository.save(analysis(12L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        recommendationEventService.recordClick(1L, token);
        recommendationEventService.recordEnteredProblem(1L, token);
        recommendationEventService.recordSubmission(followup, token);

        assertThat(response.getRecommendations()).allSatisfy(item -> assertThat(item.getRecommendationToken()).isNotBlank());
        assertThat(recommendationEventRepository.items)
                .filteredOn(item -> StudentRecommendationEventService.EVENT_EXPOSED.equals(item.getEventType()))
                .hasSize(1);
        assertThat(recommendationEventRepository.items)
                .extracting(StudentRecommendationEvent::getEventType)
                .contains(
                        StudentRecommendationEventService.EVENT_CLICKED,
                        StudentRecommendationEventService.EVENT_ENTERED_PROBLEM,
                        StudentRecommendationEventService.EVENT_SUBMITTED
                );
        assertThat(recommendationEventRepository.items)
                .filteredOn(item -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(item.getEventType()))
                .first()
                .satisfies(item -> {
                    assertThat(item.getFollowupSubmissionId()).isEqualTo(12L);
                    assertThat(item.getAssignmentId()).isEqualTo(7L);
                    assertThat(item.getFollowupVerdict()).isEqualTo(Submission.Verdict.ACCEPTED.name());
                    assertThat(item.getFollowupFineGrainedTag()).isEqualTo("OFF_BY_ONE");
                    assertThat(item.getStrategy()).isEqualTo("REPAIR_SAME_PROBLEM");
                    assertThat(item.getLearningHypothesis()).isNull();
                    assertThat(item.getExpectedCompletionSignal()).contains("再次提交后通过");
                    assertThat(item.getFallbackAction()).isNull();
                });
    }

    @Test
    void olderRecommendationOutcomeDoesNotOverrideCurrentUnfinishedProblem() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "old boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        problemRepository.items.put(102L, problem(102L, "new boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("large")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        recommendationEventRepository.items.add(StudentRecommendationEvent.builder()
                .recommendationToken("old-rec")
                .studentProfileId(1L)
                .type("REDO")
                .problemId(101L)
                .focusAbility("循环与边界")
                .focusTags("[\"OFF_BY_ONE\"]")
                .strategy("REPAIR_SAME_PROBLEM")
                .riskLevel("MEDIUM")
                .eventType(StudentRecommendationEventService.EVENT_SUBMITTED)
                .followupSubmissionId(10L)
                .followupVerdict(Submission.Verdict.WRONG_ANSWER.name())
                .followupIssueTag("BOUNDARY_CONDITION")
                .followupFineGrainedTag("OFF_BY_ONE")
                .createdAt(LocalDateTime.of(2026, 5, 18, 9, 30))
                .build());

        var response = service.recommend(1L);

        assertThat(response.getSummary()).contains("最近未通过");
        assertThat(response.getRecommendations().get(0)).satisfies(item -> {
            assertThat(item.getType()).isEqualTo("REDO");
            assertThat(item.getStrategy()).isEqualTo("REPAIR_SAME_PROBLEM");
            assertThat(item.getProblemId()).isEqualTo(101L);
        });
    }

    @Test
    void allAttemptedProblemsPassedMovesToNewProblem() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "old boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        problemRepository.items.put(102L, problem(102L, "new boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("large")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 8));
        submissionRepository.items.add(submission(12L, 1L, 101L, 7L, Submission.Verdict.ACCEPTED, 1));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var response = service.recommend(1L);

        assertThat(response.getRecommendations()).first()
                .satisfies(item -> {
                    assertThat(item.getType()).isEqualTo("NEXT_PROBLEM");
                    assertThat(item.getStrategy()).isEqualTo("START_NEW_PROBLEM");
                    assertThat(item.getProblemId()).isEqualTo(102L);
                });
    }

    @Test
    void crossProblemSignalsDoNotOverrideLatestUnfinishedProblem() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "old boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        problemRepository.items.put(102L, problem(102L, "new boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("large")));
        problemRepository.items.put(103L, problem(103L, "other task", List.of("string"), List.of("format"), List.of("empty")));
        submissionRepository.items.add(submission(31L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 8));
        submissionRepository.items.add(submission(32L, 1L, 102L, 8L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(31L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(32L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var response = service.recommend(1L);

        assertThat(response.getRecommendations()).first()
                .satisfies(item -> {
                    assertThat(item.getStrategy()).isEqualTo("REPAIR_SAME_PROBLEM");
                    assertThat(item.getProblemId()).isEqualTo(102L);
                });
    }

    @Test
    void coachAnswersDoNotOverrideLatestUnfinishedProblem() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "old boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        problemRepository.items.put(102L, problem(102L, "new boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("large")));
        submissionRepository.items.add(submission(41L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 8));
        submissionRepository.items.add(submission(42L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(41L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(42L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        coachPromptRepository.items.add(prompt(51L, 41L, "知道了，我改一下"));
        coachPromptRepository.items.add(prompt(52L, 42L, "懂了，我试试"));

        var response = service.recommend(1L);

        assertThat(response.getRecommendations()).first()
                .satisfies(item -> {
                    assertThat(item.getStrategy()).isEqualTo("REPAIR_SAME_PROBLEM");
                    assertThat(item.getProblemId()).isEqualTo(101L);
                });
    }

    @Test
    void aiUsageSignalsDoNotOverrideLatestUnfinishedProblem() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "old boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        problemRepository.items.put(102L, problem(102L, "new boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("large")));
        submissionRepository.items.add(submission(61L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 8));
        submissionRepository.items.add(submission(62L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(61L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(62L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        coachPromptRepository.items.add(prompt(71L, 61L, "再给一个提示"));
        coachPromptRepository.items.add(prompt(72L, 62L, "我还是不会"));
        recommendationEventRepository.items.add(recommendationEvent(81L, 61L, StudentRecommendationEventService.EVENT_SUBMITTED, Submission.Verdict.WRONG_ANSWER, 1));
        recommendationEventRepository.items.add(recommendationEvent(82L, 62L, StudentRecommendationEventService.EVENT_SUBMITTED, Submission.Verdict.WRONG_ANSWER, 2));
        recommendationEventRepository.items.add(recommendationEvent(83L, null, StudentRecommendationEventService.EVENT_CLICKED, null, 3));

        var response = service.recommend(1L);

        assertThat(response.getRecommendations()).first()
                .satisfies(item -> {
                    assertThat(item.getStrategy()).isEqualTo("REPAIR_SAME_PROBLEM");
                    assertThat(item.getProblemId()).isEqualTo(101L);
                });
    }

    @Test
    void abilityTrendDoesNotOverrideLatestUnfinishedProblem() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "old boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        problemRepository.items.put(102L, problem(102L, "second boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("large")));
        problemRepository.items.put(103L, problem(103L, "third boundary task", List.of("loop"), List.of("OFF_BY_ONE"), List.of("large")));
        submissionRepository.items.add(submission(91L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 8));
        submissionRepository.items.add(submission(92L, 1L, 102L, 7L, Submission.Verdict.WRONG_ANSWER, 5));
        submissionRepository.items.add(submission(93L, 1L, 103L, 7L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(91L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(92L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(93L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]"));

        var response = service.recommend(1L);

        assertThat(response.getRecommendations()).first()
                .satisfies(item -> {
                    assertThat(item.getStrategy()).isEqualTo("REPAIR_SAME_PROBLEM");
                    assertThat(item.getProblemId()).isEqualTo(103L);
                });
    }

    @Test
    void transferVerifiedAcceptedProblemDoesNotRepeatPostAcRecommendation() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "old boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        problemRepository.items.put(102L, problem(102L, "new boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("large")));
        problemRepository.items.put(103L, problem(103L, "next task", List.of("string"), List.of("format"), List.of("empty")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 9));
        submissionRepository.items.add(submission(12L, 1L, 101L, 7L, Submission.Verdict.ACCEPTED, 8));
        submissionRepository.items.add(submission(13L, 1L, 102L, 7L, Submission.Verdict.ACCEPTED, 1));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var response = service.recommend(1L);

        assertThat(response.getRecommendations()).extracting("strategy")
                .doesNotContain("POST_AC_REFLECTION");
        assertThat(response.getRecommendations()).first().extracting("strategy", "problemId")
                .containsExactly("START_NEW_PROBLEM", 103L);
    }

    @Test
    void returnsNoActionWhenThereIsNoSafeProblemOrSubmissionTarget() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));

        var response = service.recommend(1L);

        assertThat(response.getRecommendations()).isEmpty();
        assertThat(response.getSummary()).contains("没有需要处理");
    }

    @Test
    void reviewsLatestSubmissionWhenEverythingAvailableWasCompleted() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "completed", List.of("array"), List.of("boundary"), List.of("single")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.ACCEPTED, 1));

        var response = service.recommend(1L);

        assertThat(response.getRecommendations()).singleElement().satisfies(item -> {
            assertThat(item.getType()).isEqualTo("REVIEW");
            assertThat(item.getProblemId()).isEqualTo(101L);
            assertThat(item.getAssignmentId()).isEqualTo(7L);
        });
    }

    @Test
    void submittedRecommendationEventCanBackfillAnalysisWhenDiagnosisArrivesLater() {
        studentProfileRepository.items.put(1L, student(1L, 9L, "student-a", "08"));
        problemRepository.items.put(101L, problem(101L, "old boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        problemRepository.items.put(102L, problem(102L, "new boundary task", List.of("array"), List.of("OFF_BY_ONE"), List.of("large")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var response = service.recommend(1L);
        String token = response.getRecommendations().get(0).getRecommendationToken();
        Submission followup = submission(12L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 1);

        recommendationEventService.recordSubmission(followup, token);

        StudentRecommendationEvent submitted = recommendationEventRepository.items.stream()
                .filter(item -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(item.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(submitted.getFollowupIssueTag()).isNull();
        assertThat(submitted.getFollowupFineGrainedTag()).isNull();

        SubmissionAnalysis generated = analysisRepository.save(analysis(12L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        recommendationEventService.backfillSubmissionAnalysis(followup, generated);

        assertThat(submitted.getFollowupIssueTag()).isEqualTo("BOUNDARY_CONDITION");
        assertThat(submitted.getFollowupFineGrainedTag()).isEqualTo("OFF_BY_ONE");
        assertThat(submitted.getFollowupVerdict()).isEqualTo(Submission.Verdict.WRONG_ANSWER.name());
    }

    private StudentProfile student(Long id, Long classGroupId, String displayName, String studentNo) {
        return StudentProfile.builder()
                .id(id)
                .classGroupId(classGroupId)
                .displayName(displayName)
                .studentNo(studentNo)
                .identityKey("key:" + id)
                .createdAt(LocalDateTime.of(2026, 5, 18, 9, 0))
                .lastSeenAt(LocalDateTime.of(2026, 5, 18, 9, 0))
                .build();
    }

    private Submission submission(Long id, Long studentProfileId, Long problemId, Long assignmentId, Submission.Verdict verdict, int minutesAgo) {
        return Submission.builder()
                .id(id)
                .studentProfileId(studentProfileId)
                .problemId(problemId)
                .assignmentId(assignmentId)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0).minusMinutes(minutesAgo))
                .build();
    }

    private Problem problem(Long id, String title, List<String> knowledgePoints, List<String> commonMistakes, List<String> boundaryTypes) {
        return Problem.builder()
                .id(id)
                .title(title)
                .description(title)
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .knowledgePoints(knowledgePoints)
                .commonMistakes(commonMistakes)
                .boundaryTypes(boundaryTypes)
                .build();
    }

    private SubmissionAnalysis analysis(Long submissionId, String issueTags, String fineTags) {
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("诊断")
                .summary("summary")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "issueTags": %s,
                          "fineGrainedTags": %s
                        }
                        """.formatted(issueTags, fineTags))
                .build();
    }

    private CoachPrompt prompt(Long id, Long submissionId, String answer) {
        return CoachPrompt.builder()
                .id(id)
                .assignmentId(7L)
                .studentProfileId(1L)
                .submissionId(submissionId)
                .turnIndex(1)
                .hintPolicy("L2")
                .promptType("SOCRATIC_NEXT_STEP")
                .question("请补充证据。")
                .studentAnswer(answer)
                .coachFeedback("反馈")
                .answeredAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(id))
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(id))
                .build();
    }

    private StudentRecommendationEvent recommendationEvent(Long id,
                                                           Long followupSubmissionId,
                                                           String eventType,
                                                           Submission.Verdict verdict,
                                                           int minuteOffset) {
        return StudentRecommendationEvent.builder()
                .id(id)
                .recommendationToken("rec-" + id)
                .studentProfileId(1L)
                .type("REVIEW")
                .assignmentId(7L)
                .eventType(eventType)
                .followupSubmissionId(followupSubmissionId)
                .followupVerdict(verdict == null ? null : verdict.name())
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
                .build();
    }

    private static class FakeStudentProfileRepository extends UnsupportedJpaRepository<StudentProfile, Long> implements StudentProfileRepository {
        private final Map<Long, StudentProfile> items = new LinkedHashMap<>();

        @Override
        public Optional<StudentProfile> findById(Long id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public Optional<StudentProfile> findByIdentityKey(String identityKey) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getIdentityKey(), identityKey))
                    .findFirst();
        }

        @Override
        public List<StudentProfile> findByIdentityKeyOrderByCreatedAtDesc(String identityKey) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getIdentityKey(), identityKey))
                    .sorted(Comparator.comparing(StudentProfile::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<StudentProfile> findByIdentityKeyIn(Collection<String> identityKeys) {
            return items.values()
                    .stream()
                    .filter(item -> identityKeys.contains(item.getIdentityKey()))
                    .toList();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(Long classGroupId) {
            return List.of();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdAndStudentNoIgnoreCaseOrderByCreatedAtDesc(Long classGroupId, String studentNo) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getClassGroupId(), classGroupId))
                    .filter(item -> item.getStudentNo() != null && item.getStudentNo().equalsIgnoreCase(studentNo))
                    .toList();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdAndDisplayNameIgnoreCaseOrderByCreatedAtDesc(Long classGroupId, String displayName) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getClassGroupId(), classGroupId))
                    .filter(item -> item.getDisplayName() != null && item.getDisplayName().equalsIgnoreCase(displayName))
                    .toList();
        }
    }

    private static class FakeSubmissionRepository extends UnsupportedJpaRepository<Submission, Long> implements SubmissionRepository {
        private final List<Submission> items = new ArrayList<>();

        @Override
        public List<Submission> findByProblemIdOrderBySubmittedAtDesc(Long problemId) {
            return List.of();
        }

        @Override
        public List<Submission> findByProblemIdOrderBySubmittedAtAsc(Long problemId) {
            return List.of();
        }

        @Override
        public List<Submission> findByAssignmentIdOrderBySubmittedAtDesc(Long assignmentId) {
            return List.of();
        }

        @Override
        public List<Submission> findByAssignmentIdIn(Collection<Long> assignmentIds) {
            return items.stream()
                    .filter(item -> assignmentIds.contains(item.getAssignmentId()))
                    .toList();
        }

        @Override
        public List<Submission> findByStudentProfileIdInOrderBySubmittedAtDesc(Collection<Long> studentProfileIds) {
            return items.stream()
                    .filter(item -> studentProfileIds.contains(item.getStudentProfileId()))
                    .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<Submission> findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(Long assignmentId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<Submission> findByAssignmentIdAndProblemIdAndStudentProfileIdOrderBySubmittedAtDesc(Long assignmentId, Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<Submission> findTop10ByOrderBySubmittedAtDesc() {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findHistorySummariesByProblemId(Long problemId) {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findHistorySummariesByProblemIdAndStudentProfileId(Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findHistorySummariesByAssignmentIdAndProblemIdAndStudentProfileId(Long assignmentId, Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findPublicHistorySummariesByProblemIdAndStudentProfileId(Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<SubmissionHistoryProjection> findAnonymousHistorySummariesByProblemId(Long problemId) {
            return List.of();
        }

        @Override
        public List<ProblemSubmissionStatsProjection> summarizeByProblem() {
            return List.of();
        }

        @Override
        public long deleteByProblemId(Long problemId) {
            return 0;
        }
    }

    private static class FakeSubmissionAnalysisRepository extends UnsupportedJpaRepository<SubmissionAnalysis, Long> implements SubmissionAnalysisRepository {
        private final Map<Long, SubmissionAnalysis> bySubmissionId = new LinkedHashMap<>();

        @Override
        public SubmissionAnalysis save(SubmissionAnalysis analysis) {
            bySubmissionId.put(analysis.getSubmissionId(), analysis);
            return analysis;
        }

        @Override
        public Optional<SubmissionAnalysis> findBySubmissionId(Long submissionId) {
            return Optional.ofNullable(bySubmissionId.get(submissionId));
        }

        @Override
        public List<SubmissionAnalysis> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return bySubmissionId.entrySet()
                    .stream()
                    .filter(entry -> submissionIds.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
        }

        @Override
        public long deleteBySubmissionId(Long submissionId) {
            return bySubmissionId.remove(submissionId) == null ? 0 : 1;
        }

        @Override
        public long deleteBySubmissionIdIn(Collection<Long> submissionIds) {
            long before = bySubmissionId.size();
            submissionIds.forEach(bySubmissionId::remove);
            return before - bySubmissionId.size();
        }
    }

    private static class FakeProblemRepository extends UnsupportedJpaRepository<Problem, Long> implements ProblemRepository {
        private final Map<Long, Problem> items = new LinkedHashMap<>();

        @Override
        public List<Problem> findAllById(Iterable<Long> ids) {
            Set<Long> wanted = new LinkedHashSet<>();
            ids.forEach(wanted::add);
            return items.values()
                    .stream()
                    .filter(item -> wanted.contains(item.getId()))
                    .toList();
        }

        @Override
        public List<Problem> findAllByOrderByIdAsc() {
            return List.copyOf(items.values());
        }

        @Override
        public List<ProblemCatalogProjection> findCatalogItems() {
            return List.of();
        }

        @Override
        public Optional<String> findTitleById(Long id) {
            return Optional.empty();
        }
    }

    private static class FakeCoachPromptRepository extends UnsupportedJpaRepository<CoachPrompt, Long> implements CoachPromptRepository {
        private final List<CoachPrompt> items = new ArrayList<>();

        @Override
        public Optional<CoachPrompt> findTopBySubmissionIdOrderByCreatedAtDesc(Long submissionId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .reduce((left, right) -> right);
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(Long submissionId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .toList();
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return items.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }
    }

    private static class FakeStudentRecommendationEventRepository extends UnsupportedJpaRepository<StudentRecommendationEvent, Long> implements StudentRecommendationEventRepository {
        private final List<StudentRecommendationEvent> items = new ArrayList<>();

        @Override
        public StudentRecommendationEvent save(StudentRecommendationEvent event) {
            if (event.getId() == null) {
                event.setId((long) items.size() + 1);
            }
            items.add(event);
            return event;
        }

        @Override
        public List<StudentRecommendationEvent> findByStudentProfileIdOrderByCreatedAtDesc(Long studentProfileId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getStudentProfileId(), studentProfileId))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<StudentRecommendationEvent> findTop500ByOrderByCreatedAtDesc() {
            return items.stream()
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .limit(500)
                    .toList();
        }

        @Override
        public List<StudentRecommendationEvent> findTop500ByAssignmentIdOrderByCreatedAtDesc(Long assignmentId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .limit(500)
                    .toList();
        }

        @Override
        public Optional<StudentRecommendationEvent> findTopByRecommendationTokenAndEventTypeOrderByCreatedAtDesc(String recommendationToken, String eventType) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getRecommendationToken(), recommendationToken))
                    .filter(item -> Objects.equals(item.getEventType(), eventType))
                    .max(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        }

        @Override
        public List<StudentRecommendationEvent> findByFollowupSubmissionIdAndEventTypeOrderByCreatedAtDesc(Long followupSubmissionId, String eventType) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getFollowupSubmissionId(), followupSubmissionId))
                    .filter(item -> Objects.equals(item.getEventType(), eventType))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }
    }

    private abstract static class UnsupportedJpaRepository<T, ID> {
        public List<T> findAll() { throw unsupported(); }
        public List<T> findAllById(Iterable<ID> ids) { throw unsupported(); }
        public <S extends T> S save(S entity) { throw unsupported(); }
        public <S extends T> List<S> saveAll(Iterable<S> entities) { throw unsupported(); }
        public Optional<T> findById(ID id) { throw unsupported(); }
        public boolean existsById(ID id) { throw unsupported(); }
        public long count() { throw unsupported(); }
        public void deleteById(ID id) { throw unsupported(); }
        public void delete(T entity) { throw unsupported(); }
        public void deleteAllById(Iterable<? extends ID> ids) { throw unsupported(); }
        public void deleteAll(Iterable<? extends T> entities) { throw unsupported(); }
        public void deleteAll() { throw unsupported(); }
        public void flush() { throw unsupported(); }
        public <S extends T> S saveAndFlush(S entity) { throw unsupported(); }
        public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) { throw unsupported(); }
        public void deleteAllInBatch(Iterable<T> entities) { throw unsupported(); }
        public void deleteAllByIdInBatch(Iterable<ID> ids) { throw unsupported(); }
        public void deleteAllInBatch() { throw unsupported(); }
        public T getOne(ID id) { throw unsupported(); }
        public T getById(ID id) { throw unsupported(); }
        public T getReferenceById(ID id) { throw unsupported(); }
        public List<T> findAll(Sort sort) { throw unsupported(); }
        public Page<T> findAll(Pageable pageable) { throw unsupported(); }
        public <S extends T> Optional<S> findOne(Example<S> example) { throw unsupported(); }
        public <S extends T> List<S> findAll(Example<S> example) { throw unsupported(); }
        public <S extends T> List<S> findAll(Example<S> example, Sort sort) { throw unsupported(); }
        public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) { throw unsupported(); }
        public <S extends T> long count(Example<S> example) { throw unsupported(); }
        public <S extends T> boolean exists(Example<S> example) { throw unsupported(); }
        public <S extends T, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw unsupported(); }
        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not used in this test");
        }
    }
}
