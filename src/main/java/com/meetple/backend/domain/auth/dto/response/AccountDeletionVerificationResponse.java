package com.meetple.backend.domain.auth.dto.response;

public record AccountDeletionVerificationResponse(
        String accountDeletionToken,
        long expiresIn
) {
}
