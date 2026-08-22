package com.meetple.backend.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetProperties(Duration tokenTtl) {

    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(15);

    public PasswordResetProperties {
        tokenTtl = tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()
                ? DEFAULT_TOKEN_TTL
                : tokenTtl;
    }
}
