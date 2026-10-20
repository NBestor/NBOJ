package com.onlinejudge.execution.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.AssignmentTaskRepository;
import com.onlinejudge.classroom.persistence.AssignmentRecipientRepository;
import com.onlinejudge.classroom.application.AssignmentTargetingPolicy;
import com.onlinejudge.shared.web.PlatformApiException;
import org.springframework.http.HttpStatus;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.problem.application.ProblemAccessPolicy;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.shared.security.AccessDeniedException;
import com.onlinejudge.shared.security.AuthenticationRequiredException;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class ClassroomProblemAccessService {

    private final StudentAccessTokenService studentAccessTokenService;
    private final StudentProfileRepository studentProfileRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentTaskRepository assignmentTaskRepository;
    private final AssignmentRecipientRepository assignmentRecipientRepository;
    private final AssignmentTargetingPolicy assignmentTargetingPolicy;
    private final ProblemRepository problemRepository;
    private final ProblemAccessPolicy problemAccessPolicy;

    public ClassroomProblemAccessService(StudentAccessTokenService studentAccessTokenService,
                                         StudentProfileRepository studentProfileRepository,
                                         AssignmentRepository assignmentRepository,
                                         AssignmentTaskRepository assignmentTaskRepository) {
        this(studentAccessTokenService, studentProfileRepository, assignmentRepository, assignmentTaskRepository,
                null, new AssignmentTargetingPolicy(), null, new ProblemAccessPolicy());
    }

    @Autowired
    public ClassroomProblemAccessService(StudentAccessTokenService studentAccessTokenService,
                                         StudentProfileRepository studentProfileRepository,
                                         AssignmentRepository assignmentRepository,
                                         AssignmentTaskRepository assignmentTaskRepository,
                                         AssignmentRecipientRepository assignmentRecipientRepository,
                                         AssignmentTargetingPolicy assignmentTargetingPolicy,
                                         ProblemRepository problemRepository,
                                         ProblemAccessPolicy problemAccessPolicy) {
        this.studentAccessTokenService = studentAccessTokenService;
        this.studentProfileRepository = studentProfileRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentTaskRepository = assignmentTaskRepository;
        this.assignmentRecipientRepository = assignmentRecipientRepository;
        this.assignmentTargetingPolicy = assignmentTargetingPolicy;
        this.problemRepository = problemRepository;
        this.problemAccessPolicy = problemAccessPolicy;
    }

    public Long requireAccess(Long assignmentId, Long problemId, HttpServletRequest request) {
        Long studentId = studentAccessTokenService.currentStudentId(request);
        if (assignmentId == null) {
            if (problemRepository != null) {
                var problem = problemRepository.findById(problemId)
                        .orElseThrow(() -> new PlatformApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "题目不存在"));
                if (!problemAccessPolicy.isAnonymousCatalogVisible(problem)) {
                    throw new PlatformApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "题目不存在");
                }
            }
            return studentId;
        }
        if (studentId == null) {
            throw new AuthenticationRequiredException("课堂作业运行代码前请先登录学生端");
        }

        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new AuthenticationRequiredException("学生身份不存在，请重新登录"));
        if (student.getStatus() != StudentProfile.RosterStatus.ACTIVE) {
            throw new AuthenticationRequiredException("ROSTER_MISMATCH");
        }
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("作业不存在: " + assignmentId));
        if (assignment.getClassGroupId() == null
                || student.getClassGroupId() == null
                || !Objects.equals(assignment.getClassGroupId(), student.getClassGroupId())) {
            throw new AccessDeniedException("当前学生不属于该作业班级");
        }
        var recipients = assignmentRecipientRepository == null ? java.util.Set.<Long>of()
                : assignmentRecipientRepository.findByAssignmentId(assignmentId).stream()
                .map(recipient -> recipient.getStudentProfileId()).collect(Collectors.toSet());
        if (!assignmentTargetingPolicy.isTargeted(assignment, student, recipients)) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "ASSIGNMENT_NOT_TARGETED", "当前作业未布置给该学生");
        }
        LocalDateTime now = LocalDateTime.now();
        if (assignment.getStatus() != Assignment.AssignmentStatus.ACTIVE
                || assignment.getStartsAt() != null && now.isBefore(assignment.getStartsAt())
                || assignment.getEndsAt() != null && now.isAfter(assignment.getEndsAt())) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "ASSIGNMENT_NOT_ACTIVE", "作业不在有效时间窗口内");
        }
        if (!assignmentTaskRepository.existsByAssignmentIdAndProblemId(assignmentId, problemId)) {
            throw new AccessDeniedException("该题目不属于当前作业");
        }
        return studentId;
    }
}
