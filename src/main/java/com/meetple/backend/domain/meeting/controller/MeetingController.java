package com.meetple.backend.domain.meeting.controller;

import com.meetple.backend.domain.meeting.dto.request.CancelMeetingRequest;
import com.meetple.backend.domain.meeting.dto.request.CreateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.request.NearbyMeetingSearchRequest;
import com.meetple.backend.domain.meeting.dto.request.UpdateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.service.MeetingService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting", description = "모임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 생성", description = "로그인한 회원을 모임장으로 새 모임을 생성합니다.")
    public ResponseEntity<ApiResponse<MeetingResponse>> createMeeting(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody CreateMeetingRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.CREATED,
                meetingService.createMeeting(authenticatedMember.id(), request)
        );
    }

    @GetMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 목록 조회", description = "상태 조건을 선택적으로 적용해 모임 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<MeetingResponse>>> getMeetings(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "meetingDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.success(SuccessStatus.OK, meetingService.getMeetings(status, pageable));
    }

    @GetMapping("/nearby")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "주변 모임 조회", description = "요청한 반경 안의 모집 중인 모임을 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<MeetingResponse>>> getNearbyMeetings(
            @Valid @ModelAttribute NearbyMeetingSearchRequest request,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(SuccessStatus.OK, meetingService.getNearbyMeetings(request, pageable));
    }

    @GetMapping("/{meetingId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 상세 조회", description = "모임 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<MeetingResponse>> getMeeting(@PathVariable Long meetingId) {
        return ApiResponse.success(SuccessStatus.OK, meetingService.getMeeting(meetingId));
    }

    @PatchMapping("/{meetingId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 수정", description = "로그인한 회원이 모임장인 모임 정보를 수정합니다.")
    public ResponseEntity<ApiResponse<MeetingResponse>> updateMeeting(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId,
            @Valid @RequestBody UpdateMeetingRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                meetingService.updateMeeting(authenticatedMember.id(), meetingId, request)
        );
    }

    @DeleteMapping("/{meetingId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 삭제", description = "로그인한 회원이 모임장인 모임을 취소 처리합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteMeeting(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId
    ) {
        meetingService.deleteMeeting(authenticatedMember.id(), meetingId);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }

    @PatchMapping("/{meetingId}/complete")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 완료", description = "모임을 완료 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<MeetingResponse>> completeMeeting(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                meetingService.completeMeeting(authenticatedMember.id(), meetingId)
        );
    }

    @PatchMapping("/{meetingId}/cancel")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "모임 취소", description = "로그인한 회원이 모임장인 모임을 취소합니다.")
    public ResponseEntity<ApiResponse<MeetingResponse>> cancelMeeting(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId,
            @Valid @RequestBody CancelMeetingRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                meetingService.cancelMeeting(authenticatedMember.id(), meetingId, request.reason())
        );
    }
}
