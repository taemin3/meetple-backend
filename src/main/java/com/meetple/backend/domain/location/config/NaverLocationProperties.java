package com.meetple.backend.domain.location.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.location")
public record NaverLocationProperties(
        String baseUrl,
        String clientId,
        String clientSecret
) {
}
