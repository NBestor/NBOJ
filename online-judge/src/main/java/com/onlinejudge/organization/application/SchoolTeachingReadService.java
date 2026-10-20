package com.onlinejudge.organization.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.identity.application.AuditService;
import com.onlinejudge.identity.application.TeacherPrincipal;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.shared.web.PlatformApiException;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolTeachingReadService {
    private final TeacherAccountRepository accounts;
    private final ClassGroupRepository classes;
    private final StudentProfileRepository students;
    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final SubmissionAnalysisRepository analyses;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<TeachingClass> classes(TeacherPrincipal admin, String ip) {
        List<UUID> teacherIds = teacherIds(admin.schoolId());
        List<TeachingClass> result = classes.findAllByOrderByCreatedAtDesc().stream()
                .filter(group -> teacherIds.contains(group.getOwnerTeacherId()))
                .map(group -> new TeachingClass(group.getId(), group.getOwnerTeacherId(), ownerName(group.getOwnerTeacherId()),
                        group.getName(), group.getGrade(), group.getCreatedAt(),
                        students.findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(group.getId()).size(),
                        assignments.findByClassGroupIdOrderByCreatedAtDesc(group.getId()).size())).toList();
        audit.record(admin.id(), "SCHOOL_TEACHING_VIEWED", "SCHOOL", admin.schoolId(), "classes", ip);
        return result;
    }

    @Transactional(readOnly = true)
    public List<TeachingStudent> students(Long classId, TeacherPrincipal admin, String ip) {
        requireClass(classId, admin.schoolId());
        audit.record(admin.id(), "SCHOOL_TEACHING_DETAIL_VIEWED", "CLASS", classId, "students", ip);
        return students.findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(classId).stream()
                .map(student -> new TeachingStudent(student.getId(), student.getDisplayName(), student.getStudentNo(),
                        student.getStatus(), student.getCreatedAt(), student.getLastSeenAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<TeachingAssignment> assignments(Long classId, TeacherPrincipal admin, String ip) {
        requireClass(classId, admin.schoolId());
        audit.record(admin.id(), "SCHOOL_TEACHING_DETAIL_VIEWED", "CLASS", classId, "assignments", ip);
        return assignments.findByClassGroupIdOrderByCreatedAtDesc(classId).stream().map(this::assignment).toList();
    }

    @Transactional(readOnly = true)
    public List<TeachingSubmission> assignmentSubmissions(Long assignmentId, TeacherPrincipal admin, String ip) {
        requireAssignment(assignmentId, admin.schoolId());
        audit.record(admin.id(), "SCHOOL_TEACHING_DETAIL_VIEWED", "ASSIGNMENT", assignmentId, "submissions", ip);
        return submissions.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId).stream().map(this::submission).toList();
    }

    @Transactional(readOnly = true)
    public TeachingSubmission submission(Long submissionId, TeacherPrincipal admin, String ip) {
        Submission submission = submissions.findById(submissionId).orElseThrow(this::notFound);
        if (submission.getAssignmentId() == null) throw notFound();
        requireAssignment(submission.getAssignmentId(), admin.schoolId());
        audit.record(admin.id(), "SCHOOL_SUBMISSION_SOURCE_VIEWED", "SUBMISSION", submissionId, null, ip);
        return submission(submission);
    }

    @Transactional(readOnly = true)
    public String exportAssignment(Long assignmentId, TeacherPrincipal admin, String ip) {
        requireAssignment(assignmentId, admin.schoolId());
        StringBuilder csv = new StringBuilder("submissionId,studentProfileId,problemId,language,verdict,submittedAt,sourceCode\n");
        submissions.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId).forEach(value -> csv.append(value.getId()).append(',')
                .append(value.getStudentProfileId()).append(',').append(value.getProblemId()).append(',')
                .append(csv(value.getLanguageName())).append(',').append(value.getVerdict()).append(',')
                .append(value.getSubmittedAt()).append(',').append(csv(value.getSourceCode())).append('\n'));
        audit.record(admin.id(), "SCHOOL_TEACHING_EXPORTED", "ASSIGNMENT", assignmentId, "csv-with-source", ip);
        return csv.toString();
    }

    private TeachingAssignment assignment(Assignment value) {
        return new TeachingAssignment(value.getId(), value.getOwnerTeacherId(), value.getTitle(), value.getDescription(),
                value.getClassGroupId(), value.getTargetMode(), value.getStatus(), value.getStartsAt(), value.getEndsAt(), value.getCreatedAt());
    }
    private TeachingSubmission submission(Submission value) {
        Object analysis = analyses.findBySubmissionId(value.getId()).orElse(null);
        return new TeachingSubmission(value.getId(), value.getAssignmentId(), value.getProblemId(), value.getStudentProfileId(),
                value.getLanguageName(), value.getSourceCode(), value.getVerdict(), value.getExecutionTime(), value.getMemoryUsed(),
                value.getCompileOutput(), value.getErrorMessage(), value.getSubmittedAt(), analysis);
    }
    private void requireClass(Long id, UUID schoolId) {
        var group = classes.findById(id).orElseThrow(this::notFound);
        if (!teacherIds(schoolId).contains(group.getOwnerTeacherId())) throw notFound();
    }
    private void requireAssignment(Long id, UUID schoolId) {
        Assignment assignment = assignments.findById(id).orElseThrow(this::notFound);
        if (!teacherIds(schoolId).contains(assignment.getOwnerTeacherId())) throw notFound();
    }
    private List<UUID> teacherIds(UUID schoolId) {
        return accounts.findBySchoolIdAndRoleOrderByCreatedAtAsc(schoolId, TeacherAccount.Role.TEACHER)
                .stream().map(TeacherAccount::getId).toList();
    }
    private String ownerName(UUID ownerId) { return accounts.findById(ownerId).map(TeacherAccount::getDisplayName).orElse(""); }
    private PlatformApiException notFound() { return new PlatformApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "教学数据不存在"); }
    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    public record TeachingClass(Long id, UUID teacherId, String teacherName, String name, String grade,
                                LocalDateTime createdAt, int studentCount, int assignmentCount) { }
    public record TeachingStudent(Long id, String displayName, String studentNo, StudentProfile.RosterStatus status,
                                  LocalDateTime createdAt, LocalDateTime lastSeenAt) { }
    public record TeachingAssignment(Long id, UUID teacherId, String title, String description, Long classGroupId,
                                     Assignment.TargetMode targetMode, Assignment.AssignmentStatus status,
                                     LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime createdAt) { }
    public record TeachingSubmission(Long id, Long assignmentId, Long problemId, Long studentProfileId,
                                     String languageName, String sourceCode, Submission.Verdict verdict,
                                     Double executionTime, Integer memoryUsed, String compileOutput, String errorMessage,
                                     LocalDateTime submittedAt, Object analysis) { }
}
