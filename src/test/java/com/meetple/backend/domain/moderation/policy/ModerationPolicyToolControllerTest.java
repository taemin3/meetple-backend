package com.meetple.backend.domain.moderation.policy;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.auth.repository.AccessTokenValidationRepository;
import com.meetple.backend.global.config.JacksonConfig;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.security.JwtAuthenticationEntryPoint;
import com.meetple.backend.global.security.JwtAccessDeniedHandler;
import com.meetple.backend.global.security.JwtTokenProvider;
import com.meetple.backend.global.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ModerationPolicyToolController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JacksonConfig.class})
class ModerationPolicyToolControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiModerationAuthenticator authenticator;
    @MockitoBean ModerationPolicyService service;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean AccessTokenValidationRepository sessions;

    @Test
    void internalPolicyApiUsesServiceTokenInsteadOfLoginJwt() throws Exception {
        when(service.findEmbeddingJobs("model", 10))
                .thenReturn(new ModerationPolicyContracts.EmbeddingJobs(List.of()));

        mvc.perform(get("/internal/ai/moderation/policies/embedding-jobs")
                        .header("X-AI-Service-Token", "service-token")
                        .param("embeddingModel", "model")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        verify(authenticator).verify("service-token");
        verifyNoInteractions(sessions);
    }

    @Test
    void missingServiceTokenIsForbiddenByControllerAuthenticator() throws Exception {
        doThrow(new BaseException(ErrorStatus.FORBIDDEN)).when(authenticator).verify(null);

        mvc.perform(get("/internal/ai/moderation/policies/embedding-jobs")
                        .param("embeddingModel", "model"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void unrelatedInternalModerationPathStillRequiresLoginJwt() throws Exception {
        mvc.perform(get("/internal/ai/moderation/other"))
                .andExpect(status().isUnauthorized());
    }
}
