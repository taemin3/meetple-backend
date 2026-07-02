package com.meetple.backend.domain.image.dto.request;

import com.meetple.backend.domain.image.entity.ImageUploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ImageUploadUrlRequest(
        @NotNull(message = "이미지 용도를 선택해주세요.")
        ImageUploadPurpose purpose,

        @NotBlank(message = "파일명을 입력해주세요.")
        @Size(max = 255, message = "파일명은 255자 이하여야 합니다.")
        String fileName,

        @NotBlank(message = "이미지 Content-Type을 입력해주세요.")
        @Size(max = 100, message = "Content-Type은 100자 이하여야 합니다.")
        String contentType,

        @NotNull(message = "파일 크기를 입력해주세요.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        Long contentLength
) {
}
