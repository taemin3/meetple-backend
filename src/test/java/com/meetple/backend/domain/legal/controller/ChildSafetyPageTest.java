package com.meetple.backend.domain.legal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChildSafetyPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pageIsPublicAndContainsRequiredStandards() throws Exception {
        mockMvc.perform(get("/child-safety/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/child-safety/index.html"));

        String page = mockMvc.perform(get("/child-safety/index.html"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(page).contains(
                "밋플 아동 안전 표준",
                "Child Sexual Abuse and Exploitation",
                "Child Sexual Abuse Material",
                "앱 내 신고",
                "관할 수사기관",
                "meetple99@gmail.com",
                "/privacy-policy"
        );
    }

    @Test
    void pageIgnoresInvalidAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/child-safety/")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isOk());
    }
}
