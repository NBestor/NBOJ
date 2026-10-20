package com.onlinejudge.identity.application;

import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.dto.OwnershipTransferResponse;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.shared.web.PlatformApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnershipTransferService {
    private final TeacherAccountService accounts;
    private final ClassGroupRepository classes;
    private final AssignmentRepository assignments;
    private final ProblemRepository problems;
    private final AuditService audit;

    @Transactional
    public OwnershipTransferResponse transfer(UUID sourceId, UUID targetId, TeacherPrincipal admin, String ipAddress) {
        if (Objects.equals(sourceId, targetId)) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER", "来源教师和目标教师不能相同");
        }
        accounts.require(sourceId);
        TeacherAccount target = accounts.require(targetId);
        if (target.getStatus() != TeacherAccount.Status.ACTIVE) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_TARGET", "只能转交给正常状态的教师");
        }
        var ownedClasses = classes.findByOwnerTeacherIdOrderByCreatedAtDesc(sourceId);
        var ownedAssignments = assignments.findByOwnerTeacherIdOrderByCreatedAtDesc(sourceId);
        var ownedProblems = problems.findByOwnerTeacherIdOrderByCreatedAtDesc(sourceId);
        ownedClasses.forEach(item -> item.setOwnerTeacherId(targetId));
        ownedAssignments.forEach(item -> item.setOwnerTeacherId(targetId));
        ownedProblems.forEach(item -> item.setOwnerTeacherId(targetId));
        classes.saveAll(ownedClasses);
        assignments.saveAll(ownedAssignments);
        problems.saveAll(ownedProblems);
        String detail = "target=" + targetId + ", classes=" + ownedClasses.size()
                + ", assignments=" + ownedAssignments.size() + ", problems=" + ownedProblems.size();
        audit.record(admin.id(), "TEACHER_DATA_TRANSFERRED", "TEACHER", sourceId, detail, ipAddress);
        return new OwnershipTransferResponse(sourceId, targetId, ownedClasses.size(), ownedAssignments.size(), ownedProblems.size());
    }
}
