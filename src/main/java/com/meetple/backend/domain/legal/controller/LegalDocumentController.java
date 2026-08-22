package com.meetple.backend.domain.legal.controller;

import com.meetple.backend.domain.legal.dto.response.SignupLegalDocumentResponse;
import com.meetple.backend.domain.legal.service.LegalDocumentService;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Legal Document", description = "약관 문서 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/legal-documents")
public class LegalDocumentController {

    private final LegalDocumentService legalDocumentService;

    @GetMapping("/signup")
    @Operation(summary = "회원가입 약관 조회", description = "현재 적용되는 회원가입 필수 문서를 조회합니다.")
    public ResponseEntity<ApiResponse<List<SignupLegalDocumentResponse>>> getSignupDocuments() {
        return ApiResponse.success(SuccessStatus.OK, legalDocumentService.getCurrentSignupDocuments());
    }
}
