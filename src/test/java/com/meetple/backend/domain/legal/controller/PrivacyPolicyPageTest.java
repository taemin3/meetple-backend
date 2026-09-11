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
class PrivacyPolicyPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pageIsPublicAndContainsRequiredLinks() throws Exception {
        mockMvc.perform(get("/privacy-policy/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/privacy-policy/index.html"));

        String page = mockMvc.perform(get("/privacy-policy/index.html"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(page).contains(
                "밋플 개인정보 처리방침",
                "meetple99@gmail.com",
                "/account-deletion",
                "장소 검색어",
                "채팅 메시지 식별자",
                "데이터베이스 최종 스냅샷",
                "VPC 보안그룹 접근 제한"
        );
    }
}
