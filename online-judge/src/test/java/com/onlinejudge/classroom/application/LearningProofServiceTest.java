package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.AssignmentTask;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.dto.CoachReplyRequest;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.classroom.persistence.AssignmentTaskRepository;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.application.SubmissionGrowthSummaryService;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.dto.SubmissionGrowthSummaryResponse;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningProofServiceTest {

    @Test
    void selectsRecoveredEffectiveAttemptAsKeyRepair() {
        Fixture fixture = fixture();
        Submission failed = submission(11L, 101L, Submission.Verdict.WRONG_ANSWER, 1, 1L);
        Submission repaired = submission(12L, 101L, Submission.Verdict.ACCEPTED, 2, 1L);
        fixture.base(failed, List.of(failed, repaired), problem(101L, "边界统计", List.of("边界")));
        when(fixture.growth.summarize(anyCollection())).thenReturn(Map.of(
                11L, growth(11L, null, 0, null, "FIRST_RECORD", List.of()),
                12L, growth(12L, 11L, 1, 3, "COMPLETED", List.of(issue("数组边界", "RECOVERED")))
        ));

        var proof = fixture.service.getForSubmission(12L);

        assertThat(proof.getRepair().getStatus()).isEqualTo("REPAIRED");
        assertThat(proof.getRepair().getBaselineSubmissionId()).isEqualTo(11L);
        assertThat(proof.getRepair().getTargetSubmissionId()).isEqualTo(12L);
        assertThat(proof.getRepair().getPassedTestCaseDelta()).isEqualTo(3);
        assertThat(proof.getRepair().getRecoveredIssues()).containsExactly("数组边界");
    }

    @Test
    void createsOneFixedReflectionAndSavesCheckableAnswerWithoutModelCall() {
        Fixture fixture = fixture();
        Submission failed = submission(21L, 101L, Submission.Verdict.WRONG_ANSWER, 1, 1L);
        Submission accepted = submission(22L, 101L, Submission.Verdict.ACCEPTED, 2, 1L);
        fixture.base(accepted, List.of(failed, accepted), problem(101L, "边界统计", List.of("边界")));
        when(fixture.growth.summarize(anyCollection())).thenReturn(Map.of());
        AtomicReference<CoachPrompt> stored = new AtomicReference<>();
        when(fixture.prompts.findTopBySubmissionIdAndPromptTypeOrderByCreatedAtDesc(22L, LearningProofService.REFLECTION_PROMPT_TYPE))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(fixture.prompts.save(any(CoachPrompt.class))).thenAnswer(invocation -> {
            CoachPrompt prompt = invocation.getArgument(0);
            if (prompt.getId() == null) prompt.setId(91L);
            stored.set(prompt);
            return prompt;
        });

        var opened = fixture.service.createReflection(22L);
        fixture.service.createReflection(22L);
        CoachReplyRequest request = new CoachReplyRequest();
        request.setAnswer("我把循环上界从 n 改为 n-1；输入 n=1 时原来会越界，预期输出 0。 ");
        var answered = fixture.service.answerReflection(22L, request);

        assertThat(opened.getExplanation().getStatus()).isEqualTo("WAITING");
        assertThat(answered.getExplanation().getStatus()).isEqualTo("CHECKABLE");
        assertThat(answered.getExplanation().getAnswer()).contains("n=1");
        verify(fixture.prompts, atLeastOnce()).save(any(CoachPrompt.class));
    }

    @Test
    void returnsNoTargetInsteadOfInventingAnUnrelatedProblem() {
        Fixture fixture = fixture();
        Submission accepted = submission(31L, 101L, Submission.Verdict.ACCEPTED, 1, 1L);
        Problem source = problem(101L, "数组边界", List.of("数组边界"));
        Problem unrelated = problem(102L, "图搜索", List.of("图论"));
        fixture.base(accepted, List.of(accepted), source);
        when(fixture.growth.summarize(anyCollection())).thenReturn(Map.of());
        when(fixture.tasks.findByAssignmentIdOrderByOrderIndexAsc(7L)).thenReturn(List.of(task(1L, 101L), task(2L, 102L)));
        when(fixture.problems.findAllById(any())).thenReturn(List.of(source, unrelated));

        var proof = fixture.service.getForSubmission(31L);

        assertThat(proof.getIndependentUse().getStatus()).isEqualTo("NOT_AVAILABLE");
        assertThat(proof.getIndependentUse().getTargetProblemId()).isNull();
    }

    @Test
    void returnsRealRelatedTargetAndLaterAcceptedEvidence() {
        Fixture fixture = fixture();
        Submission accepted = submission(41L, 101L, Submission.Verdict.ACCEPTED, 1, 1L);
        Submission targetAccepted = submission(42L, 102L, Submission.Verdict.ACCEPTED, 2, 1L);
        Problem source = problem(101L, "数组边界", List.of("数组边界"));
        Problem target = problem(102L, "边界计数", List.of("数组边界"));
        fixture.base(accepted, List.of(accepted, targetAccepted), source);
        when(fixture.growth.summarize(anyCollection())).thenReturn(Map.of());
        when(fixture.tasks.findByAssignmentIdOrderByOrderIndexAsc(7L)).thenReturn(List.of(task(1L, 101L), task(2L, 102L)));
        when(fixture.problems.findAllById(any())).thenReturn(List.of(source, target));
        when(fixture.analyses.findBySubmissionIdIn(anyList())).thenReturn(List.of());
        when(fixture.transfer.analyzeTasks(anyList(), any(), any(), any())).thenReturn(Map.of(
                101L, StudentTrajectoryResponse.PostAcTransferSignal.builder()
                        .phase(PostAcTransferAnalyzer.PHASE_TRANSFER_VERIFIED)
                        .build()
        ));

        var proof = fixture.service.getForSubmission(41L);

        assertThat(proof.getIndependentUse().getStatus()).isEqualTo("VERIFIED");
        assertThat(proof.getIndependentUse().getTargetProblemId()).isEqualTo(102L);
        assertThat(proof.getIndependentUse().getTargetSubmissionId()).isEqualTo(42L);
    }

    @Test
    void teacherCountsStudentsOnceAcrossMultipleSubmissions() {
        Fixture fixture = fixture();
        Submission failed = submission(51L, 101L, Submission.Verdict.WRONG_ANSWER, 1, 1L);
        Submission accepted = submission(52L, 101L, Submission.Verdict.ACCEPTED, 2, 1L);
        Submission firstPass = submission(53L, 101L, Submission.Verdict.ACCEPTED, 3, 2L);
        Problem problem = problem(101L, "边界统计", List.of("边界"));
        when(fixture.problems.findById(101L)).thenReturn(Optional.of(problem));
        when(fixture.submissions.findByAssignmentIdOrderBySubmittedAtDesc(7L)).thenReturn(List.of(firstPass, accepted, failed));
        when(fixture.submissions.findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(7L, 1L)).thenReturn(List.of(accepted, failed));
        when(fixture.submissions.findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(7L, 2L)).thenReturn(List.of(firstPass));
        when(fixture.profiles.findAllById(any())).thenReturn(List.of(
                StudentProfile.builder().id(1L).displayName("甲").studentNo("01").build(),
                StudentProfile.builder().id(2L).displayName("乙").studentNo("02").build()
        ));
        when(fixture.growth.summarize(anyCollection())).thenReturn(Map.of());
        when(fixture.prompts.findTopBySubmissionIdAndPromptTypeOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(fixture.tasks.findByAssignmentIdOrderByOrderIndexAsc(7L)).thenReturn(List.of());

        var result = fixture.service.getTeacherProblemProof(7L, 101L);

        assertThat(result.getStudents()).hasSize(2);
        assertThat(result.getFailedStudentCount()).isEqualTo(1);
        assertThat(result.getRepairedStudentCount()).isEqualTo(1);
        assertThat(result.getExplainedStudentCount()).isZero();
    }

    private static Fixture fixture() {
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionAnalysisRepository analyses = mock(SubmissionAnalysisRepository.class);
        ProblemRepository problems = mock(ProblemRepository.class);
        AssignmentTaskRepository tasks = mock(AssignmentTaskRepository.class);
        StudentProfileRepository profiles = mock(StudentProfileRepository.class);
        CoachPromptRepository prompts = mock(CoachPromptRepository.class);
        SubmissionGrowthSummaryService growth = mock(SubmissionGrowthSummaryService.class);
        PostAcTransferAnalyzer transfer = mock(PostAcTransferAnalyzer.class);
        LearningProofService service = new LearningProofService(
                submissions, analyses, problems, tasks, profiles, prompts, growth,
                new CoachAnswerQualityAnalyzer(), transfer, new ObjectMapper());
        return new Fixture(service, submissions, analyses, problems, tasks, profiles, prompts, growth, transfer);
    }

    private static Submission submission(Long id, Long problemId, Submission.Verdict verdict, int minute, Long studentId) {
        return Submission.builder()
                .id(id).assignmentId(7L).studentProfileId(studentId).problemId(problemId)
                .sourceCode("code-" + id).verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 8, 11, 9, 0).plusMinutes(minute)).build();
    }

    private static Problem problem(Long id, String title, List<String> knowledge) {
        return Problem.builder().id(id).title(title).description(title).difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000).memoryLimit(65536).knowledgePoints(knowledge).build();
    }

    private static AssignmentTask task(Long id, Long problemId) {
        return AssignmentTask.builder().id(id).assignmentId(7L).problemId(problemId).orderIndex(id.intValue()).required(true).build();
    }

    private static SubmissionGrowthSummaryResponse growth(
            Long id, Long comparisonId, long recovered, Integer testDelta, String state,
            List<SubmissionGrowthSummaryResponse.IssueSignal> issues) {
        return SubmissionGrowthSummaryResponse.builder()
                .submissionId(id).comparisonSubmissionId(comparisonId).comparable(true).effectiveAttempt(true)
                .growthState(state).ruleVersion("test").recoveredCount(recovered).passedTestCaseDelta(testDelta)
                .issueSignals(issues).build();
    }

    private static SubmissionGrowthSummaryResponse.IssueSignal issue(String title, String status) {
        return SubmissionGrowthSummaryResponse.IssueSignal.builder().title(title).changeStatus(status).build();
    }

    private record Fixture(
            LearningProofService service,
            SubmissionRepository submissions,
            SubmissionAnalysisRepository analyses,
            ProblemRepository problems,
            AssignmentTaskRepository tasks,
            StudentProfileRepository profiles,
            CoachPromptRepository prompts,
            SubmissionGrowthSummaryService growth,
            PostAcTransferAnalyzer transfer
    ) {
        private void base(Submission requested, List<Submission> assignmentSubmissions, Problem problem) {
            when(submissions.findById(requested.getId())).thenReturn(Optional.of(requested));
            assignmentSubmissions.forEach(item -> when(submissions.findById(item.getId())).thenReturn(Optional.of(item)));
            when(submissions.findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(
                    requested.getAssignmentId(), requested.getStudentProfileId())).thenReturn(assignmentSubmissions);
            when(problems.findById(requested.getProblemId())).thenReturn(Optional.of(problem));
            when(prompts.findTopBySubmissionIdAndPromptTypeOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
            when(tasks.findByAssignmentIdOrderByOrderIndexAsc(requested.getAssignmentId())).thenReturn(List.of());
        }
    }
}
