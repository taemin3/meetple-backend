package com.meetple.backend.domain.member.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pageIsPublicAndNamesMeetple() throws Exception {
        mockMvc.perform(get("/account-deletion/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/account-deletion/index.html"));

        String page = mockMvc.perform(get("/account-deletion/index.html"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("밋플 계정 삭제 요청", "Meetple");
    }
}
