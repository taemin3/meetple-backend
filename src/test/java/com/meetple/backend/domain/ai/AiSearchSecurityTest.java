package com.meetple.backend.domain.ai;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meetple.backend.domain.auth.repository.AccessTokenValidationRepository;
import com.meetple.backend.global.config.JacksonConfig;
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

@WebMvcTest({AiSearchController.class, AiSearchToolController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JacksonConfig.class})
class AiSearchSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiSearchService service;
    @MockitoBean AiSearchCapability capability;
    @MockitoBean AiMeetingSearchRepository repository;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean AccessTokenValidationRepository sessions;

    @Test void publicSearchRequiresLoginEvenWithInternalServiceHeader() throws Exception {
        mvc.perform(post("/api/v1/meetings/ai-search").header("X-AI-Service-Token", "anything"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void onlyExactInternalPathsSkipLoginJwtAndReachCapabilityValidation() throws Exception {
        when(capability.verify("service", "signed")).thenReturn(42L);
        when(repository.categories()).thenReturn(List.of("운동"));
        mvc.perform(get("/internal/ai/search/categories")
                        .header("X-AI-Service-Token", "service").header("X-Meetple-Capability", "signed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value("운동"));
        verify(capability).verify("service", "signed");
        mvc.perform(get("/internal/ai/search/other")).andExpect(status().isUnauthorized());
        verifyNoInteractions(sessions);
    }
}
