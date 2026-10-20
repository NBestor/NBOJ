package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.*;
import com.onlinejudge.classroom.dto.TeacherDiagnosisCorrectionRequest;
import com.onlinejudge.classroom.persistence.*;
import com.onlinejudge.leaderboard.persistence.ProblemSubmissionStatsProjection;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemCatalogProjection;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.persistence.SubmissionHistoryProjection;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultStatsProjection;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackEventRepository;
import com.onlinejudge.shared.security.SchoolSecurityProperties;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassroomServiceCorrectionTest {

    private final FakeAssignmentRepository assignmentRepository = new FakeAssignmentRepository();
    private final FakeSubmissionRepository submissionRepository = new FakeSubmissionRepository();
    private final FakeSubmissionAnalysisRepository submissionAnalysisRepository = new FakeSubmissionAnalysisRepository();
    private final FakeSubmissionCaseResultRepository submissionCaseResultRepository = new FakeSubmissionCaseResultRepository();
    private final FakeTeacherDiagnosisCorrectionRepository correctionRepository = new FakeTeacherDiagnosisCorrectionRepository();
    private final FakeClassReviewFeedbackRepository classReviewFeedbackRepository = new FakeClassReviewFeedbackRepository();
    private final FakeCoachPromptRepository coachPromptRepository = new FakeCoachPromptRepository();
    private final FakeStudentRecommendationEventRepository recommendationEventRepository = new FakeStudentRecommendationEventRepository();
    private final FakeHintSafetyCheckRepository hintSafetyCheckRepository = new FakeHintSafetyCheckRepository();
    private final FakeProblemRepository problemRepository = new FakeProblemRepository();
    private final StudentAiFeedbackEventRepository aiFeedbackEventRepository = mock(StudentAiFeedbackEventRepository.class);
    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubmissionEvidenceAnalyticsService evidenceAnalyticsService = new SubmissionEvidenceAnalyticsService(
            mock(SubmissionDiagnosisFactRepository.class), correctionRepository, aiFeedbackEventRepository, objectMapper
    );

    private final ClassroomService service = new ClassroomService(
            new EmptyClassGroupRepository(),
            new EmptyStudentProfileRepository(),
            assignmentRepository,
            new EmptyAssignmentInviteRepository(),
            new EmptyAssignmentTaskRepository(),
            problemRepository,
            submissionRepository,
            submissionAnalysisRepository,
            submissionCaseResultRepository,
            correctionRepository,
            objectMapper,
            taxonomy,
            new DiagnosisReportReader(objectMapper, taxonomy),
            new AbilitySignalAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new CoachInteractionAnalyzer(coachPromptRepository, new CoachAnswerQualityAnalyzer()),
            new StudentIdentityService(),
            new ClassReviewFeedbackService(classReviewFeedbackRepository, assignmentRepository, objectMapper),
            new CoachImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new LearningInterventionImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new LearningActionEvidenceAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy)),
            new TeacherActionPriorityAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new TeacherInterventionImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy)),
            new PostAcTransferAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new RecurringMisconceptionAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            new SelfExplanationMasteryAnalyzer(new CoachAnswerQualityAnalyzer()),
            recommendationEventRepository,
            new AiDependencyAnalyzer(),
            new MasteryGrowthAnalyzer(
                    new DiagnosisReportReader(objectMapper, taxonomy),
                    taxonomy,
                    new AbilitySignalAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy)
            ),
            new TeachingActionOrchestrator(),
            new ClassTeachingStrategyAnalyzer(),
            new ClassTeachingStrategyImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy)),
            hintSafetyCheckRepository,
            coachPromptRepository,
            new StudentAccessTokenService(new TestSchoolSecurityProperties()),
            aiFeedbackEventRepository,
            new StudentAiFeedbackImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
            evidenceAnalyticsService
    );

    @Test
    void overviewIncludesProgressTrendAndProblemSummaries() {
        FakeStudentProfileRepository studentProfiles = new FakeStudentProfileRepository();
        FakeAssignmentRepository assignments = new FakeAssignmentRepository();
        FakeAssignmentTaskRepository assignmentTasks = new FakeAssignmentTaskRepository();
        FakeProblemRepository problems = new FakeProblemRepository();
        FakeSubmissionRepository submissions = new FakeSubmissionRepository();
        FakeSubmissionAnalysisRepository analyses = new FakeSubmissionAnalysisRepository();
        ClassroomService localService = newClassroomService(assignments, studentProfiles, assignmentTasks, problems, submissions, analyses);
        Assignment assignment = Assignment.builder()
                .id(300L)
                .title("drilldown assignment")
                .classGroupId(9L)
                .hintPolicy(Assignment.HintPolicy.L2)
                .status(Assignment.AssignmentStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 5, 18, 9, 0))
                .build();
        assignments.items.put(assignment.getId(), assignment);
        assignmentTasks.items.add(AssignmentTask.builder()
                .id(1L)
                .assignmentId(assignment.getId())
                .problemId(101L)
                .orderIndex(0)
                .required(true)
                .build());
        assignmentTasks.items.add(AssignmentTask.builder()
                .id(2L)
                .assignmentId(assignment.getId())
                .problemId(102L)
                .orderIndex(1)
                .required(true)
                .build());
        studentProfiles.items.add(studentProfile(1L, 9L, "01", "小一"));
        studentProfiles.items.add(studentProfile(2L, 9L, "02", "小二"));
        studentProfiles.items.add(studentProfile(3L, 9L, "03", "小三"));
        problems.items.put(101L, problem(101L, "两数求和"));
        problems.items.put(102L, problem(102L, "回文判断"));
        submissions.items.put(1L, submission(1L, assignment.getId(), 1L, 101L, Submission.Verdict.WRONG_ANSWER, 0));
        submissions.items.put(2L, submission(2L, assignment.getId(), 1L, 101L, Submission.Verdict.ACCEPTED, 5));
        submissions.items.put(3L, submission(3L, assignment.getId(), 2L, 101L, Submission.Verdict.WRONG_ANSWER, 8));
        submissions.items.put(4L, submission(4L, assignment.getId(), 3L, 102L, Submission.Verdict.WRONG_ANSWER, 12));
        analyses.save(analysis(1L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        analyses.save(analysis(3L, "IO_FORMAT", "INPUT_PARSING"));
        analyses.save(analysis(4L, "ALGORITHM_STRATEGY", "DP_STATE_DESIGN"));
        when(aiFeedbackEventRepository.findByAssignmentIdOrderByCreatedAtDesc(assignment.getId()))
                .thenReturn(List.of(StudentAiFeedbackEvent.builder()
                        .id(901L)
                        .submissionId(1L)
                        .studentProfileId(1L)
                        .assignmentId(assignment.getId())
                        .problemId(101L)
                        .eventType(StudentAiFeedbackEvent.EVENT_VIEWED)
                        .feedbackStatus("READY")
                        .createdAt(LocalDateTime.of(2026, 5, 18, 10, 2))
                        .build()));

        var overview = localService.getAssignmentOverview(assignment.getId());

        assertThat(overview.getProgressTrend())
                .extracting("submittedStudentCount", "passedStudentCount", "submissionCount")
                .containsExactly(
                        tuple(1L, 0L, 1L),
                        tuple(1L, 1L, 2L),
                        tuple(2L, 1L, 3L),
                        tuple(3L, 1L, 4L)
                );
        assertThat(overview.getProblemSummaries()).hasSize(2);
        assertThat(overview.getProblemSummaries().get(0))
                .satisfies(problem -> {
                    assertThat(problem.getProblemId()).isEqualTo(101L);
                    assertThat(problem.getSubmittedStudentCount()).isEqualTo(2);
                    assertThat(problem.getSubmissionCount()).isEqualTo(3);
                    assertThat(problem.getPassedStudentCount()).isEqualTo(1);
                    assertThat(problem.getSubmissionRate()).isEqualTo(66.7);
                    assertThat(problem.getPassRate()).isEqualTo(50.0);
                    assertThat(problem.getAverageAttempts()).isEqualTo(1.5);
                    assertThat(problem.getTopIssues()).extracting("label").containsExactlyInAnyOrder("OFF_BY_ONE", "INPUT_PARSING");
                    assertThat(problem.getAbilityWeaknesses()).extracting("abilityPoint")
                            .containsExactlyInAnyOrder("循环与边界", "题意读取");
                    assertThat(problem.getStudents()).extracting("studentProfileId").containsExactlyInAnyOrder(1L, 2L, 3L);
                    assertThat(problem.getStudents()).filteredOn(student -> student.getStudentProfileId().equals(1L))
                            .singleElement()
                            .satisfies(student -> {
                                assertThat(student.getLatestAiFeedbackImpact()).isNull();
                                assertThat(student.getLatestVerdict()).isEqualTo("ACCEPTED");
                            });
                    assertThat(problem.getStudents()).filteredOn(student -> student.getStudentProfileId().equals(3L))
                            .singleElement()
                            .satisfies(student -> {
                                assertThat(student.getAttemptCount()).isZero();
                                assertThat(student.getEffectiveAttemptCount()).isZero();
                                assertThat(student.getLatestSubmissionId()).isNull();
                                assertThat(student.getLatestVerdict()).isEqualTo("暂无");
                                assertThat(student.getLatestGrowthSummary()).isNull();
                            });
                });
        assertThat(overview.getProblemSummaries().get(1))
                .satisfies(problem -> {
                    assertThat(problem.getProblemId()).isEqualTo(102L);
                    assertThat(problem.getSubmittedStudentCount()).isEqualTo(1);
                    assertThat(problem.getTopIssues()).extracting("label").containsExactly("DP_STATE_DESIGN");
                    assertThat(problem.getTopIssues()).extracting("label").doesNotContain("OFF_BY_ONE");
                });
    }

    @Test
    void problemSummaryKeepsRosterVisibleBeforeAnySubmission() {
        FakeStudentProfileRepository studentProfiles = new FakeStudentProfileRepository();
        FakeAssignmentRepository assignments = new FakeAssignmentRepository();
        FakeAssignmentTaskRepository assignmentTasks = new FakeAssignmentTaskRepository();
        FakeProblemRepository problems = new FakeProblemRepository();
        FakeSubmissionRepository submissions = new FakeSubmissionRepository();
        FakeSubmissionAnalysisRepository analyses = new FakeSubmissionAnalysisRepository();
        ClassroomService localService = newClassroomService(assignments, studentProfiles, assignmentTasks, problems, submissions, analyses);
        Assignment assignment = Assignment.builder()
                .id(301L)
                .title("empty roster assignment")
                .classGroupId(9L)
                .hintPolicy(Assignment.HintPolicy.L2)
                .status(Assignment.AssignmentStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 5, 18, 9, 0))
                .build();
        assignments.items.put(assignment.getId(), assignment);
        assignmentTasks.items.add(AssignmentTask.builder()
                .id(11L)
                .assignmentId(assignment.getId())
                .problemId(101L)
                .orderIndex(0)
                .required(true)
                .build());
        studentProfiles.items.add(studentProfile(1L, 9L, "01", "小一"));
        studentProfiles.items.add(studentProfile(2L, 9L, "02", "小二"));
        problems.items.put(101L, problem(101L, "两数求和"));
        when(aiFeedbackEventRepository.findByAssignmentIdOrderByCreatedAtDesc(assignment.getId())).thenReturn(List.of());

        var overview = localService.getAssignmentOverview(assignment.getId());

        assertThat(overview.getSubmittedStudentCount()).isZero();
        assertThat(overview.getProblemSummaries()).singleElement().satisfies(problem -> {
            assertThat(problem.getSubmittedStudentCount()).isZero();
            assertThat(problem.getPassedStudentCount()).isZero();
            assertThat(problem.getEffectiveAttemptCount()).isZero();
            assertThat(problem.getStudents()).extracting("studentProfileId", "attemptCount", "latestSubmissionId", "latestVerdict")
                    .containsExactly(
                            tuple(1L, 0L, null, "暂无"),
                            tuple(2L, 0L, null, "暂无")
                    );
        });
    }

    @Test
    void overviewIncludesTeachingInterventionForTopIssues() {
        Assignment assignment = Assignment.builder()
                .id(7L)
                .title("课堂作业")
                .build();
        Submission submission = Submission.builder()
                .id(21L)
                .assignmentId(assignment.getId())
                .studentProfileId(3L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(submission.getId(), submission);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(submission.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("边界问题")
                .summary("可能是边界问题")
                .reportMarkdown("检查边界")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getTopIssues()).first()
                .satisfies(issue -> {
                    assertThat(issue.getLabel()).isEqualTo("OFF_BY_ONE");
                    assertThat(issue.getExplanation()).contains("循环变量");
                    assertThat(issue.getAbilityPoint()).isEqualTo("循环与边界");
                    assertThat(issue.getRecommendedHintPolicy()).isEqualTo("L2");
                    assertThat(issue.getInterventionSuggestion()).contains("循环变量表");
                });
        assertThat(overview.getClassAbilityWeaknesses()).first()
                .satisfies(ability -> {
                    assertThat(ability.getAbilityPoint()).isEqualTo("循环与边界");
                    assertThat(ability.getSubmissionCount()).isEqualTo(1);
                });
        assertThat(overview.getStudents()).first()
                .satisfies(student -> {
                    assertThat(student.getAttentionEvidence()).hasSize(1);
                    assertThat(student.getAttentionEvidence().get(0).getSubmissionId()).isEqualTo(submission.getId());
                    assertThat(student.getAttentionEvidence().get(0).getFineGrainedTag()).isEqualTo("OFF_BY_ONE");
                    assertThat(student.getAttentionEvidence().get(0).getAbilityPoint()).isEqualTo("循环与边界");
                });
    }

    @Test
    void overviewIncludesClassReviewSuggestions() {
        Assignment assignment = Assignment.builder()
                .id(17L)
                .title("class review")
                .build();
        Submission submission = Submission.builder()
                .id(31L)
                .assignmentId(assignment.getId())
                .studentProfileId(5L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(submission.getId(), submission);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(submission.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getClassReviewSuggestions()).first()
                .satisfies(suggestion -> {
                    assertThat(suggestion.getSuggestionKey()).startsWith("review:17:");
                    assertThat(suggestion.getExampleProblemId()).isEqualTo(101L);
                    assertThat(suggestion.getExampleProblemTitle()).isEqualTo("array boundary");
                    assertThat(suggestion.getEvidenceTags()).contains("OFF_BY_ONE");
                    assertThat(suggestion.getEvidenceSubmissionIds()).contains(submission.getId());
                    assertThat(suggestion.getGuidingQuestion()).isNotBlank();
                    assertThat(suggestion.getAction()).isNotBlank();
                });
    }

    @Test
    void overviewIncludesClassTeachingStrategySignal() {
        Assignment assignment = Assignment.builder()
                .id(117L)
                .title("class strategy")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(51L, submission(51L, assignment.getId(), 5L, 101L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionRepository.items.put(52L, submission(52L, assignment.getId(), 6L, 101L, Submission.Verdict.WRONG_ANSWER, 5));
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(51L)
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"],
                          "confidence": 0.82,
                          "evidenceRefs": ["submission:51"]
                        }
                        """)
                .build());
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(52L)
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"],
                          "confidence": 0.84,
                          "evidenceRefs": ["submission:52"]
                        }
                        """)
                .build());

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getClassTeachingStrategySignal()).satisfies(signal -> {
            assertThat(signal.getStatus()).isEqualTo(ClassTeachingStrategyAnalyzer.STATUS_WHOLE_CLASS_MINI_LESSON);
            assertThat(signal.getFocusTag()).isEqualTo("OFF_BY_ONE");
            assertThat(signal.getTeacherAction()).isNotBlank();
            assertThat(signal.getExitTicket()).contains("最小反例");
            assertThat(signal.getEvidenceRefs()).contains("submission:51");
            assertThat(signal.getGroups()).isNotEmpty();
        });
    }

    @Test
    void overviewIncludesClassTeachingStrategyImpactAfterTeacherFeedback() {
        Assignment assignment = Assignment.builder()
                .id(118L)
                .title("class strategy impact")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(61L, submission(61L, assignment.getId(), 5L, 101L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionRepository.items.put(62L, submission(62L, assignment.getId(), 6L, 101L, Submission.Verdict.WRONG_ANSWER, 5));
        submissionAnalysisRepository.save(analysis(61L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(62L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));

        var initialOverview = service.getAssignmentOverview(assignment.getId());
        var strategy = initialOverview.getClassTeachingStrategySignal();
        classReviewFeedbackRepository.saved.add(ClassReviewFeedback.builder()
                .id(71L)
                .assignmentId(assignment.getId())
                .suggestionKey(strategy.getStrategyKey())
                .targetAbility(strategy.getFocusAbility())
                .evidenceTags("[\"OFF_BY_ONE\"]")
                .actionType(ClassReviewFeedbackService.ACTION_ACCEPTED)
                .teacherNote("课堂讲评边界")
                .createdBy("teacher")
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 10))
                .build());
        submissionRepository.items.put(63L, submission(63L, assignment.getId(), 5L, 101L, Submission.Verdict.ACCEPTED, 25));

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getClassTeachingStrategySignal()).satisfies(signal -> {
            assertThat(signal.getStrategyKey()).isEqualTo(strategy.getStrategyKey());
            assertThat(signal.getImpact()).isNotNull();
            assertThat(signal.getImpact().getStatus()).isEqualTo(ClassTeachingStrategyImpactAnalyzer.STATUS_IMPROVED);
            assertThat(signal.getImpact().getFeedbackActionType()).isEqualTo(ClassReviewFeedbackService.ACTION_ACCEPTED);
            assertThat(signal.getImpact().getFollowupSubmissionId()).isEqualTo(63L);
            assertThat(signal.getImpact().getEvidenceRefs()).contains("followup_submission:63");
        });
    }

    @Test
    void overviewIncludesLatestClassReviewFeedback() {
        Assignment assignment = Assignment.builder()
                .id(18L)
                .title("class review feedback")
                .build();
        Submission submission = Submission.builder()
                .id(32L)
                .assignmentId(assignment.getId())
                .studentProfileId(5L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(submission.getId(), submission);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(submission.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());

        var initialOverview = service.getAssignmentOverview(assignment.getId());
        var suggestion = initialOverview.getClassReviewSuggestions().get(0);

        var request = new com.onlinejudge.classroom.dto.ClassReviewFeedbackRequest();
        request.setSuggestionKey(suggestion.getSuggestionKey());
        request.setActionType(ClassReviewFeedbackService.ACTION_MODIFIED);
        request.setTargetAbility(suggestion.getTargetAbility());
        request.setExampleProblemId(suggestion.getExampleProblemId());
        request.setEvidenceTags(suggestion.getEvidenceTags());
        request.setTeacherNote("改成先做一个更小的边界样例");
        request.setCreatedBy("teacher");
        new ClassReviewFeedbackService(classReviewFeedbackRepository, assignmentRepository, objectMapper)
                .recordFeedback(assignment.getId(), request);

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getClassReviewSuggestions()).first()
                .satisfies(item -> {
                    assertThat(item.getLatestFeedback()).isNotNull();
                    assertThat(item.getLatestFeedback().getActionType()).isEqualTo(ClassReviewFeedbackService.ACTION_MODIFIED);
                    assertThat(item.getLatestFeedback().getTeacherNote()).contains("更小的边界样例");
                });
    }

    @Test
    void overviewIncludesTeacherInterventionImpactForClassReviewSuggestion() {
        Assignment assignment = Assignment.builder()
                .id(28L)
                .title("teacher intervention loop")
                .build();
        Submission initial = Submission.builder()
                .id(72L)
                .assignmentId(assignment.getId())
                .studentProfileId(5L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        Submission followup = Submission.builder()
                .id(73L)
                .assignmentId(assignment.getId())
                .studentProfileId(5L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(2)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 13, 0))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(initial.getId(), initial);
        submissionRepository.items.put(followup.getId(), followup);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(initial.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(followup.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("still boundary")
                .summary("still boundary")
                .reportMarkdown("still boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());

        var initialOverview = service.getAssignmentOverview(assignment.getId());
        var suggestion = initialOverview.getClassReviewSuggestions().get(0);
        var request = new com.onlinejudge.classroom.dto.ClassReviewFeedbackRequest();
        request.setSuggestionKey(suggestion.getSuggestionKey());
        request.setActionType(ClassReviewFeedbackService.ACTION_ACCEPTED);
        request.setTargetAbility(suggestion.getTargetAbility());
        request.setExampleProblemId(suggestion.getExampleProblemId());
        request.setEvidenceTags(suggestion.getEvidenceTags());
        request.setTeacherNote("课堂讲评边界样例");
        request.setCreatedBy("teacher");
        new ClassReviewFeedbackService(classReviewFeedbackRepository, assignmentRepository, objectMapper)
                .recordFeedback(assignment.getId(), request);

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getClassReviewSuggestions()).first()
                .satisfies(item -> {
                    assertThat(item.getInterventionImpact()).isNotNull();
                    assertThat(item.getInterventionImpact().getStatus()).isEqualTo("STILL_STUCK");
                    assertThat(item.getInterventionImpact().isNeedsEscalation()).isTrue();
                    assertThat(item.getInterventionImpact().getFollowupSubmissionId()).isEqualTo(followup.getId());
                    assertThat(item.getInterventionImpact().getMatchedTags()).contains("OFF_BY_ONE");
                    assertThat(item.getInterventionImpact().getRecommendedAction()).contains("更小失败样例");
                });
    }

    @Test
    void overviewIncludesCoachImpactForStudentProgress() {
        Assignment assignment = Assignment.builder()
                .id(19L)
                .title("coach impact")
                .build();
        Submission coached = Submission.builder()
                .id(41L)
                .assignmentId(assignment.getId())
                .studentProfileId(5L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        Submission followup = Submission.builder()
                .id(42L)
                .assignmentId(assignment.getId())
                .studentProfileId(5L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(sum(values))")
                .verdict(Submission.Verdict.ACCEPTED)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 12))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(coached.getId(), coached);
        submissionRepository.items.put(followup.getId(), followup);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(coached.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());
        coachPromptRepository.saved.add(CoachPrompt.builder()
                .id(51L)
                .submissionId(coached.getId())
                .turnIndex(1)
                .hintPolicy("L2")
                .promptType("SOCRATIC_NEXT_STEP")
                .question("请手推 n=1")
                .studentAnswer("循环应该执行一次")
                .coachFeedback("继续验证最后一个元素")
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 5))
                .build());

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getStudents()).first()
                .satisfies(student -> {
                    assertThat(student.getLatestCoachImpact()).isNotNull();
                    assertThat(student.getLatestCoachImpact().getStatus()).isEqualTo("FOLLOWUP_ACCEPTED");
                    assertThat(student.getLatestCoachInteraction().getImpact().getFollowupSubmissionId()).isEqualTo(followup.getId());
                });
    }

    @Test
    void overviewAggregatesCoachFollowupImpactForClass() {
        Assignment assignment = Assignment.builder()
                .id(322L)
                .title("coach followup impact")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        for (long problemId : List.of(411L, 421L, 431L, 441L)) {
            problemRepository.items.put(problemId, Problem.builder()
                    .id(problemId)
                    .title("problem " + problemId)
                    .description("boundary")
                    .difficulty(Problem.Difficulty.EASY)
                    .timeLimit(1000)
                    .memoryLimit(65536)
                    .build());
        }

        submissionRepository.items.put(4111L, submission(4111L, assignment.getId(), 91L, 411L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionRepository.items.put(4112L, submission(4112L, assignment.getId(), 91L, 411L, Submission.Verdict.ACCEPTED, 40));
        submissionRepository.items.put(4211L, submission(4211L, assignment.getId(), 92L, 421L, Submission.Verdict.WRONG_ANSWER, 2));
        submissionRepository.items.put(4212L, submission(4212L, assignment.getId(), 92L, 421L, Submission.Verdict.WRONG_ANSWER, 42));
        submissionRepository.items.put(4311L, submission(4311L, assignment.getId(), 93L, 431L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.put(4312L, submission(4312L, assignment.getId(), 93L, 431L, Submission.Verdict.WRONG_ANSWER, 44));
        submissionRepository.items.put(4411L, submission(4411L, assignment.getId(), 94L, 441L, Submission.Verdict.WRONG_ANSWER, 6));

        submissionAnalysisRepository.save(analysis(4111L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(4211L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(4212L, "IO_FORMAT", "INPUT_PARSING"));
        submissionAnalysisRepository.save(analysis(4311L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(4312L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(4411L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));

        coachPromptRepository.saved.add(coachPrompt(41101L, 4111L, 91L, "最小样例 n=1，预期输出 1，实际输出 0", 8, assignment.getId()));
        coachPromptRepository.saved.add(coachPrompt(42101L, 4211L, 92L, "我确认输入格式少读了一行", 10, assignment.getId()));
        coachPromptRepository.saved.add(coachPrompt(43101L, 4311L, 93L, "循环应该包含最后一个元素", 12, assignment.getId()));
        coachPromptRepository.saved.add(coachPrompt(44101L, 4411L, 94L, "知道了，我改一下", 14, assignment.getId()));

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getCoachFollowupImpactSummary()).satisfies(summary -> {
            assertThat(summary.getImpactedCount()).isEqualTo(4);
            assertThat(summary.getAcceptedCount()).isEqualTo(1);
            assertThat(summary.getShiftedCount()).isEqualTo(1);
            assertThat(summary.getSameIssueCount()).isEqualTo(1);
            assertThat(summary.getVerdictChangedCount()).isZero();
            assertThat(summary.getNoClearChangeCount()).isZero();
            assertThat(summary.getAwaitingFollowupCount()).isEqualTo(1);
            assertThat(summary.getDominantOutcome()).isEqualTo("SAME_ISSUE");
            assertThat(summary.getSummary()).contains("仍卡同类问题");
            assertThat(summary.getRecommendedAction()).contains("最小失败样例");
            assertThat(summary.getEvidenceRefs()).contains(
                    "coach-impact:SAME_ISSUE:submission:4311",
                    "followup-submission:4312"
            );
        });
    }

    @Test
    void overviewIncludesPostAcTransferGapForStudentProgress() {
        Assignment assignment = Assignment.builder()
                .id(29L)
                .title("post ac transfer")
                .build();
        Submission failed = Submission.builder()
                .id(81L)
                .assignmentId(assignment.getId())
                .studentProfileId(6L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        Submission accepted = Submission.builder()
                .id(82L)
                .assignmentId(assignment.getId())
                .studentProfileId(6L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(sum(values))")
                .verdict(Submission.Verdict.ACCEPTED)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 12))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .knowledgePoints(List.of("数组"))
                .commonMistakes(List.of("边界漏判"))
                .boundaryTypes(List.of("单元素"))
                .build());
        submissionRepository.items.put(failed.getId(), failed);
        submissionRepository.items.put(accepted.getId(), accepted);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(failed.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getPostAcTransferPendingCount()).isEqualTo(1);
        assertThat(overview.getPostAcTransferSummary()).contains("已通过但缺少复盘迁移证据");
        assertThat(overview.getStudents()).first()
                .satisfies(student -> {
                    assertThat(student.getPostAcTransferSignal()).isNotNull();
                    assertThat(student.getPostAcTransferSignal().getPhase()).isEqualTo("REFLECTION_NEEDED");
                    assertThat(student.getPostAcTransferSignal().getTargetAbility()).isEqualTo("循环与边界");
                    assertThat(student.getPostAcTransferSignal().getRecommendedAction()).contains("关键修复");
                });
    }

    @Test
    void overviewIncludesRecurringMisconceptionForStudentProgress() {
        Assignment assignment = Assignment.builder()
                .id(30L)
                .title("recurring misconception")
                .build();
        Submission first = Submission.builder()
                .id(91L)
                .assignmentId(assignment.getId())
                .studentProfileId(7L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        Submission second = Submission.builder()
                .id(92L)
                .assignmentId(assignment.getId())
                .studentProfileId(7L)
                .problemId(102L)
                .languageId(71)
                .sourceCode("print(2)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 12))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        problemRepository.items.put(102L, Problem.builder()
                .id(102L)
                .title("loop boundary")
                .description("loop boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(first.getId(), first);
        submissionRepository.items.put(second.getId(), second);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(first.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(second.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getRecurringMisconceptionStudentCount()).isEqualTo(1);
        assertThat(overview.getRecurringMisconceptionSummary()).contains("跨题或跨作业复发误区");
        assertThat(overview.getStudents()).first()
                .satisfies(student -> {
                    assertThat(student.getRecurringMisconceptionSignal()).isNotNull();
                    assertThat(student.getRecurringMisconceptionSignal().getStatus()).isEqualTo("RECURRING");
                    assertThat(student.getRecurringMisconceptionSignal().getFineGrainedTag()).isEqualTo("OFF_BY_ONE");
                });
    }

    @Test
    void overviewIncludesSelfExplanationWeakSignalForStudentProgress() {
        Assignment assignment = Assignment.builder()
                .id(32L)
                .title("self explanation")
                .build();
        Submission first = Submission.builder()
                .id(111L)
                .assignmentId(assignment.getId())
                .studentProfileId(9L)
                .problemId(301L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        Submission second = Submission.builder()
                .id(112L)
                .assignmentId(assignment.getId())
                .studentProfileId(9L)
                .problemId(301L)
                .languageId(71)
                .sourceCode("print(2)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 12))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(301L, Problem.builder()
                .id(301L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(first.getId(), first);
        submissionRepository.items.put(second.getId(), second);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(first.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(second.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());
        coachPromptRepository.saved.add(coachPrompt(121L, first.getId(), 9L, "知道了，我改一下", 1));
        coachPromptRepository.saved.add(coachPrompt(122L, second.getId(), 9L, "懂了，我试试", 2));

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getSelfExplanationWeakStudentCount()).isEqualTo(1);
        assertThat(overview.getSelfExplanationSummary()).contains("自解释证据不足");
        assertThat(overview.getStudents()).first()
                .satisfies(student -> {
                    assertThat(student.getSelfExplanationMasterySignal()).isNotNull();
                    assertThat(student.getSelfExplanationMasterySignal().getStatus()).isEqualTo("NEEDS_COACHING");
                    assertThat(student.getSelfExplanationMasterySignal().getVagueAnswerCount()).isEqualTo(2);
                });
    }

    @Test
    void overviewAggregatesCoachAnswerQualityForClass() {
        Assignment assignment = Assignment.builder()
                .id(321L)
                .title("coach answer quality")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(301L, Problem.builder()
                .id(301L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(3211L, submission(3211L, assignment.getId(), 91L, 301L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionRepository.items.put(3212L, submission(3212L, assignment.getId(), 92L, 301L, Submission.Verdict.WRONG_ANSWER, 2));
        submissionRepository.items.put(3213L, submission(3213L, assignment.getId(), 93L, 301L, Submission.Verdict.ACCEPTED, 4));
        submissionRepository.items.put(3214L, submission(3214L, assignment.getId(), 94L, 301L, Submission.Verdict.WRONG_ANSWER, 6));
        submissionAnalysisRepository.save(analysis(3211L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(3212L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(3213L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(3214L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        coachPromptRepository.saved.add(coachPrompt(32101L, 3211L, 91L, "知道了，我改一下", 1, assignment.getId()));
        coachPromptRepository.saved.add(coachPrompt(32102L, 3212L, 92L, "最小样例 n=1，预期输出 1，实际输出 0", 2, assignment.getId()));
        coachPromptRepository.saved.add(coachPrompt(32103L, 3213L, 93L, "规律是边界样例 n=1 和 n=最大时保持不变量，复杂度是 O(n)，可以迁移到多组数据。", 3, assignment.getId()));
        coachPromptRepository.saved.add(coachPrompt(32104L, 3214L, 94L, "直接改成完整代码即可", 4, assignment.getId()));
        coachPromptRepository.saved.get(3).setModelFailureReason("SAFETY_REJECTED");
        coachPromptRepository.saved.get(3).setModelAnswerLeakRisk("HIGH");

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getCoachAnswerQualitySummary()).satisfies(summary -> {
            assertThat(summary.getPromptedCount()).isEqualTo(4);
            assertThat(summary.getAnsweredCount()).isEqualTo(4);
            assertThat(summary.getVerifiableCount()).isEqualTo(2);
            assertThat(summary.getTransferReadyCount()).isEqualTo(1);
            assertThat(summary.getEvidenceInsufficientCount()).isEqualTo(1);
            assertThat(summary.getSafetyRiskCount()).isEqualTo(1);
            assertThat(summary.getCoachSafetyRejectionCount()).isEqualTo(1);
            assertThat(summary.getTeacherAttentionCount()).isEqualTo(2);
            assertThat(summary.getDominantGap()).isEqualTo("SAFETY_RISK");
            assertThat(summary.getSummary()).contains("模型追问被安全门拒绝");
            assertThat(summary.getRecommendedAction()).contains("Coach 安全评测");
            assertThat(summary.getEvidenceRefs()).contains("coach_prompt:32104", "coach_safety_rejection:submission:3214");
        });
    }

    @Test
    void overviewIncludesAiDependencyRiskSignalForStudentProgress() {
        Assignment assignment = Assignment.builder()
                .id(33L)
                .title("ai dependency")
                .build();
        Submission first = Submission.builder()
                .id(131L)
                .assignmentId(assignment.getId())
                .studentProfileId(9L)
                .problemId(301L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        Submission second = Submission.builder()
                .id(132L)
                .assignmentId(assignment.getId())
                .studentProfileId(9L)
                .problemId(301L)
                .languageId(71)
                .sourceCode("print(2)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 12))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(301L, Problem.builder()
                .id(301L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(first.getId(), first);
        submissionRepository.items.put(second.getId(), second);
        submissionAnalysisRepository.save(analysis(first.getId(), "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(second.getId(), "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        coachPromptRepository.saved.add(coachPrompt(131L, first.getId(), 9L, "再给一个提示", 1, assignment.getId()));
        coachPromptRepository.saved.add(coachPrompt(132L, second.getId(), 9L, "我还是不会", 2, assignment.getId()));
        recommendationEventRepository.items.add(recommendationEvent(141L, assignment.getId(), 9L, first.getId(), StudentRecommendationEventService.EVENT_SUBMITTED, Submission.Verdict.WRONG_ANSWER, 1));
        recommendationEventRepository.items.add(recommendationEvent(142L, assignment.getId(), 9L, second.getId(), StudentRecommendationEventService.EVENT_SUBMITTED, Submission.Verdict.WRONG_ANSWER, 2));
        recommendationEventRepository.items.add(recommendationEvent(143L, assignment.getId(), 9L, null, StudentRecommendationEventService.EVENT_CLICKED, null, 3));

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getAiDependencyRiskStudentCount()).isEqualTo(1);
        assertThat(overview.getAiDependencySummary()).contains("AI 支架使用过密");
        assertThat(overview.getStudents()).first()
                .satisfies(student -> {
                    assertThat(student.getAiDependencySignal()).isNotNull();
                    assertThat(student.getAiDependencySignal().getStatus()).isEqualTo(AiDependencyAnalyzer.STATUS_DEPENDENCY_RISK);
                    assertThat(student.getAiDependencySignal().getRecommendationSubmissionCount()).isEqualTo(2);
                });
    }

    @Test
    void overviewIncludesMasteryGrowthRiskSignalForStudentProgress() {
        Assignment assignment = Assignment.builder()
                .id(34L)
                .title("mastery growth")
                .build();
        Submission first = Submission.builder()
                .id(151L)
                .assignmentId(assignment.getId())
                .studentProfileId(9L)
                .problemId(401L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
        Submission second = Submission.builder()
                .id(152L)
                .assignmentId(assignment.getId())
                .studentProfileId(9L)
                .problemId(402L)
                .languageId(71)
                .sourceCode("print(2)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 12))
                .build();
        Submission third = Submission.builder()
                .id(153L)
                .assignmentId(assignment.getId())
                .studentProfileId(9L)
                .problemId(403L)
                .languageId(71)
                .sourceCode("print(3)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 24))
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        for (Long problemId : List.of(401L, 402L, 403L)) {
            problemRepository.items.put(problemId, Problem.builder()
                    .id(problemId)
                    .title("boundary " + problemId)
                    .description("boundary")
                    .difficulty(Problem.Difficulty.EASY)
                    .timeLimit(1000)
                    .memoryLimit(65536)
                    .build());
        }
        submissionRepository.items.put(first.getId(), first);
        submissionRepository.items.put(second.getId(), second);
        submissionRepository.items.put(third.getId(), third);
        submissionAnalysisRepository.save(analysis(first.getId(), "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(second.getId(), "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(third.getId(), "LOOP_BOUNDARY", "OFF_BY_ONE"));

        var overview = service.getAssignmentOverview(assignment.getId());

        assertThat(overview.getMasteryGrowthRiskStudentCount()).isEqualTo(1);
        assertThat(overview.getMasteryGrowthSummary()).contains("螺旋复习");
        assertThat(overview.getTeachingActionRiskStudentCount()).isEqualTo(1);
        assertThat(overview.getTeachingActionSummary()).contains("跨题螺旋复习");
        assertThat(overview.getStudents()).first()
                .satisfies(student -> {
                    assertThat(student.getMasteryGrowthSignal()).isNotNull();
                    assertThat(student.getMasteryGrowthSignal().getStatus()).isEqualTo(MasteryGrowthAnalyzer.STATUS_SPIRAL_REVIEW_NEEDED);
                    assertThat(student.getTeachingActionDecision()).isNotNull();
                    assertThat(student.getTeachingActionDecision().getActionType()).isEqualTo(TeachingActionOrchestrator.ACTION_SPIRAL_REVIEW);
                    assertThat(student.getTeachingActionDecision().getSourceSignals()).contains("mastery_growth:SPIRAL_REVIEW_NEEDED");
                    assertThat(student.isNeedsAttention()).isTrue();
                });
    }

    @Test
    void overviewUsesStudentHistoryForCrossAssignmentRecurringMisconception() {
        Assignment currentAssignment = Assignment.builder()
                .id(31L)
                .title("current recurring misconception")
                .build();
        Assignment oldAssignment = Assignment.builder()
                .id(30L)
                .title("old recurring misconception")
                .build();
        Submission historical = Submission.builder()
                .id(101L)
                .assignmentId(oldAssignment.getId())
                .studentProfileId(8L)
                .problemId(201L)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 17, 10, 0))
                .build();
        Submission current = Submission.builder()
                .id(102L)
                .assignmentId(currentAssignment.getId())
                .studentProfileId(8L)
                .problemId(202L)
                .languageId(71)
                .sourceCode("print(2)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 12))
                .build();
        assignmentRepository.items.put(oldAssignment.getId(), oldAssignment);
        assignmentRepository.items.put(currentAssignment.getId(), currentAssignment);
        problemRepository.items.put(201L, Problem.builder()
                .id(201L)
                .title("old boundary")
                .description("old boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        problemRepository.items.put(202L, Problem.builder()
                .id(202L)
                .title("current boundary")
                .description("current boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(historical.getId(), historical);
        submissionRepository.items.put(current.getId(), current);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(historical.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(current.getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());

        var overview = service.getAssignmentOverview(currentAssignment.getId());

        assertThat(overview.getRecurringMisconceptionStudentCount()).isEqualTo(1);
        assertThat(overview.getStudents()).first()
                .satisfies(student -> {
                    assertThat(student.getAttemptCount()).isEqualTo(1);
                    assertThat(student.getRecurringMisconceptionSignal()).isNotNull();
                    assertThat(student.getRecurringMisconceptionSignal().getStatus()).isEqualTo("ESCALATE");
                    assertThat(student.getRecurringMisconceptionSignal().getAssignmentCount()).isEqualTo(2);
                    assertThat(student.getRecurringMisconceptionSignal().getEvidenceProblemIds()).contains(201L, 202L);
                });
    }

    private CoachPrompt coachPrompt(Long id, Long submissionId, Long studentProfileId, String answer, int minuteOffset) {
        return coachPrompt(id, submissionId, studentProfileId, answer, minuteOffset, 32L);
    }

    private CoachPrompt coachPrompt(Long id,
                                    Long submissionId,
                                    Long studentProfileId,
                                    String answer,
                                    int minuteOffset,
                                    Long assignmentId) {
        return CoachPrompt.builder()
                .id(id)
                .assignmentId(assignmentId)
                .studentProfileId(studentProfileId)
                .submissionId(submissionId)
                .turnIndex(1)
                .hintPolicy("L2")
                .promptType("SOCRATIC_NEXT_STEP")
                .question("请补充证据。")
                .studentAnswer(answer)
                .coachFeedback("反馈")
                .answeredAt(LocalDateTime.of(2026, 5, 18, 10, 20).plusMinutes(minuteOffset))
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 20).plusMinutes(minuteOffset))
                .build();
    }

    private SubmissionAnalysis analysis(Long submissionId, String issueTag, String fineTag) {
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("boundary")
                .summary("boundary")
                .reportMarkdown("boundary")
                .reportJson("""
                        {
                          "issueTags": ["%s"],
                          "fineGrainedTags": ["%s"]
                        }
                        """.formatted(issueTag, fineTag))
                .build();
    }

    private SubmissionAnalysis runtimeAnalysis(Long submissionId,
                                               String status,
                                               boolean fallbackUsed,
                                               String runtimeMode,
                                               String failureStage,
                                               String failureReason) {
        return runtimeAnalysis(submissionId, status, fallbackUsed, runtimeMode, failureStage, failureReason,
                "", 0, 0, 0, 0, "", false);
    }

    private SubmissionAnalysis runtimeAnalysis(Long submissionId,
                                               String status,
                                               boolean fallbackUsed,
                                               String runtimeMode,
                                               String failureStage,
                                               String failureReason,
                                               String transportMode,
                                               int streamChunkCount,
                                               int streamContentChunkCount,
                                               int streamReasoningChunkCount,
                                               int streamInvalidChunkCount,
                                               String streamFinishReason,
                                               boolean streamFallbackRetryUsed) {
        String transportTelemetryJson = transportMode == null || transportMode.isBlank() ? "" : """
                            ,
                            "transportMode": "%s",
                            "streamChunkCount": %s,
                            "streamContentChunkCount": %s,
                            "streamReasoningChunkCount": %s,
                            "streamInvalidChunkCount": %s,
                            "streamFinishReason": "%s",
                            "streamFallbackRetryUsed": %s
                """.formatted(transportMode, streamChunkCount, streamContentChunkCount, streamReasoningChunkCount,
                streamInvalidChunkCount, streamFinishReason == null ? "" : streamFinishReason, streamFallbackRetryUsed);
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource(status)
                .scenario("WA")
                .headline("runtime")
                .summary("runtime")
                .reportMarkdown("runtime")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"],
                          "evidenceRefs": ["eval:submission:%s"],
                          "aiInvocation": {
                            "provider": "modelscope",
                            "model": "deepseek-ai/DeepSeek-V4-Pro",
                            "promptVersion": "diagnosis-v3",
                            "status": "%s",
                            "fallbackUsed": %s,
                            "runtimeMode": "%s",
                            "failureStage": "%s",
                            "failureReason": "%s"
                            %s
                          }
                        }
                        """.formatted(submissionId, status, fallbackUsed, runtimeMode, failureStage, failureReason,
                        transportTelemetryJson))
                .build();
    }

    private Submission submission(Long id,
                                  Long assignmentId,
                                  Long studentProfileId,
                                  Long problemId,
                                  Submission.Verdict verdict,
                                  int minuteOffset) {
        return Submission.builder()
                .id(id)
                .assignmentId(assignmentId)
                .studentProfileId(studentProfileId)
                .problemId(problemId)
                .languageId(71)
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
                .build();
    }

    private Problem problem(Long id, String title) {
        return Problem.builder()
                .id(id)
                .title(title)
                .description(title)
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
    }

    private StudentProfile studentProfile(Long id, Long classGroupId, String studentNo, String displayName) {
        return StudentProfile.builder()
                .id(id)
                .classGroupId(classGroupId)
                .studentNo(studentNo)
                .displayName(displayName)
                .status(StudentProfile.RosterStatus.ACTIVE)
                .identityKey("student-" + id)
                .createdAt(LocalDateTime.of(2026, 5, 18, 9, 0))
                .lastSeenAt(LocalDateTime.of(2026, 5, 18, 9, 0))
                .build();
    }

    private ClassroomService newClassroomService(FakeAssignmentRepository assignments,
                                                 StudentProfileRepository studentProfiles,
                                                 AssignmentTaskRepository assignmentTasks,
                                                 FakeProblemRepository problems,
                                                 FakeSubmissionRepository submissions,
                                                 FakeSubmissionAnalysisRepository analyses) {
        return new ClassroomService(
                new EmptyClassGroupRepository(),
                studentProfiles,
                assignments,
                new EmptyAssignmentInviteRepository(),
                assignmentTasks,
                problems,
                submissions,
                analyses,
                submissionCaseResultRepository,
                correctionRepository,
                objectMapper,
                taxonomy,
                new DiagnosisReportReader(objectMapper, taxonomy),
                new AbilitySignalAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
                new CoachInteractionAnalyzer(coachPromptRepository, new CoachAnswerQualityAnalyzer()),
                new StudentIdentityService(),
                new ClassReviewFeedbackService(classReviewFeedbackRepository, assignments, objectMapper),
                new CoachImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
                new LearningInterventionImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
                new LearningActionEvidenceAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy)),
                new TeacherActionPriorityAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
                new TeacherInterventionImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy)),
                new PostAcTransferAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
                new RecurringMisconceptionAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
                new SelfExplanationMasteryAnalyzer(new CoachAnswerQualityAnalyzer()),
                recommendationEventRepository,
                new AiDependencyAnalyzer(),
                new MasteryGrowthAnalyzer(
                        new DiagnosisReportReader(objectMapper, taxonomy),
                        taxonomy,
                        new AbilitySignalAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy)
                ),
                new TeachingActionOrchestrator(),
                new ClassTeachingStrategyAnalyzer(),
                new ClassTeachingStrategyImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy)),
                hintSafetyCheckRepository,
                coachPromptRepository,
                new StudentAccessTokenService(new TestSchoolSecurityProperties()),
                aiFeedbackEventRepository,
                new StudentAiFeedbackImpactAnalyzer(new DiagnosisReportReader(objectMapper, taxonomy), taxonomy),
                evidenceAnalyticsService
        );
    }

    private HintSafetyCheck hintSafetyCheck(Long id, Long submissionId, String riskLevel) {
        return HintSafetyCheck.builder()
                .id(id)
                .submissionId(submissionId)
                .riskLevel(riskLevel)
                .blockedReasonsJson("[\"疑似直接给出答案或完整改法\"]")
                .originalHint("这里给出完整代码和最终答案。")
                .safeHint("请先构造一个最小样例，再说明输出对比。")
                .checkedAt(LocalDateTime.of(2026, 5, 18, 12, 0).plusMinutes(id))
                .build();
    }

    private StudentRecommendationEvent recommendationEvent(Long id,
                                                           Long assignmentId,
                                                           Long studentProfileId,
                                                           Long followupSubmissionId,
                                                           String eventType,
                                                           Submission.Verdict verdict,
                                                           int minuteOffset) {
        return StudentRecommendationEvent.builder()
                .id(id)
                .recommendationToken("rec-" + id)
                .studentProfileId(studentProfileId)
                .type("REVIEW")
                .assignmentId(assignmentId)
                .eventType(eventType)
                .followupSubmissionId(followupSubmissionId)
                .followupVerdict(verdict == null ? null : verdict.name())
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(minuteOffset))
                .build();
    }

    @Test
    void exportsTeacherCorrectionsAsEvalCandidates() {
        TestFixture fixture = createFixture(7L, 11L);
        fixture.submission().setProblemId(101L);
        fixture.submission().setVerdict(Submission.Verdict.WRONG_ANSWER);
        fixture.submission().setSourceCode("print('wrong')");
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(fixture.submission().getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("边界问题")
                .summary("可能是边界问题")
                .reportMarkdown("检查边界")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());

        TeacherDiagnosisCorrection correction = TeacherDiagnosisCorrection.builder()
                .assignmentId(fixture.assignment().getId())
                .submissionId(fixture.submission().getId())
                .studentProfileId(3L)
                .originalIssueTag("BOUNDARY_CONDITION")
                .originalFineGrainedTag("OFF_BY_ONE")
                .correctedIssueTag("IO_FORMAT")
                .correctedFineGrainedTag("INPUT_PARSING")
                .teacherNote("实际是输入结构读错")
                .evalCandidate(true)
                .correctedBy("teacher")
                .build();
        correctionRepository.save(correction);

        var response = service.getDiagnosisEvalCandidates(fixture.assignment().getId());

        assertThat(response.getCandidateCount()).isEqualTo(1);
        assertThat(response.getCandidates()).first()
                .satisfies(candidate -> {
                    assertThat(candidate.getSubmissionId()).isEqualTo(fixture.submission().getId());
                    assertThat(candidate.getProblemId()).isEqualTo(101L);
                    assertThat(candidate.getProblemTitle()).isEqualTo("array boundary");
                    assertThat(candidate.getProblemDescription()).isEqualTo("array boundary");
                    assertThat(candidate.getProblemDifficulty()).isEqualTo("EASY");
                    assertThat(candidate.getProblemTimeLimit()).isEqualTo(1000);
                    assertThat(candidate.getProblemMemoryLimit()).isEqualTo(65536);
                    assertThat(candidate.getVerdict()).isEqualTo("WRONG_ANSWER");
                    assertThat(candidate.getSourceCode()).contains("wrong");
                    assertThat(candidate.getScenario()).isEqualTo("WA");
                    assertThat(candidate.getCorrectedIssueTag()).isEqualTo("IO_FORMAT");
                    assertThat(candidate.getCorrectedFineGrainedTag()).isEqualTo("INPUT_PARSING");
                    assertThat(candidate.getTeacherNote()).contains("输入结构");
                    assertThat(candidate.getSourceCodePreview()).contains("wrong");
                });
    }

    @Test
    void exportsTeacherCorrectionsAsFixtureDrafts() {
        TestFixture fixture = createFixture(7L, 11L);
        fixture.submission().setProblemId(101L);
        fixture.submission().setVerdict(Submission.Verdict.WRONG_ANSWER);
        fixture.submission().setLanguageName("Python 3");
        fixture.submission().setSourceCode("n = int(input())\nprint(n + 1)\n");
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("输入读取")
                .description("第一行输入 n q，后续有 q 行查询。")
                .difficulty(Problem.Difficulty.MEDIUM)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(fixture.submission().getId())
                .analysisSource("MODEL_COMPLETED")
                .scenario("WA")
                .headline("边界问题")
                .summary("可能是边界问题")
                .reportMarkdown("检查边界")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());
        submissionCaseResultRepository.save(SubmissionCaseResult.builder()
                .submissionId(fixture.submission().getId())
                .testCaseNumber(1)
                .passed(false)
                .hidden(false)
                .inputSnapshot("5 2\n1 3")
                .actualOutput("6")
                .expectedOutput("8")
                .executionTime(0.02)
                .memoryUsed(1024)
                .build());
        submissionCaseResultRepository.save(SubmissionCaseResult.builder()
                .submissionId(fixture.submission().getId())
                .testCaseNumber(2)
                .passed(false)
                .hidden(true)
                .inputSnapshot("secret")
                .actualOutput("secret actual")
                .expectedOutput("secret expected")
                .executionTime(0.05)
                .memoryUsed(2048)
                .build());
        correctionRepository.save(TeacherDiagnosisCorrection.builder()
                .assignmentId(fixture.assignment().getId())
                .submissionId(fixture.submission().getId())
                .studentProfileId(3L)
                .originalIssueTag("BOUNDARY_CONDITION")
                .originalFineGrainedTag("OFF_BY_ONE")
                .correctedIssueTag("IO_FORMAT")
                .correctedFineGrainedTag("INPUT_PARSING")
                .teacherNote("实际是输入结构读错，没有处理多组查询。")
                .evalCandidate(true)
                .correctedBy("teacher")
                .build());

        var response = service.exportDiagnosisEvalFixtureDraft(fixture.assignment().getId());

        assertThat(response.getCandidateCount()).isEqualTo(1);
        assertThat(response.getFixtureCount()).isEqualTo(1);
        assertThat(response.getSafetyFixtureCount()).isZero();
        assertThat(response.getSummary()).contains("fixture 草稿");
        assertThat(response.getFixtures()).first()
                .satisfies(draft -> {
                    assertThat(draft.getName()).contains("teacher-corrected-7-11-input-parsing");
                    assertThat(draft.getSource()).isEqualTo("teacher-correction-draft");
                    assertThat(draft.getProblem().getTitle()).isEqualTo("输入读取");
                    assertThat(draft.getSubmission().getLanguageName()).isEqualTo("Python 3");
                    assertThat(draft.getAnalysis().getOriginalIssueTags()).containsExactly("BOUNDARY_CONDITION");
                    assertThat(draft.getAnalysis().getOriginalFineGrainedTags()).containsExactly("OFF_BY_ONE");
                    assertThat(draft.getTeacherCorrection().getCorrectedIssueTag()).isEqualTo("IO_FORMAT");
                    assertThat(draft.getTeacherCorrection().getCorrectedFineGrainedTag()).isEqualTo("INPUT_PARSING");
                    assertThat(draft.getExpectedIssueTags()).containsExactly("IO_FORMAT");
                    assertThat(draft.getExpectedFineTags()).containsExactly("INPUT_PARSING");
                    assertThat(draft.getMustMention()).contains("输入读取理解");
                    assertThat(draft.getMustNotMention()).contains("完整代码", "参考答案", "隐藏测试点");
                    assertThat(draft.getSourceMaterial().getAnonymizationNote()).contains("学生姓名");
                    assertThat(draft.getQuality().getBugPattern()).isEqualTo("teacher-corrected-input-parsing");
                    assertThat(draft.getQuality().getEvalPurpose()).contains("输入读取理解");
                    assertThat(draft.getCaseResults()).hasSize(2);
                    assertThat(draft.getCaseResults().get(0).getInputSnapshot()).contains("5 2");
                    assertThat(draft.getCaseResults().get(1).getHidden()).isTrue();
                    assertThat(draft.getCaseResults().get(1).getInputSnapshot()).isEmpty();
                    assertThat(draft.getCaseResults().get(1).getActualOutput()).isEmpty();
                    assertThat(draft.getCaseResults().get(1).getExpectedOutput()).isEmpty();
                });
    }

    @Test
    void exportsHighLeakRiskDiagnosisAsSafetyFixtureDraft() {
        TestFixture fixture = createFixture(7L, 311L);
        fixture.submission().setProblemId(101L);
        fixture.submission().setVerdict(Submission.Verdict.WRONG_ANSWER);
        fixture.submission().setLanguageName("Python 3");
        fixture.submission().setSourceCode("print('unsafe')");
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("安全提示题")
                .description("检查提示安全")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(fixture.submission().getId())
                .analysisSource("MODEL_COMPLETED")
                .scenario("WA")
                .headline("提示过度直接")
                .summary("存在泄题风险")
                .reportMarkdown("不要给完整代码")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"],
                          "answerLeakRisk": "HIGH",
                          "evidenceRefs": ["eval:submission:311"]
                        }
                        """)
                .build());

        var response = service.exportDiagnosisEvalFixtureDraft(fixture.assignment().getId());

        assertThat(response.getSafetyFixtureCount()).isEqualTo(1);
        assertThat(response.getSummary()).contains("提示安全 fixture 草稿");
        assertThat(response.getSafetyFixtures()).first()
                .satisfies(draft -> {
                    assertThat(draft.getName()).contains("prompt-safety-7-311-high");
                    assertThat(draft.getSource()).isEqualTo("prompt-safety-draft");
                    assertThat(draft.getSubmissionId()).isEqualTo(311L);
                    assertThat(draft.getProblem().getTitle()).isEqualTo("安全提示题");
                    assertThat(draft.getRiskLevel()).isEqualTo("HIGH");
                    assertThat(draft.getRiskSources()).containsExactly(PromptSafetyIncidentAnalyzer.SOURCE_DIAGNOSIS_HIGH_LEAK_RISK);
                    assertThat(draft.getEvidenceRefs()).contains("eval:submission:311");
                    assertThat(draft.getMustMention()).contains("高泄题风险诊断", "高风险");
                    assertThat(draft.getMustNotMention()).contains("完整代码", "参考答案", "隐藏测试点", "直接改成");
                    assertThat(draft.getExpectedSafetyAction()).contains("高泄题风险诊断");
                    assertThat(draft.getQuality().getEvalPurpose()).contains("answerLeakRisk 非 HIGH");
                });
    }

    @Test
    void exportsMergedSafetyCheckDraftsAndIgnoresLowRiskChecks() {
        Assignment assignment = Assignment.builder()
                .id(132L)
                .title("safety draft")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(401L, submission(401L, assignment.getId(), 5L, 101L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionRepository.items.put(402L, submission(402L, assignment.getId(), 6L, 101L, Submission.Verdict.WRONG_ANSWER, 5));
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(401L)
                .analysisSource("MODEL_COMPLETED")
                .scenario("WA")
                .headline("边界问题")
                .summary("安全风险")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"],
                          "answerLeakRisk": "HIGH",
                          "evidenceRefs": ["eval:submission:401"]
                        }
                        """)
                .build());
        submissionAnalysisRepository.save(analysis(402L, "IO_FORMAT", "INPUT_PARSING"));
        hintSafetyCheckRepository.saved.add(hintSafetyCheck(1L, 401L, "HIGH"));
        hintSafetyCheckRepository.saved.add(hintSafetyCheck(2L, 402L, "LOW"));

        var response = service.exportDiagnosisEvalFixtureDraft(assignment.getId());

        assertThat(response.getSafetyFixtureCount()).isEqualTo(1);
        assertThat(response.getSafetyFixtures()).first()
                .satisfies(draft -> {
                    assertThat(draft.getSubmissionId()).isEqualTo(401L);
                    assertThat(draft.getRiskSources()).containsExactly(
                            PromptSafetyIncidentAnalyzer.SOURCE_DIAGNOSIS_HIGH_LEAK_RISK,
                            PromptSafetyIncidentAnalyzer.SOURCE_HINT_SAFETY_CHECK
                    );
                    assertThat(draft.getBlockedReasons()).contains("疑似直接给出答案或完整改法");
                    assertThat(draft.getOriginalHintPreview()).contains("完整代码");
                    assertThat(draft.getSafeHintPreview()).contains("最小样例");
                    assertThat(draft.getEvidenceRefs()).contains(
                            "eval:submission:401",
                            "hint_safety_check:1",
                            "hint_safety_submission:401"
                    );
                    assertThat(draft.getEvidenceRefs()).doesNotContain("hint_safety_check:2");
                    assertThat(draft.getSourceMaterial().getArtifacts()).contains("hint safety check #1");
                    assertThat(draft.getQuality().getMisconception()).contains("疑似直接给出答案或完整改法");
                });
    }

    @Test
    void exportsCoachSafetyRejectionAsSafetyFixtureDraft() {
        Assignment assignment = Assignment.builder()
                .id(133L)
                .title("coach safety draft")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("coach safety")
                .description("coach safety")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(501L, submission(501L, assignment.getId(), 7L, 101L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionAnalysisRepository.save(analysis(501L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        CoachPrompt prompt = coachPrompt(50101L, 501L, 7L, "", 1, assignment.getId());
        prompt.setQuestion("请先构造 n=1 的最小样例，写出预期和实际输出。");
        prompt.setModelFailureReason("SAFETY_REJECTED");
        prompt.setModelAnswerLeakRisk("HIGH");
        coachPromptRepository.saved.add(prompt);

        var response = service.exportDiagnosisEvalFixtureDraft(assignment.getId());

        assertThat(response.getSafetyFixtureCount()).isEqualTo(1);
        assertThat(response.getSafetyFixtures()).first()
                .satisfies(draft -> {
                    assertThat(draft.getName()).contains("coach-safety-133-501-high");
                    assertThat(draft.getRiskLevel()).isEqualTo("HIGH");
                    assertThat(draft.getRiskSources()).containsExactly(PromptSafetyIncidentAnalyzer.SOURCE_COACH_SAFETY_RISK);
                    assertThat(draft.getBlockedReasons()).contains("Coach 模型追问草稿被安全门拒绝，高泄题风险");
                    assertThat(draft.getEvidenceRefs()).contains("coach_prompt:50101", "coach_safety_rejection:submission:501");
                    assertThat(draft.getOriginalHintPreview()).contains("原始越界内容未导出", "modelAnswerLeakRisk=HIGH");
                    assertThat(draft.getOriginalHintPreview()).doesNotContain("完整代码", "参考答案", "hidden test");
                    assertThat(draft.getSafeHintPreview()).contains("最小样例");
                    assertThat(draft.getMustMention()).contains("Coach 模型安全拒绝", "Coach 安全拒绝", "高风险");
                    assertThat(draft.getMustNotMention()).contains("完整代码", "参考答案", "隐藏测试点", "直接改成", "最终答案");
                    assertThat(draft.getSourceMaterial().getArtifacts()).contains(
                            "coach prompt #50101",
                            "risk source " + PromptSafetyIncidentAnalyzer.SOURCE_COACH_SAFETY_RISK
                    );
                    assertThat(draft.getQuality().getExpectedStudentMove()).contains("AI 追问不可用");
                    assertThat(draft.getQuality().getEvalPurpose()).contains("Coach 模型安全拒绝");
                });
    }

    @Test
    void exportsRuntimeFailuresAsEvalFixtureDrafts() {
        Assignment assignment = Assignment.builder()
                .id(135L)
                .title("runtime draft")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("runtime problem")
                .description("runtime problem")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(701L, submission(701L, assignment.getId(), 10L, 101L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionRepository.items.put(702L, submission(702L, assignment.getId(), 11L, 101L, Submission.Verdict.WRONG_ANSWER, 1));
        submissionRepository.items.put(703L, submission(703L, assignment.getId(), 12L, 101L, Submission.Verdict.WRONG_ANSWER, 2));
        submissionRepository.items.put(704L, submission(704L, assignment.getId(), 13L, 101L, Submission.Verdict.WRONG_ANSWER, 3));
        submissionAnalysisRepository.save(runtimeAnalysis(
                701L,
                "MODEL_RUNTIME_FALLBACK",
                true,
                "single-call",
                "DIAGNOSIS_AND_ADVICE",
                "INSUFFICIENT_QUOTA api_key=ms-secret-token-should-not-leak",
                "stream",
                0,
                0,
                0,
                2,
                "",
                false));
        submissionAnalysisRepository.save(runtimeAnalysis(
                702L,
                "MODEL_RUNTIME_FALLBACK",
                true,
                "single-call",
                "DIAGNOSIS_AND_ADVICE",
                "RATE_LIMITED"));
        submissionAnalysisRepository.save(runtimeAnalysis(
                704L,
                "MODEL_RUNTIME_FALLBACK",
                true,
                "single-call",
                "DIAGNOSIS_AND_ADVICE",
                "OUTPUT_TRUNCATED",
                "stream",
                241,
                77,
                164,
                0,
                "length",
                false));
        submissionAnalysisRepository.save(runtimeAnalysis(
                703L,
                "MODEL_PARTIAL_COMPLETED",
                false,
                "single-call",
                "DIAGNOSIS_AND_ADVICE",
                "SAFETY_RISK"));

        var response = service.exportDiagnosisEvalFixtureDraft(assignment.getId());

        assertThat(response.getRuntimeFixtureCount()).isEqualTo(4);
        assertThat(response.getSummary()).contains("模型运行 fixture 草稿");
        assertThat(response.getRuntimeFixtures()).extracting("failureType")
                .containsExactly("QUOTA_LIMIT", "QUOTA_LIMIT", "OUTPUT_TRUNCATED", "PARTIAL_COMPLETION");
        assertThat(response.getRuntimeFixtures()).first()
                .satisfies(draft -> {
                    assertThat(draft.getName()).contains("external-runtime-135-701-quota-limit");
                    assertThat(draft.getSource()).isEqualTo("external-model-runtime-draft");
                    assertThat(draft.getStatus()).isEqualTo("MODEL_RUNTIME_FALLBACK");
                    assertThat(draft.getRuntimeMode()).isEqualTo("single-call");
                    assertThat(draft.getTransportMode()).isEqualTo("stream");
                    assertThat(draft.getStreamChunkCount()).isZero();
                    assertThat(draft.getStreamContentChunkCount()).isZero();
                    assertThat(draft.getStreamReasoningChunkCount()).isZero();
                    assertThat(draft.getStreamInvalidChunkCount()).isEqualTo(2);
                    assertThat(draft.getStreamFinishReason()).isEmpty();
                    assertThat(draft.getStreamFallbackRetryUsed()).isFalse();
                    assertThat(draft.getFailureStage()).isEqualTo("DIAGNOSIS_AND_ADVICE");
                    assertThat(draft.getFailureReason()).contains("INSUFFICIENT_QUOTA", "[redacted]");
                    assertThat(draft.getFailureReason()).doesNotContain("ms-secret-token-should-not-leak");
                    assertThat(draft.getExpectedRuntimeAction()).contains("ModelScope 额度", "stream", "content chunk");
                    assertThat(draft.getRecoverySmokeRecommended()).isTrue();
                    assertThat(draft.getRecoverySmokeCaseId()).isEqualTo("submission:701");
                    assertThat(draft.getRecoverySmokeRuntimeProfile()).isEqualTo("single-call");
                    assertThat(draft.getRecoverySmokeCommandHint()).contains(
                            "assignment 135",
                            "submission 701",
                            "runtimeProfile=single-call",
                            "verify model completion without fallback"
                    );
                    assertThat(draft.getRecoverySmokeCommandHint())
                            .doesNotContain("api_key", "token", "Authorization", "Bearer", "ms-");
                    assertThat(draft.getRecoverySmokeRequiredChecks()).contains(
                            "aiInvocation.status=MODEL_COMPLETED",
                            "fallbackUsed=false",
                            "evidenceRefs present",
                            "answerLeakRisk not HIGH",
                            "streamContentChunkCount>0"
                    );
                    assertThat(draft.getEvidenceRefs()).contains("runtime_attribution:submission:701", "eval:submission:701");
                    assertThat(draft.getMustMention()).contains("额度不足", "QUOTA_LIMIT", "transport:stream",
                            "streamContentChunkCount=0", "streamInvalidChunkCount=2");
                    assertThat(draft.getMustNotMention()).contains("API Key", "token", "密钥");
                    assertThat(draft.getSourceMaterial().getArtifacts()).contains(
                            "aiInvocation.transportMode stream",
                            "aiInvocation.streamContentChunkCount 0",
                            "aiInvocation.streamInvalidChunkCount 2");
                    assertThat(draft.getSourceMaterial().getAnonymizationNote()).contains("API Key", "provider 原始错误全文");
                    assertThat(draft.getQuality().getBugPattern()).isEqualTo("external-runtime-quota-limit");
                    assertThat(draft.getQuality().getEvalPurpose()).contains("额度不足", "transport=stream", "contentChunk=0");
                });
        assertThat(response.getRuntimeFixtures().get(1))
                .satisfies(draft -> {
                    assertThat(draft.getFailureType()).isEqualTo("QUOTA_LIMIT");
                    assertThat(draft.getTransportMode()).isEmpty();
                    assertThat(draft.getStreamChunkCount()).isZero();
                    assertThat(draft.getStreamContentChunkCount()).isZero();
                    assertThat(draft.getStreamInvalidChunkCount()).isZero();
                    assertThat(draft.getRecoverySmokeRecommended()).isTrue();
                    assertThat(draft.getRecoverySmokeRequiredChecks()).contains("aiInvocation.status=MODEL_COMPLETED", "fallbackUsed=false");
                    assertThat(draft.getMustMention()).doesNotContain("transport:stream", "streamContentChunkCount=0");
                });
        assertThat(response.getRuntimeFixtures().get(2))
                .satisfies(draft -> {
                    assertThat(draft.getFailureType()).isEqualTo("OUTPUT_TRUNCATED");
                    assertThat(draft.getName()).contains("external-runtime-135-704-output-truncated");
                    assertThat(draft.getTransportMode()).isEqualTo("stream");
                    assertThat(draft.getStreamContentChunkCount()).isEqualTo(77);
                    assertThat(draft.getStreamReasoningChunkCount()).isEqualTo(164);
                    assertThat(draft.getStreamFinishReason()).isEqualTo("length");
                    assertThat(draft.getExpectedRuntimeAction()).contains("输出 token 预算", "JSON schema", "finish_reason=length", "max_tokens");
                    assertThat(draft.getRecoverySmokeRecommended()).isFalse();
                    assertThat(draft.getRecoverySmokeRequiredChecks()).isEmpty();
                    assertThat(draft.getMustMention()).contains("输出截断", "OUTPUT_TRUNCATED",
                            "streamContentChunkCount=77", "streamFinishReason=length");
                    assertThat(draft.getSourceMaterial().getArtifacts()).contains(
                            "aiInvocation.streamFinishReason length",
                            "runtime failure type OUTPUT_TRUNCATED");
                    assertThat(draft.getQuality().getMisconception()).contains("输出预算不足", "截断 JSON");
                    assertThat(draft.getQuality().getExpectedStudentMove()).contains("max tokens", "结构化重试");
                });
        assertThat(response.getRuntimeFixtures().get(3))
                .satisfies(draft -> {
                    assertThat(draft.getFailureType()).isEqualTo("PARTIAL_COMPLETION");
                    assertThat(draft.getExpectedRuntimeAction()).contains("保留可用诊断", "复核教学提示阶段");
                });
    }

    @Test
    void mergesCoachSafetyRejectionWithPromptSafetyDraftForSameSubmission() {
        Assignment assignment = Assignment.builder()
                .id(134L)
                .title("merged safety draft")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("merged safety")
                .description("merged safety")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(601L, submission(601L, assignment.getId(), 8L, 101L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(601L)
                .analysisSource("MODEL_COMPLETED")
                .scenario("WA")
                .headline("提示过度直接")
                .summary("存在泄题风险")
                .reportMarkdown("不要给完整代码")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"],
                          "answerLeakRisk": "HIGH",
                          "evidenceRefs": ["eval:submission:601"]
                        }
                        """)
                .build());
        hintSafetyCheckRepository.saved.add(hintSafetyCheck(3L, 601L, "MEDIUM"));
        CoachPrompt prompt = coachPrompt(60101L, 601L, 8L, "", 2, assignment.getId());
        prompt.setQuestion("请先说明最小失败样例。");
        prompt.setModelFailureReason("SAFETY_REJECTED");
        prompt.setModelAnswerLeakRisk("MEDIUM");
        coachPromptRepository.saved.add(prompt);

        var response = service.exportDiagnosisEvalFixtureDraft(assignment.getId());

        assertThat(response.getSafetyFixtureCount()).isEqualTo(1);
        assertThat(response.getSafetyFixtures()).first()
                .satisfies(draft -> {
                    assertThat(draft.getSubmissionId()).isEqualTo(601L);
                    assertThat(draft.getRiskLevel()).isEqualTo("HIGH");
                    assertThat(draft.getRiskSources()).containsExactly(
                            PromptSafetyIncidentAnalyzer.SOURCE_DIAGNOSIS_HIGH_LEAK_RISK,
                            PromptSafetyIncidentAnalyzer.SOURCE_HINT_SAFETY_CHECK,
                            PromptSafetyIncidentAnalyzer.SOURCE_COACH_SAFETY_RISK
                    );
                    assertThat(draft.getBlockedReasons()).contains(
                            "疑似直接给出答案或完整改法",
                            "Coach 模型追问草稿被安全门拒绝，中等泄题风险"
                    );
                    assertThat(draft.getEvidenceRefs()).contains(
                            "eval:submission:601",
                            "hint_safety_check:3",
                            "coach_prompt:60101",
                            "coach_safety_rejection:submission:601"
                    );
                    assertThat(draft.getSourceMaterial().getArtifacts()).contains(
                            "hint safety check #3",
                            "coach prompt #60101"
                    );
                });
    }

    @Test
    void exportsClassReviewInterventionAsFixtureDraft() {
        Assignment assignment = Assignment.builder()
                .id(128L)
                .title("intervention draft")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(201L, submission(201L, assignment.getId(), 5L, 101L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionRepository.items.put(202L, submission(202L, assignment.getId(), 5L, 101L, Submission.Verdict.ACCEPTED, 40));
        submissionAnalysisRepository.save(analysis(201L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));

        var overview = service.getAssignmentOverview(assignment.getId());
        var suggestion = overview.getClassReviewSuggestions().get(0);
        classReviewFeedbackRepository.saved.add(ClassReviewFeedback.builder()
                .id(81L)
                .assignmentId(assignment.getId())
                .suggestionKey(suggestion.getSuggestionKey())
                .targetAbility(suggestion.getTargetAbility())
                .exampleProblemId(suggestion.getExampleProblemId())
                .evidenceTags("[\"OFF_BY_ONE\"]")
                .actionType(ClassReviewFeedbackService.ACTION_ACCEPTED)
                .teacherNote("已讲评边界样例")
                .createdBy("teacher")
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 10))
                .build());

        var response = service.exportDiagnosisEvalFixtureDraft(assignment.getId());

        assertThat(response.getInterventionFixtureCount()).isEqualTo(1);
        assertThat(response.getSummary()).contains("课堂介入 fixture 草稿");
        assertThat(response.getInterventionFixtures()).first()
                .satisfies(draft -> {
                    assertThat(draft.getSource()).isEqualTo("class-review-intervention-draft");
                    assertThat(draft.getSuggestionKey()).isEqualTo(suggestion.getSuggestionKey());
                    assertThat(draft.getFeedbackActionType()).isEqualTo(ClassReviewFeedbackService.ACTION_ACCEPTED);
                    assertThat(draft.getImpactStatus()).isEqualTo(TeacherInterventionImpactAnalyzer.STATUS_IMPROVED);
                    assertThat(draft.getFollowupSubmissionId()).isEqualTo(202L);
                    assertThat(draft.getEvidenceTags()).contains("OFF_BY_ONE");
                    assertThat(draft.getEvidenceRefs()).contains("followup_submission:202");
                    assertThat(draft.getMustMention()).contains("已有改善");
                    assertThat(draft.getExpectedTeachingActions()).anyMatch(action -> action.contains("复述"));
                    assertThat(draft.getMustNotMention()).contains("参考答案", "学生姓名");
                    assertThat(draft.getQuality().getEvalPurpose()).contains("已有改善");
                });
    }

    @Test
    void exportsClassStrategyInterventionAsFixtureDraftWhenStillStuck() {
        Assignment assignment = Assignment.builder()
                .id(129L)
                .title("strategy intervention draft")
                .build();
        assignmentRepository.items.put(assignment.getId(), assignment);
        problemRepository.items.put(101L, Problem.builder()
                .id(101L)
                .title("array boundary")
                .description("array boundary")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build());
        submissionRepository.items.put(211L, submission(211L, assignment.getId(), 5L, 101L, Submission.Verdict.WRONG_ANSWER, 0));
        submissionRepository.items.put(212L, submission(212L, assignment.getId(), 6L, 101L, Submission.Verdict.WRONG_ANSWER, 5));
        submissionRepository.items.put(213L, submission(213L, assignment.getId(), 5L, 101L, Submission.Verdict.WRONG_ANSWER, 35));
        submissionAnalysisRepository.save(analysis(211L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(212L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));
        submissionAnalysisRepository.save(analysis(213L, "BOUNDARY_CONDITION", "OFF_BY_ONE"));

        var overview = service.getAssignmentOverview(assignment.getId());
        var strategy = overview.getClassTeachingStrategySignal();
        classReviewFeedbackRepository.saved.add(ClassReviewFeedback.builder()
                .id(82L)
                .assignmentId(assignment.getId())
                .suggestionKey(strategy.getStrategyKey())
                .targetAbility(strategy.getFocusAbility())
                .evidenceTags("[\"OFF_BY_ONE\"]")
                .actionType(ClassReviewFeedbackService.ACTION_MODIFIED)
                .teacherNote("改成小组复盘边界变量")
                .createdBy("teacher")
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 15))
                .build());

        var response = service.exportDiagnosisEvalFixtureDraft(assignment.getId());

        assertThat(response.getInterventionFixtureCount()).isEqualTo(1);
        assertThat(response.getInterventionFixtures()).first()
                .satisfies(draft -> {
                    assertThat(draft.getSource()).isEqualTo("class-strategy-intervention-draft");
                    assertThat(draft.getSuggestionKey()).isEqualTo(strategy.getStrategyKey());
                    assertThat(draft.getFeedbackActionType()).isEqualTo(ClassReviewFeedbackService.ACTION_MODIFIED);
                    assertThat(draft.getImpactStatus()).isEqualTo(ClassTeachingStrategyImpactAnalyzer.STATUS_STILL_STUCK);
                    assertThat(draft.getFollowupSubmissionId()).isEqualTo(213L);
                    assertThat(draft.getMustMention()).contains("仍卡同类问题");
                    assertThat(draft.getExpectedTeachingActions()).anyMatch(action -> action.contains("更小粒度"));
                    assertThat(draft.getQuality().getBugPattern()).contains("strategy-off-by-one");
                    assertThat(draft.getQuality().getEvalPurpose()).contains("仍卡同类问题");
                });
    }

    @Test
    void savesTeacherCorrectionAsEvalCandidate() {
        TestFixture fixture = createFixture(7L, 11L);
        submissionAnalysisRepository.save(SubmissionAnalysis.builder()
                .submissionId(fixture.submission().getId())
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("边界问题")
                .summary("可能是边界问题")
                .reportMarkdown("检查边界")
                .reportJson("""
                        {
                          "issueTags": ["BOUNDARY_CONDITION"],
                          "fineGrainedTags": ["OFF_BY_ONE"]
                        }
                        """)
                .build());

        TeacherDiagnosisCorrectionRequest request = new TeacherDiagnosisCorrectionRequest();
        request.setSubmissionId(fixture.submission().getId());
        request.setCorrectedIssueTag("IO_FORMAT");
        request.setCorrectedFineGrainedTag("INPUT_PARSING");
        request.setTeacherNote("实际问题是输入读取结构理解错。");
        request.setCorrectionType("KNOWLEDGE_PATH");
        request.setTargetIssueId("I2");
        request.setCorrectedKnowledgePath("基础语法 / 输入输出 / 多组数据读取");
        request.setTargetEvidenceRef("code:line:4");

        var response = service.correctDiagnosis(fixture.assignment().getId(), request);

        assertThat(response.getOriginalIssueTag()).isEqualTo("BOUNDARY_CONDITION");
        assertThat(response.getOriginalFineGrainedTag()).isEqualTo("OFF_BY_ONE");
        assertThat(response.getCorrectedIssueTag()).isEqualTo("IO_FORMAT");
        assertThat(response.getCorrectedFineGrainedTag()).isEqualTo("INPUT_PARSING");
        assertThat(response.getCorrectionType()).isEqualTo("KNOWLEDGE_PATH");
        assertThat(response.getTargetIssueId()).isEqualTo("I2");
        assertThat(response.getCorrectedKnowledgePath()).contains("多组数据读取");
        assertThat(response.getTargetEvidenceRef()).isEqualTo("code:line:4");
        assertThat(response.isEvalCandidate()).isTrue();
        assertThat(correctionRepository.saved).hasSize(1);
    }

    @Test
    void rejectsSubmissionOutsideAssignment() {
        TestFixture fixture = createFixture(7L, 11L);
        Assignment otherAssignment = Assignment.builder()
                .id(8L)
                .title("另一个作业")
                .build();
        assignmentRepository.items.put(otherAssignment.getId(), otherAssignment);

        TeacherDiagnosisCorrectionRequest request = new TeacherDiagnosisCorrectionRequest();
        request.setSubmissionId(fixture.submission().getId());
        request.setCorrectedIssueTag("IO_FORMAT");

        assertThatThrownBy(() -> service.correctDiagnosis(otherAssignment.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于当前作业");
    }

    @Test
    void rejectsUnknownCorrectionTag() {
        TestFixture fixture = createFixture(7L, 11L);

        TeacherDiagnosisCorrectionRequest request = new TeacherDiagnosisCorrectionRequest();
        request.setSubmissionId(fixture.submission().getId());
        request.setCorrectedIssueTag("NOT_A_REAL_TAG");

        assertThatThrownBy(() -> service.correctDiagnosis(fixture.assignment().getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("粗粒度错因不存在");
    }

    private TestFixture createFixture(Long assignmentId, Long submissionId) {
        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .title("课堂作业")
                .build();
        Submission submission = Submission.builder()
                .id(submissionId)
                .assignmentId(assignmentId)
                .studentProfileId(3L)
                .build();
        assignmentRepository.items.put(assignmentId, assignment);
        submissionRepository.items.put(submissionId, submission);
        return new TestFixture(assignment, submission);
    }

    record TestFixture(Assignment assignment, Submission submission) {
    }

    private static class FakeAssignmentRepository extends UnsupportedJpaRepository<Assignment, Long> implements AssignmentRepository {
        private final Map<Long, Assignment> items = new LinkedHashMap<>();

        @Override
        public Optional<Assignment> findById(Long id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public Optional<Assignment> findByIdAndOwnerTeacherId(Long id, UUID ownerTeacherId) {
            return findById(id).filter(assignment -> assignment.getOwnerTeacherId() == null
                    || ownerTeacherId.equals(assignment.getOwnerTeacherId()));
        }

        @Override
        public boolean existsById(Long id) {
            return items.containsKey(id);
        }

        @Override
        public List<Assignment> findAllByOrderByCreatedAtDesc() {
            return List.copyOf(items.values());
        }

        @Override
        public List<Assignment> findByClassGroupIdOrderByCreatedAtDesc(Long classGroupId) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getClassGroupId(), classGroupId))
                    .toList();
        }
    }

    private static class FakeSubmissionRepository extends UnsupportedJpaRepository<Submission, Long> implements SubmissionRepository {
        private final Map<Long, Submission> items = new LinkedHashMap<>();

        @Override
        public Optional<Submission> findById(Long id) {
            return Optional.ofNullable(items.get(id));
        }

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
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<Submission> findByAssignmentIdIn(Collection<Long> assignmentIds) {
            return items.values()
                    .stream()
                    .filter(item -> assignmentIds.contains(item.getAssignmentId()))
                    .toList();
        }

        @Override
        public List<Submission> findByStudentProfileIdInOrderBySubmittedAtDesc(Collection<Long> studentProfileIds) {
            return items.values()
                    .stream()
                    .filter(item -> studentProfileIds.contains(item.getStudentProfileId()))
                    .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<Submission> findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(Long assignmentId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<Submission> findAllById(Iterable<Long> ids) {
            Set<Long> wanted = new LinkedHashSet<>();
            ids.forEach(wanted::add);
            return items.values()
                    .stream()
                    .filter(item -> wanted.contains(item.getId()))
                    .toList();
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

    private static class TestSchoolSecurityProperties extends SchoolSecurityProperties {
        @Override
        public String studentTokenSecret() {
            return "test-student-token-secret-1234567890";
        }

        @Override
        public long studentTokenTtlDays() {
            return 30;
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

    private static class FakeSubmissionCaseResultRepository extends UnsupportedJpaRepository<SubmissionCaseResult, Long>
            implements SubmissionCaseResultRepository {
        private final List<SubmissionCaseResult> saved = new ArrayList<>();

        @Override
        public SubmissionCaseResult save(SubmissionCaseResult result) {
            saved.add(result);
            return result;
        }

        @Override
        public List<SubmissionCaseResult> findBySubmissionIdOrderByTestCaseNumberAsc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .sorted(Comparator.comparing(SubmissionCaseResult::getTestCaseNumber, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
        }

        @Override
        public List<SubmissionCaseResult> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }

        @Override
        public List<SubmissionCaseResultStatsProjection> summarizeBySubmissionIdIn(Collection<Long> submissionIds) {
            return List.of();
        }

        @Override
        public long deleteBySubmissionId(Long submissionId) {
            long before = saved.size();
            saved.removeIf(item -> Objects.equals(item.getSubmissionId(), submissionId));
            return before - saved.size();
        }

        @Override
        public long deleteBySubmissionIdIn(Collection<Long> submissionIds) {
            long before = saved.size();
            saved.removeIf(item -> submissionIds.contains(item.getSubmissionId()));
            return before - saved.size();
        }
    }

    private static class FakeTeacherDiagnosisCorrectionRepository extends UnsupportedJpaRepository<TeacherDiagnosisCorrection, Long>
            implements TeacherDiagnosisCorrectionRepository {
        private final List<TeacherDiagnosisCorrection> saved = new ArrayList<>();
        private long nextId = 1;

        @Override
        public TeacherDiagnosisCorrection save(TeacherDiagnosisCorrection correction) {
            correction.setId(nextId++);
            correction.setCorrectedAt(LocalDateTime.of(2026, 5, 18, 11, 30));
            saved.add(correction);
            return correction;
        }

        @Override
        public Optional<TeacherDiagnosisCorrection> findTopBySubmissionIdOrderByCorrectedAtDesc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .reduce((left, right) -> right);
        }

        @Override
        public List<TeacherDiagnosisCorrection> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }

        @Override
        public List<TeacherDiagnosisCorrection> findByAssignmentIdIn(Collection<Long> assignmentIds) {
            return saved.stream()
                    .filter(item -> assignmentIds.contains(item.getAssignmentId()))
                    .toList();
        }

        @Override
        public List<TeacherDiagnosisCorrection> findByAssignmentIdOrderByCorrectedAtDesc(Long assignmentId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .sorted(Comparator.comparing(TeacherDiagnosisCorrection::getCorrectedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<TeacherDiagnosisCorrection> findByAssignmentIdAndEvalCandidateTrueOrderByCorrectedAtDesc(Long assignmentId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .filter(TeacherDiagnosisCorrection::isEvalCandidate)
                    .sorted(Comparator.comparing(TeacherDiagnosisCorrection::getCorrectedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }
    }

    private static class FakeClassReviewFeedbackRepository extends UnsupportedJpaRepository<ClassReviewFeedback, Long>
            implements ClassReviewFeedbackRepository {
        private final List<ClassReviewFeedback> saved = new ArrayList<>();
        private long nextId = 1;

        @Override
        public ClassReviewFeedback save(ClassReviewFeedback feedback) {
            feedback.setId(nextId++);
            feedback.setCreatedAt(LocalDateTime.of(2026, 5, 18, 12, 0).plusMinutes(saved.size()));
            saved.add(feedback);
            return feedback;
        }

        @Override
        public List<ClassReviewFeedback> findByAssignmentIdOrderByCreatedAtDesc(Long assignmentId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .sorted(Comparator.comparing(ClassReviewFeedback::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<ClassReviewFeedback> findByAssignmentIdIn(Collection<Long> assignmentIds) {
            return saved.stream()
                    .filter(item -> assignmentIds.contains(item.getAssignmentId()))
                    .toList();
        }

        @Override
        public Optional<ClassReviewFeedback> findTopByAssignmentIdAndSuggestionKeyOrderByCreatedAtDesc(Long assignmentId, String suggestionKey) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .filter(item -> Objects.equals(item.getSuggestionKey(), suggestionKey))
                    .max(Comparator.comparing(ClassReviewFeedback::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        }
    }

    private static class FakeCoachPromptRepository extends UnsupportedJpaRepository<CoachPrompt, Long> implements CoachPromptRepository {
        private final List<CoachPrompt> saved = new ArrayList<>();

        @Override
        public Optional<CoachPrompt> findTopBySubmissionIdOrderByCreatedAtDesc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .reduce((left, right) -> right);
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .sorted(Comparator
                            .comparing(CoachPrompt::getTurnIndex, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(CoachPrompt::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                    .toList();
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }
    }

    private static class FakeHintSafetyCheckRepository extends UnsupportedJpaRepository<HintSafetyCheck, Long>
            implements HintSafetyCheckRepository {
        private final List<HintSafetyCheck> saved = new ArrayList<>();

        @Override
        public Optional<HintSafetyCheck> findTopBySubmissionIdOrderByCheckedAtDesc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .max(Comparator.comparing(HintSafetyCheck::getCheckedAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        }

        @Override
        public List<HintSafetyCheck> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }
    }

    private static class FakeStudentRecommendationEventRepository extends UnsupportedJpaRepository<StudentRecommendationEvent, Long>
            implements StudentRecommendationEventRepository {
        private final List<StudentRecommendationEvent> items = new ArrayList<>();

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
        public List<StudentRecommendationEvent> findByFollowupSubmissionIdAndEventTypeOrderByCreatedAtDesc(Long followupSubmissionId, String eventType) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getFollowupSubmissionId(), followupSubmissionId))
                    .filter(item -> Objects.equals(item.getEventType(), eventType))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public Optional<StudentRecommendationEvent> findTopByRecommendationTokenAndEventTypeOrderByCreatedAtDesc(String recommendationToken, String eventType) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getRecommendationToken(), recommendationToken))
                    .filter(item -> Objects.equals(item.getEventType(), eventType))
                    .max(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        }
    }

    private static class EmptyClassGroupRepository extends UnsupportedJpaRepository<ClassGroup, Long> implements ClassGroupRepository {
        @Override
        public Optional<ClassGroup> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<ClassGroup> findAllByOrderByCreatedAtDesc() {
            return List.of();
        }

        @Override
        public Optional<ClassGroup> findByNameIgnoreCase(String name) {
            return Optional.empty();
        }
    }

    private static class EmptyStudentProfileRepository extends UnsupportedJpaRepository<StudentProfile, Long> implements StudentProfileRepository {
        @Override
        public List<StudentProfile> findAllById(Iterable<Long> ids) {
            return List.of();
        }

        @Override
        public Optional<StudentProfile> findByIdentityKey(String identityKey) {
            return Optional.empty();
        }

        @Override
        public List<StudentProfile> findByIdentityKeyOrderByCreatedAtDesc(String identityKey) {
            return List.of();
        }

        @Override
        public List<StudentProfile> findByIdentityKeyIn(Collection<String> identityKeys) {
            return List.of();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(Long classGroupId) {
            return List.of();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdAndStudentNoIgnoreCaseOrderByCreatedAtDesc(Long classGroupId, String studentNo) {
            return List.of();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdAndDisplayNameIgnoreCaseOrderByCreatedAtDesc(Long classGroupId, String displayName) {
            return List.of();
        }
    }

    private static class FakeStudentProfileRepository extends EmptyStudentProfileRepository {
        private final List<StudentProfile> items = new ArrayList<>();

        @Override
        public List<StudentProfile> findAllById(Iterable<Long> ids) {
            Set<Long> wanted = new LinkedHashSet<>();
            ids.forEach(wanted::add);
            return items.stream()
                    .filter(item -> wanted.contains(item.getId()))
                    .toList();
        }

        @Override
        public List<StudentProfile> findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(Long classGroupId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getClassGroupId(), classGroupId))
                    .sorted(Comparator
                            .comparing(StudentProfile::getStudentNo, Comparator.nullsLast(String::compareTo))
                            .thenComparing(StudentProfile::getDisplayName, Comparator.nullsLast(String::compareTo)))
                    .toList();
        }
    }

    private static class EmptyAssignmentInviteRepository extends UnsupportedJpaRepository<AssignmentInvite, Long> implements AssignmentInviteRepository {
        @Override
        public List<AssignmentInvite> findAll() {
            return List.of();
        }

        @Override
        public Optional<AssignmentInvite> findByCodeIgnoreCase(String code) {
            return Optional.empty();
        }

        @Override
        public boolean existsByCodeIgnoreCase(String code) {
            return false;
        }
    }

    private static class EmptyAssignmentTaskRepository extends UnsupportedJpaRepository<AssignmentTask, Long> implements AssignmentTaskRepository {
        @Override
        public List<AssignmentTask> findByAssignmentIdOrderByOrderIndexAsc(Long assignmentId) {
            return List.of();
        }

        @Override
        public List<AssignmentTask> findByAssignmentIdIn(Collection<Long> assignmentIds) {
            return List.of();
        }

        @Override
        public boolean existsByAssignmentIdAndProblemId(Long assignmentId, Long problemId) {
            return false;
        }

        @Override
        public long deleteByAssignmentId(Long assignmentId) {
            return 0;
        }
    }

    private static class FakeAssignmentTaskRepository extends EmptyAssignmentTaskRepository {
        private final List<AssignmentTask> items = new ArrayList<>();

        @Override
        public List<AssignmentTask> findByAssignmentIdOrderByOrderIndexAsc(Long assignmentId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .sorted(Comparator.comparing(AssignmentTask::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
        }

        @Override
        public boolean existsByAssignmentIdAndProblemId(Long assignmentId, Long problemId) {
            return items.stream()
                    .anyMatch(item -> Objects.equals(item.getAssignmentId(), assignmentId)
                            && Objects.equals(item.getProblemId(), problemId));
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
            return List.of();
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

    private abstract static class UnsupportedJpaRepository<T, ID> {
        public List<T> findAll() {
            throw unsupported();
        }

        public List<T> findAllById(Iterable<ID> ids) {
            throw unsupported();
        }

        public <S extends T> S save(S entity) {
            throw unsupported();
        }

        public <S extends T> List<S> saveAll(Iterable<S> entities) {
            throw unsupported();
        }

        public Optional<T> findById(ID id) {
            throw unsupported();
        }

        public boolean existsById(ID id) {
            throw unsupported();
        }

        public long count() {
            throw unsupported();
        }

        public void deleteById(ID id) {
            throw unsupported();
        }

        public void delete(T entity) {
            throw unsupported();
        }

        public void deleteAllById(Iterable<? extends ID> ids) {
            throw unsupported();
        }

        public void deleteAll(Iterable<? extends T> entities) {
            throw unsupported();
        }

        public void deleteAll() {
            throw unsupported();
        }

        public void flush() {
            throw unsupported();
        }

        public <S extends T> S saveAndFlush(S entity) {
            throw unsupported();
        }

        public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
            throw unsupported();
        }

        public void deleteAllInBatch(Iterable<T> entities) {
            throw unsupported();
        }

        public void deleteAllByIdInBatch(Iterable<ID> ids) {
            throw unsupported();
        }

        public void deleteAllInBatch() {
            throw unsupported();
        }

        public T getOne(ID id) {
            throw unsupported();
        }

        public T getById(ID id) {
            throw unsupported();
        }

        public T getReferenceById(ID id) {
            throw unsupported();
        }

        public List<T> findAll(Sort sort) {
            throw unsupported();
        }

        public Page<T> findAll(Pageable pageable) {
            throw unsupported();
        }

        public <S extends T> Optional<S> findOne(Example<S> example) {
            throw unsupported();
        }

        public <S extends T> List<S> findAll(Example<S> example) {
            throw unsupported();
        }

        public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
            throw unsupported();
        }

        public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
            throw unsupported();
        }

        public <S extends T> long count(Example<S> example) {
            throw unsupported();
        }

        public <S extends T> boolean exists(Example<S> example) {
            throw unsupported();
        }

        public <S extends T, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not used in this test");
        }
    }

}
