package com.meetple.backend.global.security;

import org.springframework.security.core.Authentication;

public record AuthenticatedAccessToken(
        Authentication authentication,
        JwtTokenSession session
) {
}
