package com.meetple.backend.domain.auth.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "auth.email-verification")
public record EmailVerificationProperties(
        Duration codeTtl,
        Duration resendCooldown,
        Duration signupTokenTtl,
        int maxAttempts,
        Duration requesterRateLimitWindow,
        int requesterRateLimit,
        Duration globalRateLimitWindow,
        int globalRateLimit,
        Duration confirmationRequesterRateLimitWindow,
        int confirmationRequesterRateLimit,
        String hmacSecret,
        String fromAddress
) {

    private static final Duration DEFAULT_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final Duration DEFAULT_SIGNUP_TOKEN_TTL = Duration.ofMinutes(15);
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_REQUESTER_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_REQUESTER_RATE_LIMIT = 5;
    private static final Duration DEFAULT_GLOBAL_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_GLOBAL_RATE_LIMIT = 100;
    private static final Duration DEFAULT_CONFIRMATION_REQUESTER_RATE_LIMIT_WINDOW =
            Duration.ofMinutes(1);
    private static final int DEFAULT_CONFIRMATION_REQUESTER_RATE_LIMIT = 10;
    private static final int MINIMUM_SECRET_BYTES = 32;

    public EmailVerificationProperties {
        codeTtl = positiveOrDefault(codeTtl, DEFAULT_CODE_TTL);
        resendCooldown = positiveOrDefault(resendCooldown, DEFAULT_RESEND_COOLDOWN);
        signupTokenTtl = positiveOrDefault(signupTokenTtl, DEFAULT_SIGNUP_TOKEN_TTL);
        maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
        requesterRateLimitWindow = positiveOrDefault(
                requesterRateLimitWindow,
                DEFAULT_REQUESTER_RATE_LIMIT_WINDOW
        );
        requesterRateLimit = requesterRateLimit > 0
                ? requesterRateLimit
                : DEFAULT_REQUESTER_RATE_LIMIT;
        globalRateLimitWindow = positiveOrDefault(
                globalRateLimitWindow,
                DEFAULT_GLOBAL_RATE_LIMIT_WINDOW
        );
        globalRateLimit = globalRateLimit > 0
                ? globalRateLimit
                : DEFAULT_GLOBAL_RATE_LIMIT;
        confirmationRequesterRateLimitWindow = positiveOrDefault(
                confirmationRequesterRateLimitWindow,
                DEFAULT_CONFIRMATION_REQUESTER_RATE_LIMIT_WINDOW
        );
        confirmationRequesterRateLimit = confirmationRequesterRateLimit > 0
                ? confirmationRequesterRateLimit
                : DEFAULT_CONFIRMATION_REQUESTER_RATE_LIMIT;

        if (!StringUtils.hasText(hmacSecret)
                || hmacSecret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("Email verification HMAC secret must be at least 32 bytes.");
        }
        if (!StringUtils.hasText(fromAddress)) {
            throw new IllegalStateException("Email verification sender address is required.");
        }
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isZero() || value.isNegative() ? defaultValue : value;
    }
}
