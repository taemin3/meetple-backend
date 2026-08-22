package com.meetple.backend.domain.auth.controller;

import com.meetple.backend.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.dto.request.LoginRequest;
import com.meetple.backend.domain.auth.dto.request.LogoutRequest;
import com.meetple.backend.domain.auth.dto.request.PasswordResetRequest;
import com.meetple.backend.domain.auth.dto.request.ReissueRequest;
import com.meetple.backend.domain.auth.dto.request.SignupRequest;
import com.meetple.backend.domain.auth.dto.response.AuthMemberResponse;
import com.meetple.backend.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.meetple.backend.domain.auth.dto.response.LoginResponse;
import com.meetple.backend.domain.auth.dto.response.PasswordResetVerificationResponse;
import com.meetple.backend.domain.auth.service.AuthService;
import com.meetple.backend.domain.auth.service.EmailVerificationService;
import com.meetple.backend.domain.auth.service.PasswordResetService;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/email-verifications")
    @Operation(summary = "회원가입 이메일 인증번호 발송", description = "입력한 이메일로 6자리 인증번호를 발송합니다.")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerificationCode(
            @Valid @RequestBody EmailVerificationSendRequest request,
            HttpServletRequest httpServletRequest
    ) {
        emailVerificationService.sendVerificationCode(request, httpServletRequest.getRemoteAddr());
        return ApiResponse.successOnly(SuccessStatus.OK);
    }

    @PostMapping("/email-verifications/confirm")
    @Operation(
            summary = "회원가입 이메일 인증번호 확인",
            description = "인증번호를 확인하고 회원가입 전용 1회성 토큰을 발급합니다."
    )
    public ResponseEntity<ApiResponse<EmailVerificationConfirmResponse>> confirmEmailVerificationCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                emailVerificationService.confirm(request, httpServletRequest.getRemoteAddr())
        );
    }

    @PostMapping("/password-resets/email-verifications")
    @Operation(
            summary = "비밀번호 재설정 인증번호 발송",
            description = "계정 존재 여부를 노출하지 않고 입력한 이메일의 비밀번호 재설정 인증을 시작합니다."
    )
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetVerificationCode(
            @Valid @RequestBody EmailVerificationSendRequest request,
            HttpServletRequest httpServletRequest
    ) {
        passwordResetService.sendVerificationCode(request, httpServletRequest.getRemoteAddr());
        return ApiResponse.successOnly(SuccessStatus.OK);
    }

    @PostMapping("/password-resets/email-verifications/confirm")
    @Operation(
            summary = "비밀번호 재설정 인증번호 확인",
            description = "인증번호를 확인하고 비밀번호 재설정 전용 1회성 토큰을 발급합니다."
    )
    public ResponseEntity<ApiResponse<PasswordResetVerificationResponse>> confirmPasswordResetVerificationCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                passwordResetService.confirm(request, httpServletRequest.getRemoteAddr())
        );
    }

    @PostMapping("/password-resets")
    @Operation(
            summary = "비밀번호 재설정",
            description = "1회성 재설정 토큰으로 새 비밀번호를 저장하고 기존 로그인 세션을 모두 종료합니다."
    )
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        passwordResetService.resetPassword(request);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "인증된 이메일, 비밀번호, 닉네임으로 회원가입합니다.")
    public ResponseEntity<ApiResponse<AuthMemberResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(SuccessStatus.CREATED, authService.signup(request));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하고 access token을 발급합니다.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(SuccessStatus.OK, authService.login(request));
    }

    @PostMapping("/reissue")
    @Operation(summary = "토큰 재발급", description = "refresh token으로 access token과 refresh token을 재발급합니다.")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponse.success(SuccessStatus.OK, authService.reissue(request));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(
            summary = "로그아웃",
            description = "저장된 refresh token을 삭제하고 access token을 차단합니다. deviceId가 있으면 해당 기기의 FCM token도 삭제합니다."
    )
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request, authorizationHeader);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(
            summary = "전체 기기 로그아웃",
            description = "회원의 모든 refresh token 세션과 FCM 기기 token을 삭제합니다."
    )
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        authService.logoutAll(authorizationHeader);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }
}
