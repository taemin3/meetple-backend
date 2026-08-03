package com.meetple.backend.domain.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendChatMessageRequest(
        @NotNull(message = "clientMessageId를 입력해주세요.")
        UUID clientMessageId,

        @NotBlank(message = "메시지 내용을 입력해주세요.")
        @Size(max = 1000, message = "메시지 내용은 1000자 이하여야 합니다.")
        String content
) {
}
