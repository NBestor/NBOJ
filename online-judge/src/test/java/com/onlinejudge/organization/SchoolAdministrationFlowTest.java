package com.onlinejudge.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.shared.security.TeacherSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:school-administration-flow;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "TEACHER_DEV_AUTO_AUTH=false",
        "BOOTSTRAP_ADMIN_USERNAME=platform-coder",
        "BOOTSTRAP_ADMIN_PASSWORD=PlatformPass123",
        "BOOTSTRAP_ADMIN_DISPLAY_NAME=Coder",
        "AI_ENABLED=false",
        "app.content-seed.enabled=false"
})
class SchoolAdministrationFlowTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void provisionsSchoolThenDelegatesTeacherAndQuotaGovernance() throws Exception {
        String platformCookie = login("platform-coder", "PlatformPass123", "PLATFORM_ADMIN").cookie();
        mockMvc.perform(get("/api/school-admin/overview").header("Cookie", platformCookie))
                .andExpect(status().isForbidden());

        JsonNode created = postJson("/api/platform-admin/schools", platformCookie, Map.of(
                "schoolName", "温州试点中学", "adminUsername", "wz-school-admin",
                "adminDisplayName", "校级管理员", "monthlyAiUnits", 20));
        String schoolId = created.path("school").path("id").asText();
        String registrationCode = created.path("schoolRegistrationCode").asText();
        String temporaryPassword = created.path("temporaryPassword").asText();
        assertThat(registrationCode).startsWith("SCH-");

