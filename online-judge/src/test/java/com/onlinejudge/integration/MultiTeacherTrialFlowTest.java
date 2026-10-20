package com.onlinejudge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.aiquota.application.AiInvocationContext;
import com.onlinejudge.aiquota.application.AiQuotaService;
import com.onlinejudge.aiquota.domain.QuotaExhaustedException;
import com.onlinejudge.aiquota.domain.TeacherAiQuota;
import com.onlinejudge.aiquota.persistence.AiUsageEventRepository;
import com.onlinejudge.aiquota.persistence.TeacherAiQuotaRepository;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.organization.domain.School;
import com.onlinejudge.organization.persistence.SchoolRepository;
import com.onlinejudge.shared.security.CryptoSupport;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import com.onlinejudge.shared.security.StudentAccessTokenService;
import com.onlinejudge.shared.security.TeacherSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.YearMonth;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:multi-teacher-trial;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "TEACHER_DEV_AUTO_AUTH=false",
        "AI_ENABLED=false",
        "app.assignment-readiness.enforce=false",
        "app.content-seed.enabled=false"
})
class MultiTeacherTrialFlowTest {
    private static final String PASSWORD = "StrongPass123";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TeacherAccountRepository accounts;
    @Autowired StudentProfileRepository students;
    @Autowired ProblemRepository problems;
    @Autowired SubmissionRepository submissions;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AiQuotaService quotaService;
    @Autowired TeacherAiQuotaRepository quotas;
    @Autowired AiUsageEventRepository usageEvents;
    @Autowired SchoolRepository schools;

