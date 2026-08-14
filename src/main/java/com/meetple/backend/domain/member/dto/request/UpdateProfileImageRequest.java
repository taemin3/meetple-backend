package com.meetple.backend.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileImageRequest(
        @NotBlank(message = "프로필 이미지 URL을 입력해주세요.")
        @Size(max = 255, message = "프로필 이미지 URL은 255자 이하여야 합니다.")
        String profileImageUrl
) {
}
