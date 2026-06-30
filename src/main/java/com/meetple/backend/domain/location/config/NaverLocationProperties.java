package com.meetple.backend.domain.location.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.location")
public record NaverLocationProperties(
        String searchBaseUrl,
        String searchClientId,
        String searchClientSecret,
        String mapsBaseUrl,
        String mapsClientId,
        String mapsClientSecret
) {
}
