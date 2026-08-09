package com.meetple.backend.domain.chat.dto.response;

public record ChatNotificationSettingResponse(
        Long roomId,
        boolean enabled
) {
}
