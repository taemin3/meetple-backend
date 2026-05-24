package com.meetple.backend.global.security;

public record JwtTokenSession(
        Long memberId,
        String sessionId
) {
}
