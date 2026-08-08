package com.meetple.backend.domain.push.fcm;

public record PushSendFailure(
        Long targetId,
        String errorCode
) {
}
