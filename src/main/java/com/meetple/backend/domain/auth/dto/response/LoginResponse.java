package com.meetple.backend.domain.auth.dto.response;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    private static final String BEARER_TYPE = "Bearer";

    public static LoginResponse bearer(String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, BEARER_TYPE, expiresIn);
    }
}
