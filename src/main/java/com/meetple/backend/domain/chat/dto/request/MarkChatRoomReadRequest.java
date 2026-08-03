package com.meetple.backend.domain.chat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record MarkChatRoomReadRequest(
        @NotNull(message = "lastReadSequence is required.")
        @PositiveOrZero(message = "lastReadSequence must not be negative.")
        Long lastReadSequence
) {
}