    @Test
    void twoTeachersCannotReadEachOthersClassesOrAssignments() throws Exception {
        TeacherAccount teacherA = activeTeacher("isolation-a");
        TeacherAccount teacherB = activeTeacher("isolation-b");
        teacherB.setRole(TeacherAccount.Role.TEACHER);
        accounts.save(teacherB);
        String cookieA = login(teacherA.getUsernameNormalized(), PASSWORD);
        String cookieB = login(teacherB.getUsernameNormalized(), PASSWORD);

        JsonNode classA = createClass(cookieA, "A 班");
        JsonNode classB = createClass(cookieB, "B 班");
        Problem problem = publicProblem("隔离测试题");
        JsonNode assignmentA = postJson("/api/teacher/assignments", cookieA, Map.of(
                "title", "A 班作业", "classGroupId", classA.path("id").asLong(),
                "targetMode", "CLASS", "status", "ACTIVE", "hintPolicy", "L2",
                "problemIds", List.of(problem.getId())));

        mockMvc.perform(get("/api/teacher/classes/{id}/students", classA.path("id").asLong()).header("Cookie", cookieB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/teacher/assignments/{id}", assignmentA.path("id").asLong()).header("Cookie", cookieB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/teacher/classes/{id}/students", classB.path("id").asLong()).header("Cookie", cookieA))
                .andExpect(status().isNotFound());

        JsonNode assignmentB = postJson("/api/teacher/assignments", cookieB, Map.of(
                "title", "B 班作业", "classGroupId", classB.path("id").asLong(),
                "targetMode", "CLASS", "status", "ACTIVE", "hintPolicy", "L2",
                "problemIds", List.of(problem.getId())));
        submissions.save(Submission.builder().assignmentId(assignmentA.path("id").asLong())
                .problemId(problem.getId()).languageId(71).sourceCode("print(1)")
                .verdict(Submission.Verdict.ACCEPTED).executionTime(0.1).build());
        submissions.save(Submission.builder().assignmentId(assignmentB.path("id").asLong())
                .problemId(problem.getId()).languageId(71).sourceCode("print(2)")
                .verdict(Submission.Verdict.WRONG_ANSWER).executionTime(0.2).build());
        JsonNode leaderboardA = getJson("/api/leaderboard/problems", cookieA);
        JsonNode leaderboardB = getJson("/api/leaderboard/problems", cookieB);
        assertThat(problemEntry(leaderboardA, problem.getId()).path("totalSubmissions").asInt()).isEqualTo(1);
        assertThat(problemEntry(leaderboardA, problem.getId()).path("acceptedSubmissions").asInt()).isEqualTo(1);
        assertThat(problemEntry(leaderboardB, problem.getId()).path("totalSubmissions").asInt()).isEqualTo(1);
        assertThat(problemEntry(leaderboardB, problem.getId()).path("acceptedSubmissions").asInt()).isZero();

        mockMvc.perform(post("/api/admin/teachers/{id}/transfer-ownership", teacherA.getId())
                        .header("Cookie", cookieB).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetTeacherId", teacherB.getId()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/teacher/classes/{id}/students", classA.path("id").asLong()).header("Cookie", cookieB))
                .andExpect(status().isNotFound());
    }

    @Test
    void strictRosterLoginDoesNotCreateStudentsAndTargetedAssignmentOnlyReachesRecipients() throws Exception {
        TeacherAccount teacher = activeTeacher("roster-owner");
        String teacherCookie = login(teacher.getUsernameNormalized(), PASSWORD);
        JsonNode classGroup = createClass(teacherCookie, "名单班");
        long classId = classGroup.path("id").asLong();
        String classCode = classGroup.path("joinCode").asText();

        postJson("/api/teacher/classes/import-commit", teacherCookie, Map.of(
                "classGroupId", classId, "format", "csv", "content", "姓名,学号\n张三,S001\n李四,S002"));
        List<StudentProfile> roster = students.findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(classId);
        assertThat(roster).hasSize(2).allMatch(student -> student.getStatus() == StudentProfile.RosterStatus.ACTIVE);

        long before = students.count();
        mockMvc.perform(post("/api/auth/student/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("classCode", classCode, "displayName", "不存在", "studentNo", "S999"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ROSTER_MISMATCH"));
        assertThat(students.count()).isEqualTo(before);

        StudentProfile recipient = roster.stream().filter(item -> "S001".equals(item.getStudentNo())).findFirst().orElseThrow();
        StudentProfile excluded = roster.stream().filter(item -> "S002".equals(item.getStudentNo())).findFirst().orElseThrow();
        String recipientCookie = studentLogin(classCode, recipient.getDisplayName(), recipient.getStudentNo());
        String excludedCookie = studentLogin(classCode, excluded.getDisplayName(), excluded.getStudentNo());
        Problem problem = publicProblem("定向测试题");
        JsonNode assignment = postJson("/api/teacher/assignments", teacherCookie, Map.of(
                "title", "定向作业", "classGroupId", classId, "targetMode", "STUDENTS",
                "studentProfileIds", List.of(recipient.getId()), "status", "ACTIVE", "hintPolicy", "L2",
                "problemIds", List.of(problem.getId())));

        mockMvc.perform(get("/api/student/assignments/{assignmentId}/profile/{studentId}/trajectory",
                        assignment.path("id").asLong(), recipient.getId())
                        .header("Cookie", recipientCookie + "; " + teacherCookie))
                .andExpect(status().isOk());

        MvcResult list = mockMvc.perform(get("/api/student/profile/{id}/assignments", excluded.getId())
                        .header("Cookie", excludedCookie)).andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(list.getResponse().getContentAsString()).size()).isZero();
        mockMvc.perform(post("/api/code-runs").header("Cookie", excludedCookie).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("problemId", assignment.path("tasks").get(0).path("problemId").asLong(),
                                "assignmentId", assignment.path("id").asLong(), "languageId", 71, "sourceCode", "print(1)", "stdin", ""))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_TARGETED"));

        mockMvc.perform(put("/api/teacher/classes/{classId}/students/{studentId}/status", classId, excluded.getId())
                        .header("Cookie", teacherCookie).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(get("/api/auth/student/session").header("Cookie", excludedCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void classAssignmentsDynamicallyInheritLateRosterAndExcludeHistoricalStudentsFromDenominator() throws Exception {
        TeacherAccount teacher = activeTeacher("dynamic-roster");
        String teacherCookie = login(teacher.getUsernameNormalized(), PASSWORD);
        JsonNode classGroup = createClass(teacherCookie, "动态名单班");
        long classId = classGroup.path("id").asLong();
        String classCode = classGroup.path("joinCode").asText();
        postJson("/api/teacher/classes/import-commit", teacherCookie, Map.of(
                "classGroupId", classId, "format", "csv", "content", "姓名,学号\n先到学生,S101"));
        StudentProfile first = students.findByClassGroupIdOrderByStudentNoAscDisplayNameAsc(classId).get(0);
        Problem problem = publicProblem("动态继承测试题");
        JsonNode assignment = postJson("/api/teacher/assignments", teacherCookie, Map.of(
                "title", "动态全班作业", "classGroupId", classId, "targetMode", "CLASS",
                "status", "ACTIVE", "hintPolicy", "L2", "problemIds", List.of(problem.getId())));
        assertThat(assignment.path("targetCount").asLong()).isEqualTo(1);

        postJson("/api/teacher/classes/import-commit", teacherCookie, Map.of(
                "classGroupId", classId, "format", "csv", "content", "姓名,学号\n后来学生,S102"));
        StudentProfile lateStudent = students.findFirstByClassGroupIdAndStudentNoIgnoreCase(classId, "S102").orElseThrow();
        String lateCookie = studentLogin(classCode, lateStudent.getDisplayName(), lateStudent.getStudentNo());
        JsonNode inheritedAssignments = getJson("/api/student/profile/" + lateStudent.getId() + "/assignments", lateCookie);
        assertThat(inheritedAssignments.findValuesAsText("id")).contains(String.valueOf(assignment.path("id").asLong()));
        JsonNode expanded = getJson("/api/teacher/assignments/" + assignment.path("id").asLong(), teacherCookie);
        assertThat(expanded.path("targetCount").asLong()).isEqualTo(2);

        submissions.save(Submission.builder().assignmentId(assignment.path("id").asLong())
                .studentProfileId(first.getId()).problemId(assignment.path("tasks").get(0).path("problemId").asLong())
                .languageId(71).sourceCode("print(1)").verdict(Submission.Verdict.ACCEPTED).build());
        mockMvc.perform(put("/api/teacher/classes/{classId}/students/{studentId}/status", classId, first.getId())
                        .header("Cookie", teacherCookie).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        JsonNode contracted = getJson("/api/teacher/assignments/" + assignment.path("id").asLong(), teacherCookie);
        assertThat(contracted.path("targetCount").asLong()).isEqualTo(1);
        JsonNode overview = getJson("/api/teacher/assignments/" + assignment.path("id").asLong() + "/overview", teacherCookie);
        assertThat(overview.path("rosterStudentCount").asLong()).isEqualTo(1);
        assertThat(overview.path("attemptCount").asLong()).isZero();
        JsonNode historical = overview.path("students").get(0);
        assertThat(historical.path("studentProfileId").asLong()).isEqualTo(first.getId());
        assertThat(historical.path("currentRoster").asBoolean()).isFalse();
        assertThat(historical.path("rosterHistoryLabel").asText()).isEqualTo("非当前名单历史记录");
    }

    @Test
    void administratorApprovalSuspensionAndTemporaryPasswordRevokeSessions() throws Exception {
        TeacherAccount admin = activeTeacher("trial-admin");
        admin.setRole(TeacherAccount.Role.SCHOOL_ADMIN);
        accounts.save(admin);
        School adminSchool = testSchool();
        adminSchool.setAdminAccountId(admin.getId());
        schools.save(adminSchool);
        String adminCookie = login(admin.getUsernameNormalized(), PASSWORD);
        String username = "pending-" + UUID.randomUUID().toString().substring(0, 8);

        JsonNode application = postJson("/api/auth/teacher/register", null, Map.of(
                "username", username, "password", PASSWORD, "displayName", "待审教师",
                "schoolRegistrationCode", "TRIAL-SCHOOL-CODE"));
        mockMvc.perform(post("/api/auth/teacher/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));

        mockMvc.perform(post("/api/admin/teacher-applications/{id}/approve", application.path("id").asText()).header("Cookie", adminCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        String teacherCookie = login(username, PASSWORD);
        mockMvc.perform(get("/api/teacher/classes").header("Cookie", teacherCookie)).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/teachers/{id}/suspend", application.path("id").asText()).header("Cookie", adminCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/teacher/classes").header("Cookie", teacherCookie)).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/teachers/{id}/restore", application.path("id").asText()).header("Cookie", adminCookie))
                .andExpect(status().isOk());
        MvcResult reset = mockMvc.perform(post("/api/admin/teachers/{id}/reset-password", application.path("id").asText()).header("Cookie", adminCookie))
                .andExpect(status().isOk()).andReturn();
        String temporaryPassword = objectMapper.readTree(reset.getResponse().getContentAsString()).path("temporaryPassword").asText();
        String temporaryCookie = login(username, temporaryPassword);
        mockMvc.perform(get("/api/teacher/classes").header("Cookie", temporaryCookie))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void fiveFailedLoginsLockTheTeacherForFifteenMinutes() throws Exception {
        TeacherAccount teacher = activeTeacher("locked-teacher");
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/teacher/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("username", teacher.getUsernameNormalized(), "password", "WrongPass999"))))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/teacher/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", teacher.getUsernameNormalized(), "password", PASSWORD))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertThat(accounts.findById(teacher.getId()).orElseThrow().getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void aiQuotaChargesOnlySuccessfulIdempotentActionsAndNeverOverReserves() throws Exception {
        UUID teacherId = activeTeacher("quota-teacher").getId();
        YearMonth month = YearMonth.now(AiQuotaService.BILLING_ZONE);
        quotas.saveAndFlush(TeacherAiQuota.forMonth(teacherId, month, 2));

        AiInvocationContext firstContext = new AiInvocationContext(teacherId, 1L, 2L, 3L, "STUDENT_FEEDBACK", "feedback-3");
        AiQuotaService.Reservation failed = quotaService.reserve(firstContext);
        quotaService.settleFailure(failed, "test", "model", "provider unavailable");
        assertThat(quotas.findByTeacherIdAndQuotaMonth(teacherId, month.toString()).orElseThrow().getUsedUnits()).isZero();

        AiQuotaService.Reservation success = quotaService.reserve(firstContext);
        quotaService.settleSuccess(success, "test", "model", 10, 20);
        AiQuotaService.Reservation duplicate = quotaService.reserve(firstContext);
        assertThat(duplicate.reserved()).isFalse();
        quotaService.settleSuccess(duplicate, "test", "model", 10, 20);
        TeacherAiQuota afterIdempotentRetry = quotas.findByTeacherIdAndQuotaMonth(teacherId, month.toString()).orElseThrow();
        assertThat(afterIdempotentRetry.getUsedUnits()).isEqualTo(1);
        assertThat(usageEvents.findAll().stream().filter(event -> teacherId.equals(event.getTeacherId()) && event.isCharged())).hasSize(1);

        UUID concurrentTeacher = activeTeacher("quota-concurrent").getId();
        quotas.saveAndFlush(TeacherAiQuota.forMonth(concurrentTeacher, month, 2));
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<AiQuotaService.Reservation>> calls = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                int invocation = index;
                calls.add(() -> quotaService.reserve(new AiInvocationContext(concurrentTeacher, null, 8L, null,
                        "COACH", "coach-" + invocation)));
            }
            var results = executor.invokeAll(calls);
            List<AiQuotaService.Reservation> reservations = new ArrayList<>();
            int exhausted = 0;
            for (var result : results) {
                try {
                    reservations.add(result.get());
                } catch (java.util.concurrent.ExecutionException failure) {
                    assertThat(failure.getCause()).isInstanceOf(QuotaExhaustedException.class);
                    exhausted++;
                }
            }
            assertThat(reservations).hasSize(2);
            assertThat(exhausted).isEqualTo(4);
            assertThat(quotas.findByTeacherIdAndQuotaMonth(concurrentTeacher, month.toString()).orElseThrow().getReservedUnits()).isEqualTo(2);
            for (AiQuotaService.Reservation reservation : reservations) {
                quotaService.settleFailure(reservation, "test", "model", "cleanup");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void problemGovernanceCopiesImmutableVersionsAndFreezesPrivateAssignmentSnapshots() throws Exception {
        TeacherAccount owner = activeTeacher("problem-owner");
        TeacherAccount observer = activeTeacher("problem-observer");
        TeacherAccount admin = activeTeacher("problem-admin");
        admin.setRole(TeacherAccount.Role.PLATFORM_ADMIN);
        admin.setSchoolId(null);
        admin.setSchoolName("平台");
        accounts.save(admin);
        String ownerCookie = login(owner.getUsernameNormalized(), PASSWORD);
        String observerCookie = login(observer.getUsernameNormalized(), PASSWORD);
        String adminCookie = login(admin.getUsernameNormalized(), PASSWORD);

        JsonNode draft = postJson("/api/teacher/problems", ownerCookie, problemPayload("私有送审题"));
        long draftId = draft.path("id").asLong();
        assertThat(getJson("/api/teacher/problems", observerCookie).findValuesAsText("id")).doesNotContain(String.valueOf(draftId));
        mockMvc.perform(get("/api/problems/{id}", draftId)).andExpect(status().isNotFound());

        JsonNode review = postJson("/api/teacher/problems/" + draftId + "/submit-review", ownerCookie, Map.of());
        assertThat(review.path("id").asLong()).isNotEqualTo(draftId);
        assertThat(review.path("sourceProblemId").asLong()).isEqualTo(draftId);
        assertThat(review.path("versionState").asText()).isEqualTo("REVIEW_PENDING");
        mockMvc.perform(put("/api/teacher/problems/{id}", review.path("id").asLong()).header("Cookie", ownerCookie)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(problemPayload("不能覆盖送审版本"))))
                .andExpect(status().isForbidden());

        JsonNode shared = postJson("/api/admin/problem-reviews/" + review.path("id").asLong() + "/approve", adminCookie, Map.of());
        assertThat(shared.path("scope").asText()).isEqualTo("SHARED");
        assertThat(getJson("/api/teacher/problems", observerCookie).findValuesAsText("id"))
                .contains(String.valueOf(shared.path("id").asLong()));
        JsonNode publicVersion = postJson("/api/admin/problem-reviews/" + shared.path("id").asLong() + "/publish-public", adminCookie, Map.of());
        assertThat(publicVersion.path("id").asLong()).isNotEqualTo(shared.path("id").asLong());
        assertThat(publicVersion.path("scope").asText()).isEqualTo("PUBLIC");
        assertThat(getJson("/api/problems/catalog", null).toString()).contains("私有送审题");

        JsonNode privateAssignmentDraft = postJson("/api/teacher/problems", ownerCookie, problemPayload("作业快照题"));
        JsonNode ownerClass = createClass(ownerCookie, "快照班");
        JsonNode assignment = postJson("/api/teacher/assignments", ownerCookie, Map.of(
                "title", "快照作业", "classGroupId", ownerClass.path("id").asLong(), "targetMode", "CLASS",
                "status", "ACTIVE", "hintPolicy", "L2", "problemIds", List.of(privateAssignmentDraft.path("id").asLong())));
        long frozenId = assignment.path("tasks").get(0).path("problemId").asLong();
        Problem frozen = problems.findById(frozenId).orElseThrow();
        assertThat(frozen.getVersionState()).isEqualTo(Problem.VersionState.FROZEN);
        assertThat(frozen.getSourceProblemId()).isEqualTo(privateAssignmentDraft.path("id").asLong());
        assertThat(problems.findById(privateAssignmentDraft.path("id").asLong()).orElseThrow().getVersionState())
                .isEqualTo(Problem.VersionState.DRAFT);
    }

    private TeacherAccount activeTeacher(String prefix) {
        String username = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        School school = testSchool();
        TeacherAccount account = TeacherAccount.active(UUID.randomUUID(), username, passwordEncoder.encode(PASSWORD),
                prefix, school.getName(), Instant.now());
        account.setSchoolId(school.getId());
        return accounts.save(account);
    }

    private School testSchool() {
        return schools.findByNameIgnoreCase("试点学校").orElseGet(() -> schools.save(School.create(UUID.randomUUID(),
                "试点学校", CryptoSupport.sha256("TRIAL-SCHOOL-CODE"), UUID.randomUUID(),
                TeacherAccount.BOOTSTRAP_ADMIN_ID, Instant.now())));
    }

    private Problem publicProblem(String title) {
        return problems.save(Problem.builder().ownerTeacherId(TeacherAccount.BOOTSTRAP_ADMIN_ID)
                .scope(Problem.Scope.PUBLIC).versionState(Problem.VersionState.PUBLISHED)
                .title(title + UUID.randomUUID()).description("测试").difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000).memoryLimit(128000).build());
    }

    private Map<String, Object> problemPayload(String title) {
        return Map.of("title", title, "description", "题目说明", "difficulty", "EASY",
                "timeLimit", 1000, "memoryLimit", 128000,
                "testCases", List.of(Map.of("input", "1", "expectedOutput", "1", "hidden", false)));
    }

    private JsonNode getJson(String path, String cookie) throws Exception {
        var request = get(path);
        if (cookie != null) request.header("Cookie", cookie);
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode problemEntry(JsonNode leaderboard, long problemId) {
        for (JsonNode entry : leaderboard) {
            if (entry.path("problemId").asLong() == problemId) return entry;
        }
        throw new AssertionError("排行榜缺少题目 " + problemId);
    }

    private JsonNode createClass(String cookie, String name) throws Exception {
        return postJson("/api/teacher/classes", cookie, Map.of("name", name));
    }

    private JsonNode postJson(String path, String cookie, Object body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (cookie != null) request.header("Cookie", cookie);
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String login(String username, String password) throws Exception {
        TeacherAccount account = accounts.findByUsernameNormalized(username).orElseThrow();
        MvcResult result = mockMvc.perform(post("/api/auth/account/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password,
                                "portal", account.getRole().name()))))
                .andExpect(status().isOk()).andReturn();
        return cookie(result, TeacherSessionService.COOKIE_NAME);
    }

    private String studentLogin(String classCode, String displayName, String studentNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/student/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("classCode", classCode, "displayName", displayName, "studentNo", studentNo))))
                .andExpect(status().isOk()).andReturn();
        return cookie(result, StudentAccessTokenService.COOKIE_NAME);
    }

    private String cookie(MvcResult result, String name) {
        return java.util.Arrays.stream(result.getResponse().getHeaders("Set-Cookie").toArray(String[]::new))
                .map(value -> value.split(";", 2)[0]).filter(value -> value.startsWith(name + "=")).findFirst().orElseThrow();
    }
}
