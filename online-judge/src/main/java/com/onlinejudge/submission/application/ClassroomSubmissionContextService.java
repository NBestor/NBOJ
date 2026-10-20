package com.onlinejudge.submission.application;

import com.onlinejudge.execution.application.ClassroomProblemAccessService;
import com.onlinejudge.shared.security.AccessDeniedException;
import com.onlinejudge.shared.security.AuthenticationRequiredException;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import com.onlinejudge.submission.dto.SubmissionRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassroomSubmissionContextService {

    private final StudentAccessTokenService studentAccessTokenService;
    private final ClassroomProblemAccessService problemAccessService;
    private final SubmissionEvidenceProperties properties;

    public SubmissionRequest resolve(SubmissionRequest request, HttpServletRequest httpRequest) {
        Long tokenStudentId = studentAccessTokenService.currentStudentId(httpRequest);
        Long requestedStudentId = request.getStudentProfileId();

        if (request.getAssignmentId() == null) {
            problemAccessService.requireAccess(null, request.getProblemId(), httpRequest);
            resolvePublicIdentity(request, httpRequest, tokenStudentId, requestedStudentId);
            return request;
        }

        if (tokenStudentId == null) {
            if (requestedStudentId != null) {
                studentAccessTokenService.requireStudent(httpRequest, requestedStudentId);
            }
            if (properties.isStrictClassroomContextEnabled()) {
                reject("MISSING_STUDENT_TOKEN", request);
                throw new AuthenticationRequiredException("课堂作业提交前请先登录学生端");
            }
            return request;
        }
        if (requestedStudentId != null && !Objects.equals(requestedStudentId, tokenStudentId)) {
            reject("STUDENT_ID_MISMATCH", request);
            throw new AccessDeniedException("提交学生与当前登录身份不一致");
        }

        request.setStudentProfileId(problemAccessService.requireAccess(
                request.getAssignmentId(),
                request.getProblemId(),
                httpRequest
        ));
        return request;
    }

    private void resolvePublicIdentity(SubmissionRequest request,
                                       HttpServletRequest httpRequest,
                                       Long tokenStudentId,
                                       Long requestedStudentId) {
        if (tokenStudentId == null) {
            if (requestedStudentId != null) {
                studentAccessTokenService.requireStudent(httpRequest, requestedStudentId);
            }
            request.setStudentProfileId(null);
            return;
        }
        if (requestedStudentId != null && !Objects.equals(requestedStudentId, tokenStudentId)) {
            reject("PUBLIC_STUDENT_ID_MISMATCH", request);
            throw new AccessDeniedException("提交学生与当前登录身份不一致");
        }
        request.setStudentProfileId(tokenStudentId);
    }

    private void reject(String reason, SubmissionRequest request) {
        log.warn("Rejected submission context. reason={}, assignmentId={}, problemId={}, requestedStudentId={}",
                reason, request.getAssignmentId(), request.getProblemId(), request.getStudentProfileId());
    }
}
