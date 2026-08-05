package com.meetple.backend.global.websocket;

public record ChatAccessRevokedMessage(
        String type,
        ChatAccessRevocationReason reason,
        Long roomId
) {

    public static final String TYPE = "CHAT_ACCESS_REVOKED";

    public static ChatAccessRevokedMessage from(ChatSessionInvalidationEvent event) {
        return new ChatAccessRevokedMessage(TYPE, event.reason(), event.roomId());
    }
}
