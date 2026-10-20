package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.AssignmentTask;
import com.onlinejudge.classroom.domain.ClassGroup;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.AssignmentTaskRepository;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeacherLearningAnalysisServiceTest {

    private final ClassGroupRepository classGroupRepository = mock(ClassGroupRepository.class);
    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final AssignmentTaskRepository assignmentTaskRepository = mock(AssignmentTaskRepository.class);
    private final StudentProfileRepository studentProfileRepository = mock(StudentProfileRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final TeacherLearningAnalysisService service = new TeacherLearningAnalysisService(
            classGroupRepository,
            assignmentRepository,
            assignmentTaskRepository,
            studentProfileRepository,
            submissionRepository
    );

    @Test
    void aggregatesUniqueClassParticipationAndRequiredCompletion() {
        ClassGroup classGroup = ClassGroup.builder().id(1L).name("一班").build();
        Assignment first = assignment(11L, "作业一");
        Assignment second = assignment(12L, "作业二");
        when(classGroupRepository.findById(1L)).thenReturn(Optional.of(classGroup));
        when(assignmentRepository.findByClassGroupIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(first, second));
        when(studentProfileRepository.findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(1L))
                .thenReturn(List.of(student(101L), student(102L), student(103L)));
        when(assignmentTaskRepository.findByAssignmentIdIn(List.of(11L, 12L))).thenReturn(List.of(
                task(201L, 11L, true),
                task(202L, 11L, true),
                task(203L, 12L, true)
        ));
        when(submissionRepository.findByAssignmentIdIn(List.of(11L, 12L))).thenReturn(List.of(
                submission(1L, 11L, 201L, 101L, Submission.Verdict.ACCEPTED),
                submission(2L, 11L, 202L, 101L, Submission.Verdict.WRONG_ANSWER),
                submission(3L, 12L, 203L, 101L, Submission.Verdict.ACCEPTED),
                submission(4L, 11L, 201L, 102L, Submission.Verdict.ACCEPTED),
                submission(5L, 11L, 202L, 102L, Submission.Verdict.ACCEPTED),
                submission(6L, 11L, 201L, 999L, Submission.Verdict.ACCEPTED)
        ));

        var overview = service.getClassOverview(1L);

        assertThat(overview.getAssignmentCount()).isEqualTo(2);
        assertThat(overview.getRosterStudentCount()).isEqualTo(3);
        assertThat(overview.getSubmittedStudentCount()).isEqualTo(2);
        assertThat(overview.getUnsubmittedStudentCount()).isEqualTo(1);
        assertThat(overview.getAssignments()).extracting("assignmentId", "submittedStudentCount", "completedRequiredStudentCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(11L, 2L, 1L),
                        org.assertj.core.groups.Tuple.tuple(12L, 1L, 1L)
                );
    }

    private Assignment assignment(Long id, String title) {
        return Assignment.builder()
                .id(id)
                .classGroupId(1L)
                .title(title)
                .status(Assignment.AssignmentStatus.ACTIVE)
                .build();
    }

    private StudentProfile student(Long id) {
        return StudentProfile.builder().id(id).classGroupId(1L).displayName("学生" + id).build();
    }

    private AssignmentTask task(Long problemId, Long assignmentId, boolean required) {
        return AssignmentTask.builder()
                .assignmentId(assignmentId)
                .problemId(problemId)
                .orderIndex(1)
                .required(required)
                .build();
    }

    private Submission submission(Long id, Long assignmentId, Long problemId, Long studentId, Submission.Verdict verdict) {
        return Submission.builder()
                .id(id)
                .assignmentId(assignmentId)
                .problemId(problemId)
                .studentProfileId(studentId)
                .verdict(verdict)
                .build();
    }
}
