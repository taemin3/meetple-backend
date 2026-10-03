package com.meetple.backend.domain.moderation.admin;

import static com.meetple.backend.domain.moderation.admin.AdminModerationContracts.*;
import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.AnalysisStatus;

import com.meetple.backend.domain.moderation.entity.ReportReviewStatus;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Moderation", description = "관리자 신고 검토 및 제재 승인 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reports")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class AdminModerationController {
    private final AdminModerationService service;

    @GetMapping
    @Operation(summary = "관리자 신고 목록 조회", description = "처리 상태와 AI 분석 상태로 신고를 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<ReportSummary>>> getReports(
            @RequestParam(required = false) ReportReviewStatus reviewStatus,
            @RequestParam(required = false) AnalysisStatus analysisStatus,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                service.getReports(reviewStatus, analysisStatus, pageable)
        );
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "관리자 신고 상세 조회", description = "증거 스냅샷, AI 분석, 정책 근거와 처리 이력을 조회합니다.")
    public ResponseEntity<ApiResponse<ReportDetail>> getReport(@PathVariable long reportId) {
        return ApiResponse.success(SuccessStatus.OK, service.getReport(reportId));
    }

    @PostMapping("/{reportId}/actions")
    @Operation(
            summary = "관리자 신고 처리 승인",
            description = "관리자가 신고 처리, 제재, 해제 또는 복구를 승인합니다. 모임 강제 삭제에는 모임장 정지를 함께 적용할 수 있습니다."
    )
    public ResponseEntity<ApiResponse<ActionResult>> applyAction(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long reportId,
            @Valid @RequestBody ActionRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.CREATED,
                service.applyAction(authenticatedMember.id(), reportId, request)
        );
    }
}
