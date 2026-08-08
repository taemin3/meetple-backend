package com.meetple.backend.domain.push.fcm;

import java.util.Map;

public record PushMessage(
        String title,
        String body,
        Map<String, String> data,
        String collapseKey,
        String notificationTag
) {
}
