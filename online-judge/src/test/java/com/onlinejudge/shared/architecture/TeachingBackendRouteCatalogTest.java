package com.onlinejudge.shared.architecture;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TeachingBackendRouteCatalogTest {

    @Test
    void separatesTeachingMainFlowFromInternalSupportRoutes() {
        Map<String, TeachingBackendRole> roles = TeachingBackendRouteCatalog.routes().stream()
                .collect(Collectors.toMap(
                        entry -> entry.method() + " " + entry.path(),
                        TeachingBackendRouteCatalog.RouteEntry::role
                ));

        assertThat(roles.get("POST /api/code-runs")).isEqualTo(TeachingBackendRole.KEEP_MAIN);
        assertThat(roles.get("POST /api/submissions")).isEqualTo(TeachingBackendRole.KEEP_MAIN);
        assertThat(roles.get("GET /api/submissions/{id}/student-ai-feedback")).isEqualTo(TeachingBackendRole.KEEP_MAIN);
        assertThat(roles.get("GET /api/teacher/assignments/{assignmentId}/overview")).isEqualTo(TeachingBackendRole.KEEP_MAIN);
        assertThat(roles.get("GET /api/submissions/{id}/analysis")).isEqualTo(TeachingBackendRole.SIMPLIFY);
        assertThat(roles.get("GET /api/teacher/assignments/{assignmentId}/ai-quality")).isEqualTo(TeachingBackendRole.INTERNAL_ONLY);
        assertThat(roles.get("GET /api/teacher/assignments/{assignmentId}/diagnosis-eval-fixture-draft")).isEqualTo(TeachingBackendRole.INTERNAL_ONLY);
        assertThat(roles.get("GET /api/teacher/assignments/{assignmentId}/student-ai-feedback-observability")).isEqualTo(TeachingBackendRole.INTERNAL_ONLY);
    }

    @Test
    void internalRoutesCannotDriveStudentOrTeacherMainFlow() {
        assertThat(TeachingBackendRole.KEEP_MAIN.canDriveStudentOrTeacherMainFlow()).isTrue();
        assertThat(TeachingBackendRole.SIMPLIFY.canDriveStudentOrTeacherMainFlow()).isTrue();
        assertThat(TeachingBackendRole.INTERNAL_ONLY.canDriveStudentOrTeacherMainFlow()).isFalse();
        assertThat(TeachingBackendRole.REMOVE_LATER.canDriveStudentOrTeacherMainFlow()).isFalse();
    }

    @Test
    void routeReasonsAreNotEmpty() {
        assertThat(TeachingBackendRouteCatalog.routes())
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.reason()).isNotBlank());
    }
}
