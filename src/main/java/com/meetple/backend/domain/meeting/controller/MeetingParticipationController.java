package com.meetple.backend.domain.meeting.controller;

import com.meetple.backend.domain.meeting.dto.request.CreateMeetingParticipationRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingParticipationResponse;
import com.meetple.backend.domain.meeting.service.MeetingParticipationService;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.PageResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MeetingParticipation", description = "모임 참여 신청 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/participations")
public class MeetingParticipationController {

    private final MeetingParticipationService participationService;

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 참여 신청", description = "모임에 참여 신청을 생성합니다.")
    public ResponseEntity<ApiResponse<MeetingParticipationResponse>> applyParticipation(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId,
            @Valid @RequestBody(required = false) CreateMeetingParticipationRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.CREATED,
                participationService.applyParticipation(authenticatedMember.id(), meetingId, request)
        );
    }

    @GetMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 참여 신청 목록 조회", description = "모임장이 자신이 개설한 모임의 참여 신청 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<MeetingParticipationResponse>>> getMeetingParticipations(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                participationService.getMeetingParticipations(authenticatedMember.id(), meetingId, status, pageable)
        );
    }

    @PatchMapping("/{participationId}/approve")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 참여 신청 승인", description = "대기 중인 모임 참여 신청을 승인합니다.")
    public ResponseEntity<ApiResponse<MeetingParticipationResponse>> approveParticipation(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId,
            @PathVariable Long participationId
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                participationService.approveParticipation(authenticatedMember.id(), meetingId, participationId)
        );
    }

    @PatchMapping("/{participationId}/reject")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 참여 신청 거절", description = "대기 중인 모임 참여 신청을 거절합니다.")
    public ResponseEntity<ApiResponse<MeetingParticipationResponse>> rejectParticipation(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId,
            @PathVariable Long participationId
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                participationService.rejectParticipation(authenticatedMember.id(), meetingId, participationId)
        );
    }

    @PatchMapping("/{participationId}/cancel")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 참여 취소", description = "본인의 대기 중이거나 승인된 모임 참여를 취소합니다.")
    public ResponseEntity<ApiResponse<MeetingParticipationResponse>> cancelParticipation(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId,
            @PathVariable Long participationId
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                participationService.cancelParticipation(authenticatedMember.id(), meetingId, participationId)
        );
    }
}
