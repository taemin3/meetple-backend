package com.meetple.backend.domain.chat.dto.response;

public record ChatWebSocketErrorResponse(
        int status,
        boolean success,
        int code,
        String message
) {
}
