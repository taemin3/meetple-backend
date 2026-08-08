package com.meetple.backend.domain.push.dto.request;

import com.meetple.backend.domain.push.entity.PushDevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterPushDeviceTokenRequest(
        @NotBlank(message = "deviceId는 필수입니다.")
        @Size(max = 100, message = "deviceId는 100자 이하여야 합니다.")
        String deviceId,

        @NotBlank(message = "FCM token은 필수입니다.")
        @Size(max = 4096, message = "FCM token이 너무 깁니다.")
        String token,

        @NotNull(message = "platform은 필수입니다.")
        PushDevicePlatform platform
) {
}
