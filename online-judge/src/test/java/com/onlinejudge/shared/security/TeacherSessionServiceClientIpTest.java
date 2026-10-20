package com.onlinejudge.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherSessionServiceClientIpTest {

    @Test
    void ignoresUntrustedForwardedForHeaderForRateLimitIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        assertThat(TeacherSessionService.clientIp(request)).isEqualTo("192.0.2.10");
    }
}
