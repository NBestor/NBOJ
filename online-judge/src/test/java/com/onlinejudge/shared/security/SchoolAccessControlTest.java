package com.onlinejudge.shared.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.ClassGroup;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryGrowthAgentService;
import com.onlinejudge.learning.standardlibrary.application.StandardLibraryGrowthProposal;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.domain.TeacherSession;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.identity.persistence.TeacherSessionRepository;
import com.onlinejudge.organization.domain.School;
import com.onlinejudge.organization.persistence.SchoolRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:school-access-control;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "TEACHER_PASSWORD=test-teacher-password",
        "TEACHER_SESSION_SECRET=test-teacher-session-secret-1234567890",
        "TEACHER_DEV_AUTO_AUTH=false",
        "STUDENT_TOKEN_SECRET=test-student-token-secret-1234567890",
        "AI_ENABLED=false",
        "app.content-migration.enabled=true"
})
class SchoolAccessControlTest {
    private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000410");
    private static final UUID SCHOOL_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000411");
    private static final UUID TEACHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000412");
    private static final String TEACHER_USERNAME = "school-access-teacher";
    private static final String TEACHER_PASSWORD = "TeacherAccess123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ClassGroupRepository classGroupRepository;

    @Autowired
    StudentProfileRepository studentProfileRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    AiStandardLibraryGrowthAgentService growthAgentService;

    @Autowired
    TeacherAccountRepository teacherAccounts;

    @Autowired
    TeacherSessionRepository teacherSessions;

    @Autowired
    SchoolRepository schools;

    @BeforeEach
    void ensureSchoolTeacher() {
        if (schools.findById(SCHOOL_ID).isEmpty()) {
            Instant now = Instant.now();
            schools.save(School.create(SCHOOL_ID, "权限测试学校", "a".repeat(64), SCHOOL_ADMIN_ID,
                    TeacherAccount.BOOTSTRAP_ADMIN_ID, now));
            teacherAccounts.save(TeacherAccount.schoolAdmin(SCHOOL_ADMIN_ID, "school-access-admin",
                    passwordEncoder.encode("SchoolAdmin123"), "测试校管", SCHOOL_ID, "权限测试学校", now));
        }
        if (teacherAccounts.findById(TEACHER_ID).isEmpty()) {
            TeacherAccount teacher = TeacherAccount.active(TEACHER_ID, TEACHER_USERNAME,
                    passwordEncoder.encode(TEACHER_PASSWORD), "测试教师", "权限测试学校", Instant.now());
            teacher.setSchoolId(SCHOOL_ID);
            teacherAccounts.save(teacher);
        }
    }

