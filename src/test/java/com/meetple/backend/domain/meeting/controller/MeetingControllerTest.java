package com.meetple.backend.domain.meeting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.meeting.dto.request.CreateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.request.UpdateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.service.MeetingService;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.exception.BadRequestException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MeetingControllerTest {

    @Mock
    private MeetingService meetingService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MeetingController(meetingService))
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
    void createMeetingReturnsCreatedApiResponse() throws Exception {
        given(meetingService.createMeeting(eq(1L), any(CreateMeetingRequest.class)))
                .willReturn(meetingResponse());

        mockMvc.perform(post("/api/v1/meetings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.CREATED.getCode()))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.title").value("Weekend running"))
                .andExpect(jsonPath("$.data.status").value("RECRUITING"));
    }

    @Test
    void getMeetingsReturnsPagedApiResponse() throws Exception {
        given(meetingService.getMeetings(eq("RECRUITING"), any()))
                .willReturn(PageResponse.from(new org.springframework.data.domain.PageImpl<>(
                        List.of(meetingResponse()),
                        PageRequest.of(0, 20),
                        1
                )));

        mockMvc.perform(get("/api/v1/meetings")
                        .param("status", "RECRUITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data.content[0].id").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getMeetingsReturnsBadRequestForInvalidStatus() throws Exception {
        given(meetingService.getMeetings(eq("OPEN"), any()))
                .willThrow(new BadRequestException("지원하지 않는 모임 상태입니다."));

        mockMvc.perform(get("/api/v1/meetings")
                        .param("status", "OPEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("지원하지 않는 모임 상태입니다."));
    }

    @Test
    void getNearbyMeetingsReturnsPagedApiResponse() throws Exception {
        given(meetingService.getNearbyMeetings(any(), eq(PageRequest.of(0, 20))))
                .willReturn(PageResponse.from(new org.springframework.data.domain.PageImpl<>(
                        List.of(meetingResponse()),
                        PageRequest.of(0, 20),
                        1
                )));

        mockMvc.perform(get("/api/v1/meetings/nearby")
                        .param("latitude", "37.5219")
                        .param("longitude", "126.9245")
                        .param("radiusMeters", "1000")
                        .param("category", "exercise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].categoryName").value("exercise"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getMeetingReturnsApiResponse() throws Exception {
        given(meetingService.getMeeting(10L)).willReturn(meetingResponse());

        mockMvc.perform(get("/api/v1/meetings/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.hostId").value(1));
    }

    @Test
    void updateMeetingReturnsApiResponse() throws Exception {
        given(meetingService.updateMeeting(eq(1L), eq(10L), any(UpdateMeetingRequest.class)))
                .willReturn(meetingResponse());

        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "Updated title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        mockMvc.perform(patch("/api/v1/meetings/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void deleteMeetingReturnsOkApiResponse() throws Exception {
        mockMvc.perform(delete("/api/v1/meetings/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()));

        verify(meetingService).deleteMeeting(1L, 10L);
    }

    @Test
    void completeMeetingReturnsApiResponse() throws Exception {
        given(meetingService.completeMeeting(1L, 10L)).willReturn(meetingResponse());

        mockMvc.perform(patch("/api/v1/meetings/10/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void cancelMeetingReturnsApiResponse() throws Exception {
        given(meetingService.cancelMeeting(1L, 10L)).willReturn(meetingResponse());

        mockMvc.perform(patch("/api/v1/meetings/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));
    }

    private void authenticate() {
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(
                1L,
                "user@meetple.com",
                MemberRole.USER
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedMember, null, List.of())
        );
    }

    private CreateMeetingRequest createRequest() {
        return new CreateMeetingRequest(
                "Weekend running",
                "exercise",
                "Yeouido Park",
                "330 Yeouidong-ro, Yeongdeungpo-gu, Seoul",
                37.5219,
                126.9245,
                LocalDateTime.now().plusDays(7),
                10,
                "Run together at an easy pace."
        );
    }

    private MeetingResponse meetingResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 0);
        return new MeetingResponse(
                10L,
                1L,
                "host",
                1L,
                "exercise",
                "Weekend running",
                "Run together at an easy pace.",
                "Yeouido Park",
                "330 Yeouidong-ro, Yeongdeungpo-gu, Seoul",
                37.5219,
                126.9245,
                now.plusDays(7),
                10,
                1,
                MeetingStatus.RECRUITING,
                null,
                now,
                now
        );
    }
}
