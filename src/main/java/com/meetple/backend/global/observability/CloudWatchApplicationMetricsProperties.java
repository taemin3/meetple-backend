package com.meetple.backend.global.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "meetple.observability.cloudwatch")
public record CloudWatchApplicationMetricsProperties(
        boolean enabled,
        String namespace,
        Duration step,
        String environment
) {
}
