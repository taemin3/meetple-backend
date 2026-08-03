package com.meetple.backend.domain.chat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record MarkChatRoomReadRequest(
        @NotNull(message = "lastReadSequence를 입력해주세요.")
        @PositiveOrZero(message = "lastReadSequence는 0 이상이어야 합니다.")
        Long lastReadSequence
) {
}
