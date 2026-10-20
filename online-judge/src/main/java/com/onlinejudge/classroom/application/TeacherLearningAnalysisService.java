package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.AssignmentTask;
import com.onlinejudge.classroom.domain.ClassGroup;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.dto.ClassGroupResponse;
import com.onlinejudge.classroom.dto.ClassLearningOverviewResponse;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.AssignmentTaskRepository;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherLearningAnalysisService {

    private final ClassGroupRepository classGroupRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentTaskRepository assignmentTaskRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SubmissionRepository submissionRepository;

    @Transactional(readOnly = true)
    public ClassLearningOverviewResponse getClassOverview(Long classGroupId) {
        ClassGroup classGroup = classGroupRepository.findById(classGroupId)
                .orElseThrow(() -> new IllegalArgumentException("班级不存在: " + classGroupId));
        List<Assignment> assignments = assignmentRepository.findByClassGroupIdOrderByCreatedAtDesc(classGroupId);
        List<Long> assignmentIds = assignments.stream()
                .map(Assignment::getId)
                .filter(Objects::nonNull)
                .toList();
        List<StudentProfile> roster = studentProfileRepository
                .findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(classGroupId);
        Set<Long> rosterIds = roster.stream()
                .map(StudentProfile::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<AssignmentTask> tasks = assignmentIds.isEmpty()
                ? List.of()
                : assignmentTaskRepository.findByAssignmentIdIn(assignmentIds);
        List<Submission> submissions = assignmentIds.isEmpty()
                ? List.of()
                : submissionRepository.findByAssignmentIdIn(assignmentIds);
        List<Submission> legalSubmissions = submissions.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getAssignmentId() != null)
                .filter(item -> item.getStudentProfileId() != null && rosterIds.contains(item.getStudentProfileId()))
                .toList();

        Map<Long, List<AssignmentTask>> tasksByAssignment = tasks.stream()
                .filter(item -> item.getAssignmentId() != null)
                .collect(Collectors.groupingBy(
                        AssignmentTask::getAssignmentId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, List<Submission>> submissionsByAssignment = legalSubmissions.stream()
                .collect(Collectors.groupingBy(
                        Submission::getAssignmentId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ClassLearningOverviewResponse.AssignmentSummary> assignmentSummaries = assignments.stream()
                .map(assignment -> summarizeAssignment(
                        assignment,
                        rosterIds,
                        tasksByAssignment.getOrDefault(assignment.getId(), List.of()),
                        submissionsByAssignment.getOrDefault(assignment.getId(), List.of())
                ))
                .toList();
        long submittedStudentCount = legalSubmissions.stream()
                .map(Submission::getStudentProfileId)
                .distinct()
                .count();

        return ClassLearningOverviewResponse.builder()
                .classGroup(ClassGroupResponse.from(classGroup))
                .assignmentCount(assignments.size())
                .rosterStudentCount(rosterIds.size())
                .submittedStudentCount(submittedStudentCount)
                .unsubmittedStudentCount(Math.max(0, rosterIds.size() - submittedStudentCount))
                .assignments(assignmentSummaries)
                .build();
    }

    private ClassLearningOverviewResponse.AssignmentSummary summarizeAssignment(
            Assignment assignment,
            Set<Long> rosterIds,
            List<AssignmentTask> tasks,
            List<Submission> submissions
    ) {
        long submittedStudentCount = submissions.stream()
                .map(Submission::getStudentProfileId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        List<Long> requiredProblemIds = requiredProblemIds(tasks);
        Map<Long, List<Submission>> byStudent = submissions.stream()
                .collect(Collectors.groupingBy(
                        Submission::getStudentProfileId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        long completedRequiredStudentCount = requiredProblemIds.isEmpty()
                ? 0
                : byStudent.values().stream()
                .filter(items -> completedRequired(items, requiredProblemIds))
                .count();
        return ClassLearningOverviewResponse.AssignmentSummary.builder()
                .assignmentId(assignment.getId())
                .title(assignment.getTitle())
                .status(assignment.getStatus() == null ? null : assignment.getStatus().name())
                .createdAt(assignment.getCreatedAt())
                .problemCount(tasks.stream().map(AssignmentTask::getProblemId).filter(Objects::nonNull).distinct().count())
                .rosterStudentCount(rosterIds.size())
                .submittedStudentCount(submittedStudentCount)
                .unsubmittedStudentCount(Math.max(0, rosterIds.size() - submittedStudentCount))
                .completedRequiredStudentCount(completedRequiredStudentCount)
                .build();
    }

    private List<Long> requiredProblemIds(Collection<AssignmentTask> tasks) {
        List<Long> required = tasks.stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequired()))
                .map(AssignmentTask::getProblemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!required.isEmpty()) {
            return required;
        }
        return tasks.stream()
                .map(AssignmentTask::getProblemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean completedRequired(List<Submission> submissions, List<Long> requiredProblemIds) {
        Set<Long> acceptedProblemIds = submissions.stream()
                .filter(item -> item.getVerdict() == Submission.Verdict.ACCEPTED)
                .map(Submission::getProblemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return acceptedProblemIds.containsAll(requiredProblemIds);
    }
}
