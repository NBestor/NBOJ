package com.onlinejudge.submission.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.AssignmentTaskRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.shared.security.AccessDeniedException;
import com.onlinejudge.shared.security.AuthenticationRequiredException;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import com.onlinejudge.execution.application.ClassroomProblemAccessService;
import com.onlinejudge.submission.dto.SubmissionRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassroomSubmissionContextServiceTest {

    private final StudentAccessTokenService tokenService = mock(StudentAccessTokenService.class);
    private final StudentProfileRepository studentRepository = mock(StudentProfileRepository.class);
    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final AssignmentTaskRepository taskRepository = mock(AssignmentTaskRepository.class);
    private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);
    private final SubmissionEvidenceProperties properties = new SubmissionEvidenceProperties();
    private final ClassroomSubmissionContextService service = new ClassroomSubmissionContextService(
            tokenService,
            new ClassroomProblemAccessService(tokenService, studentRepository, assignmentRepository, taskRepository),
            properties
    );

    @BeforeEach
    void setUp() {
        properties.setStrictClassroomContextEnabled(true);
    }

    @Test
    void resolvesLegalClassroomSubmissionFromTokenIdentity() {
        when(tokenService.currentStudentId(httpRequest)).thenReturn(41L);
        when(studentRepository.findById(41L)).thenReturn(Optional.of(student(41L, 3L)));
        when(assignmentRepository.findById(9L)).thenReturn(Optional.of(assignment(9L, 3L)));
        when(taskRepository.existsByAssignmentIdAndProblemId(9L, 101L)).thenReturn(true);

        SubmissionRequest resolved = service.resolve(request(9L, 101L, null), httpRequest);

        assertThat(resolved.getStudentProfileId()).isEqualTo(41L);
    }

    @Test
    void rejectsSpoofedStudentAndMissingToken() {
        when(tokenService.currentStudentId(httpRequest)).thenReturn(41L);
        assertThatThrownBy(() -> service.resolve(request(9L, 101L, 99L), httpRequest))
                .isInstanceOf(AccessDeniedException.class);

        when(tokenService.currentStudentId(httpRequest)).thenReturn(null);
        assertThatThrownBy(() -> service.resolve(request(9L, 101L, null), httpRequest))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void rejectsCrossClassAndProblemOutsideAssignment() {
        when(tokenService.currentStudentId(httpRequest)).thenReturn(41L);
        when(studentRepository.findById(41L)).thenReturn(Optional.of(student(41L, 4L)));
        when(assignmentRepository.findById(9L)).thenReturn(Optional.of(assignment(9L, 3L)));
        assertThatThrownBy(() -> service.resolve(request(9L, 101L, 41L), httpRequest))
                .isInstanceOf(AccessDeniedException.class);

        when(studentRepository.findById(41L)).thenReturn(Optional.of(student(41L, 3L)));
        when(taskRepository.existsByAssignmentIdAndProblemId(9L, 101L)).thenReturn(false);
        assertThatThrownBy(() -> service.resolve(request(9L, 101L, 41L), httpRequest))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("不属于当前作业");
    }

    @Test
    void keepsAnonymousPublicPracticeOutOfStudentIdentityAndUsesTokenWhenPresent() {
        when(tokenService.currentStudentId(httpRequest)).thenReturn(null);
        SubmissionRequest anonymous = service.resolve(request(null, 101L, null), httpRequest);
        assertThat(anonymous.getStudentProfileId()).isNull();

        when(tokenService.currentStudentId(httpRequest)).thenReturn(41L);
        SubmissionRequest signedIn = service.resolve(request(null, 101L, null), httpRequest);
        assertThat(signedIn.getStudentProfileId()).isEqualTo(41L);
    }

    private SubmissionRequest request(Long assignmentId, Long problemId, Long studentId) {
        SubmissionRequest request = new SubmissionRequest();
        request.setAssignmentId(assignmentId);
        request.setProblemId(problemId);
        request.setStudentProfileId(studentId);
        request.setLanguageId(71);
        request.setSourceCode("print(1)");
        return request;
    }

    private StudentProfile student(Long id, Long classId) {
        return StudentProfile.builder().id(id).classGroupId(classId).displayName("学生")
                .status(StudentProfile.RosterStatus.ACTIVE)
                .identityKey("student:" + id).build();
    }

    private Assignment assignment(Long id, Long classId) {
        return Assignment.builder().id(id).classGroupId(classId).title("作业")
                .status(Assignment.AssignmentStatus.ACTIVE).build();
    }
}
