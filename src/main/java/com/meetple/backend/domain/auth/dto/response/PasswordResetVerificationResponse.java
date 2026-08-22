package com.meetple.backend.domain.auth.dto.response;

public record PasswordResetVerificationResponse(
        String passwordResetToken,
        long expiresIn
) {
}
