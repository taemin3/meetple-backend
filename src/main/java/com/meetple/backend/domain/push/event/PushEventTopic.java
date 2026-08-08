package com.meetple.backend.domain.push.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PushEventTopic {

    NOTIFICATION("meetple.push.notification.v1"),
    CHAT("meetple.push.chat.v1");

    private final String value;
}
