package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.StudentProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentTargetingPolicyTest {

    private final AssignmentTargetingPolicy policy = new AssignmentTargetingPolicy();

    @Test
    void classTargetDynamicallyIncludesEveryActiveStudentInTheClass() {
        Assignment assignment = Assignment.builder()
                .classGroupId(7L)
                .targetMode(Assignment.TargetMode.CLASS)
                .build();
        StudentProfile student = StudentProfile.builder()
                .id(11L)
                .classGroupId(7L)
                .status(StudentProfile.RosterStatus.ACTIVE)
                .build();

        assertThat(policy.isTargeted(assignment, student, Set.of())).isTrue();

        student.setStatus(StudentProfile.RosterStatus.INACTIVE);
        assertThat(policy.isTargeted(assignment, student, Set.of())).isFalse();
    }

    @Test
    void selectedTargetRequiresAnExplicitSameClassRecipient() {
        Assignment assignment = Assignment.builder()
                .classGroupId(7L)
                .targetMode(Assignment.TargetMode.STUDENTS)
                .build();
        StudentProfile student = StudentProfile.builder()
                .id(11L)
                .classGroupId(7L)
                .status(StudentProfile.RosterStatus.ACTIVE)
                .build();

        assertThat(policy.isTargeted(assignment, student, Set.of(11L))).isTrue();
        assertThat(policy.isTargeted(assignment, student, Set.of(12L))).isFalse();
    }
}
