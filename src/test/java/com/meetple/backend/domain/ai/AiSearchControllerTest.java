package com.meetple.backend.domain.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.GlobalExceptionHandler;
import com.meetple.backend.global.security.AuthenticatedMember;
import java.util.List;
import java.util.stream.Collectors;
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
                AiSearchContracts.Status.INPUT_REQUIRED, "위치를 선택해주세요.", null, List.of(), "keyword"));
        var mvc = MockMvcBuilders.standaloneSetup(new AiSearchController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/meetings/ai-search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"러닝 모임\",\"radiusMeters\":3000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INPUT_REQUIRED"));
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

    @Test void acceptsOnlyConfiguredEmbeddingDimensionsAndFiniteValues() {
        var valid = new java.util.ArrayList<>(java.util.Collections.nCopies(1536, 0.0));
        valid.set(0, 1.0);
        assertThatCode(() -> AiSearchToolController.validateEmbedding(valid)).doesNotThrowAnyException();
        assertThatCode(() -> AiSearchToolController.validateEmbedding(null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> AiSearchToolController.validateEmbedding(List.of(0.1)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> AiSearchToolController.validateEmbedding(
                java.util.Collections.nCopies(1536, 0.0))).isInstanceOf(BadRequestException.class);
        var nonFinite = new java.util.ArrayList<>(java.util.Collections.nCopies(1536, 0.0));
        nonFinite.set(0, Double.NaN);
        assertThatThrownBy(() -> AiSearchToolController.validateEmbedding(nonFinite))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void internalToolBindsAndForwardsQueryEmbedding() throws Exception {
        var repository = mock(AiMeetingSearchRepository.class);
        var capability = new AiSearchCapability(AiSearchCapabilityTest.PROPERTIES);
        when(repository.search(eq(42L), any(), any())).thenReturn(new AiSearchContracts.Candidates(List.of(), false));
        var mvc = MockMvcBuilders.standaloneSetup(new AiSearchToolController(capability, repository))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String embedding = "1.0," + java.util.Collections.nCopies(1535, "0.0").stream()
                .collect(Collectors.joining(","));

        mvc.perform(post("/internal/ai/search/meetings")
                        .header("X-AI-Service-Token", AiSearchCapabilityTest.SERVICE_KEY)
                        .header("X-Meetple-Capability", capability.issue(42))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"러닝","category":"운동",
                                 "startsAt":"2027-01-03T00:00:00","endsBefore":"2027-01-05T00:00:00",
                                 "startsAtTime":null,"endsBeforeTime":null,
                                 "latitude":37.5,"longitude":127.0,"radiusMeters":3000,
                                 "queryEmbedding":[%s]}
                                """.formatted(embedding)))
                .andExpect(status().isOk());

        verify(repository).search(eq(42L), any(), argThat(values -> values.size() == 1536));
    }
}
