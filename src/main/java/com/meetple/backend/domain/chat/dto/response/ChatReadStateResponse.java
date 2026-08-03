package com.meetple.backend.domain.chat.dto.response;

public record ChatReadStateResponse(
        Long roomId,
        Long memberId,
        Long lastReadSequence
) {
}
