package com.meetple.backend.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
        String nickname,

        @Size(max = 30, message = "한줄 소개는 30자 이하여야 합니다.")
        String introduction
) {

    public UpdateProfileRequest {
        nickname = nickname == null ? null : nickname.trim();
        introduction = introduction == null ? null : introduction.trim();
    }
}
