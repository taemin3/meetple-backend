package com.meetple.backend.domain.outbox.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxEventTopic {

    PUSH_NOTIFICATION("meetple.push.notification.v1"),
    PUSH_CHAT("meetple.push.chat.v1"),
    IMAGE_DELETION("meetple.image.delete.v1");

    private final String value;
}
