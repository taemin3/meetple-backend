package com.meetple.backend.domain.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateChatNotificationSettingRequest(
        @NotNull(message = "알림 사용 여부는 필수입니다.")
        Boolean enabled
) {
}
