package com.meetple.backend.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.response.ErrorStatus;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final AtomicLong MEMBER_ID_SEQUENCE = new AtomicLong(1L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AccessTokenBlacklistRepository accessTokenBlacklistRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String accessToken;

    @BeforeEach
    void setUp() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID_SEQUENCE.getAndIncrement());
        accessToken = jwtTokenProvider.createAccessToken(member, "security-config-test-session");
        refreshTokenRepository.save(
                member.getId(),
                "security-config-test-session",
                "refresh-token",
                Duration.ofMinutes(10)
        );
    }

    @Test
    void publicEndpointAllowsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void publicEndpointWithInvalidTokenAllowsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/health")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorizedApiResponse() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.UNAUTHORIZED.getCode()));
    }

    @Test
    void locationSearchWithoutTokenReturnsUnauthorizedApiResponse() throws Exception {
        mockMvc.perform(get("/api/v1/locations/search")
                        .param("query", "여의도공원"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.UNAUTHORIZED.getCode()));
    }

    @Test
    void locationReverseWithoutTokenReturnsUnauthorizedApiResponse() throws Exception {
        mockMvc.perform(get("/api/v1/locations/reverse")
                        .param("latitude", "37.5219")
                        .param("longitude", "126.9245"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.UNAUTHORIZED.getCode()));
    }

    @Test
    void imageUploadUrlWithoutTokenReturnsUnauthorizedApiResponse() throws Exception {
        mockMvc.perform(post("/api/v1/images/upload-url"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.UNAUTHORIZED.getCode()));
    }

    @Test
    void protectedEndpointWithInvalidTokenReturnsInvalidTokenApiResponse() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.INVALID_TOKEN.getCode()));
    }

    @Test
    void protectedEndpointWithBlacklistedTokenReturnsInvalidTokenApiResponse() throws Exception {
        accessTokenBlacklistRepository.save(accessToken, Duration.ofMinutes(10));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.INVALID_TOKEN.getCode()));
    }

    @Test
    void controllerIllegalArgumentExceptionIsNotConvertedToInvalidToken() throws Exception {
        mockMvc.perform(get("/test/protected/illegal-argument")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.INTERNAL_SERVER_ERROR.getCode()));
    }

    @TestConfiguration
    static class ProtectedTestControllerConfig {

        @Bean
        ProtectedTestController protectedTestController() {
            return new ProtectedTestController();
        }
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/test/protected/illegal-argument")
        void throwIllegalArgumentException() {
            throw new IllegalArgumentException("business error");
        }
    }
}
