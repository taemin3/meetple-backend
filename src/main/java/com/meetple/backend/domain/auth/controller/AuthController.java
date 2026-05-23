package com.meetple.backend.domain.auth.controller;

import com.meetple.backend.domain.auth.dto.request.LoginRequest;
import com.meetple.backend.domain.auth.dto.request.LogoutRequest;
import com.meetple.backend.domain.auth.dto.request.ReissueRequest;
import com.meetple.backend.domain.auth.dto.request.SignupRequest;
import com.meetple.backend.domain.auth.dto.response.AuthMemberResponse;
import com.meetple.backend.domain.auth.dto.response.LoginResponse;
import com.meetple.backend.domain.auth.service.AuthService;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임으로 회원가입합니다.")
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
    @Operation(summary = "로그아웃", description = "저장된 refresh token을 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }
}
