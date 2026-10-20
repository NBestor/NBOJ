package com.onlinejudge.execution.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.AssignmentTaskRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.shared.security.AccessDeniedException;
import com.onlinejudge.shared.security.AuthenticationRequiredException;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassroomProblemAccessServiceTest {

    private final StudentAccessTokenService tokenService = mock(StudentAccessTokenService.class);
    private final StudentProfileRepository studentRepository = mock(StudentProfileRepository.class);
    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final AssignmentTaskRepository taskRepository = mock(AssignmentTaskRepository.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final ClassroomProblemAccessService service = new ClassroomProblemAccessService(
            tokenService, studentRepository, assignmentRepository, taskRepository
    );

    @Test
    void allowsAnonymousPublicProblemsWithoutReadingClassroomData() {
        when(tokenService.currentStudentId(request)).thenReturn(null);

        assertThat(service.requireAccess(null, 101L, request)).isNull();

        verify(studentRepository, never()).findById(41L);
        verify(assignmentRepository, never()).findById(7L);
    }

    @Test
    void allowsAStudentOnlyForProblemsInTheirClassAssignment() {
        when(tokenService.currentStudentId(request)).thenReturn(41L);
        when(studentRepository.findById(41L)).thenReturn(Optional.of(student(41L, 3L)));
        when(assignmentRepository.findById(7L)).thenReturn(Optional.of(assignment(7L, 3L)));
        when(taskRepository.existsByAssignmentIdAndProblemId(7L, 101L)).thenReturn(true);

        assertThat(service.requireAccess(7L, 101L, request)).isEqualTo(41L);
    }

    @Test
    void rejectsMissingIdentityCrossClassAndProblemsOutsideTheAssignment() {
        when(tokenService.currentStudentId(request)).thenReturn(null);
        assertThatThrownBy(() -> service.requireAccess(7L, 101L, request))
                .isInstanceOf(AuthenticationRequiredException.class);

        when(tokenService.currentStudentId(request)).thenReturn(41L);
        when(studentRepository.findById(41L)).thenReturn(Optional.of(student(41L, 4L)));
        when(assignmentRepository.findById(7L)).thenReturn(Optional.of(assignment(7L, 3L)));
        assertThatThrownBy(() -> service.requireAccess(7L, 101L, request))
                .isInstanceOf(AccessDeniedException.class);

        when(studentRepository.findById(41L)).thenReturn(Optional.of(student(41L, 3L)));
        when(taskRepository.existsByAssignmentIdAndProblemId(7L, 101L)).thenReturn(false);
        assertThatThrownBy(() -> service.requireAccess(7L, 101L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("不属于当前作业");
    }

    private StudentProfile student(Long id, Long classId) {
        return StudentProfile.builder().id(id).classGroupId(classId).displayName("学生")
                .status(StudentProfile.RosterStatus.ACTIVE)
                .identityKey("student:" + id).build();
    }

    private Assignment assignment(Long id, Long classId) {
        return Assignment.builder().id(id).classGroupId(classId).title("课堂作业")
                .status(Assignment.AssignmentStatus.ACTIVE).build();
    }
}
