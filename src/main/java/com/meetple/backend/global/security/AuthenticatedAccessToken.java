package com.meetple.backend.global.security;

import java.time.Instant;
import org.springframework.security.core.Authentication;

public record AuthenticatedAccessToken(
        Authentication authentication,
        JwtTokenSession session,
        Instant expiresAt
) {
}
