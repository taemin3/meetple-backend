package com.meetple.backend.domain.moderation.analysis;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.auth.repository.AccessTokenValidationRepository;
import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.policy.AiModerationAuthenticator;
import com.meetple.backend.global.config.JacksonConfig;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.security.JwtAuthenticationEntryPoint;
import com.meetple.backend.global.security.JwtTokenProvider;
import com.meetple.backend.global.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportAnalysisToolController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JacksonConfig.class})
class ReportAnalysisToolControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiModerationAuthenticator authenticator;
    @MockitoBean ReportAnalysisService service;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean AccessTokenValidationRepository sessions;

    @Test
    void reportContextUsesServiceTokenInsteadOfLoginJwt() throws Exception {
        when(service.getContext(10L)).thenReturn(new Context(
                10L,
                ReportTargetType.CHAT_MESSAGE,
                ReportReason.SPAM,
                null,
                List.of(new Evidence(20L, ReportTargetType.CHAT_MESSAGE, "광고 메시지"))
        ));

        mvc.perform(get("/internal/ai/moderation/reports/10/context")
                        .header("X-AI-Service-Token", "service-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(10))
                .andExpect(jsonPath("$.data.evidence[0].evidenceId").value(20));

        verify(authenticator).verify("service-token");
        verifyNoInteractions(sessions);
    }

    @Test
    void missingServiceTokenIsForbiddenByControllerAuthenticator() throws Exception {
        doThrow(new BaseException(ErrorStatus.FORBIDDEN)).when(authenticator).verify(null);

        mvc.perform(get("/internal/ai/moderation/reports/10/context"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
