package com.meetple.backend.domain.chat.event;

import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;

public record ChatMessageCreatedEvent(ChatMessageResponse message) {
}
