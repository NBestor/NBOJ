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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAssignmentInsightsService {

    public static final String RANKING_RULE = "PROGRESS_ONLY_V1";

    private final AssignmentRepository assignmentRepository;
    private final AssignmentTaskRepository assignmentTaskRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentAssignmentSubmissionQueryRepository submissionQueryRepository;
    private final ProblemRepository problemRepository;

    public StudentAssignmentLeaderboardResponse getLeaderboard(Long assignmentId, Long studentProfileId) {
        AccessContext context = requireAccess(assignmentId, studentProfileId);
        List<AssignmentTask> tasks = assignmentTaskRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId);
        Set<Long> taskProblemIds = tasks.stream()
                .map(AssignmentTask::getProblemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<StudentProfile> classStudents = studentProfileRepository
                .findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(context.assignment().getClassGroupId());
        Set<Long> classStudentIds = classStudents.stream()
                .map(StudentProfile::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<Submission>> submissionsByStudent = submissionRepository
                .findByAssignmentIdOrderBySubmittedAtDesc(assignmentId)
                .stream()
                .filter(item -> item.getStudentProfileId() != null && classStudentIds.contains(item.getStudentProfileId()))
                .filter(item -> taskProblemIds.contains(item.getProblemId()))
                .collect(Collectors.groupingBy(
                        Submission::getStudentProfileId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<RankAccumulator> ranked = classStudents.stream()
                .map(student -> summarize(student, submissionsByStudent.getOrDefault(student.getId(), List.of()), taskProblemIds))
                .sorted(Comparator.comparingInt(RankAccumulator::completedTasks).reversed())
                .toList();

        int rank = 0;
        int previousCompleted = Integer.MIN_VALUE;
        List<StudentAssignmentLeaderboardResponse.Row> rows = new java.util.ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            RankAccumulator item = ranked.get(index);
            if (item.completedTasks() != previousCompleted) {
                rank = index + 1;
                previousCompleted = item.completedTasks();
            }
            boolean current = Objects.equals(item.student().getId(), studentProfileId);
            rows.add(StudentAssignmentLeaderboardResponse.Row.builder()
                    .rank(rank)
                    .studentProfileId(current ? item.student().getId() : null)
                    .displayName(current ? item.student().getDisplayName() + "（我）" : maskName(item.student().getDisplayName()))
                    .completedTasks(item.completedTasks())
                    .totalTasks(taskProblemIds.size())
                    .attemptCount(item.attemptCount())
                    .lastSubmittedAt(item.lastSubmittedAt())
                    .currentStudent(current)
                    .build());
        }

        StudentAssignmentLeaderboardResponse.Row currentRow = rows.stream()
                .filter(StudentAssignmentLeaderboardResponse.Row::isCurrentStudent)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("当前学生不在本班名单中"));
        int tiedStudentCount = (int) rows.stream()
                .filter(item -> item.getCompletedTasks() == currentRow.getCompletedTasks())
                .count();

        return StudentAssignmentLeaderboardResponse.builder()
                .assignmentId(assignmentId)
                .totalStudents(rows.size())
                .totalTasks(taskProblemIds.size())
                .myRank(currentRow.getRank())
                .tiedStudentCount(tiedStudentCount)
                .rankingRule(RANKING_RULE)
                .generatedAt(LocalDateTime.now())
                .rows(rows)
                .build();
    }

    public StudentAssignmentSubmissionPageResponse getSubmissions(Long assignmentId,
                                                                  Long studentProfileId,
                                                                  Long problemId,
                                                                  Boolean accepted,
                                                                  Submission.Verdict verdict,
                                                                  String languageName,
                                                                  Long submissionId,
                                                                  int page,
                                                                  int size) {
        requireAccess(assignmentId, studentProfileId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedLanguage = languageName == null || languageName.isBlank() ? null : languageName.trim();
        Page<Submission> result = submissionQueryRepository.findStudentAssignmentSubmissions(
                assignmentId,
                studentProfileId,
                problemId,
                accepted,
                Submission.Verdict.ACCEPTED,
                verdict,
                normalizedLanguage,
                submissionId,
                PageRequest.of(safePage, safeSize)
        );
        List<Submission> allStudentSubmissions = submissionRepository
                .findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(assignmentId, studentProfileId);
        List<Long> problemIds = result.getContent().stream()
                .map(Submission::getProblemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Problem> problemsById = problemRepository.findAllById(problemIds)
                .stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));

        List<StudentAssignmentSubmissionPageResponse.Item> items = result.getContent().stream()
                .map(item -> StudentAssignmentSubmissionPageResponse.Item.builder()
                        .id(item.getId())
                        .problemId(item.getProblemId())
                        .problemTitle(problemsById.containsKey(item.getProblemId())
                                ? problemsById.get(item.getProblemId()).getTitle()
                                : "题目 #" + item.getProblemId())
                        .verdict(item.getVerdict())
                        .languageName(item.getLanguageName())
                        .executionTime(item.getExecutionTime())
                        .memoryUsed(item.getMemoryUsed())
                        .submittedAt(item.getSubmittedAt())
                        .build())
                .toList();

        return StudentAssignmentSubmissionPageResponse.builder()
                .assignmentId(assignmentId)
                .totalSubmissionCount(allStudentSubmissions.size())
                .acceptedSubmissionCount(allStudentSubmissions.stream()
                        .filter(item -> item.getVerdict() == Submission.Verdict.ACCEPTED)
                        .count())
                .distinctProblemCount(allStudentSubmissions.stream()
                        .map(Submission::getProblemId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count())
                .latestSubmittedAt(allStudentSubmissions.stream()
                        .map(Submission::getSubmittedAt)
                        .filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(null))
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .page(result.getNumber())
                .size(result.getSize())
                .items(items)
                .build();
    }

    private AccessContext requireAccess(Long assignmentId, Long studentProfileId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("作业不存在: " + assignmentId));
        StudentProfile student = studentProfileRepository.findById(studentProfileId)
                .orElseThrow(() -> new IllegalArgumentException("学生不存在: " + studentProfileId));
        if (assignment.getClassGroupId() == null
                || student.getClassGroupId() == null
                || !assignment.getClassGroupId().equals(student.getClassGroupId())) {
            throw new AccessDeniedException("只能访问本班作业数据");
        }
        return new AccessContext(assignment, student);
    }

    private RankAccumulator summarize(StudentProfile student,
                                      List<Submission> submissions,
                                      Set<Long> taskProblemIds) {
        int completedTasks = (int) submissions.stream()
                .filter(item -> item.getVerdict() == Submission.Verdict.ACCEPTED)
                .map(Submission::getProblemId)
                .filter(taskProblemIds::contains)
                .distinct()
                .count();
        LocalDateTime lastSubmittedAt = submissions.stream()
                .map(Submission::getSubmittedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new RankAccumulator(student, completedTasks, submissions.size(), lastSubmittedAt);
    }

    private String maskName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "同学";
        }
        int firstCodePoint = displayName.codePointAt(0);
        return new String(Character.toChars(firstCodePoint)) + "同学";
    }

    private record AccessContext(Assignment assignment, StudentProfile student) {
    }

    private record RankAccumulator(StudentProfile student,
                                   int completedTasks,
                                   int attemptCount,
                                   LocalDateTime lastSubmittedAt) {
    }
}