    @Test
    void teacherApisRequireSessionAndLoginSetsCookie() throws Exception {
        mockMvc.perform(get("/api/teacher/assignments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/teacher/ai-standard-library/items"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/teacher/ai-standard-library/growth-candidates"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/teacher/ai-standard-library/growth-candidates/governance-summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/teacher/informatics-knowledge/tree"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEACHER_USERNAME + "\",\"password\":\"" + TEACHER_PASSWORD + "\",\"portal\":\"TEACHER\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(TeacherSessionService.COOKIE_NAME));
    }

    @Test
    void activeTeacherSessionRenewsBeforeItsIdleExpiry() throws Exception {
        String cookie = loginTeacherCookie();
        TeacherSession session = teacherSessions.findAll().stream()
                .filter(candidate -> TEACHER_ID.equals(candidate.getTeacherId()))
                .max(java.util.Comparator.comparing(TeacherSession::getCreatedAt))
                .orElseThrow();
        Instant nearExpiry = Instant.now().plusSeconds(30);
        session.setExpiresAt(nearExpiry);
        teacherSessions.saveAndFlush(session);

        mockMvc.perform(get("/api/auth/account/session")
                        .header("Cookie", cookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(TeacherSessionService.COOKIE_NAME));

        TeacherSession renewed = teacherSessions.findById(session.getId()).orElseThrow();
        assertThat(renewed.getExpiresAt()).isAfter(Instant.now().plusSeconds(11 * 3600));
    }

    @Test
    void codeApiPrefixPreservesAccountAuthenticationAndPublicApiRouting() throws Exception {
        mockMvc.perform(get("/code/api/teacher/assignments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/code/api/auth/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEACHER_USERNAME + "\",\"password\":\"" + TEACHER_PASSWORD + "\",\"portal\":\"TEACHER\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(TeacherSessionService.COOKIE_NAME));

        mockMvc.perform(get("/code/api/system/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void loggedTeacherCanQueryEditDisableAndEnableStandardLibraryItems() throws Exception {
        String cookie = loginTeacherCookie();

        MvcResult listResult = mockMvc.perform(get("/api/teacher/ai-standard-library/items")
                        .param("query", "MP_IO_ONLY_READS_ONE_CASE")
                        .header("Cookie", cookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode first = objectMapper.readTree(listResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).get(0);
        long id = first.path("id").asLong();

        Map<String, Object> updatePayload = new LinkedHashMap<>();
        updatePayload.put("layer", "MISTAKE_POINT");
        updatePayload.put("code", "MP_IO_ONLY_READS_ONE_CASE");
        updatePayload.put("category", "易错点/输入输出");
        updatePayload.put("name", "多组数据只处理一组测试更新");
        updatePayload.put("description", "测试更新");
        updatePayload.put("studentExplanation", "先核对读入和输出。");
        updatePayload.put("teacherExplanation", "教师可要求学生逐行对照题面格式。");
        updatePayload.put("skillUnitCode", "SK_IO_STRUCTURE_MAPPING");
        updatePayload.put("mistakeType", "IO_FORMAT");
        updatePayload.put("commonMisconception", "把样例单组输入当成完整格式。");
        updatePayload.put("severity", "HIGH");
        updatePayload.put("applicableLanguages", List.of("PYTHON", "CPP17"));
        updatePayload.put("relatedItems", List.of("SK_IO_STRUCTURE_MAPPING"));
        updatePayload.put("knowledgeNodeCodes", List.of("BASIC.IO.MULTI_CASE.显式 T 组循环"));
        updatePayload.put("prerequisiteKnowledgeCodes", List.of("BASIC.LOOP.FOR.循环次数计算"));
        updatePayload.put("enabled", true);

        MvcResult updateResult = mockMvc.perform(put("/api/teacher/ai-standard-library/items/" + id)
                        .header("Cookie", cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(updateResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("knowledgeNodeCodes")
                .get(0)
                .asText()).isEqualTo("BASIC.IO.MULTI_CASE.显式 T 组循环");
        assertThat(objectMapper.readTree(updateResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("skillUnitCode")
                .asText()).isEqualTo("SK_IO_STRUCTURE_MAPPING");

        mockMvc.perform(get("/api/teacher/informatics-knowledge/tree")
                        .header("Cookie", cookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/teacher/ai-standard-library/items/" + id + "/disable")
                        .header("Cookie", cookie))
                .andExpect(status().isOk());

        MvcResult disabledResult = mockMvc.perform(get("/api/teacher/ai-standard-library/items/" + id)
                        .header("Cookie", cookie))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(disabledResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("enabled").asBoolean())
                .isFalse();

        mockMvc.perform(post("/api/teacher/ai-standard-library/items/" + id + "/enable")
                        .header("Cookie", cookie))
                .andExpect(status().isOk());
    }

    @Test
    void teacherCannotSaveIncompleteMistakePoint() throws Exception {
        String cookie = loginTeacherCookie();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("layer", "MISTAKE_POINT");
        payload.put("code", "MP_TEST_INCOMPLETE");
        payload.put("category", "易错点/测试");
        payload.put("name", "缺少能力点关联");
        payload.put("description", "这是一个测试易错点。");
        payload.put("mistakeType", "BOUNDARY");
        payload.put("commonMisconception", "学生常见误解。");
        payload.put("knowledgeNodeCodes", List.of("BASIC.LOOP.BOUNDARY.左闭右开"));
        payload.put("enabled", true);

        MvcResult result = mockMvc.perform(post("/api/teacher/ai-standard-library/items")
                        .header("Cookie", cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("error")
                .asText()).contains("易错点必须关联能力点");
    }

    @Test
    void teacherCannotSaveSkillUnitWithoutKnowledgeNode() throws Exception {
        String cookie = loginTeacherCookie();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("layer", "SKILL_UNIT");
        payload.put("code", "SK_TEST_NO_KNOWLEDGE");
        payload.put("category", "能力点/测试");
        payload.put("name", "测试能力点");
        payload.put("description", "这是一个测试能力点。");
        payload.put("studentExplanation", "学习目标。");
        payload.put("enabled", true);

        MvcResult result = mockMvc.perform(post("/api/teacher/ai-standard-library/items")
                        .header("Cookie", cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("error")
                .asText()).contains("能力点必须关联至少一个知识节点");
    }

    @Test
    void loggedTeacherCanReviewIgnoreAndMergeGrowthCandidates() throws Exception {
        String cookie = loginTeacherCookie();
        var candidate = growthAgentService.propose(StandardLibraryGrowthProposal.builder()
                .suggestedCode("MP_TEACHER_REVIEW_GROWTH_CANDIDATE")
                .suggestedName("教师确认扩库候选")
                .layer(AiStandardLibraryLayer.MISTAKE_POINT)
                .suggestedPath(List.of("BASIC", "DEBUG", "TRACE"))
                .sourceProblemId(10L)
                .sourceSubmissionId(100L)
                .similarExistingItemCodes(List.of("SK_AI_GROWTH_REVIEW"))
                .changeReason("教师端应能看到候选、忽略候选或合并到正式库。")
                .evidenceRefs(List.of("code:trace_missing", "judge:wrong_answer"))
                .confidence(0.93)
                .build());

        MvcResult listResult = mockMvc.perform(get("/api/teacher/ai-standard-library/growth-candidates")
                        .header("Cookie", cookie))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(listResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).get(0)
                .path("suggestedCode")
                .asText()).isEqualTo("MP_TEACHER_REVIEW_GROWTH_CANDIDATE");

        MvcResult summaryResult = mockMvc.perform(get("/api/teacher/ai-standard-library/growth-candidates/governance-summary")
                        .header("Cookie", cookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode summary = objectMapper.readTree(summaryResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(summary.path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(summary.path("reviewPendingCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(summary.path("highFrequencyPaths").isArray()).isTrue();

        MvcResult mergeResult = mockMvc.perform(post("/api/teacher/ai-standard-library/growth-candidates/" + candidate.getId() + "/merge")
                        .header("Cookie", cookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode merged = objectMapper.readTree(mergeResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(merged.path("status").asText()).isEqualTo("MERGED");
        assertThat(merged.path("rollbackInfo").asText()).contains("回滚");

        var ignoredCandidate = growthAgentService.propose(StandardLibraryGrowthProposal.builder()
                .suggestedCode("MP_TEACHER_IGNORE_GROWTH_CANDIDATE")
                .suggestedName("教师忽略扩库候选")
                .layer(AiStandardLibraryLayer.MISTAKE_POINT)
                .suggestedPath(List.of("BASIC", "DEBUG", "IGNORE"))
                .sourceProblemId(11L)
                .sourceSubmissionId(101L)
                .changeReason("这个候选用于验证忽略操作。")
                .evidenceRefs(List.of("code:ignore"))
                .confidence(0.82)
                .build());
        Map<String, Object> ignorePayload = Map.of("teacherNote", "暂不进入标准库");

        MvcResult ignoreResult = mockMvc.perform(post("/api/teacher/ai-standard-library/growth-candidates/" + ignoredCandidate.getId() + "/ignore")
                        .header("Cookie", cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ignorePayload)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(ignoreResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("status")
                .asText()).isEqualTo("IGNORED");
    }

    @Test
    void studentTokenGuardsStudentScopedApis() throws Exception {
        String classCode = "CLASS101";
        ClassGroup group = classGroupRepository.save(ClassGroup.builder()
                .name("高一试点班")
                .grade("高一")
                .teacherName("老师")
                .ownerTeacherId(TEACHER_ID)
                .joinCodeHash(passwordEncoder.encode(classCode))
                .build());
        StudentProfile rosterStudent = studentProfileRepository.save(StudentProfile.builder()
                .classGroupId(group.getId())
                .displayName("张三")
                .studentNo("01")
                .identityKey("class:" + group.getId() + "|student:01")
                .status(StudentProfile.RosterStatus.ACTIVE)
                .build());

        MvcResult login = mockMvc.perform(post("/api/auth/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + classCode + "\",\"displayName\":\"张三\",\"studentNo\":\"01\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(StudentAccessTokenService.COOKIE_NAME))
                .andReturn();

        JsonNode student = objectMapper.readTree(login.getResponse().getContentAsString());
        long studentId = student.path("id").asLong();
        assertThat(studentId).isEqualTo(rosterStudent.getId());
        String cookie = login.getResponse().getHeader("Set-Cookie").split(";", 2)[0];

        mockMvc.perform(get("/api/student/profile/" + studentId + "/assignments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/student/profile/" + studentId + "/assignments")
                        .header("Cookie", cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/profile/" + (studentId + 1) + "/assignments")
                        .header("Cookie", cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void submissionOwnedByStudentRequiresMatchingToken() throws Exception {
        Submission submission = submissionRepository.save(Submission.builder()
                .problemId(1L)
                .studentProfileId(99L)
                .languageId(71)
                .languageName("Python 3")
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.ACCEPTED)
                .memoryUsed(0)
                .executionTime(0.0)
                .build());

        mockMvc.perform(get("/api/submissions/" + submission.getId()))
                .andExpect(status().isUnauthorized());
    }

    private String loginTeacherCookie() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEACHER_USERNAME + "\",\"password\":\"" + TEACHER_PASSWORD + "\",\"portal\":\"TEACHER\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String cookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).isNotBlank();
        return cookie.split(";", 2)[0];
    }
}
