package com.meetple.backend.domain.moderation.policy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.auth.repository.AccessTokenValidationRepository;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.config.JacksonConfig;
import com.meetple.backend.global.security.AuthenticatedAccessToken;
import com.meetple.backend.global.security.AuthenticatedMember;
import com.meetple.backend.global.security.JwtAccessDeniedHandler;
import com.meetple.backend.global.security.JwtAuthenticationEntryPoint;
import com.meetple.backend.global.security.JwtTokenProvider;
import com.meetple.backend.global.security.JwtTokenSession;
import com.meetple.backend.global.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminModerationPolicyController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JacksonConfig.class})
class AdminModerationPolicyControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminModerationPolicyService service;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean AccessTokenValidationRepository sessions;

    private AuthenticatedMember administrator;

    @BeforeEach
    void setUp() {
        administrator = new AuthenticatedMember(7L, "admin@meetple.com", MemberRole.ADMIN);
        var authentication = new UsernamePasswordAuthenticationToken(
                administrator,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        given(jwt.authenticateAccessToken("admin-token")).willReturn(new AuthenticatedAccessToken(
                authentication,
                new JwtTokenSession(7L, "admin-session"),
                Instant.now().plusSeconds(3600)
        ));
        given(sessions.getStatus("admin-token", 7L, "admin-session"))
                .willReturn(AccessTokenValidationRepository.Status.ACTIVE);
    }

    @Test
    void administratorCanCreateInactivePolicyVersion() throws Exception {
        given(service.createPolicy(anyLong(), any())).willReturn(policyDetail());

        mvc.perform(post("/api/v1/admin/moderation-policies")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyCode": "COMMUNITY-SPAM",
                                  "title": "스팸 금지",
                                  "policyType": "SPAM",
                                  "targetType": "ALL",
                                  "effectiveFrom": "2026-10-03",
                                  "clauses": [
                                    {"clauseCode": "SPAM-1", "content": "반복 광고 금지"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.policyId").value(10))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.embeddingModel").value("text-embedding-3-small"));

        verify(service).createPolicy(eq(administrator.id()), any());
    }

    private static AdminModerationPolicyContracts.PolicyDetail policyDetail() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 9, 0);
        return new AdminModerationPolicyContracts.PolicyDetail(
                10L,
                "COMMUNITY-SPAM",
                "스팸 금지",
                ModerationPolicyType.SPAM,
                ModerationPolicyTargetType.ALL,
                LocalDate.of(2026, 10, 3),
                null,
                false,
                1,
                "text-embedding-3-small",
                1,
                List.of(),
                List.of(),
                now,
                now
        );
    }
}
