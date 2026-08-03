package com.meetple.backend.domain.chat.dto.response;

import java.util.List;

public record ChatMessagePageResponse(
        List<ChatMessageResponse> content,
        boolean hasMore,
        Long oldestSequence,
        Long latestSequence
) {

    public static ChatMessagePageResponse from(List<ChatMessageResponse> content, boolean hasMore) {
        Long oldestSequence = content.isEmpty() ? null : content.getFirst().sequence();
        Long latestSequence = content.isEmpty() ? null : content.getLast().sequence();
        return new ChatMessagePageResponse(content, hasMore, oldestSequence, latestSequence);
    }
}
