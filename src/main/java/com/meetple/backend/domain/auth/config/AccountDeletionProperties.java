package com.meetple.backend.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.account-deletion")
public record AccountDeletionProperties(Duration tokenTtl) {

    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(15);

    public AccountDeletionProperties {
        tokenTtl = tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()
                ? DEFAULT_TOKEN_TTL
                : tokenTtl;
    }
}
