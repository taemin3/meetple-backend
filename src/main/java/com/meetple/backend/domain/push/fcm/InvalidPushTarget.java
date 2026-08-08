package com.meetple.backend.domain.push.fcm;

public record InvalidPushTarget(
        Long targetId,
        String tokenHash
) {
}
