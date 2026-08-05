package com.meetple.backend.domain.chat.realtime;

import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import java.util.Objects;
import java.util.UUID;

public record ChatMessageFanOutEvent(
        UUID eventId,
        ChatMessageResponse message
) {

    public ChatMessageFanOutEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static ChatMessageFanOutEvent create(ChatMessageResponse message) {
        return new ChatMessageFanOutEvent(UUID.randomUUID(), message);
    }
}
