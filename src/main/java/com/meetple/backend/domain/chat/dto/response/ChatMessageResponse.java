package com.meetple.backend.domain.chat.dto.response;

import com.meetple.backend.domain.chat.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long sequence,
        UUID clientMessageId,
        Long senderId,
        String senderNickname,
        String senderProfileImageUrl,
        String content,
        LocalDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage message, String senderProfileImageUrl) {
        return new ChatMessageResponse(
                message.getId(),
                message.getMeeting().getId(),
                message.getRoomSequence(),
                message.getClientMessageId(),
                message.getSender().getId(),
                message.getSender().getNickname(),
                senderProfileImageUrl,
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
