package com.meetple.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @NotBlank(message = "refresh token은 필수입니다.")
        String refreshToken,

        @Size(max = 100, message = "deviceId는 100자 이하여야 합니다.")
        String deviceId
) {

    public LogoutRequest(String refreshToken) {
        this(refreshToken, null);
    }
}
