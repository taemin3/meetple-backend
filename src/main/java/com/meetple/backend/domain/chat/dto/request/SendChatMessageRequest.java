package com.meetple.backend.domain.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendChatMessageRequest(
        @NotNull(message = "clientMessageId is required.")
        UUID clientMessageId,

        @NotBlank(message = "Message content is required.")
        @Size(max = 1000, message = "Message content must be at most 1000 characters.")
        String content
) {
}
