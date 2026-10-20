package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import com.onlinejudge.classroom.persistence.StudentRecommendationEventRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
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
import com.onlinejudge.leaderboard.persistence.ProblemSubmissionStatsProjection;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAbilityProfileServiceTest {

    private final FakeStudentProfileRepository studentProfileRepository = new FakeStudentProfileRepository();
    private final FakeSubmissionRepository submissionRepository = new FakeSubmissionRepository();
    private final FakeSubmissionAnalysisRepository analysisRepository = new FakeSubmissionAnalysisRepository();
    private final FakeProblemRepository problemRepository = new FakeProblemRepository();
    private final FakeCoachPromptRepository coachPromptRepository = new FakeCoachPromptRepository();
    private final FakeStudentRecommendationEventRepository recommendationEventRepository = new FakeStudentRecommendationEventRepository();
    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StudentAbilityProfileService service = new StudentAbilityProfileService(
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

    @Test
    void mergesSameStudentAcrossAssignmentProfiles() {
        StudentProfile firstProfile = student(1L, 9L, "张三", "08");
        StudentProfile secondProfile = student(2L, 9L, "张三", "08");
        studentProfileRepository.items.put(1L, firstProfile);
        studentProfileRepository.items.put(2L, secondProfile);
        problemRepository.items.put(101L, problem(101L, "数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")));
        problemRepository.items.put(102L, problem(102L, "循环统计", List.of("循环"), List.of("下标越界"), List.of("最大规模")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.add(submission(12L, 2L, 102L, 8L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(12L, "[\"LOOP_BOUNDARY\"]", "[]"));
        coachPromptRepository.saved.add(prompt(21L, 12L));

        var profile = service.buildProfile(1L);

        assertThat(profile.getMergedStudentProfileIds()).containsExactly(1L, 2L);
        assertThat(profile.getTotalSubmissions()).isEqualTo(2);
        assertThat(profile.getProblemCount()).isEqualTo(2);
        assertThat(profile.getAssignmentCount()).isEqualTo(2);
        assertThat(profile.getPrimaryAbilityFocus()).isEqualTo("循环与边界");
        assertThat(profile.getSummary()).contains("跨作业主要集中在：循环与边界");
        assertThat(profile.getAbilityGaps()).first()
                .satisfies(ability -> {
                    assertThat(ability.getTaskCount()).isEqualTo(2);
                    assertThat(ability.getSubmissionCount()).isEqualTo(2);
                });
        assertThat(profile.getKnowledgeFocus()).extracting("label").contains("数组", "循环");
        assertThat(profile.getCommonMistakeFocus()).extracting("label").contains("边界漏判", "下标越界");
        assertThat(profile.getLatestCoachInteraction().getSubmissionId()).isEqualTo(12L);
    }

    @Test
    void profileExposesRecurringMisconceptionSignal() {
        StudentProfile student = student(1L, 9L, "张三", "08");
        studentProfileRepository.items.put(1L, student);
        problemRepository.items.put(101L, problem(101L, "数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")));
        problemRepository.items.put(102L, problem(102L, "循环统计", List.of("循环"), List.of("边界漏判"), List.of("最大规模")));
        submissionRepository.items.add(submission(31L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 5));
        submissionRepository.items.add(submission(32L, 1L, 102L, 8L, Submission.Verdict.WRONG_ANSWER, 1));
        analysisRepository.save(analysis(31L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(32L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var profile = service.buildProfile(1L);

        assertThat(profile.getRecurringMisconceptionSignal()).isNotNull();
        assertThat(profile.getRecurringMisconceptionSignal().getStatus()).isEqualTo("ESCALATE");
        assertThat(profile.getRecurringMisconceptionSignal().getFineGrainedTag()).isEqualTo("OFF_BY_ONE");
        assertThat(profile.getRecurringMisconceptionSignal().getEvidenceProblemIds()).contains(101L, 102L);
        assertThat(profile.getTeachingActionDecision()).isNotNull();
        assertThat(profile.getTeachingActionDecision().getActionType()).isEqualTo(TeachingActionOrchestrator.ACTION_TEACHER_REVIEW);
        assertThat(profile.getTeachingActionDecision().getSourceSignals()).contains("recurring_misconception:ESCALATE");
    }

    @Test
    void profileDoesNotTreatSingleProblemRepeatedDebuggingAsLongTermRecurring() {
        StudentProfile student = student(1L, 9L, "张三", "08");
        studentProfileRepository.items.put(1L, student);
        problemRepository.items.put(101L, problem(101L, "数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")));
        submissionRepository.items.add(submission(41L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 8));
        submissionRepository.items.add(submission(42L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 6));
        submissionRepository.items.add(submission(43L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.add(submission(44L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 1));
        analysisRepository.save(analysis(41L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(42L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(43L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(44L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var profile = service.buildProfile(1L);

        assertThat(profile.getRecurringMisconceptionSignal()).isNotNull();
        assertThat(profile.getRecurringMisconceptionSignal().getStatus()).isEqualTo("WATCH");
        assertThat(profile.getRecurringMisconceptionSignal().getProblemCount()).isEqualTo(1);
        assertThat(profile.getRecurringMisconceptionSignal().isNeedsTeacherAttention()).isFalse();
    }

    @Test
    void profileExposesSelfExplanationMasterySignal() {
        StudentProfile student = student(1L, 9L, "张三", "08");
        studentProfileRepository.items.put(1L, student);
        problemRepository.items.put(101L, problem(101L, "数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")));
        submissionRepository.items.add(submission(51L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.add(submission(52L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 1));
        analysisRepository.save(analysis(51L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(52L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        coachPromptRepository.saved.add(prompt(61L, 51L, LocalDateTime.of(2026, 5, 18, 9, 50),
                "n=1 时预期输出 1，实际输出 0，所以最后一次循环没有覆盖。"));
        coachPromptRepository.saved.add(prompt(62L, 52L, LocalDateTime.of(2026, 5, 18, 10, 10),
                "变量 i 最后一次应该到 n-1，我只改边界再提交。"));

        var profile = service.buildProfile(1L);

        assertThat(profile.getSelfExplanationMasterySignal()).isNotNull();
        assertThat(profile.getSelfExplanationMasterySignal().getStatus()).isEqualTo("EVIDENCE_GROUNDED");
        assertThat(profile.getSelfExplanationMasterySignal().getVerifiableAnswerCount()).isEqualTo(2);
        assertThat(profile.getSelfExplanationMasterySignal().getEvidenceTypes()).contains("MIN_CASE", "VARIABLE_TRACE");
    }

    @Test
    void prefersStableIdentityKeyBeforeNameFallback() {
        StudentProfile current = student(1L, 9L, "张三", "08");
        current.setIdentityKey("class:9:08");
        StudentProfile sameStableKey = student(2L, 9L, "张三", "08");
        sameStableKey.setIdentityKey("class:9:08");
        StudentProfile sameNameDifferentStudentNo = student(3L, 9L, "张三", "09");
        sameNameDifferentStudentNo.setIdentityKey("class:9:09");
        studentProfileRepository.items.put(1L, current);
        studentProfileRepository.items.put(2L, sameStableKey);
        studentProfileRepository.items.put(3L, sameNameDifferentStudentNo);
        problemRepository.items.put(101L, problem(101L, "数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.add(submission(12L, 2L, 101L, 8L, Submission.Verdict.WRONG_ANSWER, 2));
        submissionRepository.items.add(submission(13L, 3L, 101L, 9L, Submission.Verdict.ACCEPTED, 1));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(12L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var profile = service.buildProfile(1L);

        assertThat(profile.getMergedStudentProfileIds()).containsExactly(1L, 2L);
        assertThat(profile.getMergedStudentProfileIds()).doesNotContain(3L);
        assertThat(profile.getTotalSubmissions()).isEqualTo(2);
    }

    @Test
    void profileIncludesCoachImpactSignalWhenAnsweredPromptHasFollowup() {
        StudentProfile current = student(1L, 9L, "student-a", "08");
        studentProfileRepository.items.put(1L, current);
        problemRepository.items.put(101L, problem(101L, "array boundary", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 8));
        submissionRepository.items.add(submission(12L, 1L, 101L, 7L, Submission.Verdict.ACCEPTED, 1));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        coachPromptRepository.saved.add(prompt(31L, 11L, LocalDateTime.of(2026, 5, 18, 9, 55)));

        var profile = service.buildProfile(1L);

        assertThat(profile.getCoachImpactSummary()).contains("同题后续提交通过");
        assertThat(profile.getLatestCoachImpact()).isNotNull();
        assertThat(profile.getLatestCoachImpact().getStatus()).isEqualTo("FOLLOWUP_ACCEPTED");
        assertThat(profile.getLatestCoachInteraction().getImpact().getFollowupSubmissionId()).isEqualTo(12L);
    }

    @Test
    void stableIdentityStillIncludesLegacyAssignmentScopedProfilesByStudentNo() {
        StudentProfile current = student(1L, 9L, "张三", "08");
        current.setIdentityKey("class:9:08");
        StudentProfile oldAssignmentScopedProfile = student(2L, 9L, "张三", "08");
        oldAssignmentScopedProfile.setIdentityKey("7:9:张三:08");
        studentProfileRepository.items.put(1L, current);
        studentProfileRepository.items.put(2L, oldAssignmentScopedProfile);
        problemRepository.items.put(101L, problem(101L, "数组边界", List.of("数组"), List.of("边界漏判"), List.of("单元素")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.add(submission(12L, 2L, 101L, 8L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(12L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var profile = service.buildProfile(1L);

        assertThat(profile.getMergedStudentProfileIds()).containsExactly(1L, 2L);
        assertThat(profile.getAssignmentCount()).isEqualTo(2);
    }

    @Test
    void manualSplitIdentityPreventsHeuristicStudentNoMerge() {
        StudentProfile current = student(1L, 9L, "student-a", "08");
        current.setIdentityKey("manual-split:9:1");
        StudentProfile sameStudentNo = student(2L, 9L, "student-a", "08");
        sameStudentNo.setIdentityKey("class:9:08");
        studentProfileRepository.items.put(1L, current);
        studentProfileRepository.items.put(2L, sameStudentNo);
        problemRepository.items.put(101L, problem(101L, "array boundary", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.add(submission(12L, 2L, 101L, 8L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(12L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var profile = service.buildProfile(1L);

        assertThat(profile.getMergedStudentProfileIds()).containsExactly(1L);
        assertThat(profile.getTotalSubmissions()).isEqualTo(1);
    }

    @Test
    void manualMergeIdentityMergesSelectedProfiles() {
        StudentProfile current = student(1L, 9L, "student-a", "08");
        current.setIdentityKey("manual-merge:9:1");
        StudentProfile merged = student(2L, 9L, "student-b", "09");
        merged.setIdentityKey("manual-merge:9:1");
        studentProfileRepository.items.put(1L, current);
        studentProfileRepository.items.put(2L, merged);
        problemRepository.items.put(101L, problem(101L, "array boundary", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.add(submission(12L, 2L, 101L, 8L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(12L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));

        var profile = service.buildProfile(1L);

        assertThat(profile.getMergedStudentProfileIds()).containsExactly(1L, 2L);
        assertThat(profile.getTotalSubmissions()).isEqualTo(2);
    }

    @Test
    void profileIncludesRecommendationEffectSignal() {
        StudentProfile current = student(1L, 9L, "student-a", "08");
        studentProfileRepository.items.put(1L, current);
        problemRepository.items.put(101L, problem(101L, "array boundary", List.of("array"), List.of("OFF_BY_ONE"), List.of("single")));
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        recommendationEventRepository.items.add(StudentRecommendationEvent.builder()
                .recommendationToken("rec:1:REDO:101:test")
                .studentProfileId(1L)
                .type("REDO")
                .problemId(101L)
                .focusTags("[\"OFF_BY_ONE\"]")
                .eventType(StudentRecommendationEventService.EVENT_EXPOSED)
                .createdAt(LocalDateTime.of(2026, 5, 18, 9, 30))
                .build());
        recommendationEventRepository.items.add(StudentRecommendationEvent.builder()
                .recommendationToken("rec:1:REDO:101:test")
                .studentProfileId(1L)
                .type("REDO")
                .problemId(101L)
                .focusTags("[\"OFF_BY_ONE\"]")
                .eventType(StudentRecommendationEventService.EVENT_SUBMITTED)
                .followupSubmissionId(11L)
                .followupVerdict(Submission.Verdict.WRONG_ANSWER.name())
                .followupFineGrainedTag("OFF_BY_ONE")
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build());

        var profile = service.buildProfile(1L);

        assertThat(profile.getRecommendationEffectSummary()).isNotBlank();
        assertThat(profile.getRecommendationEffectSummary()).contains("1");
    }

    @Test
    void profileExposesAiDependencySignal() {
        StudentProfile student = student(1L, 9L, "张三", "08");
        studentProfileRepository.items.put(1L, student);
        submissionRepository.items.add(submission(11L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.add(submission(12L, 1L, 102L, 7L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(11L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(12L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        coachPromptRepository.saved.add(prompt(21L, 11L, LocalDateTime.of(2026, 5, 18, 10, 10), "再给点提示"));
        coachPromptRepository.saved.add(prompt(22L, 12L, LocalDateTime.of(2026, 5, 18, 10, 12), "我还是不会"));
        recommendationEventRepository.items.add(recommendationEvent(31L, 11L, StudentRecommendationEventService.EVENT_SUBMITTED, Submission.Verdict.WRONG_ANSWER, 1));
        recommendationEventRepository.items.add(recommendationEvent(32L, 12L, StudentRecommendationEventService.EVENT_SUBMITTED, Submission.Verdict.WRONG_ANSWER, 2));
        recommendationEventRepository.items.add(recommendationEvent(33L, null, StudentRecommendationEventService.EVENT_CLICKED, null, 3));

        var profile = service.buildProfile(1L);

        assertThat(profile.getAiDependencySignal()).isNotNull();
        assertThat(profile.getAiDependencySignal().getStatus()).isEqualTo(AiDependencyAnalyzer.STATUS_DEPENDENCY_RISK);
        assertThat(profile.getAiDependencySignal().getRecommendationSubmissionCount()).isEqualTo(2);
        assertThat(profile.getAiDependencySignal().getRecommendedAction()).contains("独立尝试");
    }

    @Test
    void profileExposesMasteryGrowthSignal() {
        StudentProfile student = student(1L, 9L, "张三", "08");
        studentProfileRepository.items.put(1L, student);
        submissionRepository.items.add(submission(61L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 6));
        submissionRepository.items.add(submission(62L, 1L, 102L, 7L, Submission.Verdict.WRONG_ANSWER, 4));
        submissionRepository.items.add(submission(63L, 1L, 103L, 7L, Submission.Verdict.WRONG_ANSWER, 2));
        analysisRepository.save(analysis(61L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(62L, "[\"BOUNDARY_CONDITION\"]", "[\"OFF_BY_ONE\"]"));
        analysisRepository.save(analysis(63L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]"));

        var profile = service.buildProfile(1L);

        assertThat(profile.getMasteryGrowthSignal()).isNotNull();
        assertThat(profile.getMasteryGrowthSignal().getStatus()).isEqualTo(MasteryGrowthAnalyzer.STATUS_SPIRAL_REVIEW_NEEDED);
        assertThat(profile.getMasteryGrowthSignal().getFocusAbility()).isEqualTo("循环与边界");
        assertThat(profile.getMasteryGrowthSignal().getRecommendedAction()).contains("螺旋复习");
    }

    @Test
    void profileExposesFineGrainedLearningContextAndReviewCards() {
        StudentProfile student = student(1L, 9L, "张三", "08");
        studentProfileRepository.items.put(1L, student);
        problemRepository.items.put(101L, problem(101L, "区间求和", List.of("循环"), List.of("右端漏取"), List.of("最小值")));
        submissionRepository.items.add(submission(71L, 1L, 101L, 7L, Submission.Verdict.WRONG_ANSWER, 1));
        analysisRepository.save(analysisWithLearningProfile(
                71L,
                "[\"BOUNDARY_CONDITION\"]",
                "[\"OFF_BY_ONE\"]",
                "[\"循环与边界\"]",
                "[\"range 右端不包含\"]"
        ));

        var profile = service.buildProfile(1L);

        assertThat(profile.getFineGrainedProfile()).isNotNull();
        assertThat(profile.getFineGrainedProfile().getIssueTagFocus()).extracting("label").contains("BOUNDARY_CONDITION");
        assertThat(profile.getFineGrainedProfile().getFineGrainedTagFocus()).extracting("label").contains("OFF_BY_ONE");
        assertThat(profile.getFineGrainedProfile().getAbilityPointFocus()).extracting("label").contains("循环与边界");
        assertThat(profile.getFineGrainedProfile().getFocusPointFocus()).extracting("label").contains("range 右端不包含");
        assertThat(profile.getFineGrainedProfile().getAiContextSummary()).contains("OFF_BY_ONE", "循环与边界");
        assertThat(profile.getReviewCards()).singleElement().satisfies(card -> {
            assertThat(card.getSubmissionId()).isEqualTo(71L);
            assertThat(card.getProblemTitle()).isEqualTo("区间求和");
            assertThat(card.getPrimaryIssueTag()).isEqualTo("BOUNDARY_CONDITION");
            assertThat(card.getPrimaryFineGrainedTag()).isEqualTo("OFF_BY_ONE");
            assertThat(card.getAbilityPoint()).isEqualTo("循环与边界");
            assertThat(card.getNextAction()).contains("手推");
            assertThat(card.getEvidenceRefs()).contains("code:range_stop");
        });
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

    private SubmissionAnalysis analysisWithLearningProfile(Long submissionId,
                                                           String issueTags,
                                                           String fineTags,
                                                           String abilityPoints,
                                                           String focusPoints) {
        return SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("TEST")
                .scenario("WA")
                .headline("循环边界")
                .summary("循环没有覆盖右端点。")
                .reportMarkdown("markdown")
                .reportJson("""
                        {
                          "issueTags": %s,
                          "fineGrainedTags": %s,
                          "abilityPoints": %s,
                          "focusPoints": %s,
                          "evidenceRefs": ["code:range_stop"],
                          "studentHintPlan": {
                            "nextAction": "先手推 n=1 时循环变量实际出现过哪些值。"
                          }
                        }
                        """.formatted(issueTags, fineTags, abilityPoints, focusPoints))
                .build();
    }

    private CoachPrompt prompt(Long id, Long submissionId) {
        return prompt(id, submissionId, LocalDateTime.of(2026, 5, 18, 10, 20));
    }

    private CoachPrompt prompt(Long id, Long submissionId, LocalDateTime createdAt) {
        return prompt(id, submissionId, createdAt, "我会手推单元素");
    }

    private CoachPrompt prompt(Long id, Long submissionId, LocalDateTime createdAt, String answer) {
        return CoachPrompt.builder()
                .id(id)
                .submissionId(submissionId)
                .studentProfileId(1L)
                .turnIndex(1)
                .hintPolicy("L2")
                .promptType("SOCRATIC_NEXT_STEP")
                .question("请手推边界")
                .studentAnswer(answer)
                .coachFeedback("回答包含边界意识")
                .createdAt(createdAt)
                .answeredAt(createdAt)
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
                    .toList();
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }
    }

    private static class FakeStudentRecommendationEventRepository extends UnsupportedJpaRepository<StudentRecommendationEvent, Long> implements StudentRecommendationEventRepository {
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
