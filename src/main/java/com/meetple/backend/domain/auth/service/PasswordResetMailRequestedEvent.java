package com.meetple.backend.domain.auth.service;

public record PasswordResetMailRequestedEvent(
        String email,
        String code,
        String codeHash,
        boolean deliver
) {
}
