package com.meetple.backend.domain.auth.service;

public record SignupEmailVerificationCompletedEvent(
        String email,
        String signupVerificationToken
) {
}
