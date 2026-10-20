package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.AssignmentTask;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.dto.StudentAssignmentLeaderboardResponse;
import com.onlinejudge.classroom.dto.StudentAssignmentSubmissionPageResponse;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.AssignmentTaskRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.shared.security.AccessDeniedException;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import com.onlinejudge.submission.persistence.StudentAssignmentSubmissionQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAssignmentInsightsServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private AssignmentTaskRepository assignmentTaskRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private StudentAssignmentSubmissionQueryRepository submissionQueryRepository;
    @Mock
    private ProblemRepository problemRepository;

    private StudentAssignmentInsightsService service;

    @BeforeEach
    void setUp() {
        service = new StudentAssignmentInsightsService(
                assignmentRepository,
                assignmentTaskRepository,
                studentProfileRepository,
                submissionRepository,
                submissionQueryRepository,
                problemRepository
        );
    }

    @Test
    void ranksByCompletedProblemsWithSharedRanksAndMaskedPeers() {
        Assignment assignment = assignment(7L, 3L);
        StudentProfile current = student(41L, 3L, "学生甲");
        StudentProfile peerOne = student(42L, 3L, "王小明");
        StudentProfile peerTwo = student(43L, 3L, "李小红");
        LocalDateTime now = LocalDateTime.of(2026, 7, 11, 10, 0);

        when(assignmentRepository.findById(7L)).thenReturn(Optional.of(assignment));
        when(studentProfileRepository.findById(41L)).thenReturn(Optional.of(current));
        when(studentProfileRepository.findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(3L))
                .thenReturn(List.of(current, peerOne, peerTwo));
        when(assignmentTaskRepository.findByAssignmentIdOrderByOrderIndexAsc(7L))
                .thenReturn(List.of(task(1L, 7L, 101L, 1), task(2L, 7L, 102L, 2), task(3L, 7L, 103L, 3)));
        when(submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(7L)).thenReturn(List.of(
                submission(11L, 7L, 41L, 101L, Submission.Verdict.ACCEPTED, now.minusMinutes(4)),
                submission(12L, 7L, 41L, 102L, Submission.Verdict.ACCEPTED, now.minusMinutes(3)),
                submission(13L, 7L, 42L, 101L, Submission.Verdict.ACCEPTED, now.minusMinutes(2)),
                submission(14L, 7L, 42L, 102L, Submission.Verdict.ACCEPTED, now.minusMinutes(1)),
                submission(15L, 7L, 43L, 101L, Submission.Verdict.ACCEPTED, now)
        ));

        StudentAssignmentLeaderboardResponse response = service.getLeaderboard(7L, 41L);

        assertThat(response.getMyRank()).isEqualTo(1);
        assertThat(response.getTiedStudentCount()).isEqualTo(2);
        assertThat(response.getRankingRule()).isEqualTo("PROGRESS_ONLY_V1");
        assertThat(response.getRows()).extracting(StudentAssignmentLeaderboardResponse.Row::getRank)
                .containsExactly(1, 1, 3);
        assertThat(response.getRows()).extracting(StudentAssignmentLeaderboardResponse.Row::getDisplayName)
                .containsExactly("学生甲（我）", "王同学", "李同学");
        assertThat(response.getRows()).extracting(StudentAssignmentLeaderboardResponse.Row::isCurrentStudent)
                .containsExactly(true, false, false);
    }

    @Test
    void rejectsRankingForStudentOutsideAssignmentClass() {
        when(assignmentRepository.findById(7L)).thenReturn(Optional.of(assignment(7L, 3L)));
        when(studentProfileRepository.findById(41L)).thenReturn(Optional.of(student(41L, 9L, "学生甲")));

        assertThatThrownBy(() -> service.getLeaderboard(7L, 41L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("本班");
    }

    @Test
    void returnsOnlyCurrentStudentSubmissionPageWithFilters() {
        Assignment assignment = assignment(7L, 3L);
        StudentProfile current = student(41L, 3L, "学生甲");
        Submission accepted = submission(
                21L,
                7L,
                41L,
                102L,
                Submission.Verdict.ACCEPTED,
                LocalDateTime.of(2026, 7, 11, 11, 30)
        );
        accepted.setLanguageName("Python 3");
        accepted.setExecutionTime(0.015);
        accepted.setMemoryUsed(12600);

        when(assignmentRepository.findById(7L)).thenReturn(Optional.of(assignment));
        when(studentProfileRepository.findById(41L)).thenReturn(Optional.of(current));
        when(submissionQueryRepository.findStudentAssignmentSubmissions(
                eq(7L),
                eq(41L),
                eq(102L),
                eq(null),
                eq(Submission.Verdict.ACCEPTED),
                eq(Submission.Verdict.ACCEPTED),
                eq("Python 3"),
                eq(null),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(accepted), PageRequest.of(0, 8), 1));
        when(submissionRepository.findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(7L, 41L))
                .thenReturn(List.of(accepted));
        when(problemRepository.findAllById(List.of(102L)))
                .thenReturn(List.of(Problem.builder().id(102L).title("回文判断").build()));

        StudentAssignmentSubmissionPageResponse response = service.getSubmissions(
                7L,
                41L,
                102L,
                null,
                Submission.Verdict.ACCEPTED,
                "Python 3",
                null,
                0,
                8
        );

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(21L);
            assertThat(item.getProblemTitle()).isEqualTo("回文判断");
            assertThat(item.getVerdict()).isEqualTo(Submission.Verdict.ACCEPTED);
            assertThat(item.getLanguageName()).isEqualTo("Python 3");
        });
    }

    private Assignment assignment(Long id, Long classGroupId) {
        return Assignment.builder()
                .id(id)
                .title("课堂编程作业")
                .classGroupId(classGroupId)
                .status(Assignment.AssignmentStatus.ACTIVE)
                .hintPolicy(Assignment.HintPolicy.L2)
                .build();
    }

    private StudentProfile student(Long id, Long classGroupId, String displayName) {
        return StudentProfile.builder()
                .id(id)
                .classGroupId(classGroupId)
                .displayName(displayName)
                .identityKey("student:" + id)
                .build();
    }

    private AssignmentTask task(Long id, Long assignmentId, Long problemId, int orderIndex) {
        return AssignmentTask.builder()
                .id(id)
                .assignmentId(assignmentId)
                .problemId(problemId)
                .orderIndex(orderIndex)
                .required(true)
                .build();
    }

    private Submission submission(Long id,
                                  Long assignmentId,
                                  Long studentProfileId,
                                  Long problemId,
                                  Submission.Verdict verdict,
                                  LocalDateTime submittedAt) {
        return Submission.builder()
                .id(id)
                .assignmentId(assignmentId)
                .studentProfileId(studentProfileId)
                .problemId(problemId)
                .languageId(71)
                .languageName("Python 3")
                .sourceCode("print('ok')")
                .verdict(verdict)
                .submittedAt(submittedAt)
                .build();
    }
}
