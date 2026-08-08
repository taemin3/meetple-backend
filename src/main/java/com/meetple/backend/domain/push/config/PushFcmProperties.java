package com.meetple.backend.domain.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "push.fcm")
public record PushFcmProperties(
        boolean enabled,
        String credentialsPath
) {
}
