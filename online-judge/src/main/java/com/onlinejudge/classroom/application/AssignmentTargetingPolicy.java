package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.StudentProfile;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

@Component
public class AssignmentTargetingPolicy {

    public boolean isTargeted(Assignment assignment, StudentProfile student, Set<Long> recipientIds) {
        if (assignment == null || student == null
                || student.getStatus() != StudentProfile.RosterStatus.ACTIVE
                || !Objects.equals(assignment.getClassGroupId(), student.getClassGroupId())) {
            return false;
        }
        Assignment.TargetMode mode = assignment.getTargetMode() == null
                ? Assignment.TargetMode.CLASS : assignment.getTargetMode();
        return mode == Assignment.TargetMode.CLASS
                || recipientIds != null && recipientIds.contains(student.getId());
    }
}
