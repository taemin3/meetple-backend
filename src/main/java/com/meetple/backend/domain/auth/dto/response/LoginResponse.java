package com.meetple.backend.domain.auth.dto.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn
) {

    private static final String BEARER_TYPE = "Bearer";

    public static LoginResponse bearer(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresIn,
            long refreshTokenExpiresIn
    ) {
        return new LoginResponse(
                accessToken,
                refreshToken,
                BEARER_TYPE,
                accessTokenExpiresIn,
                refreshTokenExpiresIn
        );
    }
}
