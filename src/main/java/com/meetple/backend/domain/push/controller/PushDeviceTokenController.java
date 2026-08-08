package com.meetple.backend.domain.push.controller;

import com.meetple.backend.domain.push.dto.request.RegisterPushDeviceTokenRequest;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Push", description = "푸시 알림 기기 관리 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push/device-tokens")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class PushDeviceTokenController {

    private final PushDeviceTokenService pushDeviceTokenService;

    @PostMapping
    @Operation(summary = "FCM 기기 토큰 등록 또는 갱신")
    public ResponseEntity<ApiResponse<Void>> register(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody RegisterPushDeviceTokenRequest request
    ) {
        pushDeviceTokenService.register(authenticatedMember.id(), request);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "현재 회원의 특정 기기 토큰 삭제")
    public ResponseEntity<ApiResponse<Void>> removeDevice(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable
            @NotBlank(message = "deviceId는 필수입니다.")
            @Size(max = 100, message = "deviceId는 100자 이하여야 합니다.")
            String deviceId
    ) {
        pushDeviceTokenService.removeDevice(authenticatedMember.id(), deviceId);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }
}