        LoginResult firstLogin = login("wz-school-admin", temporaryPassword, "SCHOOL_ADMIN");
        assertThat(firstLogin.body().path("mustChangePassword").asBoolean()).isTrue();
        mockMvc.perform(post("/api/auth/account/change-password").header("Cookie", firstLogin.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", temporaryPassword, "newPassword", "SchoolAdmin123"))))
                .andExpect(status().isOk());
        String schoolAdminCookie = login("wz-school-admin", "SchoolAdmin123", "SCHOOL_ADMIN").cookie();

        mockMvc.perform(post("/api/auth/account/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "wz-school-admin", "password", "SchoolAdmin123", "portal", "TEACHER"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PORTAL_ROLE_MISMATCH"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(post("/api/auth/teacher/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "wrong-code-teacher", "password", "TeacherPass123",
                                "displayName", "错误学校", "schoolRegistrationCode", "invalid"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SCHOOL_CODE"));

        JsonNode teacher = postJson("/api/auth/teacher/register", null, Map.of(
                "username", "wz-teacher-01", "password", "TeacherPass123", "displayName", "张老师",
                "schoolRegistrationCode", registrationCode));
        String teacherId = teacher.path("id").asText();
        assertThat(teacher.path("schoolId").asText()).isEqualTo(schoolId);
        postJson("/api/school-admin/teacher-applications/" + teacherId + "/approve", schoolAdminCookie, Map.of());
        JsonNode usage = putJson("/api/school-admin/teachers/" + teacherId + "/quota", schoolAdminCookie,
                Map.of("baseUnits", 8, "additionalUnits", 0));
        assertThat(usage.path("baseUnits").asInt()).isEqualTo(8);
        assertThat(login("wz-teacher-01", "TeacherPass123", "TEACHER").body().path("schoolId").asText()).isEqualTo(schoolId);

        JsonNode overview = getJson("/api/school-admin/overview", schoolAdminCookie);
        assertThat(overview.path("quota").path("totalUnits").asInt()).isEqualTo(20);
        assertThat(overview.path("quota").path("allocatedUnits").asInt()).isEqualTo(8);

        postJson("/api/platform-admin/schools/" + schoolId + "/suspend", platformCookie, Map.of());
        mockMvc.perform(get("/api/school-admin/overview").header("Cookie", schoolAdminCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/account/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "wz-teacher-01", "password", "TeacherPass123", "portal", "TEACHER"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SCHOOL_SUSPENDED"));
    }

    @Test
    void redirectsLegacyAdminWorkspaceRoutesToCanonicalCodePath() throws Exception {
        mockMvc.perform(get("/app/platform-admin/schools/example"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(redirectedUrl("/code/platform-admin/schools/example"));
        mockMvc.perform(get("/app/school-admin/teaching/classes/1"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(redirectedUrl("/code/school-admin/teaching/classes/1"));
    }

    @Test
    void schoolAdminReadModelIsScopedAcrossTwoSchoolsAndCannotMutateTeachingData() throws Exception {
        String platformCookie = login("platform-coder", "PlatformPass123", "PLATFORM_ADMIN").cookie();
        SchoolFixture first = createSchool(platformCookie, "跨校隔离甲", "isolation-school-admin-a");
        SchoolFixture second = createSchool(platformCookie, "跨校隔离乙", "isolation-school-admin-b");
        String firstAdminCookie = activateSchoolAdmin(first, "SchoolAdminA123");
        String secondAdminCookie = activateSchoolAdmin(second, "SchoolAdminB123");

        String firstTeacherCookie = registerApproveAndLoginTeacher(first, firstAdminCookie,
                "isolation-school-teacher-a", "SchoolTeacherA123", "甲校教师");
        registerApproveAndLoginTeacher(second, secondAdminCookie,
                "isolation-school-teacher-b", "SchoolTeacherB123", "乙校教师");
        JsonNode firstClass = postJson("/api/teacher/classes", firstTeacherCookie, Map.of("name", "甲校一班"));
        long classId = firstClass.path("id").asLong();

        mockMvc.perform(get("/api/school-admin/teaching/classes/{id}/students", classId)
                        .header("Cookie", firstAdminCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/school-admin/teaching/classes/{id}/students", classId)
                        .header("Cookie", secondAdminCookie)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/school-admin/teaching/classes/{id}/students", classId)
                        .header("Cookie", platformCookie)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/teacher/classes").header("Cookie", firstAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "禁止代建班级"))))
                .andExpect(status().isForbidden());
    }

    private SchoolFixture createSchool(String platformCookie, String name, String adminUsername) throws Exception {
        JsonNode created = postJson("/api/platform-admin/schools", platformCookie, Map.of(
                "schoolName", name, "adminUsername", adminUsername, "adminDisplayName", name + "管理员", "monthlyAiUnits", 10));
        return new SchoolFixture(adminUsername, created.path("temporaryPassword").asText(),
                created.path("schoolRegistrationCode").asText());
    }

    private String activateSchoolAdmin(SchoolFixture fixture, String newPassword) throws Exception {
        LoginResult login = login(fixture.adminUsername(), fixture.temporaryPassword(), "SCHOOL_ADMIN");
        mockMvc.perform(post("/api/auth/account/change-password").header("Cookie", login.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                                "currentPassword", fixture.temporaryPassword(), "newPassword", newPassword))))
                .andExpect(status().isOk());
        return login(fixture.adminUsername(), newPassword, "SCHOOL_ADMIN").cookie();
    }

    private String registerApproveAndLoginTeacher(SchoolFixture school, String schoolAdminCookie,
                                                   String username, String password, String displayName) throws Exception {
        JsonNode teacher = postJson("/api/auth/teacher/register", null, Map.of(
                "username", username, "password", password, "displayName", displayName,
                "schoolRegistrationCode", school.registrationCode()));
        postJson("/api/school-admin/teacher-applications/" + teacher.path("id").asText() + "/approve",
                schoolAdminCookie, Map.of());
        return login(username, password, "TEACHER").cookie();
    }

    private LoginResult login(String username, String password, String portal) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/account/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password, "portal", portal))))
                .andExpect(status().isOk()).andReturn();
        return new LoginResult(cookie(result), objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)));
    }
    private JsonNode postJson(String path, String cookie, Object body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json(body));
        if (cookie != null) request.header("Cookie", cookie);
        return body(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
    }
    private JsonNode putJson(String path, String cookie, Object body) throws Exception {
        return body(mockMvc.perform(put(path).header("Cookie", cookie).contentType(MediaType.APPLICATION_JSON)
                .content(json(body))).andExpect(status().isOk()).andReturn());
    }
    private JsonNode getJson(String path, String cookie) throws Exception {
        return body(mockMvc.perform(get(path).header("Cookie", cookie)).andExpect(status().isOk()).andReturn());
    }
    private JsonNode body(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)); }
    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String cookie(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).isNotBlank();
        return header.substring(0, header.indexOf(';'));
    }
    private record LoginResult(String cookie, JsonNode body) { }
    private record SchoolFixture(String adminUsername, String temporaryPassword, String registrationCode) { }
}
