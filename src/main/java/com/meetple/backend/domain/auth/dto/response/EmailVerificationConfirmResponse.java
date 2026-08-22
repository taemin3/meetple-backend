package com.meetple.backend.domain.auth.dto.response;

public record EmailVerificationConfirmResponse(
        String signupVerificationToken,
        long expiresIn
) {
}
