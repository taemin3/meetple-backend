package com.meetple.backend.domain.push.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.push.entity.PushDevicePlatform;
import com.meetple.backend.domain.push.repository.PushDeviceTokenRepository;
import com.meetple.backend.global.security.JwtTokenProvider;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushDeviceTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PushDeviceTokenRepository pushDeviceTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String accessToken;

    @BeforeEach
    void setUp() {
        pushDeviceTokenRepository.deleteAll();
        memberRepository.deleteAll();

        Member member = memberRepository.save(
                Member.createUser("push@meetple.com", "encoded-password", "push-user", null)
        );
        String sessionId = "push-device-controller-session";
        accessToken = jwtTokenProvider.createAccessToken(member, sessionId);
        refreshTokenRepository.save(member.getId(), sessionId, "refresh-token", Duration.ofMinutes(10));
    }

    @Test
    void registersRefreshesAndDeletesInstallationToken() throws Exception {
        mockMvc.perform(post("/api/v1/push/device-tokens")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("token-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(pushDeviceTokenRepository.findAll()).singleElement().satisfies(token -> {
            assertThat(token.getDeviceId()).isEqualTo("installation-1");
            assertThat(token.getToken()).isEqualTo("token-1");
            assertThat(token.getPlatform()).isEqualTo(PushDevicePlatform.ANDROID);
        });

        mockMvc.perform(post("/api/v1/push/device-tokens")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("token-2")))
                .andExpect(status().isOk());

        assertThat(pushDeviceTokenRepository.findAll()).singleElement()
                .satisfies(token -> assertThat(token.getToken()).isEqualTo("token-2"));

        mockMvc.perform(delete("/api/v1/push/device-tokens/{deviceId}", "installation-1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(pushDeviceTokenRepository.count()).isZero();
    }

    @Test
    void rejectsBlankToken() throws Exception {
        mockMvc.perform(post("/api/v1/push/device-tokens")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(" ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String requestJson(String token) {
        return """
                {
                  "deviceId": "installation-1",
                  "token": "%s",
                  "platform": "ANDROID"
                }
                """.formatted(token);
    }
}
