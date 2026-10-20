package com.onlinejudge.classroom.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudentIdentityServiceTest {

    private final StudentIdentityService service = new StudentIdentityService();

    @Test
    void buildsStableClassIdentityKeyWithoutAssignmentScope() {
        String key = service.buildPreferredIdentityKey(7L, 9L, "高一1班", "张三", " 08 ");

        assertThat(key).isEqualTo("class:9:08");
    }

    @Test
    void fallsBackToClassNameWhenClassGroupIsMissing() {
        String key = service.buildPreferredIdentityKey(7L, null, " 高一 1 班 ", "张三", "");

        assertThat(key).isEqualTo("class-name:高一1班:张三");
    }

    @Test
    void keepsLegacyAssignmentKeyAsLastResort() {
        String key = service.buildPreferredIdentityKey(7L, null, "", "张三", "08");

        assertThat(key).isEqualTo("7::张三:08");
    }
}
