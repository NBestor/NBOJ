package com.onlinejudge.shared.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FrontendForwardControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FrontendForwardController()).build();
    }

    @Test
    void forwardsTeacherStudentProblemDetailToFrontendApp() throws Exception {
        mockMvc.perform(get("/code/teacher/classes/1/assignments/2/problems/3/students/4"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/code/index.html"));
    }

    @Test
    void keepsUnknownApiPathAsNotFound() throws Exception {
        mockMvc.perform(get("/code/api/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
