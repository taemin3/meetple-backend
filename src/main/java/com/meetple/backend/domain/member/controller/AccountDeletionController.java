package com.meetple.backend.domain.member.controller;

import com.meetple.backend.domain.auth.dto.request.AccountDeletionCompleteRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.dto.response.AccountDeletionVerificationResponse;
import com.meetple.backend.domain.member.service.AccountDeletionService;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account Deletion", description = "외부 계정 삭제 요청 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account-deletions")
public class AccountDeletionController {

    private final AccountDeletionService accountDeletionService;

    @PostMapping("/email-verifications")
    @Operation(summary = "계정 삭제 인증번호 발송", description = "계정 존재 여부와 무관하게 동일하게 응답합니다.")
    public ResponseEntity<ApiResponse<Void>> sendCode(
            @Valid @RequestBody EmailVerificationSendRequest request,
            HttpServletRequest httpServletRequest
    ) {
        accountDeletionService.sendVerificationCode(request, httpServletRequest.getRemoteAddr());
        return ApiResponse.successOnly(SuccessStatus.OK);
    }

    @PostMapping("/email-verifications/confirm")
    @Operation(summary = "계정 삭제 인증번호 확인", description = "계정 삭제 전용 일회용 토큰을 발급합니다.")
    public ResponseEntity<ApiResponse<AccountDeletionVerificationResponse>> confirmCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                accountDeletionService.confirm(request, httpServletRequest.getRemoteAddr())
        );
    }

    @PostMapping
    @Operation(summary = "웹 계정 삭제", description = "이메일 인증을 완료한 계정의 삭제를 최종 요청합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @Valid @RequestBody AccountDeletionCompleteRequest request
    ) {
        accountDeletionService.deleteWithVerifiedEmail(request);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }
}
