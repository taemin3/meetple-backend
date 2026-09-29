package com.meetple.backend.domain.ai;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.exception.GlobalExceptionHandler;
import com.meetple.backend.global.security.AuthenticatedMember;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiSearchControllerTest {
    private final AiSearchService service = mock(AiSearchService.class);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void delegatesAuthenticatedMemberAndWrapsResponse() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(42L, "test@example.test", MemberRole.USER), null, List.of()));
        when(service.search(eq(42L), any())).thenReturn(new AiSearchContracts.Response(
                AiSearchContracts.Status.NEEDS_CLARIFICATION, "위치를 선택해주세요.", null, List.of(), "keyword"));
        var mvc = MockMvcBuilders.standaloneSetup(new AiSearchController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/meetings/ai-search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"러닝 모임\",\"radiusMeters\":3000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NEEDS_CLARIFICATION"));
    }

    @Test void rejectsIncompleteCoordinatesBeforeCallingModel() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AiSearchController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/meetings/ai-search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"러닝\",\"latitude\":37.5,\"radiusMeters\":3000}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void internalToolRequiresItsOwnCapabilityEvenWithoutLoginJwt() throws Exception {
        var repository = mock(AiMeetingSearchRepository.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AiSearchToolController(
                        new AiSearchCapability(AiSearchCapabilityTest.PROPERTIES), repository))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/internal/ai/search/meetings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"러닝\",\"category\":null,\"startsAt\":\"2026-10-03T00:00:00\","
                                + "\"endsBefore\":\"2026-10-05T00:00:00\",\"latitude\":37.5,\"longitude\":127,\"radiusMeters\":3000}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
}
