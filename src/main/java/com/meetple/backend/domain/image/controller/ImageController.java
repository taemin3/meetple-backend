package com.meetple.backend.domain.image.controller;

import com.meetple.backend.domain.image.dto.request.ImageUploadUrlRequest;
import com.meetple.backend.domain.image.dto.request.ImageUploadUrlsRequest;
import com.meetple.backend.domain.image.dto.response.ImageUploadUrlResponse;
import com.meetple.backend.domain.image.service.ImageService;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Image", description = "이미지 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/images")
public class ImageController {

    private final ImageService imageService;

    @PostMapping("/upload-url")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "이미지 업로드 URL 발급", description = "S3 호환 저장소에 직접 업로드할 presigned URL을 발급합니다.")
    public ResponseEntity<ApiResponse<ImageUploadUrlResponse>> createUploadUrl(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ImageUploadUrlRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                imageService.createUploadUrl(authenticatedMember.id(), request)
        );
    }

    @PostMapping("/upload-urls")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "이미지 업로드 URL 여러 장 발급", description = "여러 이미지 파일을 S3 호환 저장소에 직접 업로드할 presigned URL로 발급합니다.")
    public ResponseEntity<ApiResponse<List<ImageUploadUrlResponse>>> createUploadUrls(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ImageUploadUrlsRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                imageService.createUploadUrls(authenticatedMember.id(), request)
        );
    }
}
