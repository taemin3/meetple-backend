package com.meetple.backend.domain.member.controller;

import com.meetple.backend.domain.member.dto.response.MemberProfileResponse;
import com.meetple.backend.domain.member.service.MemberService;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "회원 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "내 정보 조회", description = "로그인한 회원의 기본 프로필 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return ApiResponse.success(SuccessStatus.OK, memberService.getMyProfile(authenticatedMember.id()));
    }
}
