package com.meetple.backend.domain.meeting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.meeting.dto.request.CreateMeetingParticipationRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingParticipationResponse;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.service.MeetingParticipationService;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.exception.GlobalExceptionHandler;
import com.meetple.backend.global.response.PageResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MeetingParticipationControllerTest {

    @Mock
    private MeetingParticipationService participationService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MeetingParticipationController(participationService))
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver()
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        authenticate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void applyParticipationReturnsCreatedApiResponse() throws Exception {
        given(participationService.applyParticipation(
                eq(2L),
                eq(10L),
                any(CreateMeetingParticipationRequest.class)
        )).willReturn(participationResponse(ParticipationStatus.PENDING));

        mockMvc.perform(post("/api/v1/meetings/10/participations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMeetingParticipationRequest(
                                "I want to join."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.CREATED.getCode()))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getMeetingParticipationsReturnsPagedApiResponse() throws Exception {
        given(participationService.getMeetingParticipations(eq(2L), eq(10L), eq("PENDING"), any()))
                .willReturn(PageResponse.from(new PageImpl<>(
                        List.of(participationResponse(ParticipationStatus.PENDING)),
                        PageRequest.of(0, 20),
                        1
                )));

        mockMvc.perform(get("/api/v1/meetings/10/participations")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data.content[0].id").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void approveParticipationReturnsApiResponse() throws Exception {
        given(participationService.approveParticipation(2L, 10L, 100L))
                .willReturn(participationResponse(ParticipationStatus.APPROVED));

        mockMvc.perform(patch("/api/v1/meetings/10/participations/100/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void rejectParticipationReturnsApiResponse() throws Exception {
        given(participationService.rejectParticipation(2L, 10L, 100L))
                .willReturn(participationResponse(ParticipationStatus.REJECTED));

        mockMvc.perform(patch("/api/v1/meetings/10/participations/100/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void cancelParticipationReturnsApiResponse() throws Exception {
        given(participationService.cancelParticipation(2L, 10L, 100L))
                .willReturn(participationResponse(ParticipationStatus.CANCELED));

        mockMvc.perform(patch("/api/v1/meetings/10/participations/100/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        verify(participationService).cancelParticipation(2L, 10L, 100L);
    }

    private void authenticate() {
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(
                2L,
                "runner@meetple.com",
                MemberRole.USER
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedMember, null, List.of())
        );
    }

    private MeetingParticipationResponse participationResponse(ParticipationStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 0);
        return new MeetingParticipationResponse(
                100L,
                10L,
                "Weekend running",
                2L,
                "runner",
                status,
                "I want to join.",
                status == ParticipationStatus.APPROVED || status == ParticipationStatus.REJECTED ? now : null,
                status == ParticipationStatus.CANCELED ? now : null,
                now,
                now
        );
    }
}
