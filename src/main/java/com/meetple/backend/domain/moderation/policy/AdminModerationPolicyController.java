package com.meetple.backend.domain.moderation.policy;

import static com.meetple.backend.domain.moderation.policy.AdminModerationPolicyContracts.*;

import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.PageResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Moderation Policy", description = "관리자 운영 정책 및 버전 관리 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/moderation-policies")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class AdminModerationPolicyController {
    private final AdminModerationPolicyService service;

    @GetMapping
    @Operation(summary = "운영 정책 목록 조회", description = "정책 코드와 활성 상태로 운영 정책 버전을 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<PolicySummary>>> getPolicies(
            @RequestParam(required = false) String policyCode,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                service.getPolicies(policyCode, active, page, size)
        );
    }

    @GetMapping("/{policyId}")
    @Operation(summary = "운영 정책 상세 조회", description = "정책 조항, 임베딩 준비 상태와 감사 이력을 조회합니다.")
    public ResponseEntity<ApiResponse<PolicyDetail>> getPolicy(@PathVariable long policyId) {
        return ApiResponse.success(SuccessStatus.OK, service.getPolicy(policyId));
    }

    @PostMapping
    @Operation(summary = "운영 정책 등록", description = "비활성 상태의 운영 정책 1버전과 조항을 등록합니다.")
    public ResponseEntity<ApiResponse<PolicyDetail>> createPolicy(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody CreatePolicyRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.CREATED,
                service.createPolicy(authenticatedMember.id(), request)
        );
    }

    @PostMapping("/{policyId}/versions")
    @Operation(summary = "운영 정책 새 버전 생성", description = "원문을 덮어쓰지 않고 비활성 상태의 새 버전을 생성합니다.")
    public ResponseEntity<ApiResponse<PolicyDetail>> createVersion(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long policyId,
            @Valid @RequestBody CreateVersionRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.CREATED,
                service.createVersion(authenticatedMember.id(), policyId, request)
        );
    }

    @PatchMapping("/{policyId}/activation")
    @Operation(
            summary = "운영 정책 활성 상태 변경",
            description = "임베딩이 준비된 정책을 활성화하거나 정책 검색에서 제외합니다."
    )
    public ResponseEntity<ApiResponse<PolicyDetail>> updateActivation(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long policyId,
            @Valid @RequestBody ActivationRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                service.updateActivation(authenticatedMember.id(), policyId, request)
        );
    }
}
