package com.meetple.backend.domain.meeting.controller;

import com.meetple.backend.domain.meeting.dto.request.CreateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.request.NearbyMeetingSearchRequest;
import com.meetple.backend.domain.meeting.dto.request.UpdateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
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

@Tag(name = "Meeting", description = "Meeting API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "Create meeting", description = "Create a meeting hosted by the logged-in member.")
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
    @Operation(summary = "Get meetings", description = "Get meetings with optional status filtering.")
    public ResponseEntity<ApiResponse<PageResponse<MeetingResponse>>> getMeetings(
            @RequestParam(required = false) MeetingStatus status,
            @PageableDefault(size = 20, sort = "meetingDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.success(SuccessStatus.OK, meetingService.getMeetings(status, pageable));
    }

    @GetMapping("/nearby")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "Get nearby meetings", description = "Get recruiting meetings within the requested radius.")
    public ResponseEntity<ApiResponse<PageResponse<MeetingResponse>>> getNearbyMeetings(
            @Valid @ModelAttribute NearbyMeetingSearchRequest request,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(SuccessStatus.OK, meetingService.getNearbyMeetings(request, pageable));
    }

    @GetMapping("/{meetingId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "Get meeting", description = "Get a meeting detail.")
    public ResponseEntity<ApiResponse<MeetingResponse>> getMeeting(@PathVariable Long meetingId) {
        return ApiResponse.success(SuccessStatus.OK, meetingService.getMeeting(meetingId));
    }

    @PatchMapping("/{meetingId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "Update meeting", description = "Update a meeting hosted by the logged-in member.")
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
    @Operation(summary = "Delete meeting", description = "Cancel a meeting hosted by the logged-in member.")
    public ResponseEntity<ApiResponse<Void>> deleteMeeting(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId
    ) {
        meetingService.deleteMeeting(authenticatedMember.id(), meetingId);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }

    @PatchMapping("/{meetingId}/complete")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "Complete meeting", description = "Mark a meeting as completed.")
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
    @Operation(summary = "Cancel meeting", description = "Cancel a meeting hosted by the logged-in member.")
    public ResponseEntity<ApiResponse<MeetingResponse>> cancelMeeting(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                meetingService.cancelMeeting(authenticatedMember.id(), meetingId)
        );
    }
}
