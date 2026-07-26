package com.meetple.backend.domain.meeting.controller;

import com.meetple.backend.domain.meeting.dto.response.MeetingEngagementResponse;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.service.MeetingEngagementService;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.PageResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MeetingEngagement", description = "모임 참여 상태 및 찜 API")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class MeetingEngagementController {

    private final MeetingEngagementService engagementService;

    @GetMapping("/api/v1/meetings/{meetingId}/engagement")
    @Operation(summary = "모임 상세 참여 상태 조회")
    public ResponseEntity<ApiResponse<MeetingEngagementResponse>> getEngagement(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                engagementService.getEngagement(authenticatedMember.id(), meetingId)
        );
    }

    @PostMapping("/api/v1/meetings/{meetingId}/bookmark")
    @Operation(summary = "모임 찜 추가")
    public ResponseEntity<ApiResponse<Void>> addBookmark(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId
    ) {
        engagementService.addBookmark(authenticatedMember.id(), meetingId);
        return ApiResponse.successOnly(SuccessStatus.CREATED);
    }

    @DeleteMapping("/api/v1/meetings/{meetingId}/bookmark")
    @Operation(summary = "모임 찜 해제")
    public ResponseEntity<ApiResponse<Void>> removeBookmark(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long meetingId
    ) {
        engagementService.removeBookmark(authenticatedMember.id(), meetingId);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }

    @GetMapping("/api/v1/users/me/meetings/bookmarked")
    @Operation(summary = "내가 찜한 모임 조회")
    public ResponseEntity<ApiResponse<PageResponse<MeetingResponse>>> getMyBookmarks(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                engagementService.getMyBookmarks(authenticatedMember.id(), pageable)
        );
    }
}
