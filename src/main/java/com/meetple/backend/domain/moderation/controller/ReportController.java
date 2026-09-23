package com.meetple.backend.domain.moderation.controller;

import com.meetple.backend.domain.moderation.dto.request.CreateReportRequest;
import com.meetple.backend.domain.moderation.dto.response.ReportResponse;
import com.meetple.backend.domain.moderation.service.ModerationService;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "사용자 생성 콘텐츠 신고 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class ReportController {
    private final ModerationService moderationService;

    @PostMapping
    @Operation(summary = "신고 생성", description = "회원, 모임 또는 채팅 메시지를 신고합니다.")
    public ResponseEntity<ApiResponse<ReportResponse>> createReport(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody CreateReportRequest request
    ) {
        return ApiResponse.success(SuccessStatus.CREATED,
                moderationService.createReport(authenticatedMember.id(), request));
    }
}
