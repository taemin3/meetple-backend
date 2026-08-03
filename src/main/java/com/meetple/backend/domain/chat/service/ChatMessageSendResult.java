package com.meetple.backend.domain.chat.service;

import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;

public record ChatMessageSendResult(
        ChatMessageResponse message,
        boolean created
) {
}
